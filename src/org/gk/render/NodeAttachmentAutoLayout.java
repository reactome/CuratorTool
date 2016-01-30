/*
 * Created on Jan 26, 2016
 *
 */
package org.gk.render;

import java.awt.Rectangle;
import java.util.List;

/**
 * A simple algorithm to layout NodeAttachment objects evenly on a Node
 * provided by its bounding information.
 * @author gwu
 *
 */
public class NodeAttachmentAutoLayout {
    private final double BUFFER = 10.0d;
    
    /**
     * Default constructor.
     */
    public NodeAttachmentAutoLayout() {
    }
    
    public void layout(List<NodeAttachment> attachments,
                       int w,
                       int h) {
        if (attachments == null)
            return; // No need to do anything
//        if (attachments.size() < 5)
//            radialLayout(attachments, w, h);
//        else
            simpleLayout(attachments, w, h); // As suggested by Steve, arrange all modifications from top-left corner.
    }
    
    private double calculateBuffer(List<NodeAttachment> attachments,
                                   int w,
                                   int h,
                                   double space) {
        double buffer = BUFFER * 2.0d; // Give extra pixels to four corners
        while (true) {
            buffer /= 2.0d;
            if (buffer < 1.0d)
                break;
            double totalLength = space * attachments.size();
            for (NodeAttachment att : attachments) {
                Rectangle bounds = att.getBounds();
                if (bounds != null)
                    totalLength += bounds.getWidth();
            }
            double nodeLength = 2.0d * (w + h) - 8 * buffer;
            if (nodeLength >= totalLength)
                return buffer;
        }
        return 0.0d;
    }
    
    /**
     * Just layout one by one starting from top-left corner.
     * @param attachments
     * @param w
     * @param h
     */
    private void simpleLayout(List<NodeAttachment> attachments,
                              int w,
                              int h) {
        if (attachments == null || attachments.size() == 0)
            return;
        double space = 4.0d;
        double buffer = calculateBuffer(attachments, w, h, space);
        if (buffer < BUFFER) {
            edgeLayout(attachments, w, h, buffer);
            return;
        }
        // Start from top-right corner
        double prevLength = BUFFER;
        int phase = 0; // Four cases: 0, north; 1: east, 2: south, 3: west
        int oldPhase = phase;
        for (int i = 0; i < attachments.size(); i++) {
            NodeAttachment attachment = attachments.get(i);
            Rectangle bounds = attachment.getBounds();
            oldPhase = phase;
            phase = determinePhase(phase, 
                                   prevLength, 
                                   bounds,
                                   w,
                                   h, 
                                   buffer);
            if (oldPhase != phase)
                prevLength = buffer; // Start a new phase
            calculatePosition(attachment, 
                              bounds, 
                              phase,
                              prevLength,
                              w,
                              h);
            if (phase == 0 || phase == 2)
                prevLength += (bounds.getWidth() + space);
            else
                prevLength += (bounds.getHeight() + space);
        }
    }
    
    private void calculatePosition(NodeAttachment attachment,
                                   Rectangle bounds,
                                   int phase,
                                   double prevLength,
                                   int w,
                                   int h) {
        double x, y;
        switch (phase) {
            case 0 :
                x = (prevLength + bounds.getWidth() / 2.0d) / w;
                y = 0.0d;
                break;
            case 1 :
                x = 1.0d;
                y = (prevLength + bounds.getHeight() / 2.0d) / h;
                break;
            case 2 :
                x = (prevLength + bounds.getWidth() / 2.0d); // Calculate from right to left
                x = (w - x) / w;
                y = 1.0d;
                break;
            default : // Case 3
                x = 0.0d;
                y = (prevLength + bounds.getHeight() / 2.0d); // From bottom to up
                y = (h - y) / h; 
        }
        attachment.setRelativePosition(x, y);
    }
    
    private int determinePhase(int currentPhase,
                               double prevLength,
                               Rectangle bounds,
                               int w,
                               int h,
                               double buffer) {
        switch (currentPhase) {
            case 0 : case 2 :
                if (prevLength + bounds.getWidth() > w - buffer) 
                    return ++currentPhase;
                else
                    return currentPhase;
            case 1 :
                if (prevLength + bounds.getHeight() > h - buffer)
                    return 2;
                else
                    return 1;
            default : 
                return currentPhase; // Default
        }
    }
    
    /**
     * Layout evenly around the edges. The implementation here needs a little bit more work
     * for attachments around the corner which may not be nice.
     * @param attachments
     * @param w
     * @param h
     */
    private void edgeLayout(List<NodeAttachment> attachments,
                           int w, 
                           int h,
                           double buffer) {
        if (attachments == null || attachments.size() == 0)
            return;
        double length = 2.0 * (w + h) - 8 * buffer;
        double step = length / attachments.size(); // Give it extra space
        for (int i = 0; i < attachments.size(); i++) {
            NodeAttachment attachment = attachments.get(i);
            double tmp = step * i;
            if (tmp < w - buffer) // Give it an extra space so that it will not occupy the corner position
                attachment.setRelativePosition((tmp + buffer)/ w, 0d);
            else if (tmp < (w + h - 3 * buffer))
                attachment.setRelativePosition(1.0d, (tmp - w + 3 * buffer) / h);
            else if (tmp < (2 * w + h - 5 * buffer))
                attachment.setRelativePosition((w - (tmp - w - h + 5 * buffer)) / w, 1.0d);
            else
                attachment.setRelativePosition(0.0d, (h - (tmp - 2.0 * w - h + 7 * buffer)) / h);
        }
    }
    
    /**
     * Layout a list of attachments nicely in a rectangle specified by width and height.
     * @param attachments
     * @param w width
     * @param h height
     */
    private void radialLayout(List<NodeAttachment> attachments,
                             int w,
                             int h) {
        // If there is only one attachment, for sure we don't need to do anything.
        if (attachments == null || attachments.size() == 0)
            return; // Nothing needs to be done
        // Using a circle and evenly divide it into multiple angles to calculate 
        // the relative coordinates for each attachment.
        double step = 2.0d * Math.PI / attachments.size();
        for (int i = 0; i < attachments.size(); i ++) {
            NodeAttachment attachment = attachments.get(i);
            double alpha = (i + 1) * step; // Start with 40 degree to avoid interfering the edge connection
            calculatePosition(attachment, alpha, w, h);
        }
    }
    
    private void calculatePosition(NodeAttachment attachment,
                                   double alpha,
                                   int w,
                                   int h) {
        double tan = Math.tan(alpha);
        // Use the center of the rectangle as the origin of the coordinate system
        // Assume the position should be in one of two horizontal edges
        double x, y; // the coordinate relate to the origin
        if (alpha < Math.PI)
            y = h / 2.0d;
        else
            y = - h / 2.0d;
        // Calculate x
        x = y / tan;
        if (Math.abs(x) > w / 2.0d) {
            // The position should be in one of two vertical edges
            if (alpha < Math.PI * 0.5d || alpha > 1.5d * Math.PI)
                x = w / 2.0d;
            else
                x = - w / 2.0d;
            y = x * tan;
        }
        // Shift the origin to top-left corner
        x += w / 2.0d;
        y = h / 2.0d - y;
        // We need to have relative coordinates
        attachment.setRelativePosition(x / w, y / h);
    }
    
}
