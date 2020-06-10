package org.gk.slicing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public void testUploadUpdateTrackers() throws Exception {
        PropertyConfigurator.configure("SliceLog4j.properties");
        this.sliceEngine = new SlicingEngine();
        sliceEngine.setDefaultPersonId(140537L);
        MySQLAdaptor sourceDBA = new MySQLAdaptor("localhost",
                                                  "gk_central_02_05_20_update_tracker",
                                                  "root",
                "macmysql01");
        MySQLAdaptor currentSliceDBA = new MySQLAdaptor("localhost",
                                                        "test_ver73_slice_update_tracker",
                                                        "root",
                "macmysql01");
        MySQLAdaptor previousSliceDBA = new MySQLAdaptor("localhost", 
                                                         "test_slice_ver71",
                                                         "root",
                                                         "macmysql01");
        List<GKInstance> updateTrackers = createAllUpdateTrackers(currentSliceDBA,
                                                                  previousSliceDBA);
        int i = 0; 
        for (GKInstance inst : updateTrackers) {
            System.out.println(i++ + "" + 
                    inst + ": " + 
                    inst.getAttributeValue(ReactomeJavaConstants.updatedEvent) + "; " + 
                    inst.getAttributeValue(ReactomeJavaConstants.action));
        }
        //        Collection<GKInstance> updateTrackers = currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants._UpdateTracker);
        //        currentSliceDBA.loadInstanceAttributeValues(updateTrackers,
        //                                                    new String[] {
        //                                                            ReactomeJavaConstants.updatedEvent,
        //                                                            ReactomeJavaConstants.action
        //        });
        //        logger.info("Total UpdateTrackers: " + updateTrackers.size());
        //        uploadUpdateTrackers(sourceDBA, currentSliceDBA, new ArrayList<>(updateTrackers));
    }
    
    @Test
    public void testGetEventActions() throws Exception {
        Long dbId = 4608870L;
        MySQLAdaptor currentSliceDBA = new MySQLAdaptor("localhost",
                                                        "test_ver73_slice_update_tracker",
                                                        "root",
                "macmysql01");
        GKInstance event = currentSliceDBA.fetchInstance(dbId);
        MySQLAdaptor previousSliceDBA = new MySQLAdaptor("localhost", 
                                                         "test_slice_ver71",
                                                         "root",
                "macmysql01");
        Map<GKInstance, Set<String>> eventToActions = new HashMap<>();
        getEventActions(event,
                        previousSliceDBA,
                        eventToActions);
        eventToActions.forEach((e, actions) -> System.out.println(e + " -> " + actions));
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
        if (c.size() > 1)
            throw new IllegalStateException("There is more than on _Release instance in the current slice database.");
        return c.iterator().next();
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
    private List<GKInstance> createAllUpdateTrackers(MySQLAdaptor currentSliceDBA,
                                                     MySQLAdaptor previousSliceDBA) throws Exception {
        if (previousSliceDBA == null)
            return null;
        logger.info("Create _UpdateTracker instances...");
        GKInstance release = getReleaseInstance(currentSliceDBA);
        GKInstance defaultIE = sliceEngine.createDefaultIE(currentSliceDBA);
        Collection<GKInstance> events = currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants.Event);

        // Collection all actions for events
        Map<GKInstance, Set<String>> eventToActions = new HashMap<>();
        for (GKInstance event : events) {
            getEventActions(event, previousSliceDBA, eventToActions);
        }

        // Create UpdateTracker instances based on actions.
        List<GKInstance> updateTrackers = new ArrayList<>();
        for (GKInstance sourceEvent : events) {
            Set<String> actionText = eventToActions.get(sourceEvent);
            GKInstance updateTracker = createUpdateTracker(currentSliceDBA,
                                                           sourceEvent, 
                                                           actionText, 
                                                           release, 
                                                           defaultIE);
            if (updateTracker != null) {
                updateTrackers.add(updateTracker);
            }
        }
        return updateTrackers;
    }

    private void getEventActions(GKInstance event,  // The event in the current slice database
                                 MySQLAdaptor preSliceDBA, // DBA for the previous slice database
                                 Map<GKInstance, Set<String>> eventToActions) throws Exception {
        if (eventToActions.containsKey(event))
            return;
        GKInstance preEvent = preSliceDBA.fetchInstance(event.getDBID());
        // It is simple for an RLE
        if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
            Set<String> actions = getRLERevisions(event, preEvent);
            eventToActions.put(event, actions);
            return;
        }
        // Actions at this pathway level
        Set<String> pathwayLevelActions = getPathwayRevisions(event,
                                                              preEvent);
        List<GKInstance> hasEvent = event.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        if (hasEvent == null || hasEvent.size() == 0) {
            eventToActions.put(event, pathwayLevelActions); // Whatever we have
            return;
        }
        // Get into the contained event. This is a width first search
        Set<String> actions = new HashSet<>();
        for (GKInstance inst : hasEvent) {
            // For pathways, we need to drill down
            if (!eventToActions.containsKey(inst)) {
                getEventActions(inst, preSliceDBA, eventToActions);
            }
            Set<String> instActions = eventToActions.get(inst);
            if (instActions != null && instActions.size() > 0) {// Regardless what changes there.
                if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    actions.add(UPDATE_ACTION.update + "ContainedRLE");
                }
                else {
                    // Bubble up UpdateContainedRLE if it is there
                    Set<String> copy = new HashSet<>(instActions);
                    if (copy.contains(UPDATE_ACTION.update + "ContainedRLE")) {
                        actions.add(UPDATE_ACTION.update + "ContainedRLE");
                        copy.remove(UPDATE_ACTION.update + "ContainedRLE");
                    }
                    if (copy.size() > 0) // Anything else will be marked as this.
                        actions.add(UPDATE_ACTION.update + "ContainedPathway");
                }
            }
        }
        if (pathwayLevelActions != null)
            actions.addAll(pathwayLevelActions);
        eventToActions.put(event, actions);
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
    private GKInstance createUpdateTracker(MySQLAdaptor dba,
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
        updateTracker.setAttributeValue(ReactomeJavaConstants.action,
                                        actions.stream().sorted().collect(Collectors.joining(",")));
        // _release
        updateTracker.setAttributeValue(ReactomeJavaConstants._release, release);
        // updatedEvent
        updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);
        // _displayName
        InstanceDisplayNameGenerator.setDisplayName(updateTracker);

        return updateTracker;
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
    private Set<String> getRLERevisions(GKInstance sourceRLE, GKInstance previousSliceRLE) throws Exception {
        return getEventRevisions(sourceRLE, 
                                 previousSliceRLE, 
                                 ReactomeJavaConstants.input,
                                 ReactomeJavaConstants.output,
                                 ReactomeJavaConstants.regulatedBy,
                                 ReactomeJavaConstants.catalystActivity,
                                 ReactomeJavaConstants.literatureReference,
                                 ReactomeJavaConstants.summation);
    }

    private Set<String> getPathwayRevisions(GKInstance sourcePathway, GKInstance prePathway) throws Exception {
        return getEventRevisions(sourcePathway, 
                                 prePathway, 
                                 ReactomeJavaConstants.hasEvent,
                                 ReactomeJavaConstants.literatureReference,
                                 ReactomeJavaConstants.summation);
    }

    private Set<String> getEventRevisions(GKInstance sourceEvent,
                                          GKInstance preEvent,
                                          String... attributes) throws Exception {
        if (sourceEvent == null || preEvent == null)
            return null;

        Set<String> actions = new HashSet<String>();
        for (String attrName : attributes) {
            Set<String> tmp = getAttributeRevisions(sourceEvent, preEvent, attrName);
            if (tmp != null)
                actions.addAll(tmp);
        }

        // Check if summation is revised.
        Set<String> summationTextRevision = getEventSummationTextRevisions(sourceEvent, preEvent);
        if (summationTextRevision != null)
            actions.addAll(summationTextRevision);

        return actions;
    }

    /**
     * Check if an instance's summation attribute is revised and return revision strings.
     * e.g. ["addInput", "removeCatalyst", "modifySummation"].
     *
     * @param sourceEvent
     * @param preEvent
     * @return Set
     * @throws InvalidAttributeException
     * @throws Exception
     */
    private Set<String> getEventSummationTextRevisions(GKInstance sourceEvent,
                                                       GKInstance preEvent) throws Exception {
        if (sourceEvent == null || preEvent == null)
            return null;

        List<GKInstance> sourceSummations = sourceEvent.getAttributeValuesList(ReactomeJavaConstants.summation);
        List<GKInstance> previousSliceSummations = preEvent.getAttributeValuesList(ReactomeJavaConstants.summation);

        // Iterate over source summations.
        GKInstance matchingSummation = null;
        Set<String> actions = new HashSet<>();
        for (GKInstance sourceSummation : sourceSummations) {
            matchingSummation =  (GKInstance) getMatchingAttribute(previousSliceSummations, sourceSummation);
            if (matchingSummation == null)
                continue;
            Set<String> actions1 = getAttributeRevisions(sourceSummation, matchingSummation, ReactomeJavaConstants.text);
            if (actions != null)
                actions.addAll(actions1);
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
    private Set<String> getAttributeRevisions(GKInstance sourceInstance,
                                              GKInstance previousSliceInstance,
                                              String attributeName) throws Exception {
        if (sourceInstance == null || previousSliceInstance == null)
            return null;

        List<Object> sourceAttributes = sourceInstance.getAttributeValuesList(attributeName);
        List<Object> previousSliceAttributes = previousSliceInstance.getAttributeValuesList(attributeName);

        // Both attribute lists are empty.
        if (sourceAttributes.size() == 0 && previousSliceAttributes.size() == 0)
            return null;

        // Check for additions.
        boolean addedInstances = containsNewAttribute(sourceAttributes, previousSliceAttributes);
        // Check for deletions.
        boolean removedInstances = containsNewAttribute(previousSliceAttributes, sourceAttributes);

        Set<String> actions = new HashSet<String>();
        // Additions and deletions are both present.
        if (addedInstances && removedInstances) {
            if (sourceInstance.getSchemClass().getAttribute(attributeName).isInstanceTypeAttribute())
                actions.add(UPDATE_ACTION.addRemove + format(attributeName));
            else
                actions.add(UPDATE_ACTION.modify + format(attributeName));
        }
        // Only additions are present.
        else if (addedInstances)
            actions.add(UPDATE_ACTION.add + format(attributeName));
        // Only deletions are present.
        else if (removedInstances)
            actions.add(UPDATE_ACTION.remove + format(attributeName));

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
    private boolean containsNewAttribute(List<Object> sourceAttributes, 
                                         List<Object> previousSliceAttributes) {
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
    private Object getMatchingAttribute(List<?> attributes, Object searchAttribute) {
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

    private enum UPDATE_ACTION {
        add,
        addRemove,
        remove,
        modify,
        update
    }
}
