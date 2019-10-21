package org.gk.slicing;

import static org.junit.Assert.assertEquals;

import java.io.PrintStream;
import java.sql.SQLException;
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
import org.junit.Before;
import org.junit.Test;

public class RevisionDetector {
	// Database adaptor used for tests.
	// TODO Should the tests be moved to an independent class?
	private MySQLAdaptor testDBA;

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
	 * @param AttributeName
	 * @throws Exception
	 * @throws InvalidAttributeException
	 * @see {@link org.gk.database.SynchronizationManager#isInstanceClassSameInDb(GKInstance, MySQLAdapter)}
	 */
	public void checkForRevisions(String schemaClassName,
								  MySQLAdaptor sourceDBA,
								  MySQLAdaptor compareDBA,
								  Map<Long,GKInstance> sliceMap,
								  PrintStream ps) throws InvalidAttributeException, Exception {
		// Iterate over all instances in the slice.
		for (Long dbId : sliceMap.keySet()) {
			GKInstance inst = sliceMap.get(dbId);
			// Sanity Check.
			if (!inst.getSchemClass().isa(schemaClassName))
				continue;

			boolean revised = false;
			if (schemaClassName.equals(ReactomeJavaConstants.Pathway))
				revised = isPathwayRevised(inst, compareDBA);
			else if (schemaClassName.equals(ReactomeJavaConstants.ReactionlikeEvent))
				revised = isRLERevised(inst, compareDBA);

			// If a "revised flag" condition is met, set "revised flag" on the instance.
			if (revised) {
				// TODO determine API database UpdateTrack schema class.
			}
		}
	}

	/**
	 * Check if Pathway is revised.
	 *
	 * TODO Is the "in-method" recursive approach that
	 * {@link InstanceUtilities#getContainedInstances(GKInstance, String...)} takes recommended?
	 *
	 * @param pathway
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isPathwayRevised(GKInstance pathway, MySQLAdaptor targetDBA)
			throws InvalidAttributeException, Exception {
		if (pathway == null)
			return false;

		GKInstance targetInstance = targetDBA.fetchInstance(pathway.getDBID());

		// Check if an immediate child Pathway is added or removed
		if (isChildPathwayAddedOrRemoved(pathway, targetInstance))
			return true;

		// Finally check for changes in summation text.
		if (isSummationRevised(pathway, targetDBA))
			return true;

		// Recursively iterate over events in pathway.
		Collection<GKInstance> events = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);

		if (events != null && events.size() > 0) {
			for (GKInstance event : events) {
				// Pathway
				if (event.getSchemClass().isa(ReactomeJavaConstants.Pathway))
					isPathwayRevised(event, targetDBA);

				// RLE
				else if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)
					 && isRLERevised(event, targetDBA))
					return true;
			}
		}

		return false;
	}

	/**
	 * Check if a child pathway is added or removed for a given parent.
	 *
	 * @param parent
	 * @return boolean (true if a child pathway is added or removed, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws SQLException
	 * @throws Exception
	 */
	private boolean isChildPathwayAddedOrRemoved(GKInstance parent, GKInstance targetInstance)
			throws InvalidAttributeException, SQLException, Exception {

		Collection<GKInstance> childPathways = getChildPathways(parent);
		Collection<GKInstance> compareChildPathways = getChildPathways(targetInstance);

		if (childPathways.isEmpty() && compareChildPathways.isEmpty())
			return false;

		// If the number of child pathways is different between the two parents,
		// then an addition or deletion has taken place.
		if (childPathways.size() != compareChildPathways.size())
			return true;

		// List of child pathway IDs.
		Collection<Long> childPathwayDBIDs = childPathways.stream()
				.map(attrValue -> attrValue.getDBID())
				.collect(Collectors.toList());

		// Check if there is a child pathway not associated with both parents.
		Optional<Long> commonDBID = compareChildPathways.stream()
				// Get database ID.
				.map(attrValue -> attrValue.getDBID())
				// Set match criteria (identical database ID's).
				.filter(comparePathwayDBID -> childPathwayDBIDs.contains(comparePathwayDBID))
				// Find match (or null if no match).
				.findAny();

		if (!commonDBID.isPresent())
			return true;

		return false;
	}

	/**
	 * Return a list of child pathways for a given parent.
	 *
	 * @param parent
	 * @return List
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private Collection<GKInstance> getChildPathways(GKInstance parent) throws InvalidAttributeException, Exception {
		Collection<GKInstance> events = parent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
		return events.stream()
				.filter(attr -> attr.getSchemClass().isa(ReactomeJavaConstants.Pathway))
				.collect(Collectors.toList());
	}

	/**
	 * Check if a ReactionlikeEvent is revised.
	 *
	 * @param reactionlikeEvent
	 * @return boolean (true if revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	private boolean isRLERevised(GKInstance reactionlikeEvent, MySQLAdaptor targetDBA)
			throws InvalidAttributeException, Exception {
		// Check for changes in inputs, outputs, regulators, and catalysts.
		Collection<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
				ReactomeJavaConstants.output,
				ReactomeJavaConstants.regulatedBy,
				ReactomeJavaConstants.catalystActivity);

		for (String attrName : revisionList) {
			if (attributesRevised(reactionlikeEvent, targetDBA.fetchInstance(reactionlikeEvent.getDBID()), attrName))
				return true;
		}

		// Check if a catalyst or regulator is added or removed.
		Collection<String> additionsOrDeletionsList = Arrays.asList(ReactomeJavaConstants.regulatedBy,
				ReactomeJavaConstants.catalystActivity);

		for (String attrName : additionsOrDeletionsList) {
			if (additionOrDeletionInList(reactionlikeEvent, targetDBA.fetchInstance(reactionlikeEvent.getDBID()), attrName))
				return true;
		}

		// Check if summation is revised.
		// Requires "special case" (examining "text" attribute for all summations).
		if (isSummationRevised(reactionlikeEvent, targetDBA))
			return true;

		return false;
	}

	/**
	 * Compare the value of a given attribute between two instances.
	 *
	 * Valid attribute checks take place in
	 * {@link org.gk.slicing.SlicingEngine#isValidAttributeOrThrow(SchemaAttribute attribute)}
	 *
	 * @param oldInstance
	 * @param newInstance
	 * @param attrName
	 * @return boolean (true if an attribute value is revised, false if all attribute values are equal).
	 * @throws Exception
	 */
	private boolean attributesRevised(GKInstance oldInstance, GKInstance newInstance, String attrName)
			throws Exception {
		// Instance null checker.
		if (oldInstance == null || newInstance == null) {
			// if both instances are null, then there are no attributes to revise.
			if (oldInstance == newInstance)
				return false;
			// if only one instance is null, then any attribute may be considered revised.
			return true;
		}

		// Attribute null checker.
		if (oldInstance.getAttributeValue(attrName) == null || newInstance.getAttributeValue(attrName) == null) {
			if (oldInstance.getAttributeValue(attrName) == newInstance.getAttributeValue(attrName))
				return false;
			return true;
		}

		// If the object will not be able to cast to GKInstance, then simply compare the
		// values.
		if (oldInstance.getAttributeValue(attrName) instanceof String)
			return !oldInstance.getAttributeValue(attrName).equals(newInstance.getAttributeValue(attrName));

		// In the case where an attribute has more than one value.
		// Cast to GKInstance and compare the database ID's.
		List<Long> oldAttributeDBIDs = (List<Long>) oldInstance.getAttributeValuesList(attrName).stream()
                                                                                                .map(attr -> ((GKInstance) attr).getDBID())
                                                                                                .collect(Collectors.toList());
		List<Long> newAttributeDBIDs = (List<Long>) newInstance.getAttributeValuesList(attrName).stream()
                                                                                                .map(attr -> ((GKInstance) attr).getDBID())
                                                                                                .collect(Collectors.toList());
		if (oldAttributeDBIDs.size() != newAttributeDBIDs.size())
		    return true;

		for (Long oldAttributeDBID : oldAttributeDBIDs) {
		    if (!newAttributeDBIDs.contains(oldAttributeDBID))
		        return true;
		}

		return false;
	}

	/**
	 * Compare the attribute lists in order to detect additions or deletions.
	 *
	 * @param oldInstance
	 * @param newInstance
	 * @param attrName
	 * @return boolean (true if there is an addition or deletion in the attribute list, false if the lists are equal).
	 * @throws InvalidAttributeException
	 * @throws SQLException
	 * @throws Exception
	 */
	private boolean additionOrDeletionInList(GKInstance oldInstance, GKInstance newInstance, String attrName)
			throws InvalidAttributeException, SQLException, Exception {
		Collection<GKInstance> newInstanceList = newInstance.getAttributeValuesList(attrName);
		Collection<GKInstance> oldInstanceList = oldInstance.getAttributeValuesList(attrName);

		// Constant time check to detect additions or deletions.
		// Misses the case where the same number of unique additions and deletions have occurred.
		// That case is detected by the iterator below.
		if (newInstanceList.size() > oldInstanceList.size()
		 || newInstanceList.size() < oldInstanceList.size())
			return true;

		// O(n^2) time check to detect additions or deletions.
		for (Object instance : oldInstanceList) {
			// If newInstanceList does not contain an instance in oldInstanceList,
			// then either newInstanceList has a deletion, or oldInstanceList has an addition.
			if (!newInstanceList.contains(instance)) {
				for (Object inst : newInstanceList) {
					Long oldInstanceDBID = ((GKInstance) inst).getDBID();
					Long newInstanceDBID = ((GKInstance) inst).getDBID();

					// An attribute has been added or deleted from the instance.
					if (!oldInstanceDBID.equals(newInstanceDBID))
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
	 * @param instance
	 * @return boolean (true if summation is revised, false otherwise).
	 * @throws InvalidAttributeException
	 * @throws Exception
	 *
	 * @see {@link org.gk.model.Summation}
	 */
	private boolean isSummationRevised(GKInstance instance, MySQLAdaptor targetDBA)
			throws InvalidAttributeException, Exception {
		Collection<GKInstance> summations = instance.getAttributeValuesList(ReactomeJavaConstants.summation);
		for (GKInstance summation : summations) {
			// if a change in text is detected, then summation is considered revised.
			if (attributesRevised(summation, targetDBA.fetchInstance(summation.getDBID()), ReactomeJavaConstants.text))
				return true;
		}

		return false;
	}

	/**
	 * Set up a database to be used as a "before" state for testing.
	 *
	 * @throws SQLException
	 */
	@Before
	public void setUp() throws SQLException {
		testDBA = new MySQLAdaptor("localhost",
								   "reactome",
								   "liam",
								   ")8J7m]!%[<");
	}

	@Test
	public void testIsRLERevised() throws Exception {
		// Example RLE (DIT and MIT combine to form triiodothyronine).
		GKInstance RLE = (GKInstance) testDBA.fetchInstance(209925L).clone();
		RLE.setDBID(209925L);
		assertEquals(false, isRLERevised(RLE, testDBA));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		RLE.addAttributeValue(ReactomeJavaConstants.regulatedBy, testDBA.fetchInstance(5210962L));
		assertEquals(true, isRLERevised(RLE, testDBA));

		// Remove attribute.
		RLE.removeAttributeValueNoCheck(ReactomeJavaConstants.regulatedBy, testDBA.fetchInstance(5210962L));
		assertEquals(false, isRLERevised(RLE, testDBA));
	}

	@Test
	public void testIsPathwayRevised() throws Exception {
		// Example pathway #1 (xylitol degradation).
		GKInstance xylitolDegradation = (GKInstance) testDBA.fetchInstance(5268107L).clone();
		xylitolDegradation.setDBID(5268107L);
		assertEquals(false, isPathwayRevised(xylitolDegradation, testDBA));

		// Example addition of a child pathway (tRNA processing).
		GKInstance pathway = testDBA.fetchInstance(72306L);
		xylitolDegradation.addAttributeValue(ReactomeJavaConstants.hasEvent, pathway);
		assertEquals(true, isPathwayRevised(xylitolDegradation, testDBA));
		// Reset the addition.
		xylitolDegradation.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, pathway);
		assertEquals(false, isPathwayRevised(xylitolDegradation, testDBA));


		// Example pathway #2 (neuronal system).
		GKInstance neuronalSystem = (GKInstance) testDBA.fetchInstance(112316L).clone();
		neuronalSystem.setDBID(112316L);
		assertEquals(false, isPathwayRevised(neuronalSystem, testDBA));

		// Remove an existing child pathway.
		GKInstance removedChildPathway = testDBA.fetchInstance(1296071L);
		neuronalSystem.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, removedChildPathway);
		assertEquals(true, isPathwayRevised(neuronalSystem, testDBA));
		// Reset the removal.
		neuronalSystem.addAttributeValue(ReactomeJavaConstants.hasEvent, removedChildPathway);
		assertEquals(false, isPathwayRevised(neuronalSystem, testDBA));
	}

	@Test
	public void testSliceAttributes() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance oldInstance = testDBA.fetchInstance(8875579L);

		// ABI2 [cytosol]
		GKInstance newInstance = testDBA.fetchInstance(1671649L);

		assertEquals(false, attributesRevised(oldInstance, oldInstance, ReactomeJavaConstants.stableIdentifier));
		assertEquals(false, attributesRevised(newInstance, newInstance, ReactomeJavaConstants.stableIdentifier));
		assertEquals(true, attributesRevised(oldInstance, newInstance, ReactomeJavaConstants.stableIdentifier));
	}

	@Test
	public void testAdditionOrDeletionInLists() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance oldInstance = (GKInstance) testDBA.fetchInstance(8875579L).clone();
		oldInstance.setDBID(8875579L);
		GKInstance newInstance = (GKInstance) oldInstance.clone();

		assertEquals(false, additionOrDeletionInList(oldInstance, oldInstance, ReactomeJavaConstants.name));
		assertEquals(false, additionOrDeletionInList(oldInstance, newInstance, ReactomeJavaConstants.name));
		assertEquals(false, additionOrDeletionInList(newInstance, newInstance, ReactomeJavaConstants.name));

		newInstance.addAttributeValue(ReactomeJavaConstants.name, "dedicator of cytokinesis 7");

		assertEquals(false, additionOrDeletionInList(newInstance, newInstance, ReactomeJavaConstants.name));
		assertEquals(true, additionOrDeletionInList(oldInstance, newInstance, ReactomeJavaConstants.name));
	}
}
