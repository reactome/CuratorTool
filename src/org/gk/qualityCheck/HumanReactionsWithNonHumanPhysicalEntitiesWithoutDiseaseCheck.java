package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class HumanReactionsWithNonHumanPhysicalEntitiesWithoutDiseaseCheck extends NonHumanEventsNotManuallyInferredCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);
        for (GKInstance reaction : findHumanReactionsNotUsedForManualInference(dba, humanSpeciesInst)) {
            for (GKInstance reactionPE : findAllPhysicalEntitiesInReaction(reaction)) {
                if ((hasNonHumanSpecies(reactionPE, humanSpeciesInst) || hasHumanSpeciesWithRelatedSpecies(reactionPE, humanSpeciesInst)) && reactionPE.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                    report.addLine(getReportLine(reactionPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private boolean hasHumanSpeciesWithRelatedSpecies(GKInstance reactionPE, GKInstance humanSpeciesInst) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        return speciesInst != null && speciesInst.equals(humanSpeciesInst) && reactionPE.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies) && reactionPE.getAttributeValue(ReactomeJavaConstants.relatedSpecies) != null;
    }

    private List<GKInstance> findHumanReactionsNotUsedForManualInference(MySQLAdaptor dba, GKInstance humanSpeciesInst) throws Exception {
        List<GKInstance> reactionsNotUsedForManualInference = new ArrayList<>();
        for (GKInstance event : findEventsNotUsedForManualInference(dba)) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) && isHumanDatabaseObject(event, humanSpeciesInst)) {
                reactionsNotUsedForManualInference.add(event);
            }
        }
        return reactionsNotUsedForManualInference;
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
        return "Human_Reactions_Containing_NonHuman_PhysicalEntities_Without_Disease";
    }
}
