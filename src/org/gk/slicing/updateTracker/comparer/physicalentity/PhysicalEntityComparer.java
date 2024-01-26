package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.slicing.updateTracker.comparer.InstanceComparer;
import org.gk.slicing.updateTracker.model.Action;
import org.gk.slicing.updateTracker.model.ActionObject;
import org.gk.slicing.updateTracker.model.ActionType;

import java.util.*;

import static org.gk.slicing.updateTracker.comparer.physicalentity.Utils.bothComplexes;
import static org.gk.slicing.updateTracker.comparer.physicalentity.Utils.bothPolymers;
import static org.gk.slicing.updateTracker.comparer.physicalentity.Utils.bothSets;


/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 4/25/2022
 */
public abstract class PhysicalEntityComparer extends InstanceComparer {

    public PhysicalEntityComparer() {
        super();
    }

    protected Set<Action> getCommonPhysicalEntityActions(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair)
        throws Exception {

        if (equivalentPhysicalEntityPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierPhysicalEntity = equivalentPhysicalEntityPair.getKey();
        GKInstance newPhysicalEntity = equivalentPhysicalEntityPair.getValue();

        Set<Action> physicalEntityActions = new TreeSet<>();

        physicalEntityActions.addAll(
            getInstanceActions(
                earlierPhysicalEntity,
                newPhysicalEntity,
                ReactomeJavaConstants.cellType,
                ReactomeJavaConstants.disease,
                ReactomeJavaConstants.literatureReference,
                ReactomeJavaConstants.name,
                ReactomeJavaConstants.summation,
                "systematicName"
            )
        );
        return physicalEntityActions;
    }

    private Map<GKInstance, GKInstance> getMatchingPhysicalEntityUnits(
        Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair, String attributeForUnit) {

        Map<GKInstance, GKInstance> matchingPhysicalEntityUnits = new HashMap<>();

        GKInstance earlierPhysicalEntity = equivalentPhysicalEntityPair.getKey();
        GKInstance newPhysicalEntity = equivalentPhysicalEntityPair.getValue();

        List<GKInstance> newPhysicalEntityUnits = getPhysicalEntityUnits(newPhysicalEntity, attributeForUnit);

        for (GKInstance earlierPhysicalEntityUnit : getPhysicalEntityUnits(earlierPhysicalEntity, attributeForUnit)) {
            GKInstance newPhysicalEntityUnit =
                getMatchingInstanceInList(earlierPhysicalEntityUnit, newPhysicalEntityUnits);
            if (newPhysicalEntityUnit != null) {
                matchingPhysicalEntityUnits.put(earlierPhysicalEntityUnit, newPhysicalEntityUnit);
            }
        }

        return matchingPhysicalEntityUnits;
    }

    @SuppressWarnings("unchecked")
    private List<GKInstance> getPhysicalEntityUnits(GKInstance physicalEntity, String attributeForUnit) {
        try {
            return physicalEntity.getAttributeValuesList(attributeForUnit);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get physical entity units from " +  physicalEntity, e);
        }
    }

    protected Set<Action> getDirectPhysicalEntityActions(
        Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair, String attribute) throws Exception {

        GKInstance earlierPhysicalEntity = equivalentPhysicalEntityPair.getKey();
        GKInstance newPhysicalEntity = equivalentPhysicalEntityPair.getValue();

        if (earlierPhysicalEntity == null || newPhysicalEntity == null ||
            !isValidAttribute(earlierPhysicalEntity, attribute) ||
            !isValidAttribute(newPhysicalEntity, attribute)) {
            return new TreeSet<>();
        }

        Set<Action> directPhysicalEntityActions = new TreeSet<>();
        directPhysicalEntityActions.addAll(
            getInstanceActions(
                earlierPhysicalEntity,
                newPhysicalEntity,
                attribute
            )
        );
        return directPhysicalEntityActions;
    }

    protected Set<Action> getIndirectPhysicalEntityActions(
        Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair, String attribute)
        throws Exception {

        if (instancePairHasNullOrInvalidInstance(equivalentPhysicalEntityPair, attribute)) {
            return new TreeSet<>();
        }

        Set<Action> indirectPhysicalEntityActions = new TreeSet<>();


        Set<Map.Entry<GKInstance, GKInstance>> matchingPhysicalEntityUnitEntries =
            getMatchingPhysicalEntityUnits(equivalentPhysicalEntityPair, attribute).entrySet();
        for (Map.Entry<GKInstance,GKInstance> matchingPhysicalEntityUnits : matchingPhysicalEntityUnitEntries) {
            if (!areSingleEntities(matchingPhysicalEntityUnits)) {
                Set<Action> childActions = PhysicalEntityComparerFactory.create(matchingPhysicalEntityUnits)
                    .getChanges(matchingPhysicalEntityUnits);
                if (!childActions.isEmpty()) {
                    //Filter for "component", "member", "candidate", "repeatedUnit"
                    indirectPhysicalEntityActions.add(
                        Action.getAction(ActionType.UPDATE, getIndirectActionObject(equivalentPhysicalEntityPair)));
                }
            }

            if (areEWASs(matchingPhysicalEntityUnits)) {
                Set<Action> childActions = PhysicalEntityComparerFactory.create(matchingPhysicalEntityUnits)
                    .getChanges(matchingPhysicalEntityUnits);

                if (childActions.stream()
                    .anyMatch(action -> action.getActionObject().equals(ActionObject.referenceEntity))) {
                    indirectPhysicalEntityActions.add(
                        Action.getAction(ActionType.UPDATE, getIndirectActionObject(equivalentPhysicalEntityPair))
                    );
                }
            }
        }

        return indirectPhysicalEntityActions;
    }

    private boolean instancePairHasNullOrInvalidInstance(
        Map.Entry<GKInstance, GKInstance> equivalentInstancePair, String requiredAttribute) {

        GKInstance earlierInstance = equivalentInstancePair.getKey();
        GKInstance newInstance = equivalentInstancePair.getValue();

        return (earlierInstance == null || newInstance == null ||
            !isValidAttribute(earlierInstance, requiredAttribute) ||
            !isValidAttribute(newInstance, requiredAttribute));
    }

    private boolean areSingleEntities(Map.Entry<GKInstance, GKInstance> subunitPhysicalEntities) {
        return isSingleEntity(subunitPhysicalEntities.getKey()) && isSingleEntity(subunitPhysicalEntities.getValue());
    }

    private boolean isSingleEntity(GKInstance subunitPhysicalEntity) {
        return !subunitPhysicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) &&
            !subunitPhysicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer) &&
            !subunitPhysicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet);
    }

    private boolean areEWASs(Map.Entry<GKInstance, GKInstance> subunitPhysicalEntities) {
        return isEWAS(subunitPhysicalEntities.getKey()) && isEWAS(subunitPhysicalEntities.getValue());
    }

    private boolean isEWAS(GKInstance subunitPhysicalEntity) {
        return subunitPhysicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence);
    }

    private ActionObject getIndirectActionObject(Map.Entry<GKInstance, GKInstance> parentPhysicalEntityPair) {
        if (bothComplexes(parentPhysicalEntityPair)) {
            return ActionObject.containedComponent;
        } else if (bothSets(parentPhysicalEntityPair)) {
            return ActionObject.containedMemberOrCandidate;
        } else if (bothPolymers(parentPhysicalEntityPair)) {
            return ActionObject.containedRepeatedUnit;
        } else {
            throw new RuntimeException("Unexpected physical entities " + parentPhysicalEntityPair);
        }
    }

    private GKInstance getMatchingInstanceInList(GKInstance instance, List<GKInstance> instanceList) {
        return instanceList
            .stream()
            .filter(instanceInList -> instanceInList.getDBID().equals(instance.getDBID()))
            .findFirst()
            .orElse(null);
    }
}