package org.gk.slicing.updateTracker.model;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class Action implements Comparable<Action> {
    private ActionType actionType;
    private ActionObject actionObject;

    private static Map<ActionType, Map<ActionObject, Action>> actionCache = new HashMap<>();

    /**
     * Returns the Action object representing the change to a physical entity or event (e.g. "addInput",
     * "removeReactionlikeEvent", etc.).  The same combination of action type and action object queried more than once
     * will return a cached Action instance.
     *
     * @param actionType Type of action (e.g. "add", "remove", etc.)
     * @param actionObject Entity for which action occurred (e.g. "input", "output", "catalyst", "reactionlikeevent", etc.)
     */
    public static Action getAction(ActionType actionType, ActionObject actionObject) {
        return actionCache
            .computeIfAbsent(actionType, k -> new HashMap<>())
            .computeIfAbsent(actionObject, k -> new Action(actionType, actionObject));
    }

    private Action(ActionType actionType, ActionObject actionObject) {
        this.actionType = actionType;
        this.actionObject = actionObject;
    }

    /**
     * Returns the type of action (e.g. "add", "remove", etc.)
     * @return Type of action
     */
    public ActionType getActionType() {
        return this.actionType;
    }

    /**
     * Returns the entity for the action occurred
     */
    public ActionObject getActionObject() {
        return this.actionObject;
    }

    /**
     * Returns the String representation of the Action as a combination of its ActionType and ActionObject.
     *
     * For example, an ActionType of "add" and and ActionObject of "catalyst" will return the String "addCatalyst".
     */
    @Override
    public String toString() {
        return this.actionType.toString() + firstLetterUpperCase(this.actionObject.toString());
    }

    private String firstLetterUpperCase(String string) {
        return string.substring(0,1).toUpperCase() + string.substring(1);
    }

    @Override
    public int compareTo(Action action) {
        if (action == null) {
            return 1;
        }

        return this.toString().compareTo(action.toString());
    }
}
