package org.gk.render;

import java.awt.Graphics;

public class DefaultRNADrugRenderer extends DefaultRNARenderer {
    
    public DefaultRNADrugRenderer() {
        
    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        super.renderDrugLabel(g);
    }
}