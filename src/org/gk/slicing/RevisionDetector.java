package org.gk.slicing;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.Test;

public class RevisionDetector {
	// Database adaptor used for tests.
	private MySQLAdaptor dba;

    private static final Logger logger = Logger.getLogger(RevisionDetector.class);

    /**
     * <p><strong>An RLE gets a revised flag if:</strong></p>
     * <ol>
     *   <li>An immediate child RLE revised</li>
     *   <ul>
     *     <li>A Catalyst added/removed/changed</li>
     *     <li>A regulator added/removed/changed</li>
     *     <li>A change is made in inputs/outputs</li>
     *     <li>A significant change in summation text</li>
     *   </ul>
     * </ol>
     * 
     * <p><strong>A pathway gets a revised flag if:</strong></p>
     * <ol>
     *   <li>An immediate child RLE revised 
     *   <ul>
     *     <li>A Catalyst added/removed/changed</li>
     *     <li>A regulator added/removed/changed</li>
     *     <li>A change is made in inputs/outputs</li>
     *     <li>A significant change in summation text</li>
     *   </ul>
     *   <li>An immediate child RLE is added/removed</li>
     *   <li>An immediate child Pathway added/removed</li>
     *   <li>An immediate child Pathway is revised as in B</li>
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
								   Map<Long,GKInstance> sliceMap) throws InvalidAttributeException, Exception {
    	// Verify that `schemaClass` is a valid schema class.
    	if (!sourceDBA.getSchema().isValidClass(schemaClassName)
		 || !compareDBA.getSchema().isValidClass(schemaClassName))
    		return;

        logger.info("checkForRevisions(" + schemaClassName + ")");

		// Iterate over all instances in the slice.
		for (long dbId : sliceMap.keySet()) {
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
	private boolean isPathwayRevised(GKInstance pathway, MySQLAdaptor dba) throws InvalidAttributeException, Exception {
		if (pathway == null)
			return false;

		// Recursively iterate over events in pathway.
		Collection<GKInstance> events = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);

		if (events != null && events.size() > 0) {
			for (GKInstance event : events) {
				// Pathway
				if (event.getSchemClass().isa(ReactomeJavaConstants.Pathway))
					isPathwayRevised(event, dba);

				// RLE
				else if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)
						&& isRLERevised(event, dba))
					return true;
			}
		}

		// Check if an immediate child Pathway is added or removed
		if (isChildPathwayAddedOrRemoved(pathway, dba))
			return true;

		// Finally check for changes in summation text.
		if (isSummationRevised(pathway, dba))
			return true;

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
	private boolean isChildPathwayAddedOrRemoved(GKInstance parent, MySQLAdaptor dba)
			throws InvalidAttributeException, SQLException, Exception {

		Collection<GKInstance> childPathways = getChildPathways(parent);
		Collection<GKInstance> compareChildPathways = getChildPathways(getInstance(dba, parent));

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
	private boolean isRLERevised(GKInstance reactionlikeEvent, MySQLAdaptor dba) throws InvalidAttributeException, Exception {
		// Check for changes in inputs, outputs, regulators, and catalysts.
		Collection<String> revisionList = Arrays.asList(ReactomeJavaConstants.input,
				ReactomeJavaConstants.output,
				ReactomeJavaConstants.regulatedBy,
				ReactomeJavaConstants.catalystActivity);

		for (String attrName : revisionList) {
			if (revisionInAttributeList(reactionlikeEvent, getInstance(dba, reactionlikeEvent), attrName))
				return true;
		}

		// Check if a catalyst or regulator is added or removed.
		Collection<String> additionsOrDeletionsList = Arrays.asList(ReactomeJavaConstants.regulatedBy,
				ReactomeJavaConstants.catalystActivity);

		for (String attrName : additionsOrDeletionsList) {
			if (additionOrDeletionInList(reactionlikeEvent, getInstance(dba, reactionlikeEvent), attrName))
				return true;
		}

		// Check if summation is revised.
		// Requires "special case" (examining "text" attribute for all summations).
		if (isSummationRevised(reactionlikeEvent, dba))
			return true;

		return false;
	}

	/**
	 * Compare the value of a given attribute between two instances.
	 *
	 * Valid attribute checks take place in
	 * {@link org.gk.slicing.SlicingEngine#isValidAttributeOrThrow(SchemaAttribute attribute)}
	 *
	 * @param left
	 * @param right
	 * @param attrName
	 * @return boolean (true if an attribute value is revised, false if all attribute values are equal).
	 * @throws Exception
	 */
	private boolean attributesRevised(GKInstance left, GKInstance right, String attrName) throws Exception {
		// Null checker.
		if (left == null || right == null) {
			// if both instances are null, then there are no attributes to revise.
			if (left == right)
				return false;
			// if only one instance is null, then any attribute may be considered revised.
			return true;
		}

		// If the object will not be able to cast to GKInstance, then simply compare the values.
		if (left.getAttributeValue(attrName) instanceof String)
			return !left.getAttributeValue(attrName).equals(right.getAttributeValue(attrName));

		// Default option is to cast to GKInstance and compare the database ID's.
		Long leftDBID = ((GKInstance) left.getAttributeValue(attrName)).getDBID();
		Long rightDBID = ((GKInstance) right.getAttributeValue(attrName)).getDBID();
		return !leftDBID.equals(rightDBID);
	}

	/**
	 * Iterate over and compare all elements in a GKInstance's list of a given attribute.
	 *
	 * @param left
	 * @param right
	 * @param attrName
	 * @return boolean (true if a revision is detected, false if all attributes are equal).
	 * @throws SQLException
	 * @throws Exception
	 */
	private boolean revisionInAttributeList(GKInstance left, GKInstance right, String attrName)
			throws SQLException, Exception {
		Collection<GKInstance> attrList = left.getAttributeValuesList(attrName);
		for (GKInstance attrValue : attrList) {
			// If slice output differs from database output
			if (attributesRevised(left, right, attrName))
				return true;
		}

		return false;
	}

	/**
	 * Compare the attribute lists in order to detect additions or deletions.
	 *
	 * @param left
	 * @param right
	 * @param attrName
	 * @return boolean (true if there is an addition or deletion in the attribute list, false if the lists are equal).
	 * @throws InvalidAttributeException
	 * @throws SQLException
	 * @throws Exception
	 */
	private boolean additionOrDeletionInList(GKInstance left, GKInstance right, String attrName)
			throws InvalidAttributeException, SQLException, Exception {
		Collection<Object> rightList = right.getAttributeValuesList(attrName);
		Collection<Object> leftList = left.getAttributeValuesList(attrName);

		// Constant time check to detect additions or deletions.
		// Misses the case where the same number of unique additions and deletions have occurred.
		// That case is detected by the iterator below.
		if (rightList.size() > leftList.size()
				|| rightList.size() < leftList.size())
			return true;

		// O(n^2) time check to detect additions or deletions.
		for (Object instance : leftList) {
			// If rightList does not contain an instance in leftList,
			// then either rightList has a deletion, or leftList has an addition.
			if (!rightList.contains(instance)) {
				for (Object inst : rightList) {
					Long leftDBID = ((GKInstance) inst).getDBID();
					Long rightDBID = ((GKInstance) inst).getDBID();

					// An attribute has been added or deleted from the instance.
					if (!leftDBID.equals(rightDBID))
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
	private boolean isSummationRevised(GKInstance instance, MySQLAdaptor dba) throws InvalidAttributeException, Exception {
		Collection<GKInstance> summations = instance.getAttributeValuesList(ReactomeJavaConstants.summation);
		for (GKInstance summation : summations) {
			// if a change in text is detected, then summation is considered revised.
			if (attributesRevised(summation, getInstance(dba, summation), ReactomeJavaConstants.text))
				return true;
		}

		return false;
	}

	/**
	 * Given a particular GKInstance, return the associated GKInstance from the "compare" slice.
	 *
	 * @param instance
	 * @return GKInstance
	 * @throws SQLException
	 * @throws Exception
	 */
	private GKInstance getInstance(MySQLAdaptor dba, GKInstance instance) throws SQLException, Exception {
		return getInstance(dba, instance.getDBID());
	}

	private GKInstance getInstance(MySQLAdaptor dba, Long DBID) throws SQLException, Exception {
		return dba.fetchInstance(DBID);
	}

	private GKInstance getInstanceShallow(MySQLAdaptor dba, GKInstance instance) throws SQLException, Exception {
		return getInstanceShallow(dba, instance.getDBID());
	}

	private GKInstance getInstanceShallow(MySQLAdaptor dba, Long DBID) throws SQLException, Exception {
		GKInstance clone = (GKInstance) getInstance(dba, DBID).clone();
		clone.setDBID(DBID);
		return clone;
	}

	/**
	 * Check that all elements in a list exist (not null with a length greater than 0).
	 *
	 * @param Collection
	 * @return boolean
	 */
	private boolean allElementsExist(Collection<Object> list) {
		for (Object element : list) {
			if (element == null || String.valueOf(element).length() == 0)
				return false;
		}
		return true;
	}

	/**
	 * Set up a database to be used as a "before" state for testing.
	 *
	 * Using the compare database is just a quick and simple to way to set
	 * up such a test database.
	 *
	 * @throws SQLException
	 */
	@Before
	public void setUp() throws SQLException {
		dba = new MySQLAdaptor("localhost",
							   "reactome",
						       "liam",
						       ")8J7m]!%[<");
	}

	@Test
	public void testIsRLERevised() throws Exception {
		// Example RLE (DIT and MIT combine to form triiodothyronine).
		GKInstance RLE = getInstanceShallow(dba, 209925L);
		assertEquals(false, isRLERevised(RLE, dba));

		// Example added attribute (Positive regulation by 'H+ [endosome lumen]').
		RLE.addAttributeValue(ReactomeJavaConstants.regulatedBy, getInstance(dba, 5210962L));
		assertEquals(true, isRLERevised(RLE, dba));

		// Remove attribute.
		RLE.removeAttributeValueNoCheck(ReactomeJavaConstants.regulatedBy, getInstance(dba, 5210962L));
		assertEquals(false, isRLERevised(RLE, dba));
	}

	@Test
	public void testIsPathwayRevised() throws Exception {
		// Example pathway #1 (xylitol degradation).
		GKInstance xylitolDegradation = getInstanceShallow(dba, 5268107L);
		assertEquals(false, isPathwayRevised(xylitolDegradation, dba));

		GKInstance clone = (GKInstance) xylitolDegradation.clone();
		// Example addition of a child pathway (tRNA processing).
		xylitolDegradation.addAttributeValue(ReactomeJavaConstants.hasEvent, getInstance(dba, 72306L));
		assertEquals(true, isPathwayRevised(xylitolDegradation, dba));
		// Reset the addition.
		xylitolDegradation.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent,
				getInstance(dba, 72306L));
		assertEquals(false, isPathwayRevised(xylitolDegradation, dba));


		// Example pathway #2 (neuronal system).
		GKInstance neuronalSystem = getInstanceShallow(dba, 112316L);
		assertEquals(false, isPathwayRevised(neuronalSystem, dba));

		// Remove an existing child pathway.
		GKInstance removedChildPathway = getInstance(dba, 1296071L);
		neuronalSystem.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, removedChildPathway);
		assertEquals(true, isPathwayRevised(neuronalSystem, dba));
		// Reset the removal.
		neuronalSystem.addAttributeValue(ReactomeJavaConstants.hasEvent, removedChildPathway);
		assertEquals(false, isPathwayRevised(neuronalSystem, dba));
	}

	@Test
	public void testSliceAttributes() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance left = getInstance(dba, 8875579L);

		// ABI2 [cytosol]
		GKInstance right = getInstance(dba, 1671649L);

		assertEquals(false, attributesRevised(left, left, ReactomeJavaConstants.stableIdentifier));
		assertEquals(false, attributesRevised(right, right, ReactomeJavaConstants.stableIdentifier));
		assertEquals(true, attributesRevised(left, right, ReactomeJavaConstants.stableIdentifier));
	}

	@Test
	public void testSliceAllAttributesInList() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance left = getInstance(dba, 8875579L);

		// ABI2 [cytosol]
		GKInstance right = getInstance(dba, 1671649L);

		assertEquals(false, revisionInAttributeList(left, left, ReactomeJavaConstants.hasCandidate));
		assertEquals(false, revisionInAttributeList(right, right, ReactomeJavaConstants.hasCandidate));
		assertEquals(true, revisionInAttributeList(left, right, ReactomeJavaConstants.hasCandidate));
	}

	@Test
	public void testAdditionOrDeletionInLists() throws Exception {
		// (DOCK7) [cytosol]
		GKInstance left = getInstanceShallow(dba, 8875579L);
		GKInstance right = (GKInstance) left.clone();

		assertEquals(false, additionOrDeletionInList(left, left, ReactomeJavaConstants.name));
		assertEquals(false, additionOrDeletionInList(left, right, ReactomeJavaConstants.name));
		assertEquals(false, additionOrDeletionInList(right, right, ReactomeJavaConstants.name));

		right.addAttributeValue(ReactomeJavaConstants.name, "dedicator of cytokinesis 7");

		assertEquals(false, additionOrDeletionInList(right, right, ReactomeJavaConstants.name));
		assertEquals(true, additionOrDeletionInList(left, right, ReactomeJavaConstants.name));
	}

	@Test
	public void testAllElementsExist() {
		Collection<Object> strings = Arrays.asList("Read", "Eval", "Print", "Loop");
		assertEquals(true, allElementsExist(strings));

		Collection<Object> nulls = Arrays.asList(null, "");
		assertEquals(false, allElementsExist(nulls));
	}
}
