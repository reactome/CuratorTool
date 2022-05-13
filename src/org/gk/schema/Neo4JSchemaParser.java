package org.gk.schema;

import org.reactome.server.graph.curator.domain.annotations.ReactomeConstraint;
import org.reactome.server.graph.curator.domain.annotations.ReactomeInstanceDefiningValue;
import org.reactome.server.graph.curator.domain.model.DatabaseObject;
import org.reactome.server.graph.curator.service.helper.AttributeClass;
import org.reactome.server.graph.curator.service.helper.AttributeProperties;
import org.reactome.server.graph.curator.service.util.DatabaseObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class Neo4JSchemaParser {

    private Map cache = new HashMap();
    // className -> (field Name -> Field in the the Java class corresponding to className_
    private Map<String, Map<String, Field>> classNameToFields = new HashMap();

    public GKSchema parseNeo4JResults(
            Collection<String> classNames)
            throws java.lang.ClassNotFoundException {

        for (String className : classNames) {
            GKSchemaClass s = schemaClassFromCacheOrNew(className);
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
                List<String> skaIds = Arrays.asList(parentName + ":" + ap.getName(), ap.getName());
                for (String skaId : skaIds) {
                    GKSchemaAttribute ska = schemaAttributeFromCacheOrNew(skaId);
                    ska.setName(ap.getName());
                    if (skaId.contains(":")) {
                        ska.addSchemaClass(parentSC);
                        setCategoryAndDefiningType(parentName, ska, ap);
                    }

                    String cardinality = ap.getCardinality();
                    if (cardinality == "1") {
                        ska.setMinCardinality(1);
                        ska.setMaxCardinality(1);
                        ska.setMultiple(false);
                    } else if (cardinality == "+") {
                        ska.setMinCardinality(1);
                        ska.setMultiple(true);
                    }

                    // Equivalent of inverse_slots in MySQL Schema
                    if (Arrays.asList("reverseReaction", "orthologousEvent").contains(ap.getName())) {
                        ska.setInverseSchemaAttribute(schemaAttributeFromCacheOrNew(ap.getName()));
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
                            if (!typeSimpleName.equals("InteractionEvent")) {
                                // TODO: N.B. InteractionEvent shows in https://curator.reactome.org/cgi-bin/classbrowser?DB=gk_central&CLASS=DatabaseIdentifier
                                //  but has not been released yet (hence graph-importer does not output it into graph.db)
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
        }
        GKSchema gks = schemaFromCacheOrNew("Schema");
        gks.setCache(cache);
        gks.initialise();
        return gks;
    }

    /**
     * Sets SchemaAttribute category and instance defining value for ap in ska
     *
     * @param className
     * @param ska
     * @param ap
     */
    private void setCategoryAndDefiningType(String className, GKSchemaAttribute ska, AttributeProperties ap)
            throws ClassNotFoundException {
        if (!classNameToFields.containsKey(className)) {
            String parentClazzName = DatabaseObject.class.getPackage().getName() + "." + className;
            Class<?> parentClazz = Class.forName(parentClazzName);
            List<Field> fields = getAllFields(new ArrayList<>(), parentClazz);
            Map<String, Field> fieldName2Field = new HashMap();
            for (Field field : fields) {
                fieldName2Field.put(field.getName(), field);
            }
            classNameToFields.put(className, fieldName2Field);
        }
        Field field = classNameToFields.get(className).get(ap.getName());
        if (field != null) {
            // Set Constraint Category
            Annotation annotation = field.getAnnotation(ReactomeConstraint.class);
            if (annotation != null) {
                String category = ((ReactomeConstraint) annotation).constraint().toString();
                ska.setCategory(category);
            }
            // Set instance defining value
            annotation = field.getAnnotation(ReactomeInstanceDefiningValue.class);
            if (annotation != null) {
                String category = ((ReactomeInstanceDefiningValue) annotation).category().toString();
                if (category.equals("none")) {
                    category = null;
                }
                ska.setDefiningType(category);
            }
        }
    }

    /**
     * Method used to get all fields for given class, event inherited fields
     *
     * @param fields List of fields for storing fields during recursion
     * @param type   Current class
     * @return inherited and declared fields
     */
    private List<Field> getAllFields(List<Field> fields, Class<?> type) {
        fields.addAll(Arrays.asList(type.getDeclaredFields()));
        if (type.getSuperclass() != null && !type.getSuperclass().equals(Object.class)) {
            fields = getAllFields(fields, type.getSuperclass());
        }
        return fields;
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
