package org.gk.reach;

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.gk.model.Reference;

@SuppressWarnings("serial")
public class RowFilterPane extends JPanel {
    private JButton filterBtn;
    private JButton addBtn;
    private JComboBox<String> filterTypeBox;
    private JTextField filterTextField;
    private JLabel inLabel;
    private JComboBox<String> colNameBox;
    private JTable eventTable;

    public RowFilterPane(JTable eventTable) {
        this.eventTable = eventTable;
        init();
    }

    private void init() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 4, 1));
        // Table filter field and label.
        JLabel label = new JLabel("Filter rows by:");
        filterTypeBox = new JComboBox<>();
        filterTypeBox.addItem("text containing");
        filterTypeBox.addItem("citations no less than");
        filterTypeBox.addItem("occurrences no less than");
        filterTypeBox.addItem("year no older than");
        filterTypeBox.setSelectedIndex(0);
        filterTextField = new JTextField(10);
        inLabel = new JLabel("in");
        colNameBox = new JComboBox<String>();
        TableModel model = eventTable.getModel();
        colNameBox.addItem("Any Column");
        for (int i = 0; i < eventTable.getColumnCount(); i++) {
            colNameBox.addItem(model.getColumnName(i));
        }
        colNameBox.setSelectedIndex(0); 
        addBtn = new JButton("Add"); 
        addBtn.addActionListener(e -> updateFilter());
        addBtn.setToolTipText("Click to add a new filter");
        filterBtn = new JButton("Filter");
        ActionListener al = (e -> doFilter(filterTypeBox, filterTextField));
        filterBtn.addActionListener(al);
        filterTextField.addActionListener(al);
        add(label);
        add(filterTypeBox);
        add(filterTextField);
        add(inLabel);
        add(colNameBox);
        add(addBtn);
        add(filterBtn);
        // Control the display
        filterTypeBox.addItemListener(e -> {
            boolean isVisible = filterTypeBox.getSelectedItem().toString().startsWith("text");
            inLabel.setVisible(isVisible);
            colNameBox.setVisible(isVisible);
        });
    }
    
    private void updateFilter() {
        Container container = getParent();
        if (container == null)
            return;
        if (addBtn.getText().equals("Add")) {
            RowFilterPane newPane = new RowFilterPane(eventTable);
            newPane.addBtn.setText("Remove");
            newPane.addBtn.setToolTipText("Click to remove this filter");
            newPane.filterBtn.setVisible(false);
            container.add(newPane);
            revalidate(); // Need to call the topmost container
        }
        else if (addBtn.getText().equals("Remove")) {
            container.remove(this);
            container.revalidate(); // Just need to call container. Weird!
        }
    }
    
    private RowFilter<ReachTableModel, Object> getFilterForText(String text) {
        String selectedCol = colNameBox.getSelectedItem().toString();
        if (selectedCol.equals("Any Column"))
            return RowFilter.regexFilter("(?i)" + text);
        TableModel model = eventTable.getModel();
        int colIndex = 0;
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (model.getColumnName(i).equals(selectedCol)) {
                colIndex = i;
                break;
            }
        }
        int selectedColIndex = colIndex;
        RowFilter<ReachTableModel, Object> rowFilter = new RowFilter<ReachTableModel, Object>() {
            @Override
            public boolean include(Entry<? extends ReachTableModel, ? extends Object> entry) {
                Object value = entry.getValue(selectedColIndex);
                return value.toString().contains(text);
            }
        };
        return rowFilter;
    }
    
    private RowFilter<ReachTableModel, Object> getFilter() {
        String type = (String) filterTypeBox.getSelectedItem();
        String text = filterTextField.getText().trim();
        if (type.startsWith("text")) {
            return getFilterForText(text);
        }
        else {
            if (!text.matches("\\d+")) {
                JOptionPane.showMessageDialog(eventTable,
                                              "Enter a number for this type of filtering.",
                                              "Wrong Filtering",
                                              JOptionPane.ERROR_MESSAGE);
                return null;
            }
            int number = new Integer(text);
            if (type.startsWith("citations")) {
                return getFilterForNumber(number, "citations");
            }
            else if (type.startsWith("occurrences")) {
                return getFilterForNumber(number, "occurrences");
            }
            else if (type.startsWith("year")) {
                return getFilterForYear(number);
            }
        }
        return null;
    }

    private void doFilter(JComboBox<String> typeBox,
                          JTextField filterText) {
        Container container = getParent();
        List<RowFilter<ReachTableModel, Object>> filters = new ArrayList<>();
        for (int i = 0; i < container.getComponentCount(); i++) {
            Component comp = container.getComponent(i);
            if (comp instanceof RowFilterPane) {
                RowFilter<ReachTableModel, Object> filter = ((RowFilterPane)comp).getFilter();
                if (filter != null)
                    filters.add(filter);
            }
        }
        if (filters.size() > 0) {
            @SuppressWarnings("unchecked")
            TableRowSorter<ReachTableModel> sorter = (TableRowSorter<ReachTableModel>) eventTable.getRowSorter();
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    private RowFilter<ReachTableModel, Object> getFilterForYear(int year) {
        RowFilter<ReachTableModel, Object> rowFilter = new RowFilter<ReachTableModel, Object>() {
            @Override
            public boolean include(Entry<? extends ReachTableModel, ? extends Object> entry) {
                Integer rowIndex = (Integer) entry.getIdentifier();
                ReachResultTableRowData rowData = entry.getModel().getReachResultTableRowData(rowIndex);
                // If the row has any publication no earlier than year, we will keep it
                for (Reference ref : rowData.getReferences()) {
                    if (ref.getYear() >= year)
                        return true;
                }
                return false;
            }
        };
        return rowFilter;
    }
    
    private RowFilter<ReachTableModel, Object> getFilterForNumber(int number,
                                                                  String type) {
        RowFilter<ReachTableModel, Object> rowFilter = new RowFilter<ReachTableModel, Object>() {
            @Override
            public boolean include(Entry<? extends ReachTableModel, ? extends Object> entry) {
                Integer rowIndex = (Integer) entry.getIdentifier();
                ReachResultTableRowData rowData = entry.getModel().getReachResultTableRowData(rowIndex);
                long count = Integer.MAX_VALUE;
                if (type.equals("citations"))
                    count = rowData.getReferences().stream().distinct().count();
                else if (type.equals("occurrences"))
                    count = rowData.getEvents().stream().distinct().count();
                return count >= number;
            }
        };
        return rowFilter;
    }
}
