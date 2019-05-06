/*
 * Created on Nov 17, 2010
 *
 */
package org.gk.qualityCheck;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.util.FileUtilities;
import org.gk.util.GKApplicationUtilities;


/**
 * This class is used to escape a set of instances that should not be under QA checking.
 * @author wgm
 *
 */
public class QAEscapeHelper {
    // Check if a dialog should be displayed to ask if escape is needed
    private boolean needEscapePermission;
    // A flag to check if escape is needed
    private boolean needEscape;
    // A list of DB_IDs of instances that should be escaped QA checking
    private Set<Long> escapedDbIds;
    
    /**
     * The date after which instances should be checked even it is in the
     * escaped list. Note that, unlike the release-qa QA checks, the
     * Curator QA check instance is responsible for setting the cut-off
     * date. 
     */
    private Date cutoffDate;
    
    public QAEscapeHelper() {
    }
    
    public boolean isNeedEscapePermission() {
        return needEscapePermission;
    }

    public void setNeedEscapePermission(boolean needEscapePermission) {
        this.needEscapePermission = needEscapePermission;
        // Have to force needEscape false if the above value is false
        if (!needEscapePermission)
            needEscape = false;
    }

    public boolean isNeedEscape() {
        return needEscape;
    }

    public void setNeedEscape(boolean needEscape) {
        this.needEscape = needEscape;
    }

    public Set<Long> getEscapedDbIds() {
        return escapedDbIds;
    }

    public void setEscapedDbIds(Set<Long> escapedDbIds) {
        this.escapedDbIds = escapedDbIds;
    }

    public Date getCutoffDate() {
        return cutoffDate;
    }

    public void setCutoffDate(Date cutoffDate) {
        this.cutoffDate = cutoffDate;
    }

    /**
     * Check if a list of instances should be escaped QA check.
     * @param parentComponent
     * @return false for the operation is canceled.
     */
    public boolean checkIfEscapeNeeded(Component parentComponent) {
        if (!needEscapePermission) {
            needEscape = false;
            return true; // No need to check. QA should be run always for a local project.
        }
        needEscape = false; // Default should be false
        int reply = JOptionPane.showConfirmDialog(parentComponent,
                                                  "Do you want to provide a file containing a list of instances that should be\n" + 
                                                  "escaped QA check? This file may have been generated from a previous QA.", 
                                                  "Escape QA?", 
                                                  JOptionPane.YES_NO_CANCEL_OPTION);
        if (reply == JOptionPane.CANCEL_OPTION) {
            return false;
        }
        if (reply == JOptionPane.NO_OPTION)
            return true;
        // Need to get a file
        Properties prop = GKApplicationUtilities.getApplicationProperties();
        JFileChooser fileChooser = GKApplicationUtilities.createFileChooser(prop);
        reply = fileChooser.showOpenDialog(parentComponent);
        if (reply != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        GKApplicationUtilities.storeCurrentDir(fileChooser, prop);
        File file = fileChooser.getSelectedFile();
        try {
            openEscapeList(file);
        }
        catch(IOException e) {
            System.err.println("QAEscapeHelper.checkifEscapeNeeded(): " + e);
            e.printStackTrace();
            JOptionPane.showMessageDialog(parentComponent, 
                                          "Cannot parse DB_IDs from the provided file: " + e,
                                          "Error in Opening",
                                          JOptionPane.ERROR_MESSAGE);
            return false;
        }
        // Ask to provide a date time
        String message = "Please enter a date cutoff here in the format \"YYYY-MM-DD\". All instances\n" +
                         "that have been edited after this cutoff date will be checked even if they\n" + 
                         "are listed in the escaped file.";
        cutoffDate = inputCutoffDate(message, parentComponent);
        if (cutoffDate == null)
            return false;
        needEscape = true;
        return needEscape;
    }
    
    /**
     * Set up a cutoff date for QA check. 
     * @param message
     * @param parentComponent
     * @return
     */
    public Date inputCutoffDate(String message,
                                Component parentComponent) {
        // Ask to provide a date time
        String value = JOptionPane.showInputDialog(parentComponent,
                                                   message,
                                                   "Cutoff Date?",
                                                   JOptionPane.QUESTION_MESSAGE);
        if (value == null || value.trim().length() == 0)
            return null;
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date cutoffDate = df.parse(value);
            return cutoffDate;
        }
        catch (ParseException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parentComponent, 
                                          "Cannot read the entered date. Please make sure your format is correct: YYYY-MM-DD",
                                          "Error in Date",
                                          JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    
    /**
     * A method to open a dump file to get a set of DB_IDs for escaped instances.
     * @param file
     * @throws IOException
     */
    public void openEscapeList(File file) throws IOException {
        FileUtilities fu = new FileUtilities();
        fu.setInput(file.getAbsolutePath());
        String line = null;
        escapedDbIds = new HashSet<Long>();
        while ((line = fu.readLine()) != null) {
            if (line.startsWith("#"))
                continue; // Escape comment line
            String[] tokens = line.split("\t");
            // Make sure only number will be got
            if (tokens[0].matches("\\d+"))
                escapedDbIds.add(new Long(tokens[0]));
        }
        fu.close();
    }
    
    public boolean shouldEscape(GKInstance inst) {
        if (!needEscape)
            return false;
        if (needEscape && escapedDbIds.contains(inst.getDBID())) {
            if (cutoffDate != null) {
                try {
                    // Check if the latest IEs is later than the cutoff date
                    GKInstance latestIE = QACheckUtilities.getLatestCuratorIEFromInstance(inst);
                    // Just in case
                    if (latestIE == null)
                        return true;
                    String ieDateValue = (String) latestIE.getAttributeValue(ReactomeJavaConstants.dateTime);
                    // Have to make sure default date instance is used!
                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
                    Date ieDate = df.parse(ieDateValue);
                    return !ieDate.after(cutoffDate);
                }
                catch(Exception e) {
                    // Don't want to show a GUI here. Just generate error message silently.
                    System.err.println("QAEscapeHelper.shouldEscape(): " + e);
                    e.printStackTrace();
                    return false;
                }
            }
            else
                return true;
        }
        return false;
    }
    
}
