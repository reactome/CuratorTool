package org.gk.qualityCheck;

import java.util.Collection;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.RenderablePathway;
import org.junit.Test;

/**
 * This QA check verified that reactions contained by a Pathway have been
 * drawn in its PathwayDiagram.
 * 
 * @author wgm
 */
public class DiagramMissingReactionsCheck extends DiagramReactionsCheck {
    
    public DiagramMissingReactionsCheck() {
    }
    
    @Test
    public void testCheckInstance() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "test_slice_20181217",
                                            "loneyf",
                                            "fl3ky");
        setDatasource(dba);
        GKInstance diagram = dba.fetchInstance(987018L);
        Collection<Long> missingRxnDbIds = getIssueDbIds(diagram);
        System.out.println("Total missing ids: " + missingRxnDbIds.size());
    }
    
    @Override
    public String getDisplayName() {
        return "Diagram_Missing_Reactions";
    }

    /**
     * The missing reaction issue column header.
     */
    @Override
    protected String getIssueTitle() {
        return "Missing_Reaction_DBIDs";
    }

    /**
     * Validates that the given instance is a PathwayDiagram and
     * returns the diagram reaction <code>reactomeId</code> values
     * which are not in the represented Pathway event hierarchy.
     * 
     * @param instance the PathwayDiagram instance
     * @return the QA report issue db ids
     */
    protected Collection<Long> doCheck(GKInstance instance) throws Exception {
        RenderablePathway diagram = getRenderablePathway(instance);
        Collection<Long> diagramDbIds = extractDiagramReactionLikeDbIds(diagram);
        Collection<GKInstance> reactions = getReactions(instance, diagramDbIds);
        return reactions.stream()
                .map(GKInstance::getDBID)
                .filter(dbId -> !diagramDbIds.contains(dbId))
                .collect(Collectors.toSet());
    }
 
    @Override
    protected String getResultTableModelTitle() {
        return "Missing Reaction DB_IDs";
    }

}
