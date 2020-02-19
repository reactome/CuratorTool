package org.gk.slicing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.junit.Before;
import org.junit.Test;

public class RevisionDetector {
	// Database adaptors used for test.
	private MySQLAdaptor sourceDBATest;
	private MySQLAdaptor previousSliceDBATest;

	// SliceMap cache file.
	private String sliceMapFile = "sliceMap.ser";
	private final String delimiter = ",";

	public RevisionDetector() {
	}

	/**
	 * <p>The frontend for detecting revisions in pathways and RLE's between a slice and a previous slice.</p>
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
	 * @param targetDBA
	 * @param sliceMap
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
	 */
	public List<GKInstance> getRevisions(MySQLAdaptor sourceDBA, MySQLAdaptor targetDBA, Map<Long,GKInstance> sliceMap)
	        throws InvalidAttributeException, Exception {

	    GKInstance targetEvent = null;
	    GKInstance updateTracker = null;
	    GKInstance updatedEvent = null;
	    SchemaClass cls = null;
	    List<GKInstance> updateTrackers = new ArrayList<GKInstance>();
	    Set<String> actions = null;

	    // Uncomment to write "n" number of pathways from the sliceMap to a cache file (used for testing).
	    // writeSliceMap(sliceMap, sliceMapFile, 20);

	    // Iterate over all instances in the slice.
        for (GKInstance sourceEvent : sliceMap.values()) {
	        if (!sourceEvent.getSchemClass().isa(ReactomeJavaConstants.Event))
	            continue;

	        targetEvent = targetDBA.fetchInstance(sourceEvent.getDBID());
	        if (sourceEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	            actions = isPathwayRevised(sourceEvent, targetEvent);
	        else if (sourceEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	            actions = isRLERevised(sourceEvent, targetEvent);

	        if (actions == null || actions.size() == 0) continue;

	        // If a revision condition is met, create new _UpdateTracker instance.
	        cls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	        updateTracker = new GKInstance(cls);

	        // action
	        updateTracker.setAttributeValue(ReactomeJavaConstants.action, collectionToString(actions));

	        // updatedEvent
	        updatedEvent = sourceDBA.fetchInstance(sourceEvent.getDBID());
	        updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);

	        // _displayName
	        updateTracker.setDisplayName(updatedEvent.toString());

	        // dbAdaptor
	        updateTracker.setDbAdaptor(sourceDBA);

	        // DBID (the updateTracker instance is added to the "source" database to get a new DBID).
	        updateTracker.setDBID(sourceDBA.storeInstance(updateTracker));

	        updateTrackers.add(updateTracker);
        }

        return updateTrackers;
	}

	/**
	 * Return a list of pathway revisions.
	 *
	 * TODO Is the recursive approach here recommended over this?
	 * {@link InstanceUtilities#getContainedInstances(GKInstance, String...)}
	 *
	 * @param sourcePathway
	 * @param targetPathway
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private Set<String> isPathwayRevised(GKInstance sourcePathway, GKInstance targetPathway)
			throws InvalidAttributeException, Exception {

	    if (!sourcePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return null;

	    // Check if a child event (pathway or RLE) is added or removed.
	    Set<String> actions = getAttributeRevisions(sourcePathway, targetPathway, ReactomeJavaConstants.hasEvent);

		List<Object> sourceEvents = sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		List<Object> targetEvents = targetPathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
	    GKInstance targetEvent = null;
	    Set<String> tmp = null;

	    // Recursively iterate over and apply revision detection to all events in the pathway.
	    for (Object sourceEvent : sourceEvents) {
		    targetEvent = (GKInstance) getMatchingAttribute(targetEvents, sourceEvent);
		    if (targetEvent == null)
		        continue;

		    // Child pathways.
		    tmp = isPathwayRevised((GKInstance) sourceEvent, targetEvent);
		    if (tmp != null) actions.addAll(tmp);

		    // Child RLE's.
		    tmp = isRLERevised((GKInstance) sourceEvent, targetEvent);
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
	private Set<String> isRLERevised(GKInstance sourceRLE, GKInstance targetRLE)
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
			tmp = getAttributeRevisions(sourceRLE, targetRLE, attrName);
		    if (tmp != null)
		        actions.addAll(tmp);
		}

		// Check if summation is revised.
		actions.addAll(isRLESummationRevised(sourceRLE, targetRLE));

		return actions;
	}

	/**
	 * Check if an instance's summation attribute is revised.
	 *
	 * @param sourceRLE
	 * @param targetRLE
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 *
	 * @see {@link org.gk.model.Summation}
	 */
	private Set<String> isRLESummationRevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

	    Set<String> actions = getAttributeRevisions(sourceRLE, targetRLE, ReactomeJavaConstants.summation);
		List<Object> sourceSummations = sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation);
		List<Object> targetSummations = targetRLE.getAttributeValuesList(ReactomeJavaConstants.summation);

		// Iterate over source summations.
	    Object matchingSummation = null;
		for (Object sourceSummation : sourceSummations) {
		    matchingSummation =  getMatchingAttribute(targetSummations, sourceSummation);
		    actions.addAll(getAttributeRevisions(sourceSummation, matchingSummation, ReactomeJavaConstants.text));
		}

		return actions;
	}

	/**
	 * Compare the value of a given attribute between two instances.
	 *
	 * @param sourceInstance
	 * @param targetInstance
	 * @param attribute
	 * @return List
	 * @throws Exception
	 */
	private Set<String> getAttributeRevisions(Object sourceInstance, Object targetInstance, String attribute) throws Exception {

		List<Object> sourceAttributes = ((GKInstance) sourceInstance).getAttributeValuesList(attribute);
		List<Object> targetAttributes = ((GKInstance) targetInstance).getAttributeValuesList(attribute);
        Set<String> actions = new HashSet<String>();

		// Both attribute lists are empty.
		if (sourceAttributes.size() == 0 && targetAttributes.size() == 0)
		    return null;

	    // Check for additions.
		boolean addedInstances = containsNewAttribute(sourceAttributes, targetAttributes);

		// Check for deletions.
		boolean removedInstances = containsNewAttribute(targetAttributes, sourceAttributes);

		if (!addedInstances && !removedInstances) {
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
	 * Return true if a new attribute is added to targetAttributes.
	 * (i.e. not all instances in "sourceAttributes" are also contained in "targetAttributes").
	 *
	 * @param sourceAttributes
	 * @param targetAttributes
	 * @return boolean
	 */
	private boolean containsNewAttribute(List<Object> sourceAttributes, List<Object> targetAttributes) {
	    // If targetInstances does not contain an instance in sourceInstances, then sourceInstances has had an addition.
	    Object matchingAttribute = null;
	    for (Object sourceInstance: sourceAttributes) {
	        matchingAttribute = getMatchingAttribute(targetAttributes, sourceInstance);
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
	 * Simple utility method to capitalize an input String (e.g. "hazelnut" -> "Hazelnut").
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

	    Iterator<String> iterator = list.iterator();
	    String output = (String) iterator.next();
        while (iterator.hasNext())
	        output += delimiter + iterator.next();
	    return output;
	}

	/**
	 * Test utility method.
	 * Read a slice map object from file (for caching use in debugging).
	 *
	 * @param inFile
	 * @return Map
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public Map<Long, GKInstance> readSliceMap(String inFile) throws IOException, ClassNotFoundException {
	    try (
            FileInputStream fileInput = new FileInputStream(inFile);
            BufferedInputStream bufferedInput = new BufferedInputStream(fileInput);
            ObjectInputStream objectInput = new ObjectInputStream(bufferedInput);
        ) {
            return (Map<Long, GKInstance>) objectInput.readObject();
	    }
	}

    /**
	 * Test utility method.
	 * Write a slice map object to file (for caching use in debugging).
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void writeSliceMap(Map<Long, GKInstance> sliceMap, String outFile, int limit) throws FileNotFoundException, IOException {
        try (
                FileOutputStream fileOutput = new FileOutputStream(outFile);
                BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput);
                ObjectOutputStream objectOutput = new ObjectOutputStream(bufferedOutput);
        ) {
            Map<Long, GKInstance> smallSliceMap = new HashMap<Long, GKInstance>();
            int i = 0;
            for (Map.Entry<Long, GKInstance> entry : sliceMap.entrySet()) {
                // Limit to event instances.
                if (!entry.getValue().getSchemClass().isa(ReactomeJavaConstants.Event))
                    continue;

                smallSliceMap.put(entry.getKey(), entry.getValue());

                // Stop early if the desired number of pathways are added to the cache file.
                if (entry.getValue().getSchemClass().isa(ReactomeJavaConstants.Pathway))
                    if (i++ > limit) break;
            }
            // Serialize sliceMap object to file.
            objectOutput.writeObject(smallSliceMap);
        }
    }

    /**
     * <p>In the tests below:</p>
     * <ol>
     *   <li>A "source" instance is fetched from the test database "central"</li>
     *   <li>A "target" instance is fetched from the test database "previous_slice".</li>
     *   <li>The "source" instance is modified and compared against the "target" instance --
     *       "assertEquals(source instance is revised)".</li>
     *   <li>The revision is reset.</li>
     *   <li>The "source" instance is again compared against the "target" instance --
     *        "assertNull(source instance is revised)".</li>
     * </ol>
     */
    @Before
    public void setUp() throws SQLException {
        String host = "localhost";
        String user = "liam";
	    String pass = ")8J7m]!%[<";
		sourceDBATest = new MySQLAdaptor(host, "central", user, pass);
		previousSliceDBATest = new MySQLAdaptor(host, "previous_slice", user, pass);
	}

	@Test
	public void testGetRevisions() throws InvalidAttributeException, Exception {
	    List<GKInstance> updateTrackers = null;
	    Map<Long, GKInstance> sliceMap = readSliceMap(sliceMapFile);

	    // Start with five revisions between the two "slices".
	    updateTrackers = getRevisions(sourceDBATest, previousSliceDBATest, sliceMap);
	    assertEquals(updateTrackers.size(), 5);
	}

	@Test
	public void testIsPathwayRevised() throws Exception {
	    GKInstance sourcePathway = null;
	    GKInstance targetPathway = null;
	    GKInstance sourceChildPathway = null;
	    GKInstance sourceRLE = null;
	    String actions = null;

		// Example pathway #1 (Acetylation).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(156582L);
		targetPathway = (GKInstance) previousSliceDBATest.fetchInstance(156582L);
		actions = collectionToString(isPathwayRevised(sourcePathway, targetPathway));
		assertNull(actions);

		// Add a new child pathway (tRNA processing).
		sourceChildPathway = sourceDBATest.fetchInstance(72306L);
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourcePathway);
		actions = "addHasEvent";
		assertEquals(actions, collectionToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Reset the addition.
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertNull(collectionToString(isPathwayRevised(sourcePathway, targetPathway)));


		// Example pathway #2 (DNA repair).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(353377L);
		targetPathway = (GKInstance) previousSliceDBATest.fetchInstance(353377L);
		assertNull(collectionToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Remove an existing child pathway (Base Excision Repair).
		sourceChildPathway = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		actions = "removeHasEvent";
		assertEquals(actions, collectionToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Reset the removal.
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertNull(collectionToString(isPathwayRevised(sourcePathway, targetPathway)));


		// Revise a child pathway (by changing the summation text of one of it's RLEs).
		sourceRLE = (GKInstance) sourceDBATest.fetchInstance(353484L);
		GKInstance sourceRLESummation = (GKInstance) sourceRLE.getAttributeValue(ReactomeJavaConstants.summation);
		String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		actions = "modifyText";
		assertEquals(actions, collectionToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Reset the revision.
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertNull(collectionToString(isPathwayRevised(sourcePathway, targetPathway)));
	}

	@Test
	public void testIsRLERevised() throws Exception {
	    String actions = null;
	    String attribute = null;
		Long revisedAttributeDBID = null;

		// Example RLE (AXL binds SRC-1).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(5357432L);
		GKInstance targetRLE = (GKInstance) previousSliceDBATest.fetchInstance(5357432L);
		assertNull(collectionToString(isRLERevised(sourceRLE, targetRLE)));

		// Revise input (ALS2 dimer [cytosol]).
	    attribute = ReactomeJavaConstants.input;
	    revisedAttributeDBID = 8875299L;
		actions = "addInput";
		RLETestRunner(sourceRLE, targetRLE, attribute, revisedAttributeDBID, actions);

		// Revise output (AUF1 p37 dimer [cytosol]).
	    attribute = ReactomeJavaConstants.output;
	    revisedAttributeDBID = 450539L;
		actions = "addOutput";
		RLETestRunner(sourceRLE, targetRLE, attribute, revisedAttributeDBID, actions);

		// Revise regulatedBy (Negative regulation by 'ARL2 [cytosol]').
	    attribute = ReactomeJavaConstants.regulatedBy;
	    revisedAttributeDBID = 5255416L;
		actions = "addRegulatedBy";
		RLETestRunner(sourceRLE, targetRLE, attribute, revisedAttributeDBID, actions);

		// Revise catalystActivity (ATPase activity of NSF [cytosol]).
	    attribute = ReactomeJavaConstants.catalystActivity;
	    revisedAttributeDBID = 416995L;
		actions = "addCatalystActivity";
		RLETestRunner(sourceRLE, targetRLE, attribute, revisedAttributeDBID, actions);

		// Revise species -- should not trigger revision (Felis catus).
	    attribute = ReactomeJavaConstants.species;
	    revisedAttributeDBID = 164922L;
	    actions = null;
		RLETestRunner(sourceRLE, targetRLE, attribute, revisedAttributeDBID, actions);
	}

	/**
	 * Test runner for RLE instances.
	 *
	 * @param sourceRLE
	 * @param targetRLE
	 * @param attribute
	 * @param revisedAttributeDBID
	 * @param actions
	 * @throws Exception
	 */
	private void RLETestRunner(GKInstance sourceRLE,
                               GKInstance targetRLE,
                               String attribute,
                               Long revisedAttributeDBID,
                               String actions) throws Exception {

		List<Object> originalAttributes = new ArrayList<Object>(sourceRLE.getAttributeValuesList(attribute));
		if (originalAttributes.size() == 0)
		    originalAttributes = null;

		// Add attribute value.
	    GKInstance revisedAttribute = (GKInstance) sourceDBATest.fetchInstance(revisedAttributeDBID);
	    sourceRLE.addAttributeValue(attribute, revisedAttribute);
	    assertEquals(actions, collectionToString(isRLERevised(sourceRLE, targetRLE)));

	    // Reset revision.
	    sourceRLE.setAttributeValue(attribute, originalAttributes);
	    assertNull(collectionToString(isRLERevised(sourceRLE, targetRLE)));
	}

	@Test
	public void testIsRLESummationRevised() throws Exception {
	    String actions = null;
	    String attribute = ReactomeJavaConstants.text;

		// Example RLE (A1 and A3 receptors bind adenosine).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(418904L);
		GKInstance targetRLE = (GKInstance) previousSliceDBATest.fetchInstance(418904L);
		assertNull(collectionToString(isRLESummationRevised(sourceRLE, targetRLE)));

        // Revise summation text.
		GKInstance sourceSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		sourceSummation.setAttributeValue(attribute, "revised");
		actions = "modifyText";
		assertEquals(actions, collectionToString(isRLESummationRevised(sourceRLE, targetRLE)));

		// Reset the revision,
		GKInstance targetSummation = (GKInstance) targetRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		String targetSummationText = (String) targetSummation.getAttributeValue(attribute);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, targetSummationText);
		assertNull(collectionToString(isRLESummationRevised(sourceRLE, targetRLE)));
	}

	@Test
	public void testisAttributeRevised() throws Exception {
	    String actions = null;
	    String attribute = null;
	    GKInstance sourceInstance = null;
	    GKInstance targetInstance = null;

		//--------------------------------------//
		// Revision detection (non-GKInstance). //
		//--------------------------------------//

	    attribute = ReactomeJavaConstants._displayName;

		// (DOCK7) [cytosol]
		sourceInstance = sourceDBATest.fetchInstance(8875579L);
		targetInstance = previousSliceDBATest.fetchInstance(8875579L);
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Change attribute value.
		sourceInstance.setAttributeValue(attribute, "revised");
		actions = "modify_displayName";
		assertEquals(actions, collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset change.
		sourceInstance.setAttributeValue(attribute, targetInstance.getAttributeValue(attribute));
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		//----------------------------------//
		// Revision detection (GKInstance). //
		//----------------------------------//

		attribute = ReactomeJavaConstants.regulatedBy;

		// Example RLE (DIT and MIT combine to form triiodothyronine).
		sourceInstance = (GKInstance) sourceDBATest.fetchInstance(209925L);
		targetInstance = (GKInstance) previousSliceDBATest.fetchInstance(209925L);
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Original attributes.
		List<Object> originalAttributes = new ArrayList<Object>(sourceInstance.getAttributeValuesList(attribute));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		GKInstance newAttributeInstance = sourceDBATest.fetchInstance(5210962L);
		sourceInstance.addAttributeValue(attribute, newAttributeInstance);
		actions = "addRegulatedBy";
		assertEquals(actions, collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Remove original attribute instance.
		sourceInstance.setAttributeValue(attribute, newAttributeInstance);
		actions = "add/removeRegulatedBy";
		assertEquals(actions, collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Remove all attribute instances.
		sourceInstance.setAttributeValue(attribute, null);
		actions = "removeRegulatedBy";
		assertEquals(actions, collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset revisions.
		sourceInstance.setAttributeValue(attribute, originalAttributes);
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		//------------------------------------//
		// Event addition/deletion detection. //
		//------------------------------------//

		attribute = ReactomeJavaConstants.hasEvent;

	    // Example pathway (2-LTR circle formation).
	    sourceInstance = sourceDBATest.fetchInstance(164843L);
	    targetInstance = previousSliceDBATest.fetchInstance(164843L);
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

	    // Get a reference to the source instance's event list.
	    List<Object> events = sourceInstance.getAttributeValuesList(attribute);
	    originalAttributes = new ArrayList<Object>(events);

	    // Add RLE (Reaction (1,25(OH)2D [nucleoplasm])).
	    GKInstance newRLE = sourceDBATest.fetchInstance(8963915L);
	    events.add(newRLE);
		actions = "addHasEvent";
		assertEquals(actions, collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset revision.
	    events.remove(newRLE);
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

	    // Add pathway (ABC transporters disorders).
	    GKInstance newPathway = sourceDBATest.fetchInstance(5619084L);
	    events.add(newPathway);
	    // Add second pathway (Actin assembly).
	    newPathway = sourceDBATest.fetchInstance(419989L);
	    events.add(newPathway);
		actions = "addHasEvent";
		assertEquals(actions, collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset revision.
		sourceInstance.setAttributeValue(attribute, originalAttributes);
		assertNull(collectionToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));
	}
}
