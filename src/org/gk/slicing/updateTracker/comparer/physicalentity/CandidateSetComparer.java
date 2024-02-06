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
public class CandidateSetComparer extends EntitySetComparer {

    @Override
    public Set<Action> getChanges(Map.Entry<GKInstance, GKInstance> equivalentCandidateSetPair) throws Exception {
        if (equivalentCandidateSetPair == null) {
            return new TreeSet<>();
        }

        Set<Action> candidateSetActions = new TreeSet<>();
        candidateSetActions.addAll(
            getDirectPhysicalEntityActions(equivalentCandidateSetPair, ReactomeJavaConstants.hasCandidate));
        candidateSetActions.addAll(
            getIndirectPhysicalEntityActions(equivalentCandidateSetPair, ReactomeJavaConstants.hasCandidate));
        candidateSetActions.addAll(super.getChanges(equivalentCandidateSetPair));
        return candidateSetActions;
    }
}
