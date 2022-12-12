package org.gk.scripts;

import static org.gk.model.ReactomeJavaConstants.FiveStars;
import static org.gk.model.ReactomeJavaConstants.OneStar;
import static org.gk.model.ReactomeJavaConstants.TwoStars;
import static org.gk.scripts.ScriptUtilities.GUANMING_WU_DB_ID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.InstanceNotFoundException;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

/**
 * The methods in this class are used to handle the star systems for content release.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class StarSystemHandler {
    
    public StarSystemHandler() {
    }
    
    private MySQLAdaptor getCentralDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_120622",
                "root",
                "macmysql01");
        return dba;
    }
    
    private MySQLAdaptor getSliceDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "slice_release_83",
                "root",
                "macmysql01");
        return dba;
    }
    
    /**
     * Use this method to assign five stars to released Events if these events don't have any reviewStatus 
     * assigned.
     * @param centralDBA
     * @param sliceDBA
     * @throws Exception
     */
    public void assignFiveStarToReleased(MySQLAdaptor centralDBA,
                                         MySQLAdaptor sliceDBA) throws Exception {
        Map<String, GKInstance> star2inst = loadReviewStatus(centralDBA);
        // Load the released events
        Collection<GKInstance> releasedEvents = sliceDBA.fetchInstancesByClass(ReactomeJavaConstants.Event);
        List<GKInstance> toBeUpdatedSourceEvents = new ArrayList<>();
        for (GKInstance releasedEvent : releasedEvents) {
            GKInstance sourceEvent = centralDBA.fetchInstance(releasedEvent.getDBID());
            if (sourceEvent == null)
                throw new InstanceNotFoundException(releasedEvent.getDBID());
            GKInstance reviewStatus = (GKInstance) sourceEvent.getAttributeValue(ReactomeJavaConstants.reviewStatus);
            if (reviewStatus == null) {
                // Default for five stars
                sourceEvent.setAttributeValue(ReactomeJavaConstants.reviewStatus,
                                              star2inst.get(FiveStars));
                toBeUpdatedSourceEvents.add(sourceEvent);
            }
            else if (reviewStatus.getDisplayName().equals(OneStar) || reviewStatus.getDisplayName().equals(TwoStars)) {
                throw new IllegalStateException("Event has one or two star ReviewStatus released: " + sourceEvent);
            }
        }
        System.out.println("Total events to be assigned five stars: " + toBeUpdatedSourceEvents.size());
        boolean needTransaction = centralDBA.supportsTransactions();
        try {
            if (needTransaction)
                centralDBA.startTransaction();
            GKInstance ie = ScriptUtilities.createDefaultIE(centralDBA, 
                    GUANMING_WU_DB_ID, 
                    true);
            int count = 1;
            for (GKInstance event : toBeUpdatedSourceEvents) {
                System.out.println(count + ": " + event);
                centralDBA.updateInstanceAttribute(event, ReactomeJavaConstants.reviewStatus);
                ScriptUtilities.addIEToModified(event, ie, centralDBA);
                count ++;
            }
            System.out.println("Total processed events: " + (count - 1));
            if (needTransaction) 
                centralDBA.commit();
        }
        catch(Exception e) {
            if (needTransaction)
                centralDBA.rollback();
            throw e;
        }
    }
    
    private Map<String, GKInstance> loadReviewStatus(MySQLAdaptor dba) throws Exception {
        Map<String, GKInstance> name2instance = new HashMap<>();
        Collection<GKInstance> instances = dba.fetchInstancesByClass(ReactomeJavaConstants.ReviewStatus);
        for (GKInstance instance : instances) {
            name2instance.put(instance.getDisplayName(), instance);
        }
        return name2instance;
    }

    @Test
    public void testLoadReviewStatus() throws Exception {
        MySQLAdaptor dba = getCentralDBA();
        Map<String, GKInstance> name2inst = loadReviewStatus(dba);
        name2inst.forEach((name, inst) -> {
           System.out.println(name + ": " + inst); 
        });
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("The following arguments are needed: target_db_name (usually gk_central) " +
                                                                   "current_slice_db_name " + 
                                                                   "db_use " + 
                                                                   "db_pwd. Also make sure both databases are at localhost.");
            System.exit(1);
        }
        try {
            MySQLAdaptor targetDBA = new MySQLAdaptor("localhost",
                    args[0],
                    args[2], 
                    args[3]);
            MySQLAdaptor sliceDBA = new MySQLAdaptor("localhost",
                    args[1],
                    args[2], 
                    args[3]);
            StarSystemHandler handler = new StarSystemHandler();
            handler.assignFiveStarToReleased(targetDBA, sliceDBA);
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
    }
    
}
