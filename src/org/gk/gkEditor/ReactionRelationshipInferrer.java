package org.gk.gkEditor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import org.gk.database.FrameManager;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderableReaction;
import org.gk.util.DialogControlPane;
import org.gk.util.GKApplicationUtilities;

/**
 * This class is used to infer preceding/following relationships among reactions drawn in a pathway diagram.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class ReactionRelationshipInferrer {
    private Set<String> escapedEntities;
    
    public ReactionRelationshipInferrer() {
        String names = "ATP, ADP, Pi, H2O, GTP, GDP, CO2, H+";
        escapedEntities = Stream.of(names.split(", ")).collect(Collectors.toSet());
    }
    
    public void setEscapedEntities(Set<String> set) {
        this.escapedEntities = set;
    }
    
    public boolean customizeEsacpeEntities(PathwayEditor pathwayEditor) {
        JFrame parent = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, pathwayEditor);
        EscapeEntitiesDialog dialog = new EscapeEntitiesDialog(parent);
        // Place this small dialog at the center of the pathway editor
        GKApplicationUtilities.center(dialog, parent);
        dialog.setModal(true);
        dialog.setVisible(true);
        return dialog.isOkClicked;
    }
    
    public void inferRelationships(PathwayEditor pathwayEditor) {
        if (!customizeEsacpeEntities(pathwayEditor))
            return;
        List<GKInstance> reactions = new ArrayList<>();
        List<Renderable> renderables = pathwayEditor.getDisplayedObjects();
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        // Have to make sure all relationships have been checked out
        List<GKInstance> shellRles = new ArrayList<>();
        for (Renderable r : renderables) {
            if (r instanceof RenderableReaction) {
                GKInstance rle = fileAdaptor.fetchInstance(r.getReactomeId());
                if (rle != null) {
                    reactions.add(rle);
                    if (rle.isShell())
                        shellRles.add(rle);
                }
            }
        }
        if (shellRles.size() > 0) {
            JOptionPane.showMessageDialog(pathwayEditor,
                                          "Some reactions in the diagram have not been checked out. Use \"Update from DB\"\n"
                                          + "in the schema view to check out these reactions first.",
                                          "Shell Reactions in Diagram",
                                          JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        InstanceUtilities.sortInstances(reactions);
        try {
            RelationshipTableModel tableModel = new RelationshipTableModel();
            // This should be pairwise: two reactions may have a circle relationships
            for (int i = 0; i < reactions.size(); i++) {
                GKInstance rxt1 = reactions.get(i);
                for (int j = 0; j < reactions.size(); j++) {
                    GKInstance rxt2 = reactions.get(j);
                    if (i == j)
                        continue;
                    List<GKInstance> precedings = rxt2.getAttributeValuesList(ReactomeJavaConstants.precedingEvent);
                    if (precedings.contains(rxt1))
                        continue; // Don't check it
                    Set<GKInstance> overlapped = checkOverlap(rxt1, rxt2);
                    if (overlapped.size() == 0)
                        continue;
                    tableModel.addRow(rxt1, rxt2, overlapped);
                }
            }
            if (tableModel.data.size() == 0) {
                JOptionPane.showMessageDialog(pathwayEditor,
                                              "No relationship can be inferred in this pathway diagram.",
                                              "No Relationship",
                                              JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            showTable(pathwayEditor, tableModel);
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(pathwayEditor,
                                          "Error in inferring relationship: " + e.getMessage(),
                                          "Error in Inferring Relationship",
                                          JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void showTable(PathwayEditor pathwayEditor,
                           RelationshipTableModel tableModel) throws Exception {
        JFrame owner = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, pathwayEditor);
        RelationshipDialog dialog = new RelationshipDialog(owner, tableModel, pathwayEditor);
        dialog.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent e1) {
                if (!dialog.isOkCliked)
                    return;
                // Need to add the relationships
                List<List<GKInstance>> approvedRels = tableModel.getApprovedRelationships();
                if (approvedRels.size() == 0)
                    return;
                XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
                try {
                    for (List<GKInstance> pairs : approvedRels) {
                        GKInstance pre = pairs.get(0);
                        GKInstance fol = pairs.get(1);
                        fol.addAttributeValue(ReactomeJavaConstants.precedingEvent, pre);
                        fileAdaptor.markAsDirty(fol);
                        fileAdaptor.markAsDirty();
                    }
                }
                catch(Exception e) {
                    System.err.println(e.getMessage());
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(pathwayEditor,
                                                  "Error in setting relationship: " + e.getMessage(),
                                                  "Error in Setting Relationship",
                                                  JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        dialog.setModal(false); // So that the user can do something with the pathway diagram
        dialog.setVisible(true);
    }
    
    /**
     * Check if the output from rxt1 is input, catalyst or regulator of rxt2
     * @param rxt1
     * @param rxt2
     * @return
     * @throws Exception
     */
    private Set<GKInstance> checkOverlap(GKInstance rxt1,
                                         GKInstance rxt2) throws Exception {
        Set<GKInstance> rtn = new HashSet<>();
        List<GKInstance> rxt1Output = rxt1.getAttributeValuesList(ReactomeJavaConstants.output);
        rxt2.getAttributeValuesList(ReactomeJavaConstants.input)
            .stream()
            .filter(pe -> rxt1Output.contains(pe))
            .filter(pe -> !shouldEscape((GKInstance)pe))
            .forEach(pe -> rtn.add((GKInstance)pe));
        for (Object cas : rxt2.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)) {
            GKInstance ca = (GKInstance) ((GKInstance)cas).getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (ca == null || !rxt1Output.contains(ca) || shouldEscape(ca))
                continue;
            rtn.add(ca);
        }
        for (Object regulation : InstanceUtilities.getRegulations(rxt2)) {
            GKInstance regulator = (GKInstance) ((GKInstance)regulation).getAttributeValue(ReactomeJavaConstants.regulator);
            if (regulator == null || !rxt1Output.contains(regulator) || shouldEscape(regulator))
                continue;
            rtn.add(regulator);
        }
        return rtn;
    }
    
    private boolean shouldEscape(GKInstance pe) {
        String name = pe.getDisplayName().trim();
        // Remove compartment in the display name
        int index = name.lastIndexOf("[");
        if (index > 0 && name.endsWith("]"))
            name = name.substring(0, index).trim();
        return escapedEntities.contains(name);
    }
    
    private class EscapeEntitiesDialog extends JDialog {
        private JTextField tf;
        private boolean isOkClicked;
        
        public EscapeEntitiesDialog(JFrame owner) {
            super(owner);
            init();
        }
        
        private void init() {
            setTitle("Escape Entities");
            
            JPanel content = new JPanel();
            content.setBorder(BorderFactory.createEtchedBorder());
            content.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(4, 4, 4, 4);
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.WEST;
            JLabel label = new JLabel("Edit entities that should be escaped for analysis:");
            tf = new JTextField();
            tf.setColumns(28);
            tf.setText(escapedEntities.stream().sorted().collect(Collectors.joining(", ")));
            content.add(label, constraints);
            constraints.gridy = 1;
            content.add(tf, constraints);
            getContentPane().add(content, BorderLayout.CENTER);
            
            DialogControlPane controlPane = new DialogControlPane();
            controlPane.getOKBtn().addActionListener(e -> {
                updateEscapeEntities();
                isOkClicked = true;
                dispose();
            });
            controlPane.getCancelBtn().addActionListener(e -> dispose());
            getContentPane().add(controlPane, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(controlPane.getOKBtn());
            
            setSize(450, 250);
        }
        
        private void updateEscapeEntities() {
            String text = tf.getText().trim();
            escapedEntities = Stream.of(text.split(",")).map(t -> t.trim()).collect(Collectors.toSet());
        }
        
    }
    
    private class RelationshipDialog extends JDialog {
        private Icon instanceIcon = GKApplicationUtilities.createImageIcon(getClass(), "Instance.gif");
        private boolean isOkCliked = false;
        private PathwayEditor pathwayEditor;
        
        public RelationshipDialog(JFrame owner,
                                  TableModel tableModel,
                                  PathwayEditor pathwayEditor) {
            super(owner);
            this.pathwayEditor = pathwayEditor;
            init(tableModel);
        }
        
        private void init(TableModel tableModel) {
            setTitle("Reaction Relationship Inference");
            JLabel label = new JLabel("Review the following inferred relationships and check ones you want to approve:");
            label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            getContentPane().add(label, BorderLayout.NORTH);
            JTable table = new JTable(tableModel);
            table.getTableHeader().setReorderingAllowed(false); // So that we can do double click
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

                @Override
                public Component getTableCellRendererComponent(JTable table,
                                                               Object value,
                                                               boolean isSelected,
                                                               boolean hasFocus,
                                                               int row,
                                                               int column) {
                    setIcon(instanceIcon);
                    setText(((GKInstance)value).getDisplayName());
                    return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                }
                
            };
            table.setDefaultRenderer(GKInstance.class, renderer);
            getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
            DialogControlPane controlPane = new DialogControlPane();
            getContentPane().add(controlPane, BorderLayout.SOUTH);
            controlPane.getOKBtn().addActionListener(e -> {
                isOkCliked = true;
                dispose();
            });
            controlPane.getCancelBtn().addActionListener(e -> dispose());
            addTableListeners(table);
            
            setSize(1200, 500);
            GKApplicationUtilities.center(this);
        }
        
        private void addTableListeners(JTable table) {
            table.getSelectionModel().addListSelectionListener(l -> selectReactionsForTable(table));
            table.addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        Point p = e.getPoint();
                        int col = table.columnAtPoint(p);
                        if (col > 1)
                            return;
                        int row = table.rowAtPoint(p);
                        List<GKInstance> insts = ((RelationshipTableModel)table.getModel()).getReactionsInRow(row);
                        GKInstance inst = insts.get(col);
                        FrameManager.getManager().showInstance(inst, true); // It is local. So editable.
                    }
                }
                
            });
        }
        
        private void selectReactionsForTable(JTable table) {
            RelationshipTableModel tableModel = (RelationshipTableModel) table.getModel();
            int[] selectedRows = table.getSelectedRows();
            Set<GKInstance> selected = new HashSet<>();
            for (int row : selectedRows) {
                selected.addAll(tableModel.getReactionsInRow(row));
            }
            Set<Long> dbIds = selected.stream().map(i -> i.getDBID()).collect(Collectors.toSet());
            List<Renderable> selection = new ArrayList<>();
            for (Renderable r : (List<Renderable>)pathwayEditor.getDisplayedObjects()) {
                if (r.getReactomeId() != null && dbIds.contains(r.getReactomeId()))
                    selection.add(r);
            }
            pathwayEditor.setSelection(selection);
        }
        
    }
    
    private class RelationshipTableModel extends AbstractTableModel {
        private String[] cols = new String[] {
                "PrecedingReaction",
                "FollowingReaction",
                "SharedEntity",
                "Approval"
        };
        private List<List<Object>> data;
        
        public RelationshipTableModel() {
            data = new ArrayList<>();
        }
        
        public List<List<GKInstance>> getApprovedRelationships() {
            List<List<GKInstance>> rtn = new ArrayList<>();
            for (List<Object> row : data) {
                Boolean approval = (Boolean) row.get(row.size() - 1);
                if (approval) {
                    rtn.add(Stream.of((GKInstance)row.get(0), (GKInstance)row.get(1)).collect(Collectors.toList()));
                }
            }
            return rtn;
        }
        
        public List<GKInstance> getReactionsInRow(int row) {
            List<Object> rowData = data.get(row);
            return Stream.of((GKInstance)rowData.get(0), (GKInstance) rowData.get(1)).collect(Collectors.toList());
        }
        
        public void addRow(GKInstance rxt1, 
                           GKInstance rxt2,
                           Set<GKInstance> overlapped) {
            List<Object> row = new ArrayList<>();
            row.add(rxt1);
            row.add(rxt2);
            String entityText = overlapped.stream().map(e -> e.getDisplayName()).sorted().collect(Collectors.joining(", "));
            row.add(entityText);
            row.add(Boolean.FALSE);
            data.add(row);
        }
                           
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3)
                return Boolean.class;
            if (columnIndex == 0 || columnIndex == 1)
                return GKInstance.class;
            return String.class;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 3)
                return true;
            return false;
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public Object getValueAt(int row, int col) {
            List<Object> rowData = data.get(row);
            return rowData.get(col);
        }

        @Override
        public void setValueAt(Object arg0, int arg1, int arg2) {
            List<Object> row = data.get(arg1);
            row.set(arg2, arg0);
        }
    }

}
