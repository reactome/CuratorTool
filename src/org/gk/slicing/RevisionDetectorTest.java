package org.gk.slicing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    // SlicingEngine
    SlicingEngine slicingEngine;

    // Database adaptors used for test.
    private MySQLAdaptor sliceDBA;
    private MySQLAdaptor previousDBA;

    private RevisionDetector revisionDetector;
    private String action;
    private String attributeName;

    // actions map
    private Map<GKInstance, Set<String>> actionMap;

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
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        String host = "localhost";
        String user = "liam";
        String pass = ")8J7m]!%[<";
        sliceDBA = new MySQLAdaptor(host, "slice", user, pass);
        previousDBA = new MySQLAdaptor(host, "previous_slice", user, pass);
        revisionDetector = new RevisionDetector();
        slicingEngine = new SlicingEngine();
        revisionDetector.setSlicingEngine(slicingEngine);
        actionMap = new HashMap<GKInstance, Set<String>>();
    }

    @Test
    public void isPathwayRevisedTest() throws Exception {
        GKInstance pathway = null;
        GKInstance childPathway = null;
        List<GKInstance> events = new ArrayList<GKInstance>();

        // Example pathway #1 (Inhibition of Signaling by Overexpressed EGFR).
        pathway = (GKInstance) sliceDBA.fetchInstance(5638303L);
        events.add(pathway);
        actionMap = revisionDetector.createActionMap(sliceDBA, events);
        assertNull(actionMap.get(pathway));

        // Add a new child pathway (tRNA processing).
        childPathway = sliceDBA.fetchInstance(72306L);
        events.add(childPathway);
        pathway.addAttributeValue(ReactomeJavaConstants.hasEvent, childPathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "addHasEvent";
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Reset the addition.
        pathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, childPathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));


        // Example pathway #2 (Hedgehog 'on' state).
        pathway = (GKInstance) sliceDBA.fetchInstance(5632684L);
        events.add(pathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));

        // Remove an existing child pathway.
        childPathway = (GKInstance) pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
        events.add(childPathway);
        pathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, childPathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "removeHasEvent";
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Reset the removal.
        pathway.addAttributeValue(ReactomeJavaConstants.hasEvent, childPathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));


        // Revise a child pathway (by changing the summation text of one of it's RLEs).
        // 'Bubbling up' of revision actions is disabled, so 'action' attribute should be blank.
        GKInstance grandparent = pathway;
        GKInstance parent = (GKInstance) sliceDBA.fetchInstance(5632681L);
        GKInstance child = (GKInstance) sliceDBA.fetchInstance(5632653L);
        events.add(child);
        GKInstance sourceRLESummation = (GKInstance) child.getAttributeValue(ReactomeJavaConstants.summation);
        String originalSummationText = (String) sourceRLESummation.getAttributeValue(ReactomeJavaConstants.text);
        sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "modifyText";
        assertEquals(action, String.join(",", actionMap.get(child)));
        assertEquals(action, String.join(",", actionMap.get(parent)));
        action = "actionFilter";
        assertEquals(action, String.join(",", actionMap.get(grandparent)));

        // Reset the revision.
        sourceRLESummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));
    }


    @Test
    public void isRLERevisedTest() throws Exception {
        GKInstance revisedAttribute = null;

        // Example RLE (AXL binds SRC-1).
        GKInstance rle = (GKInstance) sliceDBA.fetchInstance(5357432L);
        Set<GKInstance> events = new HashSet<GKInstance>();
        events.add(rle);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "";
        assertEquals(action, String.join(",", actionMap.get(rle)));

        // Revise input (ALS2 dimer [cytosol]).
        attributeName = ReactomeJavaConstants.input;
        revisedAttribute = (GKInstance) sliceDBA.fetchInstance(8875299L);
        action = "addInput";
        RLETestRunner(rle, attributeName, revisedAttribute, action);

        // Revise output (AUF1 p37 dimer [cytosol]).
        attributeName = ReactomeJavaConstants.output;
        revisedAttribute = (GKInstance) sliceDBA.fetchInstance(450539L);
        action = "addOutput";
        RLETestRunner(rle, attributeName, revisedAttribute, action);

        // Revise regulatedBy (Negative regulation by 'ARL2 [cytosol]').
        attributeName = ReactomeJavaConstants.regulatedBy;
        revisedAttribute = (GKInstance) sliceDBA.fetchInstance(5255416L);
        action = "addRegulatedBy";
        RLETestRunner(rle, attributeName, revisedAttribute, action);

        // Revise catalystActivity (ATPase activity of NSF [cytosol]).
        attributeName = ReactomeJavaConstants.catalystActivity;
        revisedAttribute = (GKInstance) sliceDBA.fetchInstance(416995L);
        action = "addCatalystActivity";
        RLETestRunner(rle, attributeName, revisedAttribute, action);

        // Revise species -- should not trigger revision (Felis catus).
        attributeName = ReactomeJavaConstants.species;
        revisedAttribute = (GKInstance) sliceDBA.fetchInstance(164922L);
        action = "";
        RLETestRunner(rle, attributeName, revisedAttribute, action);
    }

    /**
     * Test runner for RLE instances.
     *
     * @param rle
     * @param previousSliceRLE
     * @param attributeName
     * @param revisedAttribute
     * @param action
     * @throws Exception
     */
    private void RLETestRunner(GKInstance rle, String attributeName, GKInstance revisedAttribute, String action) throws Exception {
        List<Object> originalAttributes = new ArrayList<Object>(rle.getAttributeValuesList(attributeName));
        if (originalAttributes.size() == 0)
            originalAttributes = null;

        Set<GKInstance> events = new HashSet<GKInstance>();
        events.add(rle);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals("", String.join(",", actionMap.get(rle)));

        // Add attribute value.
        rle.addAttributeValue(attributeName, revisedAttribute);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(rle)));

        // Reset revision.
        rle.setAttributeValue(attributeName, originalAttributes);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals("", String.join(",", actionMap.get(rle)));
    }

    @Test
    public void isRLESummationRevisedTest() throws Exception {
        attributeName = ReactomeJavaConstants.text;
        Set<GKInstance> events = new HashSet<GKInstance>();

        // Example RLE (A1 and A3 receptors bind adenosine).
        GKInstance rle = (GKInstance) sliceDBA.fetchInstance(418904L);
        events.add(rle);
        action = "";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(rle)));

        // Save original summation text.
        GKInstance originalSummation = (GKInstance) rle.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
        String originalSummationText = (String) originalSummation.getAttributeValue(attributeName);

        // Revise summation text.
        GKInstance sourceSummation = (GKInstance) rle.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
        sourceSummation.setAttributeValue(attributeName, "revised");
        action = "modifyText";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(rle)));

        // Reset the revision,
        sourceSummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "";
        assertEquals(action, String.join(",", actionMap.get(rle)));
    }

    @Test
    public void IsAttributeRevisedTest() throws Exception {
        GKInstance pathway = null;
        Set<GKInstance> events = new HashSet<GKInstance>();

        //--------------------------------------------//
        // Revision detection (GKInstance attribute). //
        //--------------------------------------------//

        attributeName = ReactomeJavaConstants.regulatedBy;

        // Example RLE (DIT and MIT combine to form triiodothyronine).
        pathway = (GKInstance) sliceDBA.fetchInstance(209925L);
        events.add(pathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "";
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Original attributes.
        List<Object> originalAttributes = new ArrayList<Object>(pathway.getAttributeValuesList(attributeName));

        // Example added attribute (Positive regulation by 'H+ [endosome lumen]').
        GKInstance newAttributeInstance = sliceDBA.fetchInstance(5210962L);
        pathway.addAttributeValue(attributeName, newAttributeInstance);
        action = "addRegulatedBy";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Remove original attribute instance.
        pathway.setAttributeValue(attributeName, newAttributeInstance);
        action = "add/removeRegulatedBy";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Remove all attribute instances.
        pathway.setAttributeValue(attributeName, null);
        action = "removeRegulatedBy";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Reset revisions.
        pathway.setAttributeValue(attributeName, originalAttributes);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        action = "";
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        //------------------------------------//
        // Event addition/deletion detection. //
        //------------------------------------//

        attributeName = ReactomeJavaConstants.hasEvent;

        // Example pathway (2-LTR circle formation).
        pathway = sliceDBA.fetchInstance(164843L);
        events.add(pathway);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));

        // Get a reference to the source instance's event list.
        List<GKInstance> hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        originalAttributes = new ArrayList<Object>(hasEvent);

        // Add RLE (Reaction (1,25(OH)2D [nucleoplasm])).
        GKInstance newRLE = sliceDBA.fetchInstance(8963915L);
        hasEvent.add(newRLE);
        action = "addHasEvent";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Reset revision.
        hasEvent.remove(newRLE);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));

        // Add pathway (ABC transporters disorders).
        GKInstance newPathway = sliceDBA.fetchInstance(5619084L);
        hasEvent.add(newPathway);
        // Add second pathway (Actin assembly).
        newPathway = sliceDBA.fetchInstance(419989L);
        hasEvent.add(newPathway);
        action = "addHasEvent";
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertEquals(action, String.join(",", actionMap.get(pathway)));

        // Reset revision.
        pathway.setAttributeValue(attributeName, originalAttributes);
        actionMap = revisionDetector.createActionMap(previousDBA, events);
        assertNull(actionMap.get(pathway));
    }
}
