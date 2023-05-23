package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * This QA check is used to make sure the Cell instance referring to a MarkerReference that is 
 * listed in the cell slot of the MarkerReference instance and the MarkerReference's marker is
 * referred in the Cell's marker slot.
 * @author wug
 *
 */
@SuppressWarnings({"unchecked", "serial"})
public class CellMarkerReferenceCheck extends SingleAttributeClassBasedCheck {
    private Map<GKInstance, String> inst2issue;

    public CellMarkerReferenceCheck() {
        this.checkAttribute = "MarkerReference in Cell";
        this.checkClsName = ReactomeJavaConstants.Cell;
        this.followAttributes = new String[] {ReactomeJavaConstants.markerReference};
        inst2issue = new HashMap<>();
    }
    
    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        if (!instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.markerReference))
            return true;
        List<GKInstance> markerReferences = instance.getAttributeValuesList(ReactomeJavaConstants.markerReference);
        if (markerReferences == null || markerReferences.size() == 0)
            return true;
        StringBuilder issue = null;
        for (GKInstance ref : markerReferences) {
            if (ref.getSchemClass().isValidAttribute(ReactomeJavaConstants.cell)) {
                // Check if the cell is the reference
                List<GKInstance> cells = ref.getAttributeValuesList(ReactomeJavaConstants.cell);
                if (cells == null || !cells.contains(instance)) {
                    if (issue == null) 
                        issue = new StringBuilder();
                    if (issue.length() > 0)
                        issue.append(";");
                    issue.append(instance.getDisplayName() + " is not in " + ref.getDisplayName());
                }
            }
            if (ref.getSchemClass().isValidAttribute(ReactomeJavaConstants.marker) &&
                (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.proteinMarker) ||
                (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.RNAMarker)))) {
                List<GKInstance> cellMarkers = new ArrayList<>();
                if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.proteinMarker)) {
                    List<GKInstance> markers = instance.getAttributeValuesList(ReactomeJavaConstants.proteinMarker);
                    if (markers != null)
                        cellMarkers.addAll(markers);
                }
                if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.RNAMarker)) {
                    List<GKInstance> markers = instance.getAttributeValuesList(ReactomeJavaConstants.RNAMarker);
                    if (markers != null)
                        cellMarkers.addAll(markers);
                }
                // Check the marker is referred in the cell's markers
                List<GKInstance> refMarkers = ref.getAttributeValuesList(ReactomeJavaConstants.marker);
                if (refMarkers == null)
                    refMarkers = Collections.EMPTY_LIST;
                // Make sure at least there is one marker matched
                Set<GKInstance> refMarkersCopy = new HashSet<>(refMarkers); 
                refMarkersCopy.retainAll(cellMarkers);
                if (refMarkers.size() == 0) {
                    if (issue == null) 
                        issue = new StringBuilder();
                    if (issue.length() > 0)
                        issue.append(";");
                    issue.append(ref.getDisplayName() + " marker doesn't contain cell marker"); 
                }
            }
        }
        if (issue == null)
            return true;
        inst2issue.put(instance, issue.toString());
        return false;
    }

    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        String issue = inst2issue.get(instance);
        if (issue == null)
            issue = "";
        return issue;
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return instances.stream()
                        .filter(i -> i.getSchemClass().isa(ReactomeJavaConstants.Cell))
                        .collect(Collectors.toSet());
    }

    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        // Needed for MySQLAdaptor
        if (!(dataSource instanceof MySQLAdaptor))
            return;
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        dba.loadInstanceReverseAttributeValues(instances,
                followAttributes);
    }

    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        return new CellMarkerReferenceTable();
    }

    private class CellMarkerReferenceTable extends ResultTableModel {
        private List<String[]> data;

        public CellMarkerReferenceTable() {
            setColNames(new String[]{"MarkerReference_DBID", 
                                     "MarkerReference_DisplayName",
                                     "MarkerReference_cell",
                                     "MarkerReference_LastIE_ID",
                                     "MarkerReference_LastIE_DisplayName"});
            data = new ArrayList<>();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String[] row = data.get(rowIndex);
            if (columnIndex >= row.length)
                return "";
            else
                return row[columnIndex];
        }

        @Override
        public void setInstance(GKInstance instance) {
            data.clear();
            try {
                List<GKInstance> markerReferences = instance.getAttributeValuesList(ReactomeJavaConstants.markerReference);
                for (GKInstance ref : markerReferences) {
                    List<GKInstance> cells = ref.getAttributeValuesList(ReactomeJavaConstants.cell);
                    if (cells != null && cells.contains(instance))
                        continue; // No need to display this ref since it is correct.
                    String cellText = null;
                    if (cells == null || cells.size() == 0)
                        cellText = "";
                    else
                        cellText = cells.stream().map(cell -> cell.getDisplayName()).collect(Collectors.joining(";"));
                    String[] row = new String[getColumnCount()];
                    row[0] = ref.getDBID() + "";
                    row[1] = ref.getDisplayName();
                    row[2] = cellText;
                    GKInstance lastIE = InstanceUtilities.getLatestIEFromInstance(ref);
                    row[3] = lastIE == null ? "" : lastIE.getDBID() + "";
                    row[4] = lastIE == null ? "" : lastIE.getDisplayName();
                    data.add(row);
                }
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
        
    }


}
