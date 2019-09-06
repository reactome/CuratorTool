/*
 * ReactionSelectionInfo.java
 *
 * Created on June 20, 2003, 2:15 PM
 */

package org.gk.render;

import java.awt.Point;
import java.io.Serializable;

/**
 * This class contains information related to a reaction selection.
 * @author wgm
 */
public class HyperEdgeSelectionInfo implements Serializable {
    Point selectPoint;
    ConnectWidget connectWidget;
    int selectedType; // One of input, helper, and output.
    int selectedBranch;
    
    /** 
     * Creates a new instance of ReactionSelectionInfo 
     */
    public HyperEdgeSelectionInfo() {
    }
    
    /**
     * Get type of HyperEdge selected
     * 
     * @return one of Edge.INPUT, Edge.OUTPUT and Edge.HELPER.
     */
    public int getSelectedType() {
        return selectedType;
    }
    
    /**
     * Get the selected branch of the HyperEdge
     * 
     * @return the index of the selected branch.
     */
    public int getSelectedBranch() {
        return this.selectedBranch;
    }
    
    public void reset() {
        selectPoint = null;
        connectWidget = null;
        selectedType = HyperEdge.NONE;
        selectedBranch = -1;
    }
    
    /**
     * Set the selection point information.
     * 
     * @param p Point to set as selected
     */
    public void setSelectionPoint(Point p) {
        this.selectPoint = p;
    }
    
    /**
     * Get the select point.
     * 
     * @return Point object set as selected
     */
    public Point getSelectPoint() {
        return this.selectPoint;
    }
}
