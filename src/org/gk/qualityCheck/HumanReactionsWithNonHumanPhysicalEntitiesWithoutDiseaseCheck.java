package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Flags all Human Reactions that contain Non-only-Human PhysicalEntities that do not have a populated 'disease' attribute.
 * Valid PhysicalEntities are those that have a NonHuman species or that have a Human species AND have a filled 'relatedSpecies'.
 */
public class HumanReactionsWithNonHumanPhysicalEntitiesWithoutDiseaseCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);
        QACheckUtilities.setSkipList(Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt")));

        // This QA Check is only performed on Human ReactionlikeEvents that do not have any 'inferredFrom' referrals.
        for (GKInstance reaction : QACheckUtilities.findHumanReactionsNotUsedForManualInference(dba, humanSpeciesInst)) {
            for (GKInstance reactionPE : QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
                // Valid PhysicalEntities include those that have a nonHuman species OR have a human species AND have a relatedSpecies, and that do not have a populated disease attribute.
                if ((QACheckUtilities.hasNonHumanSpecies(reactionPE, humanSpeciesInst) || hasHumanSpeciesWithRelatedSpecies(reactionPE, humanSpeciesInst)) && reactionPE.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                    report.addLine(getReportLine(reactionPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    /**
     * Returns true if PhysicalEntities species is Homo sapiens and if the relatedSpecies attribute is populated.
     * @param reactionPE GKInstance -- PhysicalEntity involved in a ReactionlikeEvent
     * @param humanSpeciesInst GKInstance -- Homo sapiens species instance.
     * @return
     * @throws Exception -- Thrown by MySQLAdaptor
     */
    private boolean hasHumanSpeciesWithRelatedSpecies(GKInstance reactionPE, GKInstance humanSpeciesInst) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        return speciesInst != null && speciesInst.equals(humanSpeciesInst) && reactionPE.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies) && reactionPE.getAttributeValue(ReactomeJavaConstants.relatedSpecies) != null;
    }

    private String getReportLine(GKInstance reactionPE, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst == null ? "null" : createdInst.getDisplayName();
        return String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), reactionPE.getDBID().toString(), reactionPE.getDisplayName(), reactionPE.getSchemClass().getName(), speciesInst.getDisplayName(), createdName);
    }

    @Override
    public String getDisplayName() {
        return "Infectious_Disease_RLEs_Containing_PhysicalEntities_Without_Disease";
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species", "Created"};
    }

    // Unused, but required, AbstractQualityCheck methods.
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
