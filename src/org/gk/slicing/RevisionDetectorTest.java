package org.gk.slicing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.Test;

public class RevisionDetectorTest {
	// Database adaptors used for test.
	private MySQLAdaptor sourceDBATest;
	private MySQLAdaptor previousSliceDBATest;
	// SliceMap cache file.
	private String sliceMapFile;
	private RevisionDetector revisionDetector;

	/**
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
     *   <li>A "previousSlice" instance is fetched from the test database "previous_slice".</li>
     *   <li>The "source" instance is modified and compared against the "previousSlice" instance --
     *       "assertEquals(source instance is revised)".</li>
     *   <li>The revision is reset.</li>
     *   <li>The "source" instance is again compared against the "previousSlice" instance --
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
	    revisionDetector = new RevisionDetector();
	    revisionDetector.setPreviousSliceDBA(previousSliceDBATest);
		sliceMapFile = "sliceMap.ser";
	}

	@Test
	public void testGetRevisions() throws InvalidAttributeException, Exception {
	    List<GKInstance> updateTrackers = null;
	    Map<Long, GKInstance> sliceMap = readSliceMap(sliceMapFile);
	    // Start with five revisions between the two "slices".
	    updateTrackers = revisionDetector.getRevisions(sourceDBATest, sliceMap);
	    assertEquals(updateTrackers.size(), 5);
	}

	@Test
	public void testIsPathwayRevised() throws Exception {
	    GKInstance sourcePathway = null;
	    GKInstance previousSlicePathway = null;
	    GKInstance sourceChildPathway = null;
	    GKInstance sourceRLE = null;
	    String actions = null;
	    Set<String> revisions = null;

		// Example pathway #1 (Acetylation).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(156582L);
		previousSlicePathway = (GKInstance) previousSliceDBATest.fetchInstance(156582L);
		revisions = revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Add a new child pathway (tRNA processing).
		sourceChildPathway = sourceDBATest.fetchInstance(72306L);
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourcePathway);
		actions = "addHasEvent";
		revisions = revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset the addition.
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourcePathway);
		assertTrue(revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway).isEmpty());


		// Example pathway #2 (DNA repair).
		sourcePathway = (GKInstance) sourceDBATest.fetchInstance(353377L);
		previousSlicePathway = (GKInstance) previousSliceDBATest.fetchInstance(353377L);
		assertTrue(revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway).isEmpty());

		// Remove an existing child pathway (Base Excision Repair).
		sourceChildPathway = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		actions = "removeHasEvent";
		revisions = revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset the removal.
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertTrue(revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway).isEmpty());


		// Revise a child pathway (by changing the summation text of one of it's RLEs).
		sourceRLE = (GKInstance) sourceDBATest.fetchInstance(353484L);
		GKInstance sourceRLESummation = (GKInstance) sourceRLE.getAttributeValue(ReactomeJavaConstants.summation);
		String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		actions = "modifyText";
		revisions = revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset the revision.
		sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertTrue(revisionDetector.isPathwayRevised(sourcePathway, previousSlicePathway).isEmpty());
	}

	@Test
	public void testIsRLERevised() throws Exception {
	    String actions = null;
	    String attribute = null;
		Long revisedAttributeDBID = null;

		// Example RLE (AXL binds SRC-1).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(5357432L);
		GKInstance previousSliceRLE = (GKInstance) previousSliceDBATest.fetchInstance(5357432L);
        assertTrue(revisionDetector.isRLERevised(sourceRLE, previousSliceRLE).isEmpty());

		// Revise input (ALS2 dimer [cytosol]).
	    attribute = ReactomeJavaConstants.input;
	    revisedAttributeDBID = 8875299L;
		actions = "addInput";
		RLETestRunner(sourceRLE, previousSliceRLE, attribute, revisedAttributeDBID, actions);

		// Revise output (AUF1 p37 dimer [cytosol]).
	    attribute = ReactomeJavaConstants.output;
	    revisedAttributeDBID = 450539L;
		actions = "addOutput";
		RLETestRunner(sourceRLE, previousSliceRLE, attribute, revisedAttributeDBID, actions);

		// Revise regulatedBy (Negative regulation by 'ARL2 [cytosol]').
	    attribute = ReactomeJavaConstants.regulatedBy;
	    revisedAttributeDBID = 5255416L;
		actions = "addRegulatedBy";
		RLETestRunner(sourceRLE, previousSliceRLE, attribute, revisedAttributeDBID, actions);

		// Revise catalystActivity (ATPase activity of NSF [cytosol]).
	    attribute = ReactomeJavaConstants.catalystActivity;
	    revisedAttributeDBID = 416995L;
		actions = "addCatalystActivity";
		RLETestRunner(sourceRLE, previousSliceRLE, attribute, revisedAttributeDBID, actions);

		// Revise species -- should not trigger revision (Felis catus).
	    attribute = ReactomeJavaConstants.species;
	    revisedAttributeDBID = 164922L;
	    actions = null;
		RLETestRunner(sourceRLE, previousSliceRLE, attribute, revisedAttributeDBID, actions);
	}

	/**
	 * Test runner for RLE instances.
	 *
	 * @param sourceRLE
	 * @param previousSliceRLE
	 * @param attribute
	 * @param revisedAttributeDBID
	 * @param actions
	 * @throws Exception
	 */
	private void RLETestRunner(GKInstance sourceRLE,
                               GKInstance previousSliceRLE,
                               String attribute,
                               Long revisedAttributeDBID,
                               String actions) throws Exception {

	    Set<String> revisions = null;
		List<Object> originalAttributes = new ArrayList<Object>(sourceRLE.getAttributeValuesList(attribute));
		if (originalAttributes.size() == 0)
		    originalAttributes = null;

		// Add attribute value.
	    GKInstance revisedAttribute = (GKInstance) sourceDBATest.fetchInstance(revisedAttributeDBID);
	    sourceRLE.addAttributeValue(attribute, revisedAttribute);
	    revisions = revisionDetector.isRLERevised(sourceRLE, previousSliceRLE);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

	    // Reset revision.
	    sourceRLE.setAttributeValue(attribute, originalAttributes);
	    assertTrue(revisionDetector.isRLERevised(sourceRLE, previousSliceRLE).isEmpty());
	}

	@Test
	public void testIsRLESummationRevised() throws Exception {
	    String actions = null;
	    Collection<String> revisions = null;
	    String attribute = ReactomeJavaConstants.text;

		// Example RLE (A1 and A3 receptors bind adenosine).
		GKInstance sourceRLE = (GKInstance) sourceDBATest.fetchInstance(418904L);
		GKInstance previousSliceRLE = (GKInstance) previousSliceDBATest.fetchInstance(418904L);
		assertTrue(revisionDetector.isRLESummationRevised(sourceRLE, previousSliceRLE).isEmpty());

        // Revise summation text.
		GKInstance sourceSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		sourceSummation.setAttributeValue(attribute, "revised");
		actions = "modifyText";
		revisions = revisionDetector.isRLESummationRevised(sourceRLE, previousSliceRLE);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset the revision,
		GKInstance previousSliceSummation = (GKInstance) previousSliceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		String previousSliceSummationText = (String) previousSliceSummation.getAttributeValue(attribute);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, previousSliceSummationText);
		assertTrue(revisionDetector.isRLESummationRevised(sourceRLE, previousSliceRLE).isEmpty());
	}

	@Test
	public void testisAttributeRevised() throws Exception {
	    String actions = null;
	    Set<String> revisions = null;
	    String attribute = null;
	    GKInstance sourceInstance = null;
	    GKInstance previousSliceInstance = null;

		//--------------------------------------//
		// Revision detection (non-GKInstance). //
		//--------------------------------------//

	    attribute = ReactomeJavaConstants._displayName;

		// (DOCK7) [cytosol]
		sourceInstance = sourceDBATest.fetchInstance(8875579L);
		previousSliceInstance = previousSliceDBATest.fetchInstance(8875579L);
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());

		// Change attribute value.
		sourceInstance.setAttributeValue(attribute, "revised");
		actions = "modify_displayName";
		revisions = revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset change.
		sourceInstance.setAttributeValue(attribute, previousSliceInstance.getAttributeValue(attribute));
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());

		//----------------------------------//
		// Revision detection (GKInstance). //
		//----------------------------------//

		attribute = ReactomeJavaConstants.regulatedBy;

		// Example RLE (DIT and MIT combine to form triiodothyronine).
		sourceInstance = (GKInstance) sourceDBATest.fetchInstance(209925L);
		previousSliceInstance = (GKInstance) previousSliceDBATest.fetchInstance(209925L);
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());

		// Original attributes.
		List<Object> originalAttributes = new ArrayList<Object>(sourceInstance.getAttributeValuesList(attribute));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		GKInstance newAttributeInstance = sourceDBATest.fetchInstance(5210962L);
		sourceInstance.addAttributeValue(attribute, newAttributeInstance);
		actions = "addRegulatedBy";
		revisions = revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Remove original attribute instance.
		sourceInstance.setAttributeValue(attribute, newAttributeInstance);
		actions = "add/removeRegulatedBy";
		revisions = revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Remove all attribute instances.
		sourceInstance.setAttributeValue(attribute, null);
		actions = "removeRegulatedBy";
		revisions = revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset revisions.
		sourceInstance.setAttributeValue(attribute, originalAttributes);
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());

		//------------------------------------//
		// Event addition/deletion detection. //
		//------------------------------------//

		attribute = ReactomeJavaConstants.hasEvent;

	    // Example pathway (2-LTR circle formation).
	    sourceInstance = sourceDBATest.fetchInstance(164843L);
	    previousSliceInstance = previousSliceDBATest.fetchInstance(164843L);
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());

	    // Get a reference to the source instance's event list.
	    List<Object> events = sourceInstance.getAttributeValuesList(attribute);
	    originalAttributes = new ArrayList<Object>(events);

	    // Add RLE (Reaction (1,25(OH)2D [nucleoplasm])).
	    GKInstance newRLE = sourceDBATest.fetchInstance(8963915L);
	    events.add(newRLE);
		actions = "addHasEvent";
		revisions = revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset revision.
	    events.remove(newRLE);
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());

	    // Add pathway (ABC transporters disorders).
	    GKInstance newPathway = sourceDBATest.fetchInstance(5619084L);
	    events.add(newPathway);
	    // Add second pathway (Actin assembly).
	    newPathway = sourceDBATest.fetchInstance(419989L);
	    events.add(newPathway);
		actions = "addHasEvent";
		revisions = revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute);
		assertEquals(actions, revisionDetector.collectionToString(revisions));

		// Reset revision.
		sourceInstance.setAttributeValue(attribute, originalAttributes);
		assertTrue(revisionDetector.getAttributeRevisions(sourceInstance, previousSliceInstance, attribute).isEmpty());
	}
}
