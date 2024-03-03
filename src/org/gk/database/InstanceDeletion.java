/*
 * Created on May 24, 2010
 *
 */
package org.gk.database;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.schema.SchemaClass;
import org.gk.util.ProgressPane;

/**
 * This class is used to do instance deletion.
 * @author wgm
 *
 */
public class InstanceDeletion {
    
    public InstanceDeletion() {
    }
    
    /**
     * Delete a list of GKInstance.
     * @param list List of instances to delete
     * @param parentFrame Parent GUI frame in which to show messages 
     */
    public void delete(List<GKInstance> list,
                       JFrame parentFrame) {
        delete(list, parentFrame, true);
    }
    
    public void delete(List<GKInstance> list,
                       JFrame parentFrame,
                       boolean needWarning) {
        if (list == null || list.size() == 0)
            return;
        if (needWarning) {
            String message = "Are you sure you want to delete the selected instance" + (list.size() == 1 ? "" : "s") + "? The slot values in\n"
                           + "other instances referring the deleted instances will be set to null.";
            if (needReferrers(list)) {
                message += "\nStructure related referrers will be checked and downloaded from the"
                        +  "\ndatabase if needed. Their review status will be demoted automatically."
                        +  "\nThis may take some time.";
            }
            int reply = JOptionPane.showConfirmDialog(parentFrame,
                                                      message,
                                                      "Delete Confirmation",
                                                      JOptionPane.YES_NO_OPTION);
            if (reply != JOptionPane.YES_OPTION)
                return;
        }
        _delete(list, parentFrame, needWarning);
    }

    /**
     * A threaded deletion to show the progress.
     * @param list
     * @param parentFrame
     * @param needWarning
     */
    private void _delete(List<GKInstance> list, JFrame parentFrame, boolean needWarning) {
        Thread t = new Thread() {
            public void run() {
                // Need the database connection
                MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor(parentFrame);
                if (dba == null) {
                    if (dba == null) {
                        JOptionPane.showMessageDialog(
                            parentFrame,
                            "Cannot connect to the database. The deletion is aborted.",
                            "Error in DB Connecting",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                ProgressPane progressPane = null;
                boolean needReferrers = needReferrers(list);
                try {
                    if (needReferrers) {
                        // Start with a progress pane
                        progressPane = new ProgressPane();
                        progressPane.setSize(progressPane.getWidth() + 75, progressPane.getHeight());
                        parentFrame.setGlassPane(progressPane);
                        progressPane.setIndeterminate(true);
                        progressPane.setText("Check structure related referrers...");
                        parentFrame.getGlassPane().setVisible(true);
                        Set<GKInstance> referrers = new HashSet<>();
                        for (GKInstance inst : list) {
                            GKInstance dbInst = dba.fetchInstance(inst.getDBID());
                            if (dbInst == null)
                                continue;
                            for (String attName : InstanceUtilities.getStructureRelatedAttributes()) {
                                @SuppressWarnings("unchecked")
                                Collection<GKInstance> refs = dbInst.getReferers(attName);
                                if (refs != null && refs.size() > 0)
                                    referrers.addAll(refs);
                            }
                        }
                        filterReferrers(referrers);
                        if (referrers.size() > 0) {
                            progressPane.setText(String.format("Check out %d structure related referrer" + (referrers.size() == 1 ? "":"s") + "...",
                                    referrers.size()));
                            SynchronizationManager.getManager().checkOut(new ArrayList<>(referrers), parentFrame);
                            progressPane.setText("Checking-out Done");
                        }
                    }
                    XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
                    // Create a _Deleted instance
                    // These instances are used to keep track of
                    // deletions.
                    if (progressPane != null)
                        progressPane.setText("Deleting...");
                    boolean isCreateDeleteInstanceOK = createDeleteInstance(fileAdaptor, 
                            list,
                            parentFrame, 
                            needWarning);
                    // Do the actual deletions
                    if (isCreateDeleteInstanceOK) {
                        for (GKInstance instance : list) {
                            fileAdaptor.deleteInstance(instance);
                        }
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(parentFrame,
                            "Error in deletion: " + e.getMessage(),
                            "Error in Deletion",
                            JOptionPane.ERROR_MESSAGE);
                }
                if (progressPane != null) {
                    // Don't set glasspane to null. Otherwise, a null exception will be thrown.
                    parentFrame.getGlassPane().setVisible(false);
                }
            }
        };
        t.start();
    }
    
    private boolean needReferrers(List<GKInstance> list) {
        boolean needReferrers = !list.stream()
                .filter(inst -> InstanceUtilities.isStructureRelatedInstance(inst)).findAny().isEmpty();
        return needReferrers;
    }
    
    private void filterReferrers(Set<GKInstance> referrers) {
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        for (Iterator<GKInstance> it = referrers.iterator(); it.hasNext();) {
            GKInstance dbInst = it.next();
            GKInstance local = fileAdaptor.fetchInstance(dbInst.getDBID());
            if (local == null || local.isShell()) // Download only shell instances or instances not here yet
                continue;
            it.remove();
        }
    }
    
    
    public void delete(GKInstance instance,
                       JFrame parentFrame) {
        List<GKInstance> list = Collections.singletonList(instance);
        delete(list, parentFrame);
    }
    
    /**
     * Given a list of instances that should be deleted, create a new
     * instance of class _Deleted and add it to the fileAdaptor.
     * This is used to track deletions.
     * 
     * N.B. if the user deletes a "_Deleted" instance, no new "_Deleted"
     * instance will be created.  Also, if the instance being deleted
     * was created locally (i.e. is not in the database), then no
     * "_Deleted" instance will be created.
     * 
     * Returns true if everything was OK, false if an error was
     * detected or if the user cancels.
     * 
     * @param dbAdaptor
     * @param instance
     */
    private boolean createDeleteInstance(XMLFileAdaptor fileAdaptor,
                                         List<GKInstance> instances,
                                         JFrame parentFrame,
                                         boolean needWarning) throws Exception {
        if (!isDeletedNeeded(instances))
            return true; // Correct handling
        // Make sure DeletedControlledVocabulary existing
        if(!downloadControlledVocabulary(parentFrame))
            return false;
        if (!checkShellInstances(instances, parentFrame))
            return false;
        // Create an instance of class "_Deleted".  It's done
        // in this rather complicated way, rather than using
        // fileAdaptor.createNewInstance, because this latter
        // method inserts two copies of the instance into
        // fileAdaptor's cache.
        SchemaClass schemaClass = fileAdaptor.getSchema().getClassByName(ReactomeJavaConstants._Deleted);
        Long dbID = fileAdaptor.getNextLocalID();
        GKInstance deleted = new GKInstance(schemaClass, dbID, fileAdaptor);
        deleted.setDBID(dbID);
        
        // Add the DB_IDs of all instances to be deleted
        //TODO A bug in the data model: using integer, instead of long for _Deleted's deltedInstanceDB_ID
        // This should be fixed!
        List<Integer> deletedIds = new ArrayList<Integer>();
        boolean required = false;
        for (Iterator<?> it = instances.iterator(); it.hasNext();) {
            GKInstance instance = (GKInstance)it.next();
            if (instance.getSchemClass().isa(ReactomeJavaConstants._Deleted))
                continue;
            if (instance.getDBID() < 0)
                continue;
            deletedIds.add(instance.getDBID().intValue());
            if (instance.getSchemClass().isa(ReactomeJavaConstants.Event) ||
                instance.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
                required = true;
        }
        // For sure deletedIds should not be null. However, just in case, do this extra check
        if (deletedIds.size() == 0)
            return true;
        Collections.sort(deletedIds);
        deleted.setAttributeValue(ReactomeJavaConstants.deletedInstanceDB_ID,
                deletedIds);
        InstanceDisplayNameGenerator.setDisplayName(deleted);
        if (needWarning) {
            // Let the curator add comment, etc.
            DeletedInstanceDialog deletedInstanceDialog = new DeletedInstanceDialog(parentFrame, deleted);
            deletedInstanceDialog.setModal(true);
            deletedInstanceDialog.setSize(475, 600);
            deletedInstanceDialog.setLocationRelativeTo(parentFrame);
            // As of April, 2023, deleting a PE or an Event must create a _Deleted instance
            // regardless whether it is released or not.
            if (required)
                deletedInstanceDialog.hideSkipButton();
            deletedInstanceDialog.setVisible(true);
            if (!deletedInstanceDialog.isOKClicked)
                return false; // Cancel has been clicked. Abort the whole deletion
            if (deletedInstanceDialog.isDeletedInstanceNeeded) {
                fileAdaptor.addNewInstance(deleted);
                createDeletedInstances(deleted);
            }
        }
        else {
            fileAdaptor.addNewInstance(deleted);
            createDeletedInstances(deleted);
        }
        return true;
    }
    
    private boolean checkShellInstances(List<GKInstance> instances,
                                        Component parentComp) throws Exception {
        boolean hasShell = instances.stream().anyMatch(inst -> inst.isShell());
        if (!hasShell)
            return true;
        // Have to make sure we have database connection
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor(parentComp);
        if (dba == null) {
            JOptionPane.showMessageDialog(parentComp,
                    "One or more shell instances are being deleted. Need a DB connection for doing so.",
                    "No DB Connection",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }
    
    private void createDeletedInstances(GKInstance deleted) throws Exception {
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor(); // This should be active
        // Return integers for the time being
        List<Integer> dbIds = deleted.getAttributeValuesList(ReactomeJavaConstants.deletedInstanceDB_ID);
        List<GKInstance> deletedInstances = new ArrayList<>();
        for (Integer dbId : dbIds) {
            GKInstance deletedInst = fileAdaptor.fetchInstance((long)dbId);
            if (deletedInst.isShell()) {
                GKInstance dbCopy = dba.fetchInstance((long)dbId);
                if (dbCopy == null) {
                    // This instance has been deleted. Don't do anything here
                    continue;
                }
                SynchronizationManager.getManager().updateFromDB(deletedInst, dbCopy);
            }
            GKInstance _deletedInstance = fileAdaptor.createNewInstance(ReactomeJavaConstants._DeletedInstance);
            _deletedInstance.setAttributeValue(ReactomeJavaConstants.deletedInstanceDB_ID, dbId);
            GKInstance stableId = (GKInstance) deletedInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            _deletedInstance.setAttributeValue(ReactomeJavaConstants.deletedStableIdentifier, stableId);
            if (deletedInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.species)) {
                GKInstance species = (GKInstance) deletedInst.getAttributeValue(ReactomeJavaConstants.species);
                _deletedInstance.setAttributeValue(ReactomeJavaConstants.species, species);
            }
            if (deletedInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.name)) {
                String name = (String) deletedInst.getAttributeValue(ReactomeJavaConstants.name);
                _deletedInstance.setAttributeValue(ReactomeJavaConstants.name, name);
            }
            _deletedInstance.setAttributeValue("class", deletedInst.getSchemClass().getName());
            InstanceDisplayNameGenerator.setDisplayName(_deletedInstance);
            deletedInstances.add(_deletedInstance);
        }
        if (deletedInstances.size() > 0)
            deleted.setAttributeValue(ReactomeJavaConstants.deletedInstance, deletedInstances);
    }
    
    /**
     * Check if we need a _Deleted instance. If the passed list contains local instances
     * and _Deleted instances only, no need to create a _Deleted instance.
     * @param instances
     * @return
     */
    private boolean isDeletedNeeded(List<?> instances) {
        for (Iterator<?> it = instances.iterator(); it.hasNext();) {
            GKInstance instance = (GKInstance)it.next();
            
            if (instance.getSchemClass().isa(ReactomeJavaConstants._Deleted))
                continue;
            if (instance.getDBID() < 0)
                continue;
            
            return true;
        }
        return false;
    }
    
    private boolean downloadControlledVocabulary(JFrame parentFrame) {
        return SynchronizationManager.getManager().downloadControlledVocabulary(parentFrame,
                                                                                ReactomeJavaConstants.DeletedControlledVocabulary,
                                                                                false);
    }
    
    /**
     * This customized JDialog is used to create a new _Deleted object.
     * @author croft
     */
    private class DeletedInstanceDialog extends JDialog {
        private AttributePane attributePane;
        private boolean isOKClicked;
        private boolean isDeletedInstanceNeeded = false;
        private JButton skipBtn;
        
        public DeletedInstanceDialog(JFrame parentFrame, GKInstance instance) {
            super(parentFrame, "Deletion information");
            attributePane = new AttributePane(instance);
            init();
        }
        
        private void init() {
            // Add instance pane
            attributePane.refresh();
            attributePane.setEditable(true);
            attributePane.setBorder(BorderFactory.createRaisedBevelBorder());
            attributePane.setTitle("Enter information pertaining to deleted instance:");
            getContentPane().add(attributePane, BorderLayout.CENTER);
            // Add buttons
            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.Y_AXIS));
            JButton okBtn = new JButton("Delete with _Deleted Instance");
            okBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = true;
                    attributePane.stopEditing();
                    isDeletedInstanceNeeded = true;
                    dispose();
                }
            });
            okBtn.setMnemonic('O');
            okBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            skipBtn = new JButton("Delete without _Deleted Instance");
            skipBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = true;
                    attributePane.stopEditing();
                    isDeletedInstanceNeeded = false;
                    dispose();
                }
            });
            skipBtn.setMnemonic('S');
            skipBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isOKClicked = false; // Just abort the whole deletion process
                    dispose();
                }
            });
            cancelBtn.setMnemonic('C');
            cancelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            okBtn.setDefaultCapable(true);
            okBtn.setMinimumSize(skipBtn.getPreferredSize());
            cancelBtn.setMinimumSize(skipBtn.getPreferredSize());
            buttonPane.add(Box.createRigidArea(new Dimension(5, 5)));
            buttonPane.add(okBtn);
            buttonPane.add(Box.createRigidArea(new Dimension(5, 5)));
            buttonPane.add(skipBtn);
            buttonPane.add(Box.createRigidArea(new Dimension(5, 5)));
            buttonPane.add(cancelBtn);
            buttonPane.add(Box.createRigidArea(new Dimension(5, 5)));
            getContentPane().add(buttonPane, BorderLayout.NORTH);
        }
                
        public void hideSkipButton() {
            this.skipBtn.setVisible(false);
        }
    }
}
