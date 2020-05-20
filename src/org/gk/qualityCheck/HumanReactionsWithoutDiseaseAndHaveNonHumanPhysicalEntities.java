package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

public class HumanReactionsWithoutDiseaseAndHaveNonHumanPhysicalEntities extends AbstractQualityCheck {


    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        QACheckUtilities.addToSkipList("168249"); // Innate Immunity Pathway
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);

        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            if (!QACheckUtilities.isMemberSkipListPathway(reaction) && QACheckUtilities.isHumanDatabaseObject(reaction, humanSpeciesInst) && reaction.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                for (GKInstance nonHumanPE : findAllNonHumanEntitiesInReaction(reaction, humanSpeciesInst)) {
                    report.addLine(getReportLine(nonHumanPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Set<GKInstance> findAllNonHumanEntitiesInReaction(GKInstance reaction, GKInstance humanSpeciesInst) throws Exception {
        Set<GKInstance> nonHumanPEsInReaction = new HashSet<>();
        for (GKInstance physicalEntity : QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
            if (QACheckUtilities.hasNonHumanSpecies(physicalEntity, humanSpeciesInst)) {
                nonHumanPEsInReaction.add(physicalEntity);
            }
        }
        return nonHumanPEsInReaction;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.species);
        String speciesName = speciesInst != null ? speciesInst.getDisplayName() : "null";
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : "null";
        return String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesName, createdName);
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "Human_Reactions_Without_Disease_And_Have_NonHuman_PhysicalEntities";
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
