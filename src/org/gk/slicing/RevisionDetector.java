package org.gk.slicing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
	private MySQLAdaptor testCentralDBA;
	private MySQLAdaptor testSliceDBA;
	private MySQLAdaptor testPreviousSliceDBA;

	/**
	 * <p><strong>An RLE gets a revised flag if:</strong></p>
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
	 * <p><strong>A pathway gets a revised flag if:</strong></p>
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
	 *	 <li>An immediate child Pathway is revised as in B</li>
	 * </ol>
	 *
	 * @param sliceDBA
	 * @param previousSliceDBA
	 * @param sliceMap
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
	 */
	public void checkForRevisions(MySQLAdaptor sliceDBA, MySQLAdaptor previousSliceDBA, Map<Long,GKInstance> sliceMap)
	        throws InvalidAttributeException, Exception {
	    GKInstance instance = null;
	    boolean revised = false;
		// Iterate over all instances in the slice.
		for (Long dbId : sliceMap.keySet()) {
			instance = sliceMap.get(dbId);

			// Revised Pathway.
			if (instance.getSchemClass().isa(ReactomeJavaConstants.Pathway))
				revised = isPathwayRevised(instance, previousSliceDBA);

			// Revised ReactionlikeEvent.
			else if (instance.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
				revised = isRLERevised(instance, previousSliceDBA);

			else continue;

			// If a "revised flag" condition is met, create new _UpdateTracker instance.
			if (revised) {
			    SchemaClass cls = sliceDBA.getSchema().getClassByName(ReactomeJavaConstants._UpdateTracker);
			    GKInstance updateTracker = new GKInstance(cls);
			    updateTracker.setDbAdaptor(sliceDBA);
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
	 * @param instance
	 * @param targetDBA
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isPathwayRevised(GKInstance instance, MySQLAdaptor targetDBA)
			throws InvalidAttributeException, Exception {

	    if (!instance.getSchemClass().isa(ReactomeJavaConstants.Pathway))
	        return false;

		GKInstance targetInstance = targetDBA.fetchInstance(instance.getDBID());

		// Check if an immediate child Pathway is added or removed
		if (isChildPathwayAddedOrRemoved(instance, targetInstance))
			return true;

		// Check for changes in summation text.
		if (isSummationRevised(instance, targetDBA))
			return true;

		// Recursively iterate over events in pathway.
		Collection<GKInstance> events = instance.getAttributeValuesList(ReactomeJavaConstants.hasEvent);

		if (events != null && events.size() > 0) {
			for (GKInstance event : events) {
				// Pathway
				if (event.getSchemClass().isa(ReactomeJavaConstants.Pathway)
				 && isPathwayRevised(event, targetDBA))
                    return true;

				// RLE
				else if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)
					 && isRLERevised(event, targetDBA))
					return true;
			}
		}

		return false;
	}

	/**
	 * Check if a child pathway is added or removed for a given parent pathway.
	 *
	 * @param sourceInstance
	 * @param targetInstance
	 * @return boolean (true if a child pathway is added or removed, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws SQLException
	 * @throws Exception
	 */
	private boolean isChildPathwayAddedOrRemoved(GKInstance sourceInstance, GKInstance targetInstance)
			throws InvalidAttributeException, SQLException, Exception {

	    if (!sourceInstance.getDBID().equals(targetInstance.getDBID()))
	            return false;

		List<GKInstance> sourcePathways = getFilteredList(sourceInstance, ReactomeJavaConstants.hasEvent, ReactomeJavaConstants.Pathway);
		List<GKInstance> targetPathways = getFilteredList(targetInstance, ReactomeJavaConstants.hasEvent, ReactomeJavaConstants.Pathway);

		// If the number of pathways is different between the two parents,
		// then an addition or deletion has taken place.
		if (sourcePathways.size() != targetPathways.size())
			return true;

		// List of child pathway IDs.
		Collection<Long> parentPathwayDBIDs = sourcePathways.stream()
                                                            .map(GKInstance::getDBID)
                                                            .collect(Collectors.toList());
		// Check if there is a pathway not shared by both parents.
		for (GKInstance instance : targetPathways) {
		    // A pathway has been added or removed.
		    if (!parentPathwayDBIDs.contains(instance.getDBID()))
		        return true;
		}

		// No child pathway has been added or removed.
		return false;
	}

	private List<GKInstance> getFilteredList(GKInstance instance, String attrClass, String filterClass)
	        throws InvalidAttributeException, Exception {
	    List<Object> unfilteredList = instance.getAttributeValuesList(attrClass);
	    List<GKInstance> filteredList = new ArrayList<GKInstance>();
	    GKInstance attrInstance = null;
	    for (Object attr : unfilteredList) {
	        attrInstance = (GKInstance) attr;
	        if (attrInstance.getSchemClass().isa(filterClass))
	            filteredList.add(attrInstance);
	    }

	    return filteredList;
	}

	/**
	 * Check if a ReactionlikeEvent is revised.
	 *
	 * @param instance
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isRLERevised(GKInstance instance, MySQLAdaptor targetDBA)
			throws InvalidAttributeException, Exception {

	    if (!instance.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
	        return false;

		// Check for changes in inputs, outputs, regulators, and catalysts.
		Collection<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
				ReactomeJavaConstants.output,
				ReactomeJavaConstants.regulatedBy,
				ReactomeJavaConstants.catalystActivity);

		for (String attrName : revisionList) {
			if (attributesRevised(instance, targetDBA.fetchInstance(instance.getDBID()), attrName))
				return true;
		}

		// Check if a catalyst or regulator is added or removed.
		Collection<String> additionsOrDeletionsList = Arrays.asList(ReactomeJavaConstants.regulatedBy,
				ReactomeJavaConstants.catalystActivity);

		for (String attrName : additionsOrDeletionsList) {
			if (additionOrDeletionInList(instance, targetDBA.fetchInstance(instance.getDBID()), attrName))
				return true;
		}

		// Check if summation is revised.
		// Requires "special case" (examining "text" attribute for all summations).
		if (isSummationRevised(instance, targetDBA))
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
	 * @param attrName
	 * @return boolean (true if an attribute value is revised, false if all attribute values are equal).
	 * @throws Exception
	 */
	private boolean attributesRevised(GKInstance sourceInstance, GKInstance targetInstance, String attrName)
			throws Exception {

	    if (!sourceInstance.getDBID().equals(targetInstance.getDBID()))
	            return false;

        // If both instances are null, then there are no attributes to revise.
        // If only one instance is null, then any attribute may be considered revised.
		if (sourceInstance == null || targetInstance == null)
			return sourceInstance.equals(targetInstance);

		// Cast to GKInstance and compare attribute values.
		// Covers the case where an attribute has more than one value.
		List<Object> sourceAttributeValues = sourceInstance.getAttributeValuesList(attrName);
		List<Object> targetAttributeValues = targetInstance.getAttributeValuesList(attrName);
		if (sourceAttributeValues.size() != targetAttributeValues.size())
		    return true;

		for (int i = 0; i < sourceAttributeValues.size(); i++) {
		    Object sourceAttributeValue = sourceAttributeValues.get(i);
		    Object targetAttributeValue = targetAttributeValues.get(i);

		    // If attribute values are instances, then compare database id's
		    if (sourceAttributeValue instanceof GKInstance || targetAttributeValue instanceof GKInstance) {
		        // Check for type mismatch.
		        if (!sourceAttributeValue.getClass().equals(targetAttributeValue.getClass()))
		            return true;
		        // Check database id's.
		        if (!((GKInstance) sourceAttributeValue).getDBID().equals(((GKInstance) targetAttributeValue).getDBID()))
		            return true;
		    }

		    // If attribute values are numbers or Strings, then compare directly.
		    else {
		        if (!sourceAttributeValue.equals(targetAttributeValue))
                    return true;
		    }
		}

		// Attributes are not revised.
		return false;
	}

	/**
	 * Compare the attribute lists in order to detect additions or deletions.
	 *
	 * @param sourceInstance
	 * @param targetInstance
	 * @param attrName
	 * @return boolean (true if there is an addition or deletion in the attribute list, false if the lists are equal).
	 * @throws InvalidAttributeException
	 * @throws SQLException
	 * @throws Exception
	 */
	private boolean additionOrDeletionInList(GKInstance sourceInstance, GKInstance targetInstance, String attrName)
			throws InvalidAttributeException, SQLException, Exception {

	    if (!sourceInstance.getDBID().equals(targetInstance.getDBID()))
	            return false;

		Collection<GKInstance> targetInstanceList = targetInstance.getAttributeValuesList(attrName);
		Collection<GKInstance> sourceInstanceList = sourceInstance.getAttributeValuesList(attrName);

		// Constant time check to detect additions or deletions.
		// Misses the case where the same number of unique additions and deletions have occurred.
		// That case is detected by the iterator below.
		if (targetInstanceList.size() > sourceInstanceList.size()
		 || targetInstanceList.size() < sourceInstanceList.size())
			return true;

		// O(n^2) time check to detect additions or deletions.
		for (Object instance : sourceInstanceList) {
			// If targetInstanceList does not contain an instance in sourceInstanceList,
			// then either targetInstanceList has a deletion, or sourceInstanceList has an addition.
			if (!targetInstanceList.contains(instance)) {
				for (Object inst : targetInstanceList) {
					Long sourceInstanceDBID = ((GKInstance) inst).getDBID();
					Long targetInstanceDBID = ((GKInstance) inst).getDBID();

					// An attribute has been added or deleted from the instance.
					if (!sourceInstanceDBID.equals(targetInstanceDBID))
						return true;
				}
			}
		}

		// The lists have the same elements.
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
	private boolean isSummationRevised(GKInstance sourceInstance, MySQLAdaptor targetDBA)
			throws InvalidAttributeException, Exception {

	    if (!sourceInstance.getSchemClass().isValidAttribute(ReactomeJavaConstants.summation))
	        return false;

		Collection<GKInstance> summations = sourceInstance.getAttributeValuesList(ReactomeJavaConstants.summation);
		for (GKInstance summation : summations) {
			// if a change in text is detected, then summation is considered revised.
			if (attributesRevised(summation, targetDBA.fetchInstance(summation.getDBID()), ReactomeJavaConstants.text))
				return true;
		}

		return false;
	}

	@Before
	public void setUp() throws SQLException {
	    String host = "localhost";
	    String user = "liam";
	    String pass = ")8J7m]!%[<";
		testCentralDBA = new MySQLAdaptor(host, "central", user, pass);
		testSliceDBA = new MySQLAdaptor(host, "slice", user, pass);
		testPreviousSliceDBA = new MySQLAdaptor(host, "previousSlice", user, pass);
	}

	@Test
	public void testIsChildPathwayAddedOrRemoved() throws Exception {
	    // Example pathway (2-LTR circle formation).
	    GKInstance sourceInstance = testPreviousSliceDBA.fetchInstance(164843L);
	    GKInstance targetInstance = (GKInstance) sourceInstance.clone();
	    assertFalse(isChildPathwayAddedOrRemoved(sourceInstance, targetInstance));

	    List<Object> events = sourceInstance.getAttributeValuesList(ReactomeJavaConstants.hasEvent);

	    // Reaction ((-)-(4S)-limonene synthase).
	    GKInstance newNonPathway = testPreviousSliceDBA.fetchInstance(1119935L);
	    events.add(newNonPathway);
	    targetInstance.setAttributeValue(ReactomeJavaConstants.hasEvent, events);
	    assertFalse(isChildPathwayAddedOrRemoved(sourceInstance, targetInstance));

	    // Pathway (ABC Transporters).
	    GKInstance newChildPathway = testPreviousSliceDBA.fetchInstance(5669074L);
	    events.add(newChildPathway);
	    targetInstance.setAttributeValue(ReactomeJavaConstants.hasEvent, events);
	    assertTrue(isChildPathwayAddedOrRemoved(sourceInstance, targetInstance));
	}

	@Test
	public void testIsRLERevised() throws Exception {
		// Example RLE (DIT and MIT combine to form triiodothyronine).
		GKInstance RLE = (GKInstance) testPreviousSliceDBA.fetchInstance(209925L);
		RLE.setDBID(209925L);
		assertFalse(isRLERevised(RLE, testPreviousSliceDBA));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		RLE.addAttributeValue(ReactomeJavaConstants.regulatedBy, testPreviousSliceDBA.fetchInstance(5210962L));
		assertTrue(isRLERevised(RLE, testPreviousSliceDBA));

		// Remove attribute.
		RLE.removeAttributeValueNoCheck(ReactomeJavaConstants.regulatedBy, testPreviousSliceDBA.fetchInstance(5210962L));
		assertFalse(isRLERevised(RLE, testPreviousSliceDBA));
	}

	@Test
	public void testIsPathwayRevised() throws Exception {
		// Example pathway #1 (xylitol degradation).
		GKInstance xylitolDegradation = (GKInstance) testPreviousSliceDBA.fetchInstance(5268107L);
		xylitolDegradation.setDBID(5268107L);
		assertFalse(isPathwayRevised(xylitolDegradation, testPreviousSliceDBA));

		// Add a new child pathway (tRNA processing).
		GKInstance pathway = testPreviousSliceDBA.fetchInstance(72306L);
		xylitolDegradation.addAttributeValue(ReactomeJavaConstants.hasEvent, pathway);
		assertTrue(isPathwayRevised(xylitolDegradation, testPreviousSliceDBA));
		// Reset the addition.
		xylitolDegradation.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, pathway);
		assertFalse(isPathwayRevised(xylitolDegradation, testPreviousSliceDBA));


		// Example pathway #2 (neuronal system).
		GKInstance neuronalSystem = (GKInstance) testPreviousSliceDBA.fetchInstance(112316L);
		neuronalSystem.setDBID(112316L);
		assertFalse(isPathwayRevised(neuronalSystem, testPreviousSliceDBA));

		// Remove an existing child pathway (potassium channels).
		GKInstance potassiumChannels = testPreviousSliceDBA.fetchInstance(1296071L);
		neuronalSystem.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, potassiumChannels);
		assertTrue(isPathwayRevised(neuronalSystem, testPreviousSliceDBA));
		// Reset the removal.
		neuronalSystem.addAttributeValue(ReactomeJavaConstants.hasEvent, potassiumChannels);
		assertFalse(isPathwayRevised(neuronalSystem, testPreviousSliceDBA));

		// Revise a child pathway (by changing it's summation text).
		GKInstance potassiumChannelsSummation = (GKInstance) testPreviousSliceDBA.fetchInstance(1297383L);
		potassiumChannelsSummation.setDBID(1297383L);
		String originalSummationText = (String) potassiumChannelsSummation.getAttributeValue(ReactomeJavaConstants.text);
		potassiumChannelsSummation.setAttributeValue(ReactomeJavaConstants.text, "");
		potassiumChannels.setAttributeValue(ReactomeJavaConstants.summation, potassiumChannelsSummation);
		assertTrue(isPathwayRevised(neuronalSystem, testPreviousSliceDBA));

		// Reset the revision.
		potassiumChannelsSummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertFalse(isPathwayRevised(neuronalSystem, testPreviousSliceDBA));
	}

	@Test
	public void testIsSummationRevised() throws Exception {
		// Example pathway (neuronal system).
		GKInstance neuronalSystem = (GKInstance) testPreviousSliceDBA.fetchInstance(112316L);
		neuronalSystem.setDBID(112316L);
		assertFalse(isSummationRevised(neuronalSystem, testPreviousSliceDBA));

        // Revise a pathway's summation text.
		GKInstance neuronalSystemSummation = (GKInstance) testPreviousSliceDBA.fetchInstance(349546L);
		neuronalSystemSummation.setDBID(349546L);
		String originalSummationText = (String) neuronalSystemSummation.getAttributeValue(ReactomeJavaConstants.text);
		neuronalSystemSummation.setAttributeValue(ReactomeJavaConstants.text, "");
		neuronalSystem.setAttributeValue(ReactomeJavaConstants.summation, neuronalSystemSummation);
		assertTrue(isSummationRevised(neuronalSystem, testPreviousSliceDBA));

		// Reset the revision,
		neuronalSystemSummation.setAttributeValue(ReactomeJavaConstants.text, originalSummationText);
		assertFalse(isSummationRevised(neuronalSystem, testPreviousSliceDBA));
	}

	@Test
	public void testAttributesRevised() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance sourceInstance = testPreviousSliceDBA.fetchInstance(8875579L);

		// ABI2 [cytosol]
		GKInstance targetInstance = testPreviousSliceDBA.fetchInstance(1671649L);

		assertFalse(attributesRevised(sourceInstance, sourceInstance, ReactomeJavaConstants.stableIdentifier));
		assertFalse(attributesRevised(targetInstance, targetInstance, ReactomeJavaConstants.stableIdentifier));
		assertTrue(attributesRevised(sourceInstance, targetInstance, ReactomeJavaConstants.stableIdentifier));
	}

	@Test
	public void testAdditionOrDeletionInLists() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance sourceInstance = (GKInstance) testPreviousSliceDBA.fetchInstance(8875579L);
		sourceInstance.setDBID(8875579L);
		GKInstance targetInstance = (GKInstance) sourceInstance.clone();

		assertFalse(additionOrDeletionInList(sourceInstance, sourceInstance, ReactomeJavaConstants.name));
		assertFalse(additionOrDeletionInList(sourceInstance, targetInstance, ReactomeJavaConstants.name));
		assertFalse(additionOrDeletionInList(targetInstance, targetInstance, ReactomeJavaConstants.name));

		targetInstance.addAttributeValue(ReactomeJavaConstants.name, "dedicator of cytokinesis 7");

		assertFalse(additionOrDeletionInList(targetInstance, targetInstance, ReactomeJavaConstants.name));
		assertTrue(additionOrDeletionInList(sourceInstance, targetInstance, ReactomeJavaConstants.name));
	}
}
