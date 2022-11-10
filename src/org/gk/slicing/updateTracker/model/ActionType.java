package org.gk.slicing.updateTracker.model;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public enum ActionType {
    ADD("add"),
    REMOVE("remove"),
    ADD_REMOVE("add_remove"),
    MODIFY("modify"),
    UPDATE("update");

    private String action;

    ActionType(String actionTypeName) {
        this.action = actionTypeName;
    }

    @Override
    public String toString() {
        return this.action;
    }
}
