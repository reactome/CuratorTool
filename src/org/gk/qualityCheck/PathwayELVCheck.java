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
    
    public PathwayELVCheck() {
    }
    
    /**
     * This method is used to check the usage of Pathway instances in a specified Database.
     * @param dba
     * @return key should Pathway instances that have been contained by other pathways, while values are lists 
     * of PathwayDiagram instances that contain pathways. 
     * Note: Not all pathways are in the key set.
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
        for (Iterator<?> it = diagrams.iterator(); it.hasNext();) {
            GKInstance diagram = (GKInstance) it.next();
            String xml = (String) diagram.getAttributeValue(ReactomeJavaConstants.storedATXML);
            if (xml == null || xml.length() == 0)
                continue; // Not correct diagrams!!!
            RenderablePathway renderableDiagram = reader.openDiagram(xml);
            // Check with edges
            List<?> components = renderableDiagram.getComponents();
            if (components == null || components.size() == 0)
                continue; // Just in case it is an empty!
            GKInstance pathway = (GKInstance) diagram.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (pathway == null)
                continue;
            Set<Long> dbIdsInELV = new HashSet<Long>();
            for (Iterator<?> it1 = components.iterator(); it1.hasNext();) {
                Renderable r = (Renderable) it1.next();
                if (r.getReactomeId() != null)
                    dbIdsInELV.add(r.getReactomeId());
            }
            Set<GKInstance> contained = InstanceUtilities.getContainedEvents(pathway);
            for (GKInstance sub : contained) {
                if (!sub.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    continue;
                }
                Set<GKInstance> reactions = InstanceUtilities.getContainedEvents(sub);
                if (reactions == null || reactions.size() == 0)
                    continue; // No need to check
                boolean isContained = isContainedBy(reactions, dbIdsInELV);
                if (isContained) {
                    // Need to register
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
