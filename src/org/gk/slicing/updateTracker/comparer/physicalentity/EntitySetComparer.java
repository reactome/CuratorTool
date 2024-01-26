package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.slicing.updateTracker.model.Action;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 6/26/2023
 */
public class EntitySetComparer extends PhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentEntitySetPair)
        throws Exception {

        if (equivalentEntitySetPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierSet = equivalentEntitySetPair.getKey();
        GKInstance newSet = equivalentEntitySetPair.getValue();

        Set<Action> entitySetActions = getCommonPhysicalEntityActions(equivalentEntitySetPair);

        entitySetActions.addAll(
            getDirectPhysicalEntityActions(equivalentEntitySetPair, ReactomeJavaConstants.hasMember));
        entitySetActions.addAll(
            getIndirectPhysicalEntityActions(equivalentEntitySetPair, ReactomeJavaConstants.hasMember));
        entitySetActions.addAll(
            getInstanceActions(
                earlierSet,
                newSet,
                ReactomeJavaConstants.compartment,
                ReactomeJavaConstants.species
            )
        );
        return entitySetActions;
    }
}