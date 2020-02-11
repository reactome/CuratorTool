package org.gk.slicing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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
//	private MySQLAdaptor sliceTestDBA;

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

	    GKInstance sourceInstance = null;
	    GKInstance targetInstance = null;
	    // Iterate over all instances in the slice.
	    for (Long dbId : sliceMap.keySet()) {
	        sourceInstance = sliceMap.get(dbId);
	        targetInstance = targetDBA.fetchInstance(sourceInstance.getDBID());

	        // If a revision condition is met, create new _UpdateTracker instance.
	        if (isPathwayRevised(sourceInstance, targetInstance) || isAttributeRevised(sourceInstance, targetInstance)) {
	            SchemaClass cls = sourceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
	            GKInstance updateTracker = new GKInstance(cls);
	            updateTracker.setDbAdaptor(sourceDBA);
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
	 * @param sourceInstance
	 * @param targetDBA
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isPathwayRevised(GKInstance sourceInstance, GKInstance targetInstance)
			throws InvalidAttributeException, Exception {

	    if (!sourceInstance.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return false;

	    // Check if a child event (pathway or RLE) is added or removed.
		if (isAttributeRevised(sourceInstance, targetInstance, ReactomeJavaConstants.hasEvent))
			return true;

		// Check for changes in child RLE summation text.
		if (isChildRLESummationRevised(sourceInstance, targetInstance))
			return true;

		// Recursively iterate over and apply revision detection to all events in the pathway.
		List<GKInstance> sourceEvents = sourceInstance.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		List<GKInstance> targetEvents = targetInstance.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
	    Optional<GKInstance> targetEvent = null;
	    for (GKInstance sourceEvent : sourceEvents) {
		    // This stream is an easy way to short circuit (return false) if
		    // "targetEvents" does not contain "sourceEvent".
		    targetEvent = targetEvents.stream()
                                      .filter(event -> event.getDBID().equals(sourceEvent.getDBID()))
                                      .findAny();
		    if (!targetEvent.isPresent())
		        return false;

	        if (isPathwayRevised(sourceEvent, targetEvent.get()) || isAttributeRevised(sourceEvent, targetEvent.get()))
	            return true;
	    }

		return false;
	}


	/**
	 * Check if a ReactionlikeEvent is revised.
	 *
	 * @param sourceInstance
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isAttributeRevised(GKInstance sourceInstance, GKInstance targetInstance)
			throws InvalidAttributeException, Exception {

	    if (!sourceInstance.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return false;

		// Check for changes in inputs, outputs, regulators, and catalysts.
		List<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
                                                  ReactomeJavaConstants.output,
                                                  ReactomeJavaConstants.regulatedBy,
                                                  ReactomeJavaConstants.catalystActivity);
		for (String attrName : revisionList) {
			if (isAttributeRevised(sourceInstance, targetInstance, attrName))
				return true;
		}

		// Check if a catalyst or regulator is added or removed.
		if (isAttributeRevised(sourceInstance, targetInstance, ReactomeJavaConstants.regulatedBy))
		    return true;
		if (isAttributeRevised(sourceInstance, targetInstance, ReactomeJavaConstants.catalystActivity))
		    return true;

		// Check if summation is revised.
		if (isChildRLESummationRevised(sourceInstance, targetInstance))
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


	    // If attribute values are instances, then compare individual database id's.
	    if (sourceAttributes.get(0) instanceof GKInstance) {
            List<GKInstance> targetAttributeInstances = targetAttributes.stream()
                                                                        .map(GKInstance.class::cast)
                                                                        .collect(Collectors.toList());
            Optional<GKInstance> targetAttribute = null;

	        for (Object sourceAttribute: sourceAttributes) {
	            Long sourceAttributeDBID = ((GKInstance) sourceAttribute).getDBID();

	            targetAttribute = targetAttributeInstances.stream()
                                                          .filter(attr -> attr.getDBID().equals(sourceAttributeDBID))
                                                          .findAny();
	            // Check if there is an instance not shared by both parents.
                // If targetAttributes does not contain an instance in sourceAttributes,
                // then either targetAttributes has a deletion, or sourceAttributes has an addition.
	            if (!targetAttribute.isPresent())
	                return false;

	            // TODO Is it necessary to compare the attribute values of each "attribute instance"?
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
	 * @param sourceInstance
	 * @return boolean (true if summation is revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 *
	 * @see {@link org.gk.model.Summation}
	 */
	private boolean isChildRLESummationRevised(GKInstance sourceInstance, GKInstance targetInstance)
			throws InvalidAttributeException, Exception {

		List<GKInstance> sourceSummations = getRLESummations(sourceInstance);
		List<GKInstance> targetSummations = getRLESummations(targetInstance);
		// Check for size equality.
		if (sourceSummations.size() != targetSummations.size())
		    return true;

		// Iterate over source summations.
	    Optional<GKInstance> targetSummation = null;
		for (GKInstance sourceSummation : sourceSummations) {

		    // Get corresponding target summation.
		    // This stream is an easy way to short circuit (by returning false) if
		    // "targetSummations" does not contain "sourceSummation".
		    targetSummation = targetSummations.stream()
                                              .filter(summation -> summation.getDBID().equals(sourceSummation.getDBID()))
                                              .findAny();
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
	 * Return a list of all child RLE summations for a given pathway instance.
	 *
	 * @param instance
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	List<GKInstance> getRLESummations(GKInstance instance) throws InvalidAttributeException, Exception {
		List<GKInstance> events = instance.getAttributeValuesList(ReactomeJavaConstants.hasEvent);

		List<GKInstance> summations = new ArrayList<GKInstance>();
		GKInstance summation = null;
	    for (GKInstance event : events) {
	        // Continue if not an RLE.
	        if (!event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	            continue;
	        summation = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.summation);
	        summations.add(summation);
	    }

	    return summations;
	}


	/**
	 * <p>In the tests below:</p>
	 * <ul>
	 *   <li>A "source" instance is fetched from the test database "central"</li>
	 *   <li>A "target" instance is fetched from the test database "previous_slice".</li>
	 *   <li>The "source" instance is modified and compared against the "target" instance.</li>
	 * </ul>
	 */
	@Before
	public void setUp() throws SQLException {
	    String host = "localhost";
	    String user = "liam";
	    String pass = ")8J7m]!%[<";
		centralTestDBA = new MySQLAdaptor(host, "central", user, pass);
		previousSliceTestDBA = new MySQLAdaptor(host, "previous_slice", user, pass);
//		sliceTestDBA = new MySQLAdaptor(host, "slice", user, pass);
	}

	@Test
	public void testIsPathwayRevised() throws Exception {
	    GKInstance sourcePathway = null;
	    GKInstance targetPathway = null;
	    GKInstance sourceChildPathway = null;
	    GKInstance targetChildPathway = null;

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
		targetChildPathway = (GKInstance) targetPathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		sourcePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertTrue(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the removal.
		sourcePathway.addAttributeValue(ReactomeJavaConstants.hasEvent, sourceChildPathway);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));


		// Revise a child pathway (by changing it's summation text).
		GKInstance sourceChildPathwaySummation = (GKInstance) sourceChildPathway.getAttributeValue(ReactomeJavaConstants.summation);
		sourceChildPathwaySummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		sourceChildPathway.setAttributeValue(ReactomeJavaConstants.summation, sourceChildPathwaySummation);
		assertTrue(isPathwayRevised(sourcePathway, targetPathway));

		// Reset the revision.
		GKInstance targetChildPathwaySummation = (GKInstance) targetChildPathway.getAttributeValue(ReactomeJavaConstants.summation);
		String originalSummationText = (String) targetChildPathwaySummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceChildPathwaySummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertFalse(isPathwayRevised(sourcePathway, targetPathway));
	}

	@Test
	public void testIsChildRLESummationRevised() throws Exception {
		// Example pathway (neuronal system).
		GKInstance sourcePathway = (GKInstance) centralTestDBA.fetchInstance(164843L);
		GKInstance targetPathway = (GKInstance) previousSliceTestDBA.fetchInstance(164843L);
		assertFalse(isChildRLESummationRevised(sourcePathway, targetPathway));

        // Revise a pathway's summation text.
		GKInstance sourceEvent = (GKInstance) sourcePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent).get(0);
		GKInstance sourceSummation = (GKInstance) sourceEvent.getAttributeValuesList(ReactomeJavaConstants.summation).get(0);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, "revised");
		assertTrue(isChildRLESummationRevised(sourcePathway, targetPathway));

		// Reset the revision,
		GKInstance targetSummation = (GKInstance) targetPathway.getAttributeValue(ReactomeJavaConstants.summation);
		String targetSummationText = (String) targetSummation.getAttributeValue(ReactomeJavaConstants.text);
		sourceSummation.setAttributeValue(ReactomeJavaConstants.text, targetSummationText);
		assertFalse(isChildRLESummationRevised(sourcePathway, targetPathway));
	}

	@Test
	public void testisAttributeRevised() throws Exception {
		// Revision detection (non-GKInstance).
	    // Attribute (text) that will be revised.
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


		// Revision detection (GKInstance).
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


		// Addition or deletion detection.
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
