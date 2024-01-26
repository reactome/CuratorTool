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
public class GEEComparer extends PhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentGEEPair) throws Exception {
        Set<Action> geeActions = new TreeSet<>();

        GKInstance earlierGEE = equivalentGEEPair.getKey();
        GKInstance newGEE = equivalentGEEPair.getValue();

        geeActions.addAll(getCommonPhysicalEntityActions(equivalentGEEPair));
        geeActions.addAll(
            getInstanceActions(
                earlierGEE,
                newGEE,
                ReactomeJavaConstants.compartment,
                ReactomeJavaConstants.inferredFrom,
                ReactomeJavaConstants.species
            )
        );

        return geeActions;
    }
}
