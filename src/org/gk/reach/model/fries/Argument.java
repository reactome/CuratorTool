package org.gk.reach.model.fries;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class is used designed as a subclass to FrameObject since it doesn't have Frame-Id.
 * @author wug
 */
@JsonIgnoreProperties({"object-type", "index"})
public class Argument implements Serializable {
    
    private String text;
    @JsonProperty("argument-type")
    private String argumentType; // event or entity
    private String type; // e.g. controlled or controller
    @JsonIdentityReference
    private FrameObject arg;
    
    public Argument() {
        
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getArgumentType() {
        return argumentType;
    }

    public void setArgumentType(String argumentType) {
        this.argumentType = argumentType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FrameObject getArg() {
        return arg;
    }

    public void setArg(FrameObject arg) {
        this.arg = arg;
    }
}
