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
    public void testInverseSlots(){
        for (String attName : Arrays.asList("orthologousEvent", "reverseReaction")){
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
            // Update ReactomeJavaConstants._displayName and "compartment" attributes in instance (in memory and not in Neo4j yet)
            instance.setAttributeValue(ReactomeJavaConstants._displayName, "TestName 2");
            instance1.setAttributeValue(ReactomeJavaConstants._displayName, "TestCompartment 2");
            instance1.setDBID(null);
            neo4jAdaptor.updateInstanceAttribute(instance, ReactomeJavaConstants._displayName, tx);
            neo4jAdaptor.updateInstanceAttribute(instance, "compartment", tx);
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
                (GKInstance) neo4jAdaptor.fetchInstancesByClass("Pathway", Collections.singletonList(9612973L)).iterator().next();
        Set<Instance> identicals = neo4jAdaptor.fetchIdenticalInstances(instance);
        // TODO: This test fails because MySQL's property_name=value_defines_instance is not stored in graph-core-curator - TBC
        assumeTrue(identicals != null && identicals.size() > 0);
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
        Collection<Instance> instances = neo4jAdaptor.fetchInstanceByAttribute(attribute, "", 9612973L);
        assumeTrue(instances.iterator().next().getDBID().equals(9664770L));
        // Test AttributeQueryRequest for a primitive attribute with a single value
        SchemaAttribute attribute1 = sc.getAttribute("_displayName");
        instances = neo4jAdaptor.fetchInstanceByAttribute(attribute1, "=", "Deregulating Cellular Energetics");
        assumeTrue(instances.iterator().next().getDBID().equals(9664770L));
        // Test AttributeQueryRequest for an Instance attribute with operator = IS NULL and = IS NOT NULL
        Neo4JAdaptor.AttributeQueryRequest aqr =
                neo4jAdaptor.createAttributeQueryRequest(attribute, "IS NOT NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        aqr = neo4jAdaptor.createAttributeQueryRequest(attribute, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test AttributeQueryRequest for a primitive attribute with operator = IS NULL and = IS NOT NULL
        aqr =
                neo4jAdaptor.createAttributeQueryRequest(attribute1, "IS NOT NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        aqr = neo4jAdaptor.createAttributeQueryRequest(attribute1, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test AttributeQueryRequest for an Instance attribute with multiple values
        Collection<Instance> insts1 = neo4jAdaptor.fetchInstanceByAttribute(attribute, "", 5693579L);
        Collection<Instance> insts2 = neo4jAdaptor.fetchInstanceByAttribute(attribute, "", 5693616L);
        aqr = neo4jAdaptor.createAttributeQueryRequest(
                attribute, "=", Arrays.asList(insts1.iterator().next(), insts2.iterator().next()));
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() == 2);
        // Test AttributeQueryRequest for a primitive attribute with multiple values
        aqr = neo4jAdaptor.createAttributeQueryRequest(
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
        Neo4JAdaptor.AttributeQueryRequest aqr = neo4jAdaptor.createAttributeQueryRequest(
                attribute, "=", Arrays.asList(inst1, inst2));
        Neo4JAdaptor.AttributeQueryRequest aqr1 = neo4jAdaptor.createAttributeQueryRequest(
                attribute1, "=", "Homologous DNA Pairing and Strand Exchange");
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr, aqr1));
        assumeTrue(instances.size() == 1);
        // Test AttributeQueryRequest for a List of aqr's - a mixture of IS NULL and IS NOT NULL clauses
        aqr = neo4jAdaptor.createAttributeQueryRequest(attribute3, "=", 9604294L);
        aqr1 = neo4jAdaptor.createAttributeQueryRequest(attribute1, "IS NOT NULL", null);
        Neo4JAdaptor.AttributeQueryRequest aqr2 = neo4jAdaptor.createAttributeQueryRequest(attribute2, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr, aqr1, aqr2));
        assumeTrue(instances.size() == 1);
        aqr = neo4jAdaptor.createAttributeQueryRequest(attribute3, "IN", Arrays.asList(9604294L,9604295L));
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr, aqr1, aqr2));
        assumeTrue(instances.size() == 1);
    }

    @Test
    public void testFetchInstanceByQueryRequest2() throws Exception {
        SchemaClass refererSc = schema.getClassByName("Reaction");
        SchemaAttribute attribute = refererSc.getAttribute("species");
        SchemaClass sc = schema.getClassByName(ReactomeJavaConstants.Species);
        // Test ReverseAttributeQueryRequest (necessarily for an Instance attribute) with operator = IS NOT NULL
        Neo4JAdaptor.ReverseAttributeQueryRequest aqr =
                neo4jAdaptor.createReverseAttributeQueryRequest(sc, attribute, "IS NOT NULL", null);
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test ReverseAttributeQueryRequest with operator = IS NULL
        // Note that in "IS NULL" ReverseAttributeQueryRequest's sc has to be ignored, because a query such as
        // MATCH (n) WHERE NOT (n)-[:species]->(s:<Class>) RETURN n.DB_ID, n._displayName, n.schemaClass
        // is incorrect - it has to be:
        // MATCH (n) WHERE NOT (n)-[:species]->() RETURN n.DB_ID, n._displayName, n.schemaClass
        aqr = neo4jAdaptor.createReverseAttributeQueryRequest(sc, attribute, "IS NULL", null);
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test ReverseAttributeQueryRequest for a combination of "IS NOT NULL" and "="
        GKInstance species = (GKInstance) neo4jAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Species,ReactomeJavaConstants.name,"=","Homo sapiens").iterator().next();
        Neo4JAdaptor.AttributeQueryRequest aqr1 =
                neo4jAdaptor.createAttributeQueryRequest("ReactionLikeEvent",ReactomeJavaConstants.species,"=",species);
        Neo4JAdaptor.ReverseAttributeQueryRequest aqr2 =
                neo4jAdaptor.createReverseAttributeQueryRequest("ReactionType",ReactomeJavaConstants.reactionType,"IS NOT NULL",null);
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr1, aqr2));
        assumeTrue(instances.size() > 0);
        // Test ReverseAttributeQueryRequest for a combination of "IS NOT NULL" and "IN"
        species = (GKInstance) neo4jAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Species,ReactomeJavaConstants.name,"=",Arrays.asList("Homo sapiens", "H. sapiens")).iterator().next();
        aqr1 = neo4jAdaptor.createAttributeQueryRequest("ReactionLikeEvent",ReactomeJavaConstants.species,"=",species);
        aqr2 = neo4jAdaptor.createReverseAttributeQueryRequest("ReactionType",ReactomeJavaConstants.reactionType,"IS NOT NULL",null);
        instances = neo4jAdaptor.fetchInstance(Arrays.asList(aqr1, aqr2));
        assumeTrue(instances.size() > 0);
    }

    @Test
    public void testFetchInstanceByQueryRequest3() throws Exception {
        SchemaClass refererSc = schema.getClassByName("Pathway");
        SchemaAttribute attribute = refererSc.getAttribute("hasEvent");
        // Test AttributeQueryRequest with a "IS NOT NULL" ReverseAttributeQueryRequest sub-query
        // Below: Find all Pathway instances in which the value of hasEvent attribute is one or more of instances (of some class)
        // have a hasEvent attribute
        Neo4JAdaptor.ReverseAttributeQueryRequest subQuery =
                neo4jAdaptor.createReverseAttributeQueryRequest("Pathway","hasEvent","IS NOT NULL",null);
        Neo4JAdaptor.AttributeQueryRequest aqr =
                neo4jAdaptor.createAttributeQueryRequest("Pathway","hasEvent","=",subQuery);
        Collection<Instance> instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
        // Test AttributeQueryRequest with a list of "IS NOT NULL" AttributeQueryRequest/ReverseAttributeQueryRequest sub-queries
        // Below: Find all Pathway instances in which hasEvent attribute is one or more of instances (of some class)
        // have _both_ an hasEvent attribute and a precedingEvent attribute
        Neo4JAdaptor.ReverseAttributeQueryRequest subQuery1 =
                neo4jAdaptor.createReverseAttributeQueryRequest("StableIdentifier","stableIdentifier","IS NOT NULL",null);
        aqr = neo4jAdaptor.createAttributeQueryRequest("Pathway","hasEvent","=",Arrays.asList(subQuery, subQuery1));
        instances = neo4jAdaptor.fetchInstance(aqr);
        assumeTrue(instances.size() > 0);
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