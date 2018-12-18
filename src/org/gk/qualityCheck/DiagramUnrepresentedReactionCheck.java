package org.gk.qualityCheck;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

public class DiagramUnrepresentedReactionCheck extends AbstractQualityCheck {

    private static final String HUMAN = "Homo sapiens";
    
    private static final String[] HEADERS = {
            "DB_ID", "Display_Name", "MostRecentAuthor"
    };

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = super.checkInCommand();
        if (report == null)
            return null;
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        // The {reaction: diagrams} map.
        ReactionELVCheck reactionCheck = new ReactionELVCheck();
        Map<GKInstance, Set<GKInstance>> usageMap = reactionCheck.checkEventUsageInELV(dba);
        for (Entry<GKInstance, Set<GKInstance>> usage: usageMap.entrySet()) {
            Set<GKInstance> diagrams = usage.getValue();
            if (diagrams == null) {
                GKInstance reaction = usage.getKey();
                GKInstance species =
                        (GKInstance) reaction.getAttributeValue(ReactomeJavaConstants.species);
                if (HUMAN.equals(species.getDisplayName())) {
                    GKInstance ie = InstanceUtilities.getLatestCuratorIEFromInstance(reaction);
                    String ieName = ie == null ? "None" : ie.getDisplayName();
                    report.addLine(reaction.getDBID().toString(), reaction.getDisplayName(), ieName);
                }
            }
        }
        
        report.setColumnHeaders(HEADERS);
        
        return report;
    }

    @Override
    public void check(GKSchemaClass cls) {
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
    public void checkProject(GKInstance event) {
    }

    @Override
    protected InstanceListPane getDisplayedList() {
        return null;
    }
    
}
