package org.gk.model;

import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttributeValueCache {
    // Class Name -> Attribute Name -> DB_ID -> List<Value>
    private Map<String, Map<String, Map<Long, List<Value>>>> cache = new ConcurrentHashMap<>();

    public Boolean hasValues(String className, String attributeName) {
        return cache.containsKey(className) &&
                cache.get(className).containsKey(attributeName) &&
                cache.get(className).get(attributeName).keySet().size() > 0;
    }

    public List<Value> getValues(String className, String attributeName, Long dbId) {
        if (cache.containsKey(className) &&
                cache.get(className).containsKey(attributeName) &&
                cache.get(className).get(attributeName).containsKey(dbId))
            return cache.get(className).get(attributeName).get(dbId);
        // DEBUG System.out.println(className + " : " + attributeName + " : " + dbId);
        return null;
    }

    public void addValue(String className, String attributeName, Long dbId, Value value) {
        if (!cache.containsKey(className)) {
            cache.put(className, new ConcurrentHashMap<String, Map<Long, List<Value>>>());
        }
        if (!cache.get(className).containsKey(attributeName)) {
            cache.get(className).put(attributeName, new ConcurrentHashMap<Long, List<Value>>());
        }
        if (!cache.get(className).get(attributeName).containsKey(dbId)) {
            cache.get(className).get(attributeName).put(dbId, new ArrayList<Value>());
        }
        cache.get(className).get(attributeName).get(dbId).add(value);
    }
}
