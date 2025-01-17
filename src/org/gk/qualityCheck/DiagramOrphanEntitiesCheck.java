package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
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
 * @author Fred Loney loneyf@ohsu.edu
 */
public class DiagramOrphanEntitiesCheck extends DiagramReactionsCheck {
    
    @Test
    public void testCheckInCommand() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_041919",
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
        Collection<Renderable> components = pathway.getComponents();
        if (components == null || components.size() == 0)
            return Collections.emptySet();
        Set<Integer> connectedNodeIds = getConnectedNodeIds(components);
        
        return components.stream()
                     .filter(component -> isPhysicalEntityNode(component))
                     .filter(component -> !connectedNodeIds.contains(component.getID()))
                     .map(Renderable::getReactomeId)
                     .collect(Collectors.toSet());
    }
    
    private Set<Integer> getConnectedNodeIds(Collection<Renderable> components) {
        return components.stream()
                .filter(HyperEdge.class::isInstance)
                .map(HyperEdge.class::cast)
                .map(HyperEdge::getConnectedNodes)
                .flatMap(Collection::stream)
                .map(Node::getID)
                .collect(Collectors.toSet());
    }

    @Override
    protected String getResultTableIssueDBIDColName() {
        return "Orphan Entity DB_IDs";
    }

}
