package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

public class HumanReactionsWithNonHumanPhysicalEntitiesWithoutDiseaseCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);

        for (GKInstance reaction : findHumanReactionsNotUsedForManualInference(dba, humanSpeciesInst)) {
            for (GKInstance reactionPE : QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
                if ((QACheckUtilities.hasNonHumanSpecies(reactionPE, humanSpeciesInst) || hasHumanSpeciesWithRelatedSpecies(reactionPE, humanSpeciesInst)) && reactionPE.getAttributeValue(ReactomeJavaConstants.disease) == null) {
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
        for (GKInstance event : QACheckUtilities.findEventsNotUsedForManualInference(dba)) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) && QACheckUtilities.isHumanDatabaseObject(event, humanSpeciesInst)) {
                reactionsNotUsedForManualInference.add(event);
            }
        }
        return reactionsNotUsedForManualInference;
    }

    private String getReportLine(GKInstance reactionPE, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst == null ? "null" : createdInst.getDisplayName();
        return String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), reactionPE.getDBID().toString(), reactionPE.getDisplayName(), reactionPE.getSchemClass().getName(), speciesInst.getDisplayName(), createdName);
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species", "Created"};
    }

    @Override
    public String getDisplayName() {
        return "Human_Reactions_Containing_NonHuman_PhysicalEntities_Without_Disease";
    }

    @Override
    public void check() {
    }

    @Override
    public void check(GKInstance instance) {
    }

    @Override
    public void check(List<GKInstance> instances) {
    }

    @Override
    public void check(GKSchemaClass cls) {
    }

    @Override
    public void checkProject(GKInstance event) {
    }

    @Override
    protected InstanceListPane getDisplayedList() { return null; }
}
