package org.gk.scripts;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

/**
 * This class is used to consolidate three drug subclasses, ChemicalDrug, ProteinDrug, and
 * RNADrug into a single Drug class and then use an attribute called drugType to assign
 * drug types for annotated drugs. The class also moves referenceEntity to crossReference
 * after creating new DatabaseObject instance and then move ReferenceTherapeutic from 
 * refereneTherapeutic to referenceEntity.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class DrugConsolidation {
    
    public DrugConsolidation() {
    }
    
    public static void main(String[] args) throws Exception {
        // Need database name and user account
        if (args.length < 3) {
            System.err.println("java -Xmx8G DrugConsolidation dbName user pass");
            return;
        }
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            args[0],
                                            args[1],
                                            args[2]);
        DrugConsolidation consolidation = new DrugConsolidation();
        consolidation.consolidate(dba);
    }
    
    public void consolidate(MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> drugs = loadDrugs(dba);
        System.out.println("Total drugs: " + drugs.size());
        Set<GKInstance> databaseIdentifiers = moveReferenceEntityToCrossReference(drugs, dba);
        System.out.println("Total newly created DatabaseIdentifiers: " + databaseIdentifiers.size());
        // Need to call this before the next method. Otherwise, ReferenceTherapeutic
        // cannot be added to referenceEntity!
        useDrugAsType(drugs, dba); 
        moveReferenceTherapeuticToReferenceEntity(drugs, dba);
        commit(drugs, databaseIdentifiers, dba);
    }
    
    private Collection<GKInstance> loadDrugs(MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> drugs = dba.fetchInstancesByClass(ReactomeJavaConstants.Drug);
        for (GKInstance drug : drugs)
            dba.fastLoadInstanceAttributeValues(drug); // Load everything locally so that we can do update later on
        return drugs;
    }
    
    /**
     * Create new DatabaseIdentifier instances based on ReferenceEntities and then attach
     * them to crossReference.
     * @param drugs
     * @return
     * @throws Exception
     */
    private Set<GKInstance> moveReferenceEntityToCrossReference(Collection<GKInstance> drugs,
                                                                MySQLAdaptor dba) throws Exception {
        SchemaClass diCls = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseIdentifier);
        // Here is a local set to avoid duplication
        Map<String, GKInstance> keyToInstance = new HashMap<>();
        for (GKInstance drug : drugs) {
            GKInstance referenceEntity = (GKInstance) drug.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            if (referenceEntity == null)
                continue;
            GKInstance referenceDb = (GKInstance) referenceEntity.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDb == null)
                throw new IllegalStateException(referenceEntity + " doesn't have a ReferenceDatabase!");
            String identify = (String) referenceEntity.getAttributeValue(ReactomeJavaConstants.identifier);
            if (identify == null)
                throw new IllegalStateException(referenceEntity + " doesn't have an identify!");
            String key = referenceDb + "," + identify;
            GKInstance databaseIdentifier = keyToInstance.get(key);
            if (databaseIdentifier == null) {
                // Try to construct a DatabaseIdentify object
                databaseIdentifier = new GKInstance();
                // We will not assign any DB_ID for new instances
                databaseIdentifier.setSchemaClass(diCls);
                databaseIdentifier.setDbAdaptor(dba);
                databaseIdentifier.setAttributeValue(ReactomeJavaConstants.referenceDatabase, referenceDb);
                databaseIdentifier.setAttributeValue(ReactomeJavaConstants.identifier, identify);
                Set<GKInstance> matched = dba.fetchIdenticalInstances(databaseIdentifier);
                if (matched != null && matched.size() > 0)
                    databaseIdentifier = matched.stream().findFirst().get(); // Used the first matched instance
                else
                    InstanceDisplayNameGenerator.setDisplayName(databaseIdentifier);
                keyToInstance.put(key, databaseIdentifier);
            }
            drug.setAttributeValue(ReactomeJavaConstants.crossReference,
                                   databaseIdentifier);
        }
        return keyToInstance.values().stream().filter(inst -> inst.getDBID() == null).collect(Collectors.toSet());
    }
    
    /**
     * Move referenceTherapeutic to the referenceEntity slot.
     * @param drugs
     * @param dba
     * @throws Exception
     */
    private void moveReferenceTherapeuticToReferenceEntity(Collection<GKInstance> drugs,
                                                           MySQLAdaptor dba) throws Exception {
        for (GKInstance drug : drugs) {
            GKInstance referenceTherapeutic = (GKInstance) drug.getAttributeValue(ReactomeJavaConstants.referenceTherapeutic);
            if (referenceTherapeutic == null)
                continue;
            // The original referenceEntity value should be replaced by referenceTherapeutic.
            drug.setAttributeValue(ReactomeJavaConstants.referenceEntity, referenceTherapeutic);
            drug.setAttributeValue(ReactomeJavaConstants.referenceTherapeutic, null); // Empty it
        }
    }
    
    /**
     * Switch the class for all instances to Drug. Make sure three DrugType instances
     * existing in the database before the following can run!
     * @param drugs
     * @throws Exception
     */
    private void useDrugAsType(Collection<GKInstance> drugs, 
                               MySQLAdaptor dba) throws Exception {
        Map<String, GKInstance> nameToType = loadDrugTypes(dba);
        SchemaClass drugCls = dba.getSchema().getClassByName(ReactomeJavaConstants.Drug);
        for (GKInstance drug : drugs) {
            SchemaClass oldCls = drug.getSchemClass();
            GKInstance drugType = nameToType.get(oldCls.getName());
            if (drugType == null)
                throw new IllegalStateException("Cannot find a DrugType instance for " + oldCls.getName());
            // Assign drugType based on the original type
            drug.setSchemaClass(drugCls);
            drug.setAttributeValue(ReactomeJavaConstants.drugType, drugType);
        }
    }
    
    private Map<String, GKInstance> loadDrugTypes(MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> drugTypes = dba.fetchInstancesByClass(ReactomeJavaConstants.DrugType);
        Map<String, GKInstance> nameToType = new HashMap<>();
        for (GKInstance drugType : drugTypes) {
            nameToType.put(drugType.getDisplayName(), drugType);
        }
        return nameToType;
    }
    
    private void commit(Collection<GKInstance> drugs, 
                        Set<GKInstance> databaseIdentifiers,
                        MySQLAdaptor dba) throws Exception {
        try {
            dba.startTransaction();
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, 
                                                            ScriptUtilities.GUANMING_WU_DB_ID,
                                                            true);
            // Store the new instances
            for (GKInstance inst : databaseIdentifiers) {
                inst.setAttributeValue(ReactomeJavaConstants.created, ie);
                dba.storeInstance(inst);
            }
            // Update drugs
            for (GKInstance drug : drugs) {
                // Need to load all values first so that the update will not lose values
                // This step should be performed as the first step to avoid overwriting any
                // later changes.
//                dba.fastLoadInstanceAttributeValues(drug);
                drug.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstance(drug);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
            throw e;
        }
    }

}
