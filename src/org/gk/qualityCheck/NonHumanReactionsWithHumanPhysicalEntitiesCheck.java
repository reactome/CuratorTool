package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class NonHumanReactionsWithHumanPhysicalEntitiesCheck extends NonHumanEventsNotManuallyInferredCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;

        GKInstance humanInst = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            Collection<GKInstance> reactionSpecies = reaction.getAttributeValuesList(ReactomeJavaConstants.species);
            if (!reactionSpecies.contains(humanInst)) {
                Set<GKInstance> humanPEs = findHumanPhysicalEntitiesInReaction(reaction, humanInst);
                for (GKInstance humanPE : humanPEs) {
                    String reportLine = getReportLine(humanPE, reaction);
                    report.addLine(reportLine);
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Set<GKInstance> findHumanPhysicalEntitiesInReaction(GKInstance humanReaction, GKInstance humanInst) throws Exception {
        Set<GKInstance> reactionPEs = findAllPhysicalEntitiesInReaction(humanReaction);
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity: reactionPEs) {
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
        String reportLine = String.join("\t", humanReaction.getDBID().toString(), humanReaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesNames, createdName);
        return reportLine;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_Reactions_Containing_Human_PhysicalEntities";
    }
}
