/*
 * Created on Mar 11, 2011
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.HyperEdge;
import org.gk.render.ProcessNode;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableReaction;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;


/**
 * This is the base class for diagram reaction checks. Subclasses
 * perform a diagram check by implementing the
 * {@link PathwayDiagramCheck#doCheck(GKInstance)} method.
 * 
 * Checking a diagram does not check subpathways included in the
 * diagram which have their own diagram.
 * 
 * @author wgm
 */
public abstract class DiagramReactionsCheck extends PathwayDiagramCheck {
    protected DiagramReactionsCheck() {
    }

    /**
     * Returns the released reactions represented in the given PathwayDiagram
     * instance and diagram db ids.
     * 
     * @param instance the PathwayDiagram instance
     * @param dbIds the diagram reaction <code>reactomeId</code> values
     * @return the released reaction instances
     * @throws Exception
     */
    protected Collection<GKInstance> getReactions(GKInstance instance, Collection<Long> dbIds)
            throws InvalidAttributeException, Exception {
        if (dbIds == null || dbIds.size() == 0) {
            return Collections.emptySet(); // Nothing has been drawn yet in this diagram
        }
        if (!instance.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            throw new IllegalArgumentException(instance + " is not a PathwayDiagram instance!");
        GKInstance pathway = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.representedPathway);
        if (pathway == null) {
            return Collections.emptySet();
        }
        // Get all contained reactions
        Set<GKInstance> reactions = InstanceUtilities.grepPathwayEventComponents(pathway);
        filterOutDoNotReleaseEvents(reactions);
        // Check DB_IDs to see if any Pathway there
        GKInstance event = null;
        for (Long dbId : dbIds) {
            event = dataSource.fetchInstance(dbId);
            if (event == null && parentComp != null) {
                String msg = "Instance with DB_ID displayed in " + instance +
                        "\ncannot be found: " + dbId;
                JOptionPane.showMessageDialog(parentComp, msg, "Null Instance",
                        JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (event != null && event.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                Set<GKInstance> subPathwayRxns = InstanceUtilities.grepPathwayEventComponents(event);
                reactions.removeAll(subPathwayRxns);
            }
        }

        return reactions;
    }

    private void filterOutDoNotReleaseEvents(Set<GKInstance> events) throws Exception {
        if (events == null || events.size() == 0)
            return;
        for (Iterator<GKInstance> it = events.iterator(); it.hasNext();) {
            GKInstance event = it.next();
            // Only need to check reactions having been released
            Boolean _doRelease = (Boolean) event.getAttributeValue(ReactomeJavaConstants._doRelease);
            if (_doRelease == null || !_doRelease)
                it.remove();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        super.loadAttributes(instances);
        // Need to load all Pathways and its contained Events
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        // To check pathways
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.PathwayDiagram);
        SchemaAttribute att = cls.getAttribute(ReactomeJavaConstants.representedPathway);
        dba.loadInstanceAttributeValues(instances, att);
        if (progressPane != null)
            progressPane.setText("Load Events and their attributes...");
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway);
        cls = dba.getSchema().getClassByName(ReactomeJavaConstants.Pathway);
        att = cls.getAttribute(ReactomeJavaConstants.hasEvent);
        dba.loadInstanceAttributeValues(c, att);
        att = cls.getAttribute(ReactomeJavaConstants._doRelease);
        dba.loadInstanceAttributeValues(c, att);
        c = dba.fetchInstancesByClass(ReactomeJavaConstants.Reaction);
        cls = dba.getSchema().getClassByName(ReactomeJavaConstants.ReactionlikeEvent);
        if (cls.isValidAttribute(ReactomeJavaConstants.hasMember)) {
            att = cls.getAttribute(ReactomeJavaConstants.hasMember);
            dba.loadInstanceAttributeValues(c, att);
        }
        att = cls.getAttribute(ReactomeJavaConstants._doRelease);
        dba.loadInstanceAttributeValues(c, att);
    }

    /**
     * Returns the HyperEdge and ProcessNode renderables in the given diagram.
     * 
     * @param instance the PathwayDiagram instance
     */
    protected Collection<Renderable> extractDiagramReactionLikeNodes(GKInstance instance)
            throws Exception {
        RenderablePathway diagram = getRenderablePathway(instance);
        return extractDiagramReactionLikeNodes(diagram);
    }

    protected Collection<Renderable> extractDiagramReactionLikeNodes(
            RenderablePathway diagram) {
        List<?> list = diagram.getComponents();
        if (list == null || list.size() == 0)
            return null;
        Set<Renderable> rtn = new HashSet<Renderable>();
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            Object obj = it.next();
            if (obj instanceof HyperEdge || obj instanceof ProcessNode) {
                Renderable r = (Renderable) obj;
                if (r.getReactomeId() != null)
                    rtn.add(r);
            }
        }
        return rtn;
    }

    /**
     * Returns the <code>reactomeId</code> values of HyperEdge and ProcessNode
     * renderables in the given diagram.
     * 
     * @param instance the PathwayDiagram instance
     * @return the db ids
     */
    protected Collection<Long> extractDiagramReactionLikeDbIds(RenderablePathway diagram)
            throws Exception {
        return extractDiagramReactionLikeNodes(diagram).stream()
                .map(Renderable::getReactomeId).collect(Collectors.toSet());
    }

    /**
     * Returns the ReactomeRenderable objects in the given diagram.
     * 
     * @param instance the PathwayDiagram instance
     * @return the ReactomeRenderable objects
     */
    protected Collection<RenderableReaction> extractReactionRenderables(RenderablePathway diagram)
            throws Exception {
        return extractDiagramReactionLikeNodes(diagram).stream()
                .filter(RenderableReaction.class::isInstance)
                .map(RenderableReaction.class::cast)
                .collect(Collectors.toSet());
    }
    
}
