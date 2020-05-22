package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

/**
 *  Finds all non-human Events that are not used for manual inference (ie. inferredFrom referral is null)
 */
public class NonHumanEventsNotManuallyInferredCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        QACheckUtilities.setHumanSpeciesInst(dba);
        QACheckUtilities.setSkipList(QACheckUtilities.getNonHumanPathwaySkipList());

        // The actual method for finding Events that aren't manually inferred is used by multiple QA tests.
        for (GKInstance event : QACheckUtilities.findEventsNotUsedForManualInference(dba)) {
            // Many Events have multiple species. Cases where there are multiple species and one of them is human are also excluded.
            if (QACheckUtilities.hasNonHumanSpecies(event)) {
                report.addLine(getReportLine(event));
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private String getReportLine(GKInstance event) throws Exception {
        String speciesName = QACheckUtilities.getInstanceAttributeName(event, ReactomeJavaConstants.species);
        String createdName = QACheckUtilities.getInstanceAttributeName(event, ReactomeJavaConstants.created);
        return String.join("\t",
                event.getDBID().toString(),
                event.getDisplayName(),
                speciesName,
                createdName);
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
