package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

/**
 * Flags all human PhysicalEntities that are participants in a non-human ReactionlikeEvent.
 */
public class NonHumanReactionsWithHumanPhysicalEntitiesCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        QACheckUtilities.setHumanSpeciesInst(dba);
        QACheckUtilities.setSkipList(QACheckUtilities.getNonHumanPathwaySkipList());

        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            // Many Events have multiple species. Cases where there are multiple species and one of them is Human are also excluded.
            if (QACheckUtilities.hasNonHumanSpecies(reaction)) {
                for (GKInstance humanPE : findAllHumanPhysicalEntitiesInReaction(reaction)) {
                    report.addLine(getReportLine(humanPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    /**
     * Finds all distinct human PhysicalEntities that exist in the incoming ReactionlikeEvent.
     * @param reaction GKInstance -- ReactionlikeEvent with non-human species.
     * @return Set<GKInstance> -- Any human PhysicalEntities that exist in the ReactionlikeEvent.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    private Set<GKInstance> findAllHumanPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity: QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
            if (QACheckUtilities.isHumanDatabaseObject(physicalEntity)) {
                humanPEs.add(physicalEntity);
            }
        }
        return humanPEs;
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
        return "NonHuman_Reactions_Containing_Human_PhysicalEntities";
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
