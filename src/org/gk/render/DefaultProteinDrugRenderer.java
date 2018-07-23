package org.gk.render;

import java.awt.Graphics;

public class DefaultProteinDrugRenderer extends DefaultProteinRenderer {
    
    public DefaultProteinDrugRenderer() {
        
    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        super.renderDrug(g);
    }

}
