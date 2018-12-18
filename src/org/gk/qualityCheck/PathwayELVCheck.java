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

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.junit.Test;

/**
 * This method is used to check (sub)Pathway contained by any PathwayDiagram.
 * @author wgm
 *
 */
public class PathwayELVCheck extends ReactionELVCheck {

    private static Logger logger = Logger.getLogger(PathwayELVCheck.class);
    
    public PathwayELVCheck() {
    }
    
    /**
     * This method is used to check the usage of Pathway instances in a
     * specified Database.
     * 
     * The return value is the {pathway: diagrams} map which associates
     * each pathway with the diagrams which represent that pathway's
     * event hierarchy. The keys in the map consist of all pathways which
     * are contained in another pathway's diagram. Each map value is a set
     * of PathwayDiagram instances. The members of this set are the diagrams
     * which represent the  contained in in the pathway key event
     * hierarchy.
     * 
     * Note: Not all pathways are in the key set.
     * 
     * @param dba
     * @return the {pathway: diagrams} map of all subpathway
          diagrams contained in the event hierarchy of the key
     * @throws Exception
     */
    @Override
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba) throws Exception {
        Map<GKInstance, Set<GKInstance>> pathwayToDiagrams = new HashMap<GKInstance, Set<GKInstance>>();
        // Load all PathwayDiagrams in the database
        Collection<?> diagrams = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        if (diagrams == null || diagrams.size() == 0)
            return pathwayToDiagrams;
        DiagramGKBReader reader = new DiagramGKBReader();
        // Collect the {pathway: diagrams} map which associates
        // a pathway key with the diagrams which represent the RLEs
        // in that pathway's event hierarchy.
        for (Iterator<?> it = diagrams.iterator(); it.hasNext();) {
            GKInstance diagram = (GKInstance) it.next();
            String xml = (String) diagram.getAttributeValue(ReactomeJavaConstants.storedATXML);
            if (xml == null || xml.length() == 0) {
                logger.error("Pathway diagram does not have XML: " +
                        diagram.getDisplayName() + "(DB_ID " +
                        diagram.getDBID() + ")");
                continue;
            }
            RenderablePathway renderableDiagram = reader.openDiagram(xml);
            // Check the edges
            List<?> components = renderableDiagram.getComponents();
            if (components == null || components.size() == 0)
                continue; // Just in case it is an empty!
            GKInstance pathway = (GKInstance) diagram.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (pathway == null)
                continue;
            // Collect the db ids of renderables in the diagram.
            Set<Long> dbIdsInELV = new HashSet<Long>();
            for (Iterator<?> it1 = components.iterator(); it1.hasNext();) {
                Renderable r = (Renderable) it1.next();
                if (r.getReactomeId() != null)
                    dbIdsInELV.add(r.getReactomeId());
            }
            // The events in the pathway event hierarchy.
            Set<GKInstance> contained = InstanceUtilities.getContainedEvents(pathway);
            // If a subpathway event hierarchy RLEs are represented
            // in the parent pathway diagram, then associate the
            // parent diagram with that subpathway.
            for (GKInstance sub : contained) {
                if (!sub.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    continue;
                }
                // Here, "reactions" are the events (RLEs and pathways) in the
                // subpathway event hierarchy.
                Set<GKInstance> reactions = InstanceUtilities.getContainedEvents(sub);
                if (reactions == null || reactions.size() == 0)
                    continue; // No need to check
                // If the db id of each RLE in the "reactions" variable is
                // contained in the db id set, then add the diagram to
                // the diagrams value associated with the subpathway key.
                boolean isContained = isContainedBy(reactions, dbIdsInELV);
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
     * @param reactions the events (RLEs and pathways) to check
     * @param dbIds the db ids to check
     * @return whether the db id of each RLE in the <em>reactions</em>
     *  argument is contained in the given db ids
     */
    private boolean isContainedBy(Set<GKInstance> reactions,
                                  Set<Long> dbIds) {
        for (GKInstance rxt : reactions) {
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
                                            "gk_central_112410",
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
