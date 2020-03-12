package org.gk.pathwaylayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.ConnectInfo;
import org.gk.render.ConnectWidget;
import org.gk.render.DefaultRenderConstants;
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
    
    private Map<Long, GKInstance> createDBIdToMapForDiseasePEs(GKInstance diseaseReation) throws Exception {
        Map<Long, GKInstance> dbIdToPE = new HashMap<>();
        Set<GKInstance> participants = InstanceUtilities.getReactionParticipants(diseaseReation);
        for (GKInstance participant : participants)
            dbIdToPE.put(participant.getDBID(), participant);
        return dbIdToPE;
    }
    
    private List<GKInstance> normalizeList(List<GKInstance> list,
                                           Map<Long, GKInstance> dbIdToInstance) {
        List<GKInstance> copy = new ArrayList<>();
        for (GKInstance inst : list) {
            GKInstance tmp = dbIdToInstance.get(inst.getDBID());
            copy.add(tmp);
        }
        return copy;
    }
    
    private Set<GKInstance> normalizeSet(Set<GKInstance> set,
                                         Map<Long, GKInstance> dbIdToInstance) {
        Set<GKInstance> copy = new HashSet<>();
        for (GKInstance inst : set) {
            GKInstance tmp = dbIdToInstance.get(inst.getDBID());
            copy.add(tmp);
        }
        return copy;
    }
    
    /**
     * Overlay a single disease reaction onto a normal reaction.
     * @param normalReaction
     * @param diseaseReaction
     * @param overlaidObjects
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void overlayDiseaseReaction(HyperEdge normalReaction,
                                          GKInstance diseaseReaction) throws Exception {
        // In a servlet, cache may not be used. Therefore, a list of inputs may be different even though
        // they are the same instances (e.g. multiple ATP). The following mapping is used to normalize
        // these values in a no-cache environment. One DB_ID has only one copy of GKInstance.
        Map<Long, GKInstance> dbIdToInstance = createDBIdToMapForDiseasePEs(diseaseReaction);
        
        HyperEdge reactionCopy = normalReaction.shallowCopy();
        reactionCopy.setReactomeId(diseaseReaction.getDBID());
        reactionCopy.setDisplayName(diseaseReaction.getDisplayName());
        reactionCopy.setLineColor(DefaultRenderConstants.DEFAULT_DISEASE_LINE_COLOR);
        displayedObject.addComponent(reactionCopy);
        overlaidObjects.add(reactionCopy);

        // List objects not listed in the disease reaction as crossed objects
        Set<GKInstance> lofInstances = new HashSet<GKInstance>();
        Map<Node, GKInstance> normalToDiseaseEntity = mapNormalToMutatedEntity(diseaseReaction, 
                                                                               normalReaction,
                                                                               lofInstances);
        Map<GKInstance, Node> diseaseToNormalEntity = new HashMap<>();
        normalToDiseaseEntity.forEach((n, d) -> {
            d = dbIdToInstance.get(d.getDBID());
            diseaseToNormalEntity.put(d, n);
        });
        Set<Node> coveredNormalNodes = new HashSet<>();
        // Handle disease inputs
        List<GKInstance> inputs = diseaseReaction.getAttributeValuesList(ReactomeJavaConstants.input);
        List<Node> normalInputNodes = normalReaction.getInputNodes();
        if (inputs.size() > 0 || normalInputNodes.size() > 0) {
            inputs = normalizeList(inputs, dbIdToInstance);
            handleDiseaseEntities(reactionCopy, 
                                  diseaseReaction,
                                  lofInstances,
                                  diseaseToNormalEntity, 
                                  inputs,
                                  normalInputNodes,
                                  HyperEdge.INPUT,
                                  coveredNormalNodes);
        }
        List<GKInstance> outputs = diseaseReaction.getAttributeValuesList(ReactomeJavaConstants.output);
        List<Node> normalOutputNodes = normalReaction.getOutputNodes();
        if (outputs.size() > 0 || normalOutputNodes.size() > 0) {
            outputs = normalizeList(outputs, dbIdToInstance);
            handleDiseaseEntities(reactionCopy,
                                  diseaseReaction,
                                  lofInstances,
                                  diseaseToNormalEntity,
                                  outputs,
                                  normalOutputNodes,
                                  HyperEdge.OUTPUT,
                                  coveredNormalNodes);
        }
        Set<GKInstance> set = new HashSet<>();
        InstanceUtilities.getReactionCatalysts(diseaseReaction, set);
        List<GKInstance> catalysts = new ArrayList<>(set);
        List<Node> normalCatalystNodes = normalReaction.getHelperNodes();
        if (catalysts.size() > 0 || normalCatalystNodes.size() > 0) {
            catalysts = normalizeList(catalysts, dbIdToInstance);
            handleDiseaseEntities(reactionCopy,
                                  diseaseReaction,
                                  lofInstances,
                                  diseaseToNormalEntity, 
                                  catalysts, 
                                  normalCatalystNodes,
                                  HyperEdge.CATALYST,
                                  coveredNormalNodes);
        }
        Collection<GKInstance> regulations = InstanceUtilities.getRegulations(diseaseReaction);
        Set<GKInstance> activators = new HashSet<>();
        Set<GKInstance> inhibitors = new HashSet<>();
        for (GKInstance regulation : regulations) {
            if (regulation.getSchemClass().isa(ReactomeJavaConstants.PositiveRegulation)) {
                GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
                if (regulator != null)
                    activators.add(regulator);
            }
            else {
                GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
                if (regulator != null)
                    inhibitors.add(regulator);
            }
        }
        if (activators.size() > 0 || normalReaction.getActivatorNodes().size() > 0) {
            activators = normalizeSet(activators, dbIdToInstance);
            handleDiseaseEntities(reactionCopy,
                                  diseaseReaction,
                                  lofInstances,
                                  diseaseToNormalEntity,
                                  activators, 
                                  normalReaction.getActivatorNodes(),
                                  HyperEdge.ACTIVATOR,
                                  coveredNormalNodes);
        }
        if (inhibitors.size() > 0 || normalReaction.getInhibitorNodes().size() > 0) {
            inhibitors = normalizeSet(inhibitors, dbIdToInstance);
            handleDiseaseEntities(reactionCopy,
                                  diseaseReaction,
                                  lofInstances, 
                                  diseaseToNormalEntity, 
                                  inhibitors, 
                                  normalReaction.getInhibitorNodes(),
                                  HyperEdge.INHIBITOR,
                                  coveredNormalNodes);
        }
        collectCrossedNodes(diseaseReaction, 
                            normalReaction,
                            coveredNormalNodes);
    }
    
    private void collectCrossedNodes(GKInstance diseaseRLE,
                                     HyperEdge normalReaction,
                                     Set<Node> coveredNormalNodes) {
        // Make sure only caring about FailedReactions
        if (!diseaseRLE.getSchemClass().isa(ReactomeJavaConstants.FailedReaction))
            return;
        List<Node> connectedNodes = normalReaction.getConnectedNodes();
        connectedNodes.removeAll(coveredNormalNodes);
        crossedObjects.addAll(connectedNodes);
    }
    
    private Map<GKInstance, Integer> getStoichiometries(Collection<GKInstance> diseasePEs) {
        Map<GKInstance, Integer> instToStoi = new HashMap<>();
        diseasePEs.forEach(pe -> {
            instToStoi.compute(pe, (key, count) -> {
                if (count == null)
                    count = 1;
                else
                    count ++;
                return count;
            });
        });
        return instToStoi;
    }

    private void handleDiseaseEntities(HyperEdge reactionCopy,
                                       GKInstance diseaseRLE,
                                       Set<GKInstance> lofInstances,
                                       Map<GKInstance, Node> diseaseToNormalEntity,
                                       Collection<GKInstance> diseasePEs,
                                       List<Node> normalNodes,
                                       int role,
                                       Set<Node> coveredAllNodes) throws Exception {
        Set<Node> coveredNormalNodes = new HashSet<>();
        Set<GKInstance> inputSet = new HashSet<>(diseasePEs);
        Map<GKInstance, Integer> instToStoi = getStoichiometries(diseasePEs);
        Map<Long, Node> normalIdToNode = new HashMap<>();
        normalNodes.stream().filter(n -> n.getReactomeId() != null).forEach(n -> normalIdToNode.put(n.getReactomeId(), n));
        for (Iterator<GKInstance> it = inputSet.iterator(); it.hasNext();) {
            GKInstance input = it.next();
            Integer stoi = instToStoi.get(input);
            Node normalNode = normalIdToNode.get(input.getDBID());
            if (normalNode != null) {
                // Great. Nothing needs to be done except the stoichiometry
                // which may be different
                // Re-link to diseaseNode
                if (stoi != null) {
                    ConnectInfo connectInfo = reactionCopy.getConnectInfo();
                    List<?> widgets = connectInfo.getConnectWidgets();
                    for (Object obj : widgets) {
                        ConnectWidget widget = (ConnectWidget) obj;
                        if (widget.getConnectedNode() == normalNode && widget.getRole() == role) {
                            widget.setStoichiometry(stoi);
                            break;
                        }
                    }
                }
                it.remove();
                normalNodes.remove(normalNode);
                coveredNormalNodes.add(normalNode);
                overlaidObjects.add(normalNode);
                continue;
            }
            normalNode = diseaseToNormalEntity.get(input);
            if (normalNode != null) {
                Node diseaseNode = replaceNormalNodeByDisease(normalNode,
                                                              input, 
                                                              lofInstances, 
                                                              reactionCopy,
                                                              role,
                                                              stoi);
                if (diseaseNode == null)
                    continue; // To be handled by others.
                it.remove();
                normalNodes.remove(normalNode);
                coveredNormalNodes.add(normalNode);
                continue;
            }
        }
        if (inputSet.size() > 0) {
            for (GKInstance input : inputSet) {
                Set<GKInstance> inputRefs = InstanceUtilities.grepReferenceEntitiesForPE(input);
                Node normalNode = findBestPossibleMatch(normalNodes, inputRefs);
                if (normalNode == null) { // e.g. R-HSA-5602549
                    normalNode = findNodeFromEntitySet(normalNodes, input); // Another try
                }
                // Last test: whatever is left
                if (normalNode == null) {
                    if (inputSet.size() == 1 && normalNodes.size() == 1) {
                        // Whatever is left
                        normalNode = normalNodes.get(0);
                    }
                }
                if (normalNode != null) {
                    Node diseaseNode = replaceNormalNodeByDisease(normalNode,
                                               input,
                                               lofInstances,
                                               reactionCopy,
                                               role,
                                               instToStoi.get(input));
                    if (diseaseNode == null)
                        continue; // Cannot do anything. TODO: Probably an error should be generated here!
                    normalNodes.remove(normalNode); // We don't want to reuse normal node
                    coveredNormalNodes.add(normalNode);
                }
            }
        }
        normalNodes.removeAll(coveredNormalNodes);
        coveredAllNodes.addAll(coveredNormalNodes);
        // The following code is not used. Therefore, the overlaid FailedReactions
        // may not be consistent to the data model
//        // Special treatment for FailedReactions to keep these links
//        if (diseaseRLE.getSchemClass().isa(ReactomeJavaConstants.FailedReaction) && role == HyperEdge.OUTPUT)
//            return;
//        // Otherwise, cleaning up
//        for (Node node : normalNodes)
//            reactionCopy.remove(node, role);
    }
    
    private Node findNodeFromEntitySet(List<Node> normalNodes,
                                       GKInstance diseasePE) throws Exception {
        for (Node node : normalNodes) {
            Long id = node.getReactomeId();
            if (id == null)
                continue;
            GKInstance inst = diseasePE.getDbAdaptor().fetchInstance(id);
            if (inst == null)
                continue;
            if (inst.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
                Set<GKInstance> members = InstanceUtilities.getContainedInstances(inst,
                                                                                  ReactomeJavaConstants.hasMember,
                                                                                  ReactomeJavaConstants.hasCandidate);
                if (members.contains(diseasePE))
                    return node;
            }
        }
        return null;
    }
    
    private Node replaceNormalNodeByDisease(Node normalNode, 
                                            GKInstance input, 
                                            Set<GKInstance> lofInstances,
                                            HyperEdge reactionCopy,
                                            int role,
                                            Integer stoi) {
        Node diseaseNode = replaceNormalNode(normalNode, 
                                             input,
                                             contains(input, lofInstances));
        if (diseaseNode == null)
            return null; // Just in case
        // Re-link to diseaseNode
        ConnectInfo connectInfo = reactionCopy.getConnectInfo();
        List<?> widgets = connectInfo.getConnectWidgets();
        for (Object obj : widgets) {
            ConnectWidget widget = (ConnectWidget) obj;
            if (widget.getConnectedNode() == normalNode && widget.getRole() == role) {
                widget.replaceConnectedNode(diseaseNode);
                if (stoi != null)
                    widget.setStoichiometry(stoi);
            }
        }
        return diseaseNode;
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
