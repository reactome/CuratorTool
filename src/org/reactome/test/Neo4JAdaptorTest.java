package org.reactome.test;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.AttributeQueryRequest;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.persistence.ReverseAttributeQueryRequest;
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
        SchemaClass sc1 = schema.getClassByName("BlackBoxEvent");
        // Test that only immediate parents are retrieved
        assumeTrue(sc1.getSuperClasses().size() == 1);
        SchemaClass sc = schema.getClassByName("Pathway");
        assumeTrue(sc.getName().equals("Pathway"));
        Collection<SchemaAttribute> attributes = sc.getAttributes();
        assumeTrue(attributes.size() > 0);
        boolean foundDbId = false;
        boolean foundSpecies = false;
        for (SchemaAttribute attribute : attributes) {
            if (attribute.getName().equals(Schema.DB_ID_NAME)) {
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
    public void testInverseSlots() {
        for (String attName : Arrays.asList("orthologousEvent", "reverseReaction")) {
            SchemaClass sc = schema.getClassByName("Pathway");
            Collection<SchemaAttribute> attributes = sc.getAttributes();
            assumeTrue(attributes.size() > 0);
            for (SchemaAttribute attribute : attributes) {
                if (attribute.getName().equals(attName)) {
                    assumeTrue(attribute.getInverseSchemaAttribute().getName().equals(attName));
                    break;
                }
            }
        }
    }

    @Test
    public void testSchemaTimestamp() throws Exception {
        assumeTrue(neo4jAdaptor.getSchemaTimestamp() != null);
    }

    @Test
    public void testFetchStableIdentifiersWithDuplicateDBIds() {
        List<Map<String, Object>> sIds = neo4jAdaptor.fetchStableIdentifiersWithDuplicateDBIds();
        assumeTrue(sIds.size() > 0);
        assumeTrue(sIds.get(0).get("identifier") != null);
        assumeTrue(sIds.get(0).get("oldIdentifier") != null);
        assumeTrue(sIds.get(0).get(Schema.DB_ID_NAME) != null);
    }

    @Test
    public void testFetchEWASModifications() {
        List<List<Long>> mods = neo4jAdaptor.fetchEWASModifications();
        assumeTrue(mods.size() > 0);
        assumeTrue(mods.get(0).size() == 2);
    }

    @Test
    public void testLoadInstanceAttributeValues() throws Exception {
        Collection<Instance> instances =
                neo4jAdaptor.fetchInstancesByClass("Pathway", Collections.singletonList(9612973L));
        GKInstance gkIns = (GKInstance) instances.iterator().next();
        neo4jAdaptor.loadInstanceAttributeValues(gkIns);
        assumeTrue(gkIns.getAttributeValuesList(ReactomeJavaConstants._displayName).size() > 0);
        assumeTrue(gkIns.getAttributeValuesList("hasEvent").size() > 1);
        boolean foundEvent = false;
        for (GKInstance instance : (List<GKInstance>) gkIns.getAttributeValuesList("hasEvent")) {
            if (instance.getAttributeValue(ReactomeJavaConstants._displayName).equals("Chaperone Mediated Autophagy")) {
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
        assumeTrue(gkIns.getAttributeValuesList("_chainChangeLog").size() > 0);
    }

    @Test
    public void testDoReleaseInEvent() throws Exception {
        Collection<Instance> instances =
                neo4jAdaptor.fetchInstancesByClass(
                        "Event",
                        Collections.singletonList(15869l));
        GKInstance gkIns = (GKInstance) instances.iterator().next();
        neo4jAdaptor.loadInstanceAttributeValues(gkIns);
        assumeTrue(gkIns.getAttributeValuesList(ReactomeJavaConstants._doRelease).size() > 0);
        assumeTrue((Boolean) gkIns.getAttributeValuesList(ReactomeJavaConstants._doRelease).get(0));
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
        assumeTrue(instance.getSchemClass().getName().equals("Pathway"));
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
            assumeTrue(instance.getAttributeValue(ReactomeJavaConstants._timestamp) != null);
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
            // Update ReactomeJavaConstants._displayName and "compartment" attributes in instance (in memory and not in Neo4j yet)
            instance.setAttributeValue(ReactomeJavaConstants._displayName, "TestName 2");
            instance1.setAttributeValue(ReactomeJavaConstants._displayName, "TestCompartment 2");
            instance1.setDBID(null);
            neo4jAdaptor.updateInstanceAttribute(instance, ReactomeJavaConstants._displayName, tx);
            String timestamp = instance.getAttributeValue(ReactomeJavaConstants._timestamp).toString();
            // We need to sleep for 1s to give _timestamp (it has 1s resolution) a chance to change to satisfy the test below
            Thread.sleep(1000);
            neo4jAdaptor.updateInstanceAttribute(instance, "compartment", tx);
            assumeTrue(!instance.getAttributeValue(ReactomeJavaConstants._timestamp).toString().equals(timestamp));
            tx.commit();
            // Re-fetch instance dbID from Neo4j and check that the values changed in memory have been updated in DB
            GKInstance fetchedInstance = neo4jAdaptor.fetchInstance(dbID);
            fetchedInstance.setSchemaClass(sc);
            GKInstance compartmentValueInstance = (GKInstance) fetchedInstance.getAttributeValuesList("compartment").get(0);
            // Test that the attribute changes made in memory were committed to the DB
            assumeTrue(compartmentValueInstance.getAttributeValue(ReactomeJavaConstants._displayName).equals("TestCompartment 2"));
            assumeTrue(fetchedInstance.getAttributeValue(ReactomeJavaConstants._displayName).equals("TestName 2"));
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
    public void testStoichiometry() throws Exception {
        SchemaClass sc = schema.getClassByName("Pathway");
        // Create Pathway instance
        GKInstance instance = neo4jAdaptor.fetchInstance(9612973L);
        instance.setDBID(null);
        instance.setDisplayName("TestName");
        instance.setSchemaClass(sc);
        List<GKInstance> instances = new ArrayList();
        for (Long dbId : Arrays.asList(9664770L, 9615710L, 9664770L)) {
            instances.add(neo4jAdaptor.fetchInstance("Event", dbId));
        }
        // Set that instance as value for attribute "compartment"
        instance.setAttributeValue("hasEvent", instances);
        try (Session session = driver.session(SessionConfig.forDatabase(neo4jAdaptor.getDBName()))) {
            Transaction tx = session.beginTransaction();
            // Store instance
            long dbID = neo4jAdaptor.storeInstance(instance, tx);
            tx.commit();
            neo4jAdaptor.refreshCaches();
            GKInstance inst = neo4jAdaptor.fetchInstance("Pathway", dbID);
            neo4jAdaptor.loadInstanceAttributeValues(inst, true);
            assumeTrue(inst.getAttributeValuesList("hasEvent").size() == 3);
            // Clean up after test
            tx = session.beginTransaction();
            neo4jAdaptor.deleteInstance(inst, tx);
            tx.commit();
        }
    }

    @Test
    public void testDeleteInstanceAttribute() throws Exception {
        neo4jAdaptor.setUseCache(false);
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
            tx.commit();
            // Re-fetch instance dbID from DB and confirm that attribute: "compartment" is still present
            GKInstance fetchedInstance = neo4jAdaptor.fetchInstance(dbID);
            fetchedInstance.setSchemaClass(sc);
            neo4jAdaptor.loadInstanceAttributeValues(fetchedInstance);
            GKInstance compartmentValueInstance = (GKInstance) fetchedInstance.getAttributeValuesList("compartment").get(0);
            assumeTrue(compartmentValueInstance != null);
            // Remove instance attribute (instance1) from DB and check that value "compartment" is now absent
            tx = session.beginTransaction();
            neo4jAdaptor.deleteInstance(instance1, tx);
            tx.commit();
            neo4jAdaptor.refreshCaches(); // Clear cache
            fetchedInstance = neo4jAdaptor.fetchInstance(dbID);
            fetchedInstance.setSchemaClass(sc);
            fetchedInstance.setIsInflated(false);
            neo4jAdaptor.loadInstanceAttributeValues(fetchedInstance);
            assumeTrue(fetchedInstance.getAttributeValuesList("compartment").size() == 0);
            // Tidy-up
            tx = session.beginTransaction();
            neo4jAdaptor.deleteInstance(instance, tx);
            tx.commit();
        }
    }

    @Test
    public void testUpdateInstance() throws Exception {
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
            tx.commit();
            tx = session.beginTransaction();
            long compartmentDBID = ((GKInstance) instance.getAttributeValue("compartment")).getDBID();
            // Update displayName and "compartment" attributes in instance (in memory and not in Neo4j yet)
            instance1.setAttributeValue(ReactomeJavaConstants._displayName, "TestCompartment 2");
            neo4jAdaptor.updateInstance(instance1, tx);
            tx.commit();
            // Re-fetch instance dbID from Neo4j and check that the values changed in memory have been updated in DB
            GKInstance fetchedInstance = neo4jAdaptor.fetchInstance(dbID);
            fetchedInstance.setSchemaClass(sc);
            neo4jAdaptor.loadInstanceAttributeValues(fetchedInstance);
            GKInstance compartmentValueInstance = (GKInstance) fetchedInstance.getAttributeValuesList("compartment").get(0);
            // Test that the attribute changes made in memory were committed to the DB
            assumeTrue(compartmentValueInstance.getAttributeValue(ReactomeJavaConstants._displayName).equals("TestCompartment 2"));
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
        assumeTrue(Long.parseLong(deletedInstance.getAttributeValue("deletedInstanceDB_ID").toString()) == 69696l);
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
        assumeTrue(instance.getAttributeValue("className").equals("Reaction"));
    }

    @Test
    public void test_Release() throws Exception {
        // _Release in gkCentral mysql db has no records - hence no class won't have been loaded
        Collection coll = neo4jAdaptor.fetchInstancesByClass(ReactomeJavaConstants._Release, null);
        assumeTrue(coll.size() == 0);
    }

    @Test
    public void test_UpdateTracker() throws Exception {
        // _UpdateTracker in gkCentral mysql db has no records - hence no class won't have been loaded
        Collection coll = neo4jAdaptor.fetchInstancesByClass(ReactomeJavaConstants._UpdateTracker, null);
        assumeTrue(coll.size() == 0);
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

    @Test
    public void testExisting() throws Exception {
        List<Long> dbIds = Arrays.asList(new Long[]{5263598l, 0l});
        Set<Long> existingDBIds = neo4jAdaptor.existing(dbIds, false, false);
        assumeTrue(existingDBIds.size() == 1);
        assumeTrue(existingDBIds.iterator().next() == 5263598l);
        Set<Long> missingDBIds = neo4jAdaptor.existing(dbIds, false, true);
        assumeTrue(missingDBIds.size() > 600000);
    }

    @Test
    public void testFetchDBIDsByAttributeValueCount() throws Exception {
        // Simple Attribute
        SchemaClass sc = schema.getClassByName("Reaction");
        GKSchemaAttribute att = (GKSchemaAttribute) sc.getAttribute("name");
        Collection<Long> dbIds = neo4jAdaptor.fetchDBIDsByAttributeValueCount(
                att, Arrays.asList("Homoserine dehydrogenase", "blah"), 1);
        assumeTrue(dbIds.size() > 0);
        // Instance-type attribute
        sc = schema.getClassByName("FrontPage");
        att = (GKSchemaAttribute) sc.getAttribute("frontPageItem");
        dbIds = neo4jAdaptor.fetchDBIDsByAttributeValueCount(
                att, Arrays.asList(170877), 1);
        assumeTrue(dbIds.size() > 0);
    }

    @Test
    public void testFetchIdenticalInstances() throws Exception {
        GKInstance instance =
                (GKInstance) neo4jAdaptor.fetchInstancesByClass("Complex", Collections.singletonList(2247475L)).iterator().next();
               //  (GKInstance) neo4jAdaptor.fetchInstancesByClass("Pathway", Collections.singletonList(9612973L)).iterator().next();
        Set<Instance> identicals = neo4jAdaptor.fetchIdenticalInstances(instance);
        assumeTrue(identicals == null);
    }

    @Test
    public void testGetMaxDBID() throws Exception {
        assumeTrue(neo4jAdaptor.fetchMaxDbId() > 0);
    }

    @Test
    public void testLoadInstanceReverseAttributeValues() throws Exception {
        Collection<Instance> instances =
                neo4jAdaptor.fetchInstancesByClass("Pathway", Collections.singletonList(9612973L));
        SchemaClass sc = schema.getClassByName("Pathway");
        SchemaAttribute attribute = sc.getAttribute("hasEvent");
        neo4jAdaptor.loadInstanceReverseAttributeValues(instances, attribute);
        boolean allReferrersPresent = true;
        for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
            GKInstance ins = (GKInstance) ii.next();
            List<GKInstance> attInstances = (List<GKInstance>) ins.getAttributeValuesList(attribute.getName());
            for (GKInstance attInstance : attInstances) {
                if (attInstance.getReferers(attribute).size() == 0) {
                    allReferrersPresent = false;
                    break;
                }
            }
            assumeTrue(allReferrersPresent);
        }
    }

    @Test
    public void testFetchInstanceByQueryRequest() throws Exception {
        // Test AttributeQueryRequest for an Instance attribute with a single value
        SchemaClass sc = schema.getClassByName("Pathway");
        SchemaAttribute attribute = sc.getAttribute("hasEvent");
        Instance instance = neo4jAdaptor.fetchInstance(Collections.singletonList(9612973L).iterator().next());
        Collection<Instance> instances = neo4jAdaptor.fetchInstanceByAttribute(attribute, "", instance);
        assumeTrue(instances.iterator().next().getDBID().equals(9664770L));
        // Test AttributeQueryRequest for a primitive attribute with a single value
        SchemaAttribute attribute1 = sc.getAttribute("_displayName");
        instances = neo4jAdaptor.fetchInstanceByAttribute(attribute1, "=", "Deregulating Cellular Energetics");
        assumeTrue(instances.iterator().next().getDBID().equals(9664770L));
        // Test AttributeQueryRequest for an Instance attribute with operator = IS NULL and = IS NOT NULL
        AttributeQueryRequest aqr =
                new AttributeQueryRequest(attribute, "IS NOT NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        aqr = new AttributeQueryRequest(attribute, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test AttributeQueryRequest for a primitive attribute with operator = IS NULL and = IS NOT NULL
        aqr =
                new AttributeQueryRequest(attribute1, "IS NOT NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        aqr = new AttributeQueryRequest(attribute1, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test AttributeQueryRequest for an Instance attribute with multiple values
        Instance inst1 = neo4jAdaptor.fetchInstance(Collections.singletonList(5693579L).iterator().next());
        Instance inst2 = neo4jAdaptor.fetchInstance(Collections.singletonList(5693616L).iterator().next());
        Collection<Instance> insts1 = neo4jAdaptor.fetchInstanceByAttribute(attribute, "", inst1);
        Collection<Instance> insts2 = neo4jAdaptor.fetchInstanceByAttribute(attribute, "", inst2);
        aqr = new AttributeQueryRequest(
                attribute, "=", Arrays.asList(insts1.iterator().next(), insts2.iterator().next()));
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() == 2);
        // Test AttributeQueryRequest for a primitive attribute with multiple values
        aqr = new AttributeQueryRequest(
                attribute1, "=",
                Arrays.asList("Deregulating Cellular Energetics", "Homologous DNA Pairing and Strand Exchange"));
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() == 2);
    }

    @Test
    public void testFetchInstanceByQueryRequest1() throws Exception {
        SchemaClass sc = schema.getClassByName("Pathway");
        SchemaAttribute attribute = sc.getAttribute("hasEvent");
        SchemaAttribute attribute1 = sc.getAttribute("_displayName");
        SchemaAttribute attribute2 = sc.getAttribute("crossReference");
        SchemaAttribute attribute3 = sc.getAttribute("DB_ID");
        // Test AttributeQueryRequest for a List of aqr's - a mixture of Instance and primitive attributes
        Instance inst1 = neo4jAdaptor.fetchInstance(9604294L);
        Instance inst2 = neo4jAdaptor.fetchInstance(5685319L);
        AttributeQueryRequest aqr = new AttributeQueryRequest(
                attribute, "=", Arrays.asList(inst1, inst2));
        AttributeQueryRequest aqr1 = new AttributeQueryRequest(
                attribute1, "=", "Homologous DNA Pairing and Strand Exchange");
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr, aqr1));
        assumeTrue(instances.size() == 1);
        // Test AttributeQueryRequest for a List of aqr's - a mixture of IS NULL and IS NOT NULL clauses
        aqr = new AttributeQueryRequest(attribute3, "=", 9604294L);
        aqr1 = new AttributeQueryRequest(attribute1, "IS NOT NULL", null);
        AttributeQueryRequest aqr2 = new AttributeQueryRequest(attribute2, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr, aqr1, aqr2));
        assumeTrue(instances.size() == 1);
        aqr = new AttributeQueryRequest(attribute3, "IN", Arrays.asList(9604294L, 9604295L));
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr, aqr1, aqr2));
        assumeTrue(instances.size() == 1);
    }

    @Test
    public void testFetchInstanceByQueryRequest2() throws Exception {
        SchemaClass refererSc = schema.getClassByName("Reaction");
        SchemaAttribute attribute = refererSc.getAttribute("species");
        SchemaClass sc = schema.getClassByName(ReactomeJavaConstants.Species);
        // Test ReverseAttributeQueryRequest (necessarily for an Instance attribute) with operator = IS NOT NULL
        // Find non-orphan species = at least one node contains them in their species attribute
        ReverseAttributeQueryRequest aqr =
                new ReverseAttributeQueryRequest(sc, attribute, "IS NOT NULL", null);
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Find orphan species = no node contains them in their species attribute
        aqr = new ReverseAttributeQueryRequest(sc, attribute, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test ReverseAttributeQueryRequest for a combination of "IS NOT NULL" and "="
        GKInstance species = (GKInstance) neo4jAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Species, ReactomeJavaConstants.name, "=", "Homo sapiens").iterator().next();
        AttributeQueryRequest aqr1 =
                new AttributeQueryRequest(schema, "Pathway", ReactomeJavaConstants.species, "=", species);
        ReverseAttributeQueryRequest aqr2 =
                new ReverseAttributeQueryRequest(schema, "Pathway", ReactomeJavaConstants.hasEvent, "IS NOT NULL", null);
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr1, aqr2));
        assumeTrue(instances.size() > 0);
        // Test ReverseAttributeQueryRequest for a combination of "IS NOT NULL" and "IN"
        species = (GKInstance) neo4jAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Species, ReactomeJavaConstants.name, "=", Arrays.asList("Homo sapiens", "H. sapiens")).iterator().next();
        aqr1 = new AttributeQueryRequest(schema, "Pathway", ReactomeJavaConstants.species, "IN", species);
        aqr2 = new ReverseAttributeQueryRequest(schema, "Pathway", ReactomeJavaConstants.hasEvent, "IS NOT NULL", null);
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr1, aqr2));
        assumeTrue(instances.size() > 0);
    }

    @Test
    public void testFetchInstanceByQueryRequest3() throws Exception {
        SchemaClass refererSc = schema.getClassByName("Pathway");
        SchemaAttribute attribute = refererSc.getAttribute("hasEvent");
        // Test AttributeQueryRequest with a "IS NOT NULL" ReverseAttributeQueryRequest sub-query
        // Below: Find all Pathway instances in which the value of hasEvent attribute is one or more instances (of some class)
        // that has the hasEvent attribute
        ReverseAttributeQueryRequest subQuery =
                new ReverseAttributeQueryRequest(schema, "Pathway", "hasEvent", "IS NOT NULL", null);
        AttributeQueryRequest aqr =
                new AttributeQueryRequest(schema, "Pathway", "hasEvent", "=", subQuery);
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
    }


    @Test
    public void testFetchInstanceByQueryRequest4() throws Exception {

        for (String operator : Arrays.asList("LIKE","NOT LIKE","REGEXP")) {
            String value = "NFKbeta";
            AttributeQueryRequest aqr =
                    new AttributeQueryRequest(schema, "Pathway", "_displayName", "NOT LIKE", value);
            Collection<Instance> instances = neo4jAdaptor.fetchInstance(aqr);
            assumeTrue(instances.size() > 0);
        }

        AttributeQueryRequest aqr = new AttributeQueryRequest(schema, "BlackBoxEvent", "_displayName", "LIKE",
                "MMADHC targets transport of cytosolic cob\\(II\\)alamin to mitochondria");
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(aqr);

        aqr = new AttributeQueryRequest(schema, "DatabaseObject", "created", "LIKE",
                "D'Eustachio");
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);

        aqr = new AttributeQueryRequest(schema, "DatabaseObject", "stableIdentifier", "=",
                "R-HSA-450658.1");
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
    }

    @Test
    public void testLoadAllAttributeValues() throws Exception {
        SchemaClass cls = schema.getClassByName("Event");
        SchemaAttribute attribute = cls.getAttribute(ReactomeJavaConstants._doRelease);
        neo4jAdaptor.loadAllAttributeValues(cls.getName(), attribute);

        cls = schema.getClassByName(ReactomeJavaConstants.Pathway);
        attribute = cls.getAttribute(ReactomeJavaConstants.hasEvent);
        neo4jAdaptor.loadAllAttributeValues(cls.getName(), attribute);
    }
    /*
    @Test
    // Note this test takes a long time to run
    public void testInstanceReferenceCheckerCheck() throws Exception {
        InstanceReferenceChecker checker = new InstanceReferenceChecker();
        checker.setDBA(neo4jAdaptor);
        assumeTrue(checker.check().length() > 0);
    }
    */

    @Test
    public void testMintNewDBID() throws Exception {
        Long dbID = neo4jAdaptor.mintNewDBID();
        System.out.println(dbID);
        assumeTrue(dbID != null);
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