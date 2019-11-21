package org.gk.render;

import java.awt.Graphics;

public class DefaultEntitySetDrugRenderer extends DefaultEntitySetRenderer {
    public DefaultEntitySetDrugRenderer() {

    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        node.setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
        node.setForegroundColor(DEFAULT_DRUG_FOREGROUND);
        renderDrugLabel(g);
    }

}
