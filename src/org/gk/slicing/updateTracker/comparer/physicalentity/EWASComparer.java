package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.slicing.updateTracker.model.Action;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 4/29/2022
 */
public class EWASComparer extends GEEComparer {

    public EWASComparer() {
        super();
    }

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentEWASPair) throws Exception {
        Set<Action> ewasActions = new TreeSet<>();

        if (equivalentEWASPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierEWAS = equivalentEWASPair.getKey();
        GKInstance newEWAS = equivalentEWASPair.getValue();

        ewasActions.addAll(
            getInstanceActions(
                earlierEWAS,
                newEWAS,
                ReactomeJavaConstants.referenceEntity,
                ReactomeJavaConstants.endCoordinate,
                ReactomeJavaConstants.startCoordinate,
                ReactomeJavaConstants.hasModifiedResidue
            )
        );
        ewasActions.addAll(super.getChanges(equivalentEWASPair));
        return ewasActions;
    }
}
