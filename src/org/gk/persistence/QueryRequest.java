package org.gk.persistence;

import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;

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
