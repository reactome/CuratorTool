package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class NonHumanPhysicalEntitiesWithoutDiseaseCheck extends NonHumanEventsNotManuallyInferredCheck {

    private static List<String> skiplistDbIDs = new ArrayList<>();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        List<GKInstance> reactionsNotUsedForManualInference = findReactionsNotUsedForManualInference(dba);
        for (GKInstance reaction : reactionsNotUsedForManualInference) {
            if (reaction.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                Set<GKInstance> reactionPEs = findAllPhysicalEntitiesInReaction(reaction);
                for (GKInstance reactionPE : reactionPEs) {
                    if (hasNonHumanSpecies(reactionPE) && reactionPE.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                        String reportLine = getReportLine(reactionPE, reaction);
                        report.addLine(reportLine);
                    }
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private List<GKInstance> findReactionsNotUsedForManualInference(MySQLAdaptor dba) throws Exception {
        setSkipList();
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        List<GKInstance> reactionsNotUsedForManualInference = new ArrayList<>();
        for (GKInstance reaction : reactions) {
            if (!usedForManualInference(reaction) && !isMemberSkipListPathway(reaction)) {
                reactionsNotUsedForManualInference.add(reaction);
            }
        }
        return reactionsNotUsedForManualInference;
    }

    private boolean usedForManualInference(GKInstance reaction) throws Exception {
        return reaction.getReferers(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    private boolean isMemberSkipListPathway(GKInstance nonHumanEvent) throws Exception {

        if (inSkipList(nonHumanEvent)) {
            return true;
        }

        Collection<GKInstance> hasEventReferrals = nonHumanEvent.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            for (GKInstance hasEventReferral : hasEventReferrals) {
                return isMemberSkipListPathway(hasEventReferral);
            }
        }

        return false;
    }

    private boolean inSkipList(GKInstance nonHumanEvent) {
        return skiplistDbIDs.contains(nonHumanEvent.getDBID().toString());
    }

    private void setSkipList() throws IOException {
        skiplistDbIDs = Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt"));
    }

    private String getReportLine(GKInstance reactionPE, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst == null ? "null" : createdInst.getDisplayName();
        String reportLine = String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), reactionPE.getDBID().toString(), reactionPE.getDisplayName(), reactionPE.getSchemClass().getName(), speciesInst.getDisplayName(), createdName);
        return reportLine;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species", "Created"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_PhysicalEntities_Without_Disease";
    }

}
