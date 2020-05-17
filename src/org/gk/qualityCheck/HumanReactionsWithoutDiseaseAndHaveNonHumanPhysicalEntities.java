package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class HumanReactionsWithoutDiseaseAndHaveNonHumanPhysicalEntities extends NonHumanEventsNotManuallyInferredCheck {

    List<String> skipListDbIds = new ArrayList<>();
    GKInstance humanSpeciesInst = new GKInstance();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        skipListDbIds = Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt"));
        skipListDbIds.add("168249"); // Innate Immune System
        humanSpeciesInst = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            GKInstance stableIdentifierInst = (GKInstance) reaction.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (!isMemberSkippedEvent(reaction) && stableIdentifierInst.getDisplayName().contains("R-HSA-") && reaction.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                Set<GKInstance> nonHumanPEsInReaction = findAllNonHumanEntitiesInReaction(reaction);
                for (GKInstance nonHumanPE : nonHumanPEsInReaction) {
                    report.addLine(getReportLine(nonHumanPE, reaction));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Set<GKInstance> findAllNonHumanEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> nonHumanPEsInReaction = new HashSet<>();
        for (GKInstance physicalEntity : findAllPhysicalEntitiesInReaction(reaction)) {
            GKInstance stableIdentifierInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) != humanSpeciesInst && !stableIdentifierInst.getDisplayName().contains("R-ALL")) {
                nonHumanPEsInReaction.add(physicalEntity);
            }
        }
        return nonHumanPEsInReaction;
    }

    private boolean isMemberSkippedEvent(GKInstance event) throws Exception {
        if (skipListDbIds.contains(event.getDBID().toString())) {
            return true;
        }
        Collection<GKInstance> hasEventReferrals = event.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            for (GKInstance hasEventReferral : hasEventReferrals) {
                return isMemberSkippedEvent(hasEventReferral);
            }
        }
        return false;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.species);
        String speciesName = speciesInst != null ? speciesInst.getDisplayName() : "null";
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : "null";
        String reportLine = String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesName, createdName);
        return reportLine;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "Human_Reactions_Without_Disease_And_Have_NonHuman_PhysicalEntities";
    }
}
