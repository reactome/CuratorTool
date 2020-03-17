package org.gk.slicing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.database.DefaultInstanceEditHelper;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;

/**
 * Create a new _UpdateTracker instance for every updated/revised Event between a current slice and a previous slice.
 */
public class RevisionDetector {
	public RevisionDetector() {
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
	public synchronized List<GKInstance> createAllUpdateTrackers(MySQLAdaptor sourceDBA,
                                                                 MySQLAdaptor previousSliceDBA,
                                                                 Map<Long,GKInstance> sliceMap,
                                                                 Long defaultPersonId,
                                                                 Integer releaseNumber,
                                                                 String releaseDate,
                                                                 SlicingEngine slicingEngine) throws InvalidAttributeException, Exception {
	    if (previousSliceDBA == null)
            return null;

	    List<GKInstance> newInstances = new ArrayList<GKInstance>();

	    // created
	    GKInstance defaultIE = slicingEngine.createDefaultIE(sourceDBA, defaultPersonId);
	    newInstances.add(defaultIE);

	    // _release
	    GKInstance release = slicingEngine.createReleaseInstance(sourceDBA, releaseNumber, releaseDate);
	    newInstances.add(release);

	    // Iterate over all instances in the slice.
	    GKInstance updateTracker = null;
        for (GKInstance sourceEvent : sliceMap.values()) {
            updateTracker = createUpdateTracker(sourceDBA, previousSliceDBA, sourceEvent, defaultIE, release);
            if (updateTracker != null) {
                newInstances.add(updateTracker);
            }
        }

	    // DBID attribute (the next available DBID value from sourceDBA).
        Long newDBID = sourceDBA.fetchMaxDbId();
        for (GKInstance newInstance : newInstances) {
            newDBID += 1;
            newInstance.setDBID(newDBID);
        }

        return newInstances;
	}

	/**
	 * Return a single _UpdateTracker instance for a given "Event" instance.
	 *
	 * Return null if the passed instance is not an Event or no revisions were detected.
	 *
	 * @param sourceEvent
	 * @param sourceDBA
	 * @return GKInstance
	 * @throws Exception
	 */
	GKInstance createUpdateTracker(MySQLAdaptor sourceDBA,
                                   MySQLAdaptor previousSliceDBA,
                                   GKInstance sourceEvent,
                                   GKInstance defaultIE,
                                   GKInstance release) throws Exception {
	    if (!sourceEvent.getSchemClass().isa(ReactomeJavaConstants.Event))
	        return null;

	    GKInstance previousSliceEvent = null;
	    GKInstance updateTracker = null;
	    GKInstance updatedEvent = null;
	    SchemaClass cls = null;
	    Set<String> actions = null;
	    previousSliceEvent = previousSliceDBA.fetchInstance(sourceEvent.getDBID());

	    // Pathway
	    if (sourceEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        actions = getPathwayRevisions(sourceEvent, previousSliceEvent);

	    // RLE
	    else if (sourceEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        actions = getRLERevisions(sourceEvent, previousSliceEvent);

	    // No revision detected.
	    if (actions == null || actions.size() == 0)
	        return null;

	    // If a revision condition is met, create new _UpdateTracker instance.
	    cls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	    updateTracker = new GKInstance(cls);
	    updateTracker.setDbAdaptor(sourceDBA);

	    // action
	    updateTracker.setAttributeValue(ReactomeJavaConstants.action, String.join(",", actions));

	    // created
	    updateTracker.setAttributeValue(ReactomeJavaConstants.created, defaultIE);

	    // _release
	    updateTracker.setAttributeValue(ReactomeJavaConstants._release, release);

	    // updatedEvent
	    updatedEvent = sourceDBA.fetchInstance(sourceEvent.getDBID());
	    updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);

	    // _displayName
	    updateTracker.setDisplayName(InstanceDisplayNameGenerator.generateDisplayName(updateTracker));

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
		actions.addAll(getRLESummationTextRevisions(sourceRLE, previousSliceRLE));

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
     * Save _UpdateTracker instances to the provided database (typically "sourceDBA").
     *
	 * Adapted from {@link SlicingEngine#dumpInstances} to take "instances" and "dba" as parameters.
	 *
	 * @param dba
	 * @param instances
	 * @throws Exception
	 */
    void dumpInstances(List<GKInstance> instances, MySQLAdaptor dba, SlicingEngine slicingEngine) throws Exception {
        // Try to use transaction
        boolean isTnSupported = dba.supportsTransactions();
        if (isTnSupported)
            dba.startTransaction();
        try {
            for (GKInstance instance : instances)
                slicingEngine.storeInstance(instance, dba);

            if (isTnSupported)
                dba.commit();
        }
        catch (Exception e) {
            if (isTnSupported)
                dba.rollback();
            e.printStackTrace();
        }
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
