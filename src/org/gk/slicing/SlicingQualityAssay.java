/*
 * Created on Oct 10, 2014
 *
 */
package org.gk.slicing;

import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchema;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.StringUtils;

/**
 * This method is used to run QA during slicing.
 * @author gwu
 *
 */
public class SlicingQualityAssay {
    private final static Logger logger = Logger.getLogger(SlicingQualityAssay.class);
    private Map<Long, GKInstance> sliceMap;
    private MySQLAdaptor sourceDBA;
    
    /**
     * Default constructor.
     */
    public SlicingQualityAssay() {
    }
    
    public void setSliceMap(Map<Long, GKInstance> sliceMap) {
        this.sliceMap = sliceMap;
    }
    
    public void setSourceDBA(MySQLAdaptor dba) {
        this.sourceDBA = dba;
    }
    
    /**
     * Make sure all Events are listed in the pathway hierarchy that can be returned by the RESTful API.
     * The restful api returns a tree based on hasEvent. This may need to be changed if new attribute
     * is introduced though most unlikely.
     * @param topicIds
     * @param output
     * @throws Exception
     */
    public void validateEventsInHierarchy(List<Long> topicIds,
                                          PrintStream output) throws Exception {
        logger.info("validateEventsInHierarchy...");
        long time1 = System.currentTimeMillis();
        // Get events covered by the pathway hierarchy.
        Set<GKInstance> eventsInTree = new HashSet<GKInstance>();
        for (Long topicId : topicIds) {
            GKInstance event = sliceMap.get(topicId);
            Set<GKInstance> containedEvents = InstanceUtilities.getContainedInstances(event, 
                                                                                      ReactomeJavaConstants.hasEvent);
            eventsInTree.addAll(containedEvents);
            eventsInTree.add(event); // Don't forget itself
        }
        // Get a list of events that are not covered
        List<GKInstance> eventsNotInTree = new ArrayList<GKInstance>();
        for (Long dbId : sliceMap.keySet()) {
            GKInstance event = sliceMap.get(dbId);
            if (!event.getSchemClass().isa(ReactomeJavaConstants.Event))
                continue;
            if (eventsInTree.contains(event))
                continue;
            eventsNotInTree.add(event);
        }
        long time2 = System.currentTimeMillis();
        logger.info("validateEventsInHierarchy: " + (time2 - time1) / 1000.0d + " seconds.");
        logger.info("Events that have not listed in the pathway hierarchy: " + eventsNotInTree.size() + " instances");

//      https://reactome.atlassian.net/browse/DEV-973 format change
        output.println("UNLISTED\tevent\tspecies");
        for (GKInstance event : eventsNotInTree) {
            GKInstance species = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.species);
            String className = event.getSchemClass().getName();
            String eventName = event.getDisplayName();
            String dbId = event.getDBID().toString();
            output.println("UNLISTED\t"+className+"\t"+dbId+"\t"+eventName+"\t" + (species == null ? "No species provided" : species.getDisplayName()));
        }
        output.println();
    }
    
    /**
     * Make sure all instance references in the slice. If not, those references should be 
     * removed from the attribute list. This check is used in case there are some database
     * errors in the source. Another case is an unrelease event is used by a released event.
     * @throws Exception
     */
    public void validateAttributes(PrintStream output) throws Exception {
        GKInstance instance = null;
        List values = null;
        Long dbID = null;
        GKInstance ref = null;
        SchemaAttribute att = null;
        output.println("ATTRIBUTE_REMOVED\tattClass\tattDbId\tattName\tattribute\tclass\tdbId\tname");
        for (Iterator it = sliceMap.keySet().iterator(); it.hasNext();) {
            dbID = (Long) it.next();
            instance = (GKInstance) sliceMap.get(dbID);
            instance.setIsInflated(true); // To prevent to fetch values again.
            for (Iterator it1 = instance.getSchemClass().getAttributes().iterator(); it1.hasNext();) {
                att = (GKSchemaAttribute) it1.next();
                if (!att.isInstanceTypeAttribute())
                    continue;
                values = instance.getAttributeValuesList(att);
                if (values == null || values.size() == 0)
                    continue;
                for (Iterator it2 = values.iterator(); it2.hasNext();) {
                    ref = (GKInstance) it2.next();
                    if (!sliceMap.containsKey(ref.getDBID())) {
                        it2.remove();
                        String refDbId = ref.getDBID().toString();
                        String refClass = ref.getSchemClass().getName();
                        String refName = ref.getDisplayName();
                        String attName = att.getName();
                        String instanceName = instance.getDisplayName();
                        String instanceClass = instance.getSchemClass().getName();
                        // https://reactome.atlassian.net/browse/DEV-973 format change
                        output.println("ATTRIBUTE_REMOVED\t"+refClass+"\t"+refDbId+"\t"+refName+"\t"+
                        		attName+"\t"+instanceClass+"\t"+dbID.toString()+"\t"+instanceName);

//                        output.println("\"" + ref.toString() + "\" in \"" + att.getName() + "\" for \"" + instance + 
//                                           "\" is not in the slice and removed from the attribute list!");
                    }
                }
            }
        }
        output.println();
    }
    
    /**
     * This check is to prevent some database errors: an Instance is used
     * in attribute table but this instance is not registered in DatabaseObject
     * table. Such instances should not be in the slice.
     * @throws SQLException
     */
    public void validateExistence(PrintStream output) throws SQLException {
        SchemaClass root = ((GKSchema)sourceDBA.getSchema()).getRootClass();
        List dbIDs = new ArrayList(sliceMap.keySet());
        String query = "SELECT DB_ID FROM " + root.getName() + " WHERE DB_ID IN (" + 
                        StringUtils.join(",", dbIDs) + ")";
        Set idsInDB = new HashSet();
        Statement stat = sourceDBA.getConnection().createStatement();
        ResultSet resultSet = stat.executeQuery(query);
        while (resultSet.next()) {
            long id = resultSet.getLong(1);
            idsInDB.add(new Long(id));
        }
        resultSet.close();
        stat.close();
        dbIDs.removeAll(idsInDB);
        Long dbID = null;
        output.println("Instance existence checking:");
        for (Iterator it = dbIDs.iterator(); it.hasNext();) {
            dbID = (Long) it.next();
            sliceMap.remove(dbID);
            output.println("Instance with DB_ID \"" + dbID + "\" " +
                               "is used but not in table DatabaseObject!");
        }
        output.println();
        logger.info("validateExistence(): " + sliceMap.size() + " instances.");
    }
    
}
