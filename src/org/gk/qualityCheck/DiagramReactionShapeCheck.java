package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.ReactionType;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableReaction;
import org.gk.schema.SchemaClass;
import org.junit.Test;

/**
 * TODO: Hold this check for the time being until we figure out how we are going to
 * do with reaction types.
 * This QA is to make sure the drawn reaction type is the same as the annotated one 
 * based on the following rules:
 * <ol>
 * <li>BlackBoxEvent => <code>omitted or Uncertain</code>
 * <li>Polymerisation or Depolymerisation or has catalystActivity => <code>transition</code>
 * <li>More inputs than outputs and has a Complex output => <code>association</code>
 * <li>More outputs than inputs and has a Complex input => <code>dissociation</code>
 * <li>Otherwise => <code>transition</code>
 * </ol>
 * The output from this check should be the same as the diagram QA check T113 in
 * the diagram-converter project.
 * Note: The offended cases most likely are caused by the following two categories:
 * 1). Assign specific type to BlackBoxEvent as transition, association, or dissociation
 * 2). Use the generic transition to other types.
 * 
 * @author Fred Loney loneyf@ohsu.edu
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
    
    // The reaction issue reporting details.
    private Map<Long, IssueDetail> rxnDbIdToDetail = new HashMap<Long, IssueDetail>();
    
    @Override
    public String getDisplayName() {
        return "Diagram_Reactions_Type_Mismatch";
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
        GKInstance modified = QACheckUtilities.getLatestCuratorIEFromInstance(instance);
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
        Collection<RenderableReaction> rxnEdges = getDisplayedRLEes(pathway);
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
            ReactionType foundType = rxnEdge.getReactionType();
            if (foundType == null) { // This is the default
                foundType = ReactionType.TRANSITION;
            }
            // The reaction type inferred from the database reaction object.
            Set<ReactionType> correctTypes = inferReactionType(rxnInst);
            if (!correctTypes.contains(foundType)) {
                mismatchedRxnDbIds.add(rxnDbId);
                IssueDetail details = new IssueDetail();
                details.reaction = rxnInst;
                details.foundType = foundType.toString();
                details.correctType = correctTypes.stream().map(ReactionType::toString).collect(Collectors.joining(", "));
                rxnDbIdToDetail.put(rxnDbId, details);
            }
        }
        return mismatchedRxnDbIds;
    }
    
    @SuppressWarnings("unchecked")
    private Set<ReactionType> inferReactionType(GKInstance instance) throws Exception {
        SchemaClass schemaCls = instance.getSchemClass();
        if (!schemaCls.isa(ReactomeJavaConstants.ReactionlikeEvent)) {
            String msg = "The instance must be a ReactionlikeEvent; found: " +
                    schemaCls.getName() + " for " + instance.getDisplayName() +
                    " (DB_ID " + instance.getDBID() + ")";
            throw new IllegalArgumentException(msg);
        }
        // BBE => omitted.
        if (schemaCls.isa(ReactomeJavaConstants.BlackBoxEvent)) {
            return Stream.of(ReactionType.OMITTED_PROCESS,
                             ReactionType.UNCERTAIN_PROCESS).collect(Collectors.toSet());
        }
        Set<ReactionType> types = new HashSet<>();
        // This is for test
//        types.add(ReactionType.TRANSITION);
        
        // (de)polymerisation or has catalyst => association.
        if (schemaCls.isa(ReactomeJavaConstants.Polymerisation) ||
                schemaCls.isa(ReactomeJavaConstants.Depolymerisation) ||
                instance.getAttributeValue(ReactomeJavaConstants.catalystActivity) != null) {
            types.add(ReactionType.TRANSITION);
            return types;
        }
        else {
            List<GKInstance> inputs =
                    instance.getAttributeValuesList(ReactomeJavaConstants.input);
            List<GKInstance> outputs = 
                    instance.getAttributeValuesList(ReactomeJavaConstants.output);
            int netProductCnt = outputs.size() - inputs.size();
            // More inputs than outputs and has a Complex output => association.
            // More outputs than inputs and has a Complex input => dissociation.
            if (netProductCnt < 0 && hasComplex(outputs)) {
                types.add(ReactionType.ASSOCIATION);
                return types;
            } else if (netProductCnt > 0 && hasComplex(inputs)) {
                types.add(ReactionType.DISSOCIATION);
                return types;
            }
        }
        // As the default
        types.add(ReactionType.TRANSITION);
        return types;
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
    protected String getResultTableIssueDBIDColName() {
        return "Mismatched Reaction DBIDs";
    }
    
    @Test
    public void testCheckInCommand() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        super.testCheckInCommand(dba);
    }

}
