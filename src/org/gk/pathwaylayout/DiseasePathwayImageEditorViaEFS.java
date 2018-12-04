package org.gk.pathwaylayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.schema.InvalidAttributeException;

/**
 * A new mapping from normal entities to disease entities based on the manually curated
 * disease entities and normal entities.
 * @author wug
 *
 */
public class DiseasePathwayImageEditorViaEFS extends DiseasePathwayImageEditor {
    
    public DiseasePathwayImageEditorViaEFS() {
        
    }

    @Override
    protected Set<Long> getDiseaseIDsInRLE(GKInstance diseaseReaction) throws Exception, InvalidAttributeException {
        Set<GKInstance> participants = InstanceUtilities.getReactionParticipants(diseaseReaction);
        Set<Long> diseaseIds = participants.stream().map(i -> i.getDBID()).collect(Collectors.toSet());
        return diseaseIds;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Map<Node, GKInstance> mapNormalToMutatedEntity(GKInstance diseaseReaction,
                                                             HyperEdge normalReaction,
                                                             Set<GKInstance> lofInstances) throws InvalidAttributeException, Exception {
        List<GKInstance> efses = diseaseReaction.getAttributeValuesList(ReactomeJavaConstants.entityFunctionalStatus);
        // Map mutated entities to normal entities via ReferenceGeneProduct
        Map<Node, GKInstance> normalToDiseaseEntity = new HashMap<Node, GKInstance>();
        if (efses == null || efses.size() == 0)
            return normalToDiseaseEntity;
        List<Node> normalNodes = normalReaction.getConnectedNodes();
        Map<Long, Node> normalDBIDToNode = new HashMap<>();
        normalNodes.stream()
                   .filter(node -> node.getReactomeId() != null)
                   .forEach(node -> normalDBIDToNode.put(node.getReactomeId(), node));
        for (GKInstance efs : efses) {
            GKInstance normalEntity = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.normalEntity);
            if (normalEntity == null)
                continue;
            Node normalNode = normalDBIDToNode.get(normalEntity.getDBID());
            if (normalNode == null)
                continue;
            GKInstance diseaseEntity = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.diseaseEntity);
            if (diseaseEntity == null)
                continue;
            normalToDiseaseEntity.put(normalNode, diseaseEntity);
            if (isLOFEntity(efs))
                lofInstances.add(diseaseEntity);
        }
        return normalToDiseaseEntity;
    }
}
