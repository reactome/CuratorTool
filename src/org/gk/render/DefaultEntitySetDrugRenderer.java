package org.gk.render;

import java.awt.Graphics;

public class DefaultEntitySetDrugRenderer extends DefaultEntityRenderer {
    public DefaultEntitySetDrugRenderer() {

    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        renderDrugLabel(g);
    }

}
