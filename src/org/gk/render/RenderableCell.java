package org.gk.render;

import java.awt.Graphics;
import java.awt.Rectangle;

public class RenderableCell extends RenderableComplex {
    
    public RenderableCell() {
        backgroundColor = DEFAULT_CELL_BACKGROUND;
        hideComponents = true; // Default no markers are shown for easy programming
    }
    
    /**
     * Need some extra space for this.
     */
    @Override
    protected void initBounds(Graphics g) {
        validateTextSize(g);
        // Refer to how the internal rectangle is calculated.
        int w = (int) (textBounds.width * CELL_INTERNAL_RECT_OFFSET_RATIO / (CELL_INTERNAL_RECT_OFFSET_RATIO - 2.0d));
        if (w < minWidth) // Make sure it takes minum width for layout in a pathway diagram purpose
            w = minWidth;
        int h = (int)((w / CELL_INTERNAL_RECT_OFFSET_RATIO + textBounds.height) * CELL_INTERNAL_RECT_HEIGHT_RATIO);
        bounds.x = position.x - w / 2;
        bounds.y = position.y - h / 2;
        bounds.width = w;
        bounds.height = h;        
    }

    @Override
    protected void ensureTextInBounds(Rectangle bounds) {
        Rectangle internalRect = calculateInternalRect(bounds);
        // Check the top-left corner.
        // boundsBuffer has been considered in all textbounds related calculations
        if (textBounds.x < internalRect.x + textPadding)
            textBounds.x = internalRect.x + textPadding;
        if (textBounds.y < internalRect.y + textPadding)
            textBounds.y = internalRect.y + textPadding;
        // Check the bottom-right corner
        int diff = textBounds.x + textBounds.width - internalRect.x - internalRect.width + textPadding;
        if (diff > 0)
            textBounds.x -= diff;
        diff = textBounds.y + textBounds.height - internalRect.y - internalRect.height + textPadding;
        if (diff > 0)
            textBounds.y -= diff;
    }
    
    private Rectangle calculateInternalRect(Rectangle rect) {
        Rectangle rtn = new Rectangle();
        // Draw the second rounded rectangle
        double offset = rect.width / CELL_INTERNAL_RECT_OFFSET_RATIO;
        double width = rect.width - 2 * offset;
        double height = rect.height / CELL_INTERNAL_RECT_HEIGHT_RATIO - offset;
        rtn = new Rectangle((int)(rect.x + offset),
                            (int)(rect.y + rect.height / CELL_INTERNAL_RECT_HEIGHT_RATIO),
                            (int) width,
                            (int) height);
        return rtn;
    }
    
    @Override
    protected void setTextPositionFromBounds() {
        if (bounds == null)
            return; // Wait for bounds is setting.
        Rectangle internalRect = calculateInternalRect(bounds);
        if (textBounds == null) {
            // First time. Just get whatever it has.
            textBounds = new Rectangle(internalRect);
        }
        else {
            textBounds.x = internalRect.x + (internalRect.width - textBounds.width) / 2;
            textBounds.y = internalRect.y + (internalRect.height - textBounds.height) / 2;
        }
    }
    
}
