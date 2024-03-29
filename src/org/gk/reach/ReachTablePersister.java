package org.gk.reach;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;

import org.gk.model.Reference;
import org.gk.reach.model.fries.FriesObject;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class is used to persist table data using an external file. The data in the table may be filtered.
 * This class is for filtered data only.
 * @author wug
 *
 */
public class ReachTablePersister {
    // Used to control open process
    private boolean isOpenCanceled;
    
    public ReachTablePersister() {
    }
    
    public void loadPreResults(ReachResultTableFrame frame) {
    	JFileChooser fileChooser = GKApplicationUtilities.createFileChooser(GKApplicationUtilities.getApplicationProperties());
    	fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    	fileChooser.setDialogTitle("Select a directory having REACH results (one PMCID should have four json files)");
    	int reply = fileChooser.showOpenDialog(frame);
    	if (reply != JFileChooser.APPROVE_OPTION)
    		return;
    	try {
    		List<FriesObject> objects = new ReachCuratorToolHandler().readJsonFiles(fileChooser.getSelectedFile().getAbsoluteFile());
    		frame.setReachData(objects);
    	}
    	catch(Exception e) {
            JOptionPane.showMessageDialog(frame,
                    "Error in loading preprocessed files: " + e.getMessage(),
                    "Error in Opening",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
    	}
    }
    
    public void openPreProcessedFiles(ReachResultTableFrame frame) {
        FileNameExtensionFilter filter = new FileNameExtensionFilter("FRIES Files", "json");
        File[] files = getFiles(true,
                                "Choose one or more FRIES files...",
                                true, 
                                frame, 
                                filter);
        if (files == null || files.length == 0)
            return;
        Thread t = new Thread(() -> openPreProcessedFiles(files, frame));
        t.start();
    }
    
    private File[] getFiles(boolean isForOpen,
                            String title,
                            boolean multiSelectionEnabled,
                            Component parent,
                            FileNameExtensionFilter filter) {
        JFileChooser fileChooser = new JFileChooser();
        Properties systemProperties = GKApplicationUtilities.getApplicationProperties();
        if (systemProperties != null) {
            String currentDir = systemProperties.getProperty("currentDir");
            if (currentDir != null)
                fileChooser.setCurrentDirectory(new File(currentDir));
        }
        
        fileChooser.setDialogTitle(title);
        fileChooser.setMultiSelectionEnabled(multiSelectionEnabled);
        if (filter != null)
            fileChooser.addChoosableFileFilter(filter);
        int reply = JFileChooser.CANCEL_OPTION;
        if (isForOpen)
            reply = fileChooser.showOpenDialog(parent);
        else
            reply = fileChooser.showSaveDialog(parent);
        if (reply != JFileChooser.APPROVE_OPTION)
            return null;
        File[] files = null;
        if (multiSelectionEnabled)
            files = fileChooser.getSelectedFiles();
        else {
            File file = fileChooser.getSelectedFile();
            files = new File[] {file};
        }
        if (files != null && files.length > 0 && systemProperties != null)
            systemProperties.put("currentDir", files[0].getParentFile().getAbsolutePath());
        return files;
    }

    private void openPreProcessedFiles(File[] files, ReachResultTableFrame frame) {
        ProgressPane progressPane = new ProgressPane();
        frame.setGlassPane(progressPane);
        progressPane.setMaximum(files.length);
        progressPane.setTitle("Processing Fries Files");
        progressPane.enableCancelAction(e -> isOpenCanceled = true);
        frame.getGlassPane().setVisible(true);
        // Create Fries object from Fries file
        ReferenceFetch referenceFetch = new ReferenceFetch();
        try {
            List<FriesObject> friesObjects = new ArrayList<FriesObject>();
            int count = 0;
            for (File file : files) {
                if (isOpenCanceled)
                    break;
                progressPane.setText("Processing " + file.getName() + "...");
                progressPane.setValue(++count);
                String jsonOutput = new String(Files.readAllBytes(file.toPath()));
                FriesObject friesObject = ReachUtils.readJsonText(jsonOutput);
                if (friesObject.getReference() == null) {
                    String paperId = file.getName().substring(0, file.getName().indexOf("."));
                    progressPane.setText("Fetching reference...");
                    Reference reference = referenceFetch.fetchReference(paperId);
                    friesObject.setReference(reference);
                }
                friesObjects.add(friesObject);
            }
            if (!isOpenCanceled)
                // Add data to the table.
                frame.setReachData(friesObjects);
            frame.getGlassPane().setVisible(false);
        }
        catch(IOException e) {
            JOptionPane.showMessageDialog(frame,
                                          "Error in loading Fries files: " + e.getMessage(),
                                          "Error in Opening",
                                          JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            frame.getGlassPane().setVisible(false);
        }
    }
    
    //TODO: For the time being, the data is serized into a Java object. Somehow I cannot
    // make ObjectMapper JSON work by trying different ways. It will be better to do
    // that in the future.
    public void exportTableData(JTable table) {
        TableModel model = table.getModel();
        if (!(model instanceof ReachTableModel))
            return; // Only for ReachTable.
        List<ReachResultTableRowData> data = ((ReachTableModel)model).getTableData();
        data = ReachUtils.getDisplayedData(table, (ReachTableModel)model);
        if (data == null || data.size() == 0)
            return;
        // Get output file
        // rrtj: Reactome Reach Table file in json
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Reactome Reach Table", "rrtf");
        File[] files = getFiles(false,
                                "Choose a file to save the table data",
                                false,
                                table,
                                filter);
        if (files == null || files.length == 0)
            return;
        File file = files[0];
        // Add an extension
        String fileName = file.getName();
        if (!fileName.contains("."))
            file = new File(file.getAbsolutePath() + ".rrtf");
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Avoid export non-null values
            mapper.setSerializationInclusion(Include.NON_NULL);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        }
        catch(IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(table,
                                          "Error in exporting table data: " + e.getMessage(),
                                          "Error in Export",
                                          JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public void importTableData(ReachResultTableFrame frame) {
        JTable table = frame.getEventTable();
        TableModel model = table.getModel();
        if (!(model instanceof ReachTableModel))
            return; // Only for ReachTable.
        // rrtj: Reactome Reach Table JSON
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Reactome Reach Table", "rrtj.json");
        File[] files = getFiles(true,
                                "Choose a saved reach table data file",
                                false,
                                table,
                                filter);
        if (files == null || files.length == 0)
            return;
        File file = files[0];
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ReachResultTableRowData[] arr = mapper.readValue(file, ReachResultTableRowData[].class);
            List<ReachResultTableRowData> data = Arrays.asList(arr);   
            frame.setTableData(data);
        }
        catch(Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(table,
                                          "Error in reading table data: " + e.getMessage(),
                                          "Error in Read",
                                          JOptionPane.ERROR_MESSAGE);
        }
    }

}
