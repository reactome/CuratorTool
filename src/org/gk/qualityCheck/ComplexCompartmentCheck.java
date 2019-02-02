/*
 * Created on Mar 31, 2008
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * The logic for Complex.compartment check is implemented as following:
 * <ul>
 * <li>The complex should have a non-empty container compartment value.</li>
 * <li>There should be only one container compartment value in Complex,
 *     even though the attribute is defined as multi-valued.</li>
 * <li>The Complex container compartment should be one of its contained
 *     subunits' compartment values.</li>
 * </ul>
 * 
 * The following constraints are also validated, but reporting is disabled
 * until curators are ready to address them:
 * <ul>
 * <li>There should not be more than two compartment values in all subunits.
 * If two compartments in subunits, these two compartments should be adjacent.</li>
 * <li>includedLocation is not checked.</li>
 * </ul>
 * @author gwu
 *
 */
public class ComplexCompartmentCheck extends CompartmentCheck {
    
    public ComplexCompartmentCheck() {
        checkClsName = ReactomeJavaConstants.Complex;
        followAttributes = new String[] {
                ReactomeJavaConstants.hasComponent
        };
    }
    
    @Override
    public String getDisplayName() {
        return "Complex_Compartment_Inconsistency";
    }

    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        Set<GKInstance> toBeLoaded = loadComplexHasComponent(instances,
                                                             dba);
        if (toBeLoaded == null)
            return;
        // No need to load EntitySet's component information. They should be handled
        // by EntitySet compartment checking.
        if (progressPane != null)
            progressPane.setText("Load PhysicalEntity compartment...");
        loadAttributes(toBeLoaded,
                       ReactomeJavaConstants.PhysicalEntity,
                       ReactomeJavaConstants.compartment,
                       dba);
    }
    
    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.Complex);
    }
    
    @Override
    protected String getIssue(GKInstance container) throws Exception {
        // Recapitulate the check to get the issue.
        // TODO - eliminate this redundancy by collecting issue details
        //   as is done in other checks.
        // TODO - add Complex and Compartment db id and name columns.
        Set<GKInstance> contained = getAllContainedEntities(container);
        Set<GKInstance> containedCompartments = getContainedCompartments(contained);
        @SuppressWarnings("unchecked")
        List<GKInstance> containerCompartments =
                container.getAttributeValuesList(ReactomeJavaConstants.compartment);

        // The following code is adapted from EntitySetCompartmentCheck.
        if (containerCompartments == null)
            containerCompartments = EMPTY_LIST;
        if (containerCompartments.isEmpty() || containerCompartments.size() > 1)
            return "Extra compartments";
        GKInstance complexCompartment = (GKInstance) containerCompartments.get(0);
        // The sole container value should be one of the subunit values.
        if (!containedCompartments.contains(complexCompartment)) {
            return "Compartment is not a subunit compartment";
        }
        // That's all for now; modify when new comparison conditions are checked.
        // This is a fatal exception because the QA check is inconsistent and
        // should be addressed.
        throw new IllegalStateException("QA check issue could not be determined");
    }
   
    @SuppressWarnings("rawtypes")
    protected boolean compareCompartments(Set containedCompartments,
                                          List containerCompartments) throws Exception {
//         This condition is not yet reported per the class javadoc.
//         if (containedCompartments.size() > 2)
//             return false;
        // To make compare easier
        if (containerCompartments == null)
            containerCompartments = EMPTY_LIST;
        if (containerCompartments.isEmpty() || containerCompartments.size() > 1)
            return false;
        GKInstance complexCompartment = (GKInstance) containerCompartments.get(0);
        // The sole container value should be one of the subunit values.
        if (!containedCompartments.contains(complexCompartment)) {
            return false;
        }
// TODO - is this a requirement?
//          // Components and complex should have the same numbers of compartments used.
//          if (containerCompartments.size() != containedCompartments.size())
//              return false;
//
// The adjacency condition is not yet reported per the class javadoc.
//      if (containedCompartments.size() == 2) {
//            // Make sure two compartments are adjacent if there are two compartments
//            Iterator it = containedCompartments.iterator();
//            GKInstance compartment1 = (GKInstance) it.next();
//            GKInstance compartment2 = (GKInstance) it.next();
//            Map neighborMap = getNeighbors();
//            List neighbors = (List) neighborMap.get(compartment1.getDBID());
//            if (neighbors == null ||
//                !neighbors.contains(compartment2.getDBID()))
//                return false; // The used two compartments are not adjacent. This should be an error.
//            if (!containerCompartments.contains(compartment1) ||
//                !containerCompartments.contains(compartment2))
//                return false; // At least one of compartment used by component is not listed.
//        }
//        else if (containedCompartments.size() == 1) { 
//            // The  compartment
//            GKInstance componentCompartment = (GKInstance) containedCompartments.get(0);
//            GKInstance complexCompartment = (GKInstance) containerCompartments.get(0);
//            if (componentCompartment != complexCompartment)
//                return false;
//        }
        return true;
    }
    
    protected ResultTableModel getResultTableModel() {
        ResultTableModel tableModel = new ComponentTableModel();
        tableModel.setColNames(new String[] {"Component", "Compartment"});
        return tableModel;
    }
}
