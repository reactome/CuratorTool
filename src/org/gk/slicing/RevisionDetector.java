package org.gk.slicing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
	private String sliceMapFile;
	private final String delimiter = ",";

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
	    List<String> actions = null;

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
	        updatedEvent = sourceDBA.fetchInstance(sourceEvent.getDBID());

	        updateTracker.setAttributeValue(ReactomeJavaConstants.action, listToString(actions));

	        updateTracker.setAttributeValue(ReactomeJavaConstants.stableIdentifier, null);

	        updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);

	        updateTracker.setAttributeValue(ReactomeJavaConstants._release, null);

	        updateTracker.setDisplayName(updatedEvent.toString());

	        updateTracker.setDBID(sourceDBA.storeInstance(updatedEvent));

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
	private List<String> isPathwayRevised(GKInstance sourcePathway, GKInstance targetPathway)
			throws InvalidAttributeException, Exception {

	    if (!sourcePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return null;

	    // Check if a child event (pathway or RLE) is added or removed.
	    List<String> actions = getAttributeRevisions(sourcePathway, targetPathway, ReactomeJavaConstants.hasEvent);

		List<Object> sourceEvents = sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		List<Object> targetEvents = targetPathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
	    GKInstance targetEvent = null;
	    List<String> tmp = null;

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
	private List<String> isRLERevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

	    if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return null;

	    List<String> actions = new ArrayList<String>();
	    List<String> tmp = null;

		// Check for changes in inputs, outputs, regulators, and catalysts.
		List<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
                                                  ReactomeJavaConstants.output,
                                                  ReactomeJavaConstants.regulatedBy,
                                                  ReactomeJavaConstants.catalystActivity);
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
	private List<String> isRLESummationRevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

	    List<String> actions = getAttributeRevisions(sourceRLE, targetRLE, ReactomeJavaConstants.summation);
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
	private List<String> getAttributeRevisions(Object sourceInstance, Object targetInstance, String attribute) throws Exception {

		List<Object> sourceAttributes = ((GKInstance) sourceInstance).getAttributeValuesList(attribute);
		List<Object> targetAttributes = ((GKInstance) targetInstance).getAttributeValuesList(attribute);

		// Both attribute lists are empty.
		if (sourceAttributes.size() == 0 && targetAttributes.size() == 0)
		    return null;

        List<String> actions = new ArrayList<String>();

	    // Check for additions.
		int addedInstances = numberOfUniqueAttributes(sourceAttributes, targetAttributes);
		for (int i = 0; i < addedInstances; i++)
		    actions.add(ReactomeJavaConstants.add + format(attribute));

		// Check for deletions.
		int removedInstances = numberOfUniqueAttributes(targetAttributes, sourceAttributes);
		for (int i = 0; i < removedInstances; i++)
		    actions.add(ReactomeJavaConstants.remove + format(attribute));

		return actions;
	}

	/**
	 * Return the number of all instances that are not shared between two parent lists.
	 *
	 * @param sourceAttributes
	 * @param targetAttributes
	 * @return List
	 */
	private int numberOfUniqueAttributes(List<Object> sourceAttributes, List<Object> targetAttributes) {
	    int numberUniqueAttributes = 0;

	    // If targetInstances does not contain an instance in sourceInstances, then sourceInstances has had an addition.
	    Object matchingAttribute = null;
	    for (Object sourceInstance: sourceAttributes) {
	        matchingAttribute = getMatchingAttribute(targetAttributes, sourceInstance);
	        if (matchingAttribute == null)
	            numberUniqueAttributes += 1;
	    }

	    return numberUniqueAttributes;
	}

	/**
	 * Search a list of attribute values for a given instance value or DBID.
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
	 * Convert a String list to a delimited String.
	 *
	 * @param list
	 * @return String
	 */
	private String listToString(List<String> list) {
	    if (list == null || list.size() == 0)
	        return null;

	    if (list.size() == 1)
	        return list.get(0);

	    String output = list.get(0);
	    for (String string : list.subList(1, list.size()))
	        output += delimiter + string;
	    return output;
	}

	/*
	 * Test utility method.
	 * Read a slice map object from file (for caching use in debugging).
	 *
	 * @param inFile
	 * @return Map
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private Map<Long, GKInstance> readSliceMap(String inFile) throws IOException, ClassNotFoundException {
	    try (
            FileInputStream fileInput = new FileInputStream(inFile);
            BufferedInputStream bufferedInput = new BufferedInputStream(fileInput);
            ObjectInputStream objectInput = new ObjectInputStream(bufferedInput);
        ) {
            return (Map<Long, GKInstance>) objectInput.readObject();
	    }
	}

	@Before
	public void setUp() throws SQLException {
	    String host = "localhost";
	    String user = "liam";
	    String pass = ")8J7m]!%[<";
		sourceDBATest = new MySQLAdaptor(host, "central", user, pass);
		previousSliceDBATest = new MySQLAdaptor(host, "previous_slice", user, pass);
		// Slice map cache file.
		sliceMapFile = "sliceMap.ser";
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
	 *        "assertEquals(source instance is revised)".</li>
	 * </ol>
	 */
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
		actions = listToString(isPathwayRevised(sourcePathway, targetPathway));
		assertNull(actions);

		// Add a new child pathway (tRNA processing).
		sourceChildPathway = sourceDBATest.fetchInstance(72306L);
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourcePathway);
		actions = "addHasEvent";
		assertEquals(actions, listToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Reset the addition.
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertNull(listToString(isPathwayRevised(sourcePathway, targetPathway)));


		// Example pathway #2 (DNA repair).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(353377L);
		targetPathway = (GKInstance) previousSliceDBATest.fetchInstance(353377L);
		assertNull(listToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Remove an existing child pathway (Base Excision Repair).
		sourceChildPathway = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		actions = "removeHasEvent";
		assertEquals(actions, listToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Reset the removal.
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertNull(listToString(isPathwayRevised(sourcePathway, targetPathway)));


		// Revise a child pathway (by changing the summation text of one of it's RLEs).
		sourceRLE = (GKInstance) sourceDBATest.fetchInstance(353484L);
		GKInstance sourceRLESummation = (GKInstance) sourceRLE.getAttributeValue(ReactomeJavaConstants.summation);
		String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		actions = "addText,removeText";
		assertEquals(actions, listToString(isPathwayRevised(sourcePathway, targetPathway)));

		// Reset the revision.
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertNull(listToString(isPathwayRevised(sourcePathway, targetPathway)));
	}

	@Test
	public void testIsRLERevised() throws Exception {
	    String actions = null;
	    String attribute = null;
		Long revisedAttributeDBID = null;
		List<Object> originalAttributes = null;

		// Example RLE (AXL binds SRC-1).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(5357432L);
		GKInstance targetRLE = (GKInstance) previousSliceDBATest.fetchInstance(5357432L);
		assertNull(listToString(isRLERevised(sourceRLE, targetRLE)));

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
	    assertEquals(actions, listToString(isRLERevised(sourceRLE, targetRLE)));

	    // Reset revision.
	    sourceRLE.setAttributeValue(attribute, originalAttributes);
	    assertNull(listToString(isRLERevised(sourceRLE, targetRLE)));
	}

	@Test
	public void testIsRLESummationRevised() throws Exception {
	    String actions = null;
	    String attribute = ReactomeJavaConstants.text;

		// Example RLE (A1 and A3 receptors bind adenosine).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(418904L);
		GKInstance targetRLE = (GKInstance) previousSliceDBATest.fetchInstance(418904L);
		assertNull(listToString(isRLESummationRevised(sourceRLE, targetRLE)));

        // Revise summation text.
		GKInstance sourceSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		sourceSummation.setAttributeValue(attribute, "revised");
		actions = "addText,removeText";
		assertEquals(actions, listToString(isRLESummationRevised(sourceRLE, targetRLE)));

		// Reset the revision,
		GKInstance targetSummation = (GKInstance) targetRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		String targetSummationText = (String) targetSummation.getAttributeValue(attribute);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, targetSummationText);
		assertNull(listToString(isRLESummationRevised(sourceRLE, targetRLE)));
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
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Change attribute value.
		sourceInstance.setAttributeValue(attribute, "revised");
		actions = "add_displayName,remove_displayName";
		assertEquals(actions, listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset change.
		sourceInstance.setAttributeValue(attribute, targetInstance.getAttributeValue(attribute));
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		//----------------------------------//
		// Revision detection (GKInstance). //
		//----------------------------------//

		attribute = ReactomeJavaConstants.regulatedBy;

		// Example RLE (DIT and MIT combine to form triiodothyronine).
		sourceInstance = (GKInstance) sourceDBATest.fetchInstance(209925L);
		targetInstance = (GKInstance) previousSliceDBATest.fetchInstance(209925L);
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		List<Object> originalAttributes = new ArrayList<Object>(sourceInstance.getAttributeValuesList(attribute));
		GKInstance newAttributeInstance = sourceDBATest.fetchInstance(5210962L);
		sourceInstance.addAttributeValue(attribute, newAttributeInstance);
		actions = "addRegulatedBy";
		assertEquals(actions, listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Remove original attribute instance.
		sourceInstance.setAttributeValue(attribute, newAttributeInstance);
		actions = "addRegulatedBy,removeRegulatedBy";
		assertEquals(actions, listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Remove all attribute instances.
		sourceInstance.setAttributeValue(attribute, null);
		actions = "removeRegulatedBy";
		assertEquals(actions, listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset revisions.
		sourceInstance.setAttributeValue(attribute, originalAttributes);
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		//------------------------------------//
		// Event addition/deletion detection. //
		//------------------------------------//

		attribute = ReactomeJavaConstants.hasEvent;

	    // Example pathway (2-LTR circle formation).
	    sourceInstance = sourceDBATest.fetchInstance(164843L);
	    targetInstance = previousSliceDBATest.fetchInstance(164843L);
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

	    // Get a reference to the source instance's event list.
	    List<Object> events = sourceInstance.getAttributeValuesList(attribute);

	    // Add RLE (Reaction (1,25(OH)2D [nucleoplasm])).
	    GKInstance newRLE = sourceDBATest.fetchInstance(8963915L);
	    events.add(newRLE);
		actions = "addHasEvent";
		assertEquals(actions, listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset revision.
	    events.remove(newRLE);
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

	    // Add pathway (Pathway (ABC transporters disorders)).
	    GKInstance newPathway = sourceDBATest.fetchInstance(5619084L);
	    events.add(newPathway);
		actions = "addHasEvent";
		assertEquals(actions, listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));

		// Reset revision.
	    events.remove(newPathway);
		assertNull(listToString(getAttributeRevisions(sourceInstance, targetInstance, attribute)));
	}
}
