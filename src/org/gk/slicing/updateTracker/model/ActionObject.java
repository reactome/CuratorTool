package org.gk.slicing.updateTracker.model;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public enum ActionObject {
    // Events and Physical Entity
    literatureReference("literatureReference"),
    name("name"),
    summation("summation"),
    text("text"),
    species("species"),

    // Events
    input("input"),
    output("output"),
    catalystActivity("catalystActivity"),
    regulation("regulation"),
    regulator("regulator"),
    hasEvent("hasEvent"),
    containedPathway("ContainedPathway"),
    containedRLE("ContainedRLE"),
    indirectRLE("IndirectRLE"),

    // Physical Entities
    cellType("cellType"),
    disease("disease"),
    compartment("compartment"),
    inferredFrom("inferredFrom"),
    systematicName("systematicName"),

    // Complex
    hasComponent("hasComponent"),
    containedComponent("containedComponent"),
    entityOnOtherCell("entityOnOtherCell"),
    stoichiometryKnown("stoichiometryKnown"),

    // Entity Set
    hasMember("hasMember"),
    hasCandidate("hasCandidate"),
    containedMemberOrCandidate("containedMemberOrCandidate"),

    // EWAS
    endCoordinate("endCoordinate"),
    startCoordinate("startCoordinate"),
    hasModifiedResidue("hasModifiedResidue"),
    referenceEntity("referenceEntity"),

    // Polymer
    repeatedUnit("repeatedUnit"),
    containedRepeatedUnit("containedRepeatedUnit"),
    maxUnitCount("maxUnitCount"),
    minUnitCount("minUnitCount"),

    // Cell
    proteinMarker("proteinMarker"),
    RNAMarker("RNAMarker"),
    markerReference("markerReference"),
    organ("organ"),
    tissue("tissue"),
    tissueLayer("tissueLayer")
    ;

    private String actionEntity;

    ActionObject(String actionEntityName) {
        this.actionEntity = actionEntityName;
    }

    @Override
    public String toString() {
        return this.actionEntity;
    }
}
