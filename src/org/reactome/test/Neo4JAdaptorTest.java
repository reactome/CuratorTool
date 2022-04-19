package org.reactome.test;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.schema.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.driver.*;

import java.util.*;

import static org.junit.Assume.*;
import static org.junit.Assume.assumeTrue;

public class Neo4JAdaptorTest {

    private Driver driver;
    static Boolean checkedOnce = false;
    private Neo4JAdaptor neo4jAdaptor;
    private Schema schema = null;


    @Before
    public void baseTest() throws Exception {
        if (!checkedOnce) {
            neo4jAdaptor = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome",
                    7687);
            driver = neo4jAdaptor.getConnection();
            assumeTrue(fitForService());
            if (schema == null)
                schema = neo4jAdaptor.fetchSchema();
        }
    }

    @After
    public void closeSession() {
        driver.close();
    }

    @Test
    public void testFetchSchema() throws Exception {
        assumeTrue(schema.getClasses().size() > 0);
        assumeTrue(schema.getTimestamp() != null);
        SchemaClass sc = schema.getClassByName("Pathway");
        assumeTrue(sc.getName().equals("Pathway"));
        Collection<SchemaAttribute> attributes = sc.getAttributes();
        assumeTrue(attributes.size() > 0);
        boolean foundDbId = false;
        boolean foundSpecies = false;
        for (SchemaAttribute attribute : attributes) {
            if (attribute.getName().equals("dbId")) {
                foundDbId = true;
            } else if (attribute.getName().equals("species")) {
                foundSpecies = true;
            } else if (attribute.getName().equals("summation")) {
                assumeTrue(((GKSchemaClass) attribute.getSchemaClass().iterator().next()).getName().equals("Event"));
                assumeTrue(((GKSchemaClass) attribute.getAllowedClasses().iterator().next()).getName().equals("Summation"));
            }
            if (foundDbId && foundSpecies) {
                break;
            }
        }
        assumeTrue(foundDbId);
        assumeTrue(foundSpecies);
        assumeNotNull(schema.getTimestamp());
    }

    @Test
    public void testAttributeCategory() throws Exception {
        SchemaClass sc = schema.getClassByName("Event");
        Collection<SchemaAttribute> attributes = sc.getAttributes();
        assumeTrue(attributes.size() > 0);
        for (SchemaAttribute attribute : attributes) {
            if (attribute.getName().equals("summation")) {
                assumeTrue(attribute.getCategory() == SchemaAttribute.MANDATORY);
                break;
            }
        }
    }

    @Test
    public void testSchemaTimestamp() throws Exception {
        assumeTrue(neo4jAdaptor.getSchemaTimestamp() != null);
    }


    @Test
    public void testLoadInstanceAttributeValues() throws Exception {
        Collection<Instance> instances =
                neo4jAdaptor.fetchInstancesByClass("Pathway", Collections.singletonList(9612973L));
        GKInstance gkIns = (GKInstance) instances.iterator().next();
        neo4jAdaptor.loadInstanceAttributeValues(gkIns);
        assumeTrue(gkIns.getAttributeValuesList("displayName").size() > 0);
        assumeTrue(gkIns.getAttributeValuesList("hasEvent").size() > 1);
        boolean foundEvent = false;
        for (GKInstance instance : (List<GKInstance>) gkIns.getAttributeValuesList("hasEvent")) {
            if (instance.getAttributeValue("displayName").equals("Chaperone Mediated Autophagy")) {
                foundEvent = true;
            }
        }
        assumeTrue(foundEvent);
    }

    @Test
    public void testChainChangeLogInReferenceGeneProduct() throws Exception {
        Collection<Instance> instances =
                neo4jAdaptor.fetchInstancesByClass(
                        "ReferenceGeneProduct",
                        Collections.singletonList(48889l));
        GKInstance gkIns = (GKInstance) instances.iterator().next();
        neo4jAdaptor.loadInstanceAttributeValues(gkIns);
        assumeTrue(gkIns.getAttributeValuesList("chainChangeLog").size() > 0);
    }

    @Test
    public void testDoReleaseInEvent() throws Exception {
        Collection<Instance> instances =
                neo4jAdaptor.fetchInstancesByClass(
                        "Event",
                        Collections.singletonList(15869l));
        GKInstance gkIns = (GKInstance) instances.iterator().next();
        neo4jAdaptor.loadInstanceAttributeValues(gkIns);
        assumeTrue(gkIns.getAttributeValuesList("doRelease").size() > 0);
        assumeTrue((Boolean) gkIns.getAttributeValuesList("doRelease").get(0));
    }

    @Test
    public void testFetchInstancesByClass() throws Exception {
        Collection<Instance> instances = neo4jAdaptor.fetchInstancesByClass("Pathway", null);
        assumeTrue(instances.size() > 0);
        List<Long> dbIds = new ArrayList();
        dbIds.add(9612973l);
        dbIds.add(1640170l);
        instances = neo4jAdaptor.fetchInstancesByClass("Pathway", dbIds);
        assumeTrue(instances.size() == 2);
    }

    @Test
    public void testFetchInstanceByClassAndDBId() throws Exception {
        GKInstance instance = neo4jAdaptor.fetchInstance("Pathway", 9612973l);
        assumeNotNull(instance);
    }

    @Test
    public void testFetchInstanceByDBId() throws Exception {
        GKInstance instance = neo4jAdaptor.fetchInstance(9612973l);
        assumeTrue(instance.getSchemClass().getName().equals("DatabaseObject"));
        assumeTrue(instance.getDisplayName().equals("Autophagy"));
    }

    @Test
    public void testGetClassInstanceCount() throws Exception {
        long cnt = neo4jAdaptor.getClassInstanceCount("Pathway");
        assumeTrue(cnt > 0);
    }

    @Test
    public void testGetAllInstanceCounts() throws Exception {
        Map<String, Long> classNameToCount = neo4jAdaptor.getAllInstanceCounts();
        assumeTrue(classNameToCount.get("Pathway") > 0);
    }

    @Test
    public void testExist() throws Exception {
        assumeTrue(neo4jAdaptor.exist(9612973l));
        assumeFalse(neo4jAdaptor.exist(-1l));
    }

    @Test
    public void testFetchSchemaClassnameByDBID() throws Exception {
        assumeTrue(neo4jAdaptor.fetchSchemaClassnameByDBID(9612973l).equals("Pathway"));
    }

    @Test
    public void testStableIdentifier() throws Exception {
        SchemaClass sc = schema.getClassByName("Pathway");
        // Create Pathway instance
        GKInstance instance = neo4jAdaptor.fetchInstance(9613829l);
        List<GKInstance> instances =
                (List<GKInstance>) instance.getAttributeValuesList("stableIdentifier");
        assumeTrue(instances.size() == 1);
        assumeTrue(instances.get(0).getAttributeValue("identifier").equals("R-HSA-9613829"));
    }

    @Test
    public void testStoreAndDeleteInstance() throws Exception {
        SchemaClass sc = schema.getClassByName("Pathway");
        // Create Pathway instance
        GKInstance instance = neo4jAdaptor.fetchInstance(9612973l);
        instance.setDBID(null);
        instance.setDisplayName("TestName");
        instance.setSchemaClass(sc);
        assumeTrue(instance.getDisplayName().equals("TestName"));
        // Create Compartment instance
        SchemaClass sc1 = schema.getClassByName("Compartment");
        GKInstance instance1 = neo4jAdaptor.fetchInstance(7660l);
        instance1.setSchemaClass(sc1);
        instance1.setDBID(null);
        instance1.setDisplayName("TestCompartment");
        // Set Compartment instance as value for attribute "compartment"
        instance.setAttributeValue("compartment", Collections.singletonList(instance1));
        try (Session session = driver.session(SessionConfig.forDatabase(neo4jAdaptor.getDBName()))) {
            Transaction tx = session.beginTransaction();
            long dbID = neo4jAdaptor.storeInstance(instance, tx);
            tx.commit();
            assumeTrue(dbID >= 9760736);
            tx = session.beginTransaction();
            neo4jAdaptor.deleteInstance(instance, tx);
            tx.commit();
            // Deleting an instance removes it and its relationships, but not nodes at the other end of those relationships
            assumeTrue(neo4jAdaptor.fetchInstance(sc.getName(), dbID) == null);
            long dbID1 = instance1.getDBID();
            assumeTrue(neo4jAdaptor.fetchInstance(sc1.getName(), dbID1) != null);
            tx = session.beginTransaction();
            neo4jAdaptor.deleteInstance(instance1, tx);
            tx.commit();
            assumeTrue(neo4jAdaptor.fetchInstance(sc1.getName(), dbID1) == null);
        }
    }

    @Test
    public void testUpdateAttribute() throws Exception {
        SchemaClass sc = schema.getClassByName("Pathway");
        // Create Pathway instance
        GKInstance instance = neo4jAdaptor.fetchInstance(9612973l);
        instance.setDBID(null);
        instance.setDisplayName("TestName");
        instance.setSchemaClass(sc);
        // Create instance-type value for attribute "compartment": instance1
        SchemaClass sc1 = schema.getClassByName("Compartment");
        GKInstance instance1 = neo4jAdaptor.fetchInstance(7660l);
        instance1.setSchemaClass(sc1);
        instance1.setDBID(null);
        instance1.setDisplayName("TestCompartment");
        // Set that instance as value for attribute "compartment"
        instance.setAttributeValue("compartment", Collections.singletonList(instance1));
        try (Session session = driver.session(SessionConfig.forDatabase(neo4jAdaptor.getDBName()))) {
            Transaction tx = session.beginTransaction();
            // Store instance
            long dbID = neo4jAdaptor.storeInstance(instance, tx);
            long compartmentDBID = ((GKInstance) instance.getAttributeValue("compartment")).getDBID();
            // Update "displayName" and "compartment" attributes in instance (in memory and not in Neo4j yet)
            instance.setAttributeValue("displayName", "TestName 2");
            instance1.setAttributeValue("displayName", "TestCompartment 2");
            instance1.setDBID(null);
            neo4jAdaptor.updateInstanceAttribute(instance, "displayName", tx);
            neo4jAdaptor.updateInstanceAttribute(instance, "compartment", tx);
            tx.commit();
            // Re-fetch instance dbID from Neo4j and check that the values changed in memory have been updated in DB
            GKInstance fetchedInstance = neo4jAdaptor.fetchInstance(dbID);
            fetchedInstance.setSchemaClass(sc);
            GKInstance compartmentValueInstance = (GKInstance) fetchedInstance.getAttributeValuesList("compartment").get(0);
            // Test that the attribute changes made in memory were committed to the DB
            assumeTrue(compartmentValueInstance.getAttributeValue("displayName").equals("TestCompartment 2"));
            assumeTrue(fetchedInstance.getAttributeValue("displayName").equals("TestName 2"));
            // Clean up after test
            tx = session.beginTransaction();
            neo4jAdaptor.deleteInstance(instance, tx);
            neo4jAdaptor.deleteInstance(instance1, tx);
            GKInstance orphanInstance = neo4jAdaptor.fetchInstance(compartmentDBID);
            neo4jAdaptor.deleteInstance(orphanInstance, tx);
            tx.commit();
        }
    }

    @Test
    public void test_Deleted() throws Exception {
        SchemaClass sc = schema.getClassByName("_Deleted");
        GKInstance instance = neo4jAdaptor.fetchInstance(188476l);
        instance.setSchemaClass(sc);
        neo4jAdaptor.loadInstanceAttributeValues(instance);
        assumeTrue(((String) instance.getAttributeValuesList("curatorComment").iterator().next())
                .contains("Merged the events"));
        GKInstance deletedInstance =
                (GKInstance) instance.getAttributeValuesList("deletedInstance").iterator().next();
        assumeTrue(deletedInstance.getAttributeValue("className").equals(ReactomeJavaConstants.Reaction));
    }

    @Test
    public void test_DeletedInstance() throws Exception {
        SchemaClass sc = schema.getClassByName("_DeletedInstance");
        GKInstance instance = neo4jAdaptor.fetchInstance(9737808l);
        instance.setSchemaClass(sc);
        neo4jAdaptor.loadInstanceAttributeValues(instance);
        assumeTrue(((String) instance.getAttributeValuesList("name").iterator().next())
                .contains("Phosphorylation of proteins"));
        GKInstance stableIdentifierInst =
                (GKInstance) instance.getAttributeValuesList("deletedStableIdentifier").iterator().next();
        neo4jAdaptor.loadInstanceAttributeValues(stableIdentifierInst);
        assumeTrue(stableIdentifierInst.getAttributeValue("identifier").equals("REACT_270"));
    }

    @Test
    public void test_Release() throws Exception {
        boolean exceptionThrown = false;
        try {
            // _Release in gkCentral mysql db has no records - hence no class won't have been loaded
            neo4jAdaptor.fetchInstancesByClass(ReactomeJavaConstants._Release, null);
        } catch (InvalidClassException e) {
            exceptionThrown = true;
        }
        assumeTrue(exceptionThrown);
    }

    @Test
    public void test_UpdateTracker() throws Exception {
        boolean exceptionThrown = false;
        try {
            // _UpdateTracker in gkCentral mysql db has no records - hence no class won't have been loaded
            neo4jAdaptor.fetchInstancesByClass(ReactomeJavaConstants._UpdateTracker, null);
        } catch (InvalidClassException e) {
            exceptionThrown = true;
        }
        assumeTrue(exceptionThrown);
    }

    @Test
    public void testPathwayDiagram() throws Exception {
        SchemaClass sc = schema.getClassByName("PathwayDiagram");
        GKInstance instance = neo4jAdaptor.fetchInstance(5263598l);
        instance.setSchemaClass(sc);
        neo4jAdaptor.loadInstanceAttributeValues(instance, sc.getAttribute("storedATXML"));
        String xmlStr = (String) instance.getAttributeValuesList("storedATXML").iterator().next();
        assumeTrue(xmlStr != null && xmlStr.length() > 0);
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway renderableDiagram = reader.openDiagram(xmlStr);
        // Check the edges
        List<?> components = renderableDiagram.getComponents();
        assumeTrue(components != null && components.size() > 0);
        boolean foundDBID = false;
        for (Iterator<?> it1 = components.iterator(); it1.hasNext(); ) {
            Renderable r = (Renderable) it1.next();
            if (r.getReactomeId() != null && r.getReactomeId() == 7660l)
                foundDBID = true;
        }
        assumeTrue(foundDBID);
    }

    private boolean fitForService() {
        try (Session session = driver.session(SessionConfig.forDatabase("graph.db"))) {
            Result result = session.run(
                    "MATCH (n) RETURN COUNT(n) > 0");
            while (result.hasNext()) {
                Record record = result.next();
                Value val = record.get(0);
                return val.isTrue();
            }
        }
        return false;
    }
}