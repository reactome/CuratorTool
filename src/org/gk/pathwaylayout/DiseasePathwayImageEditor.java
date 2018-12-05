/*
 * Created on Oct 10, 2011
 *
 */
package org.gk.pathwaylayout;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFrame;

import org.gk.gkCurator.authorTool.InstanceHandler;
import org.gk.gkCurator.authorTool.InstanceHandlerFactory;
import org.gk.gkEditor.ZoomablePathwayEditor;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.ConnectInfo;
import org.gk.render.ConnectWidget;
import org.gk.render.DefaultRenderConstants;
import org.gk.render.EntitySetAndEntitySetLink;
import org.gk.render.EntitySetAndMemberLink;
import org.gk.render.FlowLine;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.render.Note;
import org.gk.render.ProcessNode;
import org.gk.render.RenderUtility;
import org.gk.render.Renderable;
import org.gk.render.RenderableCompartment;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderablePathway;
import org.gk.render.RendererFactory;
import org.gk.schema.InvalidAttributeException;
import org.gk.util.DialogControlPane;

/**
 * This customized PathwayEditor is used to draw disease related pathway.
 * @author gwu
 *
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class DiseasePathwayImageEditor extends PathwayEditor {
    private boolean isForNormal;
    protected PersistenceAdaptor adaptor;
    // Pathway instance this editor is used for
    private GKInstance pathway;
    // Cached values
    private Set<Long> normalIds;
    private Set<Long> diseaseIds;
    private List<Renderable> normalComps;
    private List<Renderable> diseaseComps;
    protected Set<Node> crossedObjects;
    protected Set<Renderable> overlaidObjects;
    // List of loss_of_function nodes that should be drawn specially
    private Set<Node> lofNodes;
    // Keep track mapping from normal node to disease node so that
    // only one copy disease node is needed to create per DB_ID
    private Map<Node, Map<Long,Node>> normalToDBIDToDiseaseNode;
    
    public DiseasePathwayImageEditor() {
    }
    
    public GKInstance getPathway() {
        return pathway;
    }
    
    /**
     * Get contained Renderable objects for the normal pathway.
     * @return
     */
    public List<Renderable> getNormalComponents() {
        return this.normalComps;
    }
    
    /**
     * Get contained Renderbale objects belong to the disease pathway only.
     * @return
     */
    public List<Renderable> getDiseaseComponents() {
        return this.diseaseComps;
    }
    
    /**
     * Get a list of Nodes that should be crossed out during drawing. 
     * @return
     */
    public List<Node> getCrossedObjects() {
        if (crossedObjects == null)
            return new ArrayList<>();
        return new ArrayList<>(crossedObjects);
    }
    
    /**
     * Get a list of Renderable objects that should be overlaid from the disease pathway.
     * @return
     */
    public List<Renderable> getOverlaidObjects() {
        if (overlaidObjects == null)
            return new ArrayList<>();
        return new ArrayList<>(overlaidObjects);
    }
    
    /**
     * Get a list of nodes that should be displayed as loss_of_function objects.
     * @return
     */
    public List<Node> getLofNodes() {
        if (lofNodes == null)
            return new ArrayList<>();
        return new ArrayList<>(lofNodes);
    }

    /**
     * Override this method to augment a disease diagram by overlaying disease related objects
     * that have not drawn (e.g. loss_of_function reactions)
     */
    @Override
    public void setRenderable(Renderable pathway) {
        super.setRenderable(pathway);
        if (this.pathway == null) // Just some place holder
            return; 
        splitObjects();
        if (isForNormal)
            return; // No need for doing overlay
        //List<Renderable> overlaidObjects = new ArrayList<Renderable>();
        crossedObjects = new HashSet<>();
        overlaidObjects = new HashSet<>();
        overlayDiseaseReactions(diseaseIds);
        // Search for loss_of_functional nodes for special display
        checkLossOfFunctionNodes();
    }
    
    /**
     * Refresh the data structure and display.
     */
    public void refresh() {
        Renderable pathway = getRenderable();
        if (pathway == null)
            return;
        setRenderable(pathway); // Reset to force update
        repaint(getVisibleRect());
    }
    
    /**
     * Display this panel in a JFrame.
     */
    public void showInFrame(Component parent) {
        if (pathway == null) {
            return; // Nothing to be displayed
        }
        JFrame frame = new JFrame("Diagram of " + pathway.getDisplayName());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        DialogControlPane controlPane = new DialogControlPane();
        ZoomablePathwayEditor pathwayEditor = new ZoomablePathwayEditor() {

            @Override
            protected PathwayEditor createPathwayEditor() {
                return DiseasePathwayImageEditor.this;
            }
            
        };
        frame.getContentPane().add(pathwayEditor, BorderLayout.CENTER);
        frame.getContentPane().add(controlPane, BorderLayout.SOUTH);
        controlPane.getCancelBtn().setVisible(false);
        JButton okBtn = controlPane.getOKBtn();
        okBtn.setText("Close");
        okBtn.addActionListener(e -> frame.dispose());
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(parent);
        frame.setVisible(true);
    }
    
    private void checkLossOfFunctionNodes() {
        try {
            lofNodes = new HashSet<Node>();
            List<Renderable> components = displayedObject.getComponents();
            if (components == null || components.size() == 0)
                return;
            for (Renderable r : components) {
                if (r instanceof Node || r.getReactomeId() == null)
                    continue;
                if (!diseaseIds.contains(r.getReactomeId()))
                    continue;
                GKInstance inst = adaptor.fetchInstance(r.getReactomeId());
                if (!inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) ||
                    !inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.entityFunctionalStatus))
                    continue;
                List<GKInstance> efs = inst.getAttributeValuesList(ReactomeJavaConstants.entityFunctionalStatus);
                Set<GKInstance> lofPEs = new HashSet<GKInstance>();
                for (GKInstance ef : efs) {
                    GKInstance pe = (GKInstance) ef.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                    if (isLOFEntity(ef))
                        lofPEs.add(pe);
                }
                List<Node> nodes = ((HyperEdge)r).getConnectedNodes();
                for (Node node : nodes) {
                    if (node.getReactomeId() == null)
                        continue;
                    GKInstance nodeInst = adaptor.fetchInstance(node.getReactomeId());
                    Set<GKInstance> nodeRefEntities = getReferenceEntity(nodeInst);
                    for (GKInstance lofPE : lofPEs) {
                        Set<GKInstance> lofRefEntities = getReferenceEntity(lofPE);
                        lofRefEntities.retainAll(nodeRefEntities);
                        if (lofRefEntities.size() > 0) {
                            // A LOF node
                            lofNodes.add(node);
                            break;
                        }
                    }
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Set the Pathway instance this object is used to draw. Since a PathwayDiagram
     * for disease may be used in multiple pathways, the wrapped pathway cannot be 
     * figured out from its RenderablePathway.
     * @param pathway
     */
    public void setPathway(GKInstance pathway) {
        this.pathway = pathway;
    }

    /**
     * Set a flag to indicate that the paint method should draw objects that are related to normal 
     * pathway part only.
     * @param isForNormal
     */
    public void setIsForNormal(boolean isForNormal) {
        this.isForNormal = isForNormal;
    }
    
    /**
     * To be used to query GKInstances based on reactomeIds.
     * @param adaptor
     */
    public void setPersistenceAdaptor(PersistenceAdaptor adaptor) {
        this.adaptor = adaptor;
    }
    
    /**
     * Some of disease reactions may not be drawn in the pathway diagram (e.g. LoF or GoF reactions).
     * These reactions should be overlaid onto their normalReaction counterparts, which are encoded
     * by their normalReaction attributes.
     * @param diseaseIds
     */
    private void overlayDiseaseReactions(Set<Long> diseaseIds) {
        // Get the list of all drawing objects for checking
        Map<Long, Renderable> idToObject = new HashMap<Long, Renderable>();
        List<Renderable> components = displayedObject.getComponents();
        if (components == null || components.size() == 0)
            return;
        for (Renderable r : components) {
            if (r.getReactomeId() == null)
                continue;
            idToObject.put(r.getReactomeId(),
                           r);
        }
        try {
            normalToDBIDToDiseaseNode = new HashMap<>();
            for (Long diseaseId : diseaseIds) {
                if (idToObject.containsKey(diseaseId))
                    continue;
                // Have to copy the normal reactions to draw 
                GKInstance inst = adaptor.fetchInstance(diseaseId);
                if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    List<GKInstance> normalReactions = inst.getAttributeValuesList(ReactomeJavaConstants.normalReaction);
                    if (normalReactions != null && normalReactions.size() > 0) {
                        for (GKInstance normalRxt : normalReactions) {
                            Renderable r = idToObject.get(normalRxt.getDBID());
                            if (r instanceof HyperEdge)
                                overlayDiseaseReaction((HyperEdge)r, 
                                                       inst);
                        }
                    }
                }
            }
            return;
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Overlay a single disease reaction onto a normal reaction.
     * @param normalReaction
     * @param diseaseReaction
     * @param overlaidObjects
     */
    protected void overlayDiseaseReaction(HyperEdge normalReaction,
                                          GKInstance diseaseReaction) throws Exception {
        HyperEdge reactionCopy = normalReaction.shallowCopy();
        reactionCopy.setReactomeId(diseaseReaction.getDBID());
        reactionCopy.setDisplayName(diseaseReaction.getDisplayName());
        reactionCopy.setLineColor(DefaultRenderConstants.DEFAULT_DISEASE_BACKGROUND);
        displayedObject.addComponent(reactionCopy);
        overlaidObjects.add(reactionCopy);
        // Want to handle inputs, outputs and catalysts since regulators can
        // be ignored
        List<Node> nodes = new ArrayList<Node>();
        nodes.addAll(normalReaction.getInputNodes());
        nodes.addAll(normalReaction.getOutputNodes());
        nodes.addAll(normalReaction.getHelperNodes());
        // Want to handle regulators too as of July 23, 2015.
        nodes.addAll(normalReaction.getInhibitorNodes());
        nodes.addAll(normalReaction.getActivatorNodes());
        // List objects not listed in the disease reaction as crossed objects
        Set<Long> diseaseIds = getDiseaseIDsInRLE(diseaseReaction);
        Set<GKInstance> lofInstances = new HashSet<GKInstance>();
        Map<Node, GKInstance> normalToDiseaseEntity = mapNormalToMutatedEntity(diseaseReaction, 
                                                                               normalReaction,
                                                                               lofInstances);
        for (Node node : nodes) {
            if (!diseaseIds.contains(node.getReactomeId())) {
                // Check if it should be mapped to a normal entity
                GKInstance diseaseEntity = normalToDiseaseEntity.get(node);
                if (diseaseEntity == null) {
                    crossedObjects.add(node); // Just crossed out
                    node.invalidateConnectWidgets();
                }
                else {
                    Node diseaseNode = replaceNormalNode(node, 
                                                         diseaseEntity,
                                                         contains(diseaseEntity, lofInstances));
                    if (diseaseNode == null)
                        continue; // Just in case
                    diseaseNode.invalidateBounds();
                    // Re-link to diseaseNode
                    ConnectInfo connectInfo = reactionCopy.getConnectInfo();
                    List<?> widgets = connectInfo.getConnectWidgets();
                    for (Object obj : widgets) {
                        ConnectWidget widget = (ConnectWidget) obj;
                        if (widget.getConnectedNode() == node)
                            widget.replaceConnectedNode(diseaseNode);
                    }
                    diseaseNode.invalidateBounds();
                    diseaseNode.invalidateConnectWidgets();
                }
            }
            else {
                overlaidObjects.add(node);
                node.invalidateBounds();
            }
        }
    }

    protected Set<Long> getDiseaseIDsInRLE(GKInstance diseaseReaction) throws Exception, InvalidAttributeException {
        Set<GKInstance> participants = InstanceUtilities.getReactionParticipants(diseaseReaction);
        Set<Long> diseaseIds = new HashSet<Long>();
        for (GKInstance participant : participants) {
            diseaseIds.add(participant.getDBID());
            if (participant.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasMember)) {
                List<GKInstance> list = participant.getAttributeValuesList(ReactomeJavaConstants.hasMember);
                if (list != null && list.size() > 0) {
                    for (GKInstance inst : list)
                        diseaseIds.add(inst.getDBID());
                }
            }
            if (participant.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasCandidate)) {
                List<GKInstance> list = participant.getAttributeValuesList(ReactomeJavaConstants.hasCandidate);
                if (list != null && list.size() > 0) {
                    for (GKInstance inst : list)
                        diseaseIds.add(inst.getDBID());
                }
            }
        }
        return diseaseIds;
    }
    
    private void cacheDiseaseNode(GKInstance diseaseEntity,
                                  Node diseaseNode, 
                                  Node normalNode) {
        Map<Long, Node> dbIdToDiseaseNode = normalToDBIDToDiseaseNode.get(normalNode);
        if (dbIdToDiseaseNode == null) {
            dbIdToDiseaseNode = new HashMap<>();
            normalToDBIDToDiseaseNode.put(normalNode, dbIdToDiseaseNode);
        }
        dbIdToDiseaseNode.put(diseaseEntity.getDBID(), diseaseNode);
    }

    protected Node replaceNormalNode(Node normalNode,
                                   GKInstance diseaseEntity,
                                   Boolean needDashedBorder) {
        Map<Long, Node> dbIdToDiseaseNode = normalToDBIDToDiseaseNode.get(normalNode);
        if (dbIdToDiseaseNode != null && dbIdToDiseaseNode.containsKey(diseaseEntity.getDBID()))
            return dbIdToDiseaseNode.get(diseaseEntity.getDBID());
        try {
            // If a node exists already, it should use
            for (Renderable r : diseaseComps) {
                if (diseaseEntity.getDBID().equals(r.getReactomeId()) && r instanceof Node) {
                    // This is rather arbitrary: if two nodes are very close,
                    // use the existing one.
                    int dx = Math.abs(r.getPosition().x - normalNode.getPosition().x);
                    int dy = Math.abs(r.getPosition().y - normalNode.getPosition().y);
                    if (dx < 10 && dy < 10) {
                        // We don't need to create a new Node if it exists already
                        cacheDiseaseNode(diseaseEntity, (Node)r, normalNode);
                        overlaidObjects.add(r); // Add it to overlaid object to cover edges
                        return (Node)r;
                    }
                }
            }
            // diseaseEntity may have different instance type from its mapped
            // normal counterpart. Therefore, we need to determine its own type
            InstanceHandler handler = InstanceHandlerFactory.getFactory().getHandler(diseaseEntity);
            Node diseaseNode = (Node) handler.simpleConvertToRenderable(diseaseEntity);
//            diseaseNode = normalNode.getClass().newInstance();
            RenderUtility.copyRenderInfo(normalNode, diseaseNode);
            // The following should NOT be called since NodeAttachment is
            // related to disease entity only.
            //TODO: Need to support this. Currently it is not supported!!! See example
            // in PI3/AKT cancer pathway.
            //diseaseNode.setNodeAttachmentsLocally(node.getNodeAttachments());
            diseaseNode.setDisplayName(diseaseEntity.getDisplayName());
            diseaseNode.setReactomeId(diseaseEntity.getDBID());
            diseaseNode.invalidateBounds();
            // Since diseaseNode type may be different from normalNode,
            // we will try to get the original possible renderer
            diseaseNode.setRenderer(RendererFactory.getFactory().getRenderer(diseaseNode));
//            diseaseNode.setRenderer(normalNode.getRenderer());
            diseaseNode.setLineColor(DefaultRenderConstants.DEFAULT_DISEASE_BACKGROUND);
            diseaseNode.setNeedDashedBorder(needDashedBorder);
            RenderUtility.hideCompartmentInNodeName(diseaseNode);
            overlaidObjects.add(diseaseNode);
            displayedObject.addComponent(diseaseNode);
            cacheDiseaseNode(diseaseEntity, diseaseNode, normalNode);
            return diseaseNode;
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    protected Node findBestPossibleMatch(Collection<Node> nodes, Set<GKInstance> refEntities) throws Exception {
        Node bestNode = null;
        int bestExtra = Integer.MAX_VALUE;
        for (Node node : nodes) {
            GKInstance nodeInst = adaptor.fetchInstance(node.getReactomeId());
            Set<GKInstance> nodeRefEntities = getReferenceEntity(nodeInst);
            Set<GKInstance> copy = new HashSet<>(nodeRefEntities);
            retainAll(nodeRefEntities, refEntities);
            if (nodeRefEntities.size() > 0) {
                // First have to make sure there is something similar
                // Then we want to see if there is something extra
                copy.removeAll(nodeRefEntities);
                int extra = copy.size();
                if (bestNode == null || extra < bestExtra) {
                    bestNode = node;
                    bestExtra = extra;
                }
            }
        }
        return bestNode;
    }

    /**
     * Map mutated nodes in a disease reaction to displayed nodes in a normal reaction. The mapping
     * is based on sharing of referenceEntity. This may not be reliable!
     * @param diseaseReaction
     * @param nodes
     * @throws InvalidAttributeException
     * @throws Exception
     * @TODO: add a new attribute normalEntity in the PhysicalEntity class.
     */
    protected Map<Node, GKInstance> mapNormalToMutatedEntity(GKInstance diseaseReaction,
                                                             HyperEdge normalReaction,
                                                             Set<GKInstance> lofInstances) throws InvalidAttributeException, Exception {
        List<GKInstance> efs = diseaseReaction.getAttributeValuesList(ReactomeJavaConstants.entityFunctionalStatus);
        // Map mutated entities to normal entities via ReferenceGeneProduct
        Map<Node, GKInstance> normalToDiseaseEntity = new HashMap<Node, GKInstance>();
        if (efs == null)
            return normalToDiseaseEntity;
        List<Node> nodes = new ArrayList<Node>();
        nodes.addAll(normalReaction.getInputNodes());
        // PEs in EFSes should never been mapped to outputs
        nodes.addAll(normalReaction.getHelperNodes());
        // Want to handle regulators too as of July 23, 2015.
        nodes.addAll(normalReaction.getInhibitorNodes());
        nodes.addAll(normalReaction.getActivatorNodes());
        for (GKInstance ef : efs) {
            GKInstance pe = (GKInstance) ef.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (pe != null) {
                Set<GKInstance> refEntities = getReferenceEntity(pe);
                // want to find the matched node
                Node matchedNode = findBestPossibleMatch(nodes, refEntities);
                if (matchedNode != null) {
                    normalToDiseaseEntity.put(matchedNode, pe);
                    if (isLOFEntity(ef))
                        lofInstances.add(pe);
                }
            }
        }
        //TODO: May have to consider stoichiometries too!!!
        // In gain of functions, some inputs or outputs may not be used by normal reactions.
        // This method will do its best to map these reaction participants
        Collection<GKInstance> mapped = normalToDiseaseEntity.values();
        // First check inputs
        List<GKInstance> diseaseInputs = diseaseReaction.getAttributeValuesList(ReactomeJavaConstants.input);
        List<Node> normalNodes = normalReaction.getInputNodes();
        randomlyMapDiseaseNodesToNormalNodes(normalToDiseaseEntity, 
                                             mapped,
                                             diseaseInputs, 
                                             normalNodes);
        // Check outputs
        List<GKInstance> diseaseOutputs = diseaseReaction.getAttributeValuesList(ReactomeJavaConstants.output);
        normalNodes = normalReaction.getOutputNodes();
        randomlyMapDiseaseNodesToNormalNodes(normalToDiseaseEntity, 
                                             mapped, 
                                             diseaseOutputs, 
                                             normalNodes);
        // Check helpers
        GKInstance ca = (GKInstance) diseaseReaction.getAttributeValue(ReactomeJavaConstants.catalystActivity);
        if (ca != null) {
            List<GKInstance> catalysts = ca.getAttributeValuesList(ReactomeJavaConstants.physicalEntity);
            normalNodes = normalReaction.getHelperNodes();
            randomlyMapDiseaseNodesToNormalNodes(normalToDiseaseEntity, 
                                                 mapped, 
                                                 catalysts,
                                                 normalNodes);
        }
        // Check activators
        Collection<GKInstance> regulations = InstanceUtilities.getRegulations(diseaseReaction);
        if (regulations != null) {
            List<GKInstance> posRegulators = new ArrayList<GKInstance>();
            List<GKInstance> negRegulators = new ArrayList<GKInstance>();
            for (GKInstance regulation : regulations) {
                GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
                if (!regulator.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
                    continue; // Work with PE only
                if (regulation.getSchemClass().isa(ReactomeJavaConstants.PositiveRegulation)) {
                    posRegulators.add(regulator);
                }
                else if (regulation.getSchemClass().isa(ReactomeJavaConstants.NegativeRegulation)) {
                    negRegulators.add(regulator);
                }
            }
            if (posRegulators.size() > 0) {
                normalNodes = normalReaction.getActivatorNodes();
                randomlyMapDiseaseNodesToNormalNodes(normalToDiseaseEntity,
                                                     mapped,
                                                     posRegulators,
                                                     normalNodes);
            }
            if (negRegulators.size() > 0) {
                normalNodes = normalReaction.getInhibitorNodes();
                randomlyMapDiseaseNodesToNormalNodes(normalToDiseaseEntity,
                                                     mapped, 
                                                     negRegulators, 
                                                     normalNodes);
            }
        }
        return normalToDiseaseEntity;
    }

    private void randomlyMapDiseaseNodesToNormalNodes(Map<Node, GKInstance> normalToDiseaseEntity,
                                                     Collection<GKInstance> mapped,
                                                     List<GKInstance> diseaseInputs,
                                                     List<Node> normalNodes) throws Exception {
        if (diseaseInputs != null && diseaseInputs.size() > 0) {
            normalNodes.removeAll(normalToDiseaseEntity.keySet());
            if (normalNodes.size() > 0) {
                for (GKInstance input : diseaseInputs) {
                    //if (mapped.contains(input))
                    if (contains(input, mapped))
                        continue; // it has been mapped already
                    // Check if it has been used based on DB_ID
                    Node matched = null;
                    for (Node node : normalNodes) {
                        if (node.getReactomeId().equals(input.getDBID())) {
                            matched = node;
                            break;
                        }
                    }
                    if (matched == null) {
                        // Check possible match
                        Set<GKInstance> refEntities = getReferenceEntity(input);
                        matched = findBestPossibleMatch(normalNodes, refEntities);
                    }
                    if (matched == null) {
                        // Just pick up the first node
                        matched = normalNodes.get(0);
                    }
                    normalToDiseaseEntity.put(matched, input);
                    normalNodes.remove(matched);
                    if (normalNodes.size() == 0)
                        break;
                }
            }
        }
    }
    
    /**
     * Similar to the method contains(), this helper method is used to tackle the cache
     * issue with MySQLAdaptor. The checked collection is checked vs the target collection.
     * @param checked
     * @param target
     */
    private void retainAll(Collection<GKInstance> checked,
                           Collection<GKInstance> target) {
        for (Iterator<GKInstance> it = checked.iterator(); it.hasNext();) {
            GKInstance srcInst = it.next();
            if (!contains(srcInst, target)) {
                it.remove();
            }
        }
    }
    
    /**
     * In case where MySQLAdaptor cache is turned off, GKInstances having the same DB_IDs from the database
     * are different during different queries. Using this helper method to check if a GKInstance is contained
     * in a Collection<GKInstance> created in different time point. 
     * @param inst
     * @param c
     * @return
     */
    protected boolean contains(GKInstance inst, Collection<GKInstance> c) {
        for (GKInstance inst1 : c) {
            if (inst1.getDBID().equals(inst.getDBID()))
                return true;
        }
        return false;
    }
    
    protected boolean isLOFEntity(GKInstance ef) throws Exception {
        GKInstance pe = (GKInstance) ef.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        if (pe == null)
            return false;
        GKInstance functionalStatus = (GKInstance) ef.getAttributeValue(ReactomeJavaConstants.functionalStatus);
        if (functionalStatus != null) {
            String fsName = functionalStatus.getDisplayName();
            if (fsName.contains("loss_of_function") || fsName.contains("decreased_"))
                return true;
        }
        return false;
    }
    
    private Set<GKInstance> getReferenceEntity(GKInstance pe) throws Exception {
        return InstanceUtilities.grepReferenceEntitiesForPE(pe);
    }

    @Override
    public void paint(Graphics g) {
        //Clear the editor
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        
        // In case g is provided by other graphics context (e.g during exporting)
        g2.setFont(getFont());
        g2.setBackground(background);
        Dimension size = getPreferredSize();
        g2.clearRect(0, 0, size.width, size.height);
        // TODO: Delete the follow two statements. For test only
        //        g2.setPaint(Color.yellow);
        //        g2.fillRect(0, 0, size.width, size.height);
        // Enable zooming
        g2.scale(scaleX, scaleY);
        
        List comps = displayedObject.getComponents();
        // Draw nodes first
        if (comps == null || comps.size() == 0)
            return;
        java.util.List edges = new ArrayList();
        Rectangle clip = g.getClipBounds();
        // Draw Compartments first
        List<RenderableCompartment> compartments = drawCompartments(g, 
                                                                    comps, 
                                                                    null,
                                                                    clip);
        drawComponents(g, 
                       normalComps,
                       clip);
        if (isForNormal) {
            // Replace the original components with new set of Renderable objects for further processing
            RenderablePathway pathway = (RenderablePathway) displayedObject;
            List<Renderable> components = pathway.getComponents();
            components.clear();
            components.addAll(normalComps);
            components.addAll(compartments); // Don't forget them
            return;
        }
        // Draw a transparent background: light grey
        Color color = new Color(204, 204, 204, 175);
        g2.setPaint(color);
        //        Dimension size = getPreferredSize();
        // Preferred size has been scaled. Need to scale it back
        g2.fillRect(0, 
                    0, 
                    (int)(size.width / scaleX + 1.0d), 
                    (int)(size.height / scaleY + 1.0d));
        // Draw disease related objects
        for (Renderable r : diseaseComps) {
            if (lofNodes.contains(r))
                ((Node)r).setNeedDashedBorder(true);
        }
        drawComponents(g, 
                       diseaseComps,
                       clip);
        if (overlaidObjects.size() > 0) 
            drawComponents(g, 
                           overlaidObjects, 
                           clip,
                           true); // Want to draw edge first so that no validation is needed.
        if (crossedObjects.size() > 0)
            drawCrosses(g,
                        crossedObjects,
                        clip);
        RenderablePathway pathway = (RenderablePathway) displayedObject;
        pathway.setBgComponents(new ArrayList<Renderable>(normalComps));
        List<Renderable> fgComps = new ArrayList<Renderable>();
        fgComps.addAll(diseaseComps);
        fgComps.addAll(overlaidObjects);
        // Don't want to place compartments and pathway
        for (Iterator<Renderable> it = fgComps.iterator(); it.hasNext();) {
            Renderable r = it.next();
            if (r instanceof RenderableCompartment ||
                r instanceof RenderablePathway)
                it.remove();
        }
        pathway.setFgComponents(fgComps);
    }
    
    private boolean isNormalObject(Renderable r, Set<Long> normalIds) {
        if (r instanceof HyperEdge) {
            if (r instanceof FlowLine)
                return isFlowLineRelatedToSet((FlowLine)r, 
                                              normalIds);
            // Most likely the following statements should not be reached. But place
            // here just in case.
            if (r.getReactomeId() == null)
                return true;
            if (normalIds.contains(r.getReactomeId()))
                return true;
        }
        else if (r instanceof Node) {
            if (r instanceof ProcessNode)
                return true; // Should be used only for normal pathway.
            if (r.getReactomeId() == null)
                return true;
            // Check if it is linked to another disease entities
            Node node = (Node) r;
            List<HyperEdge> edges = node.getConnectedReactions();
            boolean isNormal = false;
            for (HyperEdge edge :edges) {
                if (edge instanceof FlowLine)
                    isNormal = isFlowLineRelatedToSet((FlowLine)edge, 
                                                      normalIds);
                if (isNormal)
                    return true;
                if (edge.getReactomeId() != null && normalIds.contains(edge.getReactomeId())) 
                    return true;
                // Some special cases that use links
                if (!(edge instanceof EntitySetAndEntitySetLink) && !(edge instanceof EntitySetAndMemberLink) && edge.getReactomeId() == null)
                    return true;
            }
        }
        return false;
    }
    
    private boolean isDiseaseObject(Renderable r, Set<Long> diseaseIds) {
        if (r instanceof FlowLine) {
            FlowLine fl = (FlowLine) r;
            return isFlowLineRelatedToSet(fl, diseaseIds);
        }
        // Assume normal objects only: this is a very strong assumption
        if (r.getReactomeId() == null)
            return false;
        // A PE may be represented multiple times in a pathway diagram. Some of them
        // are linked to normal reactions, and some linked to disease reactions.
        // Have to check these cases.
        if (r instanceof HyperEdge)
            return diseaseIds.contains(r.getReactomeId());
        if (r instanceof Node) {
            Node node = (Node) r;
            List<HyperEdge> edges = node.getConnectedReactions();
            boolean isDisease = false;
            for (HyperEdge edge : edges) {
                if (edge instanceof FlowLine) {
                    isDisease = isFlowLineRelatedToSet((FlowLine)edge, diseaseIds);
                }
                else if (edge.getReactomeId() != null && diseaseIds.contains(edge.getReactomeId())) {
                    isDisease = true;
                }
                if (isDisease)
                    return true;
            }
        }
        return false;
    }
    
    /**
     * If a FlowLine connects two normal object, it should be a normal object. Otherwise, it should
     * be a disease object.
     * @param flowLine
     * @param ids
     * @return
     */
    private boolean isFlowLineRelatedToSet(FlowLine flowLine, 
                                           Set<Long> ids) {
        Node input = flowLine.getInputNode(0);
        Node output = flowLine.getOutputNode(0);
        if (input != null && 
            (ids.contains(input.getReactomeId()) || input instanceof ProcessNode) && // ProcessNode should be used in normal pathway only
            output != null && 
            (ids.contains(output.getReactomeId()) || output instanceof ProcessNode))
            return true;
//        if (output != null && normalIds.contains(output.getReactomeId()))
//            return true;
        return false;
    }

    /**
     * Draw nodes first so that the final images are the same as in the curator tool.
     * Usually we should not see any difference regarding the order of drawing nodes 
     * or edges.
     * @param g
     * @param comps
     * @param clip
     */
    private void drawComponents(Graphics g, 
                                List<Renderable> comps,
                                Rectangle clip) {
        drawComponents(g, comps, clip, false);
    }
    
    private void drawComponents(Graphics g, 
                                Collection<Renderable> comps,
                                Rectangle clip,
                                boolean drawEdgeFirst) {
        List<HyperEdge> edges = new ArrayList<HyperEdge>();
        for (Renderable obj : comps) {
            if (obj instanceof HyperEdge)
                edges.add((HyperEdge)obj);
        }
        if (drawEdgeFirst) {
            // Draw HyperEdges
            for (HyperEdge reaction : edges) {
                // Have to validate connect nodes first in case empty bounds
                List<Node> nodes = reaction.getConnectedNodes();
                for (Node node : nodes)
                    node.validateBounds(g);
                reaction.validateConnectInfo();
                if (clip.intersects(reaction.getBounds()))
                    reaction.render(g);
            }
        }
        // Draw complexes now
        drawComplexes(new ArrayList<>(comps), clip, g);
        for (Renderable obj : comps) {
            if (obj instanceof RenderableCompartment ||
                obj instanceof RenderableComplex ||
                obj instanceof RenderablePathway)
                continue; // Escape it. It should be drawn earlier.
            if (obj instanceof Node) {
                Node node = (Node) obj;
                if (getHidePrivateNote() && (node instanceof Note) && ((Note)node).isPrivate())
                    continue;
                node.validateBounds(g);
                if (clip.intersects(node.getBounds()))
                    node.render(g);
            }
        }
        if (!drawEdgeFirst) {
            // Draw HyperEdges
            for (HyperEdge reaction : edges) {
                reaction.validateConnectInfo();
                if (clip.intersects(reaction.getBounds()))
                    reaction.render(g);
            }
        }
    }
    
    private void drawCrosses(Graphics g,
                             Set<Node> nodes,
                             Rectangle clip) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke oldStroke = g2.getStroke();
        Stroke stroke = new BasicStroke(DefaultRenderConstants.DEFAULT_RED_CROSS_WIDTH);
        g2.setStroke(stroke);
        Paint oldPaint = g2.getPaint();
        g2.setColor(Color.RED);
        for (Node node : nodes) {
            Rectangle r = node.getBounds();
            if (!clip.intersects(r))
                continue;
            g2.drawLine(r.x, r.y, r.x + r.width, r.y + r.height);
            g2.drawLine(r.x, r.y + r.height, r.x + r.width, r.y);
        }
        g2.setStroke(oldStroke);
        g2.setPaint(oldPaint);
    }
    
    /**
     * Find the normal pathway related to the passed disease pathway.
     * @param diseasePathway
     * @return
     * @throws Exception
     */
    private GKInstance findNormalPathway(GKInstance diseasePathway,
                                         GKInstance pdInst) throws Exception {
        GKInstance normalPathway = null;
        // Check if we should use the normal pathway slot
        if (diseasePathway.getSchemClass().isValidAttribute(ReactomeJavaConstants.normalPathway)) {
            normalPathway = (GKInstance) diseasePathway.getAttributeValue(ReactomeJavaConstants.normalPathway);
        }
        if (normalPathway != null)
            return normalPathway;
        // There should be a normal pathway contained by a disease pathway
        List<?> subPathways = diseasePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        // Get the disease pathway instance
        List<GKInstance> pathways = pdInst.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
        for (Object obj : subPathways) {
            GKInstance subPathway = (GKInstance) obj;
            GKInstance disease = (GKInstance) subPathway.getAttributeValue(ReactomeJavaConstants.disease);
            if (disease == null && contains(subPathway, pathways)) {
                normalPathway = subPathway;
                break;
            }
        }
        return normalPathway;
    }
    
    /**
     * Split objects into normal and disease components
     */
    private void splitObjects()  {
        normalIds = new HashSet<Long>();
        diseaseIds = new HashSet<Long>();
        RenderablePathway diagram = (RenderablePathway) getRenderable();
        if (pathway == null || diagram.getReactomeDiagramId() == null)
            return;
        try {
            GKInstance pdInst = adaptor.fetchInstance(diagram.getReactomeDiagramId());
            // Get the disease pathway instance
            List<GKInstance> pathways = pdInst.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
            
            GKInstance diseasePathway = null;
            GKInstance normalPathway = null;
            GKInstance disease = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.disease);
            if (disease == null && contains(pathway, pathways)) {
                normalPathway = pathway;
            }
            else {
                diseasePathway = pathway;
                // There should be a normal pathway contained by a disease pathway
                normalPathway = findNormalPathway(diseasePathway, pdInst);
            }
            if (normalPathway != null) {
                // Get all disease related objects
                Set<GKInstance> normalEvents = InstanceUtilities.grepPathwayEventComponents(normalPathway);
                for (GKInstance inst : normalEvents)
                    normalIds.add(inst.getDBID());
                Set<GKInstance> normalEntities = InstanceUtilities.grepPathwayParticipants(normalPathway);
                for (GKInstance inst : normalEntities)
                    normalIds.add(inst.getDBID());
                if (diseasePathway != null) {
                    Set<GKInstance> allEvents = InstanceUtilities.grepPathwayEventComponents(diseasePathway);
                    allEvents.removeAll(normalEvents);
                    for (GKInstance inst : allEvents) {
                        diseaseIds.add(inst.getDBID());
                        // Want to make sure disease pathways are connected to objects
                        if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                            Set<GKInstance> rxtEntities = InstanceUtilities.getReactionParticipants(inst);
                            for (GKInstance rxtEntity : rxtEntities) {
                                diseaseIds.add(rxtEntity.getDBID());
                            }
                        }
                    }
//                    Set<GKInstance> allEntities = InstanceUtilities.grepPathwayParticipants(diseasePathway);
//                    allEntities.removeAll(normalEntities);
//                    for (GKInstance inst : allEntities)
//                        diseaseIds.add(inst.getDBID());
                }
            }
            splitComponents();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * A helper method to split components into normal and disease components. Components in these
     * two lists don't contain compartments.
     */
    private void splitComponents() {
        normalComps = new ArrayList<Renderable>();
        diseaseComps = new ArrayList<Renderable>();
        List<Renderable> comps = displayedObject.getComponents();
        if (comps == null || comps.size() == 0)
            return;
        for (Renderable r : comps) {
            if (isNormalObject(r, normalIds)) 
                normalComps.add(r);
            // An entity may be used by both normal and disease reactions
            // In cases like this, this entity will be drawn twice.
            if (isDiseaseObject(r, diseaseIds)) // Multiple disease pathways may share the same pathway diagram. Some of components
                diseaseComps.add(r);                 // may not be contained by this disease pathway
        }
    }
    
}
