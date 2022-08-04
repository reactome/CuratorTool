package org.gk.persistence;

import org.gk.schema.*;

public class AttributeQueryRequest extends QueryRequest {

    public AttributeQueryRequest(Schema schema, String clsName, String attName, String operator, Object value) throws InvalidClassException, InvalidAttributeException {
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
