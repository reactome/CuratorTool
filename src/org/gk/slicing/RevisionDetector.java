package org.gk.slicing;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchema;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.junit.Before;
import org.junit.Test;

public class RevisionDetector {
	// Database adaptors used for test.
	private MySQLAdaptor sourceDBATest;
	private MySQLAdaptor previousSliceDBATest;
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
	public List<GKInstance> checkForRevisions(MySQLAdaptor sourceDBA, MySQLAdaptor targetDBA, Map<Long,GKInstance> sliceMap)
	        throws InvalidAttributeException, Exception {

	    GKInstance targetEvent = null;
	    GKInstance updateTracker = null;
	    GKInstance updatedEvent = null;
	    SchemaClass cls = null;
	    List<GKInstance> updateTrackers = new ArrayList<GKInstance>();

	    // Iterate over all instances in the slice.
        for (GKInstance sourceEvent : sliceMap.values()) {
	        if (!sourceEvent.getSchemClass().isa(ReactomeJavaConstants.Event))
	            continue;

	        targetEvent = targetDBA.fetchInstance(sourceEvent.getDBID());

	        // If a revision condition is met, create new _UpdateTracker instance.
	        if (isPathwayRevised(sourceEvent, targetEvent) || isRLERevised(sourceEvent, targetEvent)) {
	            updatedEvent = sourceDBA.fetchInstance(sourceEvent.getDBID());
	            cls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	            updateTracker = new GKInstance(cls);

	            updateTracker.setAttributeValue(ReactomeJavaConstants.action, null);

	            updateTracker.setAttributeValue(ReactomeJavaConstants.modified, null);

	            updateTracker.setAttributeValue(ReactomeJavaConstants.stableIdentifier, null);

	            updateTracker.setAttributeValue(ReactomeJavaConstants.updatedEvent, updatedEvent);

	            updateTracker.setAttributeValue(ReactomeJavaConstants._release, null);

	            updateTracker.setDisplayName(updatedEvent.toString());

	            updateTracker.setDBID(sourceDBA.storeInstance(updatedEvent));

	            // Add updateTracker instance to sliceMap (so it can be committed to the target database.
	            updateTrackers.add(updateTracker);
	        }
	    }

        return updateTrackers;
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
	private List<String> isPathwayRevised(GKInstance sourcePathway, GKInstance targetPathway)
			throws InvalidAttributeException, Exception {

	    if (!sourcePathway.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return null;

	    // Check if a child event (pathway or RLE) is added or removed.
	    List<String> actions = getAttributeRevisions(sourcePathway, targetPathway, ReactomeJavaConstants.hasEvent))

		// Recursively iterate over and apply revision detection to all events in the pathway.
		List<Object> sourceEvents = sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		List<Object> targetEvents = targetPathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
	    Optional<GKInstance> targetEvent = null;
	    for (Object sourceEvent : sourceEvents) {
		    targetEvent = getMatchingInstance(targetEvents, ((GKInstance) sourceEvent).getDBID());

		    if (!targetEvent.isPresent())
		        actions.add(ReactomeJavaConstants.add + ReactomeJavaConstants.hasEvent);
	    }

	    actions.addAll(isPathwayRevised(sourceEvent, targetEvent.get()));
	    actions.addAll(isRLERevised(sourceEvent, targetEvent.get()));

		return actions;
	}


	/**
	 * Check if a ReactionlikeEvent is revised.
	 *
	 * @param sourceRLE
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private List<String> isRLERevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

	    if (!sourceRLE.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return null;

	    List<String> actions = new ArrayList<String>();

		// Check for changes in inputs, outputs, regulators, and catalysts.
		List<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
                                                  ReactomeJavaConstants.output,
                                                  ReactomeJavaConstants.regulatedBy,
                                                  ReactomeJavaConstants.catalystActivity);
		for (String attrName : revisionList)
			actions.addAll(getAttributeRevisions(sourceRLE, targetRLE, attrName));

		// Check if summation is revised.
		actions.addAll(isRLESummationRevised(sourceRLE, targetRLE));

		return actions;
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
	private List<String> isRLESummationRevised(GKInstance sourceRLE, GKInstance targetRLE)
			throws InvalidAttributeException, Exception {

	    List<String> actions = getAttributeRevisions(sourceRLE, targetRLE, ReactomeJavaConstants.summation);
		List<Object> sourceSummations = sourceRLE.getAttributeValuesList(ReactomeJavaConstants.text);
		List<Object> targetSummations = targetRLE.getAttributeValuesList(ReactomeJavaConstants.text);

		// Iterate over source summations.
	    Optional<GKInstance> matchingSummation = null;
	    GKInstance sourceSummationInstance = null;
	    GKInstance targetSummationInstance = null;
	    String sourceText = null;
	    String targetText = null;
		for (Object sourceSummation : sourceSummations) {
		    sourceSummationInstance = ((GKInstance) sourceSummation);
		    matchingSummation =  getMatchingInstance(targetSummations, sourceSummationInstance.getDBID());
		    if (!matchingSummation.isPresent())
		        continue;

		    targetSummationInstance = matchingSummation.get();
		    sourceText = (String) sourceSummationInstance.getAttributeValue(ReactomeJavaConstants.text);
		    targetText = (String) targetSummationInstance.getAttributeValue(ReactomeJavaConstants.text);

		    // Compare summations.
		    // If a change in text is detected, then summation is considered revised.
		    if (!sourceText.equals(targetText))
		        actions.add(ReactomeJavaConstants.modify + ReactomeJavaConstants.text);
		}

		return actions;
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
	private List<String> getAttributeRevisions(GKInstance sourceInstance, GKInstance targetInstance, String attribute)
			throws Exception {

		List<Object> sourceAttributes = sourceInstance.getAttributeValuesList(attribute);
		List<Object> targetAttributes = targetInstance.getAttributeValuesList(attribute);

        List<String>	 actions = new ArrayList<String>();

		// Both attribute lists are empty.
		if (sourceAttributes.size() == 0 && targetAttributes.size() == 0)
		    return null;

	    // If attribute values are instances, then compare individual database id's.
	    if (sourceAttributes.get(0) instanceof GKInstance) {
            // Check for additions.
            // If targetAttributes does not contain an instance in sourceAttributes,
            // then sourceAttributes has had an addition.
            Optional<GKInstance> matchingInstance = null;
	        for (Object sourceAttribute: sourceAttributes) {
	            matchingInstance = getMatchingInstance(sourceAttributes, ((GKInstance) sourceAttribute).getDBID());

	            if (!matchingInstance.isPresent())
	                actions.add(ReactomeJavaConstants.add + attribute);
	        }
            // Check for deletions.
            // If sourceAttributes does not contain an instance in targetAttributes,
            // then sourceAttributes has had a deletion.
	        for (Object targetAttribute: targetAttributes) {
	            matchingInstance = getMatchingInstance(sourceAttributes, ((GKInstance) targetAttribute).getDBID());

	            if (!matchingInstance.isPresent())
	                actions.add(ReactomeJavaConstants.remove + attribute);
	        }
	    }

	    // If attribute values are numbers or Strings, then compare the attribute lists directly.
	    else {
	        if (!sourceAttributes.equals(targetAttributes))
	            actions.add(ReactomeJavaConstants.modify + attribute);
	    }

		return actions;
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
	private Optional<GKInstance> getMatchingInstance(List<Object> targetInstances, Long dbID) {
	    return targetInstances.stream()
                              .map(GKInstance.class::cast)
                              .filter(summation -> summation.getDBID().equals(dbID))
                              .findAny();
	}

	@Before
	public void setUp() throws SQLException {
	    String host = "localhost";
	    String user = "liam";
	    String pass = ")8J7m]!%[<";
		sourceDBATest = new MySQLAdaptor(host, "central", user, pass);
		previousSliceDBATest = new MySQLAdaptor(host, "previous_slice", user, pass);
		sliceMapFile = "sliceMap.ser";
	}

	@Test
	public void testCheckForRevisions() throws InvalidAttributeException, Exception {
	    Map<Long, GKInstance> sliceMap = readSliceMap(sliceMapFile);

	    checkForRevisions(sourceDBATest, previousSliceDBATest, sliceMap);
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
	public void testIsPathwayRevised() throws Exception {
	    GKInstance sourcePathway = null;
	    GKInstance targetPathway = null;
	    GKInstance sourceChildPathway = null;
	    GKInstance sourceRLE = null;

		// Example pathway #1 (Acetylation).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(156582L);
		targetPathway = (GKInstance) previousSliceDBATest.fetchInstance(156582L);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));

		// Add a new child pathway (tRNA processing).
		sourceChildPathway = sourceDBATest.fetchInstance(72306L);
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the addition.
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));


		// Example pathway #2 (DNA repair).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(353377L);
		targetPathway = (GKInstance) previousSliceDBATest.fetchInstance(353377L);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));

		// Remove an existing child pathway (Base Excision Repair).
		sourceChildPathway = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the removal.
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));


		// Revise a child pathway (by changing the summation text of one of it's RLEs).
		sourceRLE = (GKInstance) sourceDBATest.fetchInstance(353484L);
		GKInstance sourceRLESummation = (GKInstance) sourceRLE.getAttributeValue(ReactomeJavaConstants.summation);
		String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the revision.
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertEquals(isPathwayRevised(sourcePathway, targetPathway));
	}

	@Test
	public void testIsRLESummationRevised() throws Exception {
		// Example RLE (A1 and A3 receptors bind adenosine).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(418904L);
		GKInstance targetRLE = (GKInstance) previousSliceDBATest.fetchInstance(418904L);
		assertEquals(isRLESummationRevised(sourceRLE, targetRLE));

        // Revise a pathway's summation text.
		GKInstance sourceSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		assertEquals(isRLESummationRevised(sourceRLE, targetRLE));

		// Reset the revision,
		GKInstance targetSummation = (GKInstance) targetRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		String targetSummationText = (String) targetSummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, targetSummationText);
		assertEquals(isRLESummationRevised(sourceRLE, targetRLE));
	}

	@Test
	public void testisAttributeRevised() throws Exception {
		//--------------------------------------//
		// Revision detection (non-GKInstance). //
		//--------------------------------------//

	    String attribute = ReactomeJavaConstants._displayName;

		// (DOCK7) [cytosol]
		GKInstance sourceInstance = sourceDBATest.fetchInstance(8875579L);
		GKInstance targetInstance = previousSliceDBATest.fetchInstance(8875579L);
		assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

		// Change attribute value.
		sourceInstance.setAttributeValue(attribute, "revised");

		// False positive tests.
		assertEquals(getAttributeRevisions(sourceInstance, sourceInstance, attribute));
		assertEquals(getAttributeRevisions(targetInstance, targetInstance, attribute));

		// False negative test.
		assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

		// Reset change.
		sourceInstance.setAttributeValue(attribute, targetInstance.getAttributeValue(attribute));
		assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

		//----------------------------------//
		// Revision detection (GKInstance). //
		//----------------------------------//

		attribute = ReactomeJavaConstants.regulatedBy;

		// Example RLE (DIT and MIT combine to form triiodothyronine).
		sourceInstance = (GKInstance) sourceDBATest.fetchInstance(209925L);
		targetInstance = (GKInstance) previousSliceDBATest.fetchInstance(209925L);
		assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		sourceInstance.addAttributeValue(attribute, sourceDBATest.fetchInstance(5210962L));
		assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

		// Remove attribute.
		sourceInstance.removeAttributeValueNoCheck(attribute, sourceDBATest.fetchInstance(5210962L));
		assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

		//------------------------------------//
		// Event addition/deletion detection. //
		//------------------------------------//

		attribute = ReactomeJavaConstants.hasEvent;

	    // Example pathway (2-LTR circle formation).
	    sourceInstance = sourceDBATest.fetchInstance(164843L);
	    targetInstance = previousSliceDBATest.fetchInstance(164843L);
	    assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

	    // Get a reference to the source instance's event list.
	    List<Object> events = sourceInstance.getAttributeValuesList(attribute);

	    // Add RLE.
	    // Reaction (1,25(OH)2D [nucleoplasm]).
	    GKInstance newRLE = sourceDBATest.fetchInstance(8963915L);
	    events.add(newRLE);
	    assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));
	    events.remove(newRLE);
	    assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));

	    // Add pathway.
	    // Pathway (ABC transporters disorders).
	    GKInstance newPathway = sourceDBATest.fetchInstance(5619084L);
	    events.add(newPathway);
	    assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));
	    events.remove(newPathway);
	    assertEquals(getAttributeRevisions(sourceInstance, targetInstance, attribute));
	}
}
