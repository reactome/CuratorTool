package org.gk.render;

import java.awt.Graphics;

public class DefaultChemicalDrugRenderer extends DefaultChemicalRenderer {
    
    public DefaultChemicalDrugRenderer() {

    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        renderDrugLabel(g, RENDER_SHAPE.OVAL);
    }
}
