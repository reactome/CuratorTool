package org.gk.model;

import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class implements a cache of all values assigned to attributes of classes. The intention behind this cache is
 * to speed-up loading (specifically from Neo4J) of Schema and Event view in CuratorTool.
 * @author datasome
 */
public class AttributeValueCache {
    // Class Name -> Attribute Name -> DB_ID -> List<Value>
    private Map<String, Map<String, Map<Long, List<AttValCacheRecord>>>> cache = new ConcurrentHashMap<>();

    public void clear() {
        cache.clear();
    }

    public Boolean inCacheAlready(String className, String attributeName) {
        return cache.containsKey(className) &&
                cache.get(className).containsKey(attributeName);
    }

    public List<AttValCacheRecord> getValues(String className, String attributeName, Long dbId) {
        if (cache.containsKey(className) &&
                cache.get(className).containsKey(attributeName) &&
                cache.get(className).get(attributeName).containsKey(dbId))
            return cache.get(className).get(attributeName).get(dbId);
        // DEBUG System.out.println(className + " : " + attributeName + " : " + dbId);
        return null;
    }

    public void addInstanceValue(String className, String attributeName, Long dbId, Value value, String valueSchemaClass) {
        addClassAttribute(className, attributeName);
        if (!cache.get(className).get(attributeName).containsKey(dbId)) {
            cache.get(className).get(attributeName).put(dbId, new CopyOnWriteArrayList<AttValCacheRecord>());
        }
        cache.get(className).get(attributeName).get(dbId).add(new AttValCacheRecord(value, valueSchemaClass));
    }

    public void addPrimitiveValue(String className, String attributeName, Long dbId, Value value) {
        addClassAttribute(className, attributeName);
        if (!cache.get(className).get(attributeName).containsKey(dbId)) {
            cache.get(className).get(attributeName).put(dbId, new CopyOnWriteArrayList<AttValCacheRecord>());
        }
        cache.get(className).get(attributeName).get(dbId).add(new AttValCacheRecord(value));
    }

    public void addClassAttribute(String className, String attributeName) {
        if (!cache.containsKey(className)) {
            cache.put(className, new ConcurrentHashMap<String, Map<Long, List<AttValCacheRecord>>>());
        }
        if (!cache.get(className).containsKey(attributeName)) {
            cache.get(className).put(attributeName, new ConcurrentHashMap<Long, List<AttValCacheRecord>>());
        }
    }

    public static class AttValCacheRecord {
        private Value value;
        private String schemaClass;

        public AttValCacheRecord(Value value, String schemaClass) {
            this.value = value;
            this.schemaClass = schemaClass;
        }

        public AttValCacheRecord(Value value) {
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public String getSchemaClass() {
            return schemaClass;
        }
    }
}
