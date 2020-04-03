package org.gk.reach.model.graphql;

import org.gk.reach.ReachUtils;

/**
 * Underscore naming required in {@link ReachUtils#readJsonText(String)}
 * @author stephen
 */
public class Feature {
	
    private String evidence;
    private String feature_type;
    private String modification_type;
    private String position;
    private String site;
    private String to_base;
    
    public Feature() {
        
    }
	
	public String getEvidence() {
		return evidence;
	}
	public void setEvidence(String evidence) {
		this.evidence = evidence;
	}
	public String getFeature_type() {
		return feature_type;
	}
	public void setFeature_type(String feature_type) {
		this.feature_type = feature_type;
	}
	public String getModification_type() {
		return modification_type;
	}
	public void setModification_type(String modification_type) {
		this.modification_type = modification_type;
	}
	public String getPosition() {
		return position;
	}
	public void setPosition(String position) {
		this.position = position;
	}
	public String getSite() {
		return site;
	}
	public void setSite(String site) {
		this.site = site;
	}
	public String getTo_base() {
		return to_base;
	}
	public void setTo_base(String to_base) {
		this.to_base = to_base;
	}
}
