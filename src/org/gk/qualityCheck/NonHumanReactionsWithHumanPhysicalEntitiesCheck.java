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
 * Flags all Human PhysicalEntities that are participants in a NonHuman ReactionlikeEvent.
 */
public class NonHumanReactionsWithHumanPhysicalEntitiesCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);
        QACheckUtilities.setSkipList(Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt")));

        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            // Many Events have multiple species. Cases where there are multiple species and one of them is Human are also excluded.
            Collection<GKInstance> reactionSpecies = reaction.getAttributeValuesList(ReactomeJavaConstants.species);
            if (!reactionSpecies.contains(humanSpeciesInst)) {
                for (GKInstance humanPE : findHumanPhysicalEntitiesInReaction(reaction, humanSpeciesInst)) {
                    report.addLine(getReportLine(humanPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    /**
     * Finds all distinct Human PhysicalEntities that exist in the incoming ReactionlikeEvent.
     * @param reaction GKInstance -- ReactionlikeEvent with nonHuman species.
     * @param humanSpeciesInst GKInstance -- Homo sapiens species instance.
     * @return Set<GKInstance> -- Any Human PhysicalEntities that exist in the ReactionlikeEvent.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    private Set<GKInstance> findHumanPhysicalEntitiesInReaction(GKInstance reaction, GKInstance humanSpeciesInst) throws Exception {
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity: QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
            if (QACheckUtilities.isHumanDatabaseObject(physicalEntity, humanSpeciesInst)) {
                humanPEs.add(physicalEntity);
            }
        }
        return humanPEs;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance reaction) throws Exception {

        Collection<GKInstance> speciesInstances = physicalEntity.getAttributeValuesList(ReactomeJavaConstants.species);
        String speciesNames = null;
        if (speciesInstances != null) {
            List<String> speciesNamesList = new ArrayList<>();
            for (GKInstance speciesInst : speciesInstances) {
                speciesNamesList.add(speciesInst.getDisplayName());
            }
            speciesNames = String.join(",", speciesNamesList);
            speciesNames = speciesNames.isEmpty() ? null : speciesNames;
        }
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : null;
        return String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesNames, createdName);
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
