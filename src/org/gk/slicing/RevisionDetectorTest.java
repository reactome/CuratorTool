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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.Test;

public class RevisionDetectorTest {
    // Database adaptors used for test.
    private MySQLAdaptor sourceDBA;
    private MySQLAdaptor previousSliceDBA;
    // SliceMap cache file.
    private String sliceMapFile;
    private RevisionDetector revisionDetector;
    private GKInstance updateTracker;
    private String action;
    private String attributeName;

    /**
     * Test utility method.
     * Read a slice map object from file (for caching use in debugging).
     *
     * @param inFile
     * @return Map
     * @throws IOException
     * @throws ClassNotFoundException
     */
    Map<Long, GKInstance> readSliceMap(String inFile) throws IOException, ClassNotFoundException {
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
     * Accepts "limit" as a parameter for the maximum number of desired pathways to include in the output file.
     *
     * @param sliceMap
     * @param outFile
     * @param limit
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void writeSliceMap(Map<Long, GKInstance> sliceMap, String outFile, int limit) throws FileNotFoundException, IOException {
        try (
            FileOutputStream fileOutput = new FileOutputStream(outFile);
            BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput);
            ObjectOutputStream objectOutput = new ObjectOutputStream(bufferedOutput);
        ) {
            Map<Long, GKInstance> filteredSliceMap = new HashMap<Long, GKInstance>();
            int i = 0;
            for (Map.Entry<Long, GKInstance> entry : sliceMap.entrySet()) {
                // Limit to event instances.
                if (!entry.getValue().getSchemClass().isa(ReactomeJavaConstants.Event))
                    continue;

                filteredSliceMap.put(entry.getKey(), entry.getValue());

                // Stop early if the desired number of pathways are added to the cache file.
                if (entry.getValue().getSchemClass().isa(ReactomeJavaConstants.Pathway))
                    if (i++ > limit) break;
            }
            // Serialize sliceMap object to file.
            objectOutput.writeObject(filteredSliceMap);
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
        sourceDBA = new MySQLAdaptor(host, "central", user, pass);
        previousSliceDBA = new MySQLAdaptor(host, "previous_slice", user, pass);
        revisionDetector = new RevisionDetector();
        sliceMapFile = "sliceMap.ser";
    }

    @Test
    public void getAllUpdateTrackersTest() throws InvalidAttributeException, Exception {
        List<GKInstance> updateTrackers = null;
        Map<Long, GKInstance> sliceMap = readSliceMap(sliceMapFile);
        // Start with five revisions between the two "slices".
        updateTrackers = revisionDetector.getAllUpdateTrackers(sliceMap, sourceDBA, previousSliceDBA);
        assertEquals(updateTrackers.size(), 5);

        // Check that the first DBID is set to maxDBID - 1.
        Long expectedDBID = updateTrackers.get(0).getDBID();
        long expectedMaxDBID = expectedDBID - 1;
        assertEquals(sourceDBA.fetchMaxDbId(), expectedMaxDBID);

        // Check that DBID's are sequential.
        for (GKInstance updateTracker : updateTrackers.subList(1, updateTrackers.size()))
            assertEquals(updateTracker.getDBID(), ++expectedDBID);
    }

    @Test
    public void isPathwayRevisedTest() throws Exception {
        GKInstance sourcePathway = null;
        GKInstance sourceChildPathway = null;
        GKInstance sourceRLE = null;

        // Example pathway #1 (Acetylation).
        sourcePathway = (GKInstance) sourceDBA.fetchInstance(156582L);
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Add a new child pathway (tRNA processing).
        sourceChildPathway = sourceDBA.fetchInstance(72306L);
        sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourcePathway);
        action = "addHasEvent";
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset the addition.
        sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourcePathway);
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);


        // Example pathway #2 (DNA repair).
        sourcePathway = (GKInstance) sourceDBA.fetchInstance(353377L);
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Remove an existing child pathway (Base Excision Repair).
        sourceChildPathway = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
        sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
        action = "removeHasEvent";
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset the removal.
        sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);


        // Revise a child pathway (by changing the summation text of one of it's RLEs).
        sourceRLE = (GKInstance) sourceDBA.fetchInstance(353484L);
        GKInstance sourceRLESummation = (GKInstance) sourceRLE.getAttributeValue(ReactomeJavaConstants.summation);
        String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
        sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
        action = "modifyText";
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset the revision.
        sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
        updateTracker = revisionDetector.getUpdateTracker(sourcePathway, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);
    }


    @Test
    public void isRLERevisedTest() throws Exception {
        GKInstance revisedAttribute = null;

        // Example RLE (AXL binds SRC-1).
        GKInstance sourceRLE = (GKInstance) sourceDBA.fetchInstance(5357432L);
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Revise input (ALS2 dimer [cytosol]).
        attributeName = ReactomeJavaConstants.input;
        revisedAttribute = (GKInstance) sourceDBA.fetchInstance(8875299L);
        action = "addInput";
        RLETestRunner(sourceRLE, attributeName, revisedAttribute, action);

        // Revise output (AUF1 p37 dimer [cytosol]).
        attributeName = ReactomeJavaConstants.output;
        revisedAttribute = (GKInstance) sourceDBA.fetchInstance(450539L);
        action = "addOutput";
        RLETestRunner(sourceRLE, attributeName, revisedAttribute, action);

        // Revise regulatedBy (Negative regulation by 'ARL2 [cytosol]').
        attributeName = ReactomeJavaConstants.regulatedBy;
        revisedAttribute = (GKInstance) sourceDBA.fetchInstance(5255416L);
        action = "addRegulatedBy";
        RLETestRunner(sourceRLE, attributeName, revisedAttribute, action);

        // Revise catalystActivity (ATPase activity of NSF [cytosol]).
        attributeName = ReactomeJavaConstants.catalystActivity;
        revisedAttribute = (GKInstance) sourceDBA.fetchInstance(416995L);
        action = "addCatalystActivity";
        RLETestRunner(sourceRLE, attributeName, revisedAttribute, action);

        // Revise species -- should not trigger revision (Felis catus).
        attributeName = ReactomeJavaConstants.species;
        revisedAttribute = (GKInstance) sourceDBA.fetchInstance(164922L);
        sourceRLE.addAttributeValue(attributeName, revisedAttribute);
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);
    }

    /**
     * Test runner for RLE instances.
     *
     * @param sourceRLE
     * @param previousSliceRLE
     * @param attributeName
     * @param revisedAttribute
     * @param action
     * @throws Exception
     */
    private void RLETestRunner(GKInstance sourceRLE, String attributeName, GKInstance revisedAttribute, String action) throws Exception {
        List<Object> originalAttributes = new ArrayList<Object>(sourceRLE.getAttributeValuesList(attributeName));
        if (originalAttributes.size() == 0)
            originalAttributes = null;

        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Add attribute value.
        sourceRLE.addAttributeValue(attributeName, revisedAttribute);
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset revision.
        sourceRLE.setAttributeValue(attributeName, originalAttributes);
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);
    }

    @Test
    public void isRLESummationRevisedTest() throws Exception {
        attributeName = ReactomeJavaConstants.text;

        // Example RLE (A1 and A3 receptors bind adenosine).
        GKInstance sourceRLE = (GKInstance) sourceDBA.fetchInstance(418904L);
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Save original summation text.
        GKInstance originalSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
        String originalSummationText = (String) originalSummation.getAttributeValue(attributeName);

        // Revise summation text.
        GKInstance sourceSummation = (GKInstance) sourceRLE.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
        sourceSummation.setAttributeValue(attributeName, "revised");
        action = "modifyText";
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset the revision,
        sourceSummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
        updateTracker = revisionDetector.getUpdateTracker(sourceRLE, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);
    }

    @Test
    public void IsAttributeRevisedTest() throws Exception {
        GKInstance sourceInstance = null;

        //--------------------------------------------//
        // Revision detection (GKInstance attribute). //
        //--------------------------------------------//

        attributeName = ReactomeJavaConstants.regulatedBy;

        // Example RLE (DIT and MIT combine to form triiodothyronine).
        sourceInstance = (GKInstance) sourceDBA.fetchInstance(209925L);
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Original attributes.
        List<Object> originalAttributes = new ArrayList<Object>(sourceInstance.getAttributeValuesList(attributeName));

        // Example added attribute (Positive regulation by 'H+ [endosome lumen]').
        GKInstance newAttributeInstance = sourceDBA.fetchInstance(5210962L);
        sourceInstance.addAttributeValue(attributeName, newAttributeInstance);
        action = "addRegulatedBy";
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Remove original attribute instance.
        sourceInstance.setAttributeValue(attributeName, newAttributeInstance);
        action = "add/removeRegulatedBy";
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Remove all attribute instances.
        sourceInstance.setAttributeValue(attributeName, null);
        action = "removeRegulatedBy";
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset revisions.
        sourceInstance.setAttributeValue(attributeName, originalAttributes);
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        //------------------------------------//
        // Event addition/deletion detection. //
        //------------------------------------//

        attributeName = ReactomeJavaConstants.hasEvent;

        // Example pathway (2-LTR circle formation).
        sourceInstance = sourceDBA.fetchInstance(164843L);
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Get a reference to the source instance's event list.
        List<Object> events = sourceInstance.getAttributeValuesList(attributeName);
        originalAttributes = new ArrayList<Object>(events);

        // Add RLE (Reaction (1,25(OH)2D [nucleoplasm])).
        GKInstance newRLE = sourceDBA.fetchInstance(8963915L);
        events.add(newRLE);
        action = "addHasEvent";
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset revision.
        events.remove(newRLE);
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);

        // Add pathway (ABC transporters disorders).
        GKInstance newPathway = sourceDBA.fetchInstance(5619084L);
        events.add(newPathway);
        // Add second pathway (Actin assembly).
        newPathway = sourceDBA.fetchInstance(419989L);
        events.add(newPathway);
        action = "addHasEvent";
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertEquals(action, updateTracker.getAttributeValue(ReactomeJavaConstants.action));

        // Reset revision.
        sourceInstance.setAttributeValue(attributeName, originalAttributes);
        updateTracker = revisionDetector.getUpdateTracker(sourceInstance, sourceDBA, previousSliceDBA);
        assertNull(updateTracker);
    }
}
