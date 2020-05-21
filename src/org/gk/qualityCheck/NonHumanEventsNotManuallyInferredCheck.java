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
 *  Finds all NonHuman Events that are not used for manual inference (ie. 'inferredFrom' referral is null)
 */
public class NonHumanEventsNotManuallyInferredCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);
        QACheckUtilities.setSkipList(Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt")));

        // The actual method for finding Events that aren't manually inferred is used by multiple QA tests.
        for (GKInstance event : QACheckUtilities.findEventsNotUsedForManualInference(dba)) {
            // Many Events have multiple species. Cases where there are multiple species and one of them is Human are also excluded.
            List<GKInstance> eventSpecies = event.getAttributeValuesList(ReactomeJavaConstants.species);
            if (!eventSpecies.contains(humanSpeciesInst)) {
                report.addLine(getReportLine(event));
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private String getReportLine(GKInstance instance) throws Exception {
        GKInstance speciesInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
        return String.join("\t", instance.getDBID().toString(), instance.getDisplayName(), speciesInst.getDisplayName(), createdInst.getDisplayName());
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_Events_Not_Manually_Inferred";
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID", "DisplayName", "Species", "Created"};
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
    protected InstanceListPane getDisplayedList() {
        return null;
    }
}
