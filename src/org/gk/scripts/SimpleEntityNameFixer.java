package org.gk.scripts;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * During an ChEBI update, the script accidentally adjusted the order of manually
 * sorted names, which were then used by orthologous script to update _displayNames
 * unnecessary during release 66. This class is used to fix this _displayName issue. 
 * There are cases:
 * 1). For gk_central and test_slice_66, only the name slot has been updated. So this
 * class is trying to copy the original names directly from a gk_central backup before
 * chEBI update
 * 2). For test_reactome_66, _displayName has been updated too. For this database, both
 * _displayName and name slots are updated.
 * However, the class doing the above cases in a single fix: If the display name is 
 * different, copy it. Otherwise, leave it. Before copying, have to make sure the curator
 * doesn't make any changes to the name by comparing Set of names to make sure they are 
 * the same.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class SimpleEntityNameFixer {
    private MySQLAdaptor targetDBA;
    private String targetDB = "gk_central_091218";
    private String sourceDB = "gk_central_before_chebi_update";
    private String user = "";
    private String pwd = "";
    
    public SimpleEntityNameFixer() {
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("java -jar SimpleEntityNameFixer.jar targetDB sourceDB dbUser dbPwd {check|fix|drug}");
            System.exit(1);
        }
        SimpleEntityNameFixer fixer = new SimpleEntityNameFixer();
        fixer.targetDB = args[0];
        fixer.sourceDB = args[1];
        fixer.user = args[2];
        fixer.pwd = args[3];
        if (args[4].equals("check"))
            fixer.checkInstancesForFix();
        else if (args[4].equals("fix"))
            fixer.fixNames();
        else if (args[4].equals("drug"))
            fixer.fixDrugNames();
    }
    
    @Test
    public void fixDrugNames() throws Exception {
        MySQLAdaptor targetDBA = getTargetDBA();
        
        GKInstance defaultIE = ScriptUtilities.createDefaultIE(targetDBA, ScriptUtilities.GUANMING_WU_DB_ID, false);
        defaultIE.setAttributeValue(ReactomeJavaConstants.note, "Fix drug _displayNames caused by a buggy Perl script");
        
        String fileName = "DrugDisplayName_Fix_" + targetDBA.getDBName() + "_" + ScriptUtilities.getDate() + ".txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine("DB_ID\tDisplayName\tClass");
        
        Collection<GKInstance> drugs = targetDBA.fetchInstancesByClass(ReactomeJavaConstants.Drug);
        System.out.println("Total drugs: " + drugs.size());
        Set<GKInstance> toBeFixed = new HashSet<>();
        for (GKInstance drug : drugs) {
            String newDisplayName = InstanceDisplayNameGenerator.generateDisplayName(drug);
            if (newDisplayName.equals(drug.getDisplayName()))
                continue;
            drug.setDisplayName(newDisplayName);
            drug.getAttributeValuesList(ReactomeJavaConstants.modified);
            drug.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
            toBeFixed.add(drug);
            fu.printLine(drug.getDBID() + "\t" + 
                         drug.getDisplayName() + "\t" + 
                         drug.getSchemClass().getName());
        }
        fu.close();
        System.out.println("To be fixed: " + toBeFixed.size());
        try {
            if (targetDBA.supportsTransactions())
                targetDBA.startTransaction();
            targetDBA.storeInstance(defaultIE);
            for (GKInstance drug : toBeFixed) {
                targetDBA.updateInstanceAttribute(drug, ReactomeJavaConstants._displayName);
                targetDBA.updateInstanceAttribute(drug, ReactomeJavaConstants.modified);
            }
            if (targetDBA.supportsTransactions())
                targetDBA.commit();
        }
        catch(Exception e) {
            if (targetDBA.supportsTransactions())
                targetDBA.rollback();
            throw e;
        }
    }
    
    /**
     * Use this method to fix names and _displayNames caused by the buggy ChEBI script.
     * @throws Exception
     */
    @Test
    public void fixNames() throws Exception {
        MySQLAdaptor targeDBA = getTargetDBA();
        // Instances to be used
        GKInstance defaultIE = ScriptUtilities.createDefaultIE(targeDBA,
                                                               ScriptUtilities.GUANMING_WU_DB_ID,
                                                               false); // We will save it later on
        Set<GKInstance> updateInstances = new HashSet<>();
        Collection<GKInstance> touchedInstances = getTouchedInstances();
        // We will work with PhysicalEntities only
        MySQLAdaptor sourceDBA = getSourceDBA();
        String fileName = "UpdateInstances_" + targeDBA.getDBName() + ".txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine("DB_ID\tDisplayName\tClass\tFixedAttributes");
        for (GKInstance targetInst : touchedInstances) {
            if (!targetInst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity)) {
                continue;
            }
            GKInstance sourceInst = sourceDBA.fetchInstance(targetInst.getDBID());
            List<String> sourceNames = sourceInst.getAttributeValuesList(ReactomeJavaConstants.name);
            String sourceDisplayName = sourceInst.getDisplayName();
            List<String> targetNames = targetInst.getAttributeValuesList(ReactomeJavaConstants.name);
            String targetDisplayName = targetInst.getDisplayName();
            if (sourceNames.equals(targetNames)) {
                if (!sourceDisplayName.equals(targetDisplayName)) {
                    // May be caused by new curation
                    String tmp = InstanceDisplayNameGenerator.generateDisplayName(targetInst);
                    if (!tmp.equals(targetDisplayName))
                        throw new IllegalStateException(targetInst + " has the same names but different _displayName!");
                }
                continue;
            }
            // Just copy all new names at the name
            for (String targetName : targetNames) {
                if (sourceNames.contains(targetName))
                    continue;
                sourceNames.add(targetName);
            }
            // Starting fix
            String fixedAtts = "name";
            targetInst.setAttributeValue(ReactomeJavaConstants.name, sourceNames);
            String displayName = InstanceDisplayNameGenerator.generateDisplayName(targetInst);
            if (!displayName.equals(targetDisplayName)) {
                targetInst.setDisplayName(displayName);
                fixedAtts += ".displayName";
            }
            targetInst.getAttributeValuesList(ReactomeJavaConstants.modified);
            targetInst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
            
            updateInstances.add(targetInst);
            fu.printLine(targetInst.getDBID() + "\t" + 
                               targetInst.getDisplayName() + "\t" + 
                               targetInst.getSchemClass().getName() + "\t" +
                               fixedAtts + "\t");
        }
        System.out.println("Total instances to be fixed: " + updateInstances.size());
        fu.close();
        try {
            if (targetDBA.supportsTransactions())
                targetDBA.startTransaction();
            targetDBA.storeInstance(defaultIE);
            for (GKInstance inst : updateInstances) {
                // Some of instances may not need to update _displayName.
                // However, this is fast. Just run it!
                targetDBA.updateInstanceAttribute(inst, ReactomeJavaConstants._displayName);
                targetDBA.updateInstanceAttribute(inst, ReactomeJavaConstants.name);
                targetDBA.updateInstanceAttribute(inst, ReactomeJavaConstants.modified);
            }
            if (targetDBA.supportsTransactions())
                targetDBA.commit();
        }
        catch(Exception e) {
            if (targetDBA.supportsTransactions())
                targetDBA.rollback();
        }
    }
    
    /**
     * Check the total number of the correct names and _displayName from the gk_central backup before
     * applying the ChEBI update so that we don't need to install the backup database.
     * @throws Exception
     */
    @Test
    public void checkInstancesForFix() throws Exception {
        Collection<GKInstance> touchedInstances = getTouchedInstances();
        // We will work with PhysicalEntities only
        MySQLAdaptor sourceDBA = getSourceDBA();
        int total = 0;
        for (GKInstance targetInst : touchedInstances) {
            if (!targetInst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity)) {
                continue;
            }
            GKInstance sourceInst = sourceDBA.fetchInstance(targetInst.getDBID());
            List<String> sourceNames = sourceInst.getAttributeValuesList(ReactomeJavaConstants.name);
            String sourceDisplayName = sourceInst.getDisplayName();
            List<String> targetNames = targetInst.getAttributeValuesList(ReactomeJavaConstants.name);
            String targetDisplayName = targetInst.getDisplayName();
            if (sourceNames.equals(targetNames)) {
                if (!sourceDisplayName.equals(targetDisplayName)) {
                    // May be caused by new curation
                    String tmp = InstanceDisplayNameGenerator.generateDisplayName(targetInst);
                    if (!tmp.equals(targetDisplayName))
                        throw new IllegalStateException(targetInst + " has the same names but different _displayName!");
                }
                continue;
            }
            // Make sure they are the same but in different order
//            Set<String> targetNameSet = new HashSet<>(targetNames);
//            Set<String> sourceNameSet = new HashSet<>(sourceNames);
//            if (!targetNameSet.equals(sourceNameSet)) {
//                throw new IllegalStateException(targetInst + " has been edited in the name slot!");
//            }
            total ++;
        }
        System.out.println("Total instances to be fixed: " + total);
    }
    
    /**
     * This is to get the list of SimpleEntities that have been touched by the chEBI
     * update script based on the flag created by the update script.
     * @throws Exception
     */
    @Test
    public void checkSimpleEntities() throws Exception {
        Collection<GKInstance> touchedInstances = getTouchedInstances();
        // Sort the modified instances by class names
        Map<String, Set<GKInstance>> clsToInstances = new HashMap<>();
        for (GKInstance inst : touchedInstances) {
            clsToInstances.compute(inst.getSchemClass().getName(), (key, set) -> {
                if (set == null)
                    set = new HashSet<>();
                set.add(inst);
                return set;
            });
        }
        clsToInstances.forEach((key, set) -> {
            System.out.println(key + ": " + set.size());
            if (set.size() < 500)
                set.forEach(inst -> System.out.println(inst));
        });
    }
    
    private Collection<GKInstance> getTouchedInstances() throws Exception {
        Long dbId = 9616752L; // DB_ID for IEs inserted by chEBI update script
        MySQLAdaptor targetDBA = getTargetDBA();
        GKInstance ie = targetDBA.fetchInstance(dbId);
        Collection<GKInstance> modifiedInstances = ie.getReferers(ReactomeJavaConstants.modified);
        Collection<GKInstance> createdInstances = ie.getReferers(ReactomeJavaConstants.created);
        System.out.println("Modified instances: " + modifiedInstances.size());
        System.out.println("Created instances: " + (createdInstances == null ? "0" : createdInstances.size()));
        if (createdInstances != null && createdInstances.size() > 0)
            throw new IllegalStateException(ie + " is used to create instances!");
        return modifiedInstances;
    }
    
    private MySQLAdaptor getTargetDBA() throws Exception {
        if (targetDBA != null)
            return targetDBA;
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                targetDB,
                user,
                pwd);
        targetDBA = dba;
        return dba;
    }
    
    private MySQLAdaptor getSourceDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                sourceDB,
                user,
                pwd);
        return dba;
    }

}
