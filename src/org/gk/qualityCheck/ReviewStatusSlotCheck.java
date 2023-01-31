package org.gk.qualityCheck;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

/**
 * This class is used to check if the assignment to the reviewStatus is correct by following the following:
 * 1). Three stars: internalReviewed is assigned. Its datetime is later than the latest datetime of the InstanceEdits 
 * in the structureUpdate slot if any.
 * 2). Four stars: both reviewed and internalReviewed are assigned. The latest datatime of the internalReviewed is later 
   than the latest of the reviewed and the latest of the structureUpdate.
   3). Five stars: reviewed must be assigned (authored will not be used). Its datetime is later than the latest datetime 
   of the InstanceEdits in the structureUpdate slot if any.
   For more details, see https://docs.google.com/presentation/d/1Y3fxXS3DzE0aRZmPnE1K6PC51BHd5dak/edit#slide=id.p8.
   Note: Since one and two stars are not released, they are not checked.
 * @author wug
 *
 */
@SuppressWarnings({"unchecked", "serial"})
public class ReviewStatusSlotCheck extends SingleAttributeClassBasedCheck {
    // Keep the found issues for display
    private Map<GKInstance, String> inst2issue = new HashMap<>();

    public ReviewStatusSlotCheck() {
        this.checkAttribute = "ReviewStatus in Event";
        this.checkClsName = ReactomeJavaConstants.Event;
        this.followAttributes = new String[] {ReactomeJavaConstants.reviewStatus,
                ReactomeJavaConstants.structureModified,
                ReactomeJavaConstants.reviewed,
                ReactomeJavaConstants.internalReviewed};
    }

    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        String issue = inst2issue.get(instance);
        if (issue == null)
            issue = "";
        return issue;
    }

    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        // This is an old database
        if (!instance.getSchemClass().isValidAttribute(ReactomeJavaConstants .reviewStatus) ||
            !instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.structureModified))
            return true;
        // Get all instances that should be checked.
        Set<GKInstance> contained = getAllContainedEntities(instance);
        // Skip checking for shell instances
        if (containShellInstances(contained))
            return true;
        // Nothing to check if contained is empty
        if (contained.size() == 0)
            return true;
        // Check if there is a need to check
        GKInstance reviewStatus = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.reviewStatus);
        if (reviewStatus == null || 
                reviewStatus.getDisplayName().equals(ReactomeJavaConstants.OneStar) ||
                reviewStatus.getDisplayName().equals(ReactomeJavaConstants.TwoStars))
            return true; // Null, one or two stars are not checked for assignment.
        List<GKInstance> structureModified = instance.getAttributeValuesList(ReactomeJavaConstants.structureModified);
        Date lastStructureUpdateTime = getLastDateTime(structureModified);
        List<GKInstance> internalReviewed = instance.getAttributeValuesList(ReactomeJavaConstants.internalReviewed);
        List<GKInstance> reviewed = instance.getAttributeValuesList(ReactomeJavaConstants.reviewed);
        if (reviewStatus.getDisplayName().equals(ReactomeJavaConstants.ThreeStars)) {
            return checkThreeStars(
                    instance, 
                    lastStructureUpdateTime, 
                    internalReviewed, 
                    reviewed
                    );
        }
        if (reviewStatus.getDisplayName().equals(ReactomeJavaConstants.FourStars)) {
            return checkFourStars(instance, 
                    lastStructureUpdateTime, 
                    internalReviewed, 
                    reviewed);
        }
        if (reviewStatus.getDisplayName().equals(ReactomeJavaConstants.FiveStars)) {
            return checkFiveStars(instance, 
                                  lastStructureUpdateTime, 
                                  reviewed);
        }
        return true;
    }
    
    /**
     * Five stars: reviewed must be assigned (authored will not be used). 
     * Its datetime is later than the latest datetime of the InstanceEdits 
     * in the structureModified slot if any, which may be null.
     * @param instance
     * @param lastStructureUpdateTime
     * @param internalReviewed
     * @param reviewed
     * @return
     * @throws Exception
     */
    private boolean checkFiveStars(GKInstance instance,
                                   Date lastStructureUpdateTime,
                                   List<GKInstance> reviewed) throws Exception {
        // Assume previous five stars are correct always
        if (lastStructureUpdateTime == null)
            return true;
        Date lastReviewedTime = getLastDateTime(reviewed);
        StringBuilder issues = new StringBuilder();
        if (lastReviewedTime == null) {
            issues.append("No reviewed"); // This is a must
        }
        else if (lastStructureUpdateTime.after(lastReviewedTime)) {
            issues.append("reviewed later than structureModified");
        }
        if (issues.length() > 0) {
            inst2issue.put(instance, issues.toString());
            return false;
        }
        // Regardless, treat as false. Don't return to avoid aborting the application.
        inst2issue.put(instance, "unknown");
        return false;
    }
    
    /**
     * Four stars: Both internalReviewed and reviewed are assigned. The latest datetime has the following order: 
     * internalReviewed (latest) < structureModified < reviewed (earliest).
     * @param instance
     * @param lastStructureUpdateTime
     * @param internalReviewed
     * @param reviewed
     * @return
     * @throws Exception
     */
    private boolean checkFourStars(GKInstance instance,
                                   Date lastStructureUpdateTime,
                                   List<GKInstance> internalReviewed,
                                   List<GKInstance> reviewed) throws Exception {
        Date lastInternalReviewedTime = getLastDateTime(internalReviewed);
        Date lastReviewedTime = getLastDateTime(reviewed);
        if (lastInternalReviewedTime != null &&
            lastReviewedTime != null &&
            lastStructureUpdateTime != null && 
            lastInternalReviewedTime.after(lastStructureUpdateTime) &&
            lastStructureUpdateTime.after(lastReviewedTime)) {
            // Correct case: internalReviewed (latest) < structureModified < reviewed (earliest).
            return true;
        }
        StringBuilder issues = new StringBuilder();
        // If there is no structureModified, there is no chance 4 stars there
        if (lastStructureUpdateTime == null) {
            issues.append("No structureModified");
        }
        else {
            if (lastInternalReviewedTime == null) {
                issues.append("No internalReviewed");
            }
            else if (lastInternalReviewedTime.before(lastStructureUpdateTime)) {
                issues.append("internalReviewed earlier than structureModified");
            }
            if (lastReviewedTime == null) {
                if (issues.length() > 0)
                    issues.append("; ");
                issues.append("No reviewed");
            }
            else if (lastReviewedTime.after(lastStructureUpdateTime)) {
                if (issues.length() > 0)
                    issues.append("; ");
                issues.append("reviewed later than structureModified");
            }
        }
        // There is no need to check between internalReviewed and reviewed. The importance
        // is their relationships to structureUpdated. The two elseif (if false) basically
        // Get the first correct case, which has been done already.
        if (issues.length() > 0) {
            inst2issue.put(instance, issues.toString());
            return false;
        }
        // Regardless, treat as false. Don't return to avoid aborting the application.
        inst2issue.put(instance, "unknown");
        return false;
    }
    
    /**
     * Three stars: internalReviewed is assigned. Its datetime is later than the latest datetime of the 
     * InstanceEdits in the structureUpdate slot if any. No reviewed is assign.
     * @param instance
     * @param lastStructureUpdateTime
     * @param internalReviewed
     * @param reviewed
     * @return
     * @throws Exception
     */
    private boolean checkThreeStars(GKInstance instance,
                                    Date lastStructureUpdateTime,
                                    List<GKInstance> internalReviewed,
                                    List<GKInstance> reviewed) throws Exception {
        Date lastInternalReviewedTime = getLastDateTime(internalReviewed);
        StringBuilder issues = new StringBuilder();
        if (lastInternalReviewedTime == null) {
            // Have to have internal reviewed for 3 star
            issues.append("No internalReviewed");
        }
        if (reviewed != null && reviewed.size() > 0) {
            if (issues.length() > 0)
                issues.append("; ");
            issues.append("reviewed assigned");
        }
        if (lastInternalReviewedTime != null &&
            lastStructureUpdateTime != null && 
            lastInternalReviewedTime.before(lastStructureUpdateTime)) {
            if (issues.length() > 0)
                issues.append("; ");
            issues.append("structureModified later than internalReviewed");
        }
        if (issues.length() > 0) {
            inst2issue.put(instance, issues.toString());
            return false;
        }
        return true;
    }

    private Date getLastDateTime(List<GKInstance> ies) throws Exception {
        if (ies == null || ies.size() == 0)
            return null;
        GKInstance lastIE = ies.get(ies.size() - 1);
        return InstanceUtilities.getDateTimeInInstanceEdit(lastIE);
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances)  {
        Set<GKInstance> filtered = filterInstancesForProject(instances, ReactomeJavaConstants.Event);
        try {
            for (Iterator<GKInstance> it = filtered.iterator(); it.hasNext();) {
                GKInstance inst = it.next();
                if (!inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus) ||
                        inst.getAttributeValue(ReactomeJavaConstants.reviewStatus) == null)
                    it.remove();
            }
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(parentComp,
                    "Error in filtering: " + e.getMessage(),
                    "Error in Filtering",
                    JOptionPane.ERROR_MESSAGE);
        }
        return filtered;
    }

    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        // Needed for MySQLAdaptor
        if (!(dataSource instanceof MySQLAdaptor))
            return;
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        dba.loadInstanceAttributeValues(instances,
                                        followAttributes);
    }

    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        return new ReviewStatusTableModel();
    }

    private class ReviewStatusTableModel extends ResultTableModel {
        private final SimpleDateFormat dateFormatter;
        private GKInstance instance;
        private String[][] data;

        public ReviewStatusTableModel() {
            setColNames(new String[]{"Attribute", 
                    "Value",
            "latestDateTime"});
            dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        @Override
        public int getRowCount() {
            // The following rows are displayed: reviewStatus, structureModified, reviewed, internalReviewed
            // The issue is listed at the final
            return instance == null ? 0 : 5;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String[] row = data[rowIndex];
            if (columnIndex >= row.length)
                return "";
            else
                return row[columnIndex];
        }

        @Override
        public void setInstance(GKInstance instance) {
            this.instance = instance;
            data = new String[getRowCount()][];
            try {
                // First reviewStatus
                int index = 0;
                GKInstance reviewStatus = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.reviewStatus);
                data[index] = new String[] {
                        ReactomeJavaConstants.reviewStatus,
                        reviewStatus == null ? "" : reviewStatus.getDisplayName(),
                                "N/A" // Not applied for latestDateTime
                };
                // StructureModified
                fillInIEsSlot(instance, ++index, ReactomeJavaConstants.structureModified);
                fillInIEsSlot(instance, ++index, ReactomeJavaConstants.reviewed);
                fillInIEsSlot(instance, ++index, ReactomeJavaConstants.internalReviewed);
                // Issue if any, which should be cached for each check previously
                data[++index] = new String[] {
                        "Issue",
                        inst2issue.get(instance) == null ? "" : inst2issue.get(instance),
                                "N/A"
                };
            }
            catch(Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace(System.err);
                JOptionPane.showMessageDialog(parentComp,
                        "Error in setInstance: " + e.getMessage(),
                        "Error in setInstance",
                        JOptionPane.ERROR_MESSAGE);
            }
            fireTableDataChanged();
        }

        private void fillInIEsSlot(GKInstance instance, 
                                   int index,
                                   String attName) throws InvalidAttributeException, Exception {
            List<GKInstance> structureModified = instance.getAttributeValuesList(attName);
            GKInstance latestIE = getLatestIE(structureModified);
            String latestDataTimeText = null;
            if (latestIE != null) {
                // Want to have a better format for dateTime
                Date dateTime = InstanceUtilities.getDateTimeInInstanceEdit(latestIE);
                latestDataTimeText = dateFormatter.format(dateTime.getTime());
            }
            else
                latestDataTimeText = "";
            data[index] = new String[] {
                    attName,
                    latestIE == null ? "" : latestIE.getDisplayName(),
                            latestDataTimeText
            };
        }

        /**
         * Grep the latest dateTime from the passed InstanceEdit instance list.
         * @param ies
         * @return
         */
        private GKInstance getLatestIE(List<GKInstance> ies) throws Exception {
            if (ies == null || ies.size() == 0)
                return null;
            // The last IE should have the latest date time
            GKInstance ie = ies.get(ies.size() - 1);
            return ie;
        }

    }


}
