package org.gk.slicing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.junit.Test;

/**
 * Create a new _UpdateTracker instance for every updated/revised Event between a current slice and a previous slice.
 */
@SuppressWarnings("unchecked")
public class RevisionDetector {
    private static final Logger logger = Logger.getLogger(RevisionDetector.class);
    private SlicingEngine sliceEngine;
    public RevisionDetector() {
    }

    public void setSlicingEngine(SlicingEngine engine) {
        this.sliceEngine = engine;
    }

    public void handleRevisions(MySQLAdaptor sourceDBA, // This is usually gk_central
                                MySQLAdaptor currentSliceDBA,
                                MySQLAdaptor previousSliceDBA,
                                boolean uploadUpdateTrackersToSource) throws Exception {
        // Make sure we have _UpdateTracker class in the currentSliceDBA
        if (currentSliceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker) == null) {
            logger.info("No _UpdateTracker class in the current slice database. Nothing to do for handleRevision!");
            return;
        }
        List<GKInstance> updateTrackers = createAllUpdateTrackers(currentSliceDBA,
                                                                  previousSliceDBA);
        if (updateTrackers == null || updateTrackers.size() == 0) {
            logger.info("No _UpdateTracker is created!");
            return;
        }
        dumpUpdateTrackers(currentSliceDBA, updateTrackers);
        if (uploadUpdateTrackersToSource)
            uploadUpdateTrackers(sourceDBA, currentSliceDBA, updateTrackers);
    }

    /**
     * Upload newly created _UpdateTracker instances into the source database, which is gk_central usually.
     * @param sourceDBA
     * @param updateTrackers
     * @throws Exception
     */
    private void uploadUpdateTrackers(MySQLAdaptor sourceDBA,
                                      MySQLAdaptor currentSliceDBA,
                                      List<GKInstance> updateTrackers) throws Exception {
        logger.info("Uploading UpdateTracker to the source database...");
        SchemaClass updateTrackerCls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
        if (updateTrackerCls == null) {
            logger.info("No _UpdateTracker class in the sourceDBA. Nothing to do for uploadUpdateTrackers.");
            return;
        }
        GKInstance release = getReleaseInstance(currentSliceDBA);
        // Switch to sourceDBA
        SchemaClass releaseCls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._Release);
        if (releaseCls == null) {
            logger.info("No _Release class in the sourceDBA. Nothing to do for uploadUpdateTrackers.");
            return;
        }
        // To copy that, we need to fully load the release instances
        // so that all values should be in the instance. Otherwise, some value may
        // not be there since the instance is loaded directly by DBA.
        currentSliceDBA.fastLoadInstanceAttributeValues(release);
        release.setSchemaClass(releaseCls);
        release.setDbAdaptor(sourceDBA);
        release.setDBID(null);
        GKInstance defaultIE = sliceEngine.createDefaultIE(sourceDBA);
        // Need to revise these instances to use the information from sourceDBA and also reset DB_IDs for better control
        boolean needTrasanction = sourceDBA.supportsTransactions();
        try {
            if (needTrasanction)
                sourceDBA.startTransaction();
            // Commit these two instances first
            sourceDBA.storeInstance(defaultIE);
            release.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
            sourceDBA.storeInstance(release);
            // Note: DB_IDs used by those instances are different from the current slice database.
            // This probably is fine.
            long time1 = System.currentTimeMillis();
            for (GKInstance updateTracker : updateTrackers) {
                // Move to the sourceDBA version
                updateTracker.setDBID(null);
                updateTracker.setSchemaClass(updateTrackerCls);
                updateTracker.setAttributeValue(ReactomeJavaConstants._release, release);
                GKInstance updateEvent = (GKInstance) updateTracker.getAttributeValue(ReactomeJavaConstants.updatedEvent);
                updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent,
                                                sourceDBA.fetchInstance(updateEvent.getDBID()));
                updateTracker.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
                updateTracker.setDbAdaptor(sourceDBA);
                sourceDBA.storeInstance(updateTracker);
            }
            if (needTrasanction)
                sourceDBA.commit();
            long time2 = System.currentTimeMillis();
            logger.info("Done uploading _UpdateTracker instances: " + (time2 - time1) / 1000.0d + " seconds.");
        }
        catch(Exception e) {
            if (needTrasanction)
                sourceDBA.rollback();
            logger.error("Error in uploadUpdateTrackers: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * This test method can be run after the slice database is created but the source database (e.g. gk_central) has not been updated.
     * @throws Exception
     */
    @Test
    public void testUploadUpdateTracers() throws Exception {
        PropertyConfigurator.configure("SliceLog4j.properties");
        MySQLAdaptor sourceDBA = new MySQLAdaptor("localhost",
                                                  "gk_central_02_05_20_update_tracker",
                                                  "root",
                                                  "macmysql01");
        MySQLAdaptor currentSliceDBA = new MySQLAdaptor("localhost",
                                                        "test_slice_ver72",
                                                        "root",
                                                        "macmysql01");
        Collection<GKInstance> updateTrackers = currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants._UpdateTracker);
        currentSliceDBA.loadInstanceAttributeValues(updateTrackers,
                                                    new String[] {
                                                            ReactomeJavaConstants.updatedEvent,
                                                            ReactomeJavaConstants.action
                                                    });
        logger.info("Total UpdateTrackers: " + updateTrackers.size());
        this.sliceEngine = new SlicingEngine();
        sliceEngine.setDefaultPersonId(140537L);
        uploadUpdateTrackers(sourceDBA, currentSliceDBA, new ArrayList<>(updateTrackers));
    }

    /**
     * A helper method to dump all UpdateTracker instances into the current slice database.
     * @param currentSliceDBA
     * @param updateTrackers
     * @throws Exception
     */
    private void dumpUpdateTrackers(MySQLAdaptor currentSliceDBA, List<GKInstance> updateTrackers) throws Exception {
        logger.info("Dumping UpdateTrackers into the slice database...");
        // This is not committed
        GKInstance defaultIE = sliceEngine.createDefaultIE(currentSliceDBA);
        currentSliceDBA.storeInstance(defaultIE);
        logger.info("Attaching default InstanceEdit and then committing...");
        long time1 = System.currentTimeMillis();
        for (GKInstance updateTracker : updateTrackers) {
            updateTracker.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
            currentSliceDBA.storeInstance(updateTracker);
        }
        // Commit new instances to source database.
        long time2 = System.currentTimeMillis();
        logger.info("Dumping UpdateTrackers done: " + (time2 - time1) / 1000.0d + " seconds.");
    }

    private GKInstance getReleaseInstance(MySQLAdaptor targetDBA) throws Exception {
        Collection<GKInstance> c = targetDBA.fetchInstancesByClass(ReactomeJavaConstants._Release);
        if (c == null | c.size() == 0)
            throw new IllegalStateException("Cannot find any _Release instance in the slice database!");
        return c.stream().findAny().get();
    }

    /**
     * Return a List of _UpdateTracker instances for every Event revision in the sliceMap (compared to a previous slice).
     *
     * <p><b>An RLE gets a revised flag if:</b></p>
     * <ol>
     *   <li>An immediate child RLE revised</li>
     *   <ul>
     *     <li>A Catalyst added/removed/changed</li>
     *     <li>A regulator added/removed/changed</li>
     *     <li>A change is made in inputs/outputs</li>
     *     <li>A significant change in summation text</li>
     *   </ul>
     * </ol>
     *
     * <p><b>A pathway gets a revised flag if:</b></p>
     * <ol>
     *   <li>An immediate child RLE revised
     *   <ul>
     *     <li>A Catalyst added/removed/changed</li>
     *     <li>A regulator added/removed/changed</li>
     *     <li>A change is made in inputs/outputs</li>
     *     <li>A significant change in summation text</li>
     *   </ul>
     *   <li>An immediate child RLE is added/removed</li>
     *   <li>An immediate child Pathway added/removed</li>
     *   <li>An immediate child Pathway is revised (recursively defined).</li>
     * </ol>
     *
     * @param currentSliceDBA
     * @param previousSliceDBA
     * @return List
     * @throws InvalidAttributeException
     * @throws Exception
     * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
     */
    List<GKInstance> createAllUpdateTrackers(MySQLAdaptor currentSliceDBA, MySQLAdaptor previousSliceDBA) throws Exception {
        if (previousSliceDBA == null)
            return null;
        GKInstance release = getReleaseInstance(currentSliceDBA);
        GKInstance defaultIE = sliceEngine.createDefaultIE(currentSliceDBA);
        Collection<GKInstance> events = currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants.Event);

        Map<GKInstance, Set<String>> actionMap = createActionMap(previousSliceDBA, events);
        List<GKInstance> updateTrackers = new ArrayList<>();
        // Iterate over all instances in the slice.
        GKInstance updateTracker = null;
        for (GKInstance sourceEvent : events) {
            updateTracker = createUpdateTracker(currentSliceDBA, sourceEvent, actionMap.get(sourceEvent), release, defaultIE);
            if (updateTracker != null) {
                updateTrackers.add(updateTracker);
            }
        }
        return updateTrackers;
    }

    /**
     * Return a single _UpdateTracker instance for a given "Event" instance.
     *
     * Return null if the passed instance is not an Event or no revisions were detected.
     *
     * @param dba
     * @param updatedEvent
     * @param actions
     * @param release
     * @param created
     * @return GKInstance
     * @throws Exception
     */
    GKInstance createUpdateTracker(MySQLAdaptor dba,
                                   GKInstance updatedEvent,
                                   Set<String> actions,
                                   GKInstance release,
                                   GKInstance created) throws Exception {
        // No revision detected.
        if (actions == null || actions.size() == 0)
            return null;

        // If a revision condition is met, create new _UpdateTracker instance.
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
        GKInstance updateTracker = new GKInstance(cls);
        updateTracker.setDbAdaptor(dba);

        // created
        updateTracker.setAttributeValue(ReactomeJavaConstants.created, created);

        // action
        updateTracker.setAttributeValue(ReactomeJavaConstants.action, String.join(",", actions));

        // _release
        updateTracker.setAttributeValue(ReactomeJavaConstants._release, release);

        // updatedEvent
        updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);

        // _displayName
        InstanceDisplayNameGenerator.setDisplayName(updateTracker);

        return updateTracker;
    }

    /**
     * Return a mapping between event instances and their respective 'action' strings.
     *
     * Current behavior for recording revisions for child events is:
     * <pre>
     *   - A revision in a child RLE is recorded in both the RLE's action set as well as it's parent's action set.
     *   - The 'actionFilter' value will be added to all parents above the parent pathway to prevent
     *     action texts from bubbling up past immediate parents.
     * </pre>
     *
     * @param previousSliceDBA
     * @param events
     * @return Map
     * @throws InvalidAttributeException
     * @throws Exception
     */
    Map<GKInstance, Set<String>> createActionMap(MySQLAdaptor previousSliceDBA, Collection<GKInstance> events) throws InvalidAttributeException, Exception {
        Map<GKInstance, Set<String>> actionMap = new HashMap<GKInstance, Set<String>>();
        Set<String> RLEActions = null;
        LinkedList<GKInstance> pathways = new LinkedList<GKInstance>();

        // For all events in 'events'.
        for (GKInstance event : events) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                // Add RLE action strings to map.
                GKInstance previousSliceEvent = previousSliceDBA.fetchInstance(event.getDBID());
                RLEActions = getRLERevisions(event, previousSliceEvent);
                actionMap.put(event, RLEActions);
            }
            // If event is a Pathway, add to pathway list.
            else pathways.add(event);
        }

        Set<GKInstance> visited = new HashSet<GKInstance>();
        while (!pathways.isEmpty()) {
            List<GKInstance> queue = new ArrayList<GKInstance>();
            queue.add(pathways.poll());

            while (!queue.isEmpty()) {
                GKInstance pathway = queue.remove(0);
                GKInstance previousPathway = previousSliceDBA.fetchInstance(pathway.getDBID());

                if (visited.contains(pathway) || previousPathway == null) continue;
                visited.add(pathway);

                // Check if a child event (pathway or RLE) is added or removed.
                Set<String> pathwayActions = getAttributeRevisions(pathway, previousPathway, ReactomeJavaConstants.hasEvent);

                // Add immediate child event's actions to pathway's action set.
                List<Object> childEvents = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                for (Object event : childEvents) {
                    if (actionMap.containsKey(event)) {
                        Set<String> childActions = actionMap.get(event);
                        pathwayActions.addAll(childActions);
                    }
                }

                // If no revisions were detected in the pathway, move on to the next pathway.
                if (pathwayActions.size() == 0) continue;

                addToMap(actionMap, pathway, pathwayActions);

                Collection<GKInstance> referrers = pathway.getReferers(ReactomeJavaConstants.hasEvent);
                for (GKInstance referrer : referrers)
                    addToMap(actionMap, referrer, pathwayActions);

                for (Object event : childEvents) {
                    if (event == null) continue;
                    GKInstance eventInstance = (GKInstance) event;
                    if (visited.contains(eventInstance)) continue;
                    if (!eventInstance.getSchemClass().isa(ReactomeJavaConstants.Pathway)) continue;
                    queue.add(eventInstance);
                }
            }
        }

        return actionMap;
    }

    private void addToMap(Map<GKInstance, Set<String>> map, GKInstance pathway, Set<String> pathwayActions) {
        if (map.get(pathway) != null)
            map.get(pathway).addAll(pathwayActions);
        else
            map.put(pathway, pathwayActions);
    }

    @Test
    public void testCreateActionMap() throws InvalidAttributeException, Exception {
        MySQLAdaptor currentSliceDBA = new MySQLAdaptor("localhost",
                                                        "slice",
                                                        "liam",
                                                        ")8J7m]!%[<");
        Collection<GKInstance> events = currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants.Event);

        MySQLAdaptor previousSliceDBA = new MySQLAdaptor("localhost",
                                                         "previous_slice",
                                                         "liam",
                                                         ")8J7m]!%[<");
        Map<GKInstance, Set<String>> actionMap = createActionMap(previousSliceDBA, events);
    }

    /**
     * Return a list of RLE revisions.
     * e.g. ["addInput", "removeCatalyst", "modifySummation"].
     *
     * @param sourceRLE
     * @return Set
     * @throws InvalidAttributeException
     * @throws Exception
     */
    private Set<String> getRLERevisions(GKInstance sourceRLE, GKInstance previousSliceRLE) throws InvalidAttributeException, Exception {
        if (sourceRLE == null || previousSliceRLE == null)
            return null;

        if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) ||
            !previousSliceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
            return null;

        // Check for changes in inputs, outputs, regulators, catalysts, and summation.
        List<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
                                                  ReactomeJavaConstants.output,
                                                  ReactomeJavaConstants.regulatedBy,
                                                  ReactomeJavaConstants.catalystActivity,
                                                  ReactomeJavaConstants.summation);
        Set<String> actions = new HashSet<String>();
        Set<String> tmp = null;
        for (String attrName : revisionList) {
            tmp = getAttributeRevisions(sourceRLE, previousSliceRLE, attrName);
            if (tmp != null)
                actions.addAll(tmp);
        }

        // Check if summation is revised.
        Set<String> summationTextRevision = getRLESummationTextRevisions(sourceRLE, previousSliceRLE);
        if (summationTextRevision != null)
            actions.addAll(summationTextRevision);

        return actions;
    }

    /**
     * Check if an instance's summation attribute is revised and return revision strings.
     * e.g. ["addInput", "removeCatalyst", "modifySummation"].
     *
     * @param sourceRLE
     * @param previousSliceRLE
     * @return Set
     * @throws InvalidAttributeException
     * @throws Exception
     *
     * @see {@link org.gk.model.Summation}
     */
    private Set<String> getRLESummationTextRevisions(GKInstance sourceRLE, GKInstance previousSliceRLE) throws InvalidAttributeException, Exception {
        if (sourceRLE == null || previousSliceRLE == null)
            return null;

        if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) ||
            !previousSliceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
            return null;

        Set<String> actions = getAttributeRevisions(sourceRLE, previousSliceRLE, ReactomeJavaConstants.summation);
        List<Object> sourceSummations = sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation);
        List<Object> previousSliceSummations = previousSliceRLE.getAttributeValuesList(ReactomeJavaConstants.summation);

        // Iterate over source summations.
        Object matchingSummation = null;
        for (Object sourceSummation : sourceSummations) {
            matchingSummation =  getMatchingAttribute(previousSliceSummations, sourceSummation);
            if (matchingSummation == null)
                continue;
            actions.addAll(getAttributeRevisions(sourceSummation, matchingSummation, ReactomeJavaConstants.text));
        }

        return actions;
    }

    /**
     * Compare the value of a given attribute between two instances, and return revision strings.
     * e.g. ["addInput", "removeCatalyst", "modifySummation"].
     *
     * @param sourceInstance
     * @param previousSliceInstance
     * @param attributeName
     * @return Set
     * @throws Exception
     */
    Set<String> getAttributeRevisions(Object sourceInstance, Object previousSliceInstance, String attributeName) throws Exception {
        if (sourceInstance == null || previousSliceInstance == null)
            return null;

        List<Object> sourceAttributes = ((GKInstance) sourceInstance).getAttributeValuesList(attributeName);
        List<Object> previousSliceAttributes = ((GKInstance) previousSliceInstance).getAttributeValuesList(attributeName);
        Set<String> actions = new HashSet<String>();

        // Both attribute lists are empty.
        if (sourceAttributes.size() == 0 && previousSliceAttributes.size() == 0)
            return null;

        // Check for additions.
        boolean addedInstances = containsNewAttribute(sourceAttributes, previousSliceAttributes);

        // Check for deletions.
        boolean removedInstances = containsNewAttribute(previousSliceAttributes, sourceAttributes);

        // Additions and deletions are both present.
        if (addedInstances && removedInstances) {
            // GKInstance attributes.
            if (sourceAttributes.get(0) instanceof GKInstance)
                actions.add(ReactomeJavaConstants.addRemove + format(attributeName));

            // Non-GKInstance attributes (e.g. summary text).
            else
                actions.add(ReactomeJavaConstants.modify + format(attributeName));
        }

        // Only additions are present.
        else if (addedInstances)
            actions.add(ReactomeJavaConstants.add + format(attributeName));

        // Only deletions are present.
        else if (removedInstances)
            actions.add(ReactomeJavaConstants.remove + format(attributeName));

        return actions;
    }

    /**
     * Return true if a new attribute is added to previousSliceAttributes.
     * i.e. not all instances in "sourceAttributes" are also contained in "previousSliceAttributes".
     *
     * @param sourceAttributes
     * @param previousSliceAttributes
     * @return boolean
     */
    private boolean containsNewAttribute(List<Object> sourceAttributes, List<Object> previousSliceAttributes) {
        // If previousSliceInstances does not contain an instance in sourceInstances, then sourceInstances has had an addition.
        Object matchingAttribute = null;
        for (Object sourceInstance: sourceAttributes) {
            matchingAttribute = getMatchingAttribute(previousSliceAttributes, sourceInstance);
            if (matchingAttribute == null)
                return true;
        }

        return false;
    }

    /**
     * Search a list of attribute values for a given DBID (for GKInstance attributes) or a given value for non-GKInstance attributes.
     *
     * This method allows attributes to be compared even when they occupy different positions in respective attribute lists.
     *
     * @param attributes
     * @param searchAttribute
     * @return Object
     */
    private Object getMatchingAttribute(List<Object> attributes, Object searchAttribute) {
        if (attributes == null || attributes.isEmpty())
            return null;

        // If the attribute list contains GKInstance's, compare DBID's.
        if (attributes.get(0) instanceof GKInstance) {
            Long searchAttributeDBID = ((GKInstance) searchAttribute).getDBID();
            Long attributeDBID = null;
            for (Object attribute : attributes) {
                attributeDBID = ((GKInstance) attribute).getDBID();

                if (attributeDBID.equals(searchAttributeDBID))
                    return attribute;
            }
        }
        // If the attribute list holds non-GKInstance's (e.g. plain text), compare directly.
        else {
            for (Object attribute : attributes) {
                if (attribute.equals(searchAttribute))
                    return attribute;
            }
        }

        return null;
    }

    /**
     * Simple utility method to capitalize an input string.
     * e.g. "summation" -> "Summation".
     *
     * @param input
     * @return String
     */
    private String format(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1, input.length());
    }
}
