package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
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
    public void testCheckInCommandTest() throws Exception {
        testCheckInCommand(false);
        testCheckInCommand(true);
    }

    private void testCheckInCommand(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost",
                    "gk_central_122118",
                    "root",
                    "macmysql01");
        super.testCheckInCommand(dba);
    }

    @Test
    public void testCheckOneDiagramTest() throws Exception {
        testCheckOneDiagram(false);
        testCheckOneDiagram(true);
    }
    
    private void testCheckOneDiagram(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost",
                    "gk_central_122118",
                    "root",
                    "macmysql01");
        setDatasource(dba);
        GKInstance diagram = dba.fetchInstance(8939215L);
        checkInstance(diagram);
        Collection<Long> missingRxnDbIds = getIssueDbIds(diagram);
        System.out.println("Total missing ids: " + (missingRxnDbIds == null ? 0 : missingRxnDbIds.size()));
    }
    
    @Override
    public String getDisplayName() {
        return "Diagram_With_Missing_Reactions";
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
     * @param pathwayDiagramInst the PathwayDiagram instance
     * @return the QA report issue db ids
     */
    protected Collection<Long> doCheck(GKInstance pathwayDiagramInst) throws Exception {
        RenderablePathway diagram = getRenderablePathway(pathwayDiagramInst);
        Collection<Long> displayedEventIds = getDisplayedEventIds(diagram);
        Collection<GKInstance> reactions = getRLEsForDisplay(pathwayDiagramInst,
                                                             displayedEventIds);
        return reactions.stream()
                        .map(GKInstance::getDBID)
                        .filter(dbId -> !displayedEventIds.contains(dbId))
                        .collect(Collectors.toSet());
    }
    
    /**
     * Returns the released ReactionlikeEvent instances annotated for the Pathway instance that is drawn 
     * by the passed pathwayDiagramInst, which should be drawn in the pathwayDiagramInst, recursively.
     * A ReactionLikeEvent should be drawn directly in a diagram if it is contained by a Pathway
     * recursively, but not by a sub-pathway or sub-sub-pathway drawn as a ProcessNode in the diagram.
     * 
     * @param pathwayDiagramInst the PathwayDiagram instance
     * @param displayedEventIds the diagram reaction <code>reactomeId</code> values
     * @return the released reaction instances that should be drawn in the diagram.
     * @throws Exception
     */
    private Collection<GKInstance> getRLEsForDisplay(GKInstance pathwayDiagramInst,
                                                      Collection<Long> displayedEventIds) throws Exception {
        if (!pathwayDiagramInst.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            throw new IllegalArgumentException(pathwayDiagramInst + " is not a PathwayDiagram instance!");
        // The following check is not right. If a Pathway has RLEs annotated, but nothing drawn,
        // then all RLEs annotated should be returned.
//        if (displayedEventIds == null || displayedEventIds.size() == 0) {
//            return Collections.emptySet(); // Nothing has been drawn yet in this diagram
//        }
        // Only the first normal pathway will be returned in this pathwayDiagramInst if
        // shared between normal pathway and one or more disease pathways. The normal pathway
        // is expected in the first place.
        GKInstance pathway = (GKInstance) pathwayDiagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
        if (pathway == null) {
            return Collections.emptySet();
        }
        // Get all contained RLEs
        Set<GKInstance> rles = InstanceUtilities.grepPathwayEventComponents(pathway);
        filterOutDoNotReleaseEvents(rles);
        // Check DB_IDs to see if any Pathway there
        GKInstance event = null;
        for (Long dbId : displayedEventIds) {
            event = dataSource.fetchInstance(dbId);
            if (event == null && parentComp != null) {
                String msg = "Instance with DB_ID displayed in " + pathwayDiagramInst +
                        "\ncannot be found: " + dbId;
                JOptionPane.showMessageDialog(parentComp, 
                                              msg, 
                                              "Null Instance",
                                              JOptionPane.ERROR_MESSAGE);
                continue;
            }
            // Means this pathway is drawn as a ProcessNode. Therefore its contained RLEs can be
            // removed for display.
            if (event != null && event.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                Set<GKInstance> subPathwayRxns = InstanceUtilities.grepPathwayEventComponents(event);
                rles.removeAll(subPathwayRxns);
            }
        }
        return rles;
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
 
    @Override
    protected String getResultTableIssueDBIDColName() {
        return "Missing Reaction DB_IDs";
    }

}
