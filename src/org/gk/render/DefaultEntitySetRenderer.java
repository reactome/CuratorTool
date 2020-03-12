/*
 * Created on Dec 12, 2013
 *
 */
package org.gk.render;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * @author gwu
 *
 */
@SuppressWarnings(value = "serial")
public class DefaultEntitySetRenderer extends DefaultProteinRenderer {
    
    public DefaultEntitySetRenderer() {
    }
    
    @Override
    protected void setProperties(Renderable renderable) {
        super.setProperties(renderable);
        // This should be safe
        if (renderable instanceof Node) {
            Node node = (Node) renderable;
            if (node.getIsForDrug()) { // We will ignore whatever color set there
                background = DEFAULT_DRUG_BACKGROUND;
                foreground = DEFAULT_DRUG_FOREGROUND;
            }
        }
    }
    
    @Override
    protected void renderShapes(Graphics g) {
        if (background == null)
            background = DEFAULT_BACKGROUND;
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
        if (node.getIsForDrug())
            renderDrugLabel(g);
    }
    
}
