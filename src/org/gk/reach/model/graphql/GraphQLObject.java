package org.gk.reach.model.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("errors")
public class GraphQLObject{
    // Turn off any error handling by this fake field.
    private String errors;
    private Data data;
    
    public GraphQLObject() {
    }
    
    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }
}