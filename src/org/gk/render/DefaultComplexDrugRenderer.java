package org.gk.render;

import java.awt.Graphics;

public class DefaultComplexDrugRenderer extends DefaultComplexRenderer {
    public DefaultComplexDrugRenderer() {

    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        renderDrugLabel(g);
    }
}
