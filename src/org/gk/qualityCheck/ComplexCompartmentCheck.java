/*
0 * Created on Mar 31, 2008
 *
 */
package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;

/**
 * This Complex Compartment QA check detects Complex compartment
 * inconsistency according to the following criteria:
 * <ul>
 * <li>There should be exactly one container compartment value in Complex,
 *     even though the attribute is defined as multi-valued.</li>
 * <li>If there is a non-empty Complex includedLocation value set,
 *     then the the included locations should equal the set of
 *     contained subunits compartments without the complex compartment.
 * </li>
 * <li>Otherwise, the Complex container compartment should be the
 *     same as each contained subunit compartment.</li>
 * <li>The complex compartment and includedLocations should be
 *     adjacent in a celluar region, as determined by the
 *     surroundedBy compartment slot.</li>
 * </ul>
 *
 * The following constraints are not validated until curators are ready to
 * address them:
 * <ul>
 * <li>There should not be more than two compartment values in all subunits.</li>
 * </ul>
 *
 * @author gwu
 */
public class ComplexCompartmentCheck extends CompartmentCheck {

    private final static String MISSING_COMPLEX_COMPARTMENT = "Complex compartment not a subunit compartment";
    private final static String TOO_MANY_COMPLEX_COMPARTMENTS = "More than one complex compartment";
    private final static String INCLUDED_NOT_IN_CONTAINED = "Extra included location";
    private final static String CONTAINED_NOT_IN_INCLUDED = "Missing included location";
    private final static String TOO_MANY_SUBUNIT_COMPARTMENTS = "More than two subunit compartments";
    private static final String COMPARTMENTS_MISMATCH = "Compartments mismatch";
    private static final String COMPARTMENTS_NOT_ADJACENT = "Noncontiguous compartments";

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
        Neo4JAdaptor dba = (Neo4JAdaptor) dataSource;
        Set<GKInstance> toBeLoaded = loadComplexHasComponent(instances,
                                                             dba);
        if (toBeLoaded == null)
            return;
        // No need to load EntitySet's component information. They should be handled
        // by EntitySet compartment checking.
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
        loadAttributes(toBeLoaded,
                ReactomeJavaConstants.Complex,
                ReactomeJavaConstants.includedLocation,
                dba);
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.Complex);
    }

    @Override
    protected Issue getIssue(GKInstance complex, Set<GKInstance> containedCompartments,
                           List<GKInstance> containerCompartments) throws Exception {
        // To make compare easier
        if (containerCompartments == null)
            containerCompartments = EMPTY_LIST;
        if (containerCompartments.isEmpty()) {
            return new Issue(MISSING_COMPLEX_COMPARTMENT);
        }
        if (containerCompartments.size() > 1) {
            return new Issue(TOO_MANY_COMPLEX_COMPARTMENTS, containerCompartments);
        }
//      This condition is not yet reported per the class javadoc.
//      if (containedCompartments.size() > 2)
//          return new Issue(Issue.Type.EXTRA_SUBUNIT_COMPARTMENTS);

        GKInstance complexCompartment = (GKInstance) containerCompartments.get(0);
        @SuppressWarnings("unchecked")
        List<GKInstance> includedLocations =
                complex.getAttributeValuesList(ReactomeJavaConstants.includedLocation);
        if (includedLocations.isEmpty()) {
            // The sole container value should be the same as each subunit value.
            if (containedCompartments.size() == 1) {
                if (containedCompartments.contains(complexCompartment)) {
                    return null;
                } else {
                    return new Issue(MISSING_COMPLEX_COMPARTMENT);
                }
            } else {
                List<GKInstance> extra = containedCompartments.stream()
                        .filter(cmpt -> cmpt != complexCompartment)
                        .collect(Collectors.toList());
                return new Issue(TOO_MANY_SUBUNIT_COMPARTMENTS, extra);
            }
        } else {
            Set<GKInstance> includedSet = new HashSet<GKInstance>(includedLocations);
            Set<GKInstance> containedSet = new HashSet<GKInstance>(containedCompartments);
            containedSet.remove(complexCompartment);
            if (!containedSet.equals(includedSet)) {
                return createMismatchIssue(containedSet, includedSet);
            }
        }

        // Check adjacency.
        Collection<GKInstance> nonAdjacent = new ArrayList<GKInstance>();
        List<GKInstance> complexCompartments =new ArrayList<GKInstance>(includedLocations);
        complexCompartments.add(complexCompartment);
        for (int i = 0; i < complexCompartments.size(); i++) {
            GKInstance container = complexCompartments.get(i);
            @SuppressWarnings("unchecked")
            List<GKInstance> neighbors =
                    container.getAttributeValuesList(ReactomeJavaConstants.surroundedBy);
            for (int j = i + 1; j < complexCompartments.size(); j++) {
                GKInstance other = complexCompartments.get(j);
                if (!neighbors.contains(other)) {
                    nonAdjacent.add(other);
                }
            }
        }
        if (!nonAdjacent.isEmpty()) {
            return new Issue(COMPARTMENTS_NOT_ADJACENT, nonAdjacent);
        }

        return null;
    }

    private Issue createMismatchIssue(Collection<GKInstance> containedCompartments,
            Collection<GKInstance> includedLocations) throws Exception {
        Set<GKInstance> containedOnly = new HashSet<GKInstance>(containedCompartments);
        containedOnly.removeAll(includedLocations);
        Set<GKInstance> includedOnly = new HashSet<GKInstance>(includedLocations);
        includedOnly.removeAll(containedCompartments);
        if (containedOnly.isEmpty()) {
            return new Issue(INCLUDED_NOT_IN_CONTAINED, includedOnly);
        }
        if (includedOnly.isEmpty()) {
            return new Issue(CONTAINED_NOT_IN_INCLUDED, containedOnly);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(COMPARTMENTS_MISMATCH);
        builder.append(" - ");
        builder.append("Included Only:");
        String includedStr = includedOnly.stream()
                .map(GKInstance::getDisplayName)
                .collect(Collectors.joining(", "));
        builder.append(includedStr);
        builder.append("; Contained Only:");
        String containedStr = containedOnly.stream()
                .map(GKInstance::getDisplayName)
                .collect(Collectors.joining(", "));
        builder.append(containedStr);
        String text = builder.toString();

        return new Issue(text);
    }

    protected ResultTableModel getResultTableModel() {
        ResultTableModel tableModel = new ComponentTableModel();
        tableModel.setColNames(new String[] {"Component", "Compartment"});
        return tableModel;
    }
}
