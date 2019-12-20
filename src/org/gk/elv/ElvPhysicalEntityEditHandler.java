/*
 * Created on Oct 18, 2011
 *
 */
package org.gk.elv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.database.AttributeEditEvent;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.render.EntitySetAndEntitySetLink;
import org.gk.render.EntitySetAndMemberLink;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderableEntitySet;
import org.gk.util.GKApplicationUtilities;

/**
 * A customized EvlInstanceEditHandler to process common tasks related to
 * PhysicalEntity editing.
 * @author gwu
 *
 */
public class ElvPhysicalEntityEditHandler extends ElvInstanceEditHandler {
    
    public ElvPhysicalEntityEditHandler() {
    }
    
    protected void entitySetEdit(AttributeEditEvent editEvent) {
        if (!editEvent.getAttributeName().equals(ReactomeJavaConstants.hasMember) &&
            !editEvent.getAttributeName().equals(ReactomeJavaConstants.hasCandidate))
            return;
        GKInstance inst = editEvent.getEditingInstance();
        if (!inst.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
            return;
        List<Renderable> sets = zoomableEditor.searchConvertedRenderables(inst);
        if (sets == null || sets.size() == 0)
            return;
        if (editEvent.getEditingType() == AttributeEditEvent.ADDING) {
            List<GKInstance> addedInsts = editEvent.getAddedInstances();
            // Just in case
            if (addedInsts == null || addedInsts.size() == 0)
                return;
            // Need to add a links
            for (Renderable set : sets) {
                Node setNode = (Node) set;
                for (GKInstance addedInst : addedInsts) {
                    List<Renderable> members = zoomableEditor.searchConvertedRenderables(addedInst);
                    addEntitySetAndMemberLink(members, setNode);
                }
            }
            // Need to check if there is any more shared entities
            Map<GKInstance, List<Renderable>> instanceToNodes = getInstanceToNodes();
            for (GKInstance tmp : instanceToNodes.keySet()) {
                if (tmp == inst || !tmp.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
                    continue;
                if (InstanceUtilities.hasSharedMembers(inst, tmp))
                    addEntitySetAndEntitySetLink(sets, instanceToNodes.get(tmp));
            }
        }
        else if (editEvent.getEditingType() == AttributeEditEvent.REMOVING) {
            List<GKInstance> removedInsts = editEvent.getRemovedInstances();
            removeEntitySetAndMemberLink(sets, removedInsts);
            removeSetAndSetLink(inst, sets);
        }
        // Check if a edit event resulted in a parent instance changing drug-renderable type.

        // If the editing instance is a drug, and at least one of its rendered nodes
        // is not of a drug class, then refresh its own rendered node, as well as all parent nodes.

        // Alternatively, if the editing instance is not a drug, and at least one of its rendered nodes
        // is of a drug class, then refresh its own rendered node, as well as all parent nodes.
        try {
            boolean isForDrug = InstanceUtilities.isDrug(inst);
            if (isForDrug != ((Node) sets.get(0)).getIsForDrug()) {
                refreshParentNodes(inst, isForDrug);
                PathwayEditor pathwayEditor = zoomableEditor.getPathwayEditor();
                pathwayEditor.repaint(pathwayEditor.getVisibleRect());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * This method is called if some members in the set instance have been removed from editing.
     * @param set
     * @param setNodes
     */
    private void removeSetAndSetLink(GKInstance set,
                                       List<Renderable> setNodes) {
        XMLFileAdaptor fileAdaptor = zoomableEditor.getXMLFileAdaptor();
        for (Renderable r : setNodes) {
            if (!(r instanceof Node))
                continue;
            Node node = (Node) r;
            List<HyperEdge> edges = node.getConnectedReactions();
            for (HyperEdge edge : edges) {
                if (edge instanceof EntitySetAndEntitySetLink) {
                    List<Node> nodes = edge.getConnectedNodes();
                    nodes.remove(node);
                    for (Node tmp : nodes) {
                        GKInstance tmpInst = fileAdaptor.fetchInstance(tmp.getReactomeId());
                        if (!InstanceUtilities.hasSharedMembers(set, tmpInst))
                            zoomableEditor.getPathwayEditor().delete(edge);
                    }
                }
            }
        }
    }
    
    
    private void addEntitySetAndEntitySetLink(List<Renderable> setNodes1,
                                              List<Renderable> setNodes2) {
        for (Renderable r1 : setNodes1) {
            if (!(r1 instanceof Node))
                continue;
            Node node1 = (Node) r1;
            for (Renderable r2 : setNodes2) {
                if (!(r2 instanceof Node))
                    continue;
                Node node2 = (Node) r2;
                // Check if there is an EntitySetAndEntitySet exists between these two nodes
                if (hasSetAndSetLink(node1, node2))
                    continue;
                addSetAndSetLink(node1, node2);
            }
        }
    }
    
    private boolean hasSetAndSetLink(Node node1, Node node2) {
        List<HyperEdge> edges = node1.getConnectedReactions();
        for (HyperEdge edge : edges) {
            if (edge instanceof EntitySetAndEntitySetLink) {
                List<Node> nodes = edge.getConnectedNodes();
                if (nodes.contains(node2))
                    return true;
            }
        }
        return false;
    }
    
    private void removeEntitySetAndMemberLink(List<Renderable> sets,
                                              List<GKInstance> removedInsts) {
        Set<Long> dbIds = new HashSet<Long>();
        for (GKInstance inst : removedInsts)
            dbIds.add(inst.getDBID());
        for (Renderable set : sets) {
            Node setNode = (Node) set;
            List<HyperEdge> edges = setNode.getConnectedReactions();
            for (HyperEdge edge : edges) {
                if (edge instanceof EntitySetAndMemberLink) {
                    EntitySetAndMemberLink link = (EntitySetAndMemberLink) edge;
                    Node member = link.getMember();
                    if (dbIds.contains(member.getReactomeId())) {
                        zoomableEditor.getPathwayEditor().delete(edge);
                    }
                }
            }
        }
    }

    @Override
    public void postInsert(GKInstance instance, Renderable renderable) throws Exception {
        // Check if this instance is a member of other displayed GKInstance
        //  Get a list of displayed EntitySet instances, and check if this instance can be a 
        // member of any of EntitySet instances.
        Map<GKInstance, List<Renderable>> peToRenderables = getInstanceToNodes();
        for (GKInstance pe : peToRenderables.keySet()) {
            if (!pe.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
                continue;
            if (InstanceUtilities.isEntitySetAndMember(pe, instance))
                addEntitySetAndMemberLink(renderable, peToRenderables.get(pe));
        }
        // Check if other displayed instance can be a member of this instance
        if (instance.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            // Check any displayed PE is a member of this instance
            for (GKInstance pe : peToRenderables.keySet()) {
                if (InstanceUtilities.isEntitySetAndMember(instance, pe)) {
                    addEntitySetAndMemberLink(peToRenderables.get(pe), renderable);
                }
            }
        }
        // Check if any new EntitySet and EntitySet link should be added
        if (instance.getSchemClass().isa(ReactomeJavaConstants.EntitySet) && 
            renderable instanceof Node) {
            for (GKInstance pe : peToRenderables.keySet()) {
                if (InstanceUtilities.hasSharedMembers(instance, pe)) {
                    for (Renderable r : peToRenderables.get(pe)) {
                        if (!(r instanceof Node))
                            continue;
                        addSetAndSetLink((Node)renderable,
                                         (Node) r);
                    }
                }
            }
        }
    }
    
    private void addEntitySetAndMemberLink(Renderable member, List<Renderable> sets) {
        if (!(member instanceof Node))
            return;
        Node node = (Node) member;
        for (Renderable set : sets) {
            if (set instanceof Node)
                addEntitySetAndMemberLink(node, (Node)set);
        }
    }
    
    private void addEntitySetAndMemberLink(List<Renderable> members, Renderable set) {
        if (!(set instanceof Node))
            return;
        Node setNode = (Node) set;
        for (Renderable member : members) {
            if (!(member instanceof Node))
                continue;
            Node memberNode = (Node) member;
            addEntitySetAndMemberLink(memberNode, setNode);
        }
    }
    
    private void addEntitySetAndMemberLink(Node member, Node set) {
        EntitySetAndMemberLink link = new EntitySetAndMemberLink();
        link.setEntitySet(set);
        link.setMember(member);
        link.layout();
        PathwayEditor editor = zoomableEditor.getPathwayEditor();
        editor.insertEdge(link, false);
        editor.repaint(editor.getVisibleRect());
    }
    
    private void addSetAndSetLink(Node set1, Node set2) {
        EntitySetAndEntitySetLink link = new EntitySetAndEntitySetLink();
        link.setEntitySets(set1, set2);
        link.layout();
        PathwayEditor editor = zoomableEditor.getPathwayEditor();
        editor.insertEdge(link, false);
        editor.repaint(editor.getVisibleRect());
    }
    
    private Map<GKInstance, List<Renderable>> getInstanceToNodes() {
        Map<GKInstance, List<Renderable>> peToRenderables = new HashMap<GKInstance, List<Renderable>>();
        XMLFileAdaptor fileAdaptor = zoomableEditor.getXMLFileAdaptor();
        if (zoomableEditor.getPathwayEditor().getDisplayedObjects() != null) {
            for (Object obj : zoomableEditor.getPathwayEditor().getDisplayedObjects()) {
                Renderable r = (Renderable) obj;
                if (r.getReactomeId() != null) {
                    GKInstance inst = fileAdaptor.fetchInstance(r.getReactomeId());
                    if (inst != null && inst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity)) {
                        GKApplicationUtilities.insertValueToMap(peToRenderables, 
                                                                inst,
                                                                r);
                    }
                }
            }
        }
        return peToRenderables;
    }

    /**
     * Refresh (via reinserting) the nodes that contain a given instance (represented by its Reactome id).
     *
     * @param childInstance
     */
    protected void refreshParentNodes(GKInstance childInstance, boolean isForDrug) {
        // Limit to Complexes and EntitySets.
        List<Class<? extends Node>> allowedClasses = Arrays.asList(RenderableComplex.class,
                                                                   RenderableEntitySet.class);
        // The Complexes/EntitySets that contain the given instance.
        List<Node> parentNodes = getParentNodes(childInstance, allowedClasses);
        if (parentNodes == null || parentNodes.size() == 0)
            return;

        // Updated the affected instances
        for (Node parentNode : parentNodes)
            parentNode.setIsForDrug(isForDrug);
    }

    /**
     * Return a list of Complex/EntitySets that contain a given instance (including the instance's nodes themselves).
     *
     * This is a costly method by which we work "backwards" to find all parent instances of a given "childInstance":
     * (1) Iterate over all displayed objects in the current pathway.
     * (2) Only consider nodes that may contain other nodes (e.g. complexes, entity sets).
     * (3) Get all contained nodes for each parent node.
     * (4) If the contained node is found within the parent node, add that parent node to the returned list.
     *
     * Note: this does not return a hierarchy of instances, simply an unordered Set.
     *
     * @param node
     * @param allowedClasses
     * @return List of Complex/EntitySet nodes that contain the instance represented by a given node.
     */
    private List<Node> getParentNodes(GKInstance childInstance, List<Class<? extends Node>> allowedClasses) {
        if (allowedClasses == null || childInstance == null)
            return null;

        List<?> displayedObjects = zoomableEditor.getPathwayEditor().getDisplayedObjects();
        if (displayedObjects.size() == 0)
            return null;

        List<Node> parentNodes = new ArrayList<Node>();
        for (Object parentNode : displayedObjects) {
            // Limit the search to allowed classes.
            if (allowedClasses.stream()
                              .noneMatch(allowedClass -> allowedClass.isInstance(parentNode)))
                continue;

            // The parent Complex/EntitySet instance.
            GKInstance parentInstance = getInstanceFromNode((Node) parentNode);
            if (parentInstance == null)
                continue;

            // The set of child instances.
            Set<GKInstance> childInstances = new HashSet<GKInstance>();

            try {
                // Populate the Set of a parent instance's contained instances.
                childInstances = InstanceUtilities.getContainedInstances(parentInstance,
                                                                         ReactomeJavaConstants.hasComponent,
                                                                         ReactomeJavaConstants.hasCandidate,
                                                                         ReactomeJavaConstants.hasMember);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Add the node representing the instance to the list.
            // TODO Is this preferred over an additional call to zoomableEditor.searchConvertedRenderables(instance)?
            if (((Node) parentNode).getReactomeId().equals(childInstance.getDBID())) {
                parentNodes.add((Node) parentNode);
                continue;
            }

            // Determine if the given Complex/EntitySet contains the given child instance.
            // If so, add to the list of parent instances.
            if (childInstances.stream()
                              .map(GKInstance::getDBID)
                              .anyMatch(childInstance.getDBID()::equals))
                parentNodes.add((Node) parentNode);
        }

        return parentNodes;
    }

    /**
     * Return the instance represented by a given node.
     * TODO This very likely duplicates an existing method. I haven't been able to find it as of yet.
     *
     * @param node, the Node to get the instance of. Returns null if instance is not found.
     * @return instance pointed to by the given node.
     */
    private GKInstance getInstanceFromNode(Node node) {
        GKInstance instance = ((Node) node).getInstance();
        if (instance == null)
            instance = zoomableEditor.getXMLFileAdaptor().fetchInstance(node.getReactomeId());
        return instance;
    }
}
