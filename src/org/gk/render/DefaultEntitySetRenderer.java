/*
 * Created on Dec 12, 2013
 *
 */
package org.gk.render;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import org.gk.model.InstanceUtilities;

/**
 * @author gwu
 *
 */
public class DefaultEntitySetRenderer extends DefaultProteinRenderer {
    
    public DefaultEntitySetRenderer() {
    }
    
    @Override
    protected void renderShapes(Graphics g) {
        // If node is a drug (if it has the "isForDrug" attribute set to true).
        if (node.isForDrug()) {
            background = DEFAULT_DRUG_FOREGROUND; // As the default for the drugs
            background = DEFAULT_DRUG_BACKGROUND;
            renderDrugLabel(g);
        }
        else {
            background = DEFAULT_COMPLEX_BACKGROUND;
            background = DEFAULT_FOREGROUND;
        }
        Graphics2D g2 = (Graphics2D) g;
        Rectangle bounds = node.getBounds();
        // The following code is used to draw two same size shapes with a litte shift
//        // Draw an extra rectangle
//        g.translate(MULTIMER_RECT_DIST, -MULTIMER_RECT_DIST); // MULTIMER_RECT_DIST = 3
//        renderShapes(bounds, g2);
//        g.translate(-MULTIMER_RECT_DIST, MULTIMER_RECT_DIST);
        // The following code is used to draw two shapes: bigger shape contains smaller one
        Rectangle bounds1 = new Rectangle(bounds);
        bounds1.x -= MULTIMER_RECT_DIST;
        bounds1.y -= MULTIMER_RECT_DIST;
        bounds1.width += 2 * MULTIMER_RECT_DIST;
        bounds1.height += 2 * MULTIMER_RECT_DIST;
        renderShapes(bounds1, g2);
        renderShapes(bounds, g2);
    }
    
}
