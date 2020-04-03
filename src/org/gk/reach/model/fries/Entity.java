package org.gk.reach.model.fries;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"frame-type", "object-type"})
public class Entity extends FrameObject {
    
    private String type;
    private List<XRef> xrefs;
    @JsonIdentityReference
    private Sentence sentence;
    @JsonProperty("alt-xrefs")
    private List<XRef> altXrefs;
    private List<Modification> modifications;
    
    public Entity() {
    }

    public List<Modification> getModifications() {
        return modifications;
    }

    public void setModifications(List<Modification> modifications) {
        this.modifications = modifications;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public void addXref(XRef xref) {
        if (xrefs == null)
            xrefs = new ArrayList<>();
        xrefs.add(xref);
    }

    public List<XRef> getXrefs() {
        return xrefs;
    }

    public void setXrefs(List<XRef> xrefs) {
        this.xrefs = xrefs;
    }

    public Sentence getSentence() {
        return sentence;
    }

    public void setSentence(Sentence sentence) {
        this.sentence = sentence;
    }

    public List<XRef> getAltXrefs() {
        return altXrefs;
    }

    public void setAltXrefs(List<XRef> altXrefs) {
        this.altXrefs = altXrefs;
    }
    
}
