package org.gk.slicing.updateTracker.model;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public enum ActionObject {
    input("input"),
    output("output"),
    catalystActivity("catalystActivity"),
    regulation("regulation"),
    regulator("regulator"),
    literatureReference("literatureReference"),
    summation("summation"),
    text("text"),
    hasEvent("hasEvent"),
    containedPathway("ContainedPathway"),
    containedRLE("ContainedRLE"),
    indirectRLE("IndirectRLE");

    private String actionEntity;

    ActionObject(String actionEntityName) {
        this.actionEntity = actionEntityName;
    }

    @Override
    public String toString() {
        return this.actionEntity;
    }
}
