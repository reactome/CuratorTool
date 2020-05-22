package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

/**
 * Flags all human Reactions that contain non-disease PhysicalEntities that are non-human, or are human PhysicalEntities with relatedSpecies.
 */
public class HumanReactionsWithNonHumanPhysicalEntitiesWithoutDiseaseCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        QACheckUtilities.setHumanSpeciesInst(dba);
        QACheckUtilities.setSkipList(QACheckUtilities.getNonHumanPathwaySkipList());

        // This QA Check is only performed on human ReactionlikeEvents that do not have any inferredFrom referrals.
        for (GKInstance reaction : QACheckUtilities.findHumanReactionsNotUsedForManualInference(dba)) {
            for (GKInstance reactionPE : QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
                // Valid PhysicalEntities include those that have a non-human species OR have a human species AND have a relatedSpecies,
                // and that do not have a populated disease attribute.
                if ((QACheckUtilities.hasNonHumanSpecies(reactionPE) || hasHumanSpeciesWithRelatedSpecies(reactionPE))
                        && !QACheckUtilities.hasDisease(reactionPE)) {

                    report.addLine(getReportLine(reactionPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }


     // Returns true if PhysicalEntities' species is Homo sapiens and if the relatedSpecies attribute is populated.
    private boolean hasHumanSpeciesWithRelatedSpecies(GKInstance reactionPE) throws Exception {
        return QACheckUtilities.isHumanDatabaseObject(reactionPE)
                && hasRelatedSpecies(reactionPE);
    }

    // Returns true if PhysicalEntities' relatedSpecies attribute is populated.
    private boolean hasRelatedSpecies(GKInstance reactionPE) throws Exception {
        return reactionPE.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies)
                && reactionPE.getAttributeValue(ReactomeJavaConstants.relatedSpecies) != null;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance reaction) throws Exception {
        String speciesName = QACheckUtilities.getInstanceAttributeName(physicalEntity, ReactomeJavaConstants.species);
        String createdName = QACheckUtilities.getInstanceAttributeName(physicalEntity, ReactomeJavaConstants.created);
        return String.join("\t",
                reaction.getDBID().toString(),
                reaction.getDisplayName(),
                physicalEntity.getDBID().toString(),
                physicalEntity.getDisplayName(),
                physicalEntity.getSchemClass().getName(),
                speciesName,
                createdName);
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
