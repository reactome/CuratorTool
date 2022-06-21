package org.gk.persistence;

import org.gk.model.*;
import org.gk.schema.*;
import org.gk.util.StringUtils;
import org.neo4j.driver.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.internal.value.NullValue;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.kernel.DeadlockDetectedException;
import org.reactome.server.graph.curator.domain.model.DatabaseObject;
import org.springframework.data.neo4j.core.schema.Relationship;

import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Neo4JAdaptor implements PersistenceAdaptor {
    private String host;
    private String database;
    private String username;
    private String password;
    private int port = 7687;
    protected Driver driver;
    private Schema schema;
    private InstanceCache instanceCache = new InstanceCache();
    private AttributeValueCache attributeValuesCache = new AttributeValueCache();
    private boolean useInstanceCache = true;
    private boolean useAttributeValuesCache = true;
    private Map classMap;
    public boolean debug = false;
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
     * Creates a new instance of Neo4JAdaptor with a default port of 7687
     *
     * @param host     Database host
     * @param database Database name
     * @param username User name to connect to the database
     * @param password Password for the specified user name to connect to the database
     */
    public Neo4JAdaptor(String host, String database, String username, String password) {
        this(host, database, username, password, 7687);
    }

    /**
     * Creates a new instance of Neo4JAdaptor
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
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
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

    public String getDBUser() {
        return this.username;
    }

    public String getDBPwd() {
        return this.password;
    }

    public Schema fetchSchema() throws Exception {
        schema = new Neo4JSchemaParser().parseNeo4JResults(getClassNames("org.reactome.server.graph.curator.domain.model"));
        // Retrieve from Neo4J the timestamp for the current data model and set it in schema
        ((GKSchema) schema).setTimestamp(getSchemaTimestamp());
        return schema;
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

    private String getQueryOperand(String className, String attributeName) throws ClassNotFoundException {
        String parentClazzName = DatabaseObject.class.getPackage().getName() + "." + className;
        Class<?> parentClazz = Class.forName(parentClazzName);
        List<Field> fields = Neo4JSchemaParser.getAllFields(new ArrayList<>(), parentClazz);
        Map<String, Field> fieldName2Field = new HashMap();
        for (Field field : fields) {
            fieldName2Field.put(field.getName(), field);
        }
        Field attNameField = fieldName2Field.get(attributeName);
        Annotation annotation = attNameField.getAnnotation(Relationship.class);
        if (annotation != null && ((Relationship) annotation).direction() == Relationship.Direction.INCOMING) {
            return "<-";
        }
        return "-";
    }

    // Load all values for className and att and put them into attributeValuesCache
    public void loadAllAttributeValues(String className, SchemaAttribute att) throws Exception {
        if (attributeValuesCache.hasValues(className, att.getName())) {
            return;
        }
        // Prepare query
        StringBuilder query = new StringBuilder("MATCH (n:").append(className).append(")");
        if (att.getTypeAsInt() > SchemaAttribute.INSTANCE_TYPE) {
            // Primitive attribute
            query.append(" RETURN DISTINCT n.DB_ID, n.").append(att.getName());
        } else {
            // Instance attribute
            String allowedClassName = ((SchemaClass) att.getAllowedClasses().iterator().next()).getName();
            String operand = getQueryOperand(className, att.getName());
            query.append(operand).append("[r:").append(att.getName()).append("]->(s:").append(allowedClassName).append(") RETURN DISTINCT n.DB_ID, s.DB_ID");
            if (att.isMultiple()) {
                query.append(", r.order");
            }
        }
        // DEBUG System.out.println(query);
        // Run query, collect results and add them to attributeValuesCache
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            if (att.getTypeAsInt() > SchemaAttribute.INSTANCE_TYPE) {
                // Primitive attribute
                while (result.hasNext()) {
                    Record rec = result.next();
                    Long dbId = rec.get(0).asLong();
                    if (rec.get(1) != NullValue.NULL) {
                        attributeValuesCache.addValue(className, att.getName(), dbId, rec.get(1));
                    }
                }
            } else {
                // Instance attribute
                if (!att.isMultiple()) {
                    // Single value Instance attribute
                    while (result.hasNext()) {
                        Record rec = result.next();
                        Long dbId = rec.get(0).asLong();
                        if (rec.get(1) != NullValue.NULL) {
                            attributeValuesCache.addValue(className, att.getName(), dbId, rec.get(1));
                        }
                    }
                } else {
                    // Multiple value Instance attribute
                    List<Record> results = result.list();
                    // Sort all results by order property of the relationship
                    Collections.sort(results, new Comparator<Record>() {
                        public int compare(Record o1, Record o2) {
                            return o1.get(2).asInt() - o2.get(2).asInt();
                        }
                    });
                    // Retrieve sorted results
                    for (Record rec : results) {
                        Long dbId = rec.get(0).asLong();
                        if (rec.get(1) != NullValue.NULL) {
                            attributeValuesCache.addValue(className, att.getName(), dbId, rec.get(1));
                        }
                    }
                }
            }
        }
    }

    /**
     * @param instances Collection of GKInstance objects for which to load attributes
     * @param attribute GKSchemaAttribute
     * @throws Exception
     */
    public void loadInstanceAttributeValues(Collection instances, SchemaAttribute attribute) throws Exception {
        loadInstanceAttributeValues(instances, Collections.singleton(attribute));
    }

    public void loadInstanceAttributeValues(Collection instances, Collection attributes) throws Exception {
        loadInstanceAttributeValues(instances, attributes, true);
    }

    /**
     * NOTE: This method expects instances to be found in InstanceCache.
     *
     * @param instances  Collection of GKInstance objects for which to load attributes
     * @param attributes Collection of GKSchemaAttribute objects for which values should be loaded
     * @param recursive  if false, dont inflate instance-type attributes
     * @throws Exception Thrown if unable to load attribute values or if an attribute being
     *                   queried is invalid in the schema
     */
    public void loadInstanceAttributeValues(Collection instances, Collection attributes, Boolean recursive) throws
            Exception {
        if (attributes.isEmpty() || instances.isEmpty()) {
            return;
        }

        for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
            GKInstance ins = (GKInstance) ii.next();
            String instanceClassName = ins.getSchemClass().getName();
            // Collect attributes into groups:
            // 1. primitive attributes (into primitiveAttributesWithSingleValue) with single value
            // 2. each primitive attribute with multiple values (into primitiveAttributesWithMultipleValues)
            // 3. Instance-type attributes
            List<GKSchemaAttribute> primitiveAttributesWithSingleValue = new ArrayList<>();
            List<GKSchemaAttribute> primitiveAttributesWithMultipleValues = new ArrayList<>();
            List<GKSchemaAttribute> instanceAttributes = new ArrayList<>();
            for (Iterator ai = attributes.iterator(); ai.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
                if (ins.isAttributeValueLoaded(att) || !ins.getSchemClass().isValidAttribute(att)) {
                    // Don't inflate att if it has already been loaded or is not valid for this instance
                    continue;
                }
                if (att.getTypeAsInt() > SchemaAttribute.INSTANCE_TYPE) {
                    if (att.isMultiple()) {
                        primitiveAttributesWithMultipleValues.add(att);
                    } else {
                        primitiveAttributesWithSingleValue.add(att);
                    }
                } else {
                    instanceAttributes.add(att);
                }
            }
            // Now construct Neo4J queries per each of the attribute groups in primitiveAttNames and instanceAttributes
            Map<String, List<GKSchemaAttribute>> cypherQueries = new HashMap<>();
            // Primitive attributes with a single value
            StringBuilder queryRoot = new StringBuilder("MATCH (n:").append(ins.getSchemClass().getName()).append("{DB_ID:").append(ins.getDBID()).append("})");
            if (primitiveAttributesWithSingleValue.size() > 0) {
                StringBuilder query = new StringBuilder(queryRoot.toString());
                Boolean first = true;
                for (GKSchemaAttribute a : primitiveAttributesWithSingleValue) {
                    if (first) {
                        query.append(" RETURN DISTINCT ");
                        first = false;
                    } else
                        query.append(", ");
                    query.append("n.").append(a.getName());
                }
                cypherQueries.put(query.toString(), primitiveAttributesWithSingleValue);
            }
            // Primitive attributes with multiple values
            if (primitiveAttributesWithMultipleValues.size() > 0) {
                for (GKSchemaAttribute a : primitiveAttributesWithMultipleValues) {
                    StringBuilder query = new StringBuilder("MATCH (n:").append(ins.getSchemClass().getName()).append("{DB_ID:").append(ins.getDBID()).append("})");
                    query.append(" RETURN DISTINCT n.").append(a.getName());
                    cypherQueries.put(query.toString(), Collections.singletonList(a));
                }
            }
            // Instance-type attributes
            for (GKSchemaAttribute a : instanceAttributes) {
                String operand = getQueryOperand(instanceClassName, a.getName());
                String allowedClassName = ((SchemaClass) a.getAllowedClasses().iterator().next()).getName();
                StringBuilder query = new StringBuilder(queryRoot.toString()).append("-[r:").append(a.getName());
                query.append("]").append(operand).append("(s:").append(allowedClassName).append(") RETURN DISTINCT s.DB_ID, type(r)");
                if (a.isMultiple()) {
                    query.append(", r.order");
                }
                cypherQueries.put(query.toString(), Collections.singletonList(a));
            }

            // Now run cypherQueries and collect results
            for (String query : cypherQueries.keySet()) {
                List<GKSchemaAttribute> atts = cypherQueries.get(query);
                try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
                    Result result = null;
                    if (atts.size() == 1) {
                        loadAllAttributeValues(instanceClassName, atts.get(0));
                    } else {
                        // DEBUG System.out.println(query);
                        result = session.run(query);
                    }
                    if (atts.size() > 0) {
                        if (!atts.get(0).isMultiple()) {
                            // Single-value attributes
                            if (!atts.get(0).isInstanceTypeAttribute()) {
                                // Primitive Single-value attributes
                                if (result != null) {
                                    if (result.hasNext()) {
                                        Record rec = result.next();
                                        int i = 0;
                                        for (GKSchemaAttribute gkAtt : atts) {
                                            Value val = rec.get(i++);
                                            if (val != NullValue.NULL) {
                                                handleAttributeValue(ins, gkAtt, Collections.singletonList(val), recursive);
                                            }
                                        }
                                    }
                                } else {
                                    List<Value> values = attributeValuesCache.getValues(instanceClassName, atts.get(0).getName(), ins.getDBID());
                                    handleAttributeValue(ins, atts.get(0), values, recursive);
                                }
                            } else {
                                // Instance-type Single-value attributes
                                if (result != null) {
                                    List<Record> results = result.list();
                                    if (results.size() > 0) {
                                        for (GKSchemaAttribute gkAtt : atts) {
                                            List<Record> resultsForAttr = results.stream()
                                                    .filter(p -> p.get(1).asString().equals(gkAtt.getName())).collect(Collectors.toList());
                                            if (resultsForAttr.size() > 0) {
                                                Value val = resultsForAttr.get(0).get(0);
                                                if (val != NullValue.NULL) {
                                                    handleAttributeValue(ins, gkAtt, Collections.singletonList(val), recursive);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    List<Value> values = attributeValuesCache.getValues(instanceClassName, atts.get(0).getName(), ins.getDBID());
                                    handleAttributeValue(ins, atts.get(0), values, recursive);
                                }
                            }
                        } else {
                            // Multiple-value attributes
                            if (!atts.get(0).isInstanceTypeAttribute()) {
                                // Primitive Multiple-value attributes
                                if (result != null) {
                                    List<Value> values = new ArrayList();
                                    for (GKSchemaAttribute gkAtt : atts) {
                                        while (result.hasNext()) {
                                            Value val = result.next().get(0);
                                            if (val != NullValue.NULL)
                                                values.add(val);
                                        }
                                        handleAttributeValue(ins, gkAtt, values, recursive);
                                    }
                                } else {
                                    List<Value> values = attributeValuesCache.getValues(instanceClassName, atts.get(0).getName(), ins.getDBID());
                                    handleAttributeValue(ins, atts.get(0), values, recursive);
                                }
                            } else {
                                // Instance-type Multiple-value attributes
                                if (result != null) {
                                    List<Value> values = new ArrayList();
                                    List<Record> results = result.list();
                                    for (GKSchemaAttribute gkAtt : atts) {
                                        // First sort all results by order property of the relationship
                                        Collections.sort(results, new Comparator<Record>() {
                                            public int compare(Record o1, Record o2) {
                                                return o1.get(2).asInt() - o2.get(2).asInt();
                                            }
                                        });
                                        // Then filter sorted results by gkAtt's name
                                        List<Record> resultsForAttr = results.stream()
                                                .filter(p -> p.get(1).asString().equals(gkAtt.getName())).collect(Collectors.toList());
                                        for (Record rec : resultsForAttr) {
                                            Value val = rec.get(0);
                                            if (val != NullValue.NULL)
                                                values.add(val);
                                        }
                                        handleAttributeValue(ins, gkAtt, values, recursive);
                                    }
                                } else {
                                    List<Value> values = attributeValuesCache.getValues(instanceClassName, atts.get(0).getName(), ins.getDBID());
                                    handleAttributeValue(ins, atts.get(0), values, recursive);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleAttributeValue(GKInstance ins, GKSchemaAttribute att, List<Value> values, Boolean
            recursive) throws Exception {
        Integer typeAsInt = att.getTypeAsInt();
        if (values != null && values.size() > 0) {
            try {
                switch (typeAsInt) {
                    case SchemaAttribute.INSTANCE_TYPE:
                        SchemaClass attributeClass = (SchemaClass) att.getAllowedClasses().iterator().next();
                        if (values.size() > 1) {
                            List<Instance> attrInstances = new ArrayList();
                            for (Value value : values) {
                                GKInstance instance;
                                if (recursive) {
                                    instance = getInflateInstance(value, attributeClass);
                                } else {
                                    Long dbID = value.asLong();
                                    instance = (GKInstance) getInstance(attributeClass.getName(), dbID);
                                }
                                attrInstances.add(instance);
                            }
                            ins.setAttributeValueNoCheck(att, attrInstances);
                        } else {
                            GKInstance instance;
                            if (recursive) {
                                instance = getInflateInstance(values.get(0), attributeClass);
                            } else {
                                Long dbID = values.get(0).asLong();
                                instance = (GKInstance) getInstance(attributeClass.getName(), dbID);
                            }
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

    private GKInstance getInflateInstance(Value value, SchemaClass attributeClass) throws Exception {
        Long dbID = value.asLong();
        // DEBUG: System.out.println("In list: " + ins.getSchemClass().getName() + " : " + attributeClass.getName() + " : " + dbID);
        boolean instanceWasInCacheAlready = useInstanceCache && instanceCache.get(dbID) != null;
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
        loadInstanceAttributeValues(instance, true);
    }

    /**
     * (Re)loads all attribute values from the database.
     *
     * @param instance  GKInstance for which to load attribute values
     * @param recursive if true, inflate all instances recursively
     * @throws Exception Thrown if unable to load attribute values or if an attribute being
     *                   queried is invalid in the schema
     */
    public void loadInstanceAttributeValues(GKInstance instance, Boolean recursive) throws Exception {
        if (!instance.isInflated()) {
            Collection attributes = new HashSet();
            java.util.List list = new ArrayList(1);
            list.add(instance);
            for (Iterator ai = instance.getSchemaAttributes().iterator(); ai.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
                attributes.add(att);
            }
            loadInstanceAttributeValues(list, attributes, recursive);
            instance.setIsInflated(true);
        }
    }

    public void loadInstanceAttributeValues(GKInstance instance, SchemaAttribute attribute) throws Exception {
        loadInstanceAttributeValues(Collections.singletonList(instance), Collections.singletonList(attribute), true);
    }

    public void loadInstanceAttributeValues(GKInstance instance, SchemaAttribute attribute, Boolean recursive) throws
            Exception {
        loadInstanceAttributeValues(Collections.singletonList(instance), Collections.singletonList(attribute), recursive);
    }

    public void loadInstanceAttributeValues(Collection instances, String[] attNames) throws Exception {
        GKSchema s = (GKSchema) getSchema();
        Set attributes = new HashSet();
        for (int i = 0; i < attNames.length; i++) {
            attributes.addAll(s.getOriginalAttributesByName(attNames[i]));
        }
        loadInstanceAttributeValues(instances, attributes);
    }

    public void loadInstanceAttributeValues(Collection instances) throws Exception {
        Set classes = new HashSet();
        //Find out what classes we have
        for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
            GKInstance i = (GKInstance) ii.next();
            classes.add(i.getSchemClass());
        }
        Set tmp = new HashSet();
        Set tmp2 = new HashSet();
        tmp.addAll(classes);
        //Find the subclasses of the classes
        while (!tmp.isEmpty()) {
            for (Iterator ti = tmp.iterator(); ti.hasNext(); ) {
                GKSchemaClass c = (GKSchemaClass) ti.next();
                tmp2.addAll(c.getSubClasses());
            }
            tmp.clear();
            tmp.addAll(tmp2);
            classes.addAll(tmp2);
            tmp2.clear();
        }
        Set attributes = new HashSet();
        //Find all original attributes that those classes have
        for (Iterator ci = classes.iterator(); ci.hasNext(); ) {
            GKSchemaClass cls = (GKSchemaClass) ci.next();
            attributes.addAll(cls.getAttributes());
        }
        //Load the values of those attributes
        loadInstanceAttributeValues(instances, attributes);
    }

    public Collection fetchInstanceByAttribute(SchemaAttribute attribute, String operator, Object value) throws
            Exception {
        AttributeQueryRequest aqr = new AttributeQueryRequest(attribute, operator, value);
        return fetchInstance(aqr);
    }

    public Collection fetchInstanceByAttribute(String className, String attributeName, String operator, Object
            value) throws Exception {
        AttributeQueryRequest aqr = new AttributeQueryRequest(className, attributeName, operator, value);
        return fetchInstance(aqr);
    }

    public Collection fetchInstance(QueryRequest aqr) throws Exception {
        List<QueryRequest> aqrList = new ArrayList();
        aqrList.add(aqr);
        return fetchInstance(aqrList);
    }

    // Throw Exception if o is an instance of a class that is inconsistent with a previously set value of collectionOfInstancesOnly
    // e.g. if o is of type Instance, but the previous value of collectionOfInstancesOnly corresponds to a QueryRequest
    private Boolean setCollectionOfInstancesOnlyFlag(Object o, Boolean collectionOfInstancesOnly) throws
            Exception {
        String errMsg = "Illegal Collection - a mix of Instances and QueryRequests in a single Collection is not implemented";
        if (o instanceof org.gk.model.Instance) {
            if (collectionOfInstancesOnly == null) {
                return true;
            } else if (collectionOfInstancesOnly == false) {
                throw new Exception(errMsg);
            }
        } else if (o instanceof AttributeQueryRequest || o instanceof ReverseAttributeQueryRequest) {
            if (collectionOfInstancesOnly == null) {
                return false;
            } else if (collectionOfInstancesOnly == true) {
                throw new Exception(errMsg);
            }
        }
        return collectionOfInstancesOnly;
    }

    // Throw Exception unless qr's operator is "IS NOT NULL"
    private Boolean isNotNullQuery(QueryRequest qr) throws Exception {
        if (!qr.getOperator().equals("IS NOT NULL")) {
            throw new Exception("Illegal sub-query - only 'IS NOT NULL' sub-queries are implemented");
        }
        return true;
    }

    public Set fetchInstance(List<QueryRequest> aqrList) throws Exception {
        SchemaAttribute _displayName = ((GKSchema) schema).getRootClass().getAttribute("_displayName");
        StringBuilder query = new StringBuilder();
        StringBuilder whereClause = new StringBuilder();
        String whereClauseKeyWord = " WHERE";
        Integer pos = 1;
        for (Iterator i = aqrList.iterator(); i.hasNext(); ) {
            Neo4JAdaptor.QueryRequest aqr = (Neo4JAdaptor.QueryRequest) i.next();
            if (aqr instanceof Neo4JAdaptor.ReverseAttributeQueryRequest) {
                query.append(" MATCH (n)");
            } else {
                query.append(" MATCH (n:").append(aqr.getCls().getName()).append(")");
            }
            SchemaAttribute att = aqr.getAttribute();
            String attName = att.getName();
            Object value = aqr.getValue();
            if (att.isInstanceTypeAttribute()) {
                // Instance attribute
                if (!aqr.getOperator().equals("IS NULL")) {
                    String operand = getQueryOperand(aqr.getCls().getName(), att.getName());
                    query.append(operand).append("[:").append(attName).append("]->(s").append(pos);
                    if (aqr instanceof Neo4JAdaptor.ReverseAttributeQueryRequest) {
                        query.append(":").append(aqr.getCls().getName());
                    }
                    query.append(") ");
                }
                if (value instanceof Collection) {
                    // Value is a collection
                    // The assumption: The Collection consists of either of 1. Instances only, or of
                    // 2. (potentially a mixture of) 'IS NOT NULL' AttributeQueryRequests/ReverseAttributeQueryRequests only.
                    List<Long> dbIds = new ArrayList();
                    Boolean collectionOfInstancesOnly = null;
                    for (Iterator ci = ((Collection) value).iterator(); ci.hasNext(); ) {
                        Object o = ci.next();
                        if (o instanceof org.gk.model.Instance) {
                            dbIds.add(((Instance) o).getDBID().longValue());
                            collectionOfInstancesOnly = setCollectionOfInstancesOnlyFlag(o, collectionOfInstancesOnly);
                        } else if (o instanceof AttributeQueryRequest) {
                            collectionOfInstancesOnly = setCollectionOfInstancesOnlyFlag(o, collectionOfInstancesOnly);
                            AttributeQueryRequest subAqr = (AttributeQueryRequest) o;
                            if (isNotNullQuery(subAqr)) {
                                query.append(" MATCH (s").append(pos).append(":")
                                        .append(subAqr.getCls().getName()).append(")")
                                        .append("-[:").append(attName).append("]->()");
                            }
                        } else if (o instanceof ReverseAttributeQueryRequest) {
                            collectionOfInstancesOnly = setCollectionOfInstancesOnlyFlag(o, collectionOfInstancesOnly);
                            ReverseAttributeQueryRequest subAqr = (ReverseAttributeQueryRequest) o;
                            if (isNotNullQuery(subAqr)) {
                                query.append(" MATCH (s").append(pos).append(")")
                                        .append("-[:").append(subAqr.getAttribute().getName())
                                        .append("]->(").append(subAqr.getCls().getName()).append(")");
                            }
                        }
                    }
                    if (collectionOfInstancesOnly) {
                        whereClause.append(whereClauseKeyWord).append(" s").append(pos).append(".DB_ID IN (").append(dbIds).append(")");
                    }
                } else {
                    // Single value - either 1. Instance, or 2. an "IS NOT NULL" AttributeQueryRequest/ReverseAttributeQueryRequest.
                    if (value instanceof org.gk.model.Instance) {
                        if (!aqr.getOperator().equals("IS NOT NULL")) {
                            if (!aqr.getOperator().equals("IS NULL")) {
                                whereClause.append(whereClauseKeyWord).append(" s").append(pos).append(".DB_ID = ").append(((GKInstance) value).getDBID());
                            } else {
                                whereClause.append(whereClauseKeyWord).append(" NOT ").append("(n)-[:").append(attName).append("]->() ");
                            }
                        }
                    } else if (value instanceof AttributeQueryRequest) {
                        AttributeQueryRequest subAqr = (AttributeQueryRequest) value;
                        if (isNotNullQuery(subAqr)) {
                            query.append(" MATCH (s").append(pos).append(":")
                                    .append(subAqr.getCls().getName()).append(")")
                                    .append("-[:").append(attName).append("]->()");
                        }
                    } else if (value instanceof ReverseAttributeQueryRequest) {
                        ReverseAttributeQueryRequest subAqr = (ReverseAttributeQueryRequest) value;
                        if (isNotNullQuery(subAqr)) {
                            query.append(" MATCH (s").append(pos).append(")")
                                    .append("-[:").append(subAqr.getAttribute().getName())
                                    .append("]->(").append(subAqr.getCls().getName()).append(")");
                        }
                    }
                }
            } else {
                // Primitive attribute
                whereClause.append(whereClauseKeyWord);
                if (att.isMultiple()) {
                    // E.g. Species.name
                    whereClause.append(" ANY(x IN n.").append(attName);
                } else {
                    if (Arrays.asList("NOT LIKE", "!=").contains(aqr.getOperator())) {
                        whereClause.append(" NOT (");
                    }
                    whereClause.append(" n.").append(attName);
                }
                if (value instanceof Collection) {
                    // Value is a collection of primitive values
                    List<Object> vals = new ArrayList();
                    for (Iterator ci = ((Collection) value).iterator(); ci.hasNext(); ) {
                        Object val = ci.next();
                        if (val instanceof String) {
                            if (att.getTypeAsInt() == SchemaAttribute.LONG_TYPE) {
                                try {
                                    vals.add(Long.parseLong((String) val));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.INTEGER_TYPE) {
                                try {
                                    vals.add(Integer.parseInt((String) val));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.FLOAT_TYPE) {
                                try {
                                    vals.add(Float.parseFloat((String) val));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.BOOLEAN_TYPE) {
                                try {
                                    vals.add(Boolean.parseBoolean((String) val));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.STRING_TYPE) {
                                vals.add("\"" + val + "\"");
                            }
                        }
                        if (val instanceof Integer) {
                            vals.add(((Integer) val).intValue());
                        } else if (val instanceof Float) {
                            vals.add(((Float) val).floatValue());
                        } else if (val instanceof Boolean) {
                            vals.add(((Boolean) val).booleanValue());
                        } else if (val instanceof Long) {
                            vals.add(((Long) val).longValue());
                        }
                    }
                    if (att.isMultiple()) {
                        whereClause.append(" WHERE x");
                    }
                    whereClause.append(" IN (").append(vals).append(")");
                    if (att.isMultiple()) {
                        whereClause.append(")");
                    }
                } else {
                    // Single primitive value
                    if (!Arrays.asList("IS NULL", "IS NOT NULL").contains(aqr.getOperator())) {
                        String operator = " = ";
                        if (Arrays.asList("LIKE", "NOT LIKE", "REGEXP").contains(aqr.getOperator())) {
                            operator = " =~ ";
                        }
                        if (att.isMultiple()) {
                            if (Arrays.asList("NOT LIKE", "!=").contains(aqr.getOperator())) {
                                whereClause.append(" WHERE NOT (x");
                            } else {
                                whereClause.append(" WHERE x");
                            }
                        }
                        if (value instanceof String) {
                            if (att.getTypeAsInt() == SchemaAttribute.LONG_TYPE) {
                                try {
                                    whereClause.append(operator).append(Long.parseLong((String) value));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.INTEGER_TYPE) {
                                try {
                                    whereClause.append(operator).append(Integer.parseInt((String) value));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.FLOAT_TYPE) {
                                try {
                                    whereClause.append(operator).append(Float.parseFloat((String) value));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.BOOLEAN_TYPE) {
                                try {
                                    whereClause.append(operator).append(Boolean.parseBoolean((String) value));
                                } catch (NumberFormatException nfe) {
                                }
                            } else if (att.getTypeAsInt() == SchemaAttribute.STRING_TYPE) {
                                whereClause.append(operator).append("\"");
                                if (operator.equals(" =~ ")) {
                                    whereClause.append(".*").append(value).append(".*");
                                } else {
                                    whereClause.append(value);
                                }
                                whereClause.append("\"");
                            }
                        } else if (value instanceof Integer) {
                            whereClause.append(operator).append(((Integer) value).intValue());
                        } else if (value instanceof Float) {
                            whereClause.append(operator).append(((Float) value).floatValue());
                        } else if (value instanceof Boolean) {
                            whereClause.append(operator).append(((Boolean) value).booleanValue());
                        } else if (value instanceof Long) {
                            whereClause.append(operator).append(((Long) value).longValue());
                        }
                        if (Arrays.asList("NOT LIKE", "!=").contains(aqr.getOperator())) {
                            whereClause.append(")");
                        }
                    } else {
                        whereClause.append(" ").append(aqr.getOperator());
                    }
                    if (att.isMultiple()) {
                        whereClause.append(")");
                    }
                }
            }
            if (whereClause.toString().contains(" WHERE"))
                whereClauseKeyWord = " AND";
            pos++;
        }
        query.append(" ").append(whereClause).append(" RETURN n.DB_ID, n._displayName, n.schemaClass");
        Set instances = new HashSet();
        // DEBUG System.out.println(query);
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            Result result = session.run(query.toString());
            while (result.hasNext()) {
                Record res = result.next();
                Long dbId = res.get(0) != Values.NULL ? res.get(0).asLong() : null;
                String dislayName = res.get(1) != Values.NULL ? res.get(1).toString() : null;
                String clsName = res.get(2) != Values.NULL ? res.get(2).asString() : null;
                if (dbId != null) {
                    Instance instance = getInstance(clsName, dbId);
                    instance.setAttributeValue(_displayName, dislayName);
                    instances.add(instance);
                }
            }
            return instances;
        }
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
     * @throws Exception Thrown if the instance has no DB_ID or if unable to update the specified attribute name
     */
    public void updateInstanceAttribute(GKInstance instance, String attributeName, Transaction tx) throws
            Exception {
        if (instance.getDBID() == null) {
            throw (new DBIDNotSetException(instance));
        }
        SchemaAttribute attribute = instance.getSchemClass().getAttribute(attributeName);
        deleteFromDBInstanceAttributeValue(attribute, instance, tx);
        storeAttribute(attribute, instance, tx, true);
    }

    /**
     * updateInstanceAttribute wrapped in transaction. Unstored instances referred
     * by the instance attribute being updated are stored in the same transaction.
     *
     * @param instance      GKInstance containing the updated attribute
     * @param attributeName Name of the attribute to update for the corresponding instance in the database
     * @throws Exception Thrown if the instance has no DB_ID, if unable to update the specified attribute name, or if
     *                   there is problem with the transaction
     */
    public void txUpdateInstanceAttribute(GKInstance instance, String attributeName) throws Exception {
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            Transaction tx = session.beginTransaction();
            updateInstanceAttribute(instance, attributeName, tx);
            tx.commit();
        }
    }

    /**
     * @param instance GKInstance to update in the database
     * @throws Exception Thrown if the instance has no DB_ID, there is a problem updating the instance or its
     *                   referrers in the database, or there is a problem loading attribute values if the instance is in the instance
     *                   cache
     */
    public void updateInstance(GKInstance instance, Transaction tx) throws Exception {
        Long dbID = instance.getDBID();
        if (dbID == null) {
            throw (new DBIDNotSetException(instance));
        }
        // Find all referrers in Neo4J and then update the relevant attribute to point to (the new) instance.
        // N.B. Need to find referrers before removing instance from Neo4J as when the instance is deleted,
        // all relationships to that instance will be lost.
        List<List<Object>> referrers = findReferrers(instance);
        deleteInstanceFromNeo4J(instance.getSchemClass(), dbID, tx);
        // Force-store instance before re-creating all relationships to it
        storeInstance(instance, true, tx, true);
        updateReferrers(referrers, instance, tx);
        // Deflate the cached copy (if any) of the updated instance. This way it
        // will be loaded with new values when they are asked for.
        GKInstance cachedInstance;
        if ((cachedInstance = (GKInstance) instanceCache.get(dbID)) != null) {
            SchemaClass newCls = schema.getClassByName(instance.getSchemClass().getName());
            cachedInstance.setSchemaClass(newCls);
            //cachedInstance.setSchemaClass(instance.getSchemClass());
            cachedInstance.deflate();
            loadInstanceAttributeValues(cachedInstance);
            //loadInstanceAttributeValues(cachedInstance);
            // A bug in schema: DB_ID is Long in GKInstance but Integer in schema
            // Manual reset it
            cachedInstance.setDBID(cachedInstance.getDBID());
        }
    }

    /**
     * updateInstance wrapped in transaction. Unstored instances referred
     * by the instance being updated are stored in the same transaction.
     *
     * @param instance GKInstance to update in the database
     * @throws Exception Thrown if the instance has no DB_ID, there is a problem updating the instance or its
     *                   referrers in the database, there is a problem loading attribute values if the instance is in the instance
     *                   cache, or if there is a problem with the transaction
     */
    public void txUpdateInstance(GKInstance instance) throws Exception {
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            Transaction tx = session.beginTransaction();
            updateInstance(instance, tx);
            tx.commit();
        }
    }

    // Find all referrer instances a
    private List<List<Object>> findReferrers(GKInstance instance) throws Exception {
        List<List<Object>> ret = new ArrayList();
        SchemaClass schemaClass = instance.getSchemClass();
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            for (Iterator ri = schemaClass.getReferers().iterator(); ri.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ri.next();
                SchemaAttribute originalAtt = att.getOriginalAttribute();
                String attName = originalAtt.getName();
                SchemaClass origin = originalAtt.getOrigin();
                StringBuilder stmt = new StringBuilder("MATCH (n:")
                        .append(origin.getName())
                        .append(")-[:").append(attName).append("]->(s:").append(schemaClass.getName())
                        .append("{").append("DB_ID:").append(instance.getDBID()).append("}) ")
                        .append("RETURN n.DB_ID");
                Result result = session.run(stmt.toString());
                if (result.hasNext()) {
                    Long dbID = result.next().get(0).asLong();
                    ret.add(Arrays.asList(dbID, origin.getName(), attName));
                }
            }
            return ret;
        }
    }

    // Find all referrer instances and then replace the previous instance as value of attName with
    // the call argument: instance
    private void updateReferrers(List<List<Object>> referrers, GKInstance instance, Transaction tx) throws
            Exception {
        for (List<Object> rec : referrers) {
            Long dbID = (Long) rec.get(0);
            String originClassName = rec.get(1).toString();
            String attName = rec.get(2).toString();
            GKInstance referrer = (GKInstance) getInstance(originClassName, dbID);
            referrer.setAttributeValue(attName, instance);
            updateInstanceAttribute(referrer, attName, tx);
        }
    }

    /**
     * Store a GKInstance object into the database. The implementation of this method will store
     * all newly created, referred GKInstances by the specified GKInstance if they are not in the
     * database.
     *
     * @param instance GKInstance to store (it might be from the local file system)
     * @return DB_ID of the stored instance
     * @throws Exception Thrown if unable to retrieve attribute values from the instance or if unable to store
     *                   the instance
     */
    public Long txStoreInstance(GKInstance instance) throws Exception {
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            Transaction tx = session.beginTransaction();
            Long dbId = storeInstance(instance, false, tx, true);
            tx.commit();
            return dbId;
        }
    }

    /**
     * Store a GKInstance object into the database. The implementation of this method will store
     * all newly created, referred GKInstances by the specified GKInstance if they are not in the
     * database.
     *
     * @param instance GKInstance to store (it might be from the local file system)
     * @param tx       transaction
     * @return DB_ID of the stored instance
     * @throws Exception Thrown if unable to retrieve attribute values from the instance or if unable to store
     *                   the instance
     */
    public Long storeInstance(GKInstance instance, Transaction tx) throws Exception {
        return storeInstance(instance, false, tx, true);
    }

    /**
     * Store a GKInstance object into the database. The implementation of this method will store
     * all newly created, referred GKInstances by the specified GKInstance if they are not in the
     * database.
     *
     * @param instance   GKInstance to store (it might be from the local file system)
     * @param forceStore when true, store the instance even if already in the database
     * @param tx         transaction
     * @return DB_ID of the stored instance
     * @throws Exception Thrown if unable to retrieve attribute values from the instance or if unable to store
     *                   the instance
     */
    public Long storeInstance(GKInstance instance, boolean forceStore, Transaction tx, boolean recursive) throws
            Exception {
        Long dbID = null;
        if (forceStore) {
            dbID = instance.getDBID();
        } else if ((dbID = instance.getDBID()) != null) {
            return dbID;
        }
        SchemaClass cls = instance.getSchemClass();
        // cls might be from a local Schema copy. Convert it to the db copy.
        cls = schema.getClassByName(cls.getName());
        List<String> classHierarchy = ((List<GKSchemaClass>) cls.getOrderedAncestors()).stream().map((x) -> (x.getName())).collect(Collectors.toList());
        StringBuilder stmt = new StringBuilder();
        if (dbID == null) {
            // Mint new DB_ID
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
        stmt.append("create (n:").append(labels).append("{").append("DB_ID: ").append(dbID).append(", displayName: \"").append(instance.getDisplayName()).append("\"").append(", schemaClass: \"").append(cls.getName()).append("\"").append("}) RETURN n.DB_ID");
        Value result = executeTransaction(stmt.toString(), tx);
        dbID = result.asLong();

        // Store attributes
        for (Iterator ai = instance.getSchemaAttributes().iterator(); ai.hasNext(); ) {
            GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
            storeAttribute(att, instance, tx, recursive);
        }
        return dbID;
    }

    /**
     * Store a list of GKInstance objects that created from the application tool. These
     * objects might refer to each other.
     *
     * @param localInstances a list of GKInstance objects with negative DB_ID
     * @return a list of DB_IDs that are generated from the DB. The sequence of this list
     * is the same as localInstances.
     * @throws Exception Thrown if unable to store any of the local instances
     */
    public java.util.List storeLocalInstances(java.util.List localInstances, Transaction tx) throws Exception {
        if (localInstances == null || localInstances.size() == 0)
            return Collections.EMPTY_LIST;
        java.util.List dbIDs = new ArrayList(localInstances.size());
        for (GKInstance instance : (List<GKInstance>) localInstances) {
            dbIDs.add(storeInstance(instance, true, tx, true));
        }
        return dbIDs;
    }

    /**
     * IMPORTANT: this method updates only those instances in the given collection
     * and not those referred but not contained in the collection. Storage works for both.
     *
     * @param instances Collection of instances to store or update
     * @throws Exception Thrown if:
     *                   -storing and unable to retrieve attribute values from the instance or if unable to store the instance.
     *                   -updating and the instance has no DB_ID, if there is a problem updating the instance or its referrers in the
     *                   database, or there is a problem loading attribute values if the instance is in the instance cache
     *                   -there is a problem with the transaction
     */
    public void txStoreOrUpdate(Collection instances) throws Exception {
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            Transaction tx = session.beginTransaction();
            for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
                GKInstance i = (GKInstance) ii.next();
                if (i.getDBID() == null) {
                    storeInstance(i, tx);
                } else {
                    updateInstance(i, tx);
                }
            }
            tx.commit();
        }
    }

    /**
     * IMPORTANT: this method updates only those instances in the given collection
     * and not those referred but not contained in the collection. Storage works for both.
     *
     * @param instances Collection of instances to store or update
     * @throws Exception Thrown if:
     *                   -storing and unable to retrieve attribute values from the instance or if unable to store the instance.
     *                   -updating and the instance has no DB_ID, if there is a problem updating the instance or its referrers in the
     *                   database, or there is a problem loading attribute values if the instance is in the instance cache
     */
    public void storeOrUpdate(Collection instances, Transaction tx) throws Exception {
        try {
            for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
                GKInstance i = (GKInstance) ii.next();
                if (i.getDBID() == null) {
                    storeInstance(i, tx);
                } else {
                    updateInstance(i, tx);
                }
            }
        } catch (Exception e) {
            throw (e);
        }
    }

    private void storeAttribute(SchemaAttribute att, GKInstance instance, Transaction tx, boolean recursive) throws
            Exception {
        SchemaClass cls = instance.getSchemClass();
        if (att.getName().equals(Schema.DB_ID_NAME)) return;
        List attVals = instance.getAttributeValuesList(att.getName());
        if ((attVals == null) || (attVals.isEmpty())) return;
        List<String> stmts = new ArrayList();
        if (att.isInstanceTypeAttribute()) {
            for (GKInstance attrValInstance : (List<GKInstance>) attVals) {
                Long valDbID;
                if (recursive)
                    valDbID = storeInstance(attrValInstance, tx);
                else
                    valDbID = attrValInstance.getDBID();
                StringBuilder stmt = new StringBuilder("MATCH (n:").append(cls.getName()).append("{").append("DB_ID:").append(instance.getDBID()).append("}) ").append("MATCH (p:").append(attrValInstance.getSchemClass().getName()).append("{").append("DB_ID:").append(valDbID).append("})").append(" CREATE (n)-[:").append(att.getName()).append("]->(p)");
                stmts.add(stmt.toString());
            }
        } else {
            Object value = instance.getAttributeValue(att.getName());
            StringBuilder stmt = new StringBuilder("MATCH (n:").append(cls.getName()).append("{").append("DB_ID:").append(instance.getDBID()).append("}) SET n.").append(att.getName()).append(" = ");
            if (att.getTypeAsInt() == SchemaAttribute.STRING_TYPE) {
                stmt.append("\"").append(value).append("\"");
            } else {
                stmt.append(value);
            }
            stmt.append(" RETURN n.DB_ID");
            stmts.add(stmt.toString());
        }
        for (String stmt : stmts) {
            executeTransaction(stmt, tx);
        }
    }

    private void deleteFromDBInstanceAttributeValue(SchemaAttribute att, GKInstance instance, Transaction tx) throws
            Exception {
        SchemaClass cls = instance.getSchemClass();
        if (att.getName().equals(Schema.DB_ID_NAME)) return;
        List attVals = instance.getAttributeValuesList(att.getName());
        if ((attVals == null) || (attVals.isEmpty())) return;
        if (att.isInstanceTypeAttribute()) {
            StringBuilder stmt = new StringBuilder("MATCH (n:");
            stmt.append(cls.getName()).append("{").append("DB_ID:").append(instance.getDBID()).append("}) ").append("-[r:").append(att.getName()).append("]->() DELETE r");
            executeTransaction(stmt.toString(), tx);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public void loadInstanceReverseAttributeValues(Collection instances, Collection attributes) throws
            Exception {
        for (Iterator ai = attributes.iterator(); ai.hasNext(); ) {
            SchemaAttribute att = (SchemaAttribute) ai.next();
            loadInstanceReverseAttributeValues(instances, att);
        }
    }

    public void loadInstanceReverseAttributeValues(Collection instances, String[] attNames) throws Exception {
        GKSchema s = (GKSchema) getSchema();
        Set attributes = new HashSet();
        for (int i = 0; i < attNames.length; i++) {
            attributes.addAll(s.getOriginalAttributesByName(attNames[i]));
        }
        loadInstanceReverseAttributeValues(instances, attributes);
    }

    /*
     * NOTE: this method may try to load reverse attribute values to instances which do not
     * have this attribute. This is fine, since if there aren't any values, they
     * can't be loaded. However, this also means that instances collection can be
     * heterogenous and the user does not need to worry about that all the instances
     * have the given reverse attribute.
     */
    public void loadInstanceReverseAttributeValues(Collection instances, SchemaAttribute attribute) throws
            Exception {
        if (instances.isEmpty()) {
            return;
        }
        if (!attribute.isInstanceTypeAttribute()) {
            throw new Exception("Attribute " + attribute.getName() + " is not instance type attribute.");
        }
        for (Iterator ii = instances.iterator(); ii.hasNext(); ) {
            GKInstance ins = (GKInstance) ii.next();
            List<GKInstance> attInstances = (List<GKInstance>) ins.getAttributeValuesList(attribute, false);
            for (GKInstance attInstance : attInstances) {
                attInstance.addRefererNoCheck(attribute, ins);
            }
        }
    }

    /**
     * Fetches a Set of instances from db which look identical to the given instance
     * based on the values of defining attributes.
     * The method is recursive i.e. if a value instance of the given instance does
     * not have DB_ID (i.e. it hasn't been stored in db yet) fetchIdenticalInstance is
     * called on this instance. DB_IDs of identical instances to the latter instance are then used
     * to figure out if the given instance is identical so something else.
     *
     * @param instance Instance for which to fetch identical instances
     * @return Set of GKInstances which are identical to the passed instance
     * @throws Exception Thrown if unable to make attribute query request to fetch identical instances from the
     *                   provided instance, if unable to fetch instances using the attribute query request, or if unable to retrieve
     *                   attributes of the instances fetched, using the attribute query request, to use in order to check if they are
     *                   identical instances
     */
    public Set fetchIdenticalInstances(GKInstance instance) throws Exception {
        Set identicals = null;
        if (((GKSchemaClass) instance.getSchemClass()).getDefiningAttributes().size() == 0) {
            if (debug) System.out.println(instance + "\tno defining attributes.");
            return null;
        }
        List<QueryRequest> aqrList = makeAqrs(instance);
        if (aqrList == null) {
            if (debug)
                System.out.println(instance + "\tno matching instances due to unmatching attribute value(s).");
        } else if (aqrList.isEmpty()) {
            if (debug) System.out.println(instance + "\tno defining attribute values.");
        } else {
            identicals = (Set) fetchInstance(aqrList);
            identicals.remove(instance);
            identicals = grepReasonablyIdenticalInstances(instance, identicals);
            if (identicals.isEmpty()) identicals = null;
            if (debug) System.out.println(instance + "\t" + identicals);
        }
        return identicals;
    }

    /**
     * A method for weeding out instances retrieved as identicals but which are not.
     * E.g. a Complex with comonents (A:A:B:B) would also fetch a Complex with
     * components (A:A:B:B:C) as an identical instance (although the reverse is not true).
     * As I don't kwno how to change the sql in a way that this wouldn't be a case it has
     * to be sorted out programmatically.
     *
     * @param instance
     * @return
     * @throws Exception
     */
    private Set grepReasonablyIdenticalInstances(GKInstance instance, Set identicals) throws Exception {
        Set out = new HashSet();
        for (Iterator ii = identicals.iterator(); ii.hasNext(); ) {
            GKInstance identicalInDb = (GKInstance) ii.next();
            if (areReasonablyIdentical(instance, identicalInDb)) out.add(identicalInDb);
        }
        return out;
    }

    /**
     * Returns true if the specified instances have equal number of values in all
     * of their type ALL defining attributes or if there are no type ALL defining
     * attributes.
     *
     * @param instance
     * @param inDb
     * @return
     * @throws Exception
     */
    private boolean areReasonablyIdentical(GKInstance instance, GKInstance inDb) throws Exception {
        GKSchemaClass cls = (GKSchemaClass) instance.getSchemClass();
        Collection attColl = cls.getDefiningAttributes(SchemaAttribute.ALL_DEFINING);
        if (attColl != null) {
            for (Iterator dai = attColl.iterator(); dai.hasNext(); ) {
                SchemaAttribute att = (SchemaAttribute) dai.next();
                String attName = att.getName();
                List vals1 = instance.getAttributeValuesList(attName);
                int count1 = 0;
                if (vals1 != null) count1 = vals1.size();
                List vals2 = inDb.getAttributeValuesList(attName);
                int count2 = 0;
                if (vals2 != null) count2 = vals2.size();
                if (count1 != count2) return false;
            }
        }
        return true;
    }

    private List makeAqrs(GKInstance instance) throws Exception {
        List aqrList = new ArrayList();
        GKSchemaClass cls = (GKSchemaClass) instance.getSchemClass();
        Collection attColl = cls.getDefiningAttributes(SchemaAttribute.ALL_DEFINING);
        //System.out.println("makeAqrs\t" + instance + "\t" + attColl);
        if (attColl != null) {
            for (Iterator ai = attColl.iterator(); ai.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
                List allList = null;
                if (att.isOriginMuliple()) {
                    allList = makeMultiValueTypeALLaqrs(instance, att);
                } else {
                    allList = makeSingleValueTypeALLaqrs(instance, att);
                }
                if (allList == null) {
                    return null;
                } else {
                    aqrList.addAll(allList);
                }
            }
        }
        attColl = cls.getDefiningAttributes(SchemaAttribute.ANY_DEFINING);
        if (attColl != null) {
            for (Iterator ai = attColl.iterator(); ai.hasNext(); ) {
                GKSchemaAttribute att = (GKSchemaAttribute) ai.next();
                List anyList = makeTypeANYaqrs(instance, att);
                if (anyList == null) {
                    return null;
                } else {
                    aqrList.addAll(anyList);
                }
            }
        }
        //Check if all queries have operator 'IS NULL' in which case there's no point
        //in executing the query and hence return empty array.
        int c = 0;
        for (Iterator ali = aqrList.iterator(); ali.hasNext(); ) {
            AttributeQueryRequest aqr = (AttributeQueryRequest) ali.next();
            if (aqr.getOperator().equalsIgnoreCase("IS NULL")) c++;
        }
        if (c == aqrList.size()) aqrList.clear();
        return aqrList;
    }

    private List makeSingleValueTypeALLaqrs(GKInstance instance, GKSchemaAttribute att) throws Exception {
        List aqrList = new ArrayList();
        List values = instance.getAttributeValuesList(att.getName());
        //System.out.println("makeTypeALLaqrs\t" + att + "\t" + values);
        if ((values == null) || (values.isEmpty())) {
            aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "IS NULL", null));
            return aqrList;
        }
        Set seen = new HashSet();
        if (att.getTypeAsInt() == SchemaAttribute.INSTANCE_TYPE) {
            for (Iterator vi = values.iterator(); vi.hasNext(); ) {
                GKInstance valIns = (GKInstance) vi.next();
                Object tmp = null;
                Long valInsDBID = null;
                if ((tmp = valIns.getDBID()) != null) {
                    if (!seen.contains(tmp)) {
                        seen.add(tmp);
                        aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "=", tmp));
                    }
                }
                //This is only relevant if, say, the extracted instances keep their
                //DB_ID as the value of respectively named attribute.
                else if ((tmp = valIns.getAttributeValueNoCheck(Schema.DB_ID_NAME)) != null) {
                    if (!seen.contains(tmp)) {
                        seen.add(tmp);
                        aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "=", tmp));
                    }
                } else if ((tmp = fetchIdenticalInstances(valIns)) != null) {
                    Collection identicals = new ArrayList();
                    for (Iterator ti = ((Set) tmp).iterator(); ti.hasNext(); ) {
                        Object o2 = ti.next();
                        if (!seen.contains(o2)) {
                            seen.add(o2);
                            identicals.add(o2);
                        }
                    }
                    if (!identicals.isEmpty()) {
                        aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "=", identicals));
                    }
                } else {
                    // no identical instance to <i>valIns</i>. Hence there can't be an identical
                    // instance to <i>instance</i>.
                    return null;
                }
            }
        } else {
            for (Iterator vi = values.iterator(); vi.hasNext(); ) {
                Object o = vi.next();
                if (!seen.contains(o)) {
                    seen.add(o);
                    aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "=", o));
                }
            }
        }
        return aqrList;
    }

    private List makeMultiValueTypeALLaqrs(GKInstance instance, GKSchemaAttribute att) throws Exception {
        List aqrList = new ArrayList();
        List values = instance.getAttributeValuesList(att.getName());
        //System.out.println("makeTypeALLaqrs\t" + att + "\t" + values);
        if ((values == null) || (values.isEmpty())) {
            aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "IS NULL", null));
            return aqrList;
        }
        String rootClassName = ((GKSchema) schema).getRootClass().getName();
        Map counter = new HashMap();
        for (Iterator vi = values.iterator(); vi.hasNext(); ) {
            Object value = vi.next();
            Integer count;
            if ((count = (Integer) counter.get(value)) == null) {
                counter.put(value, new Integer(1));
            } else {
                counter.put(value, new Integer(count.intValue() + 1));
            }
        }
        if (att.getTypeAsInt() == SchemaAttribute.INSTANCE_TYPE) {
            for (Iterator vi = counter.keySet().iterator(); vi.hasNext(); ) {
                GKInstance valIns = (GKInstance) vi.next();
                Collection dbIDs = new ArrayList();
                Object tmp = null;
                Long valInsDBID = null;
                if ((valInsDBID = valIns.getDBID()) != null) {
                    dbIDs.add(valInsDBID);
                }
                //This is only relevant if, say, the extracted instances keep their
                //DB_ID as the value of respectively named attribute.
                else if ((valInsDBID = (Long) valIns.getAttributeValueNoCheck(Schema.DB_ID_NAME)) != null) {
                    dbIDs.add(valInsDBID);
                } else if ((tmp = fetchIdenticalInstances(valIns)) != null) {
                    for (Iterator ti = ((Set) tmp).iterator(); ti.hasNext(); ) {
                        GKInstance identicalIns = (GKInstance) ti.next();
                        dbIDs.add(identicalIns.getDBID());
                    }
                }
                if (dbIDs.isEmpty()) {
                    return null;
                }
                Collection matchingDBIDs = fetchDBIDsByAttributeValueCount(att, dbIDs, (Integer) counter.get(valIns));
                if (matchingDBIDs == null) {
                    return null;
                } else {
                    aqrList.add(new AttributeQueryRequest(rootClassName, Schema.DB_ID_NAME, "=", matchingDBIDs));
                }
            }
        } else {
            for (Iterator vi = counter.keySet().iterator(); vi.hasNext(); ) {
                Object value = vi.next();
                Collection tmp = new ArrayList();
                tmp.add(value);
                Collection matchingDBIDs = fetchDBIDsByAttributeValueCount(att, tmp, (Integer) counter.get(value));
                if (matchingDBIDs == null) {
                    return null;
                } else {
                    aqrList.add(new AttributeQueryRequest(rootClassName, Schema.DB_ID_NAME, "=", matchingDBIDs));
                }
            }
        }
        return aqrList;
    }

    /**
     * @param instance
     * @param att
     * @return A "list" of one AttributeQueryRequest. Null indicates that no match can
     * be found.
     */
    private List makeTypeANYaqrs(GKInstance instance, GKSchemaAttribute att) throws Exception {
        List aqrList = new ArrayList();
        List values = instance.getAttributeValuesList(att.getName());
        if ((values == null) || (values.isEmpty())) {
            aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "IS NULL", null));
            return aqrList;
        }
        Set seen = new HashSet();
        boolean hasUnmatchedValues = false;
        if (att.getTypeAsInt() == SchemaAttribute.INSTANCE_TYPE) {
            for (Iterator vi = values.iterator(); vi.hasNext(); ) {
                GKInstance valIns = (GKInstance) vi.next();
                Object tmp = null;
                if ((tmp = valIns.getDBID()) != null) {
                    if (!seen.contains(tmp)) {
                        seen.add(tmp);
                    }
                }
                //This is only relevant if, say, the extracted instances keep their
                //DB_ID as the value of respectively named attribute.
                else if ((tmp = valIns.getAttributeValueNoCheck(Schema.DB_ID_NAME)) != null) {
                    if (!seen.contains(tmp)) {
                        seen.add(tmp);
                    }
                } else if ((tmp = fetchIdenticalInstances(valIns)) != null) {
                    for (Iterator ti = ((Set) tmp).iterator(); ti.hasNext(); ) {
                        Object o2 = ti.next();
                        if (!seen.contains(o2)) {
                            seen.add(o2);
                        }
                    }
                } else {
                    hasUnmatchedValues = true;
                }
            }
        } else {
            for (Iterator vi = values.iterator(); vi.hasNext(); ) {
                Object o = vi.next();
                if (!seen.contains(o)) {
                    seen.add(o);
                }
            }
        }
        if (!seen.isEmpty()) {
            aqrList.add(new AttributeQueryRequest(instance.getSchemClass(), att, "=", seen));
        } else if (hasUnmatchedValues) {
            aqrList = null;
        }
        return aqrList;
    }

    /**
     * This method fetches DB_IDs of instances which have given number
     * (as specified by <i>count</i>) of values (as specified in <i>values</i>)
     * as values of the attribute <i>att</i>.
     * E.g. if att is Complex.hasComponent, count =2 and values = [33,44] then
     * the result will be a collection of DB_IDs of instances like:
     * hasComponent(33,44), hasComponent(33,33), hasComponent(33,44,55) etc, but not
     * hasComponent(33,33,44), hasComponent(33,33,33) or hasComponent(33,55).
     * I know, this is a rather horrible explanation ;-).
     *
     * @param att
     * @param values - Collection of DB_IDs or non-instance attribute values.
     * @param count  - number of times the given values must occur as the values
     *               of the given attribute.
     * @return Collection of (Long) DB_IDs
     * @throws Exception
     */
    public Collection fetchDBIDsByAttributeValueCount(GKSchemaAttribute att, Collection values, Integer count) throws
            Exception {
        SchemaClass origin = att.getOrigin();
        if (!att.isOriginMuliple()) {
            throw (
                    new Exception(
                            "Attribute "
                                    + att
                                    + " is a single-value attribute and hence query by value count does not make sense."));
        }
        if (values.isEmpty()) {
            throw (new Exception("The Collection of values is empty!"));
        }
        List<String> strValues = (List<String>) values.stream().map(i -> i.toString()).collect(Collectors.toList());
        String attName = att.getName();
        StringBuilder query = new StringBuilder("MATCH (n:").append(origin.getName()).append(")");
        if (att.isInstanceTypeAttribute()) {
            query.append("-[:").append(attName).append("]->(a)")
                    .append("WITH a.DB_ID as attrDBID, n.DB_ID as DB_ID, count(*) as cnt WHERE attrDBID IN ")
                    .append("[")
                    .append(StringUtils.join(",", strValues))
                    .append("] ")
                    .append("AND cnt = ").append(count).append(" RETURN DB_ID");
        } else {
            query.append(" WITH n.").append(attName).append(" as attrName, n.DB_ID as DB_ID, count(*) as cnt WHERE (");
            boolean first = true;
            for (Object value : strValues) {
                if (first) first = false;
                else query.append(" OR ");
                query.append("\"").append(value).append("\"").append(" IN attrName");
            }
            query.append(")").append(" AND cnt = ").append(count).append(" RETURN DB_ID");
        }
        Collection<Long> ret = new ArrayList<Long>();
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            while (result.hasNext()) {
                Record record = result.next();
                long dbId = record.get(0).asLong();
                ret.add(dbId);
            }
        }
        if (ret.isEmpty()) {
            return null;
        } else {
            return ret;
        }
    }

    /**
     * Deletes the instance with the supplied DB_ID from the database.  Also deletes the instance
     * from all referrers, in the sense that if the instance is referred to in
     * an attribute of a referrer, it will be removed from that attribute.
     *
     * @param dbID DbId of the GKInstance to delete
     * @throws Exception Thrown if unable to retrieve the instance, attribute values for the instance or to delete the
     *                   instance from the database
     */
    public void deleteByDBID(Long dbID, Transaction tx) throws Exception {
        GKInstance instance = fetchInstance(((GKSchema) schema).getRootClass().getName(), dbID);
        deleteInstance(instance, tx);
    }

    /**
     * Check if a list of DB_IDs exist in the database.
     *
     * @param dbIds DbIds to check if they exist in the database
     * @return true if all DB_IDs in the passed list exist in the database; false otherwise
     * @throws Exception Thrown if there is a problem querying the database for the DB_IDs
     */
    public boolean exist(List dbIds) throws Exception {
        @SuppressWarnings("unchecked") Set<Long> idsInDB = existing(dbIds, true, false);
        // The most likely case.
        if (idsInDB.size() == dbIds.size()) {
            return true;
        }
        ;
        // The list might include duplicates.
        @SuppressWarnings("unchecked") HashSet dbIdsSet = new HashSet(dbIds);

        return idsInDB.size() == dbIdsSet.size();
    }

    /**
     * Returns the DB_IDs from the list which exist in the database.
     *
     * @param dbIds      the db ids to check
     * @param checkCache check cache (before checking the DB) if true
     * @return the db ids which are in the database (or cache - of checkCache is true)
     * @throws Exception Thrown if there is a problem querying the database for the dbIds
     */
    public Set<Long> existing(Collection<Long> dbIds, boolean checkCache, boolean inverse) throws Exception {
        Set<Long> foundDBIds = new HashSet(dbIds.size());
        if (dbIds == null || dbIds.size() == 0)
            return foundDBIds;
        // Check the cache.
        List<Long> needCheckedList = new ArrayList<Long>(dbIds);
        for (Iterator it = needCheckedList.iterator(); it.hasNext(); ) {
            Long dbID = (Long) it.next();
            if (instanceCache.containsKey(dbID)) {
                foundDBIds.add(dbID);
                it.remove();
            }
        }
        if (needCheckedList.size() == 0)
            return foundDBIds;
        // Check the database
        SchemaClass root = ((GKSchema) getSchema()).getRootClass();
        StringBuilder query = new StringBuilder("MATCH (n:").append(root.getName()).append(")");
        if (dbIds != null) {
            if (inverse)
                query.append(" WHERE NOT n.DB_ID IN");
            else
                query.append(" WHERE n.DB_ID IN");
            query.append(dbIds);
        }
        query.append(" RETURN n.DB_ID");
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            while (result.hasNext()) {
                Record record = result.next();
                long dbId = record.get(0).asLong();
                foundDBIds.add(dbId);
            }
        }
        return foundDBIds;
    }

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

    /**
     * deleteInstance wrapped in transaction. Instances referring to the instance
     * being deleted are update in the same transaction.
     *
     * @param instance GKInstance to delete
     * @throws Exception Thrown if unable to retrieve attribute values for the instance, if unable to delete the
     *                   instance from the database or if there is a problem with the transaction
     */
    public void txDeleteInstance(GKInstance instance) throws Exception {
        try (Session session = driver.session(SessionConfig.forDatabase(getDBName()))) {
            Transaction tx = session.beginTransaction();
            deleteInstance(instance, tx);
            tx.commit();
        }
    }

    public String fetchSchemaClassnameByDBID(Long dbID) {
        StringBuilder query = new StringBuilder("MATCH (n) WHERE n.DB_ID=").append(dbID).append(" RETURN n.schemaClass");
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
            if (!att.isInstanceTypeAttribute()) continue;
            List values = instance.getAttributeValuesList(att);
            if (values == null || values.size() == 0) continue;
            for (Iterator it1 = values.iterator(); it1.hasNext(); ) {
                GKInstance references = (GKInstance) it1.next();
                references.clearReferers(); // Just to refresh the whole referers map
            }
        }
    }

    private void deleteInstanceFromNeo4J(SchemaClass cls, Long dbID, Transaction tx) {
        // NB. DETACH DELETE removes the node and all its relationships
        // (but not nodes at the other end of those relationships)
        StringBuilder stmt = new StringBuilder("MATCH (n:").append(cls.getName()).append("{").append("DB_ID:").append(dbID).append("}) DETACH DELETE n");
        executeTransaction(stmt.toString(), tx);
    }

    // Adapted from: https://neo4j.com/docs/java-reference/current/transaction-management/
    private Value executeTransaction(String statement, Transaction tx) {
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

    public Collection fetchInstancesByClass(String className) throws Exception {
        return fetchInstancesByClass(className, null);
    }

    public Collection fetchInstancesByClass(String className, List dbIds) throws Exception {
        Set<Instance> instances = new HashSet();
        ((GKSchema) schema).isValidClassOrThrow(className);
        StringBuilder query = new StringBuilder("MATCH (n:").append(className).append(")");
        if (dbIds != null) {
            query.append(" WHERE n.DB_ID IN").append(dbIds);
        }
        query.append(" RETURN n.DB_ID, n._displayName, n.schemaClass");
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

    /**
     * Fetch instances from a specified class and a list of db ids in that class.
     *
     * @param className Name of class for which to fetch instances
     * @param dbIds     List of dbId values for which to retrieve the corresponding instances
     * @return Collection of GKInstance objects (empty Collection if no dbIds are specified)
     * @throws Exception Thrown if the class name provided is invalid or if unable to retrieve any
     *                   instances from the database
     */
    public Collection fetchInstances(String className, List<Long> dbIds) throws Exception {
        List<Instance> instances = new ArrayList();
        for (Long dbId : dbIds) {
            instances.add(fetchInstance(className, dbId));
        }
        return instances;
    }

    /**
     * Finds the largest DB_ID currently in the database.
     *
     * @return max DB_ID
     */
    public long fetchMaxDbId() {
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run("MATCH(n) RETURN MAX(n.DB_ID)");
            Record record = result.next();
            return record.get(0).asLong();
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
        String query = "MATCH (n) WHERE n.schemaClass is not null RETURN n.schemaClass, count(n.DB_ID)";
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
     * Query the release number. If no release number information stored in the database, a null will
     * returned.
     *
     * @return Reactome release version number
     * @throws Exception Thrown if unable to retrieve the _Release instance from the database or its
     *                   release number attribute
     */
    public Integer getReleaseNumber() throws Exception {
        if (!getSchema().isValidClass(ReactomeJavaConstants._Release))
            return null;
        Collection<?> c = fetchInstancesByClass(ReactomeJavaConstants._Release);
        if (c == null || c.size() == 0)
            return null;
        GKInstance release = (GKInstance) c.iterator().next();
        Integer releaseNumber = (Integer) release.getAttributeValue(ReactomeJavaConstants.releaseNumber);
        return releaseNumber;
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
        StringBuilder query = new StringBuilder("MATCH (n) WHERE n.DB_ID = ").append(dbID).append(" RETURN n.DB_ID");
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
        if (!useInstanceCache || (instance = instanceCache.get(dbID)) == null) {
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

    public List<Map<String, Object>> fetchStableIdentifiersWithDuplicateDBIds() {
        List<Map<String, Object>> ret = new ArrayList();
        StringBuilder query = new StringBuilder("CALL { MATCH (s1: StableIdentifier) with s1.oldIdentifier as oldId, count(s1.DB_ID) as dbIdCnt ")
                .append("where oldId is not null and dbIdCnt > 1 RETURN oldId } ")
                .append("WITH collect(oldId) as dups ")
                .append("CALL { MATCH (s:StableIdentifier) RETURN s } ")
                .append("WITH dups, collect(s) as recs ")
                .append("UNWIND recs as rec ")
                .append("WITH [item in dups WHERE item = rec.oldIdentifier][0] as match, rec ")
                .append("WHERE match is not null ")
                .append("RETURN rec");

        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> map = new HashMap();
                map.put("identifier", record.get(0).get("identifier"));
                map.put(Schema.DB_ID_NAME, record.get(0).get(Schema.DB_ID_NAME));
                map.put("oldIdentifier", record.get(0).get("oldIdentifier"));
                ret.add(map);
            }
            return ret;
        }
    }

    public List<List<Long>> fetchEWASModifications() {
        List<List<Long>> ret = new ArrayList();
        String query = "MATCH (e:EntityWithAccessionedSequence)-[:hasModifiedResidue]->(r) RETURN e.DB_ID,r.DB_ID";
        try (Session session = driver.session(SessionConfig.forDatabase(this.database))) {
            Result result = session.run(query.toString());
            while (result.hasNext()) {
                Record record = result.next();
                ret.add(Arrays.asList(record.get(0).asLong(), record.get(1).asLong()));
            }
            return ret;
        }
    }

    /**
     * If a "true" argument is supplied, instance caching will be switched on.
     * A "false" argument will switch instance caching off.  If you switch it off,
     * you will force the dba to pull instances out of the database every time
     * they are requested, which can be very time consuming, but you will reduce
     * the memory footprint.
     *
     * @param useInstanceCache true if the cache should be used; false otherwise
     */
    public void setUseCache(boolean useInstanceCache) {
        this.useInstanceCache = useInstanceCache;
    }

    /**
     * Returns true if instance caching is active, false if not.
     *
     * @return true if the cache will be used; false otherwise
     */
    public boolean isUseCache() {
        return useInstanceCache;
    }

    /**
     * Method for fetching instances with given DB_IDs
     *
     * @param dbIDs Collection of DB_ID values for which to retrieve corresponding instances
     * @return Collection of instances
     * @throws Exception Thrown if unable to retrieve instances by DB_ID values from the database
     */
    public Collection fetchInstance(Collection<Long> dbIDs) throws Exception {
        return fetchInstanceByAttribute(((GKSchema) schema).getRootClass().getName(), "DB_ID", "=", dbIDs);
    }

    public AttributeQueryRequest createAttributeQueryRequest(String clsName, String attName, String
            operator, Object value) throws InvalidClassException, InvalidAttributeException {
        return new AttributeQueryRequest(clsName, attName, operator, value);
    }

    public AttributeQueryRequest createAttributeQueryRequest(SchemaAttribute attribute, String operator, Object
            value) throws InvalidAttributeException {
        return new AttributeQueryRequest(attribute, operator, value);
    }

    public ReverseAttributeQueryRequest createReverseAttributeQueryRequest(String clsName, String
            attName, String operator, Object value) throws Exception {
        return new ReverseAttributeQueryRequest(clsName, attName, operator, value);
    }

    public ReverseAttributeQueryRequest createReverseAttributeQueryRequest(SchemaClass cls, SchemaAttribute
            att, String operator, Object value) throws InvalidAttributeException {
        return new ReverseAttributeQueryRequest(cls, att, operator, value);
    }

    // Adapted from https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection
    private static Collection<String> getClassNames(final String pack) throws java.io.IOException {
        final StandardJavaFileManager fileManager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);
        return StreamSupport.stream(
                        fileManager.list(StandardLocation.CLASS_PATH, pack, Collections.singleton(JavaFileObject.Kind.CLASS), false).spliterator(),
                        false)
                .map(javaFileObject -> {
                    final String[] split = javaFileObject.getName()
                            .replace(".class", "")
                            .replace(")", "")
                            .split(Pattern.quote(File.separator));
                    String className = split[split.length - 1];
                    if (className.indexOf("$") == -1)
                        // Exclude inner classes
                        return className;
                    return null;
                })
                .filter(x -> x != null).collect(Collectors.toCollection(ArrayList::new));
    }

    public class QueryRequestList extends ArrayList<QueryRequest> {

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

    public class ReverseAttributeQueryRequest extends QueryRequest {

        public ReverseAttributeQueryRequest(SchemaAttribute attribute, String operator, Long dbID) throws InvalidAttributeException {
            cls = attribute.getOrigin();
            //this.attribute = attribute;
            this.attribute = attribute.getOrigin().getAttribute(attribute.getName());
            if (operator == null || operator.equals("")) {
                operator = "=";
            }
            this.operator = operator.toUpperCase();
            this.value = dbID;
        }

        public ReverseAttributeQueryRequest(String clsName, String attName, String operator, Object value) throws Exception {
            schema.isValidClassOrThrow(clsName);
            cls = schema.getClassByName(clsName);
            Collection<GKSchemaAttribute> reverseAttributes = ((GKSchemaClass) cls).getReferersByName(attName);
            Set<GKSchemaClass> origins = new HashSet<GKSchemaClass>();
            for (GKSchemaAttribute revAtt : reverseAttributes) {
                origins.add((GKSchemaClass) revAtt.getOrigin());
            }
            //System.out.println(origins);
            if (origins.size() > 1) {
                throw new Exception("Class '" + clsName + "' has many referers with attribute '" + attName + "' - don't know which one to use. Use ReverseAttributeQueryRequest(SchemaClass cls, " + "SchemaAttribute att, String operator, Object value) to construct this query.");
            }
            attribute = origins.iterator().next().getAttribute(attName);
            if (operator == null || operator.equals("")) {
                operator = "=";
            }
            this.operator = operator.toUpperCase();
            this.value = value;
        }

        /**
         * An overloaded constructor with class and attributes specified so no checking is needed.
         *
         * @param cls      SchemaClass to query
         * @param att      SchemaAttribute to query
         * @param operator Operator to use in query
         * @param value    Attribute value to query
         * @throws InvalidAttributeException Thrown if the SchemaAttribute is invalid
         */
        public ReverseAttributeQueryRequest(SchemaClass cls, SchemaAttribute att, String operator, Object value) throws InvalidAttributeException {
            this.cls = cls;
            attribute = att.getOrigin().getAttribute(att.getName());
            if (operator == null || operator.equals("")) {
                operator = "=";
            }
            this.operator = operator.toUpperCase();
            this.value = value;
        }

    }
}
