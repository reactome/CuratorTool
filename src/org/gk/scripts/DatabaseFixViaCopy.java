/*
 * Created on Nov 9, 2016
 *
 */
package org.gk.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.util.FileUtilities;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * Fix gk_central by copying contents from another backup database.
 * @author gwu
 *
 */
@SuppressWarnings("unchecked")
public class DatabaseFixViaCopy {
    private Neo4JAdaptor targetDBA;
    private Neo4JAdaptor sourceDBA;
    // Keep only one copy of InstanceEdit for modification
    private GKInstance instanceEdit;
    
    /**
     * Default constructor.
     */
    public DatabaseFixViaCopy() {
    }
    
    public static void main(String[] args) {
        DatabaseFixViaCopy fixer = new DatabaseFixViaCopy();
        try {
            fixer.fix();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void fix() throws Exception {
        List<GKInstance> instances = loadTouchedInstances();
        Neo4JAdaptor targetDBA = getTargetDBA();
        Neo4JAdaptor sourceDBA = getSourceDBA();
        int count = 0;
        System.out.println("Total instances to be fixed: " + instances.size());
        System.out.println("Perform fix...");
        Driver driver = targetDBA.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(targetDBA.getDBName()))) {
            Transaction tx = session.beginTransaction();
            for (GKInstance targetInst : instances) {
                GKInstance sourceInst = sourceDBA.fetchInstance(targetInst.getDBID());
                targetDBA.loadInstanceAttributeValues(targetInst);
                sourceDBA.loadInstanceAttributeValues(sourceInst);
                fix(targetInst, sourceInst);
                targetDBA.updateInstance(targetInst, tx);
                count ++;
//                if (count == 10)
//                    break;
            }
            tx.commit();
        }
        catch(Exception e) {
            throw e;
        }
    }
    
    private void fix(GKInstance targetInst,
                     GKInstance sourceInst) throws Exception {
        System.out.println(targetInst.getDBID() + "\t" + targetInst.getDisplayName() + "\t" + targetInst.getSchemClass().getName());
        // Need to copy modified slot first for fixEdited
        SchemaAttribute att = targetInst.getSchemClass().getAttribute(ReactomeJavaConstants.modified);
        fix(targetInst, sourceInst, att);
        for (Object attObj : targetInst.getSchemaAttributes()) {
            att = (SchemaAttribute) attObj;
            if (att.getName().equals(ReactomeJavaConstants.DB_ID) ||
                att.getName().equals(ReactomeJavaConstants._displayName) ||
                att.getName().equals(ReactomeJavaConstants.modified) ||
                att.getName().equals(ReactomeJavaConstants.releaseDate)) // All for this. We don't need to copy it
                continue;
            if (att.getName().equals(ReactomeJavaConstants.edited)) {
                // The last IE should be moved to the end of the modified slot
                fixEdited(targetInst, sourceInst);
                continue;
            }
            fix(targetInst, 
                sourceInst,
                att);
        }
    }
    
    private void fix(GKInstance targetInst,
                     GKInstance sourceInst,
                     SchemaAttribute att) throws Exception {
        List<?> srcValues = sourceInst.getAttributeValuesList(att.getName());
        if (srcValues == null || srcValues.size() == 0)
            return; // Do nothing
        List<?> targetValues = targetInst.getAttributeValuesList(att);
        if (!att.getName().equals(ReactomeJavaConstants.edited) && targetValues != null && targetValues.size() > 0) {
            throw new IllegalStateException(targetInst + " has value in " + att.getName());
        }
        if (att.isInstanceTypeAttribute()) {
            Neo4JAdaptor targetDBA = getTargetDBA();
            for (Object obj : srcValues) {
                GKInstance srcValue = (GKInstance) obj;
                GKInstance targetValue = targetDBA.fetchInstance(srcValue.getDBID());
                targetInst.addAttributeValue(att, targetValue);
            }
        }
        else {
            for (Object obj : srcValues)
                targetInst.addAttributeValue(att, obj);
        }
    }
    
    private void fixEdited(GKInstance targetInst,
                           GKInstance sourceInst) throws Exception {
        if (instanceEdit == null) {
            List<?> targetValues = targetInst.getAttributeValuesList(ReactomeJavaConstants.edited);
            instanceEdit = (GKInstance) targetValues.get(targetValues.size() - 1);
        }
        targetInst.addAttributeValue(ReactomeJavaConstants.modified,
                                     instanceEdit);
        // Now we can do copy
        SchemaAttribute att = targetInst.getSchemClass().getAttribute(ReactomeJavaConstants.edited);
        // Empty the values first for copy
        targetInst.setAttributeValueNoCheck(att, new ArrayList<>());
        // Now copy all old edited values
        fix(targetInst,
            sourceInst,
            att);
    }
    
    @Test
    public void compare() throws Exception {
        List<GKInstance> instances = loadTouchedInstances();
        Neo4JAdaptor targetDBA = getTargetDBA();
        Neo4JAdaptor sourceDBA = getSourceDBA();
        int count = 0;
        for (GKInstance targetInst : instances) {
            GKInstance sourceInst = sourceDBA.fetchInstance(targetInst.getDBID());
            targetDBA.loadInstanceAttributeValues(targetInst);
            sourceDBA.loadInstanceAttributeValues(sourceInst);
            compare(targetInst, sourceInst);
            count ++;
            if (count == 100)
                break;
        }
    }
    
    private void compare(GKInstance targetInst,
                         GKInstance sourceInst) throws Exception {
        for (Object attObj : targetInst.getSchemaAttributes()) {
            SchemaAttribute att = (SchemaAttribute) attObj;
            List<?> srcValues = sourceInst.getAttributeValuesList(att.getName());
            List<?> targetValues = targetInst.getAttributeValuesList(att);
            if (srcValues.size() < targetValues.size()) {
                System.out.println("More value in target: " + targetInst + " in " + att.getName());
            }
        }
    }
    
    private List<GKInstance> loadTouchedInstances() throws Exception {
        List<Long> dbIds = loadTouchedInstancesIds();
        System.out.println("Total DB_IDs for touched instances: " + dbIds.size());
        Neo4JAdaptor targetDBA = getTargetDBA();
        List<GKInstance> instances = new ArrayList<>();
        Set<Long> dbIdsSet = new HashSet<Long>(dbIds);
        Collection<GKInstance> events = targetDBA.fetchInstancesByClass(ReactomeJavaConstants.Event);
        for (GKInstance event : events) {
            if (dbIdsSet.contains(event.getDBID())) {
                instances.add(event);
                dbIdsSet.remove(event.getDBID());
            }
        }
        System.out.println("The following instances have been deleted: " + dbIdsSet);
        // The following code runs too slow. 
//      for (Long dbId : dbIds) {
//          GKInstance inst = targetDBA.fetchInstance(dbId);
//          if (inst == null) {
//              System.out.println("Cannot find instance: " + dbId);
//              continue;
//          }
//          instances.add(inst);
//      }
        return instances;
    }
    
    @Test
    public void check() throws Exception {
        List<GKInstance> instances = loadTouchedInstances();
        Neo4JAdaptor targetDBA = getTargetDBA();
        targetDBA.loadInstanceAttributeValues(instances, new String[] {
                ReactomeJavaConstants.edited,
                ReactomeJavaConstants.modified
        });
        for (GKInstance inst : instances) {
            List<GKInstance> edited = inst.getAttributeValuesList(ReactomeJavaConstants.edited);
            List<GKInstance> modified = inst.getAttributeValuesList(ReactomeJavaConstants.modified);
            System.out.println(inst.getDBID() + "\t" + inst.getDisplayName() + "\t" + 
                               edited.size() + "\t" + 
                               edited.get(edited.size() - 1) + "\t" +
                               (modified.size() == 0 ? "null" : modified.get(modified.size() - 1)));
        }
    }
    
    private List<Long> loadTouchedInstancesIds() throws IOException {
        FileUtilities fu = new FileUtilities();
        fu.setInput("releaseDate_list.txt");
        List<Long> dbIds = new ArrayList<>();
        String line = null;
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split(",");
            dbIds.add(new Long(tokens[0]));
        }
        fu.close();
        return dbIds;
    }
    
    private Neo4JAdaptor getTargetDBA() throws Exception {
        if (targetDBA == null) {
            targetDBA = new Neo4JAdaptor("reactomecurator.oicr.on.ca", 
                                         "gk_central",
                                         "{}",
                                         "{}");
        }
        return targetDBA;
    }
    
    private Neo4JAdaptor getSourceDBA() throws Exception {
        if (sourceDBA == null) {
            sourceDBA = new Neo4JAdaptor("reactomecurator.oicr.on.ca", 
                                         "test_gk_central_before_releasedate",
                                         "{}",
                                         "{}");
        }
        return sourceDBA;
    }
    
}
