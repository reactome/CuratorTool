package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaAttribute;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is used to handle an update for class EntityFunctonalStatute by doing the following:
 * 1). Move the PhysicalEntity value to diseaseEntity
 * 2). Figure out the possible values for normalEntity when a normalReaction is filled in
 * 3). For convenience, the main() method in the class also call split of Drug into three subclasses:
 * ProteinDrug, ChemicalDrug, and RNADrug.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class EntityFunctionalStatusUpdate {
    private FileUtilities fu;
    private final String fileName = "EntityFunctionalStatusUpdate_Output.txt";
    
    public EntityFunctionalStatusUpdate() {
    }
    
    /**
     * Perform the actual update in this method as described in the description for the
     * class.
     * @param dba
     * @throws Exception
     */
    public void update(MySQLAdaptor dba) throws Exception {
        fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine("SchemaClass\tDB_ID\tDisplayName\tLastAuthor\tActionOrIssue");
        Collection<GKInstance> efs = dba.fetchInstancesByClass(ReactomeJavaConstants.EntityFunctionalStatus);
        // Move physicalEntity to diseaseEntity
        moveToDiseaseEntity(efs);
        fillInNormalEntity(efs, dba);
        Set<GKInstance> newInstances = new HashSet<>();
        Set<GKInstance> caUpdatedRLEs = new HashSet<>();
        Set<GKInstance> regulationUpdatedRLEs = new HashSet<>();
        fixDiseaseEntityNotInRLE(efs,
                                 newInstances,
                                 caUpdatedRLEs,
                                 regulationUpdatedRLEs);
        commit(efs, 
               newInstances,
               caUpdatedRLEs,
               regulationUpdatedRLEs,
               dba);
        fu.close();
    }
    
    private void commit(Collection<GKInstance> efs, 
                        Set<GKInstance> newInstances,
                        Set<GKInstance> caUpdatedRLEs,
                        Set<GKInstance> regulationUpdatedRLEs,
                        MySQLAdaptor dba) throws Exception {
        try {
            dba.startTransaction();
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, 
                                                            ScriptUtilities.GUANMING_WU_DB_ID,
                                                            true);
            // Need to store all new instances first to get correct created
            // Make the following two steps so that all new instances can have created.
            // Otherwise, some of them may not because of the check in storeInstance()
            for (GKInstance ca : newInstances) {
                ca.setAttributeValue(ReactomeJavaConstants.created, ie);
            }
            for (GKInstance ca : newInstances) {
                dba.storeInstance(ca);
            }
            for (GKInstance instance : efs) {
                dba.updateInstanceAttribute(instance, ReactomeJavaConstants.diseaseEntity);
                dba.updateInstanceAttribute(instance, ReactomeJavaConstants.normalEntity);
                // Just in case some of _displayName may be changed
                dba.updateInstanceAttribute(instance, ReactomeJavaConstants._displayName);
                ScriptUtilities.addIEToModified(instance, ie, dba);
            }
            for (GKInstance rle : caUpdatedRLEs) {
                dba.updateInstanceAttribute(rle, ReactomeJavaConstants.catalystActivity);
                ScriptUtilities.addIEToModified(rle, ie, dba);
            }
            for (GKInstance rle : regulationUpdatedRLEs) {
                dba.updateInstanceAttribute(rle, ReactomeJavaConstants.regulatedBy);
                ScriptUtilities.addIEToModified(rle, ie, dba);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
            throw e;
        }
    }
    
    private void moveToDiseaseEntity(Collection<GKInstance> efs) throws Exception {
        for (GKInstance inst : efs) {
            GKInstance pe = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (pe == null) {
//                throw new IllegalStateException(inst + " doesn't have a value in its physicalEntity slot!");
                continue;
            }
            inst.setAttributeValue(ReactomeJavaConstants.diseaseEntity, pe);
        }
    }
    
    private void fixDiseaseEntityNotInRLE(Collection<GKInstance> efses,
                                          Set<GKInstance> newInstances,
                                          Set<GKInstance> caUpdatedRLEs,
                                          Set<GKInstance> regulationUpdatedRLEs) throws Exception {
        Map<String, GKInstance> keyToNewDiseaseComplex = new HashMap<>();
        for (GKInstance efs : efses) {
            GKInstance pe = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (pe == null)
                continue;
            Collection<GKInstance> rles = efs.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
            if (rles == null || rles.size() == 0) {
                continue;
            }
            for (GKInstance diseaseRLE : rles) {
                GKInstance normalReaction = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.normalReaction);
                // Make sure PE is one of diseaseReaction's participants
                Set<GKInstance> participants = grepLFSReactionParticipants(diseaseRLE);
                if (participants.contains(pe))
                    continue;
                NormalEntityMapResult result = guessNormalEntity(pe, 
                                                                 normalReaction);
                String action = getFixDiseaseEntityAction(result, pe, diseaseRLE, normalReaction);
                if (!action.startsWith("auto fix:")) 
                    continue;
                if (result.role.equals("catalyst")) {
                    GKInstance diseaseCAS = null;
                    if (action.endsWith("replaced complex")) {
                        diseaseCAS = createComplexDiseaseCA(normalReaction,
                                                            pe,
                                                            keyToNewDiseaseComplex);
                        // Need to replace EFS's PE with the current one
                        if (diseaseCAS != null) {
                            GKInstance diseaseEntity = (GKInstance) diseaseCAS.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                            efs.setAttributeValue(ReactomeJavaConstants.diseaseEntity, diseaseEntity);
                            InstanceDisplayNameGenerator.setDisplayName(efs);
                            newInstances.add(diseaseEntity);
                        }
                    }
                    else {
                        diseaseCAS = createDiseaseCA(normalReaction,
                                                     pe);
                    }
                    if (diseaseCAS != null) {
                        diseaseRLE.setAttributeValue(ReactomeJavaConstants.catalystActivity, diseaseCAS);
                        caUpdatedRLEs.add(diseaseRLE);
                        newInstances.add(diseaseCAS);
                        fu.printLine(diseaseRLE.getSchemClass().getName() + "\t" + 
                                diseaseRLE.getDBID() + "\t" + 
                                diseaseRLE.getDisplayName() + "\t" + 
                                getLastAuthor(diseaseRLE).getDisplayName() + "\t" + 
                                action);
                    }
                }
                else if (result.role.equals("regulator")) {
                    GKInstance diseaseRegulation = createDiseaseRegulation(normalReaction, pe);
                    if (diseaseRegulation != null) {
                        // Just in case there are other values
                        diseaseRLE.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
                        diseaseRLE.addAttributeValue(ReactomeJavaConstants.regulatedBy, diseaseRegulation);
                        regulationUpdatedRLEs.add(diseaseRLE);
                        newInstances.add(diseaseRegulation);
                        fu.printLine(diseaseRLE.getSchemClass().getName() + "\t" + 
                                diseaseRLE.getDBID() + "\t" + 
                                diseaseRLE.getDisplayName() + "\t" + 
                                getLastAuthor(diseaseRLE).getDisplayName() + "\t" + 
                                action);
                    }
                }
            }
        }
    }
    
    private void fillInNormalEntity(Collection<GKInstance> efses,
                                    MySQLAdaptor dba) throws Exception {
        Map<Long, Long> hardcodedMap = getHardcodedMap();
        for (GKInstance efs : efses) {
            GKInstance pe = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (pe == null)
                continue; // Cannot do anything
            Collection<GKInstance> rles = efs.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
            if (rles == null || rles.size() == 0) {
                continue; // If this is not used, cannot do anything
            }
            GKInstance normalEntity = null;
            Long normalId = hardcodedMap.get(pe.getDBID());
            if (normalId != null)
                normalEntity = dba.fetchInstance(normalId);
            else {
                // In case we have multiple normalEntityes
                Set<GKInstance> normalEntitySet = new HashSet<>();
                for (GKInstance diseaseRLE : rles) {
                    GKInstance normalReaction = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.normalReaction);
                    if (normalReaction == null)
                        continue;
                    // Make sure PE is one of diseaseReaction's participants
                    Set<GKInstance> participants = grepLFSReactionParticipants(diseaseRLE);
                    NormalEntityMapResult result = null;
                    if (!participants.contains(pe)) {
                        result = guessNormalEntity(pe, 
                                                   normalReaction);
                    }
                    else {
                        result = guessNormalEntity(pe, 
                                                   diseaseRLE, 
                                                   normalReaction);
                    }
                    if (result.normalEntity != null) {
                        normalEntitySet.add(result.normalEntity);
                    }
                }
                if (normalEntitySet.size() == 1)
                    normalEntity = normalEntitySet.stream().findFirst().get();
                else if (normalEntitySet.size() > 1) {
                    GKInstance ie = getLastAuthor(efs);
                    fu.printLine(efs.getSchemClass().getName() + "\t" +
                                 efs.getDBID() + "\t" + 
                                 efs.getDisplayName() + "\t" + 
                                 ie.getDisplayName() + "\t" +
                                 "PE can be mapped to more than one normalEntity: " + 
                                 normalEntitySet.stream().map(e -> e.toString()).collect(Collectors.joining(";")));
                }
            }
            if (normalEntity != null)
                efs.setAttributeValue(ReactomeJavaConstants.normalEntity, normalEntity);
        }
    }
    
    private Map<Long, Long> getHardcodedMap() {
        String[] text = new String[] {
                "6802617 5672718",
                "6802642 5672718",
                "6802633 5672718",
                "6803238 5672729",
                "8936669 5672729",
                "8936725 5672729"
        };
        Map<Long, Long> map = new HashMap<>();
        Stream.of(text).forEach(line -> {
            String[] tokens = line.split(" ");
            map.put(new Long(tokens[0]), new Long(tokens[1]));
        });
        return map;
    }
    
    private GKInstance createDiseaseCA(GKInstance normalRLE,
                                       GKInstance efsPE) throws Exception {
        GKInstance normalCA = (GKInstance) normalRLE.getAttributeValue(ReactomeJavaConstants.catalystActivity);
        if (normalCA == null)
            return null;
        return _createDiseaseCA(efsPE, normalCA);
    }
    
    private GKInstance createComplexDiseaseCA(GKInstance normalRLE,
                                              GKInstance efsPE,
                                              Map<String, GKInstance> keyToNewDiseaseComplex) throws Exception {
        GKInstance normalCA = (GKInstance) normalRLE.getAttributeValue(ReactomeJavaConstants.catalystActivity);
        if (normalCA == null)
            return null;
        GKInstance normalCatalyst = (GKInstance) normalCA.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        if (normalCatalyst == null || !normalCatalyst.getSchemClass().isa(ReactomeJavaConstants.Complex))
            return null;
        // Create a new set of hasComponent
        List<GKInstance> normalComps = normalCatalyst.getAttributeValuesList(ReactomeJavaConstants.hasComponent);
        List<GKInstance> diseaseComps = new ArrayList<>();
        Set<GKInstance> efsPERefs = grepReferenceEntitiesForPE(efsPE);
        boolean isModified = false;
        // Create a new name now
        StringBuilder builder = new StringBuilder();
        for (GKInstance normalComp : normalComps) {
            Set<GKInstance> normalRefs = grepReferenceEntitiesForPE(normalComp);
            if (normalRefs.equals(efsPERefs)) {
                diseaseComps.add(efsPE);
                isModified = true;
                if (builder.length() > 0)
                    builder.append(":");
                builder.append(efsPE.getAttributeValue(ReactomeJavaConstants.name));
            }
            else {
                diseaseComps.add(normalComp);
                if (builder.length() > 0)
                    builder.append(":");
                builder.append(normalComp.getAttributeValue(ReactomeJavaConstants.name));
            }
        }
        if (!isModified)
            return null; // We just cannot use the old normal components, which doesn't make sense.
        // To avoid duplication, we want to make sure a complex doesn't exit
        String complexKey = createComplexKey(diseaseComps, normalCatalyst);
        GKInstance diseaseComplex = keyToNewDiseaseComplex.get(complexKey);
        if (diseaseComplex != null) {
            return _createDiseaseCA(diseaseComplex, normalCA); // It is find to duplicate a CatalystActivity
        }
        diseaseComplex = new GKInstance();
        keyToNewDiseaseComplex.put(complexKey, diseaseComplex);
        diseaseComplex.setDbAdaptor(normalCatalyst.getDbAdaptor());
        diseaseComplex.setSchemaClass(normalCatalyst.getSchemClass());
        diseaseComplex.setAttributeValue(ReactomeJavaConstants.hasComponent, diseaseComps);
        diseaseComplex.setAttributeValue(ReactomeJavaConstants.name, builder.toString());
        // Copy other values now
        Collection<SchemaAttribute> atts = normalCatalyst.getSchemaAttributes();
        Set<String> neededNames = Stream.of(ReactomeJavaConstants.cellType,
                                            ReactomeJavaConstants.compartment,
                                            ReactomeJavaConstants.isChimeric,
                                            ReactomeJavaConstants.inferredFrom,
                                            ReactomeJavaConstants.includedLocation,
                                            ReactomeJavaConstants.literatureReference,
                                            ReactomeJavaConstants.relatedSpecies,
                                            ReactomeJavaConstants.species
                                            ).collect(Collectors.toSet());
        // Copy original values
        for (SchemaAttribute att : atts) {
            String attName = att.getName();
            if (!neededNames.contains(attName))
                continue;
            List<GKInstance> values = normalCatalyst.getAttributeValuesList(att);
            if (values == null || values.size() == 0)
                continue;
            diseaseComplex.setAttributeValueNoCheck(att, values);
        }
        // Don't forget the disease slot
        List<GKInstance> disease = efsPE.getAttributeValuesList(ReactomeJavaConstants.disease);
        if (disease != null)
            diseaseComplex.setAttributeValue(ReactomeJavaConstants.disease, disease);
        InstanceDisplayNameGenerator.setDisplayName(diseaseComplex);
        return _createDiseaseCA(diseaseComplex, normalCA);
    }
    
    private String createComplexKey(List<GKInstance> diseaseComps,
                                    GKInstance normalComplex) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (GKInstance comp : diseaseComps) {
            builder.append(comp.getDBID()).append(",");
        }
        // Place compartment and cell type too
        List<GKInstance> compartments = normalComplex.getAttributeValuesList(ReactomeJavaConstants.compartment);
        if (compartments != null && compartments.size() > 0) {
            builder.append("||");
            for (GKInstance comp : compartments)
                builder.append(comp.getDBID()).append(",");
        }
        List<GKInstance> cellTypes = normalComplex.getAttributeValuesList(ReactomeJavaConstants.cellType);
        if (cellTypes != null && cellTypes.size() > 0) {
            builder.append("||");
            for (GKInstance cellType : cellTypes)
                builder.append(cellType.getDBID()).append(",");
        }
        return builder.toString();
    }

    private GKInstance _createDiseaseCA(GKInstance efsPE, GKInstance normalCA)
            throws InvalidAttributeException, Exception, InvalidAttributeValueException {
        GKInstance diseaseCA = new GKInstance();
        diseaseCA.setDbAdaptor(normalCA.getDbAdaptor());
        diseaseCA.setSchemaClass(normalCA.getSchemClass());
        // Need to copy activity
        GKInstance activity = (GKInstance) normalCA.getAttributeValue(ReactomeJavaConstants.activity);
        diseaseCA.setAttributeValue(ReactomeJavaConstants.activity, activity);
        diseaseCA.setAttributeValue(ReactomeJavaConstants.physicalEntity, efsPE);
        InstanceDisplayNameGenerator.setDisplayName(diseaseCA);
        return diseaseCA;
    }
    
    private GKInstance createDiseaseRegulation(GKInstance normalRLE,
                                               GKInstance efsPE) throws Exception {
        GKInstance normalRegulation = (GKInstance) normalRLE.getAttributeValue(ReactomeJavaConstants.regulatedBy);
        GKInstance diseaseRegulation = new GKInstance();
        diseaseRegulation.setDbAdaptor(normalRegulation.getDbAdaptor());
        diseaseRegulation.setSchemaClass(normalRegulation.getSchemClass());
        diseaseRegulation.setAttributeValue(ReactomeJavaConstants.regulator, efsPE);
        InstanceDisplayNameGenerator.setDisplayName(diseaseRegulation);
        return diseaseRegulation;
    }
    
    @Test
    public void checkEFSes() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            "test_gk_central_efs_new",
                                            "root",
                                            "macmysql01");
        Collection<GKInstance> efses = dba.fetchInstancesByClass(ReactomeJavaConstants.EntityFunctionalStatus);
        
//        Set<String> lastNames = new HashSet<>();
//        for (GKInstance efs : efses) {
//            GKInstance ie = getLastAuthor(efs);
//            String name = ie.getDisplayName();
//            lastNames.add(name.split(",")[0]);
//        }
//        lastNames.stream().sorted().forEach(System.out::println);
//        if (true)
//            return;
//        String targetIE = "D'Eustachio";
//        targetIE = "Gillespie";
//        targetIE = "Orlic-Milacic";
//        targetIE = "Rothfels";
//        targetIE = "Shamovsky";
//        targetIE = "Jassal";
//        targetIE = "Matthews,Amarasinghe,Jupe,Varusai";
//        targetIE = null;
//        
//        if (targetIE != null) { // Perform a filtering 
//            Set<String> lastNames = Stream.of(targetIE.split(",")).collect(Collectors.toSet());
//            for (Iterator<GKInstance> it = efses.iterator(); it.hasNext();) {
//                GKInstance efs = it.next();
//                GKInstance ie = getLastAuthor(efs);
//                String ieName = ie.getDisplayName();
//                if (!lastNames.contains(ieName.split(",")[0]))
//                    it.remove();
//            }
//        }
//        
//        System.out.println("EFS_DB_ID\tEFS_DispalyName\tphyscicalEntity_DB_ID\t" +
//                           "physicalEntity_DisplayName\t"
//                           + "Issue\tLast_Author\t" +
//                           "diseaseRLE_DB_ID\tdiseaseRLE_DisplayName\t" +
//                           "diseaseEntity_DB_ID\tdiseaseEntity_DisplayName\t" + 
//                           "normalRLE_DB_ID\tnormalRLE_DisplayName\t" + 
//                           "normalEntity_DB_ID\tnormalEntity_DisplayName\t"  +
//                           "basedOn\tRole");
//        // Perform a complete check
//        for (GKInstance efs : efses) {
//            checkEFS(efs);
//        }
//        
//        System.out.println();
        System.out.println("EFS_DB_ID\tEFS_DispalyName\tphyscicalEntity_DB_ID\t" +
                "physicalEntity_DisplayName\t" +
                "Last_Author\t" +
                "diseaseRLE_DB_ID\tdiseaseRLE_DisplayName\t" +
                "normalRLE_DB_ID\tnormalRLE_DisplayName\t" + 
                "normalEntity_DB_ID\tnormalEntity_DisplayName\t"  +
                "basedOn\tRole\tAction");
        for (GKInstance efs: efses) {
            fillDiseaseEntity(efs);
        }
    }
    
    private void fillDiseaseEntity(GKInstance efs) throws Exception {
        GKInstance pe = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        if (pe == null)
            return;
        Collection<GKInstance> rles = efs.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
        if (rles == null || rles.size() == 0) {
            return;
        }
        GKInstance ie = getLastAuthor(efs);
        for (GKInstance diseaseRLE : rles) {
            GKInstance normalReaction = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.normalReaction);
            // Make sure PE is one of diseaseReaction's participants
            Set<GKInstance> participants = grepLFSReactionParticipants(diseaseRLE);
            if (participants.contains(pe))
                continue;
            NormalEntityMapResult result = guessNormalEntity(pe, 
                                                             normalReaction);
            String action = getFixDiseaseEntityAction(result, pe, diseaseRLE, normalReaction);
            System.out.println(efs.getDBID() + "\t" +
                               efs.getDisplayName() + "\t" +
                               pe.getDBID() + "\t" + 
                               pe.getDisplayName() + "\t" + 
                               ie.getDisplayName() + "\t" + 
                               diseaseRLE.getDBID() + "\t" + 
                               diseaseRLE.getDisplayName() + "\t" + 
                               (normalReaction == null ? "" : normalReaction.getDBID()) + "\t" + 
                               (normalReaction == null ? "" : normalReaction.getDisplayName()) + "\t" + 
                               ((result == null || result.normalEntity == null) ? "" : result.normalEntity.getDBID()) + "\t" + 
                               ((result == null || result.normalEntity == null) ? "" : result.normalEntity.getDisplayName()) + "\t" + 
                               ((result == null || result.reason == null) ? "" : result.reason) + "\t" + 
                               ((result == null || result.role == null) ? "" : result.role) + "\t" +
                               action);
        }
    }
    
    private String getFixDiseaseEntityAction(NormalEntityMapResult result,
                                             GKInstance efsPE,
                                             GKInstance diseaseRLE,
                                             GKInstance normalRLE) throws Exception {
        if (result == null || result.reason == null)
            return "manual fix: unknown";
        // Special case
        // Only complex is handled. EntitySet is not handled: e.g. an EntitySet is a set of complexes
        // and the diseaseEntity is a component of one of complex.
        if (result.role.equals("catalyst") && !result.reason.endsWith("same set of referenceEntity") && normalRLE != null) {
            // Want to check if efsPE is a component of disease's catalyst
            Set<GKInstance> efsRefs = grepReferenceEntitiesForPE(efsPE);
            Set<GKInstance> catalysts = getCatalysts(normalRLE);
            for (GKInstance catalyst : catalysts) {
                if (!catalyst.getSchemClass().isa(ReactomeJavaConstants.Complex))
                    continue;
                List<GKInstance> components = catalyst.getAttributeValuesList(ReactomeJavaConstants.hasComponent);
                for (GKInstance comp : components) {
                    Set<GKInstance> compRefs = grepReferenceEntitiesForPE(comp);
                    if (compRefs.equals(efsRefs)) {
                        GKInstance ca = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.catalystActivity);
                        if (ca == null)
                            return "auto fix: create a CatalystActivity with replaced complex";
                        else {
                            return "manual fix: replace CA's complex component with EFS's PE";
                        }
                    }
                }
            }
        }
        if (!result.reason.endsWith("same set of referenceEntity"))
            return "manual fix: unknown";
        if (result.role.equals("catalyst")) {
            // Check if there is a CA in the diseaseRLE
            GKInstance ca = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.catalystActivity);
            if (ca == null)
                return "auto fix: create a CatalystActivity with EFS's PE";
            else {
                return "manual fix: replace CA's pe with EFS's PE";
            }
        }
        if (result.role.equals("regulator")) {
            GKInstance regulation = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.regulatedBy);
            if (regulation == null)
                return "auto fix: create a Regulation with EFS's PE";
            else
                return "manual fix: replace Regulation's regulator with EFS's PE";
        }
        if (result.role.equals("input")) {
            return "manual fix: add EFS's PE as input or replace input";
        }
        return "manual fix: unknown";
    }
    
    private GKInstance getLastAuthor(GKInstance efs) throws Exception {
        List<GKInstance> ies = efs.getAttributeValuesList(ReactomeJavaConstants.modified);
        for (int i = ies.size() - 1; i <= 0; i--) {
            if (i < 0)
                break;
            GKInstance ie = ies.get(i);
            if (ie.getDisplayName().startsWith("Wu, G"))
                continue;
            return ie;
        }
        GKInstance created = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.created);
        return created;
    }
    
    private void checkEFS(GKInstance efs) throws Exception {
        GKInstance pe = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        GKInstance ie = getLastAuthor(efs);
        if (pe == null) { // Cannot do anything in this case
            System.out.println(efs.getDBID() + "\t" + 
                    efs.getDisplayName() + "\t" + 
                    "\t" + 
                    "\t" + 
                    "No physicalEntity in EFS\t" + ie.getDisplayName());
            return;
        }
        Collection<GKInstance> rles = efs.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
        if (rles == null || rles.size() == 0) {
            System.out.println(efs.getDBID() + "\t" + 
                               efs.getDisplayName() + "\t" + 
                               pe.getDBID() + "\t" + 
                               pe.getDisplayName() + "\t" + 
                               "Not used\t" + ie.getDisplayName());
            return;
        }
        StringBuilder issues = new StringBuilder();
        for (GKInstance diseaseRLE : rles) {
            GKInstance normalReaction = (GKInstance) diseaseRLE.getAttributeValue(ReactomeJavaConstants.normalReaction);
            // Make sure PE is one of diseaseReaction's participants
            Set<GKInstance> participants = grepLFSReactionParticipants(diseaseRLE);
            GKInstance diseaseEntity = null;
            NormalEntityMapResult result = null;
            if (!participants.contains(pe)) {
                issues.append("physicalEntity not a diseaseRLE's participant");
                result = guessNormalEntity(pe, 
                                           normalReaction);
            }
            else {
                diseaseEntity = pe;
                result = guessNormalEntity(diseaseEntity, 
                                           diseaseRLE, 
                                           normalReaction);
            }
            System.out.println(efs.getDBID() + "\t" +
                               efs.getDisplayName() + "\t" +
                               pe.getDBID() + "\t" + 
                               pe.getDisplayName() + "\t" + 
                               issues.toString() + "\t" + 
                               ie.getDisplayName() + "\t" + 
                               diseaseRLE.getDBID() + "\t" + 
                               diseaseRLE.getDisplayName() + "\t" + 
                               (diseaseEntity == null ? "" : diseaseEntity.getDBID()) + "\t" + 
                               (diseaseEntity == null ? "" : diseaseEntity.getDisplayName()) + "\t" +
                               (normalReaction == null ? "" : normalReaction.getDBID()) + "\t" + 
                               (normalReaction == null ? "" : normalReaction.getDisplayName()) + "\t" + 
                               ((result == null || result.normalEntity == null) ? "" : result.normalEntity.getDBID()) + "\t" + 
                               ((result == null || result.normalEntity == null) ? "" : result.normalEntity.getDisplayName()) + "\t" + 
                               ((result == null || result.reason == null) ? "" : result.reason) + "\t" + 
                               ((result == null || result.role == null) ? "" : result.role));
            issues.setLength(0);
        }
    }
    
    /**
     * This method works for dieaseEntity is a participant of the passed diseaseRLE. The guess
     * is based on the role of diseaseEntity in the dieaseRLE and then based on ReferenceEntity.
     * Only exactly match is performed. Otherwise, a manual mapping is needed.
     * @param diseaseEntity
     * @param diseaseRLE
     * @param normalRLE
     * @return
     * @throws Exception
     */
    private NormalEntityMapResult guessNormalEntity(GKInstance diseaseEntity,
                                                    GKInstance diseaseRLE,
                                                    GKInstance normalRLE) throws Exception {
        if (normalRLE == null)
            return null; // There is no need to do this mapping
        NormalEntityMapResult result = new NormalEntityMapResult();
        Set<GKInstance> diseaseEntityRefEnts = grepReferenceEntitiesForPE(diseaseEntity);
        if (diseaseEntityRefEnts.size() == 0) {
            result.reason = "diseaseEntity has no referenceEntity";
            return result; // Cannot do anything
        }
        // Try CAS first since this is the most common case
        checkCAS(diseaseEntity,
                 diseaseRLE,
                 normalRLE, 
                 diseaseEntityRefEnts,
                 result);
        if (result.normalEntity != null)
            return result; // Found the normalEntity from cas
        // Try Regulators
        checkRegulations(diseaseEntity,
                         diseaseRLE,
                         normalRLE,
                         diseaseEntityRefEnts,
                         result);
        if (result.normalEntity != null)
            return result;
        // Try Inputs
        checkInputs(diseaseEntity,
                    diseaseRLE,
                    normalRLE,
                    diseaseEntityRefEnts, 
                    result);
        return result;
    }
    
    /**
     * We will remove ReferenceIsoform in our mapping.
     * @param refEnts
     * @return
     * @throws Exception
     */
    private Set<GKInstance> normalizeReferenceEntities(Set<GKInstance> refEnts) throws Exception {
        Set<GKInstance> rtn = new HashSet<>();
        for (GKInstance ref : refEnts) {
            if (ref.getSchemClass().isa(ReactomeJavaConstants.ReferenceIsoform)) {
                GKInstance parent = (GKInstance) ref.getAttributeValue(ReactomeJavaConstants.isoformParent);
                if (parent == null)
                    throw new IllegalStateException(ref + " doesn't have a parent!");
                rtn.add(parent);
            }
            else
                rtn.add(ref);
        }
        return rtn;
    }
    
    private Set<GKInstance> grepReferenceEntitiesForPE(GKInstance pe) throws Exception {
        Set<GKInstance> refs = InstanceUtilities.grepReferenceEntitiesForPE(pe);
        refs = normalizeReferenceEntities(refs);
        return refs;
    }
    
    private void checkInputs(GKInstance diseaseEntity,
                             GKInstance diseaseRLE,
                             GKInstance normalRLE,
                             Set<GKInstance> diseaseEntityRefEnts,
                             NormalEntityMapResult result) throws Exception {
        List<GKInstance> inputs = diseaseRLE.getAttributeValuesList(ReactomeJavaConstants.input);
        if (!inputs.contains(diseaseEntity))
            return;
        inputs = normalRLE.getAttributeValuesList(ReactomeJavaConstants.input);
        Set<GKInstance> inputSet = new HashSet<>(inputs);
        guessNormalEntity(inputSet,
                          diseaseEntityRefEnts, 
                          "input",
                          result);
    }
    
    /**
     * When a PE is not a diseaseReaction's participant. Try this method.
     * @param pe
     * @param peRefs
     * @param result
     * @throws Exception
     */
    private NormalEntityMapResult guessNormalEntity(GKInstance pe,
                                                    GKInstance normalRLE) throws Exception {
        if (normalRLE == null)
            return null; // There is no need to do this mapping
        NormalEntityMapResult result = new NormalEntityMapResult();
        Set<GKInstance> diseaseEntityRefEnts = grepReferenceEntitiesForPE(pe);
        if (diseaseEntityRefEnts.size() == 0) {
            result.reason = "diseaseEntity has no referenceEntity";
            return result; // Cannot do anything
        }
        // Hard code this case
        if (pe.getDBID().equals(2528982L)) {
            Set<GKInstance> inputs = new HashSet<>(normalRLE.getAttributeValuesList(ReactomeJavaConstants.input));
            getCoveredMatch(inputs, diseaseEntityRefEnts, "input", result);
            result.reason = "hard coded";
            return result;
        }
        Set<GKInstance> catalysts = getCatalysts(normalRLE);
        getCompleteMatch(catalysts, diseaseEntityRefEnts, "catalyst", result);
        if (result.normalEntity != null)
            return result;
        Set<GKInstance> regulators = getRegulators(normalRLE);
        getCompleteMatch(regulators, diseaseEntityRefEnts, "regulator", result);
        if (result.normalEntity != null)
            return result;
        Set<GKInstance> inputs = new HashSet<>(normalRLE.getAttributeValuesList(ReactomeJavaConstants.input));
        getCompleteMatch(inputs, diseaseEntityRefEnts, "input", result);
        if (result.normalEntity != null)
            return result;
        getCoveredMatch(catalysts, diseaseEntityRefEnts, "catalyst", result);
        if (result.normalEntity != null)
            return result;
        getCoveredMatch(regulators, diseaseEntityRefEnts, "regulator", result);
        if (result.normalEntity != null)
            return result;
        getCoveredMatch(inputs, diseaseEntityRefEnts, "input", result);
        if (result.normalEntity != null)
            return result;
        getOverlappedMatch(catalysts, diseaseEntityRefEnts, "catalyst", result);
        if (result.normalEntity != null)
            return result;
        getOverlappedMatch(regulators, diseaseEntityRefEnts, "regulator", result);
        if (result.normalEntity != null)
            return result;
        getOverlappedMatch(inputs, diseaseEntityRefEnts, "input", result);
        if (result.normalEntity != null)
            return result;
        return result;
    }
                                   

    private void guessNormalEntity(Set<GKInstance> normalSet,
                                   Set<GKInstance> diseaseEntityRefEnts,
                                   String role,
                                   NormalEntityMapResult result) throws Exception {
        // The first round search for perfect match
        getCompleteMatch(normalSet, diseaseEntityRefEnts, role, result);
        // The second round for not perfect match
        getCoveredMatch(normalSet, diseaseEntityRefEnts, role, result);
        // The third to find the normal entity covers the most of the disease entity
        getOverlappedMatch(normalSet, diseaseEntityRefEnts, role, result);
    }

    private void getOverlappedMatch(Set<GKInstance> normalSet,
                                    Set<GKInstance> diseaseEntityRefEnts,
                                    String role,
                                    NormalEntityMapResult result)
            throws Exception {
        int maxCount = 0;
        int count = 0;
        GKInstance maxNormalPE = null;
        for (GKInstance input : normalSet) {
            Set<GKInstance> inputRefs = grepReferenceEntitiesForPE(input);
            count = 0;
            for (GKInstance diseaseRef : diseaseEntityRefEnts) {
                if (inputRefs.contains(diseaseRef)) {
                    count ++;
                }
            }
            if (count > maxCount) {
                maxNormalPE = input;
            }
        }
        if (maxNormalPE != null) {
            result.role = role;
            result.reason = "diseaseEntity and normalEntity has the largest overlap of referenceEntity";
            result.normalEntity = maxNormalPE;
        }
    }

    private void getCoveredMatch(Set<GKInstance> normalSet,
                                 Set<GKInstance> diseaseEntityRefEnts,
                                 String role,
                                 NormalEntityMapResult result)
            throws Exception {
        for (GKInstance input : normalSet) {
            Set<GKInstance> inputRefs = grepReferenceEntitiesForPE(input);
            if (inputRefs.containsAll(diseaseEntityRefEnts)) {
                result.role = role;
                result.reason = "normalEntity contains all diseaseEntity's referenceEntity";
                result.normalEntity = input;
                return;
            }
        }
    }

    private void getCompleteMatch(Set<GKInstance> normalSet,
                                  Set<GKInstance> diseaseEntityRefEnts,
                                  String role,
                                  NormalEntityMapResult result) throws Exception {
        for (GKInstance pe : normalSet) {
            Set<GKInstance> inputRefs = grepReferenceEntitiesForPE(pe);
            if (inputRefs.equals(diseaseEntityRefEnts)) {
                result.role = role;
                result.reason = "diseaseEntity and normalEntity has same set of referenceEntity";
                result.normalEntity = pe;
                return;
            }
        }
        // Check for EntitySet. Used only for complete match
        for (GKInstance pe : normalSet) {
            if (!pe.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
                continue;
            Set<GKInstance> members = InstanceUtilities.getContainedInstances(pe,
                                                                              ReactomeJavaConstants.hasMember,
                                                                              ReactomeJavaConstants.hasCandidate);
            for (GKInstance member : members) {
                Set<GKInstance> inputRefs = grepReferenceEntitiesForPE(member);
                if (inputRefs.equals(diseaseEntityRefEnts)) {
                    result.role = role;
                    result.reason = "diseaseEntity and normalEntity's member has same set of referenceEntity";
                    result.normalEntity = pe;
                    return;
                }
            }
        }
    }

    private void checkRegulations(GKInstance diseaseEntity, 
                                  GKInstance diseaseRLE, 
                                  GKInstance normalRLE,
                                  Set<GKInstance> diseaseEntityRefEnts,
                                  NormalEntityMapResult result) throws Exception {
        Set<GKInstance> diseaseParticipants = getRegulators(diseaseRLE);
        if (!diseaseParticipants.contains(diseaseEntity))
            return;
        Set<GKInstance> normalSet = getRegulators(normalRLE);
        guessNormalEntity(normalSet, diseaseEntityRefEnts, "regulator", result);
    }

    private Set<GKInstance> getRegulators(GKInstance rle) throws Exception, InvalidAttributeException {
        Collection<GKInstance> regulations = InstanceUtilities.getRegulations(rle);
        Set<GKInstance> diseaseParticipants = new HashSet<>();
        for (GKInstance regulation : regulations) {
            GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
            if (regulator == null)
                continue;
            diseaseParticipants.add(regulator);
        }
        return diseaseParticipants;
    }

    private void checkCAS(GKInstance diseaseEntity,
                          GKInstance diseaseRLE,
                          GKInstance normalRLE,
                          Set<GKInstance> diseaseEntityRefEnts,
                          NormalEntityMapResult result) throws InvalidAttributeException, Exception {
        Set<GKInstance> diseaseParticipants = getCatalysts(diseaseRLE);
        if (!diseaseParticipants.contains(diseaseEntity))
            return;
        Set<GKInstance> normalSet = getCatalysts(normalRLE);
        guessNormalEntity(normalSet, 
                          diseaseEntityRefEnts, 
                          "catalyst", 
                          result);
    }

    private Set<GKInstance> getCatalysts(GKInstance diseaseRLE) throws InvalidAttributeException, Exception {
        List<GKInstance> cas = diseaseRLE.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        Set<GKInstance> diseaseParticipants = new HashSet<>();
        for (GKInstance ca : cas) {
            GKInstance pe = (GKInstance) ca.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (pe == null)
                continue;
            diseaseParticipants.add(pe);
        }
        return diseaseParticipants;
    }
    
    private Set<GKInstance> grepLFSReactionParticipants(GKInstance reaction) throws Exception {
        Set<GKInstance> set = new HashSet<GKInstance>();
        List<GKInstance> inputs = reaction.getAttributeValuesList(ReactomeJavaConstants.input);
        set.addAll(inputs);
        set.addAll(getCatalysts(reaction));
        set.addAll(getRegulators(reaction));
        return set;
    }
    
    public static void main(String[] args) throws Exception {
        // Need database name and user account
        if (args.length < 4) {
            System.err.println("java -Xmx8G EntityFunctionalStatusUpdate dbName user pass {drug|efs}");
            return;
        }
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            args[0],
                                            args[1],
                                            args[2]);
        if (args[3].equals("drug")) {
            // Split the drugs into their individual subclasses
            DrugConsolidation drugHandler = new DrugConsolidation();
            drugHandler.split(dba);
        }
        else if (args[3].equals("efs")) {
            EntityFunctionalStatusUpdate updater = new EntityFunctionalStatusUpdate();
            updater.update(dba);
        }
    }
    
    private class NormalEntityMapResult {
        GKInstance normalEntity;
        String role;
        String reason;
    }

}
