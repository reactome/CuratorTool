package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NonHumanPhysicalEntitiesWithoutDiseaseCheck extends NonHumanEventsNotInferredCheck{

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        List<GKInstance> nonHumanEventsNotUsedForInference = findNonHumanEventsNotUsedForInference(dba);
        for (GKInstance nonHumanEvent : nonHumanEventsNotUsedForInference) {
            if (nonHumanEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                Set<GKInstance> reactionPEs = findPhysicalEntitiesInReaction(nonHumanEvent);
                for (GKInstance reactionPE : reactionPEs) {
                    if (hasSpecies(reactionPE) && reactionPE.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                        String reportLine = getReportLine(reactionPE);
                        report.addLine(reportLine);
                    }
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private boolean hasSpecies(GKInstance reactionPE) throws Exception {
        if (reactionPE.getSchemClass().isa(ReactomeJavaConstants.Drug)) {
            return false;
        }
        if (reactionPE.getAttributeValue(ReactomeJavaConstants.species) == null) {
            return false;
        }
        return true;
    }

    private String getReportLine(GKInstance reactionPE) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst == null ? "null" : createdInst.getDisplayName();
        String reportLine = String.join("\t", reactionPE.getDBID().toString(), reactionPE.getDisplayName(), reactionPE.getSchemClass().getName(), speciesInst.getDisplayName(), createdName);
        return reportLine;
    }

    private Set<GKInstance> findPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> reactionPEs = new HashSet<>();
        reactionPEs.addAll(reaction.getAttributeValuesList(ReactomeJavaConstants.input));
        reactionPEs.addAll(reaction.getAttributeValuesList(ReactomeJavaConstants.output));
        reactionPEs.addAll(findCatalystPEs(reaction));
        reactionPEs.addAll(findRegulationPEs(reaction));
        return reactionPEs;
    }

    private Set<GKInstance> findRegulationPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> regulationPEs = new HashSet<>();
        List<GKInstance> regulations = reaction.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
        for (GKInstance regulation : regulations) {
            regulationPEs.addAll(regulation.getAttributeValuesList(ReactomeJavaConstants.activeUnit));
            regulationPEs.add((GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator));
        }
        return regulationPEs;
    }

    private Set<GKInstance> findCatalystPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> catalystPEs = new HashSet<>();
        List<GKInstance> catalysts = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        for (GKInstance catalyst : catalysts) {
            catalystPEs.addAll(catalyst.getAttributeValuesList(ReactomeJavaConstants.activeUnit));
            catalystPEs.add((GKInstance) catalyst.getAttributeValue(ReactomeJavaConstants.physicalEntity));
        }
        return catalystPEs;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID", "DisplayName", "Class", "Species", "Created"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_PhysicalEntities_Without_Disease";
    }

}
