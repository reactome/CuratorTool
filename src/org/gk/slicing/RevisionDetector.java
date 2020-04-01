package org.gk.slicing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;

/**
 * Create a new _UpdateTracker instance for every updated/revised Event between a current slice and a previous slice.
 */
@SuppressWarnings("unchecked")
public class RevisionDetector {
    private static final Logger logger = Logger.getLogger(RevisionDetector.class);
    // Use a utility class for some methods. Not a good design!
    private SlicingEngine sliceEngine;
    
	public RevisionDetector(SlicingEngine engine) {
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
	        sourceDBA.storeInstance(release);
	        for (GKInstance updateTracker : updateTrackers) {
	            // Move to the sourceDBA version
	            updateTracker.setDBID(null);
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
	    }
	    catch(Exception e) {
	        if (needTrasanction)
	            sourceDBA.rollback();
	        logger.error("Error in uploadUpdateTrackers: " + e.getMessage(), e);
	        throw e;
	    }
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
	 *	 <li>An immediate child RLE revised</li>
	 *	 <ul>
	 *	   <li>A Catalyst added/removed/changed</li>
	 *	   <li>A regulator added/removed/changed</li>
	 *	   <li>A change is made in inputs/outputs</li>
	 *	   <li>A significant change in summation text</li>
	 *	 </ul>
	 * </ol>
	 *
	 * <p><b>A pathway gets a revised flag if:</b></p>
	 * <ol>
	 *	 <li>An immediate child RLE revised
	 *	 <ul>
	 *	   <li>A Catalyst added/removed/changed</li>
	 *	   <li>A regulator added/removed/changed</li>
	 *	   <li>A change is made in inputs/outputs</li>
	 *	   <li>A significant change in summation text</li>
	 *	 </ul>
	 *	 <li>An immediate child RLE is added/removed</li>
	 *	 <li>An immediate child Pathway added/removed</li>
	 *	 <li>An immediate child Pathway is revised (recursively defined).</li>
	 * </ol>
	 *
	 * @param sourceDBA
	 * @param sliceMap
     * @param slicingEngine
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
	 */
    private List<GKInstance> createAllUpdateTrackers(MySQLAdaptor currentSliceDBA,
                                                     MySQLAdaptor previousSliceDBA) throws Exception {
	    if (previousSliceDBA == null)
            return null;
	    GKInstance release = getReleaseInstance(currentSliceDBA);
	    GKInstance defaultIE = sliceEngine.createDefaultIE(currentSliceDBA);
	    // Check revisions
	    Collection<GKInstance> events = currentSliceDBA.fetchInstancesByClass(ReactomeJavaConstants.Event);
	    List<GKInstance> updateTrackers = new ArrayList<>();
	    // Iterate over all instances in the slice.
	    GKInstance updateTracker = null;
        for (GKInstance sourceEvent : events) {
            updateTracker = createUpdateTracker(currentSliceDBA,
                                                previousSliceDBA, 
                                                sourceEvent, 
                                                release,
                                                defaultIE);
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
	 * @param currentEvent
	 * @param currentSliceDBA
	 * @return GKInstance
	 * @throws Exception
	 */
    private GKInstance createUpdateTracker(MySQLAdaptor currentSliceDBA,
                                           MySQLAdaptor previousSliceDBA,
                                           GKInstance currentEvent,
                                           GKInstance release,
                                           GKInstance defaultIE) throws Exception {
	    Set<String> actions = null;
	    GKInstance previousSliceEvent = previousSliceDBA.fetchInstance(currentEvent.getDBID());
	    if (currentEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        actions = getPathwayRevisions(currentEvent, previousSliceEvent);
	    else if (currentEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        actions = getRLERevisions(currentEvent, previousSliceEvent);

	    // No revision detected.
	    if (actions == null || actions.size() == 0)
	        return null;

	    // If a revision condition is met, create new _UpdateTracker instance.
	    SchemaClass cls = currentSliceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	    GKInstance updateTracker = new GKInstance(cls);
	    updateTracker.setDbAdaptor(currentSliceDBA);

	    updateTracker.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
	    // action
	    updateTracker.setAttributeValue(ReactomeJavaConstants.action, String.join(",", actions));

	    // _release
	    updateTracker.setAttributeValue(ReactomeJavaConstants._release, release);

	    // updatedEvent
	    updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, currentEvent);

	    // _displayName
	    InstanceDisplayNameGenerator.setDisplayName(updateTracker);

	    return updateTracker;
	}

	/**
	 * Return a list of pathway revisions.
	 *
	 * @param sourcePathway
	 * @param previousSlicePathway
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private Set<String> getPathwayRevisions(GKInstance sourcePathway, GKInstance previousSlicePathway) throws InvalidAttributeException, Exception {
	    if (sourcePathway == null || previousSlicePathway == null)
	        return null;

	    if (!sourcePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway) ||
	        !previousSlicePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return null;

	    // Check if a child event (pathway or RLE) is added or removed.
	    Set<String> actions = getAttributeRevisions(sourcePathway, previousSlicePathway, ReactomeJavaConstants.hasEvent);

		List<Object> sourceEvents = sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		List<Object> previousSliceEvents = previousSlicePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
	    GKInstance previousSliceEvent = null;
	    Set<String> tmp = null;

	    // Recursively iterate over and apply revision detection to all events in the pathway.
	    for (Object sourceEvent : sourceEvents) {
		    previousSliceEvent = (GKInstance) getMatchingAttribute(previousSliceEvents, sourceEvent);
		    if (previousSliceEvent == null)
		        continue;

		    // Child pathways.
		    tmp = getPathwayRevisions((GKInstance) sourceEvent, previousSliceEvent);
		    if (tmp != null) actions.addAll(tmp);

		    // Child RLE's.
		    tmp = getRLERevisions((GKInstance) sourceEvent, previousSliceEvent);
		    if (tmp != null) actions.addAll(tmp);
	    }

		return actions;
	}


	/**
	 * Return a list of RLE revisions.
	 *
	 * @param sourceRLE
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private Set<String> getRLERevisions(GKInstance sourceRLE, GKInstance previousSliceRLE) throws InvalidAttributeException, Exception {
	    if (sourceRLE == null || previousSliceRLE == null)
	        return null;

	    if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) ||
	        !previousSliceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return null;

		// Check for changes in inputs, outputs, regulators, and catalysts.
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
	 * Check if an instance's summation attribute is revised.
	 *
	 * @param sourceRLE
	 * @param previousSliceRLE
	 * @return List
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
	 * Compare the value of a given attribute between two instances.
	 *
	 * @param sourceInstance
	 * @param previousSliceInstance
	 * @param attributeName
	 * @return List
	 * @throws Exception
	 */
	private Set<String> getAttributeRevisions(Object sourceInstance, Object previousSliceInstance, String attributeName) throws Exception {
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

		if (addedInstances && removedInstances) {
		    // GKInstance attributes.
		    if (sourceAttributes.get(0) instanceof GKInstance)
		        actions.add(ReactomeJavaConstants.addRemove + format(attributeName));

		    // Non-GKInstance attributes (e.g. summary text).
		    else
		        actions.add(ReactomeJavaConstants.modify + format(attributeName));
		}

		else if (addedInstances)
		    actions.add(ReactomeJavaConstants.add + format(attributeName));

		else if (removedInstances)
		    actions.add(ReactomeJavaConstants.remove + format(attributeName));

		return actions;
	}

	/**
	 * Return true if a new attribute is added to previousSliceAttributes.
	 * (i.e. not all instances in "sourceAttributes" are also contained in "previousSliceAttributes").
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
	 * Simple utility method to capitalize an input string (e.g. "hazelnut" -> "Hazelnut").
	 *
	 * @param input
	 * @return String
	 */
	private String format(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1, input.length());
	}
}
