package org.gk.reach.model.graphql;

public class Modification {
    
	private String modification_type;
	private String position;
	
	public Modification() {
	    
	}
	
	public String getModification_type() {
		return modification_type;
	}
	public void setModificationType(String modification_type) {
		this.modification_type = modification_type;
	}
	public String getPosition() {
		return position;
	}
	public void setPosition(String position) {
		this.position = position;
	}
}
