package org.gk.reach.model.graphql;

import java.util.List;

import org.gk.reach.ReachUtils;

/**
 * Underscore naming required in {@link ReachUtils#readJsonText(String)}
 * @author stephen
 */
public class Participant {
    
    private String entity_text;
    private String entity_type;
    private List<Feature> features;
    private String identifier;
    // This seems always true. Don't know what it means.
    private Boolean in_model;
    
    public Participant() {
        
    }
	
    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    public String getEntity_text() {
		return entity_text;
	}
	public void setEntity_text(String entity_text) {
		this.entity_text = entity_text;
	}
	public String getEntity_type() {
		return entity_type;
	}
	public void setEntity_type(String entity_type) {
		this.entity_type = entity_type;
	}
	public String getIdentifier() {
		return identifier;
	}
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
	public Boolean getIn_model() {
		return in_model;
	}
	public void setIn_model(Boolean in_model) {
		this.in_model = in_model;
	}
}
