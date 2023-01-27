package org.gk.slicing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * This class is used to handle the star system during the slicing. The major function of this
 * class is to assign 5 stars to events that don't have the reviewStatus assigned and check
 * events in the slice database that have lower than 3 stars.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class StarSystemHelper {
    private final Logger logger = Logger.getLogger(StarSystemHelper.class);

    public StarSystemHelper() {
    }

    /**
     * Extract ReviewStatus instances.
     * @param sourceDBA
     * @param sliceMap
     * @throws Exception
     */
    public Collection<GKInstance> extractReviewStatus(Map<Long, GKInstance> id2inst) throws Exception {
        Set<GKInstance> instances = new HashSet<>();
        for (Long id : id2inst.keySet()) {
            GKInstance inst = id2inst.get(id);
            if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus)) {
                GKInstance reviewStatus = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.reviewStatus);
                if (reviewStatus != null)
                    instances.add(reviewStatus);
            }
            if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.previousReviewStatus)) {
                GKInstance reviewStatus = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.previousReviewStatus);
                if (reviewStatus != null)
                    instances.add(reviewStatus);
            }
        }
        return instances;
    }
    
    /**
     * Assign five stars to Events in the slice that don't have reviewStatus assigned. These Events
     * are assumed to have externally reviewed.
     * @param sourceDBA
     * @param sliceMap
     * @return
     * @throws Exception
     */
    public List<GKInstance> assignFiveStarsToEvents(MySQLAdaptor sourceDBA,
                                                    Map<Long, GKInstance> sliceMap) throws Exception {
        logger.info("Assigning five stars to Events to be sliced...");
        Map<String, GKInstance> star2inst = loadReviewStatus(sourceDBA);
        GKInstance fiveStarReviewStatus = star2inst.get(ReactomeJavaConstants.FiveStars);
        if (fiveStarReviewStatus == null) {
            logger.error("Error: Cannot find five stars ReviewStatus instance.");
            return Collections.EMPTY_LIST;
        }
        List<GKInstance> updatedEvents = new ArrayList<>();
        for (Long dbId : sliceMap.keySet()) {
            GKInstance inst = sliceMap.get(dbId);
            if (!inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus)) {
                continue;
            }
            GKInstance reviewStatus = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.reviewStatus);
            if (reviewStatus == null) {
                logger.info("Assigning five stars to: " + inst);
                inst.setAttributeValue(ReactomeJavaConstants.reviewStatus, fiveStarReviewStatus);
                updatedEvents.add(inst);
            }
        }
        return updatedEvents;
    }
    
    /**
     * Use this method to assign five stars to released Events if these events don't have any reviewStatus 
     * assigned.
     * @param sourceDBA
     * @throws Exception
     */
    public void commitReviewStatusToSourceDB(List<GKInstance> eventsToBeUpdated,
                                             MySQLAdaptor sourceDBA,
                                             GKInstance defaultIE) throws Exception {
        logger.info("Writing back reviewStatus to the source database...");
        logger.info("Total Events to be updated for this step: " + eventsToBeUpdated.size());
        boolean needTransaction = sourceDBA.supportsTransactions();
        try {
            if (needTransaction)
                sourceDBA.startTransaction();
            // DefaultIE has not been stored
            if (defaultIE.getDBID() == null || defaultIE.getDBID() < 0)
                sourceDBA.storeInstance(defaultIE);
            int count = 1;
            for (GKInstance event : eventsToBeUpdated) {
                logger.info(count + ": " + event);
                sourceDBA.updateInstanceAttribute(event, ReactomeJavaConstants.reviewStatus);
                event.getAttributeValuesList(ReactomeJavaConstants.modified);
                event.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                sourceDBA.updateInstanceAttribute(event, ReactomeJavaConstants.modified);
                count ++;
            }
            if (needTransaction) 
                sourceDBA.commit();
            logger.info("Total processed events: " + (count - 1));
        }
        catch(Exception e) {
            if (needTransaction)
                sourceDBA.rollback();
            logger.error("Cannot commit reviewStatus: " + e.getMessage(), e);
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

}
