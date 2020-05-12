package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class NonHumanEventsNotInferredCheck extends AbstractQualityCheck{

    private static List<String> skiplistDbIDs = new ArrayList<>();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = super.checkInCommand();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        skiplistDbIDs = Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt"));
        List<GKInstance> nonHumanEventsNotUsedForInference = findNonHumanEventsNotUsedForInference(dba);
        for (GKInstance instance : nonHumanEventsNotUsedForInference) {
            String reportLine = getReportLine(instance);
            report.addLine(reportLine);
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private String getReportLine(GKInstance instance) throws Exception {

        GKInstance speciesInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
        String reportLine = String.join("\t", instance.getDBID().toString(), instance.getDisplayName(), speciesInst.getDisplayName(), createdInst.getDisplayName());
        return reportLine;
    }

    private List<GKInstance> findNonHumanEventsNotUsedForInference(MySQLAdaptor dba) throws Exception {
        GKInstance humanInst = dba.fetchInstance(48887L);
        Collection<GKInstance> nonHumanEvents = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Event, ReactomeJavaConstants.species, "!=", humanInst);
        List<GKInstance> nonHumanEventsNotUsedForInference = new ArrayList<>();
        for (GKInstance nonHumanEvent : nonHumanEvents) {
            if (!inferredToHuman(nonHumanEvent) && !isMemberSkipListPathway(nonHumanEvent)) {
                nonHumanEventsNotUsedForInference.add(nonHumanEvent);
            }
        }
        return nonHumanEventsNotUsedForInference;
    }

    private boolean inferredToHuman(GKInstance nonHumanEvent) throws Exception {
       return nonHumanEvent.getReferers(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    private boolean isMemberSkipListPathway(GKInstance nonHumanEvent) throws Exception {

        if (inSkipList(nonHumanEvent)) {
            return true;
        }

        Collection<GKInstance> hasEventReferrals = nonHumanEvent.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            for (GKInstance hasEventReferral : hasEventReferrals) {
                return isMemberSkipListPathway(hasEventReferral);
            }
        }

        return false;
    }

    private boolean inSkipList(GKInstance nonHumanEvent) {
        return skiplistDbIDs.contains(nonHumanEvent.getDBID().toString());
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_Events_Not_Inferred_To_Human";
    }

    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID", "DisplayName", "Species", "Created"};
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
    protected InstanceListPane getDisplayedList() {
        return null;
    }
}
