package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.ReactionType;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableReaction;
import org.gk.schema.SchemaClass;

/**
 * This is the Curator QA adaptation of the diagram-converter T113 check
 * for diagram reaction shape type consistency with the represented
 * database Reaction reactionType value.
 *
 * The diagram reaction type is determined as follows:
 * <ol>
 * <li>BlackBoxEvent => <code>omitted</code>
 * <li>Polymerisation or Depolymerisation or has catalystActivity => <code>transition</code>
 * <li>More inputs than outputs and has a Complex output => <code>association</code>
 * <li>More outputs than inputs and has a Complex input => <code>dissociation</code>
 * <li>Otherwise => <code>transition</code>
 * </ol>
 * 
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class DiagramReactionShapeCheck extends DiagramReactionsCheck {
    
    private static String[] HEADERS = {
            "PathwayDiagram_DBID", "Pathway_DisplayName", "Pathway_DBID",
            "Reaction_DBID", "Reaction_DisplayName",
            "Correct_Reaction_Type", "Found_Reaction_Type",
            "Created", "Modified"
    };
    
    private static class IssueDetail {
        GKInstance reaction;
        String correctType;
        String foundType;
    }
    
    /**
     * The reaction issue reporting details.
     *
     * @author Fred Loney <loneyf@ohsu.edu>
     */
    private Map<Long, IssueDetail> rxnDbIdToDetail = new HashMap<Long, IssueDetail>();
    
    @Override
    public String getDisplayName() {
        return "Diagram_Reactions_Shape_Mismatch";
    }

    @Override
    protected String[] getColumnHeaders() {
        return HEADERS;
    }

    @Override
    protected String[][] getReportLines(GKInstance instance) throws Exception {
        GKInstance pathway =
                (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.representedPathway);
        Collection<Long> rxnDbIds = getIssueDbIds(instance);
        List<String[]> lines = new ArrayList<String[]>();
        GKInstance created = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
        GKInstance modified = InstanceUtilities.getLatestCuratorIEFromInstance(instance);
        for (Long rxnDbId: rxnDbIds) {
            IssueDetail detail = rxnDbIdToDetail.get(rxnDbId);
            String[] line = {
                    instance.getDBID().toString(),
                    pathway.getDisplayName(),
                    pathway.getDBID().toString(),
                    rxnDbId.toString(),
                    detail.reaction.getDisplayName(),
                    detail.correctType,
                    detail.foundType,
                    created.getDisplayName(),
                    modified.getDisplayName()
            };
            lines.add(line);
        }
        
        return lines.toArray(new String[lines.size()][]);
    }

    @Override
    protected Collection<Long> doCheck(GKInstance instance) throws Exception {
        RenderablePathway pathway = getRenderablePathway(instance);
        // The diagram reactions.
        Collection<RenderableReaction> rxnEdges = extractReactionRenderables(pathway);
        // Check each diagram reaction.
        Set<Long> mismatchedRxnDbIds = new HashSet<Long>();
        for (RenderableReaction rxnEdge: rxnEdges) {
            Long rxnDbId = rxnEdge.getReactomeId();
            if (rxnDbId == null) {
                continue;
            }
            GKInstance rxnInst = dataSource.fetchInstance(rxnDbId);
            if (rxnInst == null) {
                continue;
            }
            // The diagram reaction type (default transition).
            ReactionType diagRxnType = rxnEdge.getReactionType();
            if (diagRxnType == null) {
                diagRxnType = ReactionType.TRANSITION;
            }
            // The reaction type inferred from the database reaction object.
            ReactionType dbRxnType = inferReactionType(rxnInst);
            if (diagRxnType != dbRxnType) {
                mismatchedRxnDbIds.add(rxnDbId);
                IssueDetail details = new IssueDetail();
                details.reaction = rxnInst;
                details.foundType = diagRxnType.toString();
                details.correctType = dbRxnType.toString();
                rxnDbIdToDetail.put(rxnDbId, details);
            }
        }
        
        return mismatchedRxnDbIds;
    }

    @SuppressWarnings("unchecked")
    private ReactionType inferReactionType(GKInstance instance) throws Exception {
        SchemaClass schemaCls = instance.getSchemClass();
        if (!schemaCls.isa(ReactomeJavaConstants.ReactionlikeEvent)) {
            String msg = "The instance must be a ReactionlikeEvent; found: " +
                    schemaCls.getName() + " for " + instance.getDisplayName() +
                    " (DB_ID " + instance.getDBID() + ")";
            throw new IllegalArgumentException(msg);
        }
        // BBE => omitted.
        if (schemaCls.isa(ReactomeJavaConstants.BlackBoxEvent)) {
            return ReactionType.OMITTED_PROCESS;
        }
        // (de)polymerisation or has catalyst => association.
        if (schemaCls.isa(ReactomeJavaConstants.Polymerisation) ||
                schemaCls.isa(ReactomeJavaConstants.Depolymerisation) ||
                instance.getAttributeValue(ReactomeJavaConstants.catalystActivity) != null) {
            return ReactionType.TRANSITION;
        }
        List<GKInstance> inputs =
                instance.getAttributeValuesList(ReactomeJavaConstants.input);
        List<GKInstance> outputs = 
                instance.getAttributeValuesList(ReactomeJavaConstants.output);
        int netProductCnt = outputs.size() - inputs.size();
        // More inputs than outputs and has a Complex output => association.
        // More outputs than inputs and has a Complex input => dissociation.
        if (netProductCnt < 0 && hasComplex(outputs)) {
            return ReactionType.ASSOCIATION;
        } else if (netProductCnt > 0 && hasComplex(inputs)) {
            return ReactionType.DISSOCIATION;
        }
        
        // Default is transition.
        return ReactionType.TRANSITION;
    }
    
    /**
     * Returns whether any of the given instances or its constituents
     * is a Complex. An EntitySet's members and candidates are checked
     * recursively.
     * 
     * @param instances the instances to inspect
     * @return whether an instance is or contains a Complex
     * @throws Exception
     */
    private boolean hasComplex(Collection<GKInstance> instances) throws Exception {
        for (GKInstance instance: instances) {
            if (hasComplex(instance)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns whether the given instance or any of its constituents
     * is a Complex. An EntitySet's members and candidates are checked
     * recursively.
     * 
     * @param instance
     * @return whether the instance is or contains a Complex
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private boolean hasComplex(GKInstance instance) throws Exception {
        SchemaClass schemaCls = instance.getSchemClass();
        if (schemaCls.isa(ReactomeJavaConstants.EntitySet)) {
            List<GKInstance> constituents =
                    instance.getAttributeValuesList(ReactomeJavaConstants.hasMember);
            if (schemaCls.isa(ReactomeJavaConstants.CandidateSet)) {
                List<GKInstance> candidates =
                        instance.getAttributeValuesList(ReactomeJavaConstants.hasCandidate);
                constituents.addAll(candidates);
            }
            return hasComplex(constituents);
        } else {
            return schemaCls.isa(ReactomeJavaConstants.Complex);
        }
    }
    
    @Override
    protected String getResultTableModelTitle() {
        return "Mismatched Reaction DBIDs";
    }

}
