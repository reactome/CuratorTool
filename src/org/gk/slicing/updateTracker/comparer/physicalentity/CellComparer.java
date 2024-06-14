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
public class CellComparer extends AbstractPhysicalEntityComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentCellPair) throws Exception {
        if (equivalentCellPair == null) {
            return new TreeSet<>();
        }

        GKInstance earlierCell = equivalentCellPair.getKey();
        GKInstance newCell = equivalentCellPair.getValue();

        Set<Action> cellActions = getCommonPhysicalEntityActions(equivalentCellPair);

        cellActions.addAll(
            getInstanceActions(
                earlierCell,
                newCell,
                ReactomeJavaConstants.compartment,
                "markerReference",
                "organ",
                ReactomeJavaConstants.proteinMarker,
                ReactomeJavaConstants.RNAMarker,
                ReactomeJavaConstants.species,
                "tissue",
                "tissueLayer"
            )
        );
        return cellActions;
    }
}