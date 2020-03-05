package org.gk.slicing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;

/**
 * Create a new _UpdateTracker instance for every updated/revised Event between a current slice and a previous slice.
 */
public class RevisionDetector {
    private MySQLAdaptor previousSliceDBA;
	private final String delimiter = ",";

	public RevisionDetector() throws SQLException {
	    this(null);
	}

	public RevisionDetector(Properties properties) throws SQLException {
	    if (properties == null)
            return;
        previousSliceDBA = new MySQLAdaptor(properties.getProperty("previousSliceDbHost"),
                                            properties.getProperty("previousSliceDbName"),
                                            properties.getProperty("previousSliceDbUser"),
                                            properties.getProperty("previousSliceDbPwd"),
                                            Integer.parseInt(properties.getProperty("previousSliceDbPort")));
	}

	private MySQLAdaptor getPreviousSliceDBA() {
        return previousSliceDBA;
    }

    public List<GKInstance> getAllUpdateTrackers(Map<Long,GKInstance> sliceMap, MySQLAdaptor sourceDBA)
            throws InvalidAttributeException, Exception {
	    return getAllUpdateTrackers(sliceMap, sourceDBA, getPreviousSliceDBA());
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
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
	 */
	public synchronized List<GKInstance> getAllUpdateTrackers(Map<Long,GKInstance> sliceMap, MySQLAdaptor sourceDBA, MySQLAdaptor previousSliceDBA)
	        throws InvalidAttributeException, Exception {

	    if (previousSliceDBA == null)
            return null;

	    List<GKInstance> updateTrackers = new ArrayList<GKInstance>();
	    GKInstance updateTracker = null;

	    // Iterate over all instances in the slice.
        for (GKInstance sourceEvent : sliceMap.values()) {
            updateTracker = getUpdateTracker(sourceEvent, sourceDBA, previousSliceDBA);
            if (updateTracker != null)
                updateTrackers.add(updateTracker);
        }

	    // DBID attribute (the next available DBID value from sourceDBA).
        Long newDBID = sourceDBA.fetchMaxDbId();
        for (GKInstance tracker : updateTrackers) {
            newDBID += 1;
            tracker.setDBID(newDBID);
        }

        return updateTrackers;
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
	public GKInstance getUpdateTracker(GKInstance sourceEvent, MySQLAdaptor sourceDBA, MySQLAdaptor previousSliceDBA) throws Exception {
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
	        actions = isPathwayRevised(sourceEvent, previousSliceEvent);

	    // RLE
	    else if (sourceEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        actions = isRLERevised(sourceEvent, previousSliceEvent);

	    // No revision detected.
	    if (actions == null || actions.size() == 0)
	        return null;

	    // If a revision condition is met, create new _UpdateTracker instance.
	    cls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	    updateTracker = new GKInstance(cls);

	    // action attribute.
	    updateTracker.setAttributeValue(ReactomeJavaConstants.action, collectionToString(actions));

	    // updatedEvent attribute.
	    updatedEvent = sourceDBA.fetchInstance(sourceEvent.getDBID());
	    updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);

	    // _displayName attribute.
	    updateTracker.setDisplayName(updatedEvent.getDisplayName());

	    // dbAdaptor attribute.
	    updateTracker.setDbAdaptor(sourceDBA);

	    return updateTracker;
	}

	/**
	 * Return a list of pathway revisions.
	 *
	 * TODO Is the recursive approach here recommended over this?
	 * {@link InstanceUtilities#getContainedInstances(GKInstance, String...)}
	 *
	 * @param sourcePathway
	 * @param previousSlicePathway
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private Set<String> isPathwayRevised(GKInstance sourcePathway, GKInstance previousSlicePathway)
			throws InvalidAttributeException, Exception {

	    if (!sourcePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway))
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
		    tmp = isPathwayRevised((GKInstance) sourceEvent, previousSliceEvent);
		    if (tmp != null) actions.addAll(tmp);

		    // Child RLE's.
		    tmp = isRLERevised((GKInstance) sourceEvent, previousSliceEvent);
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
	private Set<String> isRLERevised(GKInstance sourceRLE, GKInstance previousSliceRLE)
			throws InvalidAttributeException, Exception {

	    if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return null;

		// Check for changes in inputs, outputs, regulators, and catalysts.
		List<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
                                                  ReactomeJavaConstants.output,
                                                  ReactomeJavaConstants.regulatedBy,
                                                  ReactomeJavaConstants.catalystActivity);
	    Set<String> actions = new HashSet<String>();
	    Set<String> tmp = null;
		for (String attrName : revisionList) {
			tmp = getAttributeRevisions(sourceRLE, previousSliceRLE, attrName);
		    if (tmp != null)
		        actions.addAll(tmp);
		}

		// Check if summation is revised.
		actions.addAll(isRLESummationRevised(sourceRLE, previousSliceRLE));

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
	private Set<String> isRLESummationRevised(GKInstance sourceRLE, GKInstance previousSliceRLE)
			throws InvalidAttributeException, Exception {

	    Set<String> actions = getAttributeRevisions(sourceRLE, previousSliceRLE, ReactomeJavaConstants.summation);
		List<Object> sourceSummations = sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation);
		List<Object> previousSliceSummations = previousSliceRLE.getAttributeValuesList(ReactomeJavaConstants.summation);

		// Iterate over source summations.
	    Object matchingSummation = null;
		for (Object sourceSummation : sourceSummations) {
		    matchingSummation =  getMatchingAttribute(previousSliceSummations, sourceSummation);
		    actions.addAll(getAttributeRevisions(sourceSummation, matchingSummation, ReactomeJavaConstants.text));
		}

		return actions;
	}

	/**
	 * Compare the value of a given attribute between two instances.
	 *
	 * @param sourceInstance
	 * @param previousSliceInstance
	 * @param attribute
	 * @return List
	 * @throws Exception
	 */
	private Set<String> getAttributeRevisions(Object sourceInstance, Object previousSliceInstance, String attribute) throws Exception {

		List<Object> sourceAttributes = ((GKInstance) sourceInstance).getAttributeValuesList(attribute);
		List<Object> previousSliceAttributes = ((GKInstance) previousSliceInstance).getAttributeValuesList(attribute);
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
		        actions.add(ReactomeJavaConstants.addRemove + format(attribute));

		    // Non-GKInstance attributes (e.g. summary text).
		    else
		        actions.add(ReactomeJavaConstants.modify + format(attribute));
		}

		else if (addedInstances)
		    actions.add(ReactomeJavaConstants.add + format(attribute));

		else if (removedInstances)
		    actions.add(ReactomeJavaConstants.remove + format(attribute));

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

	    // If the attribute list holds GKInstance's, compare DBID's.
	    if (attributes.get(0) instanceof GKInstance) {
	        Long searchAttributeDBID = ((GKInstance) searchAttribute).getDBID();
	        Long attributeDBID = null;
	        for (Object attribute : attributes) {
	            attributeDBID = ((GKInstance) attribute).getDBID();

	            // TODO Is it required to iterate over all attributes of "instance" to determine equality?
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
    void dumpInstances(List<GKInstance> instances, MySQLAdaptor dba) throws Exception {
        SlicingEngine slicingEngine = new SlicingEngine();
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

	/**
	 * Convert a String collection to a delimited String (e.g. [x, y, z] -> "x,y,z").
	 *
	 * @param list
	 * @return String
	 */
	private String collectionToString(Collection<String> list) {
	    if (list == null || list.size() == 0)
	        return null;

	    if (list.size() == 1)
	        return (String) list.iterator().next();

	    String output = new String();
	    Iterator<String> iterator = list.iterator();
	    output = (String) iterator.next();
        while (iterator.hasNext())
	        output += delimiter + iterator.next();
	    return output;
	}
}
