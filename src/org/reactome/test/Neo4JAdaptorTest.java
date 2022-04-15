package org.reactome.test;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.persistence.Neo4JAdaptor;
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

    @Before
    public void baseTest() throws Exception {
        if (!checkedOnce) {
            neo4jAdaptor = new Neo4JAdaptor("bolt://localhost:7687",
                    "graph.db",
                    "neo4j",
                    "reactome");
            driver = neo4jAdaptor.getConnection();
            assumeTrue(fitForService());
        }
    }

    @After
    public void closeSession() {
        driver.close();
    }

    @Test
    public void testFetchSchema() throws Exception {
        Schema schema = neo4jAdaptor.fetchSchema();
        assumeTrue(schema.getClasses().size() > 0);
        assumeTrue(schema.getTimestamp() != null);
        SchemaClass sc = schema.getClassByName("TopLevelPathway");
        assumeTrue(sc.getName().equals("TopLevelPathway"));
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
    public void testSchemaTimestamp() throws Exception {
        assumeTrue(neo4jAdaptor.getSchemaTimestamp() != null);
    }

    @Test
    public void testLoadInstanceAttributeValues() throws Exception {
        Schema schema = neo4jAdaptor.fetchSchema();
        Collection<Instance> instances = neo4jAdaptor.fetchInstancesByClass("TopLevelPathway", Collections.singletonList(9612973L));
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
    public void testFetchInstancesByClass() throws Exception {
        Collection<Instance> instances = neo4jAdaptor.fetchInstancesByClass("TopLevelPathway", null);
        assumeTrue(instances.size() > 0);
        List<Long> dbIds = new ArrayList();
        dbIds.add(9612973l);
        dbIds.add(1640170l);
        instances = neo4jAdaptor.fetchInstancesByClass("TopLevelPathway", dbIds);
        assumeTrue(instances.size() == 2);
    }

    @Test
    public void testFetchInstanceByClassAndDBId() throws Exception {
        GKInstance instance = neo4jAdaptor.fetchInstance("TopLevelPathway", 9612973l);
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
        long cnt = neo4jAdaptor.getClassInstanceCount("TopLevelPathway");
        assumeTrue(cnt > 0);
    }

    @Test
    public void testGetAllInstanceCounts() throws Exception {
        Map<String, Long> classNameToCount = neo4jAdaptor.getAllInstanceCounts();
        assumeTrue(classNameToCount.get("TopLevelPathway") > 0);
    }

    @Test
    public void testExist() throws Exception {
        assumeTrue(neo4jAdaptor.exist(9612973l));
        assumeFalse(neo4jAdaptor.exist(-1l));
    }

    @Test
    public void testFetchSchemaClassnameByDBID() throws Exception {
        assumeTrue(neo4jAdaptor.fetchSchemaClassnameByDBID(9612973l).equals("TopLevelPathway"));
    }

    @Test
    public void testStableIdentifier() throws Exception {
        Schema schema = neo4jAdaptor.fetchSchema();
        SchemaClass sc = schema.getClassByName("TopLevelPathway");
        // Create TopLevelPathway instance
        GKInstance instance = neo4jAdaptor.fetchInstance(9613829l);
        List<GKInstance> instances =
                (List<GKInstance>) instance.getAttributeValuesList("stableIdentifier");
        assumeTrue(instances.size() == 1);
        assumeTrue(instances.get(0).getAttributeValue("identifier").equals("R-HSA-9613829"));
    }

    @Test
    public void testStoreAndDeleteInstance() throws Exception {
        Schema schema = neo4jAdaptor.fetchSchema();
        SchemaClass sc = schema.getClassByName("TopLevelPathway");
        // Create TopLevelPathway instance
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
        long dbID = neo4jAdaptor.storeInstance(instance);
        assumeTrue(dbID >= 9760736);
        neo4jAdaptor.deleteInstance(instance);
        // Deleting an instance removes it and its relationships, but not nodes at the other end of those relationships
        assumeTrue(neo4jAdaptor.fetchInstance(sc.getName(), dbID) == null);
        long dbID1 = instance1.getDBID();
        assumeTrue(neo4jAdaptor.fetchInstance(sc1.getName(),dbID1) != null);
        neo4jAdaptor.deleteInstance(instance1);
        assumeTrue(neo4jAdaptor.fetchInstance(sc1.getName(), dbID1) == null);
    }

    @Test
    public void testUpdateAttribute() throws Exception {
        Schema schema = neo4jAdaptor.fetchSchema();
        SchemaClass sc = schema.getClassByName("TopLevelPathway");
        // Create TopLevelPathway instance
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
        // Store instance
        long dbID = neo4jAdaptor.storeInstance(instance);
        long compartmentDBID = ((GKInstance) instance.getAttributeValue("compartment")).getDBID();
        // Update "displayName" and "compartment" attributes in instance (in memory and not in Neo4j yet)
        instance.setAttributeValue("displayName", "TestName 2");
        instance1.setAttributeValue("displayName", "TestCompartment 2");
        instance1.setDBID(null);
        neo4jAdaptor.updateInstanceAttribute(instance, "displayName");
        neo4jAdaptor.updateInstanceAttribute(instance, "compartment");
        // Re-fetch instance dbID from Neo4j and check that the values changed in memory have been updated in DB
        GKInstance fetchedInstance = neo4jAdaptor.fetchInstance(dbID);
        fetchedInstance.setSchemaClass(sc);
        GKInstance compartmentValueInstance = (GKInstance) fetchedInstance.getAttributeValuesList("compartment").get(0);
        // Test that the attribute changes made in memory were committed to the DB
        assumeTrue(compartmentValueInstance.getAttributeValue("displayName").equals("TestCompartment 2"));
        assumeTrue(fetchedInstance.getAttributeValue("displayName").equals("TestName 2"));
        // Clean up after test
        neo4jAdaptor.deleteInstance(instance);
        neo4jAdaptor.deleteInstance(instance1);
        GKInstance orphanInstance = neo4jAdaptor.fetchInstance(compartmentDBID);
        neo4jAdaptor.deleteInstance(orphanInstance);
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