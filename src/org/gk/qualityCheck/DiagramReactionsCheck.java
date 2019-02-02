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
 * {@link AbstractPathwayDiagramCheck#doCheck(GKInstance)} method.
 * 
 * Checking a diagram does not check subpathways included in the
 * diagram which have their own diagram.
 * 
 * @author wgm
 */
public abstract class DiagramReactionsCheck extends AbstractPathwayDiagramCheck {
    
    protected DiagramReactionsCheck() {
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
        return getDisplayedEvents(diagram);
    }

    /**
     * Both RLEs and Pathways, which are drawn as ProcessNodes, are returned
     * from this method.
     * @param diagram
     * @return
     */
    protected Collection<Renderable> getDisplayedEvents(RenderablePathway diagram) {
        Set<Renderable> rtn = new HashSet<>();
        List<?> list = diagram.getComponents();
        if (list == null || list.size() == 0)
            return rtn; // To avoid null exception in the clients' code
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
     * Returns the <code>DB_IDs</code> values of event objects in the given diagram.
     * 
     * @param instance the PathwayDiagram instance
     * @return the db ids
     */
    protected Collection<Long> getDisplayedEventIds(RenderablePathway diagram) throws Exception {
        return getDisplayedEvents(diagram).stream()
                                          .map(Renderable::getReactomeId)
                                          .collect(Collectors.toSet());
    }

    /**
     * Returns the ReactomeRenderable objects in the given diagram.
     * 
     * @param instance the PathwayDiagram instance
     * @return the ReactomeRenderable objects
     */
    protected Collection<RenderableReaction> getDisplayedRLEes(RenderablePathway diagram) throws Exception {
        return getDisplayedEvents(diagram).stream()
                .filter(RenderableReaction.class::isInstance)
                .map(RenderableReaction.class::cast)
                .collect(Collectors.toSet());
    }
    
}
