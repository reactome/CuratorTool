/*
 * Created on Oct 18, 2011
 *
 */
package org.gk.elv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.database.AttributeEditEvent;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderableEntitySet;
import org.gk.schema.InvalidAttributeException;

/**
 * A customized EvlInstanceEditHandler to process common tasks related to
 * PhysicalEntity editing.
 * @author gwu
 *
 */
public class ElvPhysicalEntityEditHandler extends ElvInstanceEditHandler {
    
    public ElvPhysicalEntityEditHandler() {
    }
    
    protected void physicalEntityEdit(AttributeEditEvent editEvent) throws InvalidAttributeException, Exception {
        GKInstance instance = editEvent.getEditingInstance();
        if (instance == null)
            return;
        List<Renderable> renderables = zoomableEditor.searchConvertedRenderables(instance);
        if (renderables == null || renderables.size() == 0)
            return;

        boolean isForDisease = (instance.getAttributeValue(ReactomeJavaConstants.disease) != null);
        if (isForDisease != renderables.get(0).getIsForDisease()) {
            for (Renderable renderable : renderables) {
                renderable.setIsForDisease(isForDisease);
                Node node = (Node) renderable;
                node.getConnectedReactions().forEach(reaction -> reaction.setIsForDisease(isForDisease));
            }
        }
    }

    /**
     * Check if a edit event resulted in a parent instance changing drug-renderable type.
     *
     * If the editing instance is a drug, and at least one of its rendered nodes
     * is not a drug  class, then refresh its own rendered node, as well as all parent nodes.
     *
     * Alternatively, if the editing instance is not a drug, and at least one of its rendered nodes
     * is a drug  class, then refresh its own rendered node, as well as all parent nodes.
     *
     * @param instance
     * @throws Exception
     */
    protected void checkForDrugChange(GKInstance instance, Node node) throws Exception {
        if (instance == null || node == null)
            return;
         if (!instance.getSchemClass().isa(ReactomeJavaConstants.Complex) &&
             !instance.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
             return;
        List<Node> parentNodes = getParentNodes(instance);
        boolean isForDrug = InstanceUtilities.hasDrug(instance);
        if (isForDrug != node.getIsForDrug())
            parentNodes.forEach(parentNode -> parentNode.setIsForDrug(isForDrug));
    }

    /**
     * Return a list of Complex/EntitySets that contain a given instance (including the instance's nodes themselves).
     *
     * This is a costly method by which we work "backwards" to find all parent instances of a given "childInstance":
     * (1) Iterate over all displayed objects in the current pathway.
     * (2) Only consider nodes that may contain other nodes (e.g. complexes, entity sets).
     * (3) Get all contained nodes for each parent node.
     * (4) If the child node is found within the contained nodes, add the parent node to the returned list.
     *
     * Note: this does not return a hierarchy of instances, simply an unordered Set.
     *
     * @param childInstance
     * @return List of Complex/EntitySet nodes that contain the instance represented by a given node.
     * @throws Exception
     */
    private List<Node> getParentNodes(GKInstance childInstance) throws Exception {
        if (childInstance == null)
            return null;

        List<?> displayedObjects = zoomableEditor.getPathwayEditor().getDisplayedObjects();
        if (displayedObjects.size() == 0)
            return null;

        // Limit to Complexes and EntitySets.
        List<Class<? extends Node>> allowedClasses = Arrays.asList(RenderableComplex.class, RenderableEntitySet.class);
        List<Node> parentNodes = new ArrayList<Node>();

        for (Object displayedObject : displayedObjects) {
            // Limit the search to allowed classes.
            if (allowedClasses.stream().noneMatch(allowedClass -> allowedClass.isInstance(displayedObject)))
                continue;
            Node parentNode = (Node) displayedObject;

            // Add the node representing the instance to the list.
            // TODO Is this preferred over an additional call to zoomableEditor.searchConvertedRenderables(instance)?
            if (parentNode.getReactomeId().equals(childInstance.getDBID())) {
                parentNodes.add(parentNode);
                continue;
            }

            // The parent Complex/EntitySet instance.
            GKInstance parentInstance = getInstanceFromNode(parentNode);

            // Populate the Set of a parent instance's contained instances.
            Set<GKInstance> childInstances = new HashSet<GKInstance>();
            childInstances = InstanceUtilities.getContainedInstances(parentInstance,
                                                                     ReactomeJavaConstants.hasComponent,
                                                                     ReactomeJavaConstants.hasCandidate,
                                                                     ReactomeJavaConstants.hasMember);
            // Determine if the given Complex/EntitySet contains the given child instance.
            // If so, add to the list of parent instances.
            if (childInstances.stream()
                              .map(GKInstance::getDBID)
                              .anyMatch(childInstance.getDBID()::equals))
                parentNodes.add((Node) displayedObject);
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
