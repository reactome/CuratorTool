/*
 * Created on Sep 5, 2017
 *
 */
package org.gk.scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.database.util.LiteratureReferenceAttributeAutoFiller;
import org.gk.elv.InstanceCloneHelper;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is modified from org.reactome.nursa.LocalProjectBuilder in the caBigR3 project 
 * to process data from IUPHAR. Currently it is used to automatically create binding reactions
 * between drugs and targets. 
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class IupharDataAnalyzer {
//    private final static String DIR = "/Users/wug/datasets/iuphar/052219/";
//    private final static String DIR = "/Volumes/ssd/datasets/iuphar/032620/";
    private final String DIR = "/Volumes/ssd/datasets/iuphar/040221/";
//    private final String outFile = DIR + "IUPHAR_Reactions_040221.rtpj";
//    private final String outFile = DIR + "IUPHAR_Reactions_042321.rtpj";
    private final String outFile = DIR + "IUPHAR_Reactions_051821.rtpj";
    
    private final static Long CYTOSOL_DB_ID = 70101L;
    private final static Long EXTRACELLULAR_REGION_ID = 984L;
    private final static long PLASMA_MEMBRANE_ID = 876L;
    private final static Long IUPHAR_DB_ID = 9016465L;
    private final static Long PUBCHEM_DB_ID = 5263704L;
    private final static Long HOMO_SAPIENS_ID = 48887L;

    private Map<String, Ligand> idToLigand;
    private List<Long> orderedCompartmentIds;
    
    // Turn this off during development for fast generation
    private boolean needFetchPubMed = true;

    public IupharDataAnalyzer() {
    }

    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_042321",
                                            "",
                                            "");
        return dba;
    }

    private void setUpPersisteneceManager() throws Exception {
        // Create EWAS from RefGeneProduct locally
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        MySQLAdaptor dba = getDBA();
        manager.setActiveMySQLAdaptor(dba);
    }
    
    @Test
    public void checkActions() throws IOException {
        String srcFileName = DIR + "interactions.tsv";
        Set<String> actions = Files.lines(Paths.get(srcFileName))
                                   .skip(1)
                                   .map(line -> {
                                       String[] tokens = line.split("\t");
                                       return tokens[18] + "|" + tokens[19];
                                    })
                                   .collect(Collectors.toSet());
        System.out.println("Total actions: " + actions.size());
        actions.stream().sorted().forEach(System.out::println);
        System.out.println("\nAgonist only:");
        actions.stream().filter(action -> action.endsWith("agonist")).forEach(System.out::println);
    }
    
    @Test
    public void checkTypes() throws IOException {
        String srcFileName = DIR + "ligands.tsv";
        Set<String> types = Files.lines(Paths.get(srcFileName))
                                   .skip(1)
                                   .map(line -> line.split("\t")[3])
                                   .collect(Collectors.toSet());
        System.out.println("Total types: " + types.size());
        types.stream().sorted().forEach(System.out::println);
    }

    @Test
    public void buildCuratorToolProject() throws Exception {
        Set<String> approvedDrugIds = getApprovedDrugIds();
        System.out.println("Total approved drug ids: " + approvedDrugIds.size());
//        approvedDrugIds.forEach(System.out::println);
        
        String srcFileName = DIR + "interactions.tsv";

        Set<String> proteins = new HashSet<>();
        Set<String> ligands = new HashSet<>();
        Map<String, Set<String>> bindToPubmedIds = new HashMap<>();
        Map<String, String> bindToAction = new HashMap<>();
        FileUtilities fu = new FileUtilities();
        fu.setInput(srcFileName);
        String line = fu.readLine();
        int counter = 0;
        int totalLine = 0;
        while ((line = fu.readLine()) != null) {
            totalLine ++;
            String[] tokens = line.split("\t");
            if (!approvedDrugIds.contains(tokens[13]))
                continue;
            // There are 199 cases in the 2021 version using target_ligand_uniprot
            if (tokens[3].length() == 0 && tokens[9].length() == 0)
                continue;
            if (tokens.length < 38) // A new col is added in the 2021 version: approved_drug. Total 38 instead of 37.
                continue;
            if (tokens[37].trim().length() == 0)
                continue;
            if (!tokens[11].equals("Human"))
                continue;
            ligands.add(tokens[13]); // Use IUPHAR ID for drugs
            // In some cases, multiple proteins are listed in one single cell
            String proteinIdsCell = null;
            if (tokens[3].trim().length() > 0)
                proteinIdsCell = tokens[3];
            else if (tokens[9].trim().length() > 0)
                proteinIdsCell = tokens[9];
            if (proteinIdsCell == null)
                throw new IllegalStateException("No target id or target_ligand id in the followling line: \n" + line);
            String[] proteinIds = proteinIdsCell.trim().split("\\|");
            for (String proteinId : proteinIds) {
                proteins.add(proteinId);
                String key = proteinId + "\t" + tokens[13];
                String pubmedId = tokens[37].trim();
                bindToPubmedIds.compute(key, (k, v) -> {
                    if (v == null)
                        v = new HashSet<>();
                    String[] tokens1 = pubmedId.split("\\|");
                    v.addAll(Arrays.asList(tokens1));
                    return v;
                });
                // If action is empty, we will still keep it as a value for further analysis.
                // As of 2021, this is a combination of type and action so that
                // they can be used to map to DrugActionType
                StringBuilder actionType = new StringBuilder();
                if (tokens[18].length() > 0 && !tokens[18].equals("None"))
                    actionType.append(tokens[18]);
                if (tokens[19].length() > 0 && !tokens[19].equals("None")) {
                    if (actionType.length() > 0)
                        actionType.append("|");
                    actionType.append(tokens[19]);
                }
                bindToAction.put(key, actionType.toString());
            }
            counter ++;
        }
        fu.close();
        System.out.println("Total processed lines: " + counter);
        System.out.println("Total lines: " + totalLine);
        System.out.println("Total proteins: " + proteins.size());
        System.out.println("Total ligands: " + ligands.size());
        Set<String> pubmedIds = bindToPubmedIds.values().stream().flatMap(set -> set.stream()).collect(Collectors.toSet());
        System.out.println("Total pubmed ids: " + pubmedIds.size());
        System.out.println("Total bindToAction: " + bindToAction.size());
        System.out.println("Total bindToPubmedIds: " + bindToPubmedIds.size());
//        if (true)
//            return;
        
        setUpPersisteneceManager();
        ScriptUtilities.setUpAttrinuteEditConfig();

        Map<String, GKInstance> proteinToEWAS = createEWASesForProteins(proteins);
        System.out.println("Total geneToEWAS: " + proteinToEWAS.size());

        Map<String, GKInstance> ligandToDrug = createDrugsForLigands(ligands);
        System.out.println("Total ligandToSimpleEntities: " + ligandToDrug.size());

        createBindingReactions(bindToPubmedIds,
                               bindToAction,
                               proteinToEWAS,
                               ligandToDrug);
        assignCompartments();
        mapDefintionToDrugActionType();

        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
//        fileAdaptor.save(DIR + "IUPHAR_Reactions_052219.rtpj");
//        fileAdaptor.save(DIR + "IUPHAR_Reactions_061719.rtpj");
        fileAdaptor.save(outFile);
    }
    
    private void mapDefintionToDrugActionType() throws Exception {
        Collection<GKInstance> reactions = PersistenceManager.getManager().getActiveFileAdaptor().fetchInstancesByClass(ReactomeJavaConstants.Reaction);
        for (GKInstance reaction : reactions) {
            GKInstance drugActionType = fetchDrugActionType(reaction);
            if (drugActionType == null)
                continue;
            reaction.addAttributeValue(ReactomeJavaConstants.reactionType, 
                                       drugActionType);
        }
    }
    
    private GKInstance fetchDrugActionType(GKInstance reaction) throws Exception {
        String actionType = (String) reaction.getAttributeValue(ReactomeJavaConstants.definition);
        if (actionType == null)
            return null;
        // Search based on the following three combination
        List<String> keys = new ArrayList<>();
        String[] tokens = actionType.split("\\|");
        Stream.of(tokens).forEach(token -> keys.add(token.toLowerCase()));
        if (tokens.length == 2) {
            keys.add(tokens[1].toLowerCase() + " " + tokens[0].toLowerCase());
            // This is also possible: e.g. Antibody Binding
            keys.add(tokens[0].toLowerCase() + " " + tokens[1].toLowerCase());
        }
        for (String key : keys) {
            XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
            Collection<GKInstance> c = fileAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.DrugActionType,
                                                                            ReactomeJavaConstants.name,
                                                                            "=",
                                                                            key);
            if (c != null && c.size() > 0)
                return c.stream().findAny().get();
            // Check if we have this reference in database
            MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
            c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.DrugActionType,
                                             ReactomeJavaConstants.name,
                                             "=",
                                             key);
            if (c != null && c.size() > 0) {
                GKInstance dbCopy = c.stream().findAny().get();
                return getInstance(dbCopy.getDBID());
            }
        }
        return null;
    }
    
    private void assignCompartments() throws Exception {
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        Collection<GKInstance> reactions = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.Reaction);
        for (GKInstance reaction : reactions) {
            List<GKInstance> inputs = reaction.getAttributeValuesList(ReactomeJavaConstants.input);
            // Find the protein
            GKInstance protein = null;
            GKInstance drug = null;
            for (GKInstance input : inputs) {
                if (input.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence))
                    protein = input;
                else
                    drug = input;
            }
            List<GKInstance> compartments = protein.getAttributeValuesList(ReactomeJavaConstants.compartment);
            if (compartments == null || compartments.size() == 0)
                continue;
            // Need to clone the list to avoid the compartments be modified
            GKInstance output = (GKInstance) reaction.getAttributeValue(ReactomeJavaConstants.output);
            output.setAttributeValue(ReactomeJavaConstants.compartment, new ArrayList<>(compartments));
            assignCompartmentsToDrug(drug, compartments);
            // Make sure reaction's compartments have both
            List<GKInstance> rxtComps = new ArrayList<>(compartments);
            List<GKInstance> drugComps = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
            for (GKInstance drugComp : drugComps) {
                if (rxtComps.contains(drugComp))
                    continue;
                rxtComps.add(drugComp);
            }
            reaction.setAttributeValue(ReactomeJavaConstants.compartment, rxtComps);
        }
        
        // Check Drug's compartments to make sure it has only one compartment
        validateDrugCompartments(fileAdaptor);
        
        // Need to reset _displayNames because of updates of compartments
        Collection<GKInstance> pes = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.PhysicalEntity);
        for (GKInstance pe : pes) {
            InstanceDisplayNameGenerator.setDisplayName(pe);
        }
    }
    
    private void validateDrugCompartments(XMLFileAdaptor fileAdaptor) throws Exception {
        Set<GKInstance> drugsWithMultipleComps = grepDrugsWithMultipleCompartments(fileAdaptor);
        System.out.println("Total drugs with multiple compartments: " + drugsWithMultipleComps.size());
        // Want to duplicate drugs 
        Map<GKInstance, GKInstance> compToDrug = new HashMap<>();
        for (GKInstance drug : drugsWithMultipleComps) {
            List<GKInstance> comps = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
            if (comps.size() <= 1)
                continue;
            Map<String, List<GKInstance>> attToReferrers = fileAdaptor.getReferrersMap(drug);
            compToDrug.clear();
            for (GKInstance comp : comps) {
                GKInstance clone = cloneDrug(drug, fileAdaptor);
                clone.setAttributeValue(ReactomeJavaConstants.compartment, comp);
                compToDrug.put(comp, clone);
            }
            for (String att : attToReferrers.keySet()) {
                List<GKInstance> referrers = attToReferrers.get(att);
                for (GKInstance referrer : referrers) {
                    List<GKInstance> refComps = referrer.getAttributeValuesList(ReactomeJavaConstants.compartment);
                    GKInstance compDrug = null;
                    for (GKInstance refComp : refComps) {
                        compDrug = compToDrug.get(refComp);
                        if (compDrug != null)
                            break; // Whatever is found
                    }
                    // Perform another test
                    if (compDrug == null) {
                        if (referrer.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) ||
                            referrer.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                            // Use this as the default
                            compDrug = compToDrug.get(getInstance(EXTRACELLULAR_REGION_ID)); 
                        }
                    }
                    if (compDrug == null) {
//                        compDrug = compToDrug.values().stream().findFirst().get();
                        throw new IllegalStateException(referrer + " cannot find a cloned drug for " + drug);
                    }
                    List<GKInstance> list = referrer.getAttributeValuesList(att);
                    int index = list.indexOf(drug);
                    if (index < 0) 
                        throw new IllegalStateException(referrer + " doesnt have the drug in its " + att + ": " + drug);
                    list.set(index, compDrug);
                }
            }
            fileAdaptor.deleteInstance(drug);
        }
        // Another check
        drugsWithMultipleComps = grepDrugsWithMultipleCompartments(fileAdaptor);
        System.out.println("Total drugs with multiple compartments with cleaning: " + drugsWithMultipleComps);
    }
    
    private GKInstance cloneDrug(GKInstance drug, XMLFileAdaptor fileAdaptor) throws Exception {
        InstanceCloneHelper helper = new InstanceCloneHelper();
        return helper.cloneInstance(drug, fileAdaptor);
    }
    
    private Set<GKInstance> grepDrugsWithMultipleCompartments(XMLFileAdaptor fileAdaptor) throws Exception {
        Collection<GKInstance> drugs = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.Drug);
        Set<GKInstance> rtn = new HashSet<>();
        for (GKInstance drug : drugs) {
            List<GKInstance> compartments = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
            if (compartments.size() == 0) {
                System.out.println(drug + " has no compartment!");
            }
            else if (compartments.size() > 1) {
                System.out.println(drug + " has more than one compartment: " + compartments);
                rtn.add(drug);
            }
        }
        return rtn;
    }
    
    private void assignCompartmentsToDrug(GKInstance drug, List<GKInstance> compartments) throws Exception {
        // If there is only one compartment and it's plasma membrane
        if (compartments.size() == 1 && compartments.get(0).getDBID().equals(PLASMA_MEMBRANE_ID)) {
            GKInstance extraCellular = getInstance(EXTRACELLULAR_REGION_ID);
            List<GKInstance> old = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
            if (!old.contains(extraCellular))
                drug.addAttributeValue(ReactomeJavaConstants.compartment, extraCellular);
        }
        else {
            List<GKInstance> old = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
            for (GKInstance comp : compartments) {
                if (old.contains(comp))
                    continue;
                drug.addAttributeValue(ReactomeJavaConstants.compartment, comp);
            }
        }
        // If drug is an entity set, we will need to assign the value to its members.
        if (!drug.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) 
            return;
        // We will assign compartments based on the set
        compartments = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
        List<GKInstance> members = drug.getAttributeValuesList(ReactomeJavaConstants.hasMember);
        for (GKInstance member : members) {
            List<GKInstance> list = member.getAttributeValuesList(ReactomeJavaConstants.compartment);
            for (GKInstance comp : compartments) {
                if (list.contains(comp))
                    continue;
                member.addAttributeValue(ReactomeJavaConstants.compartment, comp);
            }
        }
    }

    private void createBindingReactions(Map<String, Set<String>> bindToPubmedIds,
                                        Map<String, String> bindToAction,
                                        Map<String, GKInstance> geneToEWAS,
                                        Map<String, GKInstance> ligandToDrug) throws Exception {
        Map<String, Map<String, Set<String>>> proteinToActionToDrug = sortProteinsAndDrugs(bindToPubmedIds,
                                                                                           bindToAction);
        _createBindingReactions(bindToPubmedIds, 
                                geneToEWAS, 
                                ligandToDrug, 
                                proteinToActionToDrug);
    }

    private void _createBindingReactions(Map<String, Set<String>> bindToPubmedIds,
                                         Map<String, GKInstance> geneToEWAS,
                                         Map<String, GKInstance> ligandToDrug,
                                         Map<String, Map<String, Set<String>>> proteinToActionToDrug) throws Exception {
        for (String protein : proteinToActionToDrug.keySet()) {
            GKInstance ewas = geneToEWAS.get(protein);
            // There are two cases we cannot find proteins: O60344 and P56856-2.
            // Just skip them
            if (ewas == null)
                continue;
            Map<String, Set<String>> actionToDrugs = proteinToActionToDrug.get(protein);
            for (String action : actionToDrugs.keySet()) {
                Set<String> drugs = actionToDrugs.get(action);
                // Split drugs into two types: ProteinDrugs and ChemicalDrugs so that
                // they will not be mixed into a single EntitySet
                Set<String> proteinDrugs = new HashSet<>();
                Set<String> chemicalDrugs = new HashSet<>();
                for (String drug : drugs) {
                    GKInstance drugInst = ligandToDrug.get(drug);
                    if (drugInst.getSchemClass().isa(ReactomeJavaConstants.ProteinDrug))
                        proteinDrugs.add(drug);
                    else
                        chemicalDrugs.add(drug);
                }
                createBindingReactionForAction(chemicalDrugs, 
                                               action,
                                               protein,
                                               ewas, 
                                               bindToPubmedIds, 
                                               ligandToDrug);
                createBindingReactionForAction(proteinDrugs, 
                                               action, 
                                               protein, 
                                               ewas, 
                                               bindToPubmedIds, 
                                               ligandToDrug);
            }
        }
    }
    
    private void createBindingReactionForAction(Set<String> drugs,
                                                String action,
                                                String protein,
                                                GKInstance ewas,
                                                Map<String, Set<String>> bindToPubmedIds,
                                                Map<String, GKInstance> ligandToDrug) throws Exception {
        if (drugs.size() == 0)
            return; // Do nothing
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        Set<String> pubmedIds = new HashSet<>();
        GKInstance drugInst = null;
        if (drugs.size() == 1) {
            String drug = drugs.stream().findFirst().get();
            drugInst = ligandToDrug.get(drug);
            if (drugInst != null) {
                String key = protein + "\t" + drug;
                if (bindToPubmedIds.get(key) != null)
                    pubmedIds.addAll(bindToPubmedIds.get(key));
            }
        }
        else if (drugs.size() > 1) { // Create an EntitySet to wrap all drugs together.
            List<GKInstance> drugInstList = new ArrayList<>();
            for (String drug : drugs) {
                GKInstance tmpInst = ligandToDrug.get(drug);
                if (tmpInst != null) {
                    drugInstList.add(tmpInst);
                    String key = protein + "\t" + drug;
                    if (bindToPubmedIds.get(key) != null)
                        pubmedIds.addAll(bindToPubmedIds.get(key));
                }
            }
            drugInst = createEntitySetForDrugs(drugInstList, 
                                               action,
                                               ewas,
                                               fileAdaptor);
        }
        if (drugInst == null)
            return;
        GKInstance complex = createComplex(fileAdaptor, ewas, drugInst);
        GKInstance reaction = createReaction(fileAdaptor, 
                                             ewas, 
                                             drugInst, 
                                             complex,
                                             action);
        GKInstance homoSapiens = getInstance(HOMO_SAPIENS_ID);
        reaction.addAttributeValue(ReactomeJavaConstants.species, homoSapiens);
//        if (true)
//            return;
        for (String pubmedId : pubmedIds) {
            if (pubmedId == null || pubmedId.trim().length() == 0)
                continue;
            GKInstance lr = getLiteratureReference(pubmedId);
            reaction.addAttributeValue(ReactomeJavaConstants.literatureReference, lr);
        }
        
        // Check if this reaction is in the database already
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
        Collection<GKInstance> matched = dba.fetchIdenticalInstances(reaction);
        if (matched != null && matched.size() > 0) {
            System.out.println(reaction + " is in gk_central.");
        }
    }
    
    private GKInstance createEntitySetForDrugs(List<GKInstance> drugs, 
                                               String action,
                                               GKInstance protein,
                                               XMLFileAdaptor fileAdaptor) throws Exception {
        if (drugs.size() == 1)
            return drugs.stream().findFirst().get();
        GKInstance entitySet = fileAdaptor.createNewInstance(ReactomeJavaConstants.DefinedSet);
        Set<GKInstance> compartments = new HashSet<>();
        String type = null;
        for (GKInstance drug : drugs) {
            entitySet.addAttributeValue(ReactomeJavaConstants.hasMember, drug);
            compartments.addAll(drug.getAttributeValuesList(ReactomeJavaConstants.compartment));
            if (type == null) {
                if (drug.getSchemClass().isa(ReactomeJavaConstants.ProteinDrug))
                    type = "protein";
                else
                    type = "chemical";
            }
        }
        for (GKInstance compartment : compartments) {
            entitySet.addAttributeValue(ReactomeJavaConstants.compartment, compartment);
        }
        // Just a temp name
        String proteinName = (String) protein.getAttributeValue(ReactomeJavaConstants.name);
        String name = null;
        if (action != null)
            name = proteinName + " " + action + " " + type +  " drugs";
        else 
            name = proteinName + " " + type + " drugs";
        entitySet.addAttributeValue(ReactomeJavaConstants.name, name);
        InstanceDisplayNameGenerator.setDisplayName(entitySet);
        return entitySet;
    }

    private Map<String, Map<String, Set<String>>> sortProteinsAndDrugs(Map<String, Set<String>> bindToPubmedIds,
                                                                       Map<String, String> bindToAction) {
        Map<String, String> actionMap = getActionMergeMap();
        // Sort drugs based on their targets so that we can put drugs targeting the same target
        // together into sets.
        Map<String, Map<String, Set<String>>> proteinToActionToDrug = new HashMap<>();
        for (String key : bindToPubmedIds.keySet()) {
            String action = bindToAction.get(key);
            if (actionMap.containsKey(action))
                action = actionMap.get(action);
            String[] tokens = key.split("\t");
            Map<String, Set<String>> actionToDrug = proteinToActionToDrug.get(tokens[0]);
            if (actionToDrug == null) {
                actionToDrug = new HashMap<>();
                proteinToActionToDrug.put(tokens[0], actionToDrug);
            }
            Set<String> drugs = actionToDrug.get(action);
            if (drugs == null) {
                drugs = new HashSet<>();
                actionToDrug.put(action, drugs);
            }
            drugs.add(tokens[1]);
        }
        return proteinToActionToDrug;
    }
    
    /**
     * This is a hard-coded map so that some quantitative type can be mapped
     * to type since we cannot distinguish them.
     * @return
     */
    private Map<String, String> getActionMergeMap() {
        Map<String, String> rtn = new HashMap<>();
        // So far only these three
        rtn.put("Full agonist", "Agonist");
        rtn.put("Partial agonist", "Agonist");
        return rtn;
    }

    private GKInstance getLiteratureReference(String pubmedId) throws Exception {
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        Collection<GKInstance> c = fileAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.LiteratureReference,
                                                                        ReactomeJavaConstants.pubMedIdentifier,
                                                                        "=",
                                                                        pubmedId);
        if (c != null && c.size() > 0)
            return c.stream().findAny().get();
        // Check if we have this reference in database
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
        c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.LiteratureReference,
                                         ReactomeJavaConstants.pubMedIdentifier,
                                         "=",
                                         pubmedId);
        if (c != null && c.size() > 0) {
            GKInstance dbCopy = c.stream().findAny().get();
            return getInstance(dbCopy.getDBID());
        }
        // If we cannot find it in the database, we need to create one
        GKInstance lr = fileAdaptor.createNewInstance(ReactomeJavaConstants.LiteratureReference);
        lr.setAttributeValue(ReactomeJavaConstants.pubMedIdentifier, new Integer(pubmedId));
        // Want to fetch all information from pubmed
        if (needFetchPubMed) {
            System.out.println("Query pubmed for " + pubmedId + "...");
            LiteratureReferenceAttributeAutoFiller fetcher = new LiteratureReferenceAttributeAutoFiller();
            fetcher.setPersistenceAdaptor(fileAdaptor); // Local should be used
            fetcher.process(lr, null);
        }
        InstanceDisplayNameGenerator.setDisplayName(lr);
        return lr;
    }

    private GKInstance createReaction(XMLFileAdaptor fileAdaptor, 
                                      GKInstance ewas, 
                                      GKInstance se,
                                      GKInstance complex,
                                      String action) throws Exception {
        GKInstance reaction = fileAdaptor.createNewInstance(ReactomeJavaConstants.Reaction);
        reaction.addAttributeValue(ReactomeJavaConstants.input, ewas);
        reaction.addAttributeValue(ReactomeJavaConstants.input, se);
        reaction.addAttributeValue(ReactomeJavaConstants.output, complex);
        String ewasName = (String) ewas.getAttributeValue(ReactomeJavaConstants.name);
        String seName = (String) se.getAttributeValue(ReactomeJavaConstants.name);
        String reactionName = "Binding of " + ewasName + " and " + seName;
        if (action != null && action.length() > 0) {
            reaction.addAttributeValue(ReactomeJavaConstants.definition, action);
            reactionName += " as " + action;
        }
        reaction.addAttributeValue(ReactomeJavaConstants.name, reactionName);
        InstanceDisplayNameGenerator.setDisplayName(reaction);
        return reaction;
    }

    private GKInstance createComplex(XMLFileAdaptor fileAdaptor, GKInstance ewas, GKInstance se)
            throws Exception {
        if (ewas == null)
            throw new IllegalArgumentException("EWAS should not be null");
        if (se == null)
            throw new IllegalArgumentException("SE should not be null");
        GKInstance complex = fileAdaptor.createNewInstance(ReactomeJavaConstants.Complex);
        complex.addAttributeValue(ReactomeJavaConstants.hasComponent, ewas);
        complex.addAttributeValue(ReactomeJavaConstants.hasComponent, se);
        String ewasName = (String) ewas.getAttributeValue(ReactomeJavaConstants.name);
        String seName = (String) se.getAttributeValue(ReactomeJavaConstants.name);
        String complexName = ewasName + ":" + seName;
        complex.setAttributeValue(ReactomeJavaConstants.name, complexName);
        InstanceDisplayNameGenerator.setDisplayName(complex);
        return complex;
    }

    /**
     * We want to create ReferenceMolecule instances if none can be found.
     * @return
     * @throws Exception
     */
    private GKInstance createReferenceTherapeutic(String ligandId) throws Exception {
        if (idToLigand == null)
            idToLigand = loadLigands();
        Ligand ligand = idToLigand.get(ligandId);
        if (ligand == null)
            throw new IllegalStateException("Cannot find loaded ligand for " + ligandId);

        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        GKInstance refTherapeutic = fileAdaptor.createNewInstance(ReactomeJavaConstants.ReferenceTherapeutic);
        GKInstance iuphar = getInstance(IUPHAR_DB_ID);
        refTherapeutic.setAttributeValue(ReactomeJavaConstants.referenceDatabase, iuphar);
        refTherapeutic.setAttributeValue(ReactomeJavaConstants.identifier, ligandId);
        // Copy values from ligand to refTherapeutic
        refTherapeutic.setAttributeValue(ReactomeJavaConstants.name, ligand.name);
        if (ligand.synonyms != null) {
            for (String synonym : ligand.synonyms)
                refTherapeutic.addAttributeValue(ReactomeJavaConstants.name, synonym);
        }
        refTherapeutic.setAttributeValue(ReactomeJavaConstants.approved, ligand.approved);
        refTherapeutic.setAttributeValue(ReactomeJavaConstants.inn, ligand.inn);
        refTherapeutic.setAttributeValue(ReactomeJavaConstants.type, ligand.type);
        InstanceDisplayNameGenerator.setDisplayName(refTherapeutic);

        // If there is pubchem id, we want to create a crossreference
        if (ligand.pubchemSid != null) {
            GKInstance pubchem = getInstance(PUBCHEM_DB_ID);
            GKInstance crossReference = fileAdaptor.createNewInstance(ReactomeJavaConstants.DatabaseIdentifier);
            crossReference.setAttributeValue(ReactomeJavaConstants.referenceDatabase, pubchem);
            crossReference.setAttributeValue(ReactomeJavaConstants.identifier, ligand.pubchemSid);
            InstanceDisplayNameGenerator.setDisplayName(crossReference);
            refTherapeutic.setAttributeValue(ReactomeJavaConstants.crossReference, crossReference);
        }

        return refTherapeutic;
    }

    private Map<String, GKInstance> createDrugsForLigands(Set<String> ligands) throws Exception {
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
        Map<String, GKInstance> ligandToRefTher = new HashMap<>();
        for (String ligand : ligands) {
            Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceTherapeutic,
                                                                    ReactomeJavaConstants.identifier,
                                                                    "=",
                                                                    ligand);
            GKInstance refTherapeutic = null;
            if (c == null || c.size() == 0) {
                refTherapeutic = createReferenceTherapeutic(ligand);
            }
            else {
                refTherapeutic = c.stream().findAny().get();
            }
            ligandToRefTher.put(ligand, refTherapeutic);
        }
        System.out.println("Total ReferenceTherapeutics: " + ligandToRefTher.size());

//        GKInstance cytosol = getInstance(CYTOSOL_DB_ID);
        Map<String, GKInstance> ligandToDrug = new HashMap<>();
        PersistenceManager manager = PersistenceManager.getManager();
        XMLFileAdaptor fileAdaptor = manager.getActiveFileAdaptor();
        for (String ligand : ligandToRefTher.keySet()) {
            GKInstance refMolecule = ligandToRefTher.get(ligand);
            GKInstance drugInst = getDrugFromDB(refMolecule);
            if (drugInst == null) { // Need to create a local copy
                GKInstance local = getLocalCopyInFull(refMolecule);
                // Create Drug
                // ProteinDrug for Peptide and Antibody
                Ligand ligandObj = idToLigand.get(ligand);
                if (ligandObj.type.equals("Antibody") || ligandObj.type.equals("Peptide"))
                    drugInst = fileAdaptor.createNewInstance(ReactomeJavaConstants.ProteinDrug);
                else
                    drugInst = fileAdaptor.createNewInstance(ReactomeJavaConstants.ChemicalDrug);
                // We will handle drug compartments later on
//                drugInst.setAttributeValue(ReactomeJavaConstants.compartment, cytosol);
                copyAttrinutesFromRefTherToDrug(local, drugInst);
                InstanceDisplayNameGenerator.setDisplayName(drugInst);
            }
            ligandToDrug.put(ligand, drugInst);
        }
        return ligandToDrug;
    }

    private void copyAttrinutesFromRefTherToDrug(GKInstance refTher, GKInstance drug) throws Exception {
        drug.setAttributeValue(ReactomeJavaConstants.referenceEntity, refTher);
        List<String> names = refTher.getAttributeValuesList(ReactomeJavaConstants.name);
        drug.setAttributeValue(ReactomeJavaConstants.name, names);
    }

    private Map<String, GKInstance> createEWASesForProteins(Set<String> proteins) throws Exception {
        Set<GKInstance> refGeneProducts = new HashSet<>();
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
        for (String protein : proteins) {
            Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct,
                                                                    ReactomeJavaConstants.identifier,
                                                                    "=",
                                                                    protein);
            if (c == null || c.size() == 0)
                System.err.println("Cannot find " + protein);
            else {
                GKInstance refGeneProd = c.stream().filter(inst -> !inst.getSchemClass().isa(ReactomeJavaConstants.ReferenceIsoform))
                        .findAny().get();
                refGeneProducts.add(refGeneProd);
            }
        }
        System.out.println("Total ReferenceGeneProducts: " + refGeneProducts.size());

        // Want to assign cytosol to all objects
        GKInstance cytol = getInstance(CYTOSOL_DB_ID);
        PersistenceManager manager = PersistenceManager.getManager();
        XMLFileAdaptor fileAdaptor = manager.getActiveFileAdaptor();
        Map<String, GKInstance> proteinToEWAS = new HashMap<>();
        for (GKInstance refGeneProd : refGeneProducts) {
            GKInstance ewas = getEWASFromDB(refGeneProd);
            if (ewas == null) {
                GKInstance local = getLocalCopyInFull(refGeneProd);
                // Create EWASes
                ewas = fileAdaptor.createNewInstance(ReactomeJavaConstants.EntityWithAccessionedSequence);
                ewas.setAttributeValue(ReactomeJavaConstants.compartment, cytol);
                InstanceUtilities.copyAttributesFromRefPepSeqToEwas(ewas, local);
                InstanceDisplayNameGenerator.setDisplayName(ewas);
            }
            String protein = (String) refGeneProd.getAttributeValue(ReactomeJavaConstants.identifier);
            proteinToEWAS.put(protein, ewas);
        }
        return proteinToEWAS;
    }
    
    private GKInstance getEWASFromDB(GKInstance refEntity) throws Exception {
        Collection<GKInstance> ewases = refEntity.getReferers(ReactomeJavaConstants.referenceEntity);
        if (ewases == null || ewases.size() == 0)
            return null;
        List<Long> compartmentIds = getOrderedCompartmentIds();
        for (Long compartmentId : compartmentIds) {
            for (GKInstance ewas : ewases) {
                // We want to have a vanilla copy of EWAS without any modification
                GKInstance modification = (GKInstance) ewas.getAttributeValue(ReactomeJavaConstants.hasModifiedResidue);
                if (modification != null)
                    continue;
                GKInstance compartment = (GKInstance) ewas.getAttributeValue(ReactomeJavaConstants.compartment);
                if (compartment == null)
                    continue;
                if (compartment.getDBID().equals(compartmentId)) {
                    return getLocalCopyInFull(ewas);
                }
            }
        }
        return null;
    }
    
    private GKInstance getDrugFromDB(GKInstance refEntity) throws Exception {
        Collection<GKInstance> drugs = refEntity.getReferers(ReactomeJavaConstants.referenceEntity);
        if (drugs == null || drugs.size() == 0)
            return null;
        List<Long> compartmentIds = getOrderedCompartmentIds();
        for (Long compartmentId : compartmentIds) {
            for (GKInstance drug : drugs) {
                GKInstance compartment = (GKInstance) drug.getAttributeValue(ReactomeJavaConstants.compartment);
                if (compartment == null)
                    continue;
                if (compartment.getDBID().equals(compartmentId)) {
                    return getLocalCopyInFull(drug);
                }
            }
        }
        return null;
    }
    
    private GKInstance getLocalCopyInFull(GKInstance dbInst) throws Exception {
        GKInstance rtn = PersistenceManager.getManager().getLocalReference(dbInst);
        PersistenceManager.getManager().updateLocalFromDB(rtn, dbInst);
        return rtn;
    }

    private GKInstance getInstance(Long dbId) throws Exception {
        GKInstance localCopy = PersistenceManager.getManager().getActiveFileAdaptor().fetchInstance(dbId);
        if (localCopy != null)
            return localCopy;
        PersistenceManager manager = PersistenceManager.getManager();
        GKInstance dbCopy = manager.getActiveMySQLAdaptor().fetchInstance(dbId);
        localCopy = manager.getLocalReference(dbCopy);
        manager.updateLocalFromDB(localCopy, dbCopy);
        return localCopy;
    }
    
    private Set<String> getApprovedDrugIds() throws IOException {
        Map<String, Ligand> idToLigand = loadLigands();
        Set<String> approvedIds = idToLigand.values()
                                            .stream()
                                            .filter(ligand -> ligand.approved)
                                            .map(ligand -> ligand.id)
                                            .collect(Collectors.toSet());
        return approvedIds;
    }

    private Map<String, Ligand> loadLigands() throws IOException {
        String fileName = DIR + "ligands.tsv";
        try (Stream<String> lines = Files.lines(Paths.get(fileName))) {
            Map<String, Ligand> idToLigand = new HashMap<>();
            lines.skip(1)
            .map(line -> line.split("\t"))
            .forEach(tokens -> {
                tokens = removeQuotations(tokens);
                Ligand ligand = new Ligand();
                ligand.id = tokens[0];
                ligand.name = tokens[1];
                ligand.type = tokens[3].length() > 0 ? tokens[3] : null;
                ligand.approved = tokens[4].equals("yes") ? Boolean.TRUE : Boolean.FALSE;
                ligand.pubchemSid = tokens[8].length() > 0 ? tokens[8] : null;
                ligand.inn = tokens[12].length() > 0 ? tokens[12] : null;
                if (tokens[13].length() > 0)
                    ligand.setSynonyms(tokens[13]);
                idToLigand.put(ligand.id, ligand);
            });
            return idToLigand;
        }
    }
    
    private String[] removeQuotations(String[] tokens) {
        String[] rtn = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            rtn[i] = tokens[i].substring(1, tokens[i].length() - 1);
        }
        return rtn;
    }
    
    private List<Long> getOrderedCompartmentIds() {
        if (orderedCompartmentIds != null)
            return orderedCompartmentIds;
        // The following lists all compartments used by drugs.
        // They will be used to search for existing drugs and EWASes
        //      [Compartment:984] extracellular region
        //      [Compartment:1222638] periplasmic space
        //      [Compartment: 876] plasma membrane // This will be used by EWAS only
        //      [Compartment:70101] cytosol
        //      [Compartment:5460] mitochondrial matrix
        //      [Compartment:17957] endoplasmic reticulum lumen
        //      [Compartment:7660] nucleoplasm
        orderedCompartmentIds = new ArrayList<>();
        orderedCompartmentIds.add(984L);
        orderedCompartmentIds.add(1222638L);
        orderedCompartmentIds.add(876L);
        orderedCompartmentIds.add(70101L);
        orderedCompartmentIds.add(5460L);
        orderedCompartmentIds.add(17957L);
        orderedCompartmentIds.add(7660L);
        return orderedCompartmentIds;
    }
    
    @Test
    public void generateUsedCompartmentsInDrugs() throws Exception {
        MySQLAdaptor dba = getDBA();
        Collection<GKInstance> drugs = dba.fetchInstancesByClass(ReactomeJavaConstants.Drug);
        Set<GKInstance> compartments = new HashSet<>();
        for (GKInstance drug : drugs) {
            List<GKInstance> list = drug.getAttributeValuesList(ReactomeJavaConstants.compartment);
            compartments.addAll(list);
        }
        for (GKInstance compartment : compartments) 
            System.out.println(compartment);
    }

    private class Ligand {
        String id;
        String name;
        String type;
        Boolean approved;
        String pubchemSid;
        String inn;
        List<String> synonyms;

        public Ligand() {
        }

        public void setSynonyms(String token) {
            synonyms = Arrays.asList(token.split("\\|"));
        }
    }

}
