package org.gk.reach.model.fries;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Apparently these json ignore definition cannot be inherited from the supclass.
@JsonIgnoreProperties({"passage", "frame-type", "object-meta", "object-type", "section-id", "section-name", "index", "is-title"})
public class Sentence extends FrameObject {
    
    public Sentence() {
    }

}
