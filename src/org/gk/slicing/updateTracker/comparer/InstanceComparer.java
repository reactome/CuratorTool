package org.gk.slicing.updateTracker.comparer;

import org.gk.model.GKInstance;
import org.gk.schema.InvalidAttributeException;
import org.gk.slicing.updateTracker.model.Action;
import org.gk.slicing.updateTracker.model.ActionObject;
import org.gk.slicing.updateTracker.model.ActionType;


import java.util.*;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 4/25/2022
 */
public abstract class InstanceComparer {

    public InstanceComparer() {}

    public abstract Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentInstancePair) throws Exception;

    protected Set<Action> getInstanceActions(
        GKInstance earlierInstance, GKInstance newInstance, String... attributeNames) throws Exception {

        // No actions b/c no comparison if either event is null
        if (earlierInstance == null || newInstance == null) {
            return new TreeSet<>();
        }

        Set<Action> actions = new TreeSet<>();
        for (String attributeName : attributeNames) {
            actions.addAll(getAttributeActions(earlierInstance, newInstance, attributeName));
        }

        return actions;
    }

    protected Set<Action> getAttributeActions(
        GKInstance earlierInstance, GKInstance newInstance, String attributeName) throws Exception {

        // No actions b/c no comparison if either instance is null
        if (earlierInstance == null || newInstance == null) {
            return new TreeSet<>();
        }

        // No actions b/c the attribute is not valid for both instances
        if (!isValidAttribute(earlierInstance, attributeName) || !isValidAttribute(newInstance, attributeName)) {
            return new TreeSet<>();
        }

        List<?> earlierAttributeValues = getAttributeValuesList(earlierInstance, attributeName);
        List<?> newAttributeValues = getAttributeValuesList(newInstance, attributeName);

        return getActionsComparingAttrValues(
            earlierAttributeValues, newAttributeValues, attributeName, isInstanceTypeAttribute(attributeName, newInstance)
        );
    }

    protected Set<Action> getActionsComparingAttrValues(List<?> earlierAttributeValues, List<?> newAttributeValues,
                                                        String attributeName, boolean isInstanceType) {
        // No actions if no attribute values to compare
        if (earlierAttributeValues.isEmpty() && newAttributeValues.isEmpty()) {
            return new TreeSet<>();
        }

        boolean attributeValuesAdded = firstListHasUniqueValue(newAttributeValues, earlierAttributeValues);
        boolean attributeValuesRemoved = firstListHasUniqueValue(earlierAttributeValues, newAttributeValues);

        Set<Action> actions = new TreeSet<>();
        if (attributeValuesAdded && attributeValuesRemoved) {
            if (isInstanceType) {
                actions.add(Action.getAction(ActionType.ADD_REMOVE, ActionObject.valueOf(attributeName)));
            } else {
                actions.add(Action.getAction(ActionType.MODIFY, ActionObject.valueOf(attributeName)));
            }
        } else if (attributeValuesAdded) {
            actions.add(Action.getAction(ActionType.ADD, ActionObject.valueOf(attributeName)));
        } else if (attributeValuesRemoved) {
            actions.add(Action.getAction(ActionType.REMOVE, ActionObject.valueOf(attributeName)));
        }
        return actions;
    }

    protected boolean isValidAttribute(GKInstance instance, String attributeName) {
        return instance.getSchemClass().isValidAttribute(attributeName);
    }

    protected List<?> getAttributeValuesList(GKInstance instance, String attributeName) throws Exception {
        return instance.getAttributeValuesList(attributeName);
    }

    protected boolean isInstanceTypeAttribute(String attributeName, GKInstance instance)
        throws InvalidAttributeException {

        return instance.getSchemClass().getAttribute(attributeName).isInstanceTypeAttribute();
    }

    protected GKInstance getMatchingValueByDbId(GKInstance valueToMatch, List<?> listToSearch) {
        for (Object searchItem : listToSearch) {
            GKInstance searchItemAsGKInstance = (GKInstance) searchItem;
            if (searchItemAsGKInstance.getDBID().equals(valueToMatch.getDBID())) {
                return searchItemAsGKInstance;
            }
        }
        return null;
    }

    private boolean firstListHasUniqueValue(List<?> firstList, List<?> secondList) {
        for (Object firstListElement : firstList) {
            if (getMatchingValue(firstListElement, secondList) == null) {
                return true;
            }
        }
        return false;
    }

    private Object getMatchingValue(Object valueToMatch, List<?> listToSearch) {
        if (listToSearch == null || listToSearch.isEmpty()) {
            return null;
        }

        if (valueToMatch instanceof GKInstance) {
            return getMatchingValueByDbId((GKInstance) valueToMatch, listToSearch);
        } else {
            return listToSearch
                .stream()
                .filter(searchItem -> searchItem.equals(valueToMatch))
                .findFirst()
                .orElse(null);
        }
    }
}
