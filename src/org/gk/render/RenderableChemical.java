/*
 * Created on Dec 19, 2006
 *
 */
package org.gk.render;

import java.awt.Graphics;



public class RenderableChemical extends Node {
    
    public RenderableChemical() {
    }
    
    public String getType() {
        return "Compound";
    }
    
    @Override
    protected void initBounds(Graphics g) {
        super.initBounds(g);
        // Give it an extra space so that it can contain the text can be encapsulated completely.
        bounds.x -= boundsBuffer;
        bounds.y -= boundsBuffer;
        bounds.width += 2 * boundsBuffer;
        bounds.height += 2 * boundsBuffer;
    }
}
