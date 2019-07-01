package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

/**
 * This QA check reports pathways without an associated diagram. A pathway is regarded
 * as having an associated diagram if it has its own PathwayDiagram or its contained RLEs
 * are drawn in its ancestor pathway diagram.
 *
 * @author Fred Loney <loneyf@ohsu.edu> & Guanming Wu <wug@ohsu.edu>
 */
public class PathwayHasNoDiagramCheck extends PathwayELVCheck {

    private static final String[] LOAD_ATTS = { 
            ReactomeJavaConstants.species,
            ReactomeJavaConstants.hasEvent
    };

    private static final String[] LOAD_REVERSE_ATTS = { 
            ReactomeJavaConstants.hasEvent,
            ReactomeJavaConstants.representedPathway
    };
    
    private static final String[] HEADERS = {
            "Pathway_DBID",
            "Pathway_DisplayName",
            "Species",
            "Issue",
            "Created",
            "Modified"
    };
    
    @Override
    protected String[] getHeaders() {
        return HEADERS;
    }

    @Override
    public String getDisplayName() {
        return "Pathway_Without_Diagram";
    }
    
    /**
     * Overrides the superclass
     * {@link PathwayELVCheck#checkEventUsageInELV(MySQLAdaptor)}
     * method to collect offended pathways to report. The report issue
     * is retained in a {subpathway: detail} map for subsequent
     * reporting in {@link #addEventToDiagramMapToReport(QAReport, Map)}.
     *  
     * The return value is a map whose keys are the subpathways to
     * report. The map values are unused; thus the map is effectively
     * a key set.
     * 
     * @param dba
     * @return the {subpathway: empty set} map
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba) throws Exception {
        Map<GKInstance, Set<GKInstance>> rtn = new HashMap<>();
        // The {pathway: diagrams} map of all subpathway diagrams contained in the
        // event hierarchy of the key.
        Map<GKInstance, Set<GKInstance>> subpathwayUsage = super.checkEventUsageInELV(dba);
        // Load all Pathways in the database.
        Collection<GKInstance> pathways = dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway);
        // Remove pathways represented in another diagram.
        pathways.removeIf(pathway -> subpathwayUsage.containsKey(pathway));
        dba.loadInstanceAttributeValues(pathways, LOAD_ATTS);
        dba.loadInstanceReverseAttributeValues(pathways, LOAD_REVERSE_ATTS);
        for (GKInstance pathway: pathways) {
            // We want to check all pathways regardless their species
            // Check if a pathway has its own diagram
            // As of July 1, 2019, check human pathways only
            GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
            if (!species.getDisplayName().equals("Homo sapiens"))
                continue;
            Collection<GKInstance> diagrams = pathway.getReferers(ReactomeJavaConstants.representedPathway);
            if (diagrams != null && diagrams.size() > 0)
                continue;
            // If a pathway doesn't have its own diagram, then report it.
            // The super class checks should catch pathways that are embedded
            // in other diagrams.
            rtn.put(pathway, Collections.EMPTY_SET);
        }
        return rtn;
    }

    @Override
    protected void addEventToDiagramMapToReport(QAReport report,
                                                Map<GKInstance, Set<GKInstance>> eventToDiagrams) throws Exception {
        if (eventToDiagrams.size() == 0)
            return;
        report.setColumnHeaders(HEADERS);

        // eventToDiagrams is the {pathway: empty set} map. 
        //             "Pathway_DBID",
        //        "Pathway_DisplayName",
        //        "Issue",
        //        "Created",
        //        "Modified"
        List<GKInstance> pathways = new ArrayList<>(eventToDiagrams.keySet());
        InstanceUtilities.sortInstances(pathways);
        for (GKInstance pathway : pathways) {
            GKInstance created =
                    (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.created);
            GKInstance modified = QACheckUtilities.getLatestCuratorIEFromInstance(pathway);
            GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
            report.addLine(pathway.getDBID() + "",
                           pathway.getDisplayName(),
                           species == null ? "No Species" : species.getDisplayName(),
                           "No Diagram Associated",
                           created == null ? "No Created" : created.getDisplayName(),
                           modified == null ? "No Modified" : modified.getDisplayName());

        }
    }
    
    @Override
    protected boolean isContainedBy(Set<GKInstance> events, Set<Long> dbIds) throws Exception {
        Set<GKInstance> copy = new HashSet<>(events);
        PersistenceAdaptor dba = getDatasource();
        for (Long dbId : dbIds) {
            GKInstance drawnInst = dba.fetchInstance(dbId);
            if (drawnInst == null)
                continue;
            if (drawnInst.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                Set<GKInstance> comps = InstanceUtilities.getContainedEvents(drawnInst);
                copy.removeAll(comps);
            }
            else
                copy.remove(drawnInst);
        }
        if (copy.size() == 0)
            return true;
        // In case there is something left
        for (GKInstance inst : copy) {
            if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                return false; // We have to make sure all RLEs are drawn
        }
        // If they are all Pathways
        for (GKInstance inst : copy) {
            if (inst.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                // If a pathway is empty, then it must be drawn here
                List<GKInstance> comps = inst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                if (comps == null || comps.size() == 0)
                    return false;
            }
        }
        // This will happen if only pathways are left
        return true; 
    }

    @Test
    public void testCheckInCommand() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "test_slice",
                                            "root",
                                            "macmysql01");
        super.testCheckInCommand(dba);
    }

}
