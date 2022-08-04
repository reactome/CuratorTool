/*
 * Created on Apr 3, 2008
 *
 */
package org.gk.qualityCheck;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;

/**
 * This class is used to check EntitySetCompartment. This is a very simple check
 * to make sure EntitySet and its members have the same compartment setting.
 * @author wgm
 *
 */
public class EntitySetCompartmentCheck extends CompartmentCheck {

    private static final String TOO_MANY_MEMBER_COMPARTMENTS = "More than two compartments in members";

    private static final String COMPARTMENTS_MISMATCH = "Compartment mismatch";

    public EntitySetCompartmentCheck() {
        checkClsName = ReactomeJavaConstants.EntitySet;
        followAttributes = new String[] {
                ReactomeJavaConstants.hasMember,
                ReactomeJavaConstants.hasCandidate
        };
    }

    @Override
    public String getDisplayName() {
        return "Extra_Compartments_In_Entity_Set_Or_Members";
    }

    @Override
    protected String getIssueTitle() {
        return "Extra_Compartment_DisplayNames";
    }

    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        PersistenceAdaptor dba = dataSource;
        Set<GKInstance> toBeLoaded = loadEntitySetMembers(instances, dba);
        if (progressPane != null)
            progressPane.setText("Load PhysicalEntity compartment...");

        for (GKInstance instance : toBeLoaded)
        {
            // PhysicalEntity does not have a "compartment" attribute anymore,
            // but many subclasses may have this attribute.
            if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.compartment))
            {
                loadAttributes(Arrays.asList(instance),
                    instance.getSchemClass().getName(),
                    ReactomeJavaConstants.compartment,
                    dba);
            }

        }
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.EntitySet);
    }

    @Override
    /**
     * Compartments used by an EntitySet's members should be the same as
     * its container EntitySet instance.
     *
     * @param container
     * @param containedCompartments
     * @param containerCompartments
     * @return
     */
    protected Issue getIssue(GKInstance container, Set<GKInstance> containedCompartments,
            List<GKInstance> containerCompartments) throws Exception {
        // Check if there are more than two member compartments.
        if (containedCompartments.size() > 2) {
            return new Issue(TOO_MANY_MEMBER_COMPARTMENTS, containedCompartments);
        }

        // Components and container should have the same number of compartments used.
        if (containerCompartments.size() != containedCompartments.size()) {
            return createMismatchIssue(containedCompartments, containerCompartments);
        }
        // Make sure these two collections are the same.
        for (Iterator<GKInstance> it = containedCompartments.iterator(); it.hasNext();) {
            Object obj = it.next();
            if (!containerCompartments.contains(obj)) {
                return createMismatchIssue(containedCompartments, containerCompartments);
            }
        }

        // Note: it is OK if neither the container nor the members have been assigned
        // a compartment. The mandatory checking should handle this case.
        return null;
    }

    private Issue createMismatchIssue(Set<GKInstance> containedCompartments,
            List<GKInstance> containerCompartments) throws Exception {
        Set<GKInstance> shared = new HashSet<GKInstance>(containedCompartments);
                shared.retainAll(containerCompartments);
                containedCompartments.removeAll(shared);
                containerCompartments.removeAll(shared);
                StringBuilder builder = new StringBuilder();
                builder.append(COMPARTMENTS_MISMATCH);
                builder.append(": ");
                if (containerCompartments.size() > 0) {
                    builder.append("EntitySet:");
                    containerCompartments.forEach(c -> builder.append(c.getDisplayName()).append(","));
                    builder.deleteCharAt(builder.length() - 1);
                }
                if (containedCompartments.size() > 0) {
                    if (builder.length() > 0)
                        builder.append("; ");
                    builder.append("Members:");
                    containedCompartments.forEach(c -> builder.append(c.getDisplayName()).append(","));
                    builder.deleteCharAt(builder.length() - 1);
                }
        String text = builder.toString();

        return new Issue(text);
    }

    protected void grepCheckOutInstances(GKInstance complex,
                                         Set<GKInstance> checkOutInstances) throws Exception {
        Set<GKInstance> components = getAllContainedEntities(complex);
        checkOutInstances.addAll(components);
    }

    protected ResultTableModel getResultTableModel() {
        ResultTableModel tableModel = new ComponentTableModel();
        tableModel.setColNames(new String[] {"Member", "Compartment"});
        return tableModel;
    }



}
