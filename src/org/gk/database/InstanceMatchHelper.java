package org.gk.database;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;

/**
 * A helper class to handle matching between local instances and database instances.
 * @author wug
 *
 */
@SuppressWarnings({"rawtypes", "unchecked", "serial"})
public class InstanceMatchHelper {
    // Actions for matched instances
    private final int SAVE_AS_NEW_ACTION = 0;
    private final int USE_DB_COPY_ACTION = 1;
    private final int MERGE_ACTION = 2;
    private String[] actions = {"Save as New",
                                "Use the Selected",
                                "Merge New to Selected"};
    // For some methods in the manager
    private SynchronizationManager manager;

    public InstanceMatchHelper(SynchronizationManager manager) {
        this.manager = manager;
    }

    /**
     * Search matched instances for a specified local GKInstance object.
     * @param localInstance Local instance to search for matches in database 
     * @param parentDialog Window GUI to display messages
     */
    public void matchInstanceInDB(GKInstance localInstance, Window parentDialog) {
        MySQLAdaptor dbAdaptor = PersistenceManager.getManager().getActiveMySQLAdaptor(parentDialog);
        if (dbAdaptor == null)
            return;
        Collection matchedInstances = null;
        try {
            matchedInstances = dbAdaptor.fetchIdenticalInstances(localInstance);
        }
        catch (Exception e) {
            System.err.println("SynchronizationManager.matchInstanceInDB(): " + e);
            e.printStackTrace();
        }
        if (matchedInstances == null || matchedInstances.size() == 0) {
            JOptionPane.showMessageDialog(parentDialog, 
                                          "Cannot find a matched instance in the database.",
                                          "Matching Result",
                                          JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        OneInstanceMatchDialog dialog = null;
        if (parentDialog instanceof JDialog)
            dialog = new OneInstanceMatchDialog((JDialog) parentDialog);
        else if (parentDialog instanceof JFrame)
            dialog = new OneInstanceMatchDialog((JFrame) parentDialog);
        else
            dialog = new OneInstanceMatchDialog();
        // Don't need save as new
        dialog.hideSaveAsNewButton();
        dialog.setLocalInstance(localInstance);
        dialog.setInstanceList(new ArrayList(matchedInstances));
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(parentDialog);
        dialog.setModal(true);
        dialog.setVisible(true);
        if (!dialog.isOKClicked || dialog.getSelectedAction() == SAVE_AS_NEW_ACTION)
            return;
        GKInstance dbCopy = dialog.getSelectedInstance();
        int action = dialog.getSelectedAction();
        // Handle only merge to db and use db actions here.
        handleMatchedInstance(localInstance, dbCopy, action, parentDialog);
    }

    /**
     * A helper method to handle the matching between the newly created local GKInstance and
     * the matched GKInstance in the database. This method take care of two actions: MERGE and
     * USE_DB_COPY, since no action is needed for SAVE_AS_NEW.
     * @param newInstance the newly created local GKInstance
     * @param matchedInstance the matched GKInstance in the database.
     */
    private boolean handleMatchedInstance(GKInstance newInstance, 
                                          GKInstance matchedInstance, 
                                          int action,
                                          Component comp) {
        XMLFileAdaptor fileAdaptor = (XMLFileAdaptor) newInstance.getDbAdaptor();
        // Check if there is a local copy 
        GKInstance localCopy = null;
        try {
            localCopy = fileAdaptor.fetchInstance(matchedInstance.getSchemClass().getName(), 
                                                  matchedInstance.getDBID());
        }
        catch (Exception e) {
            System.err.println("SynchronizationManager.checkMatchedInstances(): " + e);
            e.printStackTrace();
        }
        if (localCopy == null) {
            // Ask to check out the a copy of matchedInstance from the database
            localCopy = manager.checkOutShallowly(matchedInstance);
        }
        if (localCopy == null) {
            JOptionPane.showMessageDialog(comp,
                                          "Cannot checked out the matched instance.",
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return false;                              
        }
        if (localCopy.isShell()) {
            try {
                manager.updateFromDB(localCopy, matchedInstance);
            }
            catch (Exception e1) {
                System.err.println("SynchronizationManager.handleMatchedInstance(): " + e1);
                e1.printStackTrace();
            }
        }
        if (localCopy != null) {
            // Check if local Copy is the same as the one in the database.
            if (!InstanceUtilities.compare(localCopy, matchedInstance)) {
                // Have to make the localCopy and matchedInstance identical.
                // Otherwise, some unexpected behaviors will occur.
                JOptionPane.showMessageDialog(comp,
                                              "The selected matched instance has been checked out. The local copy will be used.\n" +
                                                      "However, there are differences between the local and db copies. You have to\n" +
                                                      "resolve these differences before continuing.",
                                                      "Conflicts in Matched Instances",
                                                      JOptionPane.INFORMATION_MESSAGE);
                InstanceComparisonPane comparisonPane = new InstanceComparisonPane();
                comparisonPane.setInstances(localCopy, matchedInstance);
                String title = "Comparing Instances in Class \"" + localCopy.getSchemClass().getName() + "\"";
                JDialog dialog = GKApplicationUtilities.createDialog(comp, title);
                dialog.getContentPane().add(comparisonPane, BorderLayout.CENTER);
                dialog.setModal(true);
                dialog.setSize(800, 600);
                GKApplicationUtilities.center(dialog);
                dialog.setModal(true);
                dialog.setVisible(true);
                // Whatever is done to the local copy will be regarded as the user's options
                // Go to the next step
            }
            if (action == MERGE_ACTION) { // Need to merge something to the local copy from the newInstance
                // Do another merge
                final InstanceMergingPane mergingPane = new InstanceMergingPane();
                mergingPane.setInstances(localCopy, newInstance);
                String title = "Merge New Instance to Old Instance in Class \"" + localCopy.getSchemClass().getName() + "\"";
                JDialog dialog = GKApplicationUtilities.createDialog(comp, title);
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialog.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        mergingPane.dispose();
                    }
                });
                dialog.getContentPane().add(mergingPane, BorderLayout.CENTER);
                dialog.setModal(true);
                dialog.setSize(800, 600);
                GKApplicationUtilities.center(dialog);
                dialog.setModal(true);
                dialog.setVisible(true);
                if (!mergingPane.isOKClicked())
                    return false;
            }
            // action should be either MERGE_ACTION or USE_DB_COPY_ACTION;
            if (action == USE_DB_COPY_ACTION || action == MERGE_ACTION) {
                // Replace the references
                try {
                    java.util.List referrers = fileAdaptor.getReferers(newInstance);
                    if (referrers != null && referrers.size() > 0) {
                        for (Iterator it = referrers.iterator(); it.hasNext();) {
                            GKInstance referrer = (GKInstance) it.next();
                            InstanceUtilities.replaceReference(referrer, newInstance, localCopy);
                            //fileAdaptor.markAsDirty(referrer);
                        }
                    }
                    //A special case for default InstanceEdit
                    GKInstance defaultInstanceEdit = manager.getDefaultInstanceEdit(SwingUtilities.getWindowAncestor(comp));
                    if (defaultInstanceEdit != null && newInstance.getSchemClass().isa("Person")) {
                        java.util.List authors = defaultInstanceEdit.getAttributeValuesList("author");
                        int index = authors.indexOf(newInstance);
                        if (index >= 0)
                            authors.set(index, localCopy);
                    }
                    // Delete the local copy
                    fileAdaptor.deleteInstance(newInstance);
                    return true;
                }
                catch(Exception e) {
                    System.err.println("SyncrhonizationManager.handleMatchedInstance(): " + e);
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Check to make sure local deleted instances are not used for match selection. Otherwise,
     * the user may choose a deleted local instance (actual the DB instance) and then accidently
     * delete it. This should not be allowed.
     * @param localToDbInsts
     * @param fileAdaptor
     * @param parentDialog
     */
    private boolean validateDeleted(Map<GKInstance, Collection<GKInstance>> localToDbInsts,
                                 XMLFileAdaptor fileAdaptor,
                                 Window parentDialog) {
        Map<Long, String> deletedMap = fileAdaptor.getDeleteMap();
        if (deletedMap == null || deletedMap.size() == 0)
            return true; // No local deletion. Do nothing.
        Set<GKInstance> deletedDbInsts = new HashSet<>();
        for (Iterator<GKInstance> it = localToDbInsts.keySet().iterator(); it.hasNext();) {
            GKInstance localInst = it.next();
            Collection<GKInstance> dbInsts = localToDbInsts.get(localInst);
            for (Iterator<GKInstance> dbIt = dbInsts.iterator(); dbIt.hasNext();) {
                GKInstance dbInst = dbIt.next();
                if (deletedMap.keySet().contains(dbInst.getDBID())) {
                    dbIt.remove();
                    deletedDbInsts.add(dbInst);
                }
            }
            if (dbInsts.size() == 0)
                it.remove();
        }
        if (deletedDbInsts.size() == 0)
            return true;
        // Show a warning dialog for the user's attention.
        InstanceListDialog dialog = InstanceListDialog.showInstanceList(new ArrayList<>(deletedDbInsts),
                                            "Matched Database Instances Deleted in Local Project", 
                                            "These database instances are matched with local instances to be committed. However, they " + 
                                            "have been deleted in the local project. The local instances will be saved as new. Do you wan " +
                                            "to continue?", 
                                            parentDialog,
                                            true);
        return dialog.isOKClicked();
    }

    /**
     * Have to specify parentDialog to display the matched instances.
     * @param localSet
     * @param dbAdaptor
     * @param fileAdaptor
     * @param parentDialog
     */
    boolean checkMatchedInstances(Set localSet, 
                                  MySQLAdaptor dbAdaptor,
                                  XMLFileAdaptor fileAdaptor,
                                  Window parentDialog) {
        if (localSet == null || localSet.size() == 0)
            return true;
        Map matchedMap =  new HashMap();
        try {
            for (Iterator it = localSet.iterator(); it.hasNext();) {
                GKInstance instance = (GKInstance)it.next();
                Collection c = dbAdaptor.fetchIdenticalInstances(instance);
                if (c != null && c.size() > 0) {
                    matchedMap.put(instance, c);
                }
            }
        }
        catch (Exception e) {
            System.err.println("SynchronizationManager.checkMatchedInstances(): " + e);
            e.printStackTrace();
        }
        if (!validateDeleted(matchedMap, fileAdaptor, parentDialog))
            return false; // Need to stop 
        if (matchedMap.size() == 0)
            return true;
        if (matchedMap.size() == 1) {
            GKInstance instance = (GKInstance) matchedMap.keySet().iterator().next();
            OneInstanceMatchDialog dialog = null;
            if (parentDialog instanceof JDialog) 
                dialog = new OneInstanceMatchDialog((JDialog)parentDialog);
            else if (parentDialog instanceof JFrame)
                dialog = new OneInstanceMatchDialog((JFrame)parentDialog);
            if (dialog == null) // Cannot display it.
                return false;
            dialog.setLocalInstance(instance);
            final Collection c = (Collection) matchedMap.get(instance);
            if (c instanceof java.util.List)
                dialog.setInstanceList((java.util.List)c);
            else
                dialog.setInstanceList(new ArrayList(c));
            dialog.setSize(600, 500);
            dialog.setLocationRelativeTo(parentDialog);
            dialog.setModal(true);
            dialog.setVisible(true);
            if (!dialog.isOKClicked)
                return false;
            if (dialog.getSelectedAction() == SAVE_AS_NEW_ACTION) {
                // Don't need do anything. Just return.
                return true;
            }
            GKInstance dbCopy = dialog.getSelectedInstance();
            int action = dialog.getSelectedAction();
            if(!handleMatchedInstance(instance, dbCopy, action, parentDialog))
                return false;
            // Either MERGE or USE_DB_COPY. No need to go down
            localSet.remove(instance); // No need to save instance
            return true;
        }
        else {
            MultipleMatchDialog dialog = null;
            if (parentDialog instanceof JDialog)
                dialog = new MultipleMatchDialog((JDialog)parentDialog);
            else if (parentDialog instanceof JFrame)
                dialog = new MultipleMatchDialog((JFrame)parentDialog);
            else
                dialog = new MultipleMatchDialog();
            dialog.setMatchedInstances(matchedMap);
            dialog.setSize(600, 400);
            dialog.setLocationRelativeTo(parentDialog);
            dialog.setModal(true);
            dialog.setVisible(true);
            if (!dialog.isOKClicked)
                return false;
            Map actionMap = dialog.getSelectedActions();
            Map selectedMatchedMap = dialog.getSelectedMatchedInstances();
            // Handle matched actions one by one
            for (Iterator it = actionMap.keySet().iterator(); it.hasNext();) {
                GKInstance newInstance = (GKInstance) it.next();
                Integer actionValue = (Integer) actionMap.get(newInstance);
                int action = actionValue.intValue();
                if (action == SAVE_AS_NEW_ACTION) // Do nothing with this option
                    continue; 
                GKInstance matchedInstance = (GKInstance) selectedMatchedMap.get(newInstance);
                handleMatchedInstance(newInstance, matchedInstance, action, parentDialog);
                // Don't need to save
                localSet.remove(newInstance);
            }
            return true;
        }
    }

    class OneInstanceMatchDialog extends JDialog {
        // GUIs
        private JRadioButton saveAsNew;
        private JRadioButton useDBCopy;
        private JRadioButton merge;
        private InstanceListPane listPane;
        private AttributePane attPane;
        // Local one
        boolean isOKClicked = false;

        public OneInstanceMatchDialog(JDialog parentDialog) {
            super(parentDialog);
            init();
        }

        public OneInstanceMatchDialog(JFrame parentFrame) {
            super(parentFrame);
            init();
        }

        public OneInstanceMatchDialog() {
            super();
            init();
        }

        private void init() {
            JPanel centerPane = new JPanel();
            centerPane.setBorder(BorderFactory.createRaisedBevelBorder());
            centerPane.setLayout(new BorderLayout());
            listPane = new InstanceListPane();
            attPane = new AttributePane();
            listPane.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            listPane.addSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    java.util.List selection = listPane.getSelection();
                    if (selection == null || selection.size() == 0) {
                        attPane.setInstance(null);
                        merge.setEnabled(false);
                        useDBCopy.setEnabled(false);
                        saveAsNew.setSelected(true);
                    }
                    else {
                        attPane.setInstance((GKInstance)selection.get(0));
                        merge.setEnabled(true);
                        useDBCopy.setEnabled(true);
                        // default with useDBCopy
                        useDBCopy.setSelected(true);
                    }
                }
            });
            JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                            listPane,
                                            attPane);
            jsp.setDividerLocation(300);
            jsp.setResizeWeight(0.5);
            centerPane.add(jsp, BorderLayout.CENTER);                                

            JPanel optionPane = new JPanel();
            optionPane.setLayout(new GridBagLayout());
            optionPane.setBorder(BorderFactory.createEtchedBorder());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.WEST;
            constraints.insets = new Insets(4, 8, 4, 4);
            constraints.weightx = 0.5;
            JLabel optionLabel = new JLabel("Choose one of the following actions:");
            optionPane.add(optionLabel, constraints);
            saveAsNew = new JRadioButton("Save as New Instance");
            constraints.gridy = 1;
            optionPane.add(saveAsNew, constraints);
            useDBCopy = new JRadioButton("Use the selected Instance");
            constraints.gridy = 2;
            optionPane.add(useDBCopy, constraints);
            merge = new JRadioButton("Merge the local copy to the selected one");
            constraints.gridy = 3;
            optionPane.add(merge, constraints);
            ButtonGroup btnGroup = new ButtonGroup();
            btnGroup.add(saveAsNew);
            btnGroup.add(useDBCopy);
            btnGroup.add(merge);
            // Default 
            saveAsNew.setSelected(true);
            useDBCopy.setEnabled(false);
            merge.setEnabled(false);
            centerPane.add(optionPane, BorderLayout.SOUTH);
            getContentPane().add(centerPane, BorderLayout.CENTER);

            // OK and cancel pane
            JPanel okCancelPane = new JPanel();
            okCancelPane.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 8));                                             
            final JButton okBtn = new JButton("OK");
            okBtn.setMnemonic('O');
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setMnemonic('C');
            okBtn.setPreferredSize(cancelBtn.getPreferredSize());
            okBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = true;
                    dispose();
                }
            });
            cancelBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = false;
                    dispose();
                }
            });
            okCancelPane.add(okBtn);
            okCancelPane.add(cancelBtn);
            getContentPane().add(okCancelPane, BorderLayout.SOUTH);
        }

        public void setLocalInstance(GKInstance instance) {
            // Set title
            setTitle("Matched Instances for \"" + instance.getDisplayName() + "\"");
        }

        public void setInstanceList(java.util.List list) {
            InstanceUtilities.sortInstances(list);
            listPane.setDisplayedInstances(list);
            listPane.setTitle("Matched instances in the database: " + list.size());
            if (list.size() > 0)
                listPane.setSelection((GKInstance)list.get(0));
        }

        public void select(GKInstance instance) {
            listPane.setSelection(instance);
        }

        public int getSelectedAction() {
            if (useDBCopy.isSelected())
                return USE_DB_COPY_ACTION;
            if (merge.isSelected())
                return MERGE_ACTION;
            return SAVE_AS_NEW_ACTION;
        }

        public GKInstance getSelectedInstance() {
            return (GKInstance) attPane.getInstance();
        }

        public void hideSaveAsNewButton() {
            saveAsNew.setVisible(false);
        }
    }

    class MultipleMatchDialog extends JDialog {
        private AttributeTable table;
        private boolean isOKClicked = false;

        public MultipleMatchDialog(JDialog parentDialog) {
            super(parentDialog);
            init();
        }

        public MultipleMatchDialog(JFrame parentFrame) {
            super(parentFrame);
            init();
        }

        public MultipleMatchDialog() {
            super();
            init();
        }

        public void setMatchedInstances(Map matchedMap) {
            MatchingTableModel model = (MatchingTableModel) table.getModel();
            model.setMatchedInstances(matchedMap);
        }

        protected void viewSelectedCell() {
            MatchingTableModel model = (MatchingTableModel)table.getModel();
            // Only need to handle a single cell selection.
            int rowCount = table.getSelectedRowCount();
            int colCount = table.getSelectedColumnCount();
            if (rowCount == 1 && colCount == 1) {
                int row = table.getSelectedRow();
                int col = table.getSelectedColumn();
                Object obj = model.getValueAt(row, col);
                if (obj instanceof GKInstance) {
                    GKInstance instance = (GKInstance)obj;
                    FrameManager.getManager().showInstance(instance, this);
                }
            }
        }   

        private void init() {
            // Add a textarea for label
            JTextArea ta = new JTextArea();
            ta.setText("Some of newly created local instances can be matched to instances in the database. " +
                    "To reduce duplication, it is recomended that you use matched instances in the database. " +
                    "To do so, select a matched db instance by clicking the checkbox right to it and choose " +
                    "action \"Use the Selected\" or \"Merge New to Selected\" in the Actions column. Otherwise, " +
                    "use action \"Save as New\" to save a new instance to the database.");
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setEditable(false);
            ta.setFont(ta.getFont().deriveFont(Font.BOLD));
            ta.setBackground(getContentPane().getBackground());
            ta.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            getContentPane().add(ta, BorderLayout.NORTH);
            table = new AttributeTable();
            MatchingTableModel model = new MatchingTableModel();
            table.setModel(model);
            table.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        viewSelectedCell();
                    }
                }
            });
            // Use a JComboBox for cell editing for column 3
            JComboBox jcb = new JComboBox(actions);
            DefaultCellEditor editor = new DefaultCellEditor(jcb);
            table.getColumnModel().getColumn(3).setCellEditor(editor);
            getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
            // Control pane
            JPanel controlPane = new JPanel();
            controlPane.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            JButton okBtn = new JButton("OK");
            okBtn.setMnemonic('O');
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setMnemonic('C');
            okBtn.setPreferredSize(cancelBtn.getPreferredSize());
            controlPane.add(okBtn);
            controlPane.add(cancelBtn);
            getContentPane().add(controlPane, BorderLayout.SOUTH);
            okBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = true;
                    dispose();
                }
            });
            cancelBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = false;
                    dispose();
                }
            });

            String title = new String("Resolving Matched Instances");
            setTitle(title);
        }

        public Map getSelectedActions() {
            MatchingTableModel model = (MatchingTableModel) table.getModel();
            return model.getSelectedActions();
        }

        public Map getSelectedMatchedInstances() {
            MatchingTableModel model = (MatchingTableModel) table.getModel();
            return model.getSelectedMatchedInstances();
        }
    }


    /**
     * To display a multispan table.
     */
    class MatchingTableModel extends AttributeTableModel {
        private String[] headers = {"New Local Instances", 
                "Matched DB Instances", 
                "Selected",                            
        "Actions"};
        private CellSpan cellSpan;
        private Object[][] values;
        private java.util.List sortedList;
        private JTable table;

        MatchingTableModel() {
            sortedList = new ArrayList();
            cellSpan = new CellSpan();
            values = new Object[1][1];
        }

        public void setTable(JTable table) {
            this.table = table;
        }

        public String getColumnName(int col) {
            return headers[col];
        }

        public CellSpan getCellSpan() {
            return cellSpan;
        }

        public int getRowCount() {
            return values.length;
        }

        public int getColumnCount() {
            return headers.length;
        }

        public boolean isCellEditable(int row, int col) {
            if (col == 2 || col == 3)
                return true;
            return false;
        }

        public void setMatchedInstances(Map matchedInstances) {
            // Find how many rows
            int rows = 0;
            sortedList.clear();
            for (Iterator it = matchedInstances.keySet().iterator(); it.hasNext();) {
                GKInstance instance = (GKInstance)it.next();
                Collection list = (Collection) matchedInstances.get(instance);
                if (list == null || list.size() == 0)
                    continue;
                sortedList.add(instance);
                rows += list.size();
            }
            values = new Object[rows][4];
            cellSpan = new CellSpan(rows, 4);
            InstanceUtilities.sortInstances(sortedList);
            // initialize the values
            int row = 0;
            boolean isFirst = true;
            for (Iterator it = sortedList.iterator(); it.hasNext();) {
                GKInstance instance = (GKInstance) it.next();
                values[row][0] = instance;
                values[row][3] = actions[1]; // Use DB Copy as default
                Collection list = (Collection) matchedInstances.get(instance);
                isFirst = true;
                for (Iterator i = list.iterator(); i.hasNext();) {
                    GKInstance matched = (GKInstance) i.next();
                    if (isFirst) {
                        values[row][2] = Boolean.TRUE;
                        isFirst = false;
                    }
                    else
                        values[row][2] = Boolean.FALSE;
                    values[row][1] = matched;
                    cellSpan.setSpan(1, row, 1);
                    cellSpan.setSpan(1, row, 2);
                    row ++;
                }
                cellSpan.setSpan(list.size(), row - list.size(), 0);
                cellSpan.setSpan(list.size(), row - list.size(), 3);
            }
        }

        public Object getValueAt(int row, int col) {
            if (cellSpan.isVisible(row, col))
                return values[row][col];
            return null;
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 3) {
                boolean hasSelected = false;
                Boolean isSelected = (Boolean) values[row][2];
                int selectedRow = -1;
                if (isSelected.booleanValue()) {
                    hasSelected = true;
                    selectedRow = row;
                }
                if (!hasSelected) {
                    for (int i = row + 1; i < values.length; i++) {
                        if (values[i][0] != null)
                            break;
                        isSelected = (Boolean) values[i][2];
                        if (isSelected.booleanValue()) {
                            hasSelected = true;
                            selectedRow = row;
                            break;
                        }
                    }
                }
                if (hasSelected) {
                    values[row][col] = value;
                    if (value.equals(actions[0])) {
                        // de-select
                        values[selectedRow][2] = Boolean.FALSE;
                        fireTableCellUpdated(selectedRow, 2);
                    }
                }
                else { // Only Save as New can be used.
                    if (value.equals(actions[0])) {
                        values[row][col] = value;
                    }
                    else {
                        JOptionPane.showMessageDialog(table,
                                                      "Because you have not selected any matched instance.\n" +
                                                              "You can only use action \"Save as New\"",
                                                              "Warning",
                                                              JOptionPane.WARNING_MESSAGE);
                        values[row][col] = actions[0];
                    }
                }
                return;
            }
            values[row][col] = value;
            // Have to take extra action when row is 1
            // Get the group row indices
            if (col == 2) {
                Boolean selected = (Boolean) value;
                if (selected.booleanValue()) {
                    // Go down
                    for (int i = row + 1; i < values.length; i++) {
                        if (values[i][0] != null)
                            break;
                        values[i][col] = Boolean.FALSE;
                    }
                    // Go up
                    for (int i = row; i >= 0; i--) {
                        if (i < row)
                            values[i][col] = Boolean.FALSE;
                        if (values[i][0] != null)
                            break;
                    }
                    // Use the default value
                    int anchorRow = getAnchorRow(row);
                    if (anchorRow > -1) {
                        String action = (String) getValueAt(anchorRow, 3);
                        if (action.equals(actions[0])) {
                            values[anchorRow][3] = actions[1];
                            fireTableCellUpdated(anchorRow, 3);
                        }
                    }
                }
                else {
                    int anchorRow = getAnchorRow(row);
                    if (anchorRow > -1) {
                        String action = (String) getValueAt(anchorRow, 3);
                        if (!action.equals(actions[0])) {
                            values[anchorRow][3] = actions[0];
                            fireTableCellUpdated(anchorRow, 3);
                        }
                    }
                }
            }
        }

        /**
         * An anchor row is a row with all four columns have values
         * @param row
         * @return
         */
        private int getAnchorRow(int row) {
            for (int i = row; i >= 0; i--) {
                if (values[i][0] != null)
                    return i;
            }
            return -1;
        }

        public Class getColumnClass(int col) {
            if (col == 2)
                return Boolean.class;
            return String.class;
        }

        public Map getSelectedActions() {
            Map map = new HashMap();
            for (int i = 0; i < values.length; i++) {
                if (values[i][0] != null) {
                    // Get the action
                    String action = values[i][3].toString();
                    map.put(values[i][0], new Integer(mapAction(action)));
                }
            }
            return map;
        }

        private int mapAction(String action) {
            for (int i = 0; i < actions.length; i++) {
                if (action.equals(actions[i]))
                    return i;
            }
            return SAVE_AS_NEW_ACTION;
        }

        public Map getSelectedMatchedInstances() {
            Map map = new HashMap();
            for (int i = 0; i < values.length; i++) {
                if (values[i][0] != null) {
                    // Get the selected values
                    for (int j = i; j < values.length; j++) {
                        if (j > i && values[j][0] != null) // Go to too far
                            break;
                        Boolean selected = (Boolean) values[j][2];
                        if (selected.booleanValue()) {
                            map.put(values[i][0], values[j][1]);
                            break;
                        }
                    }
                }
            }
            return map;
        }

        public SchemaClass getSchemaClass() {
            return null;
        }
    }

}
