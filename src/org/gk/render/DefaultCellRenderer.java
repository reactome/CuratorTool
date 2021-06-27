package org.gk.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

public class DefaultCellRenderer extends DefaultComplexRenderer {
    
    public DefaultCellRenderer() {
    }
    
    @Override
    protected void drawRectangle(Rectangle rect, 
                                 Graphics2D g2,
                                 boolean brighter) { // brighter is not used.
        RoundRectangle2D rounded = new RoundRectangle2D.Double();
        rounded.setRoundRect(rect.x,
                             rect.y,
                             rect.width, 
                             rect.height,
                             rect.width / 10.0d,
                             rect.height / 5.0d);
        drawRoundedRect(g2, rounded, false);
        // Draw the second rounded rectangle
        double offset = rect.width / CELL_INTERNAL_RECT_OFFSET_RATIO; // 30.0d is manually selected.
        double width = rect.width - 2 * offset;
        double height = rect.height / CELL_INTERNAL_RECT_HEIGHT_RATIO - offset;
        rounded.setRoundRect(rect.x + offset,
                             rect.y + rect.height / CELL_INTERNAL_RECT_HEIGHT_RATIO,
                             width, 
                             height, 
                             width / 10.0d, 
                             height / 5.0d);
        drawRoundedRect(g2, rounded, true);
        // Draw two elipses
        int x = (int) (rect.width / 4.0d + rect.x);
        int y = (int) (rect.y + rect.height / CELL_INTERNAL_RECT_HEIGHT_RATIO * 0.5d);
        width = rect.width / 4.0d;
        height = rect.height / CELL_INTERNAL_RECT_HEIGHT_RATIO * 0.5d;
        drawElipse(x, y, (int)width, (int)height, g2);
        x = (int) (rect.x + rect.width / 4.0d * 3.0d);
        drawElipse(x, y, (int)width, (int)height, g2);
    }
    
    private void drawElipse(int x, int y, int width, int height, Graphics2D g2) {
        Color bg = DEFAULT_CELL_INTERNAL_BACKGROUND;
        g2.setPaint(bg);
        x -= width / 2;
        y -= height / 2;
        g2.fillOval(x, y, width, height);
        setDrawPaintAndStroke(g2);
        g2.drawOval(x, y, width, height);
    }

    private void drawRoundedRect(Graphics2D g2, RoundRectangle2D rounded, boolean isInternal) {
        Color bg = background;
        if (bg == null)
            bg = DEFAULT_CELL_BACKGROUND;
        if (isInternal) {
            bg = DEFAULT_CELL_INTERNAL_BACKGROUND;
        }
        g2.setPaint(bg);
        g2.fill(rounded);
        setDrawPaintAndStroke(g2);
        g2.draw(rounded);
    }

}
