package org.gk.slicing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.junit.Before;
import org.junit.Test;

public class RevisionDetector {
	// Database adaptors used for test.
	private MySQLAdaptor centralTestDBA;
	private MySQLAdaptor previousSliceTestDBA;
	private String sliceMapFile;

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
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
	 */
	public void checkForRevisions(MySQLAdaptor sourceDBA, MySQLAdaptor targetDBA, Map<Long,GKInstance> sliceMap)
	        throws InvalidAttributeException, Exception {

	    // Iterate over all instances in the slice.
	    GKInstance targetEvent = null;
        for (GKInstance sourceEvent : sliceMap.values()) {
	        if (!sourceEvent.getSchemClass().isa(ReactomeJavaConstants.Event))
	            continue;

	        targetEvent = targetDBA.fetchInstance(sourceEvent.getDBID());

	        // If a revision condition is met, create new _UpdateTracker instance.
	        if (isPathwayRevised(sourceEvent, targetEvent) || isRLERevised(sourceEvent, targetEvent)) {

	            SchemaClass cls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	            GKInstance updateTracker = new GKInstance(cls);
	            updateTracker.setDbAdaptor(sourceDBA);

	            // action
	            updateTracker.setAttributeValue(ReactomeJavaConstants.action, null);

	            // _release
	            updateTracker.setAttributeValue(ReactomeJavaConstants._release, null);

	            // updatedEvent
	            // TODO Determine why the "Event" schema class differs below.
	            // SchemaClass eventClass = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants.Event);
	            // (org.gk.schema.GKSchemaClass) [org.gk.schema.GKSchemaClass@223f3642] Event <-- don't have.

	            // Collection eventClasses = sourceEvent.getSchemClass().getSuperClasses();
	            // (org.gk.schema.GKSchemaClass) [org.gk.schema.GKSchemaClass@3c01cfa1] Event <-- have.

	            updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, sourceEvent);

	            // Add updateTracker instance to sliceMap (so it can be committed to the target database.
	        }
	    }
	}

	/**
	 * Check if Pathway is revised.
	 *
	 * TODO Is the recursive approach that
	 * {@link InstanceUtilities#getContainedInstances(GKInstance, String...)}
	 * takes recommended over this?
	 *
	 * @param sourcePathway
	 * @param targetDBA
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isPathwayRevised(GKInstance sourcePathway, GKInstance targetPathway)
			throws InvalidAttributeException, Exception {

	    if (!sourcePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return false;

	    // Check if a child event (pathway or RLE) is added or removed.
		if (isAttributeRevised(sourcePathway, targetPathway, ReactomeJavaConstants.hasEvent))
			return true;

		// Recursively iterate over and apply revision detection to all events in the pathway.
		List<GKInstance> sourceEvents = sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		List<GKInstance> targetEvents = targetPathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
	    Optional<GKInstance> targetEvent = null;
	    for (GKInstance sourceEvent : sourceEvents) {
		    targetEvent = getMatchingInstance(targetEvents, sourceEvent.getDBID());

		    if (!targetEvent.isPresent())
		        return false;

	        if (isPathwayRevised(sourceEvent, targetEvent.get()) || isRLERevised(sourceEvent, targetEvent.get()))
	            return true;
	    }

		return false;
	}


	/**
	 * Check if a ReactionlikeEvent is revised.
	 *
	 * @param sourceRLE
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isRLERevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

	    if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return false;

		// Check for changes in inputs, outputs, regulators, and catalysts.
		List<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
                                                  ReactomeJavaConstants.output,
                                                  ReactomeJavaConstants.regulatedBy,
                                                  ReactomeJavaConstants.catalystActivity);
		for (String attrName : revisionList) {
			if (isAttributeRevised(sourceRLE, targetRLE, attrName))
				return true;
		}

		// Check if a catalyst or regulator is added or removed.
		if (isAttributeRevised(sourceRLE, targetRLE, ReactomeJavaConstants.regulatedBy))
		    return true;
		if (isAttributeRevised(sourceRLE, targetRLE, ReactomeJavaConstants.catalystActivity))
		    return true;

		// Check if summation is revised.
		if (isRLESummationRevised(sourceRLE, targetRLE))
			return true;

		return false;
	}

	/**
	 * Compare the value of a given attribute between two instances.
	 *
	 * Valid attribute checks take place in
	 * {@link org.gk.slicing.SlicingEngine#isValidAttributeOrThrow(SchemaAttribute attribute)}.
	 *
	 * @param sourceInstance
	 * @param targetInstance
	 * @param attribute
	 * @return boolean (true if an attribute value is revised, false if all attribute values are equal).
	 * @throws Exception
	 */
	private boolean isAttributeRevised(GKInstance sourceInstance, GKInstance targetInstance, String attribute)
			throws Exception {

		// Cast to GKInstance and compare attribute values.
		// Covers the case where an attribute has more than one value.
		List<Object> sourceAttributes = sourceInstance.getAttributeValuesList(attribute);
		List<Object> targetAttributes = targetInstance.getAttributeValuesList(attribute);

		// Constant time check to detect additions or deletions.
		// Misses the case where the same number of unique additions and deletions have occurred.
		// That case is detected by the iterator below.
		if (sourceAttributes.size() != targetAttributes.size())
			return true;

		// Both attribute lists are empty.
		if (sourceAttributes.size() == 0)
		    return false;

	    // If attribute values are instances, then compare individual database id's.
	    if (sourceAttributes.get(0) instanceof GKInstance) {
            List<GKInstance> targetAttributeInstances = targetAttributes.stream()
                                                                        .map(GKInstance.class::cast)
                                                                        .collect(Collectors.toList());
            Optional<GKInstance> targetAttribute = null;

	        for (Object sourceAttribute: sourceAttributes) {
	            Long sourceAttributeDBID = ((GKInstance) sourceAttribute).getDBID();

	            targetAttribute = getMatchingInstance(targetAttributeInstances, sourceAttributeDBID);

	            // Check if there is an instance not shared by both parents.
                // If targetAttributes does not contain an instance in sourceAttributes,
                // then either targetAttributes has a deletion, or sourceAttributes has an addition.
	            if (!targetAttribute.isPresent())
	                return false;

	            // TODO Is it necessary to recursively compare the attribute values of each "attribute instance"?
	        }
	    }

	    // If attribute values are numbers or Strings, then compare the attribute lists directly.
	    else {
	        if (!sourceAttributes.equals(targetAttributes))
	            return true;
	    }

	    // Attributes are not revised.
		return false;
	}

	/**
	 * Check if an instance's summation attribute is revised.
	 *
	 * @param sourceRLE
	 * @return boolean (true if summation is revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 *
	 * @see {@link org.gk.model.Summation}
	 */
	private boolean isRLESummationRevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

		List<GKInstance> sourceSummations = sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation);
		List<GKInstance> targetSummations = targetRLE.getAttributeValuesList(ReactomeJavaConstants.summation);
		// Check for size inequality.
		if (sourceSummations.size() != targetSummations.size())
		    return true;

		// Iterate over source summations.
	    Optional<GKInstance> targetSummation = null;
		for (GKInstance sourceSummation : sourceSummations) {

		    // Get corresponding target summation.
		    targetSummation = getMatchingInstance(targetSummations, sourceSummation.getDBID());
		    if (!targetSummation.isPresent())
		        return false;

		    // Compare summations.
		    // If a change in text is detected, then summation is considered revised.
		    if (isAttributeRevised(sourceSummation, targetSummation.get(), ReactomeJavaConstants.text))
		        return true;
		}

		return false;
	}

	/**
	 * <p>Search a list of instances for a given dbID.</p>
	 *
	 * <p>This method allows attributes to be compared even when they occupy different positions in respective attribute lists.</p>
	 *
	 * <p>Returning an "Optional" gives calling methods a convenient way to short circuit (e.g. by returning false) if
	 * "targetInstances" does not contain  the instance referenced by "dbID".</p>
	 *
	 * @param targetInstances
	 * @param dbID
	 * @return Optional
	 */
	private Optional<GKInstance> getMatchingInstance(List<GKInstance> targetInstances, Long dbID) {
	    return targetInstances.stream()
                              .filter(summation -> summation.getDBID().equals(dbID))
                              .findAny();
	}

	@Before
	public void setUp() throws SQLException {
	    String host = "localhost";
	    String user = "liam";
	    String pass = ")8J7m]!%[<";
		centralTestDBA = new MySQLAdaptor(host, "central", user, pass);
		previousSliceTestDBA = new MySQLAdaptor(host, "previous_slice", user, pass);
		sliceMapFile = "sliceMap.ser";
	}

	@Test
	public void testCheckForRevisions() throws InvalidAttributeException, Exception {
	    Map<Long, GKInstance> sliceMap = readSliceMap(sliceMapFile);

	    checkForRevisions(centralTestDBA, previousSliceTestDBA, sliceMap);
	}

	/**
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

	/**
	 * Write a slice map object to file (for caching use in debugging).
	 *
	 * @param map
	 * @param outFile
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void writeSliceMap(Map<Long, GKInstance> map, String outFile) throws IOException, ClassNotFoundException {
	    try (
            FileOutputStream fileOutput = new FileOutputStream(outFile);
            BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput);
            ObjectOutputStream objectOutput = new ObjectOutputStream(bufferedOutput);
        ) {
	        objectOutput.writeObject(map);
	    }
	}

	/**
	 * Alter a slice map and return the result (for use in debugging).
	 *
	 * @param inFile
	 * @param outFile
	 * @return Map
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private Map<Long, GKInstance> transmorgifySliceMap(String inFile, String outFile) throws IOException, ClassNotFoundException {
	    Map<Long, GKInstance> map = new HashMap<Long, GKInstance>();
	    map = readSliceMap(inFile);

	    // Copy values from the original map to the new map.
	    for (Map.Entry<Long, GKInstance> entry : map.entrySet()) {
	        if (!entry.getValue().getSchemClass().isa(ReactomeJavaConstants.Event))
	            map.remove(entry.getKey());
	    }

	    return map;
	}

	/**
	 * <p>In the tests below:</p>
	 * <ol>
	 *   <li>A "source" instance is fetched from the test database "central"</li>
	 *   <li>A "target" instance is fetched from the test database "previous_slice".</li>
	 *   <li>The "source" instance is modified and compared against the "target" instance --
	 *       "assertTrue(source instance is revised)".</li>
	 *   <li>The revision is reset.</li>
	 *   <li>The "source" instance is again compared against the "target" instance --
	 *        "assertFalse(source instance is revised)".</li>
	 * </ol>
	 */
	@Test
	public void testIsPathwayRevised() throws Exception {
	    GKInstance sourcePathway = null;
	    GKInstance targetPathway = null;
	    GKInstance sourceChildPathway = null;
	    GKInstance sourceRLE = null;

		// Example pathway #1 (Acetylation).
		sourcePathway = (GKInstance) centralTestDBA.fetchInstance(156582L);
		targetPathway = (GKInstance) previousSliceTestDBA.fetchInstance(156582L);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));

		// Add a new child pathway (tRNA processing).
		sourceChildPathway = centralTestDBA.fetchInstance(72306L);
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertTrue(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the addition.
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));


		// Example pathway #2 (DNA repair).
		sourcePathway = (GKInstance) centralTestDBA.fetchInstance(353377L);
		targetPathway = (GKInstance) previousSliceTestDBA.fetchInstance(353377L);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));

		// Remove an existing child pathway (Base Excision Repair).
		sourceChildPathway = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertTrue(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the removal.
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));


		// Revise a child pathway (by changing the summation text of one of it's RLEs).
		sourceRLE = (GKInstance) centralTestDBA.fetchInstance(353484L);
		GKInstance sourceRLESummation = (GKInstance) sourceRLE.getAttributeValue(ReactomeJavaConstants.summation);
		String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		assertTrue(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the revision.
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));
	}

	@Test
	public void testIsRLESummationRevised() throws Exception {
		// Example RLE (A1 and A3 receptors bind adenosine).
		GKInstance sourceRLE = (GKInstance) centralTestDBA.fetchInstance(418904L);
		GKInstance targetRLE = (GKInstance) previousSliceTestDBA.fetchInstance(418904L);
		assertFalse(isRLESummationRevised(sourceRLE, targetRLE));

        // Revise a pathway's summation text.
		GKInstance sourceSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		assertTrue(isRLESummationRevised(sourceRLE, targetRLE));

		// Reset the revision,
		GKInstance targetSummation = (GKInstance) targetRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		String targetSummationText = (String) targetSummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, targetSummationText);
		assertFalse(isRLESummationRevised(sourceRLE, targetRLE));
	}

	@Test
	public void testisAttributeRevised() throws Exception {
		//--------------------------------------//
		// Revision detection (non-GKInstance). //
		//--------------------------------------//

	    String attribute = ReactomeJavaConstants._displayName;

		// (DOCK7) [cytosol]
		GKInstance sourceInstance = centralTestDBA.fetchInstance(8875579L);
		GKInstance targetInstance = previousSliceTestDBA.fetchInstance(8875579L);
		assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));

		// Change attribute value.
		sourceInstance.setAttributeValue(attribute, "revised");

		// False positive tests.
		assertFalse(isAttributeRevised(sourceInstance, sourceInstance, attribute));
		assertFalse(isAttributeRevised(targetInstance, targetInstance, attribute));

		// False negative test.
		assertTrue(isAttributeRevised(sourceInstance, targetInstance, attribute));

		// Reset change.
		sourceInstance.setAttributeValue(attribute, targetInstance.getAttributeValue(attribute));
		assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));

		//----------------------------------//
		// Revision detection (GKInstance). //
		//----------------------------------//

		attribute = ReactomeJavaConstants.regulatedBy;

		// Example RLE (DIT and MIT combine to form triiodothyronine).
		sourceInstance = (GKInstance) centralTestDBA.fetchInstance(209925L);
		targetInstance = (GKInstance) previousSliceTestDBA.fetchInstance(209925L);
		assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		sourceInstance.addAttributeValue(attribute, centralTestDBA.fetchInstance(5210962L));
		assertTrue(isAttributeRevised(sourceInstance, targetInstance, attribute));

		// Remove attribute.
		sourceInstance.removeAttributeValueNoCheck(attribute, centralTestDBA.fetchInstance(5210962L));
		assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));

		//------------------------------------//
		// Event addition/deletion detection. //
		//------------------------------------//

		attribute = ReactomeJavaConstants.hasEvent;

	    // Example pathway (2-LTR circle formation).
	    sourceInstance = centralTestDBA.fetchInstance(164843L);
	    targetInstance = previousSliceTestDBA.fetchInstance(164843L);
	    assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));

	    // Get a reference to the source instance's event list.
	    List<Object> events = sourceInstance.getAttributeValuesList(attribute);

	    // Add RLE.
	    // Reaction (1,25(OH)2D [nucleoplasm]).
	    GKInstance newRLE = centralTestDBA.fetchInstance(8963915L);
	    events.add(newRLE);
	    assertTrue(isAttributeRevised(sourceInstance, targetInstance, attribute));
	    events.remove(newRLE);
	    assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));

	    // Add pathway.
	    // Pathway (ABC transporters disorders).
	    GKInstance newPathway = centralTestDBA.fetchInstance(5619084L);
	    events.add(newPathway);
	    assertTrue(isAttributeRevised(sourceInstance, targetInstance, attribute));
	    events.remove(newPathway);
	    assertFalse(isAttributeRevised(sourceInstance, targetInstance, attribute));
	}
}
