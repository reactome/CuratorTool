package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

public class NonHumanReactionsWithHumanPhysicalEntitiesCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanInst = dba.fetchInstance(48887L);

        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            Collection<GKInstance> reactionSpecies = reaction.getAttributeValuesList(ReactomeJavaConstants.species);
            if (!reactionSpecies.contains(humanInst)) {
                for (GKInstance humanPE : findHumanPhysicalEntitiesInReaction(reaction, humanInst)) {
                    report.addLine(getReportLine(humanPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Set<GKInstance> findHumanPhysicalEntitiesInReaction(GKInstance humanReaction, GKInstance humanInst) throws Exception {
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity: QACheckUtilities.findAllPhysicalEntitiesInReaction(humanReaction)) {
            if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) == humanInst) {
                humanPEs.add(physicalEntity);
            }
        }
        return humanPEs;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance humanReaction) throws Exception {

        Collection<GKInstance> speciesInstances = physicalEntity.getAttributeValuesList(ReactomeJavaConstants.species);
        String speciesNames = "null";
        if (speciesInstances != null) {
            List<String> speciesNamesList = new ArrayList<>();
            for (GKInstance speciesInst : speciesInstances) {
                speciesNamesList.add(speciesInst.getDisplayName());
            }
            speciesNames = String.join(",", speciesNamesList);
            speciesNames = speciesNames.isEmpty() ? "null" : speciesNames;
        }
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : "null";
        return String.join("\t", humanReaction.getDBID().toString(), humanReaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesNames, createdName);
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_Reactions_Containing_Human_PhysicalEntities";
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
