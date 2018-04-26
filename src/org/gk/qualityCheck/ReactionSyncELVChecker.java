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
 * reaction in the database. Sometimes because of editing, a drawn reaction may be out of syncrhonization
 * with one in the database.
 * @author gwu
 *
 */
public class ReactionSyncELVChecker extends ReactionELVChecker {
    private static final Logger logger = Logger.getLogger(ReactionSyncELVChecker.class);
    
    /**
     * Default constructor.
     */
    public ReactionSyncELVChecker() {
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba) throws Exception {
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
        builder.append("DB_ID\tDisplayName\tUnsynchronized Reaction IDs");
        List<GKInstance> pdList = new ArrayList<GKInstance>(pdToRxts.keySet());
        InstanceUtilities.sortInstances(pdList);
        for (GKInstance pd : pdList) {
            builder.append("\n").append(pd.getDBID()).append("\t");
            builder.append(pd.getDisplayName()).append("\t");
            Set<GKInstance> rxts = pdToRxts.get(pd);
            for (Iterator<GKInstance> it = rxts.iterator(); it.hasNext();) {
                GKInstance rxt = it.next();
                builder.append(rxt.getDBID());
                if (it.hasNext())
                    builder.append(", ");
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
            if (!isEdgeAndReactionSame(inst, edge))
                unmatched.add(inst);
        }
        return unmatched;
    }
    
    @SuppressWarnings("unchecked")
    private boolean isEdgeAndReactionSame(GKInstance instance,
                                          RenderableReaction rxt) throws Exception {
        // Fist check if they have the same inputs
        List<GKInstance> inputs = instance.getAttributeValuesList(ReactomeJavaConstants.input);
        List<Node> inputNodes = rxt.getInputNodes();
        Map<Renderable, Integer> inputToStoi = rxt.getInputStoichiometries();
        if (!checkValuesAndNodes(inputs, inputNodes, inputToStoi))
            return false;
        // Check output 
        List<GKInstance> outputs = instance.getAttributeValuesList(ReactomeJavaConstants.output);
        List<Node> outputNodes = rxt.getOutputNodes();
        Map<Renderable, Integer> outputToStoi = rxt.getOutputStoichiometries();
        if (!checkValuesAndNodes(outputs, outputNodes, outputToStoi))
            return false;
        // Check Catalysts
        List<GKInstance> cas = instance.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        List<GKInstance> catalysts = new ArrayList<GKInstance>();
        for (GKInstance ca : cas) {
            GKInstance catalyst = (GKInstance) ca.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (catalyst != null)
                catalysts.add(catalyst);
        }
        List<Node> catalystNodes = rxt.getHelperNodes();
        // Just an empty map in order to use the refactored method
        Map<Renderable, Integer> emptyMap = new HashMap<Renderable, Integer>();
        if (!checkValuesAndNodes(catalysts, catalystNodes, emptyMap))
            return false;
        // Check activators
        Collection<GKInstance> regulations = instance.getReferers(ReactomeJavaConstants.regulatedEntity);
        List<GKInstance> activators = new ArrayList<GKInstance>();
        List<GKInstance> inhibitors = new ArrayList<GKInstance>();
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
        if (!checkValuesAndNodes(activators, activatorNodes, emptyMap))
            return false;
        List<Node> inhibitorNodes = rxt.getInhibitorNodes();
        if (!checkValuesAndNodes(inhibitors, inhibitorNodes, emptyMap))
            return false;
        return true;
    }
    
    private boolean checkValuesAndNodes(List<GKInstance> values,
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
    
}
