package org.gk.slicing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
     * Extract ReviewStatus instances. All ReviewStatus instances are collected regardless whether
     * they are used or not.
     * @param dba
     * @throws Exception
     */
    public Collection<GKInstance> extractReviewStatus(MySQLAdaptor dba) throws Exception {
        return dba.fetchInstancesByClass(ReactomeJavaConstants.ReviewStatus);
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
     * This method is used to handle the following scenario related to pathway: A released pathways (3, 4, and 5 stars)
     * may be added a new Event in hasEvent, resulting the demoting of the start to 1 or 2 at gk_central. However, during 
     * the slicing or release, this newly Event is flagged for not releasing. Therefore, the original pathway structure 
     * that is determined by "hasEvent" is reverted back. Therefore, we should copy the original star from the previous release
     * (or slice) back to this slice. In reality, we copy a higher star to any lower star if a pathway's hasEvent is not changed.
     * (Added on June 9, 2025) To avoid flagging the pathway for the ReviewStatus QA, we also make sure that the list of 
     * structureModified instances are the same as in the old slice database after copying the reviewStatus.
     * @param sourceDBA
     * @param priorDBA
     * @param sliceMap
     * @return
     * @throws Exception
     */
    public List<GKInstance> copyReviewStatusFromPriorSliceForPathways(MySQLAdaptor sourceDBA,
                                                                      MySQLAdaptor priorDBA,
                                                                      Map<Long, GKInstance> sliceMap) throws Exception {
        logger.info("Copying higher stars from previous slice for pathways having no structural update...");
        if (priorDBA == null) {
            logger.info("No priorDBA specified. Stop this step.");
            return Collections.EMPTY_LIST;
        }
        Map<String, GKInstance> star2inst = loadReviewStatus(sourceDBA);
        // Map the review status to numbers so that we can do comparison
        Map<String, Integer> star2number = new HashMap<>();
        star2number.put(ReactomeJavaConstants.OneStar, 1);
        star2number.put(ReactomeJavaConstants.TwoStars, 2);
        star2number.put(ReactomeJavaConstants.ThreeStars, 3);
        star2number.put(ReactomeJavaConstants.FourStars, 4);
        star2number.put(ReactomeJavaConstants.FiveStars, 5);
        List<GKInstance> updatedPathways = new ArrayList<>();
        for (Long dbId : sliceMap.keySet()) {
            GKInstance inst = sliceMap.get(dbId);
            if (!inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus)) {
                continue;
            }
            if (!inst.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                continue; // Work for pathway only
            GKInstance reviewStatus = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.reviewStatus);
            // Escape five stars: They should be good.
            if (reviewStatus != null && reviewStatus.getDisplayName().equals(ReactomeJavaConstants.FiveStars))
                continue;
            // Check the old pathway
            GKInstance oldInst = priorDBA.fetchInstance(inst.getDBID());
            if (oldInst == null)
                continue; // Nothing to do
            if (!oldInst.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                continue; // It is not a pathway. Don't do anything
            if (!oldInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus))
                continue; // Old model. Don't bother.
            List<GKInstance> oldHasEvent = oldInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            List<Long> oldHasEventIds = oldHasEvent.stream().map(GKInstance::getDBID).collect(Collectors.toList());
            List<GKInstance> newHasEvent = inst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            List<Long> newHasEventIds = newHasEvent.stream()
                                                   .map(GKInstance::getDBID)
                                                   .collect(Collectors.toList());
            if (!oldHasEventIds.equals(newHasEventIds))
                continue; // The copy is applied to cases that have the same list of hasEvent only.
            // Get the old review status
            GKInstance oldReviewStatus = (GKInstance) oldInst.getAttributeValue(ReactomeJavaConstants.reviewStatus);
            if (oldReviewStatus == null)
                continue; // Nothing to copy
            Integer oldReviewStand = star2number.get(oldReviewStatus.getDisplayName());
            // Get the stand for the new 
            Integer newReviewStand = star2number.get(reviewStatus.getDisplayName());
            if (newReviewStand == null)
                newReviewStand = 0; // Put it at the bottom
            if (newReviewStand < oldReviewStand) {
                // Copy the old review status to the new. But we need to use the copy of the sourceDBA
                logger.info("Copying old reviewStatus for " + inst + ": " +
                            oldReviewStatus.getDisplayName() + "->" +
                            reviewStatus.getDisplayName());
                inst.setAttributeValue(ReactomeJavaConstants.reviewStatus,
                                       star2inst.get(oldReviewStatus.getDisplayName()));
                // Make sure the list of structureModified instances are the same as in the old slice database.
                // By doing this, we can avoid to flag this pathway for the ReviewStatus QA during the release.
                List<GKInstance> structureModified = inst.getAttributeValuesList(ReactomeJavaConstants.structureModified);
                if (structureModified != null && structureModified.size() > 0) {
                    // Reset the structureModified list
                    boolean isModified = false;
                    List<GKInstance> oldStructureModified = oldInst.getAttributeValuesList(ReactomeJavaConstants.structureModified);
                    Set<Long> oldStructureModifiedIds = new HashSet<>();
                    if (oldStructureModified != null && oldStructureModified.size() > 0) {
                        oldStructureModifiedIds = oldStructureModified.stream()
                                                                      .map(GKInstance::getDBID)
                                                                      .collect(Collectors.toSet());
                    }
                    for (Iterator<GKInstance> it = structureModified.iterator(); it.hasNext();) {
                        GKInstance sm = it.next();
                        if (!oldStructureModifiedIds.contains(sm.getDBID())) {
                            // This is not in the old structureModified. Remove it.
                            it.remove();
                            isModified = true;
                        }
                    }
                    if (isModified) {
                        // Update the structureModified
                        inst.setAttributeValue(ReactomeJavaConstants.structureModified, structureModified);
                    }
                }
                updatedPathways.add(inst);
            }
        }
        logger.info("Done copying. The number of pathways touched: " + updatedPathways.size());
        return updatedPathways;
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
