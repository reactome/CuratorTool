package org.gk.persistence;

import org.gk.schema.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

    public ReverseAttributeQueryRequest(Schema schema, String clsName, String attName, String operator, Object value) throws Exception {
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
