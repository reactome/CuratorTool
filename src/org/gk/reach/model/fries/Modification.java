package org.gk.reach.model.fries;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("object-type")
public class Modification implements Serializable {
    
    private String type;
    private Boolean negated;
    private String evidence;
    // Need examples about the format. The current implementation is based on
    // the groovy code, FrextFormatter.groovy. 
    private String site; 
    
    public Modification() {
    }
    
    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public Boolean getNegated() {
        return negated;
    }
    public void setNegated(Boolean negated) {
        this.negated = negated;
    }
}
