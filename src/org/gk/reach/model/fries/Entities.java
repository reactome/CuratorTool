package org.gk.reach.model.fries;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"object-type", "object-meta"})
public class Entities implements Serializable {
    
    @JsonProperty("frames")
    private List<Entity> entities;
    
    public Entities() {
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = entities;
    }

}
