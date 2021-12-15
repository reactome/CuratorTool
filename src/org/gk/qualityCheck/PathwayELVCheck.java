/*
 * Created on Jan 3, 2011
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.junit.Test;

/**
 * This QA check associates (sub)pathways with diagrams which contain
 * them. There is one report line for each subpathway whose RLEs in its
 * event hierarchy are displayed as components of a diagram. The report
 * displays the subpathway and the diagrams which contain all of its
 * RLEs. Normally there should be only one such diagram.
 * 
 * @author wgm
 */
public class PathwayELVCheck extends ReactionELVCheck {

    private static Logger logger = LogManager.getLogger(PathwayELVCheck.class.getName());
    
    public PathwayELVCheck() {
    }
    
    /**
     * Replaces the superclass {@link ReactionELVCheck#getHeaders()}
     * <code>Reaction</code> occurrences with <code>Subpathway</code>.
     * 
     * @return the report headers
     */
    @Override
    protected String[] getHeaders() {
        String[] reactionCheckHdrs = super.getHeaders();
        return Stream.of(reactionCheckHdrs)
                .map(hdr -> hdr.replace("Reaction", "Pathway"))
                .collect(Collectors.toList())
                .toArray(new String[reactionCheckHdrs.length]);
    }

    /**
     * This method is used to check the usage of Pathway instances in a
     * specified Database.
     * 
     * The return value is the {subpathway: diagrams} map which associates
     * each subpathway with the diagrams which represent that pathway's
     * event hierarchy. The keys in the map consist of all pathways which
     * are contained in another pathway's diagram. Each map value is a set
     * of PathwayDiagram instances. The members of this set are the diagrams
     * which represent all events the key pathway's event hierarchy.
     * 
     * <em>Note</em>: Only pathways whose RLEs are entirely contained in
     * another diagram are in the return value. Pathways with their own diagram
     * are not in the map. Pathways whose RLEs are only partially represented
     * in another diagram are also not in the return value.
     * {@link ReactionSyncELVCheck} checks whether an RLE is not synchronized
     * with its database instance content.
     * 
     * @param dba
     * @return the {subpathway: diagrams} map
     * @throws Exception
     */
    @Override
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba) throws Exception {
        Map<GKInstance, Set<GKInstance>> pathwayToDiagrams = new HashMap<GKInstance, Set<GKInstance>>();
        // Load all PathwayDiagrams in the database
        Collection<?> diagrams = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        if (diagrams == null || diagrams.size() == 0)
            return pathwayToDiagrams;
        // Collect the {pathway: diagrams} map which associates
        // a pathway key with the diagrams which represent the RLEs
        // in that pathway's event hierarchy.
        for (Iterator<?> it = diagrams.iterator(); it.hasNext();) {
            GKInstance diagram = (GKInstance) it.next();
            GKInstance pathway = (GKInstance) diagram.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (pathway == null)
                continue;
            // Collect the db ids of renderables in the diagram.
            Set<Long> dbIdsInELV = getComponentDbIds(diagram);
            // The events in the pathway event hierarchy.
            Set<GKInstance> contained = InstanceUtilities.getContainedEvents(pathway);
            // If a subpathway event hierarchy RLEs are represented
            // in the parent pathway diagram, then associate the
            // parent diagram with that subpathway.
            for (GKInstance sub : contained) {
                if (!sub.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    continue;
                }
                
                Set<GKInstance> subEvents = InstanceUtilities.getContainedEvents(sub);
                if (subEvents == null || subEvents.size() == 0)
                    continue; // No need to check
                // If the db id of each Event in the "subEvents" variable is
                // contained in the db id set, then add the diagram to
                // the diagrams value associated with the subpathway key.
                boolean isContained = isContainedBy(subEvents, dbIdsInELV);
                if (isContained) {
                    // Add the diagram to the subpathway diagrams set.
                    Set<GKInstance> set = pathwayToDiagrams.get(sub);
                    if (set == null) {
                        set = new HashSet<GKInstance>();
                        pathwayToDiagrams.put(sub, set);
                    }
                    set.add(diagram);
                }
            }
        }
        return pathwayToDiagrams;
    }
    
    /**
     * @param diagram the PathwayDiagram instance
     * @return the db ids of companents contained in the diagram
     * @throws Exception
     */
    protected Set<Long> getComponentDbIds(GKInstance diagram) throws Exception {
        Set<Long> dbIds = new HashSet<Long>();
        String xml = (String) diagram.getAttributeValue(ReactomeJavaConstants.storedATXML);
        if (xml == null || xml.length() == 0) {
            logger.error("Pathway diagram does not have XML: " +
                    diagram.getDisplayName() + "(DB_ID " +
                    diagram.getDBID() + ")");
            return dbIds;
        }
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway renderableDiagram = reader.openDiagram(xml);
        // Check the edges
        List<?> components = renderableDiagram.getComponents();
        if (components == null || components.size() == 0)
            return dbIds; // Just in case it is an empty!
        // Collect the db ids of renderables in the diagram.
        for (Iterator<?> it1 = components.iterator(); it1.hasNext();) {
            Renderable r = (Renderable) it1.next();
            if (r.getReactomeId() != null)
                dbIds.add(r.getReactomeId());
        }
        return dbIds;
    }
    
    /**
     * @param events the events (RLEs and pathways) to check
     * @param dbIds the db ids to check
     * @return whether the db id of each RLE in the <em>reactions</em>
     *  argument is contained in the given db ids
     */
    protected boolean isContainedBy(Set<GKInstance> events,
                                    Set<Long> dbIds) throws Exception {
        for (GKInstance rxt : events) {
            if (rxt.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                if (!dbIds.contains(rxt.getDBID()))
                    return false;
            }
        }
        return true;
    }
        
    @Test
    public void testCheckPathwaysInELVs() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        long time1 = System.currentTimeMillis();
        Map<GKInstance, Set<GKInstance>> pathwayToDiagrams = checkEventUsageInELV(dba);
        long time2 = System.currentTimeMillis();
        System.out.println("Total pathways: " + pathwayToDiagrams.size());
        System.out.println("Time for checking: " + (time2 - time1));
        String output = convertEventToDiagramMapToText(pathwayToDiagrams);
        System.out.println("\n\nOutput:");
        System.out.println(output);
    }
    
    
}
