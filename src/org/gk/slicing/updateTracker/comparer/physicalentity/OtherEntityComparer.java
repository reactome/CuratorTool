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
public class OtherEntityComparer extends PhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentOtherEntityPair)
        throws Exception {

        if (equivalentOtherEntityPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierOtherEntity = equivalentOtherEntityPair.getKey();
        GKInstance newOtherEntity = equivalentOtherEntityPair.getValue();

        Set<Action> otherEntityActions = getCommonPhysicalEntityActions(equivalentOtherEntityPair);
        otherEntityActions.addAll(
            getInstanceActions(
                earlierOtherEntity,
                newOtherEntity,
                ReactomeJavaConstants.compartment
            )
        );

        return otherEntityActions;
    }
}