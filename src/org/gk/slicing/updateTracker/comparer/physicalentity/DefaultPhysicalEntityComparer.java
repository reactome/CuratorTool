package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.slicing.updateTracker.model.Action;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 1/15/2024
 */
public class DefaultPhysicalEntityComparer extends AbstractPhysicalEntityComparer {
    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) throws Exception {
        if (equivalentPhysicalEntityPair == null) {
            return new TreeSet<>();
        }

        return getCommonPhysicalEntityActions(equivalentPhysicalEntityPair);
    }
}
