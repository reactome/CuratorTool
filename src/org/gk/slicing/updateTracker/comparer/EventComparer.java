package org.gk.slicing.updateTracker.comparer;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

import org.gk.slicing.updateTracker.model.Action;
import org.gk.slicing.updateTracker.model.ActionObject;
import org.gk.slicing.updateTracker.model.ActionType;
import org.gk.slicing.updateTracker.matcher.EventMatcher;

import java.util.*;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class EventComparer extends InstanceComparer {
    public EventComparer(EventMatcher eventMatcher) {
        super(eventMatcher);
    }

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentEventPair) throws Exception {
        return getChanges(equivalentEventPair, 0);
    }

    @Override
    protected Set<Action> getInstanceActions(GKInstance earlierEvent, GKInstance newEvent, String... attributeNames)
        throws Exception {

        Set<Action> actions = super.getInstanceActions(earlierEvent, newEvent, attributeNames);
        actions.addAll(getSummationActions(earlierEvent, newEvent));
        return actions;
    }

    @Override
    protected List<?> getAttributeValuesList(GKInstance instance, String attributeName) throws Exception {
        if (attributeName.equals(ReactomeJavaConstants.hasEvent)) {
            return getHasEventAttributeValues(instance);
        }

        return instance.getAttributeValuesList(attributeName);
    }

    @Override
    protected boolean isInstanceTypeAttribute(String attributeName, GKInstance instance)
        throws InvalidAttributeException {

        String validAttributeName = attributeName.equals(ReactomeJavaConstants.hasEvent) ?
            getValidHasEventName(instance) :
            attributeName;

        return instance.getSchemClass().getAttribute(validAttributeName).isInstanceTypeAttribute();
    }

    @Override
    protected boolean isValidAttribute(GKInstance instance, String attributeName) {
        return attributeName.equals(ReactomeJavaConstants.hasEvent) ?
            instance.getSchemClass().isValidAttribute(getValidHasEventName(instance)) :
            instance.getSchemClass().isValidAttribute(attributeName);
    }

    private Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentEventPair, int recursionLevel)
        throws Exception {

        if (equivalentEventPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierEvent = equivalentEventPair.getKey();
        GKInstance newEvent = equivalentEventPair.getValue();

        Set<Action> actions = new TreeSet<>();
        if (bothRLEs(earlierEvent, newEvent)) {
            actions.addAll(getReactionlikeEventActions(earlierEvent, newEvent));
        } else if (bothPathways(earlierEvent, newEvent)) {
            Set<Action> pathwayActions = new TreeSet<>();
            for (GKInstance pathwayChild : getPathwayChildren(newEvent)) {
                Set<Action> childActions = getChanges(getEquivalentEvents(pathwayChild), recursionLevel + 1);
                if (!childActions.isEmpty() && recursionLevel <= 1) {
                    if (isRLE(pathwayChild)) {
                        pathwayActions.add(Action.getAction(ActionType.UPDATE, ActionObject.containedRLE));
                    } else {
                        if (childActions.contains(Action.getAction(ActionType.UPDATE, ActionObject.containedRLE))) {
                            pathwayActions.add(Action.getAction(ActionType.UPDATE, ActionObject.indirectRLE));
                            childActions.remove(Action.getAction(ActionType.UPDATE, ActionObject.containedRLE));
                        }
                        if (childActions.contains(Action.getAction(ActionType.UPDATE, ActionObject.indirectRLE))) {
                            pathwayActions.add(Action.getAction(ActionType.UPDATE, ActionObject.indirectRLE));
                            childActions.remove(Action.getAction(ActionType.UPDATE, ActionObject.indirectRLE));
                        }
                        if (!childActions.isEmpty()){
                            pathwayActions.add(Action.getAction(ActionType.UPDATE, ActionObject.containedPathway));
                        }
                    }
                }
            }
            pathwayActions.addAll(getPathwayEventActions(earlierEvent, newEvent));
            actions.addAll(pathwayActions);
        } else {
            throw new IllegalStateException(earlierEvent + " and " + newEvent + " have different event types");
        }
        return actions;
    }

    private Set<Action> getReactionlikeEventActions(GKInstance earlierRLE, GKInstance newRLE) throws Exception {
        Set<Action> reactionlikeEventActions = new TreeSet<>();

        reactionlikeEventActions.addAll(getRegulationActions(earlierRLE, newRLE));

        reactionlikeEventActions.addAll(
            getInstanceActions(
                earlierRLE,
                newRLE,
                ReactomeJavaConstants.input,
                ReactomeJavaConstants.output,
                ReactomeJavaConstants.catalystActivity,
                ReactomeJavaConstants.literatureReference,
                ReactomeJavaConstants.summation
            )
        );

        return reactionlikeEventActions;
    }

    private Set<Action> getPathwayEventActions(GKInstance earlierPathway, GKInstance newPathway) throws Exception {
        return getInstanceActions(
            earlierPathway,
            newPathway,
            ReactomeJavaConstants.hasEvent,
            ReactomeJavaConstants.literatureReference,
            ReactomeJavaConstants.summation
        );
    }

    private Set<Action> getRegulationActions(GKInstance earlierRLE, GKInstance newRLE) throws Exception {
        if (earlierRLE == null || newRLE == null) {
            return new TreeSet<>();
        }

        List<GKInstance> earlierRegulations = getRegulationInstances(earlierRLE);
        List<GKInstance> newRegulations = getRegulationInstances(newRLE);

        Set<Action> regulationActions = new TreeSet<>();
        //regulationActions.addAll(getActionsForRegulationInstances(earlierRegulations, newRegulations));
        regulationActions.addAll(getActionsForRegulatorsInRegulationInstances(earlierRegulations, newRegulations));
        return regulationActions;
    }

    private List<GKInstance> getRegulationInstances(GKInstance reactionlikeEvent) throws Exception {
        if (reactionlikeEvent.getSchemClass().isValidAttribute(ReactomeJavaConstants.regulatedBy)) {
            return new ArrayList<>(reactionlikeEvent.getAttributeValuesList(ReactomeJavaConstants.regulatedBy));
        }

        return asList(reactionlikeEvent.getReferers(ReactomeJavaConstants.regulatedEntity));
    }

    private List<GKInstance> asList(Collection<GKInstance> instances) {
        if (instances == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(instances);
    }

    private Set<Action> getActionsForRegulationInstances(
        List<GKInstance> earlierRegulations, List<GKInstance> newRegulations) {

        boolean isInstanceType = true;
        return getActionsComparingAttrValues(
            earlierRegulations, newRegulations, ReactomeJavaConstants.regulation, isInstanceType);
    }

    private Set<Action> getActionsForRegulatorsInRegulationInstances(
        List<GKInstance> earlierRegulations, List<GKInstance> newRegulations) throws Exception {

        boolean isInstanceType = true;
        return getActionsComparingAttrValues(
            getRegulators(earlierRegulations),
            getRegulators(newRegulations),
            ReactomeJavaConstants.regulator,
            isInstanceType
        );
    }

    private List<GKInstance> getRegulators(List<GKInstance> regulationInstances) throws Exception {
        List<GKInstance> regulators = new ArrayList<>();
        for (GKInstance regulationInstance : regulationInstances) {
            List<GKInstance> regulatorsFromRegulationInstance =
                regulationInstance.getAttributeValuesList(ReactomeJavaConstants.regulator);
            if (regulatorsFromRegulationInstance != null) {
                regulators.addAll(regulatorsFromRegulationInstance);
            }
        }
        return regulators;
    }

    @SuppressWarnings("unchecked")
    private List<GKInstance> getPathwayChildren(GKInstance pathway) throws Exception {
        return getHasEventAttributeValues(pathway);
    }

    private Map.Entry<GKInstance, GKInstance> getEquivalentEvents(GKInstance newEvent) {
        GKInstance previousEvent = getCurrentToPreviousInstanceMap().get(newEvent);
        if (previousEvent == null) {
            return null;
        }

        return getPreviousToCurrentInstanceMap().entrySet().stream()
            .filter(entry -> entry.getKey().getDBID().equals(previousEvent.getDBID()))
            .findFirst()
            .orElse(null);
    }

    private List<Action> getSummationActions(GKInstance earlierEvent, GKInstance newEvent) throws Exception {
        // No actions b/c no comparison if either event is null
        if (earlierEvent == null || newEvent == null) {
            return new ArrayList<>();
        }

        List<GKInstance> earlierSummations = earlierEvent.getAttributeValuesList(ReactomeJavaConstants.summation);
        List<GKInstance> newSummations = newEvent.getAttributeValuesList(ReactomeJavaConstants.summation);

        Map<GKInstance, GKInstance> matchingEarlyToNewSummations =
            getMatchingInstances(earlierSummations, newSummations);

        return getSummationTextActions(matchingEarlyToNewSummations);
    }

    private boolean bothRLEs(GKInstance earlierEvent, GKInstance newEvent) throws Exception {
        return isRLE(earlierEvent) && isRLE(newEvent);
    }

    private boolean bothPathways(GKInstance earlierEvent, GKInstance newEvent) {
        return isPathway(earlierEvent) && isPathway(newEvent);
    }

    private boolean isPathway(GKInstance instance) {
        return isA(instance, ReactomeJavaConstants.Pathway);
    }

    private boolean isRLE(GKInstance instance) throws Exception {
        if (instance.getDbAdaptor().getSchema() == null) {
            instance.getDbAdaptor().fetchSchema();
        }

        if (instance.getDbAdaptor().getSchema().isValidClass(ReactomeJavaConstants.ReactionlikeEvent)) {
            return isA(instance, ReactomeJavaConstants.ReactionlikeEvent);
        } else {
            return isA(instance, ReactomeJavaConstants.Reaction);
        }
    }

    private boolean isA(GKInstance instance, String className) {
        return instance.getSchemClass().isa(className);
    }

    private Map<GKInstance, GKInstance> getMatchingInstances(
        List<GKInstance> earlierInstances, List<GKInstance> newInstances) {

        Map<GKInstance, GKInstance> matchingInstances = new HashMap<>();
        for (GKInstance newInstance : newInstances) {
            GKInstance earlierInstance = getMatchingValueByDbId(newInstance, earlierInstances);
            if (earlierInstance != null) {
                matchingInstances.put(earlierInstance, newInstance);
            }
        }
        return matchingInstances;
    }

    private List<Action> getSummationTextActions(Map<GKInstance, GKInstance> matchingEarlyToNewSummations)
        throws Exception {

        List<Action> allSummationTextActions = new ArrayList<>();
        for (Map.Entry<GKInstance, GKInstance> summationEntryPair : matchingEarlyToNewSummations.entrySet()) {
            GKInstance earlierSummation = summationEntryPair.getKey();
            GKInstance newSummation = summationEntryPair.getValue();

            allSummationTextActions.addAll(
                getAttributeActions(earlierSummation, newSummation, ReactomeJavaConstants.text)
            );
        }
        return allSummationTextActions;
    }

    @SuppressWarnings("unchecked")
    private List<GKInstance> getHasEventAttributeValues(GKInstance pathway) throws Exception {
        return pathway.getAttributeValuesList(getValidHasEventName(pathway));
    }

    private String getValidHasEventName(GKInstance pathway) {
        return pathway.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasEvent) ?
            ReactomeJavaConstants.hasEvent :
            ReactomeJavaConstants.hasComponent;
    }
}
