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
public class PolymerComparer extends PhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentPolymerPair) throws Exception {
        if (equivalentPolymerPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierPolymer = equivalentPolymerPair.getKey();
        GKInstance newPolymer = equivalentPolymerPair.getValue();

        Set<Action> polymerActions = getCommonPhysicalEntityActions(equivalentPolymerPair);

        polymerActions.addAll(
            getDirectPhysicalEntityActions(equivalentPolymerPair, ReactomeJavaConstants.repeatedUnit));

        polymerActions.addAll(
            getIndirectPhysicalEntityActions(equivalentPolymerPair, ReactomeJavaConstants.repeatedUnit));

        polymerActions.addAll(
            getInstanceActions(
                earlierPolymer,
                newPolymer,
                ReactomeJavaConstants.compartment,
                ReactomeJavaConstants.maxUnitCount,
                ReactomeJavaConstants.minUnitCount,
                ReactomeJavaConstants.species
            )
        );
        return polymerActions;
    }
}
