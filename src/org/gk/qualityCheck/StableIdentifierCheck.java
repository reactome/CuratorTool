/*
 * Created on Jun 28, 2016
 *
 */
package org.gk.qualityCheck;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.gk.database.DefaultInstanceEditHelper;
import org.gk.database.FrameManager;
import org.gk.database.InstanceListDialog;
import org.gk.database.InstanceListPane;
import org.gk.database.StableIdentifierGenerator;
import org.gk.database.SynchronizationManager;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.Schema;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * This class is used to check StableIdentifiers generated for newly created instances after last release.
 * The user is required to provide the date for last release.
 * @author gwu
 *
 */
public class StableIdentifierCheck extends AbstractQualityCheck {
    // Handling some displayed results
    private StableIdCheckResultFrame resultFrame;
    // QA for selected instances
    private Collection<GKInstance> selectedInstances;
    private boolean useSelected = false;
    
    /**
     * Default constructor.
     */
    public StableIdentifierCheck() {
    }
    
    @Override
    public String getDisplayName() {
        return "Instances_Without_StableIdentifier";
    }

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = super.checkInCommand();
        if (report == null)
            return null;
        if (escapeHelper.getCutoffDate() == null) {
            // The old cut-off date property file for compatibility.
            File file = new File("QA_SkipList/StableIdentifierCheck.txt");
            if (file.exists()) {
                Properties prop = new Properties();
                prop.load(new FileInputStream(file));
                String cutoffDate = prop.getProperty("cutoffDate");
                if (cutoffDate != null) {
                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                    escapeHelper.setCutoffDate(df.parse(cutoffDate));
                    escapeHelper.setNeedEscape(true);
                }
            }
        }
        // Perform StableIdentifier check
        StableIdentifierGenerator stidGenerator = new StableIdentifierGenerator();
        List<GKInstance> allInstances = grepAllInstances(stidGenerator);
        loadCreatedAttribute(allInstances);
        checkCreatedAttribute(allInstances);
        if (allInstances.size() == 0) {
            return report;
        }
        loadStableIdAttributes(allInstances, stidGenerator);
        Map<GKInstance, String> instToValidStid = checkStableIds(stidGenerator,
                                                                 allInstances);
        // Generate report
        report.setColumnHeaders("DB_ID", "DisplayName", "Class", "StableIdentifier", "Correct Identifier");
        List<GKInstance> sortedList = new ArrayList<>(instToValidStid.keySet());
        InstanceUtilities.sortInstances(sortedList);
        for (GKInstance inst : sortedList) {
            String validId = instToValidStid.get(inst);
            GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            report.addLine(inst.getDBID().toString(),
                           inst.getDisplayName(),
                           inst.getSchemClass().getName(),
                           stableId == null ? "Null" : stableId.getDisplayName(),
                           validId);
        }
        return report;
    }

    @Override
    public void check(GKSchemaClass cls) {
        validateDataSource();
        StableIdentifierGenerator helper = new StableIdentifierGenerator();
        if (!helper.needStid(cls, dataSource)) {
            JOptionPane.showMessageDialog(parentComp, 
                                          cls.getName() + " doesn't need stableIdentifier.",
                                          "No StableIdentifier",
                                          JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Collection<GKInstance> c = dataSource.fetchInstancesByClass(cls);
            check(new ArrayList<GKInstance>(c));
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(parentComp, 
                                          "Error in check: " + e,
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
    }

    @Override
    public void check(GKInstance instance) {
        if (instance == null)
            return;
        check(Collections.singletonList(instance));
    }

    @Override
    public void check(List<GKInstance> instances) {
        if (instances == null || instances.size() == 0)
            return;
        StableIdentifierGenerator helper = new StableIdentifierGenerator();
        selectedInstances = new ArrayList<GKInstance>(instances);
        for (Iterator<GKInstance> it = selectedInstances.iterator(); it.hasNext();) {
            GKInstance inst = it.next();
            if (!helper.needStid(inst))
                it.remove();
        }
        if (selectedInstances.size() == 0) {
            JOptionPane.showMessageDialog(parentComp, 
                                          "No StableIdentifier is needed selected instances.",
                                          "No StableIdentifier",
                                          JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        useSelected = true;
        check();
    }

    @Override
    public void checkProject(GKInstance event) {
        try {
            Set<GKInstance> instances = loadInstancesInProject(event);
            check(new ArrayList<GKInstance>(instances));
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(parentComp, 
                                          "Error in checkProject: " + e.getMessage(),
                                          "Error in Check Project",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
    }

    @Override
    protected InstanceListPane getDisplayedList() {
        return null;
    }
    
    @Override
    protected List<GKInstance> getDisplayedInstances() {
        if (resultFrame == null)
            return null;
        return resultFrame.getDisplayedInstances();
    }

    private boolean setUpCutoffDate() {
     // Need a cutoff date
        String message = "Enter the date of the previous release in the format \"YYYY-MM-DD\"\n" +
                         "to check newly created StableIdentifiers:";
        Date cutoffDate = escapeHelper.inputCutoffDate(message, 
                                                       this.parentComp);
        if (cutoffDate == null)
            return false; // Cancelled
        escapeHelper.setCutoffDate(cutoffDate);
        return true;
    }
    
    private List<GKInstance> grepAllInstances(StableIdentifierGenerator stidGenerator) throws Exception {
        Set<String> stidClassNames = stidGenerator.getClassNamesWithStableIds(dataSource);
        Schema schema = dataSource.getSchema();
        List<GKInstance> allInstances = new ArrayList<GKInstance>();
        for (String clsName : stidClassNames) {
            SchemaClass cls = schema.getClassByName(clsName);
            if (progressPane != null)
                progressPane.setText("Loading instances in " + clsName);
            Collection<GKInstance> instances = dataSource.fetchInstancesByClass(cls);
            allInstances.addAll(instances);
            if (progressPane != null && progressPane.isCancelled())
                break;
        }
        return allInstances;
    }

    public void check() {
        validateDataSource();
        if (!setUpCutoffDate())
            return;
        // Use a new thread so that the progress can be monitored
        Thread t = new Thread() {
            public void run() {
                try {
                    StableIdentifierGenerator stidGenerator = new StableIdentifierGenerator();
                    Set<String> stidClassNames = stidGenerator.getClassNamesWithStableIds(dataSource);
                    // Get the Reaction class
                    initProgressPane("Check StableIdentifiers");
                    List<GKInstance> allInstances = null;
                    if (useSelected)
                        allInstances = new ArrayList<GKInstance>(selectedInstances);
                    else
                        allInstances = grepAllInstances(stidGenerator);
                    if (progressPane.isCancelled()) {
                        hideProgressPane();
                        return;
                    }
                    loadCreatedAttribute(allInstances);
                    checkCreatedAttribute(allInstances);
                    if (progressPane.isCancelled()) {
                        hideProgressPane();
                        return;
                    }
                    if (allInstances.size() == 0) {
                        JOptionPane.showMessageDialog(parentComp,
                                                      "There are no newly created instances whose stableIdentifers should be checked.",
                                                      "No New StableIdentifiers",
                                                      JOptionPane.INFORMATION_MESSAGE);
                        hideProgressPane();
                        return;
                    }
                    loadStableIdAttributes(allInstances, stidGenerator);
                    if (progressPane.isCancelled()) {
                        hideProgressPane();
                        return;
                    }
                    Map<GKInstance, String> instToValidStid = checkStableIds(stidGenerator,
                                                                             allInstances);
                    if (progressPane.isCancelled()) {
                        hideProgressPane();
                        return;
                    }
                    hideProgressPane();
                    displayResults(instToValidStid);
                }
                catch(Exception e) {
                    hideProgressPane();
                    System.err.println("StableIdentifierCheck.check(): " + e);
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }
    
    private void displayResults(Map<GKInstance, String> instToValidStid) throws Exception {
        if (instToValidStid == null || instToValidStid.size() == 0) {
            JOptionPane.showMessageDialog(parentComp,
                                          "Cannot find any error in newly created StableIdentifiers.",
                                          "No Error in New StableIdentifiers",
                                          JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        resultFrame = new StableIdCheckResultFrame();
        resultFrame.showResults(instToValidStid);
    }

    private void loadCreatedAttribute(List<GKInstance> allInstances) throws Exception {
        // Load created slot
        if (dataSource instanceof Neo4JAdaptor) {
            Neo4JAdaptor dba = (Neo4JAdaptor) dataSource;
            if (progressPane != null)
                progressPane.setText("Loading the created attribute...");
            // Try to use attributes. It seems that there is a problem using String: the value
            // is not loaded actually, or loaded but not registered.
            SchemaAttribute created = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.created);
            dba.loadInstanceAttributeValues(allInstances, created);
            // Need dateTime for created
            Set<GKInstance> ies = new HashSet<GKInstance>();
            for (GKInstance inst : allInstances) {
                // Have to use created string, not the above created attribute
                GKInstance ie = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
                if (ie != null)
                    ies.add(ie);
            }
            // In this case, using String is fine most likely this attribute is in the class definition.
            dba.loadInstanceAttributeValues(ies, new String[]{ReactomeJavaConstants.dateTime});
        }
    }
    
    private void loadStableIdAttributes(List<GKInstance> instances,
                                        StableIdentifierGenerator stIdGenerator) throws Exception {
        if (!(dataSource instanceof Neo4JAdaptor))
            return;
        Neo4JAdaptor dba = (Neo4JAdaptor) dataSource;
        if (progressPane != null)
            progressPane.setText("Loading related attributes...");
        // Load Stable ids
        // Using SchemaAttribute. Attribute name cannot work
        SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.stableIdentifier);
        dba.loadInstanceAttributeValues(instances, att);
        // We also want to have identifiers for stable ids
        List<GKInstance> list = new ArrayList<GKInstance>();
        for (GKInstance inst : instances) {
            GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (stableId == null)
                continue;
            list.add(stableId);
        }
        dba.loadInstanceAttributeValues(list, new String[]{ReactomeJavaConstants.identifier});
        // Load modified slot for future fix
        att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.modified);
        dba.loadInstanceAttributeValues(list, att);
        list.clear();
        
        loadStableIdAttribute(instances, 
                              dba, 
                              list, 
                              ReactomeJavaConstants.Event, 
                              ReactomeJavaConstants.species);
        // Species for PEs except OtherEntity
        list.clear();
        for (GKInstance inst : instances) {
            if (inst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity) &&
                !inst.getSchemClass().isa(ReactomeJavaConstants.OtherEntity))
                list.add(inst);
        }
        dba.loadInstanceAttributeValues(list, new String[]{ReactomeJavaConstants.species});
        // hasMember and hasComponent
        loadStableIdAttribute(instances, 
                              dba, 
                              list,
                              ReactomeJavaConstants.EntitySet,
                              ReactomeJavaConstants.hasMember);
        loadStableIdAttribute(instances, 
                              dba, 
                              list,
                              ReactomeJavaConstants.Complex,
                              ReactomeJavaConstants.hasComponent);
        if (stIdGenerator.getClassNamesWithStableIds(dataSource).contains(ReactomeJavaConstants.Regulation)) {
            loadStableIdAttribute(instances, 
                                  dba, 
                                  list,
                                  ReactomeJavaConstants.Regulation,
                                  ReactomeJavaConstants.regulatedEntity);
        }
        loadStableIdAttribute(instances, dba, list, ReactomeJavaConstants.Complex, ReactomeJavaConstants.isChimeric);
        loadStableIdAttribute(instances, dba, list, ReactomeJavaConstants.ReactionlikeEvent, ReactomeJavaConstants.isChimeric);
    }

    private void loadStableIdAttribute(List<GKInstance> instances,
                                       Neo4JAdaptor dba, 
                                       List<GKInstance> list,
                                       String clsName,
                                       String attName) throws Exception {
        list.clear();
        for (GKInstance inst : instances) {
            if (inst.getSchemClass().isa(clsName))
                list.add(inst);
        }
        dba.loadInstanceAttributeValues(list, new String[]{attName});
    }

    private void checkCreatedAttribute(List<GKInstance> allInstances) throws Exception {
        if (progressPane != null)
            progressPane.setText("Checking the created attribute...");
        Map<GKInstance, String> instToValidId = new HashMap<GKInstance, String>();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
        for (Iterator<GKInstance> it = allInstances.iterator(); it.hasNext();) {
            GKInstance inst = it.next();
            GKInstance ie = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
            // Some old instances may not have values for created
//            if (ie == null) {
//                it.remove(); // Ignore it
//                continue;
//            }
            // As of June 8, 2017, if there is no value, check it anyway
            if (ie == null)
                continue;
            String dateTime = (String) ie.getAttributeValue(ReactomeJavaConstants.dateTime);
            if (dateTime == null) {
                it.remove(); // ignore it
                continue;
            }
            // Have to make sure default date instance is used!
            Date ieDate = df.parse(dateTime);
            if (ieDate.before(escapeHelper.getCutoffDate())) // If this instance was created before the previous release, no need to check its stableIdentifier.
                it.remove(); // Old instance. Ignore it
            if (progressPane != null && progressPane.isCancelled())
                break;
        }
    }

    private Map<GKInstance, String> checkStableIds(StableIdentifierGenerator stidGenerator,
                                                   List<GKInstance> allInstances) throws Exception {
        if (progressPane != null) {
            progressPane.setText("Checking StableIdentifiers...");
            progressPane.setIndeterminate(false);
            progressPane.setMaximum(allInstances.size());
            progressPane.setValue(0);
        }
        int count = 0;
        Map<GKInstance, String> instToValidStid = new HashMap<GKInstance, String>();
        for (GKInstance inst : allInstances) {
            if (progressPane != null)
                progressPane.setValue(++count);
            String newIdentifier = stidGenerator.generateIdentifier(inst);
            GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (stableId == null) {
                // Special case
                instToValidStid.put(inst, newIdentifier);
                continue; 
            }
            String oldIdentifier = (String) stableId.getAttributeValue(ReactomeJavaConstants.identifier);
            if (newIdentifier.equals(oldIdentifier))
                continue;
            instToValidStid.put(inst, newIdentifier);
            if (progressPane != null && progressPane.isCancelled())
                break;
        }
        return instToValidStid;
    }
    
    /**
     * Borrow this Action to fix wrong StableIdentifiers directly in the database.
     * @param frame
     * @return
     */
    @Override
    protected Action createCheckOutAction(JFrame frame) {
        Action fixInDbAction = new AbstractAction("Fix in DB") {
            public void actionPerformed(ActionEvent e) {
                fixStableIdsInDb();
            }
        };
        return fixInDbAction;
    }
    
    private void fixStableIdsInDb() {
        if (resultFrame == null)
            return;
        if (!(dataSource instanceof Neo4JAdaptor)) {
            return; // Since this check works for the database only, this should not happen.
        }
        Neo4JAdaptor dba = (Neo4JAdaptor) dataSource;
        Map<GKInstance, String> instToValidId = resultFrame.getSelectedInstanceToValidId();
        if (instToValidId.size() > 0) {
            int confirm = JOptionPane.showConfirmDialog(resultFrame,
                                                        "Only selected StableIdentifiers will be fixed: \n" + 
                                                         instToValidId.size() + " selected.",
                                                        "Fixing Warning",
                                                        JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION)
                return;
        }
        else
            instToValidId = resultFrame.getInstanceToValidId();
        if (instToValidId.size() == 0) // Just in case
            return;
        try {
            GKInstance defaultIE = getDefaultIE();
            if (defaultIE == null)
                return; // Cannot do anything if no InstanceEdit is available
            // We want to load all modified slot in one shot for quick performance
            List<GKInstance> newStableIds = new ArrayList<GKInstance>(); // For new StableIdentifiers
            List<GKInstance> updatedInsts = new ArrayList<GKInstance>(); // Instances with newly attached StableIds
            List<GKInstance> updatedStableIds = new ArrayList<GKInstance>(); // Updated StableIds
            StableIdentifierGenerator generator = new StableIdentifierGenerator();
            for (GKInstance inst : instToValidId.keySet()) {
                String validId = instToValidId.get(inst);
                GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                if (stableId == null) {
                    // Need to create a new StableIdentifier instance
                    stableId = generator.generateStableId(inst,
                                                          defaultIE,
                                                          null);
                    newStableIds.add(stableId);
                    inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stableId);
                    inst.getAttributeValue(ReactomeJavaConstants.modified);
                    inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    updatedInsts.add(inst);
                }
                else {
                    stableId.setAttributeValue(ReactomeJavaConstants.identifier, validId);
                    // Load values first. Just in case. They should have been loaded already
                    stableId.getAttributeValuesList(ReactomeJavaConstants.modified);
                    stableId.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    InstanceDisplayNameGenerator.setDisplayName(stableId);
                    updatedStableIds.add(stableId);
                }
            }
            boolean needTransaction = false;
            Driver driver = dba.getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                // Don't forget to commit this defaultIE
                dba.storeInstance(defaultIE, tx);
                for (GKInstance newStableId : newStableIds) {
                    dba.storeInstance(newStableId, tx);
                }
                for (GKInstance updatedStableId : updatedStableIds) {
                    dba.updateInstanceAttribute(updatedStableId, ReactomeJavaConstants.identifier, tx);
                    dba.updateInstanceAttribute(updatedStableId, ReactomeJavaConstants.modified, tx);
                    dba.updateInstanceAttribute(updatedStableId, ReactomeJavaConstants._displayName, tx);
                }
                for (GKInstance updatedInst : updatedInsts) {
                    dba.updateInstanceAttribute(updatedInst, ReactomeJavaConstants.stableIdentifier, tx);
                    dba.updateInstanceAttribute(updatedInst, ReactomeJavaConstants.modified, tx);
                }
                tx.commit();
                cleanUpResultFrame(instToValidId.keySet());
                displayFixedStableIds(instToValidId.keySet());
            }
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(parentComp, 
                                          "Error in fixing StableIdentifiers: " + e.getMessage(), 
                                          "Error in Fixing",
                                          JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void displayFixedStableIds(Set<GKInstance> instances) {
        // Convert to StableIdentifiers for easy display
        try {
            List<GKInstance> stableIds = new ArrayList<GKInstance>();
            for (GKInstance inst : instances) {
                GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                if (stableId != null)
                    stableIds.add(stableId);
            }
            if (stableIds.size() == 0)
                return;
            
            if (stableIds.size() == 1) {
                JOptionPane.showMessageDialog(resultFrame,
                                              "This StableIdentifier has been fixed: " + stableIds.iterator().next(),
                                              "Fixing Results",
                                              JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            InstanceListDialog listDialog = new InstanceListDialog(resultFrame, 
                    "Fix Results");
            InstanceUtilities.sortInstances(stableIds);
            listDialog.setDisplayedInstances(stableIds);
            listDialog.setSubTitle("The total fixed instances: " + instances.size() + ". Run this QA again to confirm.");
            listDialog.setSize(600, 400);
            GKApplicationUtilities.center(listDialog);
            listDialog.setModal(true);
            listDialog.setVisible(true);
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(resultFrame,
                                          "Error in showing fixed results: " + e,
                                          "Error in Result Display",
                                          JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void cleanUpResultFrame(Set<GKInstance> instances) {
        if (resultFrame == null || !resultFrame.isVisible())
            return;
        resultFrame.cleanUp(instances);
    }
    
    private GKInstance getDefaultIE() throws Exception {
        DefaultInstanceEditHelper defaultIEHelper = SynchronizationManager.getManager().getDefaultIEHelper();
        // The returned Person instance is a local instance. We have to get a database copy
        GKInstance person = defaultIEHelper.fetchPerson(resultFrame);
        if (person == null)
            return null; // Cancelled
        if (person.getDBID() < 0) {
            JOptionPane.showMessageDialog(resultFrame,
                                          "You have to commit your local Person instance into the database first before fixing.",
                                          "Error in Fixing",
                                          JOptionPane.ERROR_MESSAGE);
            return null;
        }
        GKInstance dbPerson = dataSource.fetchInstance(person.getDBID());
        if (dbPerson == null) {
            JOptionPane.showMessageDialog(resultFrame,
                                          "The chosen Person instance is not in the database.",
                                          "Error in Fixing",
                                          JOptionPane.ERROR_MESSAGE);
            return null;
        }
        // Get a default InstanceEdit.  This will be put into the "created"
        // or "modified" slots of any instances committed, so that creation
        // and changes to instances can be tracked.
        GKInstance defaultInstanceEdit = defaultIEHelper.createDefaultInstanceEdit(dbPerson);
        defaultInstanceEdit.addAttributeValue(ReactomeJavaConstants.dateTime,  
                                              GKApplicationUtilities.getDateTime());
        InstanceDisplayNameGenerator.setDisplayName(defaultInstanceEdit);
        return defaultInstanceEdit;
    }

    private class StableIdCheckResultFrame extends JFrame {
        private JTable resultTable;
        private JLabel titleLabel;
        
        public StableIdCheckResultFrame() {
            init();
        }
        
        private void init() {
            setTitle("New StableIdentifiers QA Results");
            
            titleLabel = new JLabel("New StableIdentifiers QA Results");
            getContentPane().add(titleLabel, BorderLayout.NORTH);
            
            resultTable = new JTable();
            StableIdCheckResultTableModel model = new StableIdCheckResultTableModel();
            resultTable.setModel(model);
            resultTable.setAutoCreateRowSorter(true);
            TableCellRenderer renderer = new DefaultTableCellRenderer() {
                Icon instanceIcon = GKApplicationUtilities.createImageIcon(getClass(), "Instance.gif");
                
                @Override
                public Component getTableCellRendererComponent(JTable table,
                                                               Object value,
                                                               boolean isSelected,
                                                               boolean hasFocus,
                                                               int row,
                                                               int col) {
                    Component comp = super.getTableCellRendererComponent(table,
                                                                         value,
                                                                         isSelected,
                                                                         hasFocus,
                                                                         row,
                                                                         col);
                    if (isSelected || hasFocus) {
                        comp.setBackground(table.getSelectionBackground());
                        comp.setForeground(table.getSelectionForeground());
                    }
                    else {
                        comp.setBackground(table.getBackground());
                        comp.setForeground(table.getForeground());
                    }
                    if (value instanceof GKInstance) {
                        GKInstance inst = (GKInstance) value;
                        setIcon(instanceIcon); // Set icon first so that it is before the text
                        setText(inst.getDisplayName());
                        setHorizontalTextPosition(SwingConstants.RIGHT);
                    }
                    return comp;
                }
            };
            resultTable.setDefaultRenderer(GKInstance.class,
                                           renderer);
            // Enable double click to show instances
            resultTable.addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2)
                        showInstance(e);
                }
                
            });
            getContentPane().add(new JScrollPane(resultTable), 
                                 BorderLayout.CENTER);
            
            // Need a control panel
            CheckOutControlPane controlPane = new CheckOutControlPane(this);
            controlPane.getCheckOutBtn().setText("Fix in DB");
            controlPane.getCheckOutBtn().setEnabled(true);
            getContentPane().add(controlPane, BorderLayout.SOUTH);
            
            setSize(650, 550);
            GKApplicationUtilities.center(this);
        }
        
        private void showInstance(MouseEvent event) {
            Point p = event.getPoint();
            int row = resultTable.rowAtPoint(p);
            int col = resultTable.columnAtPoint(p);
            Object value = resultTable.getValueAt(row, col);
            if (!(value instanceof GKInstance))
                return;
            FrameManager.getManager().showInstance((GKInstance)value);
        }
        
        public void showResults(Map<GKInstance, String> instToValidId) {
            titleLabel.setText("Total wrong StableIdentifiers: " + instToValidId.size());
            StableIdCheckResultTableModel model = (StableIdCheckResultTableModel) resultTable.getModel();
            model.setInstanceToValidId(instToValidId);
            setVisible(true);
        }
        
        public List<GKInstance> getDisplayedInstances() {
            StableIdCheckResultTableModel model = (StableIdCheckResultTableModel) resultTable.getModel();
            return model.getInstances();
        }
        
        public Map<GKInstance, String> getSelectedInstanceToValidId() {
            Map<GKInstance, String> instToValidId = new HashMap<GKInstance, String>();
            int[] selectedRows = resultTable.getSelectedRows();
            StableIdCheckResultTableModel model = (StableIdCheckResultTableModel) resultTable.getModel();
            if (selectedRows != null && selectedRows.length > 0) {
                for (int row : selectedRows) {
                    int modelRow = resultTable.convertRowIndexToModel(row);
                    instToValidId.put((GKInstance)model.getValueAt(modelRow, 1),
                                      (String)model.getValueAt(modelRow, 4));
                }
            }
            return instToValidId;
        }
        
        public Map<GKInstance, String> getInstanceToValidId() {
            Map<GKInstance, String> instToValidId = new HashMap<GKInstance, String>();
            StableIdCheckResultTableModel model = (StableIdCheckResultTableModel) resultTable.getModel();
            for (int i = 0; i < model.getRowCount(); i++) {
                instToValidId.put((GKInstance)model.getValueAt(i, 1),
                                      (String)model.getValueAt(i, 4));
            }
            return instToValidId;
        }
        
        /**
         * Remove the passed instances from the table after they are fixed.
         * @param instances
         */
        public void cleanUp(Set<GKInstance> instances) {
            StableIdCheckResultTableModel model = (StableIdCheckResultTableModel) resultTable.getModel();
            model.removeInstances(instances);
        }
        
    }
    
    private class StableIdCheckResultTableModel extends AbstractTableModel {
        private final String[] headers = new String[]{"DB_ID", "DisplayName", "Class", "StableIdentifier", "Correct Identifier"};
        private Map<GKInstance, String> instToValidId;
        private List<GKInstance> sortedInstList;
        
        public StableIdCheckResultTableModel() {
            sortedInstList = new ArrayList<GKInstance>();
            instToValidId = new HashMap<GKInstance, String>();
        }
        
        public List<GKInstance> getInstances() {
            return sortedInstList;
        }
        
        public void setInstanceToValidId(Map<GKInstance, String> instToValidId) {
            this.instToValidId.clear();
            this.instToValidId.putAll(instToValidId);
            sortedInstList = new ArrayList<GKInstance>(instToValidId.keySet());
            InstanceUtilities.sortInstances(sortedInstList);
        }
        
        public void removeInstances(Set<GKInstance> instances) {
            try {
                for (Iterator<GKInstance> it = sortedInstList.iterator(); it.hasNext();) {
                    GKInstance inst = it.next();
                    if (instances.contains(inst)) {
                        it.remove();
                        instToValidId.remove(inst);
                    }
                }
                fireTableDataChanged();
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getRowCount() {
            return sortedInstList.size();
        }

        @Override
        public int getColumnCount() {
            return headers.length;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0)
                return Long.class;
            else if (columnIndex == 1 || columnIndex == 3)
                return GKInstance.class;
            else
                return String.class;
        }

        @Override
        public String getColumnName(int column) {
            return headers[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            GKInstance inst = sortedInstList.get(rowIndex);
            switch (columnIndex) {
                case 0 : return inst.getDBID();
                case 1 : return inst;
                case 2 : return inst.getSchemClass().getName();
                case 3 : 
                    try {
                        GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                        return stableId;
                    }
                    catch(Exception e) {
                        return null;
                    }
                case 4 : return instToValidId.get(inst);    
            }
            return null;
        }
        
    }
    
}
