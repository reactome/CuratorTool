package org.gk.reach;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

import org.gk.model.Reference;
import org.gk.reach.model.fries.Entity;
import org.gk.reach.model.fries.Event;
import org.gk.reach.model.fries.FrameObject;
import org.gk.reach.model.fries.FriesObject;

@SuppressWarnings("serial")
public class ReachTableModel extends AbstractTableModel {
    
    private List<String> columnNames;
    private List<ReachResultTableRowData> tableData;

	public ReachTableModel() {
	    // Copy this list to avoid change
		columnNames = new ArrayList<>(ReachConstants.COLUMN_NAMES_PROCESS);
		tableData = new ArrayList<>();
	}

	/**
	 * Used to create checkboxes in the "Accept?" column.
	 */
	@Override
	public Class<?> getColumnClass(int col) {
	    Class<?> retVal = Object.class;
	    if (getRowCount() > 0 && getValueAt(0, col) != null)
	        retVal =  getValueAt(0, col).getClass();
	    return retVal;
	}

	/**
	 * No need for Curators to edit cells.
	 */
    @Override
    public boolean isCellEditable(int row, int column) {
        if (column != findColumn(ReachConstants.ACCEPT))
            return false;
        return true;
    }

    /**
     * Get the underlying REACH data associated with a given row.
     *
     * @param rowIndex
     * @return events for given row
     */
    public ReachResultTableRowData getReachResultTableRowData(int rowIndex) {
        return tableData.get(rowIndex);
    }
    
    public List<ReachResultTableRowData> getTableData() {
        return new ArrayList<>(tableData);
    }
    
    public void setTableData(List<ReachResultTableRowData> data) {
        if (data == null)
            return;
        tableData.clear();
        tableData.addAll(data);
        fireTableStructureChanged();
    }

	/**
	 * Add REACH data to the given table.
	 *
	 * @param dataObjects
	 * @throws IOException 
	 */
    public void setReachData(List<FriesObject> dataObjects) {
        tableData.clear();
    	Map<String, ReachResultTableRowData> rowMap = new HashMap<>();
        for (int i = 0; i < dataObjects.size(); i++) { //For each of the FriesObjects
            FriesObject fo = dataObjects.get(i);
            if (fo.getEvents() == null || fo.getEvents().getFrameObjects() == null)
                continue;
            for (Event event : dataObjects.get(i).getEvents().getFrameObjects()) {
                if (event.getArguments().size() < 2)
                    continue;
                // Limit to events with two participants.
                FrameObject participantA = event.getArguments().get(0).getArg();
                FrameObject participantB = event.getArguments().get(1).getArg();
                if (!(participantA instanceof Entity) || !(participantB instanceof Entity))
                    continue;
                
                ReachResultTableRowData newRow = new ReachResultTableRowData();
                newRow.addEvent(event);
                Reference reference = dataObjects.get(i).getReference();
                newRow.addReference(reference);
                String uniquePairing = newRow.getRowKey();
                // If the contents of newRow are contained in rowSet.
                if (rowMap.keySet().contains(uniquePairing)) {
                	// Add the event to the uniquePairing key.
                    rowMap.get(uniquePairing).addReference(reference);
                	rowMap.get(uniquePairing).addEvent(event);
                }
                else {
                	//Add a new uniquePairing key with newRow
                    rowMap.put(uniquePairing, newRow);
                }
            }
        }
        // Add all rows in the map to the table.
        for (ReachResultTableRowData uniquePairing : rowMap.values()) {
            tableData.add(uniquePairing);
        }
        fireTableDataChanged();
    }

	@Override
	public int getColumnCount() {
		return columnNames.size();
	}

	@Override
	public int getRowCount() {
		return tableData.size();
	}

	@Override
	public Object getValueAt(int row, int col) {
		ReachResultTableRowData thisRow = tableData.get(row);
		switch(col) {
		case 0:
		    return thisRow.getParticipantAText();
		case 1:
		    return thisRow.getParticipantAId();
		case 2:
		    return thisRow.getParticipantAType();
		case 3:
		    return thisRow.getParticipantBText();
		case 4:
		    return thisRow.getParticipantBId();
		case 5:
		    return thisRow.getParticipantBType();
		case 6:
		    return thisRow.getInteractionType();
		case 7:
		    return thisRow.getInteractionSubtype();
		case 8:
		    return thisRow.getOccurrenceCoount();
		case 9:
		    return thisRow.getCitationCount();
		case 10:
		    return thisRow.getIsAccepted();
		default: 
		    return null;
		}
	}
	
	@Override
	public void setValueAt(Object newValue, int row, int col) {
		ReachResultTableRowData thisRow = tableData.get(row);
		thisRow.setIsAccepted((boolean) newValue);
	}

	@Override
	public String getColumnName(int index) {
		return columnNames.get(index);
	}

}
