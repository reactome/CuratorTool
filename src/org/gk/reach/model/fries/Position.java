package org.gk.reach.model.fries;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"object-type", "reference"})
public class Position implements Serializable {
    
    private int offset;
    
    public Position() {
        
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

}
