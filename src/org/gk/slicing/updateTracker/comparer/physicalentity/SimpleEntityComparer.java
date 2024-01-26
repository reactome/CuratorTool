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
public class SimpleEntityComparer extends PhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentSimpleEntityPair) throws Exception {

        if (equivalentSimpleEntityPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierSimpleEntity = equivalentSimpleEntityPair.getKey();
        GKInstance newSimpleEntity = equivalentSimpleEntityPair.getValue();

        Set<Action> simpleEntityActions = getCommonPhysicalEntityActions(equivalentSimpleEntityPair);
        simpleEntityActions.addAll(
            getInstanceActions(
                earlierSimpleEntity,
                newSimpleEntity,
                ReactomeJavaConstants.compartment,
                ReactomeJavaConstants.species
            )
        );
        return simpleEntityActions;
    }
}