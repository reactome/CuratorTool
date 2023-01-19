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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

import org.gk.database.FrameManager;
import org.gk.database.SynchronizationManager;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
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
 * Note: The methods used to determine if two reactions have potential preceding/following relationships are 
 * copied directly from org.reactome.r3.ReactionMapGenerator in the mechismo_analysis package in the reactome-fi 
 * repo.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class ReactionRelationshipInferrer {
    private Icon instanceIcon = GKApplicationUtilities.createImageIcon(getClass(), "Instance.gif");
    private Set<String> escapedEntities;
    private boolean includeEntitySetMember = true;
    private boolean includeDrugs = false; // Default should be false
    
    public ReactionRelationshipInferrer() {
        String names = "ATP, ADP, Pi, H2O, GTP, GDP, CO2, H+";
        escapedEntities = Stream.of(names.split(", ")).collect(Collectors.toSet());
    }
    
    public void setEscapedEntities(Set<String> set) {
        this.escapedEntities = set;
    }
    
    private boolean customizeEsacpeEntities(PathwayEditor pathwayEditor) {
        JFrame parent = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, pathwayEditor);
        EscapeEntitiesDialog dialog = new EscapeEntitiesDialog(parent);
        // Place this small dialog at the center of the pathway editor
        GKApplicationUtilities.center(dialog, parent);
        dialog.setModal(true);
        dialog.setVisible(true);
        return dialog.isOkClicked;
    }
    
    private boolean downloadRejectReasons(PathwayEditor editor) {
        JFrame parentFrame = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, editor);
        return SynchronizationManager.getManager().downloadControlledVocabulary(parentFrame,
                                                                                ReactomeJavaConstants.NegativePrecedingEventReason,
                                                                                false);
    }
    
    public void inferRelationships(PathwayEditor pathwayEditor) {
        if (!customizeEsacpeEntities(pathwayEditor))
            return;
        // Need to get the controlled vocabulary
        if (!downloadRejectReasons(pathwayEditor))
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
                    if (!shouldBeInTable(rxt1, rxt2)) continue;
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
    
    private boolean shouldBeInTable(GKInstance pre, GKInstance fol) throws Exception {
        List<GKInstance> precedings = fol.getAttributeValuesList(ReactomeJavaConstants.precedingEvent);
        if (precedings.contains(pre))
            return false; // Don't check it
        // Also check negative setting
        List<GKInstance> negativePrecedingEvents = fol.getAttributeValuesList(ReactomeJavaConstants.negativePrecedingEvent);
        if (negativePrecedingEvents != null) {
            for (GKInstance negativePrecedingEvent : negativePrecedingEvents) {
                GKInstance negativeEvent = (GKInstance) negativePrecedingEvent.getAttributeValue(ReactomeJavaConstants.precedingEvent);
                if (pre == negativeEvent)
                    return false;
            }
        }
        return true;
    }
    
    private void commitChanges(PathwayEditor pathwayEditor, RelationshipTableModel tableModel) {
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        try {
            List<List<Object>> data = tableModel.data;
            for (int i = 0; i < data.size(); i++) {
                List<Object> rowData = data.get(i);
                ActionTerm action = (ActionTerm) rowData.get(3);
                switch (action) { 
                    case Approve :
                        GKInstance pre = (GKInstance) rowData.get(0);
                        GKInstance fol = (GKInstance) rowData.get(1);
                        fol.addAttributeValue(ReactomeJavaConstants.precedingEvent, pre);
                        fileAdaptor.markAsDirty(fol);
                        fileAdaptor.markAsDirty();
                        break;
                    case Reject :
                        pre = (GKInstance) rowData.get(0);
                        fol = (GKInstance) rowData.get(1);
                        GKInstance reason = (GKInstance) rowData.get(4);
                        GKInstance negativePrecedingEvent = fileAdaptor.createNewInstance(ReactomeJavaConstants.NegativePrecedingEvent);
                        negativePrecedingEvent.addAttributeValue(ReactomeJavaConstants.precedingEvent, pre);
                        negativePrecedingEvent.setAttributeValue(ReactomeJavaConstants.reason, reason);
                        InstanceDisplayNameGenerator.setDisplayName(negativePrecedingEvent);
                        fol.addAttributeValue(ReactomeJavaConstants.negativePrecedingEvent, negativePrecedingEvent);
                        fileAdaptor.markAsDirty(fol);
                        fileAdaptor.markAsDirty();
                        break;
                    case No_Action:
                        break;
                }
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
    
    private void showTable(PathwayEditor pathwayEditor,
                           RelationshipTableModel tableModel) throws Exception {
        JFrame owner = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, pathwayEditor);
        RelationshipDialog dialog = new RelationshipDialog(owner, tableModel, pathwayEditor);
        dialog.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent e1) {
                if (!dialog.isOkCliked)
                    return;
                commitChanges(pathwayEditor, tableModel);
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
        // Here we want to check if rxt1 is a preceding event of rxt2. Therefore, rxt1's output is checked
        Set<GKInstance> rtn = new HashSet<>();
        List<GKInstance> rxt1Output = rxt1.getAttributeValuesList(ReactomeJavaConstants.output);
        Set<GKInstance> rxt2LfhEntities = getLeftHandEntities(rxt2);
        for (GKInstance output : rxt1Output) {
            if (shouldEscape(output))
                continue;
            for (GKInstance lfhEntity : rxt2LfhEntities) {
                if (shouldEscape(lfhEntity))
                    continue;
                GKInstance shared = getSharedEntity(lfhEntity, output);
                if (shared != null)
                    rtn.add(shared);
            }
        }
        return rtn;
    }
    
    /**
     * Check if two entities are the same or have shared entity set members.
     * @param lfhEntity
     * @param output
     * @return
     * @throws Exception
     */
    private GKInstance getSharedEntity(GKInstance lfhEntity,
                                       GKInstance output) throws Exception {
        // If they are the same, return true
        if (lfhEntity == output)
            return output;
        if (!includeEntitySetMember)
            return null;
        Set<GKInstance> lfhMembers = null;
        if (lfhEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            // If both are EntitySets having shared member, return true
            lfhMembers = InstanceUtilities.getContainedInstances(lfhEntity,
                                                                 ReactomeJavaConstants.hasMember,
                                                                 ReactomeJavaConstants.hasCandidate);
            lfhMembers = lfhMembers.stream().filter(m -> !shouldEscape(m)).collect(Collectors.toSet());
            if (lfhMembers.contains(output))
                return output; // If the first reaction output is a member of the second reaction input set
        }
        if (output.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            // If output is an EntitySet having lfhEntity as a member, return true
            Set<GKInstance> members = InstanceUtilities.getContainedInstances(output,
                                                                              ReactomeJavaConstants.hasMember,
                                                                              ReactomeJavaConstants.hasCandidate);
            members = members.stream().filter(m -> !shouldEscape(m)).collect(Collectors.toSet());
            if (members.contains(lfhEntity))
                return lfhEntity; // If some member of the output in the first reaction is input to the second reaction
            if (lfhMembers != null) {
                // If both are EntitySets having shared member, return true
                lfhMembers.retainAll(members);
                if (lfhMembers.size() > 0)
                    return lfhMembers.stream().findAny().get(); // There is at least one shared member
            }
        }
        return null;
    }
    
    
    private Set<GKInstance> getLeftHandEntities(GKInstance reaction) throws Exception {
        Set<GKInstance> rtn = new HashSet<GKInstance>();
        List<GKInstance> input = reaction.getAttributeValuesList(ReactomeJavaConstants.input);
        if (input != null)
            rtn.addAll(input);
        List<GKInstance> cas = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        if (cas != null) {
            for (GKInstance ca : cas) {
                GKInstance catalyst = (GKInstance) ca.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                if (catalyst != null)
                    rtn.add(catalyst);
            }
        }
        Collection<GKInstance> regulations = InstanceUtilities.getRegulations(reaction);
        if (regulations != null && regulations.size() > 0) {
            for (GKInstance regulation : regulations) {
                // Should escape negative relation
                if (regulation.getSchemClass().isa(ReactomeJavaConstants.NegativeRegulation))
                    continue;
                GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
                if (regulator != null)
                    rtn.add(regulator);
            }
        }
        return rtn;
    }
    
    private boolean shouldEscape(GKInstance pe) {
        // For drugs
        if (pe.getSchemClass().isa(ReactomeJavaConstants.Drug) && !includeDrugs)
            return true;
        String name = pe.getDisplayName().trim();
        // Remove compartment in the display name
        int index = name.lastIndexOf("[");
        if (index > 0 && name.endsWith("]"))
            name = name.substring(0, index).trim();
        return escapedEntities.contains(name);
    }
    
    private class EscapeEntitiesDialog extends JDialog {
        private JTextField tf;
        private JCheckBox includeSetMember;
        private JCheckBox includeDrugs;
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
            includeSetMember = new JCheckBox("Include entity set members for analysis");
            includeSetMember.setSelected(true);
            constraints.gridy ++;
            content.add(includeSetMember, constraints);
            includeDrugs = new JCheckBox("Include drugs for analysis");
            includeDrugs.setSelected(false); // Default for drugs should be false
            constraints.gridy ++;
            content.add(includeDrugs, constraints);
            
            getContentPane().add(content, BorderLayout.CENTER);
            
            DialogControlPane controlPane = new DialogControlPane();
            controlPane.getOKBtn().addActionListener(e -> {
                updateEscapeEntities();
                ReactionRelationshipInferrer.this.includeEntitySetMember = includeSetMember.isSelected();
                ReactionRelationshipInferrer.this.includeDrugs = includeDrugs.isSelected();
                isOkClicked = true;
                dispose();
            });
            controlPane.getCancelBtn().addActionListener(e -> dispose());
            getContentPane().add(controlPane, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(controlPane.getOKBtn());
            
            setSize(450, 275);
        }
        
        private void updateEscapeEntities() {
            String text = tf.getText().trim();
            escapedEntities = Stream.of(text.split(",")).map(t -> t.trim()).collect(Collectors.toSet());
        }
        
    }
    
    private class RelationshipDialog extends JDialog {
        
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
            JLabel label = new JLabel("Review the following inferred relationships and check ones you want to approve (" + 
            		tableModel.getRowCount() + " in total):");
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
                    // Call before the customization
                    Component rtn = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (value == null) {
                        setIcon(null);
                        setText("");
                    }
                    else {
                        setIcon(instanceIcon);
                        setText(((GKInstance)value).getDisplayName());
                    }
                    return rtn;
                }
            };
            table.setDefaultRenderer(GKInstance.class, renderer);
            
            JComboBox<ActionTerm> actionBox = new JComboBox<>();
            Stream.of(ActionTerm.values()).forEach(actionBox::addItem);
            TableCellEditor cellEditor = new DefaultCellEditor(actionBox);
            table.setDefaultEditor(ActionTerm.class, cellEditor);
            TableCellEditor rejectReasonEditor = new RejectReasonCellEditor(new JComboBox<GKInstance>());
            table.setDefaultEditor(GKInstance.class, rejectReasonEditor);
            
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
                "Action",
                "Reject Reason"
        };
        private List<List<Object>> data;
        
        public RelationshipTableModel() {
            data = new ArrayList<>();
        }
        
        public List<List<GKInstance>> getApprovedRelationships() {
            List<List<GKInstance>> rtn = new ArrayList<>();
            for (List<Object> row : data) {
                ActionTerm approval = (ActionTerm) row.get(3);
                if (approval == ActionTerm.Approve) {
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
            row.add(ActionTerm.No_Action);
            row.add(null); // Nothing for the time being
            data.add(row);
        }
                           
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3)
                return ActionTerm.class;
            if (columnIndex == 2)
                return String.class;
            return GKInstance.class;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 3 || columnIndex == 4)
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
        public void setValueAt(Object value, int row, int col) {
            List<Object> rowData = data.get(row);
            rowData.set(col, value);
            if (col == 3 && value != ActionTerm.Reject)
                rowData.set(col + 1, null); // There should be no value in the reason if not rejected
        }
    }
    
    private class RejectReasonCellEditor extends DefaultCellEditor {
        
        public RejectReasonCellEditor(JComboBox<GKInstance> box) {
            super(box);
            DefaultListCellRenderer renderer = new DefaultListCellRenderer() {

                @Override
                public Component getListCellRendererComponent(JList<?> list,
                                                              Object value,
                                                              int index,
                                                              boolean isSelected,
                                                              boolean cellHasFocus) {
                    Component rtn = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    // This is a label
                    if (value != null) {
                        setIcon(instanceIcon);
                        setText(((GKInstance)value).getDisplayName());
                    }
                    else {
                        setIcon(null);
                        setText("");
                    }
                    return rtn;
                }
            };
            renderer.setIcon(instanceIcon);
            box.setRenderer(renderer);
        }
        
        private List<GKInstance> getReasons(int row, JTable table) {
        	// This should be based on the Action 
        	RelationshipTableModel model = (RelationshipTableModel) table.getModel();
        	ActionTerm action = (ActionTerm) model.getValueAt(row, 3);
        	if (action == ActionTerm.Approve || action == ActionTerm.No_Action)
        		return Collections.EMPTY_LIST;
            try {
                // Assume download has been done previously
                XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
                Collection<GKInstance> instances = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.NegativePrecedingEventReason);
                if (instances != null) {
                    List<GKInstance> rtn = new ArrayList<>(instances);
                    InstanceUtilities.sortInstances(rtn);
                    return rtn;
                }
            }
            catch(Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(table,
                                              "Cannot list the controlled vocabulary for the reject reason: " + e.getMessage(),
                                              "Error in Controlled Vocabulary",
                                              JOptionPane.ERROR_MESSAGE);
            }
            return Collections.EMPTY_LIST;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     int row,
                                                     int column) {
            JComboBox<GKInstance> box = (JComboBox<GKInstance>) super.getTableCellEditorComponent(table, value, isSelected, row, column);
            // Fill the list on the fly
            box.removeAllItems();
            List<GKInstance> reasons = getReasons(row, table);
            if (reasons != null) 
                reasons.forEach(box::addItem);
            return box;
        }
        
    }
    
    private enum ActionTerm {
        Approve,
        Reject,
        No_Action
    }

}
