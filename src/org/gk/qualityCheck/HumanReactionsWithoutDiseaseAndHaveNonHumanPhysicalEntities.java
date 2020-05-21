package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

/**
 * Flags Human ReactionlikeEvents that do not have a populated 'disease' attribute, but have NonHuman participants.
 */

public class HumanReactionsWithoutDiseaseAndHaveNonHumanPhysicalEntities extends AbstractQualityCheck {


    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        QACheckUtilities.setSkipList(Arrays.asList("168249")); // Innate Immunity Pathway
        QACheckUtilities.setHumanSpeciesInst(dba);

        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            // isHumanDatabaseObject checks that the species attribute only contains a Homo sapiens species instance. Multi-species RlEs are excluded.
            if (!QACheckUtilities.memberSkipListPathway(reaction)
                    && QACheckUtilities.isHumanDatabaseObject(reaction)
                    && reaction.getAttributeValue(ReactomeJavaConstants.disease) == null) {

                for (GKInstance nonHumanPE : findAllNonHumanPhysicalEntitiesInReaction(reaction)) {
                    report.addLine(getReportLine(nonHumanPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    /**
     * Finds all distinct non-human PhysicalEntities that exist in the incoming human ReactionlikeEvent.
     * @param reaction GKInstance -- ReactionlikeEvent with Homo sapiens species.
     * @param humanSpeciesInst GKInstance -- Homo sapiens species instance
     * @return Set<GKInstance> -- Any non-human PhysicalEntities that exist in the human ReactionlikeEvent.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    private Set<GKInstance> findAllNonHumanPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> nonHumanPEs = new HashSet<>();
        for (GKInstance physicalEntity : QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
            if (QACheckUtilities.hasNonHumanSpecies(physicalEntity)) {
                nonHumanPEs.add(physicalEntity);
            }
        }
        return nonHumanPEs;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.species);
        String speciesName = speciesInst != null ? speciesInst.getDisplayName() : "null";
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : "null";
        return String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesName, createdName);
    }

    @Override
    public String getDisplayName() {
        return "Human_Reactions_Without_Disease_And_Have_NonHuman_PhysicalEntities";
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
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
