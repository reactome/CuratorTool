package org.gk.reach.model.fries;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"object-type", "object-meta"})
public class FrameCollection<T extends FrameObject> implements Serializable {

    @JsonProperty("frames")
    private List<T> frameObjects;
    
    public FrameCollection() {
    }
    
    public void addFrameObject(T fo) {
        if (frameObjects == null)
            frameObjects = new ArrayList<T>();
        frameObjects.add(fo);
    }

    public List<T> getFrameObjects() {
        return frameObjects;
    }

    public void setFrameObjects(List<T> frameObjects) {
        this.frameObjects = frameObjects;
    }
    
}
