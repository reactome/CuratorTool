package org.gk.persistence;

import org.gk.model.*;
import org.gk.schema.*;
import org.neo4j.driver.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.internal.value.NullValue;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.kernel.DeadlockDetectedException;


import java.util.*;
import java.util.stream.Collectors;

public class Neo4JAdaptor implements PersistenceAdaptor {
    private String host;
    private String database;
    private int port = 7687;
    protected Driver driver;
    private Schema schema;
    private InstanceCache instanceCache = new InstanceCache();
    private boolean useCache = true;
    private Map classMap;
    // Transaction-related constants
    // The maximum amount of retries before transaction is deemed failed
    int RETRIES = 5;
    // Pause between each attempt to allow the other transaction to finish before trying again.
    int BACKOFF = 3000;

    /**
     * This default constructor is used for subclassing.
     */
    protected Neo4JAdaptor() {
    }

    /**
     * Creates a new instance of MySQLAdaptor
     *
     * @param host     Database host
     * @param database Database name
     * @param username User name to connect to the database
     * @param password Password for the specified user name to connect to the database
     * @param port     Database port
     */
    public Neo4JAdaptor(String host, String database, String username, String password, int port) {
        driver = GraphDatabase.driver("bolt://" + host + ":" + port, AuthTokens.basic(username, password));
        this.database = database;
        try {
            fetchSchema();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Driver getConnection() {
        return driver;
    }

    public String getDBName() {
        return database;
    }

    public String getDBHost() {
        return this.host;
    }

    public Schema fetchSchema() throws Exception {
        Collection<String> classNames = new ArrayList<String>();
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(
                    "MATCH (n:DatabaseObject) UNWIND labels(n) as label WITH DISTINCT label RETURN label\n");
            while (result.hasNext()) {
                Record record = result.next();
                classNames.add(record.get("label").asString().replaceAll("\"", ""));
            }
            Neo4JSchemaParser neo4jSP = new Neo4JSchemaParser();
            schema = neo4jSP.parseNeo4JResults(classNames);
            // Retrieve from Neo4J the timestamp for the current data model and set it in schema
            ((GKSchema) schema).setTimestamp(getSchemaTimestamp());
            return schema;
        }
    }

    public Schema getSchema() {
        return schema;
    }

    /**
     * Reload everything from the database. The saved Schema will be not re-loaded.
     * The InstanceCache will be cleared.
     */
    public void refresh() {
        instanceCache.clear();
    }

    public void cleanUp() throws Exception {
        schema = null;
        instanceCache.clear();
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    /**
     * NOTE: This method expects instances to be found in InstanceCache.
     *
     * @param instances  Collection of GKInstance objects for which to load attributes
     * @param attributes Collection of GKSchemaAttribute objects for which values should be loaded
     * @throws Exception Thrown if unable to load attribute values or if an attribute being
     *                   queried is invalid in the schema
     */
    public void loadInstanceAttributeValues(
            Collection instances,
            Collection attributes) throws Exception {
        if (attributes.isEmpty() || instances.isEmpty()) {
            return;
        }
        for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
            GKInstance ins = (GKInstance) ii.next();
            for (Iterator ai = attributes.iterator(); ai.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
                if (ins.isAttributeValueLoaded(att)) {
                    // Don't inflate att if it has already been loaded
                    continue;
                }

                if (ins.getSchemClass().isValidAttribute(att)) {
                    // Retrieve attribute value only if it's valid for that instance
                    try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
                        StringBuilder query = new StringBuilder("MATCH (n:")
                                .append(ins.getSchemClass().getName())
                                .append("{dbId:")
                                .append(ins.getDBID())
                                .append("})");
                        Integer typeAsInt;
                        if (att.getTypeAsInt() > SchemaAttribute.INSTANCE_TYPE) {
                            query.append(" RETURN DISTINCT n.").append(att.getName());
                        } else {
                            String allowedClassName = ((SchemaClass) att.getAllowedClasses().iterator().next()).getName();
                            query.append("-[:")
                                    .append(att.getName())
                                    .append("]->(s:")
                                    .append(allowedClassName)
                                    .append(") RETURN DISTINCT s.dbId");
                        }
                        typeAsInt = att.getTypeAsInt();
                        // DEBUG: System.out.println(query);
                        Result result = session.run(query.toString());
                        List<Value> values = new ArrayList();
                        while (result.hasNext()) {
                            Value val = result.next().get(0);
                            if (val != NullValue.NULL)
                                values.add(val);
                        }
                        if (values.size() > 0) {
                            try {
                                switch (typeAsInt) {
                                    case SchemaAttribute.INSTANCE_TYPE:
                                        SchemaClass attributeClass = (SchemaClass) att.getAllowedClasses().iterator().next();
                                        if (values.size() > 1) {
                                            List<Instance> attrInstances = new ArrayList();
                                            for (Value value : values) {
                                                GKInstance instance = getInflateInstance(value, attributeClass);
                                                attrInstances.add(instance);
                                            }
                                            ins.setAttributeValueNoCheck(att, attrInstances);
                                        } else {
                                            GKInstance instance = getInflateInstance(values.get(0), attributeClass);
                                            ins.setAttributeValueNoCheck(att, instance);
                                        }
                                        break;
                                    case SchemaAttribute.STRING_TYPE:
                                        List<String> res = values.stream().map((x) -> (x.asString())).collect(Collectors.toList());
                                        ins.setAttributeValueNoCheck(att, res.size() > 1 ? res : res.get(0));
                                        break;
                                    case SchemaAttribute.INTEGER_TYPE:
                                        List<Integer> resI = values.stream().map((x) -> (x.asInt())).collect(Collectors.toList());
                                        ins.setAttributeValueNoCheck(att, resI.size() > 1 ? resI : resI.get(0));
                                        break;
                                    case SchemaAttribute.LONG_TYPE:
                                        List<Long> resL = values.stream().map((x) -> (x.asLong())).collect(Collectors.toList());
                                        ins.setAttributeValueNoCheck(att, resL.size() > 1 ? resL : resL.get(0));
                                        break;
                                    case SchemaAttribute.FLOAT_TYPE:
                                        List<Float> resF = values.stream().map((x) -> (x.asFloat())).collect(Collectors.toList());
                                        ins.setAttributeValueNoCheck(att, resF.size() > 1 ? resF : resF.get(0));
                                        break;
                                    case SchemaAttribute.BOOLEAN_TYPE:
                                        List<Boolean> resB = values.stream().map((x) -> (x.asBoolean())).collect(Collectors.toList());
                                        ins.setAttributeValueNoCheck(att, resB.size() > 1 ? resB : resB.get(0));
                                        break;
                                    default:
                                        throw new Exception("Unknown value type: " + att.getTypeAsInt());
                                }
                            } catch (org.neo4j.driver.exceptions.value.Uncoercible ex) {
                                // DEBUG: System.out.println(query);
                                ins.setAttributeValueNoCheck(att, values.get(0).asList());
                            }
                        }
                    }
                }
            }
        }
    }

    private GKInstance getInflateInstance(Value value, SchemaClass attributeClass) throws Exception {
        Long dbID = value.asLong();
        // DEBUG: System.out.println("In list: " + ins.getSchemClass().getName() + " : " + attributeClass.getName() + " : " + dbID);
        boolean instanceWasInCacheAlready = useCache && instanceCache.get(dbID) != null;
        GKInstance instance = (GKInstance) getInstance(attributeClass.getName(), dbID);
        if (!instance.isInflated() && !instanceWasInCacheAlready) {
            // Don't load attributes if 1. instance is already inflated, or if
            // 2. the cache is used and instance is already in it. There are cases
            // whereby event A is a precedingEvent of B, B is a precedingEvent of C, and
            // C is a precedingEvent of A. Case 2. prevents infinite loops of attribute retrieval
            // (and thus stack overflow)
            loadInstanceAttributeValues(instance);
        }
        return instance;
    }

    /**
     * (Re)loads all attribute values from the database.
     *
     * @param instance GKInstance for which to load attribute values
     * @throws Exception Thrown if unable to load attribute values or if an attribute being
     *                   queried is invalid in the schema
     */
    public void loadInstanceAttributeValues(GKInstance instance) throws Exception {
        if (!instance.isInflated()) {
            Collection attributes = new HashSet();
            java.util.List list = new ArrayList(1);
            list.add(instance);
            for (Iterator ai = instance.getSchemaAttributes().iterator(); ai.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
                attributes.add(att);
            }
            loadInstanceAttributeValues(list, attributes);
            instance.setIsInflated(true);
        }
    }

    public void loadInstanceAttributeValues(GKInstance instance,
                                            SchemaAttribute attribute) throws Exception {
        loadInstanceAttributeValues(Collections.singletonList(instance), Collections.singletonList(attribute));

    }
    
    public Collection fetchInstanceByAttribute(SchemaAttribute attribute,
                                               String operator,
                                               Object value) throws Exception {
        AttributeQueryRequest aqr = new AttributeQueryRequest(attribute, operator, value);
        return fetchInstance(aqr);
    }

    public Collection fetchInstanceByAttribute(String className,
                                               String attributeName,
                                               String operator,
                                               Object value) throws Exception {
        AttributeQueryRequest aqr = new AttributeQueryRequest(className, attributeName, operator, value);
        return fetchInstance(aqr);
    }

    public Collection fetchInstance(QueryRequest aqr) throws Exception {
        List aqrList = new ArrayList();
        aqrList.add(aqr);
        return fetchInstance(aqrList);
    }

    public Collection fetchInstancesByClass(SchemaClass schemaClass) throws Exception {
        return fetchInstancesByClass(schemaClass.getName(), null);
    }

    public long getClassInstanceCount(SchemaClass schemaClass) throws InvalidClassException {
        return getClassInstanceCount(schemaClass.getName());
    }

    public GKInstance fetchInstance(String className, Long dbID) throws Exception {
        Iterator<GKInstance> instancesIter = fetchInstancesByClass(className, Collections.singletonList(dbID)).iterator();
        if (instancesIter.hasNext()) {
            return instancesIter.next();
        }
        return null;
    }

    public GKInstance fetchInstance(Long dbID) throws Exception {
        String rootClassName = ((GKSchema) schema).getRootClass().getName();
        return fetchInstance(rootClassName, dbID);
    }

    /**
     * Update the database for a specific attribute of an instance by providing the local instance and the name of the
     * attribute to update
     *
     * @param instance      GKInstance containing the updated attribute
     * @param attributeName Name of the attribute to update for the corresponding instance in the database
     * @throws Exception Thrown if the instance has no dbId or if unable to update the specified attribute name
     */
    public void updateInstanceAttribute(GKInstance instance, String attributeName, Transaction tx) throws Exception {
        // TODO: Add declaration to PersistenceAdaptor once MySQLAdaptor has been eliminated
        if (instance.getDBID() == null) {
            throw (new DBIDNotSetException(instance));
        }
        SchemaAttribute attribute = instance.getSchemClass().getAttribute(attributeName);
        deleteFromDBInstanceAttributeValue(attribute, instance, tx);
        storeAttribute(attribute, instance, tx);
    }

    public void updateInstanceAttribute(GKInstance instance, String attributeName) throws Exception {
        // TODO: Remove from PersistenceAdaptor once MySQLAdaptor has been eliminated
    }

    /**
     * Store a GKInstance object into the database. The implementation of this method will store
     * all newly created, referred GKInstances by the specified GKInstance if they are not in the
     * database.
     *
     * @param instance GKInstance to store (it might be from the local file system)
     * @param tx       transaction
     * @return dbId of the stored instance
     * @throws Exception Thrown if unable to retrieve attribute values from the instance or if unable to store
     *                   the instance
     */
    public Long storeInstance(GKInstance instance, Transaction tx) throws Exception {
        // TODO: Add declaration to PersistenceAdaptor once MySQLAdaptor has been eliminated
        return storeInstance(instance, false, tx);
    }

    public Long storeInstance(GKInstance instance) throws Exception {
        // TODO: Remove from PersistenceAdaptor once MySQLAdaptor has been eliminated
        return null;
    }

    /**
     * Store a GKInstance object into the database. The implementation of this method will store
     * all newly created, referred GKInstances by the specified GKInstance if they are not in the
     * database.
     *
     * @param instance   GKInstance to store (it might be from the local file system)
     * @param forceStore when true, store the instance even if already in the database
     * @param tx         transaction
     * @return dbId of the stored instance
     * @throws Exception Thrown if unable to retrieve attribute values from the instance or if unable to store
     *                   the instance
     */
    public Long storeInstance(GKInstance instance, boolean forceStore, Transaction tx) throws Exception {
        Long dbID = null;
        if (forceStore) {
            dbID = instance.getDBID();
        } else if ((dbID = instance.getDBID()) != null) {
            return dbID;
        }
        SchemaClass cls = instance.getSchemClass();
        // cls might be from a local Schema copy. Convert it to the db copy.
        cls = schema.getClassByName(cls.getName());
        List<String> classHierarchy =
                ((List<GKSchemaClass>) cls.getOrderedAncestors()).stream().map((x) ->
                        (x.getName())).collect(Collectors.toList());
        StringBuilder stmt = new StringBuilder();
        if (dbID == null) {
            // Mint new dbId
            stmt.append("MATCH (s:Seq {key:\"dbIdSeq\"}) CALL apoc.atomic.add(s,'value',1,10) YIELD newValue as seq RETURN seq");
            Value result = executeTransaction(stmt.toString(), tx);
            if (result != null) {
                dbID = result.asLong();
                instance.setDBID(dbID);
            } else {
                throw (new Exception("Unable to get auto-incremented dbID value."));
            }
        }
        // Store Instance
        // Note: ancestors are attached as labels
        StringBuilder labels = new StringBuilder(cls.getName());
        if (classHierarchy.size() > 0) {
            labels.append(":").append(String.join(":", classHierarchy));
        }
        stmt.setLength(0);
        stmt.append("create (n:").append(labels).append("{")
                .append("dbId: ").append(dbID)
                .append(", displayName: \"").append(instance.getDisplayName()).append("\"")
                .append(", schemaClass: \"").append(cls.getName()).append("\"")
                .append("}) RETURN n.dbId");
        Value result = executeTransaction(stmt.toString(), tx);
        dbID = result.asLong();

        // Store attributes
        for (Iterator ai = instance.getSchemaAttributes().iterator(); ai.hasNext(); ) {
            GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
            storeAttribute(att, instance, tx);
        }
        return dbID;
    }

    private void storeAttribute(SchemaAttribute att, GKInstance instance, Transaction tx)
            throws Exception {
        if (instance.getDisplayName().equals("TopLevelPathway") && att.getName().equals("compartment")) {
            boolean val = Boolean.TRUE;
        }
        SchemaClass cls = instance.getSchemClass();
        if (att.getName().equals("dbId")) return;
        List attVals = instance.getAttributeValuesList(att.getName());
        if ((attVals == null) || (attVals.isEmpty())) return;
        List<String> stmts = new ArrayList();
        if (att.isInstanceTypeAttribute()) {
            for (GKInstance attrValInstance : (List<GKInstance>) attVals) {
                Long valDbID = storeInstance(attrValInstance, tx);
                StringBuilder stmt = new StringBuilder("MATCH (n:")
                        .append(cls.getName()).append("{")
                        .append("dbId:").append(instance.getDBID()).append("}) ")
                        .append("MATCH (p:")
                        .append(attrValInstance.getSchemClass().getName()).append("{")
                        .append("dbId:").append(valDbID).append("})")
                        .append(" CREATE (n)-[:")
                        .append(att.getName())
                        .append("]->(p)");
                stmts.add(stmt.toString());
            }
        } else {
            Object value = instance.getAttributeValue(att.getName());
            StringBuilder stmt = new StringBuilder("MATCH (n:")
                    .append(cls.getName()).append("{")
                    .append("dbId:").append(instance.getDBID())
                    .append("}) SET n.")
                    .append(att.getName())
                    .append(" = ");
            if (att.getTypeAsInt() == SchemaAttribute.STRING_TYPE) {
                stmt.append("\"").append(value).append("\"");
            } else {
                stmt.append(value);
            }
            stmt.append(" RETURN n.dbId");
            stmts.add(stmt.toString());
        }
        for (String stmt : stmts) {
            executeTransaction(stmt, tx);
        }
    }

    private void deleteFromDBInstanceAttributeValue(SchemaAttribute att, GKInstance instance, Transaction tx)
            throws Exception {
        SchemaClass cls = instance.getSchemClass();
        if (att.getName().equals("dbId")) return;
        List attVals = instance.getAttributeValuesList(att.getName());
        if ((attVals == null) || (attVals.isEmpty())) return;
        if (att.isInstanceTypeAttribute()) {
            StringBuilder stmt = new StringBuilder("MATCH (n:");
            stmt.append(cls.getName()).append("{")
                    .append("dbId:").append(instance.getDBID()).append("}) ")
                    .append("-[r:")
                    .append(att.getName())
                    .append("]->() DELETE r");
            executeTransaction(stmt.toString(), tx);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Deletes the supplied instance from the database.  Also deletes the instance
     * from all referrers, in the sense that if the instance is referred to in
     * an attribute of a referrer, it will be removed from that attribute.
     *
     * @param instance GKInstance to delete
     * @throws Exception Thrown if unable to retrieve attribute values for the instance or to delete the
     *                   instance from the database
     */

    public void deleteInstance(GKInstance instance, Transaction tx) throws Exception {
        Long dbID = instance.getDBID();
        // In case this instance is in the referrers cache of its references
        cleanUpReferences(instance);
        SchemaClass cls = fetchSchemaClassByDBID(dbID);
        deleteInstanceFromNeo4J(cls, dbID, tx);
        // Delete the Instance from the cache, but only after it has been deleted from referrers.
        instanceCache.remove(instance.getDBID());
    }

    public String fetchSchemaClassnameByDBID(Long dbID) {
        StringBuilder query = new StringBuilder("MATCH (n) WHERE n.dbId=").append(dbID).append(" RETURN n.schemaClass");
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            if (result.hasNext()) {
                Record record = result.next();
                return record.get(0).asString();
            }
            return null;
        }
    }

    public SchemaClass fetchSchemaClassByDBID(Long dbID) {
        return schema.getClassByName(fetchSchemaClassnameByDBID(dbID));
    }

    /**
     * This method is used to cleanup the referrers cache for a reference to a deleted GKInstance.
     * If this method is not called, a deleted GKInstance might be stuck in the referrers map.
     *
     * @param instance GKInstance for which to remove references held by other GKInstances
     * @throws Exception Thrown if unable to retrieve attribute values from the instance
     */
    private void cleanUpReferences(GKInstance instance) throws Exception {
        SchemaAttribute att = null;
        for (Iterator it = instance.getSchemClass().getAttributes().iterator(); it.hasNext(); ) {
            att = (SchemaAttribute) it.next();
            if (!att.isInstanceTypeAttribute())
                continue;
            List values = instance.getAttributeValuesList(att);
            if (values == null || values.size() == 0)
                continue;
            for (Iterator it1 = values.iterator(); it1.hasNext(); ) {
                GKInstance references = (GKInstance) it1.next();
                references.clearReferers(); // Just to refresh the whole referers map
            }
        }
    }

    private void deleteInstanceFromNeo4J(SchemaClass cls, Long dbID, Transaction tx) {
        // NB. DELETE DETACH removes the node and all its relationships
        // (but not nodes at the other end of those relationships)
        StringBuilder stmt = new StringBuilder("MATCH (n:").append(cls.getName()).append("{")
                .append("dbId:").append(dbID)
                .append("}) DETACH DELETE n");
        executeTransaction(stmt.toString(), tx);
    }

    // TODO: c.f. https://neo4j.com/docs/java-reference/current/transaction-management/
    public Value executeTransaction(String statement, Transaction tx) {
        Throwable txEx = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                Result result = tx.run(statement);
                if (result.hasNext()) {
                    return result.next().get(0);
                }
                return null;
            } catch (Throwable ex) {
                txEx = ex;

                // Re-try only on DeadlockDetectedException
                if (!(ex instanceof DeadlockDetectedException)) {
                    break;
                }
            }

            // Wait so that we don't immediately get into the same deadlock
            if (i < RETRIES - 1) {
                try {
                    Thread.sleep(BACKOFF);
                } catch (InterruptedException e) {
                    throw new TransactionFailureException("Interrupted", e);
                }
            }
        }

        if (txEx instanceof TransactionFailureException) {
            throw ((TransactionFailureException) txEx);
        } else if (txEx instanceof Error) {
            throw ((Error) txEx);
        } else {
            throw ((RuntimeException) txEx);
        }
    }

    public Collection fetchInstancesByClass(String className, List dbIds) throws Exception {
        Set<Instance> instances = new HashSet();
        ((GKSchema) schema).isValidClassOrThrow(className);
        StringBuilder query = new StringBuilder("MATCH (n:").append(className).append(")");
        if (dbIds != null) {
            query.append(" WHERE n.dbId IN").append(dbIds);
        }
        query.append(" RETURN n.dbId, n.displayName, n.schemaClass");
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            while (result.hasNext()) {
                Record record = result.next();
                long dbId = record.get(0).asLong();
                String displayName = record.get(1).asString();
                String schemaClass = record.get(2).asString();
                Instance instance = getInstance(schemaClass, dbId);
                instances.add(instance);
                instance.setDisplayName(displayName);
                instance.setSchemaClass(getSchema().getClassByName(className));
            }
            return instances;
        }
    }

    public long getClassInstanceCount(String className) throws InvalidClassException {
        ((GKSchema) schema).isValidClassOrThrow(className);
        StringBuilder query = new StringBuilder("MATCH (n:").append(className).append(") RETURN count(n)");
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            Record record = result.next();
            return record.get(0).asLong();
        }
    }

    public Map getAllInstanceCounts() throws Exception {
        Map<String, Long> map = new HashMap();
        String query = "MATCH (n) WHERE n.schemaClass is not null RETURN n.schemaClass, count(n.dbId)";
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query);
            while (result.hasNext()) {
                Record record = result.next();
                map.put(record.get(0).asString(), record.get(1).asLong());
            }
            return map;
        }
    }

    /**
     * Check if there exists a GKInstance object with the specified DB_ID
     * in the database. This method will query the database directly.
     * InstanceCache is not used.
     *
     * @param dbID db id value to check to see if it exists in the database
     * @return true if existing; false otherwise
     */
    public boolean exist(Long dbID) {
        StringBuilder query = new StringBuilder("MATCH (n) WHERE n.dbId = ").append(dbID).append(" RETURN n.dbId");
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            if (result.hasNext()) {
                return true;
            }
            return false;
        }
    }

    /**
     * Tries to get an instance of the given class, with the given DB_ID, from
     * instance cache, if possible.  Otherwise, creates a new instance with
     * the given DB_ID.  This new instance will be cached even if caching is
     * switched off.
     *
     * @param className Name of the class for the instance to retrieve or create
     * @param dbID      DbId value for the instance to retrieve or create
     * @return Instance of the class name provided with the db id provided
     * @throws InstantiationException Thrown if unable to create a new instance for the class name
     * @throws IllegalAccessException Thrown if unable to access constructor for the class name
     * @throws ClassNotFoundException Thrown if unable to find the class for the class name
     */
    public Instance getInstance(String className, Long dbID) throws
            InstantiationException, IllegalAccessException, ClassNotFoundException {
        GKInstance instance;
        if (!useCache || (instance = instanceCache.get(dbID)) == null) {
            if (classMap != null && classMap.containsKey(className)) {
                String targetClassName = (String) classMap.get(className);
                instance = (GKInstance) Class.forName(targetClassName).newInstance();
            } else {
                instance = new GKInstance();
            }
            instance.setSchemaClass(getSchema().getClassByName(className));
            instance.setDBID(dbID);
            instance.setDbAdaptor(this);
            instanceCache.put(instance);
        }
        return instance;
    }

    public String getSchemaTimestamp() {
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run("MATCH(s:Schema) RETURN s.timestamp");
            if (result.hasNext()) {
                Record record = result.next();
                return record.get(0).asString();
            }
            return null;
        }
    }

    /**
     * Method for fetching instances with given DB_IDs
     *
     * @param dbIDs Collection of dbId values for which to retrieve corresponding instances
     * @return Collection of instances
     * @throws Exception Thrown if unable to retrieve instances by dbId values from the database
     */
    public Collection fetchInstance(Collection dbIDs) throws Exception {
        return fetchInstanceByAttribute(((GKSchema) schema).getRootClass().getName(), "DB_ID", "=", dbIDs);
    }

    public abstract class QueryRequest {
        protected SchemaClass cls;
        protected SchemaAttribute attribute;
        protected String operator;
        protected Object value;

        /**
         * Returns the SchemaAttribute object of the query request which defines which
         * instance attribute of a class to search
         *
         * @return SchemaAttribute of the query request
         */
        public SchemaAttribute getAttribute() {
            return attribute;
        }

        /**
         * Returns the SchemaClass object of the query request which defines which
         * class to search
         *
         * @return SchemaClass of the query request
         */
        public SchemaClass getCls() {
            return cls;
        }

        /**
         * Returns a String value of the operator of the query request which defines how
         * the search is restricted
         *
         * @return Operator of the query request
         */
        public String getOperator() {
            return operator;
        }

        /**
         * Returns the value of the search to check given the class, attribute and operator
         * restrictions defined
         *
         * @return Value of the query request
         */
        public Object getValue() {
            return value;
        }

        /**
         * Set the SchemaAttribute object of the query request which defines which
         * instance attribute of a class to search
         *
         * @param attribute SchemaAttribute of the query request
         */
        public void setAttribute(SchemaAttribute attribute) {
            this.attribute = attribute;
        }

        /**
         * The SchemaClass object of the query request which defines which class to search
         *
         * @param class1 SchemaClass of the query request
         */
        public void setCls(SchemaClass class1) {
            cls = class1;
        }

        /**
         * A String value of the operator of the query request which defines how the search is restricted
         *
         * @param string Operator of the query request
         */
        public void setOperator(String string) {
            operator = string;
        }

        /**
         * The value of the search to check given the class, attribute and operator restrictions
         * defined
         *
         * @param object Values of the query request
         */
        public void setValue(Object object) {
            value = object;
        }
    }

    public class AttributeQueryRequest extends QueryRequest {

        public AttributeQueryRequest(String clsName, String attName, String operator, Object value) throws InvalidClassException, InvalidAttributeException {
            schema.isValidClassOrThrow(clsName);
            cls = schema.getClassByName(clsName);
            // getAttribute checks for the validity
            attribute = cls.getAttribute(attName).getOrigin().getAttribute(attName);
            if (operator == null || operator.equals("")) {
                operator = "=";
            }
            this.operator = operator.toUpperCase();
            this.value = value;
        }

        public AttributeQueryRequest(SchemaAttribute attribute, String operator, Object value) throws InvalidAttributeException {
            cls = attribute.getOrigin();
            //this.attribute = attribute;
            this.attribute = attribute.getOrigin().getAttribute(attribute.getName());
            if (operator == null || operator.equals("")) {
                operator = "=";
            }
            this.operator = operator.toUpperCase();
            this.value = value;
        }

        public AttributeQueryRequest(SchemaClass cls, SchemaAttribute attribute, String operator, Object value) throws InvalidAttributeException {
            this.cls = cls;
            this.attribute = attribute.getOrigin().getAttribute(attribute.getName());
            if (operator == null || operator.equals("")) {
                operator = "=";
            }
            this.operator = operator.toUpperCase();
            this.value = value;
        }
    }
}
