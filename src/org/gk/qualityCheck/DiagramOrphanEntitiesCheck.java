package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableReaction;
import org.junit.Test;

/**
 * This is the Curator QA adaptation of the diagram-converter T108
 * isolated glypgh QA checks. This check reports diagram node db ids
 * which are potentically a reaction participant not are not connected
 * to a diagram reaction.
 * 
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class DiagramOrphanEntitiesCheck extends DiagramReactionsCheck {
    
    @Test
    public void testCheckInstance() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "test_slice_20181217",
                                            "loneyf",
                                            "fl3ky");
        setDatasource(dba);
        GKInstance diagram = dba.fetchInstance(987018L);
        Collection<Long> extraDbIds = getIssueDbIds(diagram);
        System.out.println("Extra node db ids: " + extraDbIds);
    }

    @Override
    public String getDisplayName() {
        return "Diagram_Orphan_Entities";
    }

    @Override
    protected String getIssueTitle() {
        return "Orphan_Entity_DBIDs";
    }

    @Override
    protected Collection<Long> doCheck(GKInstance instance)
            throws Exception {
        RenderablePathway pathway = getRenderablePathway(instance);
        // The diagram reactions.
        Collection<RenderableReaction> rxnEdges = extractReactionRenderables(pathway);
        // The diagram reaction participants.
        Set<Long> ptcptDbIds = rxnEdges.stream()
                .map(RenderableReaction::getConnectedNodes)
                .flatMap(Collection::stream)
                .map(Renderable::getReactomeId)
                .collect(Collectors.toSet());
        // Collect the isolated nodes.
        @SuppressWarnings("unchecked")
        Collection<Renderable> cmpnts = pathway.getComponents();
        return cmpnts.stream()
                .filter(cmpnt -> isPhysicalEntityNode(cmpnt))
                .map(Renderable::getReactomeId)
                .filter(cmpnt -> !ptcptDbIds.contains(cmpnt))
                .collect(Collectors.toSet());
    }

    @Override
    protected String getResultTableModelTitle() {
        return "Orphan Entity DB_IDs";
    }

}
