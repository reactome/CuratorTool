package org.gk.schema;

import org.reactome.server.graph.curator.domain.model.DatabaseObject;
import org.reactome.server.graph.curator.service.helper.AttributeClass;
import org.reactome.server.graph.curator.service.helper.AttributeProperties;
import org.reactome.server.graph.curator.service.util.DatabaseObjectUtils;

import java.lang.reflect.Modifier;
import java.util.*;

public class Neo4JSchemaParser {

    private Map cache = new HashMap();

    public GKSchema parseNeo4JResults(
            Collection<String> classNames)
            throws java.lang.ClassNotFoundException {

        for (String className : classNames) {
            GKSchemaClass s = schemaClassFromCacheOrNew(className);
            /* TODO: The following propertyName's not represented in Neo4J schema:
            - propertyName.equals("category"), distinct property_value
                NOMANUALEDIT
                MANDATORY
                OPTIONAL
                REQUIRED
                e.g. Event:figure	SchemaClassAttribute	category	OPTIONAL	SYMBOL
                TODO: category (of attributes) is not represented in graph-core model - hence I cannot import it from Neo4j
             */
            Class clazz = DatabaseObjectUtils.getClassForName(className);
            if (Modifier.isAbstract(clazz.getModifiers()))
                s.setAbstract(Boolean.TRUE);
            s.setName(className);
            for (String parentName : findParents(className)) {
                GKSchemaClass parentSC = schemaClassFromCacheOrNew(parentName);
                parentSC.setName(parentName);
                s.addSuperClass(parentSC);
            }

            Set<AttributeProperties> attributeProperties = DatabaseObjectUtils.getAttributeTable(className);
            for (AttributeProperties ap : attributeProperties) {
                String parentName = ap.getOrigin().getSimpleName();
                GKSchemaClass parentSC = schemaClassFromCacheOrNew(parentName);
                parentSC.setName(parentName);
                String skaId = parentName+":"+ap.getName();
                GKSchemaAttribute ska = schemaAttributeFromCacheOrNew(skaId);
                ska.setName(ap.getName());
                ska.addSchemaClass(parentSC);

                String cardinality = ap.getCardinality();
                if (cardinality == "1") {
                    ska.setMinCardinality(1);
                    ska.setMaxCardinality(1);
                    ska.setMultiple(false);
                } else if (cardinality == "+") {
                    ska.setMinCardinality(1);
                    ska.setMultiple(true);
                }
                //System.out.println("  " + ap.getName() + " : " + ap.getCardinality() + " : " +
                //        ap.getOrigin().getSimpleName() + " : " + ap.getClass().getSimpleName());
                for (AttributeClass ac : ap.getAttributeClasses()) {
                    // System.out.println("    " + ap.getName() + " :: " + ac.getClass().getSimpleName() + " : "
                    // + ac.getType().getSimpleName());
                    String typeSimpleName = ac.getType().getSimpleName();
                    if (typeSimpleName.equals("String")) {
                        ska.setType(Class.forName("java.lang.String"));
                        ska.setTypeAsInt(SchemaAttribute.STRING_TYPE);
                    } else if (typeSimpleName.equals("Integer")) {
                        ska.setType(Class.forName("java.lang.Integer"));
                        ska.setTypeAsInt(SchemaAttribute.INTEGER_TYPE);
                    } else if (typeSimpleName.equals("Float")) {
                        ska.setType(Class.forName("java.lang.Float"));
                        ska.setTypeAsInt(SchemaAttribute.FLOAT_TYPE);
                    } else if (typeSimpleName.equals("Long")) {
                        ska.setType(Class.forName("java.lang.Long"));
                        ska.setTypeAsInt(SchemaAttribute.LONG_TYPE);
                    } else if (typeSimpleName.equals("Boolean")) {
                        ska.setType(Class.forName("java.lang.Boolean"));
                        ska.setTypeAsInt(SchemaAttribute.BOOLEAN_TYPE);
                    } else {
                        if (!typeSimpleName.equals("InteractionEvent") && !typeSimpleName.equals("HasEvent")) {
                            // TODO: N.B. hasInteraction:InteractionEvent and hasEvent:HasEvent have not been released yet
                            // (hence not in graph.db)
                            GKSchemaClass typeSC = schemaClassFromCacheOrNew(typeSimpleName);
                            typeSC.setName(typeSimpleName);
                            ska.addAllowedClass(typeSC);
                            ska.setType(Class.forName("org.gk.model.Instance"));
                            ska.setTypeAsInt(SchemaAttribute.INSTANCE_TYPE);
                        }
                    }
                }
            }
        }
        GKSchema gks = schemaFromCacheOrNew("Schema");
        gks.setCache(cache);
        gks.initialise();
        return gks;
    }



    private GKSchemaAttribute schemaAttributeFromCacheOrNew(String id) {
        GKSchemaAttribute s;
        if ((s = (GKSchemaAttribute) cache.get(id)) != null) {
            return s;
        } else {
            s = new GKSchemaAttribute(id);
            cache.put(id, s);
            return s;
        }
    }

    private GKSchemaClass schemaClassFromCacheOrNew(String id) {
        GKSchemaClass s;
        if ((s = (GKSchemaClass) cache.get(id)) != null) {
            return s;
        } else {
            s = new GKSchemaClass(id);
            cache.put(id, s);
            return s;
        }
    }

    private GKSchema schemaFromCacheOrNew(String id) {
        GKSchema s;
        if ((s = (GKSchema) cache.get(id)) != null) {
            return s;
        } else {
            s = new GKSchema();
            cache.put(id, s);
            return s;
        }
    }

    private List<String> findParents(String className) throws ClassNotFoundException {
        List<String> parents = new ArrayList<String>();
        String packageName = DatabaseObject.class.getPackage().getName() + ".";
        Class clazz = Class.forName(packageName + className);
        while (clazz != null) {
            clazz = clazz.getSuperclass();
            if (!clazz.equals(Object.class)) {
                parents.add(clazz.getSimpleName());
            } else {
                clazz = null;
            }
        }
        return parents;
    }
}
