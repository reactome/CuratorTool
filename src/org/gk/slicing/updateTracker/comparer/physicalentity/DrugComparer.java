package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.slicing.updateTracker.model.Action;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 6/28/2023
 */
public class DrugComparer extends AbstractPhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentDrugPair) throws Exception {
        if (equivalentDrugPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierDrug = equivalentDrugPair.getKey();
        GKInstance newDrug = equivalentDrugPair.getValue();

        Set<Action> drugActions = getCommonPhysicalEntityActions(equivalentDrugPair);
        drugActions.addAll(
            getInstanceActions(
                earlierDrug,
                newDrug,
                ReactomeJavaConstants.compartment,
                ReactomeJavaConstants.referenceEntity
            )
        );

        if (bothHaveSpeciesAttribute(equivalentDrugPair)) {
            drugActions.addAll(
                getInstanceActions(
                    earlierDrug,
                    newDrug,
                    ReactomeJavaConstants.species
                )
            );
        }

        return drugActions;
    }

    private boolean bothHaveSpeciesAttribute(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveAttribute(ReactomeJavaConstants.species, equivalentPhysicalEntityPair);
    }

    private boolean bothHaveAttribute(
        String attribute, Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {

        GKInstance earlierPhysicalEntity = equivalentPhysicalEntityPair.getKey();
        GKInstance newPhysicalEntity = equivalentPhysicalEntityPair.getValue();

        return earlierPhysicalEntity != null &&
            newPhysicalEntity != null &&
            earlierPhysicalEntity.getSchemClass().isValidAttribute(attribute) &&
            newPhysicalEntity.getSchemClass().isValidAttribute(attribute);
    }
}
