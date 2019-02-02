package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.junit.Test;

/**
 * This check is to find any PhysicalEntity drawn in a PathwayDigram, but not connected
 * to any ReactionlikeEvent instance. The results from this check should be the same, 
 * more or less, as ones reported by the diagram-converter T108, isolated glypgh QA checks. 
 * 
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class DiagramOrphanEntitiesCheck extends DiagramReactionsCheck {
    
    @Test
    public void testCheckInCommand() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        super.testCheckInCommand(dba);
    }

    @Override
    public String getDisplayName() {
        return "Diagram_With_Unconnected_Entities";
    }

    @Override
    protected String getIssueTitle() {
        return "Unconnected_Entity_DBIDs";
    }

    @Override
    protected Collection<Long> doCheck(GKInstance pathwayDiagramInst) throws Exception {
        RenderablePathway pathway = getRenderablePathway(pathwayDiagramInst);
        // Collect the isolated nodes.
        @SuppressWarnings("unchecked")
        Collection<Renderable> cmpnts = pathway.getComponents();
        if (cmpnts == null || cmpnts.size() == 0)
            return Collections.emptySet();
        Set<Long> connectedNodeIds = getConnectedNodeIds(pathway);
        return cmpnts.stream()
                     .filter(component -> isPhysicalEntityNode(component))
                     .map(Renderable::getReactomeId)
                     .filter(componentId -> !connectedNodeIds.contains(componentId))
                     .collect(Collectors.toSet());
    }
    
    private Set<Long> getConnectedNodeIds(RenderablePathway diagram) {
        // First get DB_IDs for all entities connected to ReactionlikeEvent edges
        Set<Node> connectedNodes = new HashSet<>();
        List<Renderable> components = diagram.getComponents();
        if (components != null) {
            for (Renderable r : components) {
                if (r instanceof HyperEdge) {
                    HyperEdge edge = (HyperEdge) r;
                    List<Node> nodes = edge.getConnectedNodes();
                    connectedNodes.addAll(nodes);
                }
            }
        }
        Set<Long> nodeIds = connectedNodes.stream()
                                          .filter(node -> node.getReactomeId() != null)
                                          .map(node -> node.getReactomeId())
                                          .collect(Collectors.toSet());
        return nodeIds;
    }

    @Override
    protected String getResultTableIssueDBIDColName() {
        return "Orphan Entity DB_IDs";
    }

}
