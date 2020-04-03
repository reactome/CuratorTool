package org.gk.reach;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultRowSorter;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * This customized JPanel is used to show the connection degrees.
 * @author wug
 *
 */
public class ConnectionTablePane extends JPanel {
    private JTable table;
    
    public ConnectionTablePane() {
        init();
    }
    
    private void init() {
        setLayout(new BorderLayout());
        ConnectionTableModel model = new ConnectionTableModel();
        table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                doTablePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                doTablePopup(e);
            }
            
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
        
        JPanel controlPane = new JPanel();
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> close());
        controlPane.add(closeBtn);
        add(controlPane, BorderLayout.SOUTH);
    }
    
    private void doTablePopup(MouseEvent e) {
        Set<Integer> supportedCols = Collections.singleton(1);
        ReachUtils.doTablePopup(e, table, supportedCols);
    }

    
    public void setTableData(JTable eventTable) {
        ConnectionTableModel model = (ConnectionTableModel) table.getModel();
        model.setData(eventTable);
        @SuppressWarnings("rawtypes")
        DefaultRowSorter<?, ?> sorter = (DefaultRowSorter) table.getRowSorter(); 
        List<RowSorter.SortKey> keys = new ArrayList<>();
        keys.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
        sorter.setSortKeys(keys);
        sorter.sort();
    }
    
    public void showInDialog(JFrame owner) {
        JDialog dialog = new JDialog(owner);
        dialog.setTitle("Connection Degrees");
        dialog.getContentPane().add(this, BorderLayout.CENTER);
//        dialog.setModal(true); // Use modaless to easy navigation
        dialog.setLocationRelativeTo(owner);
        dialog.setSize(550, 650);
        dialog.setVisible(true);
    }
    
    private void close() {
        JDialog dialog = (JDialog) SwingUtilities.getAncestorOfClass(JDialog.class, this);
        if (dialog != null)
            dialog.dispose();
    }
    
    private class ConnectionTableModel extends AbstractTableModel {
        private String[] colNames = {"Participant", "Participant ID", "Connection Degree"};
        private List<List<Object>> data;
        
        public ConnectionTableModel() {
            data = new ArrayList<>();
        }
        
        public void setData(JTable reachTable) {
            ReachTableModel model = (ReachTableModel) reachTable.getModel();
            List<ReachResultTableRowData> tableData = ReachUtils.getDisplayedData(reachTable, model);
            if (tableData == null || tableData.size() == 0)
                return;
            data.clear();
            // Create a map
            Map<String, Set<String>> partToPartners = new HashMap<>();
            for (ReachResultTableRowData row : tableData) {
                String part1 = row.getParticipantAText() + "\t" + row.getParticipantAId();
                String part2 = row.getParticipantBText() + "\t" + row.getParticipantBId();
                partToPartners.compute(part1, (key, set) -> {
                    if (set == null)
                        set = new HashSet<>();
                    set.add(part2);
                    return set;
                });
                partToPartners.compute(part2, (key, set) -> {
                    if (set == null)
                        set = new HashSet<>();
                    set.add(part1);
                    return set;
                });
            }
            partToPartners.keySet().stream().forEach(p -> {
                Set<String> partners = partToPartners.get(p);
                String[] tokens = p.split("\t");
                List<Object> row = new ArrayList<>(3);
                row.add(tokens[0]);
                row.add(tokens[1]);
                row.add(new Integer(partners.size()));
                data.add(row);
            });
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return colNames.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<Object> row = data.get(rowIndex);
            return row.get(columnIndex);
        }

        @Override
        public String getColumnName(int column) {
            return colNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 2)
                return Integer.class;
            return String.class;
        }
        
        
    }
    
}
