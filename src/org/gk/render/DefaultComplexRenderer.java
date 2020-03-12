/*
 * DefaultComplexRenderer.java
 *
 * Created on June 20, 2003, 10:23 PM
 */

package org.gk.render;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
/**
 * This class is for drawing a RenderableComplex.
 * @author  wgm
 */
@SuppressWarnings("serial")
public class DefaultComplexRenderer extends AbstractNodeRenderer {
    
    /** Creates a new instance of DefaultComplexRenderer */
    public DefaultComplexRenderer() {
    }
    
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
    
    protected void renderShapes(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        // Draw three overlapping rectangles
        Stroke oldStroke = g2.getStroke();
        if (isSelected) {
        	g2.setStroke(SELECTION_STROKE);
        }
        else
            g2.setStroke(DEFAULT_THICK_STROKE);
        Rectangle bounds = node.getBounds();
        drawRectangle(bounds, g2, false);
        if (node.getIsForDrug())
            renderDrugLabel(g);
//        Rectangle rect = new Rectangle();
//        rect.x = bounds.x + RECTANGLE_DIST;
//        rect.y = bounds.y + RECTANGLE_DIST;
//        rect.width = bounds.width - 2 * RECTANGLE_DIST;
//        rect.height = bounds.height - 2 * RECTANGLE_DIST;
//        drawRectangle(rect, g2, true);
        g2.setStroke(oldStroke);
    }
    
    protected void drawRectangle(Rectangle rect, 
                                 Graphics2D g2,
                                 boolean brighter) {
        Color bg = background;
        if (bg == null)
            bg = DEFAULT_COMPLEX_BACKGROUND;
        if (brighter)
            bg = bg.brighter();
        g2.setPaint(bg);
        GeneralPath shape = createShape(rect);
        g2.fill(shape);
		setDrawPaintAndStroke(g2);
		g2.draw(shape);
    }
    
    private GeneralPath createShape(Rectangle rect) {
        GeneralPath path = new GeneralPath();
        int x = rect.x + COMPLEX_RECT_ARC_WIDTH;
        int y = rect.y;
        path.moveTo(x, y);
        x = rect.x + rect.width - COMPLEX_RECT_ARC_WIDTH;
        path.lineTo(x, y);
        x = rect.x + rect.width;
        y = rect.y + COMPLEX_RECT_ARC_WIDTH;
        path.lineTo(x, y);
        y = rect.y + rect.height - COMPLEX_RECT_ARC_WIDTH;
        path.lineTo(x, y);
        x = rect.x + rect.width - COMPLEX_RECT_ARC_WIDTH;
        y = rect.y + rect.height;
        path.lineTo(x, y);
        x = rect.x + COMPLEX_RECT_ARC_WIDTH;
        path.lineTo(x, y);
        x = rect.x;
        y = rect.y + rect.height - COMPLEX_RECT_ARC_WIDTH;
        path.lineTo(x, y);
        y = rect.y + COMPLEX_RECT_ARC_WIDTH;
        path.lineTo(x, y);
        path.closePath();
        return path;
    }
    
}
