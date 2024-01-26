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
public class ComplexComparer extends PhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentComplexPair) throws Exception {
        if (equivalentComplexPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierComplex = equivalentComplexPair.getKey();
        GKInstance newComplex = equivalentComplexPair.getValue();

        Set<Action> complexActions = getCommonPhysicalEntityActions(equivalentComplexPair);

        complexActions.addAll(
            getDirectPhysicalEntityActions(equivalentComplexPair, ReactomeJavaConstants.hasComponent));

        complexActions.addAll(
            getIndirectPhysicalEntityActions(equivalentComplexPair, ReactomeJavaConstants.hasComponent));

        complexActions.addAll(
            getInstanceActions(
                earlierComplex,
                newComplex,
                ReactomeJavaConstants.compartment,
                "entityOnOtherCell",
                ReactomeJavaConstants.species,
                "stoichiometryKnown"
            )
        );
        return complexActions;
    }
}