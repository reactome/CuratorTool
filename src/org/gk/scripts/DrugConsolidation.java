package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.Schema;
import org.gk.schema.SchemaClass;
import org.junit.Test;

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
    
    /**
     * Create DrugActionType instances as shown in the second slide of this file:
     * https://docs.google.com/presentation/d/1NllQLkNdOq2eaGem4vP1IWB-PsQirPF-8iuuRzJcpzM/edit#slide=id.gb953c97efd_0_0 
     * @throws Exception
     */
    @Test
    public void createDrugActionTypes() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                                            "test_gk_central_031621_drug",
                                            "", 
                                            "");
        // This is the list
        String[] names = {
                "Agonist",
                "partial agonist",
                "inverse agonist",
                "Antagonist",
                "allosteric antagonist",
                "Inhibitor",
                "gating inhibitor",
                "antisense inhibitor",
                "Opener",
                "Blocker",
                "Activator",
                "Modulator",
                "Positive modulator",
                "Allosteric modulator",
                "positive allosteric modulator",
                "negative allosteric modulator"
        };
        List<GKInstance> toBeStored = new ArrayList<>();
        GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, false);
        toBeStored.add(ie);
        SchemaClass drugActionTypeCls = dba.getSchema().getClassByName(ReactomeJavaConstants.DrugActionType);
        Map<String, GKInstance> nameToInst = new HashMap<>();
        for (String name : names) {
            GKInstance instance = new GKInstance(drugActionTypeCls);
            instance.setDbAdaptor(dba);
            instance.addAttributeValue(ReactomeJavaConstants.name, name);
            InstanceDisplayNameGenerator.setDisplayName(instance);
            instance.setAttributeValue(ReactomeJavaConstants.created, ie);
            nameToInst.put(name, instance);
            toBeStored.add(instance);
        }
        // Need to create parent-child relationships
        GKInstance parent = nameToInst.get("Agonist");
        GKInstance child = nameToInst.get("partial agonist");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        child = nameToInst.get("inverse agonist");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        parent = nameToInst.get("Antagonist");
        child = nameToInst.get("allosteric antagonist");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        parent = nameToInst.get("Inhibitor");
        child = nameToInst.get("gating inhibitor");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        child = nameToInst.get("antisense inhibitor");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        parent = nameToInst.get("Modulator");
        child = nameToInst.get("Positive modulator");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        child = nameToInst.get("Allosteric modulator");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        parent = child;
        child = nameToInst.get("positive allosteric modulator");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        child = nameToInst.get("negative allosteric modulator");
        child.addAttributeValue(ReactomeJavaConstants.instanceOf, parent);
        System.out.println("Total instances to be stored: " + toBeStored.size());
        try {
            dba.startTransaction();
            for (GKInstance inst : toBeStored) {
                dba.storeInstance(inst);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
            e.printStackTrace();
        }
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
        consolidation.split(dba);
//        consolidation.consolidate(dba);
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
    
    /**
     * Rollback the change made by consolidation so that we have three subclasses:
     * ProteinDrug, RNADrug, and ChemicalDrug. But nothing else will change.
     * @param dba
     * @throws Exception
     */
    public void split(MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> drugs = dba.fetchInstancesByClass(ReactomeJavaConstants.Drug);
        // All values should be loaded before update. Otherwise, values may be lost.
        for (GKInstance drug : drugs)
            dba.fastLoadInstanceAttributeValues(drug);
        Schema schema = dba.getSchema();
        SchemaClass proteinCls = schema.getClassByName(ReactomeJavaConstants.ProteinDrug);
        SchemaClass chemicalCls = schema.getClassByName(ReactomeJavaConstants.ChemicalDrug);
        SchemaClass rnaCls = schema.getClassByName(ReactomeJavaConstants.RNADrug);
        for (GKInstance drug : drugs) {
            GKInstance drugType = (GKInstance) drug.getAttributeValue(ReactomeJavaConstants.drugType);
            if (drugType.getDisplayName().equals("ProteinDrug"))
                drug.setSchemaClass(proteinCls);
            else if (drugType.getDisplayName().equals("ChemicalDrug"))
                drug.setSchemaClass(chemicalCls);
            else if (drugType.getDisplayName().equals("RNADrug"))
                drug.setSchemaClass(rnaCls);
            else
                throw new IllegalStateException(drugType + " is not known!");
        }
        commit(drugs, null, dba);
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
            if (databaseIdentifiers != null) {
                // Store the new instances
                for (GKInstance inst : databaseIdentifiers) {
                    inst.setAttributeValue(ReactomeJavaConstants.created, ie);
                    dba.storeInstance(inst);
                }
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
