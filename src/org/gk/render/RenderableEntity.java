/*
 * RenderableEntity.java
 *
 * Created on June 12, 2003, 2:33 PM
 */

package org.gk.render;


/**
 * This class describes a renderable Entity class. The rendered Entity is embedded in the class.
 * @author  wgm
 */
public class RenderableEntity extends Node {
	
	public RenderableEntity() {
		super();
	}

	public RenderableEntity(String displayName) {
		super(displayName);
	}

	public String getType() {
		return "Entity";
	}
}