package org.gk.render;

import java.awt.Graphics;

public class DefaultComplexDrugRenderer extends DefaultComplexRenderer {
    public DefaultComplexDrugRenderer() {

    }

    @Override
    protected void renderShapes(Graphics g) {
        super.renderShapes(g);
        node.setForegroundColor(DEFAULT_DRUG_FOREGROUND); // As the default for the drugs
        node.setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
        renderDrugLabel(g);
    }
}
