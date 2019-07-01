/*
 * Created on Apr 25, 2014
 *
 */
package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableReaction;
import org.junit.Test;

/**
 * This class is used to check if a drawn reaction is synchronized with the actually annotated
 * reaction in the database. Sometimes because of editing, a drawn reaction may be out of synchronization
 * with one in the database. Only one issue will be returned for an offended ReactionlikeEvent instance even
 * though it may have multiple issues. The curator should check out PathwayDiagrams and then reopen it with 
 * the curator tool. In most cases, the diagram will be fixed automatically in the local project.
 * @author gwu
 *
 */
public class ReactionSyncELVCheck extends ReactionELVCheck {
    private static final Logger logger = Logger.getLogger(ReactionSyncELVCheck.class);

    private static final String[] HEADERS = {
            "Diagram_DBID", 
            "Pathway_DisplayName", 
            "Pathway_DBID",
            "Reaction_DBID", 
            "Issue", 
            "Created", 
            "Modified"
    };
    
    // Keep the check results for display: key = dbId of PD.dbId of RLE, 
    // value is one issue. A RLE may have multiple issue, however, in this check
    // there will be only one issue reported.
    private Map<String, String> dbIdsToIssue;

    /**
     * Default constructor.
     */
    public ReactionSyncELVCheck() {
    }
    
    @Override
    public String getDisplayName() {
        return "Diagram_Unsynchronized_Reactions";
    }

    /**
     * This method overrides {@link ReactionELVCheck#checkEventUsageInELV(MySQLAdaptor)}
     * to build the database instance {diagram: unmatched reactions} map.
     * 
     * @param dba
     * @return the {diagram: unmatched reactions} map
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba) throws Exception {
        dbIdsToIssue = new HashMap<>();
        // Build the map.
        Collection<GKInstance> pds = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        Map<GKInstance, Set<GKInstance>> pdToRxts = new HashMap<GKInstance, Set<GKInstance>>();
        for (GKInstance pd : pds) {
            GKInstance pathway = (GKInstance) pd.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (pathway == null)
                continue; // Nothing to be checked with
            Set<GKInstance> unMatchedIds = getUnMatchedReactions(pd);
            if (unMatchedIds == null || unMatchedIds.size() == 0)
                continue;
            pdToRxts.put(pd, unMatchedIds);
        }
        return pdToRxts;
    }

    @Override
    public String convertEventToDiagramMapToText(Map<GKInstance, Set<GKInstance>> pdToRxts) throws Exception {
        StringBuilder builder = new StringBuilder();
        String header = String.join("\t", HEADERS);
        builder.append(header);
        builder.append("\n");
        List<GKInstance> pdList = new ArrayList<GKInstance>(pdToRxts.keySet());
        InstanceUtilities.sortInstances(pdList);
        for (GKInstance pd : pdList) {
            GKInstance pathway =
                    (GKInstance) pd.getAttributeValue(ReactomeJavaConstants.representedPathway);
            Set<GKInstance> rxts = pdToRxts.get(pd);
            for (GKInstance rle : rxts) {
                GKInstance modified = QACheckUtilities.getLatestCuratorIEFromInstance(rle);
                GKInstance created = (GKInstance) rle.getAttributeValue(ReactomeJavaConstants.created);
                String issue = dbIdsToIssue.get(pd.getDBID() + "." + rle.getDBID());
                String[] colValues = new String[] {
                        pd.getDBID().toString(),
                        pathway.getDisplayName(),
                        pathway.getDBID().toString(),
                        rle.getDBID() + "",
                        issue == null ? "" : issue,
                        created.getDisplayName(),
                        modified.getDisplayName()
                };
                String detail = String.join("\t", colValues);
                builder.append(detail);
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private Set<GKInstance> getUnMatchedReactions(GKInstance diagram) throws Exception {
        Set<GKInstance> unmatched = new HashSet<GKInstance>();
        // Get all contained Event ids by this Pathway
        Set<RenderableReaction> edges = grepHyperEdges(diagram);
        if (edges == null || edges.size() == 0)
            return unmatched;
        PersistenceAdaptor dataSource = diagram.getDbAdaptor();
        for (RenderableReaction edge : edges) {
            GKInstance inst = dataSource.fetchInstance(edge.getReactomeId());
            if (inst == null) {
                logger.error("Event with DB_ID in " + diagram + "\n cannot be found: " + edge.getReactomeId());
                continue; 
            }
            if (!isEdgeAndReactionSame(inst, 
                                       edge,
                                       diagram))
                unmatched.add(inst);
        }
        return unmatched;
    }
    
    @SuppressWarnings("unchecked")
    private boolean isEdgeAndReactionSame(GKInstance instance,
                                          RenderableReaction rxt,
                                          GKInstance pd) throws Exception {
        // Fist check if they have the same inputs
        List<GKInstance> inputs = instance.getAttributeValuesList(ReactomeJavaConstants.input);
        List<Node> inputNodes = rxt.getInputNodes();
        Map<Renderable, Integer> inputToStoi = rxt.getInputStoichiometries();
        if (!checkValuesAndNodes(inputs, inputNodes, inputToStoi)) {
            dbIdsToIssue.put(pd.getDBID() + "." + instance.getDBID(),
                             "input out of sync");
            return false;
        }
        // Check output 
        List<GKInstance> outputs = instance.getAttributeValuesList(ReactomeJavaConstants.output);
        List<Node> outputNodes = rxt.getOutputNodes();
        Map<Renderable, Integer> outputToStoi = rxt.getOutputStoichiometries();
        if (!checkValuesAndNodes(outputs, outputNodes, outputToStoi)) {
            dbIdsToIssue.put(pd.getDBID() + "." + instance.getDBID(),
                    "output out of sync");
            return false;
        }
        // Check Catalysts
        List<GKInstance> cas = instance.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        // Since there is no stoichiometry for catalysts, use set here
        Set<GKInstance> catalysts = new HashSet<GKInstance>();
        for (GKInstance ca : cas) {
            GKInstance catalyst = (GKInstance) ca.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (catalyst != null)
                catalysts.add(catalyst);
        }
        List<Node> catalystNodes = rxt.getHelperNodes();
        // Just an empty map in order to use the refactored method
        Map<Renderable, Integer> emptyMap = new HashMap<Renderable, Integer>();
        if (!checkValuesAndNodes(catalysts, catalystNodes, emptyMap)) { 
            dbIdsToIssue.put(pd.getDBID() + "." + instance.getDBID(),
                    "catalyst out of sync");
            return false;
        }
        // Check activators
        Collection<GKInstance> regulations = InstanceUtilities.getRegulations(instance);
        // Sets for regulators since there is no stoichiometry for them
        Set<GKInstance> activators = new HashSet<GKInstance>();
        Set<GKInstance> inhibitors = new HashSet<GKInstance>();
        if (regulations != null && regulations.size() > 0) {
            for (GKInstance regulation : regulations) {
                GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
                if (regulator == null)
                    continue;
                if (regulation.getSchemClass().isa(ReactomeJavaConstants.PositiveRegulation)) 
                    activators.add(regulator);
                else if (regulation.getSchemClass().isa(ReactomeJavaConstants.NegativeRegulation))
                    inhibitors.add(regulator);
            }
        }
        List<Node> activatorNodes = rxt.getActivatorNodes();
        if (!checkValuesAndNodes(activators, activatorNodes, emptyMap)) { 
            dbIdsToIssue.put(pd.getDBID() + "." + instance.getDBID(),
                    "activator out of sync");
            return false;
        }
        List<Node> inhibitorNodes = rxt.getInhibitorNodes();
        if (!checkValuesAndNodes(inhibitors, inhibitorNodes, emptyMap)) {
            dbIdsToIssue.put(pd.getDBID() + "." + instance.getDBID(),
                            "inhibitor out of sync");
            return false;
        }
        return true;
    }
    
    /**
     * @param values the data source instances to check
     * @param nodes the diagram nodes to check
     * @param nodeToStoi the {node: count} stoichiometry map
     * @return whether the node reactomeIds and instance db ids match in
     *  both occurence and count
     * @throws Exception
     */
    private boolean checkValuesAndNodes(Collection<GKInstance> values,
                                        List<Node> nodes,
                                        Map<Renderable, Integer> nodeToStoi) throws Exception {
        // Easy comparison using DB_IDs. Also avoid caching issue by using DB_IDs.
        List<Long> valueIds = new ArrayList<Long>();
        for (GKInstance inst : values)
            valueIds.add(inst.getDBID());
        Collections.sort(valueIds);
        // Get ids from pathway diagram
        List<Long> nodeIdsInDiagram = new ArrayList<Long>();
        for (Node node : nodes) {
            Integer stoi = nodeToStoi.get(node);
            if (stoi == null)
                stoi = 1; // As the minimum
            for (int i = 0; i < stoi; i++)
                nodeIdsInDiagram.add(node.getReactomeId());
        }
        Collections.sort(nodeIdsInDiagram);
        return valueIds.equals(nodeIdsInDiagram);
    }

    
    /**
     * Grep a set of HyperEdge objects drawn in the passed PathwayDiagram instance.
     * @param instance
     * @return
     * @throws Exception
     */
    private Set<RenderableReaction> grepHyperEdges(GKInstance instance) throws Exception {
        String xml = (String) instance.getAttributeValue(ReactomeJavaConstants.storedATXML);
        if (xml == null || xml.length() == 0)
            return null;
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway diagram = reader.openDiagram(xml);
        List<?> list = diagram.getComponents();
        if (list == null || list.size() == 0)
            return null;
        Set<RenderableReaction> rtn = new HashSet<RenderableReaction>();
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            Object obj = it.next();
            if (obj instanceof RenderableReaction) {
                RenderableReaction r = (RenderableReaction) obj;
                if (r.getReactomeId() != null)
                    rtn.add(r);
            }
        }
        return rtn;
    }
    
    @Test
    public void testReactionSynCheck() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_010914", 
                                            "root",
                                            "macmysql01");
        Map<GKInstance, Set<GKInstance>> pdToRxts = checkEventUsageInELV(dba);
        String output = convertEventToDiagramMapToText(pdToRxts);
        System.out.println(output);
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
