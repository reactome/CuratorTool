package org.gk.reach;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.gk.reach.model.fries.FriesObject;
import org.gk.util.ProgressPane;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public abstract class ReachProcessHandler {

    protected Integer maxPmcidNumber = 3;
    protected ProgressPane progressPanel;
    
    protected ReachProcessHandler() {
    }

    protected abstract boolean ensureRequirements(JFrame container);
    
    /**
     * The user interface entry point for submit a job to the WS servlet.
     * @param parent
     * @throws IOException
     * @throws IOException
     */
    public void submitPMCIDs(ReachResultTableFrame parent) {
        if (!ensureRequirements(parent))
            return;
        PMCIDInputDialog dialog = new PMCIDInputDialog(parent, maxPmcidNumber);
        dialog.setVisible(true);
        if (!dialog.isOKClicked())
            return;
        List<String> pmcids = Arrays.asList(dialog.getPMCIDs());
        if (pmcids == null || pmcids.size() == 0) {
            JOptionPane.showMessageDialog(parent,
                                          "Cannot find any PMCID entered.",
                                          "No PMCID",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (pmcids.size() > maxPmcidNumber) {
            JOptionPane.showMessageDialog(parent,
                                          String.format("Up to %d PMCIDs are supported. Too many PMCIDs entered.", maxPmcidNumber),
                                          "Too PMCIDs",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Need to have a new thread and a progress pane for handling this long process
        Thread t = new Thread() {
            public void run() {
                _submitPMCIDs(parent, pmcids);
            }
        };
        t.start();
    }
    
    protected abstract List<FriesObject> processReach(List<String> pmcids) throws Exception;
    
    private void _submitPMCIDs(ReachResultTableFrame parent, List<String> pmcids) {
        progressPanel = new ProgressPane();
        // Just want to enable the cancel action. Do nothing here.
        progressPanel.enableCancelAction(e -> {});
        progressPanel.setTitle("Processing Reach NLP");
        progressPanel.setIndeterminate(true);
        parent.setGlassPane(progressPanel);
        parent.getGlassPane().setVisible(true);
        try {
            List<FriesObject> objects = processReach(pmcids);
            if (objects == null) {
                parent.getGlassPane().setVisible(false);
                return; // Most likely it is canceled
            }
            if (objects.size() == 0) {
                JOptionPane.showMessageDialog(parent,
                                              "Error in Reach NLP: Cannot get any result.",
                                              "Error in Reach NLP",
                                              JOptionPane.ERROR_MESSAGE);
                parent.getGlassPane().setVisible(false);
            }
            parent.setReachData(objects);
            parent.getGlassPane().setVisible(false);
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(parent,
                                          "Error in Reach NLP: " + e.getMessage(),
                                          "Error in Reach NLP",
                                          JOptionPane.ERROR_MESSAGE);
            parent.getGlassPane().setVisible(false);
        }
    }

    protected List<FriesObject> readJsonFiles(File dir) throws IOException {
        Map<String, List<File>> idToFiles = new HashMap<>();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0)
            return new ArrayList<>();
        for (File file : files) {
            if (file.getName().equals("restart.log"))
                continue;
            String name = file.getName();
            String[] tokens = name.split("\\.");
            idToFiles.compute(tokens[0], (key, list) -> {
                if (list == null)
                    list = new ArrayList<>();
                list.add(file);
                return list;
            });
        }
        return readJsonFiles(idToFiles);
    }

    protected List<FriesObject> readJsonFiles(Map<String, List<File>> idToFiles) throws IOException {
        List<FriesObject> friesObjects = new ArrayList<>();
        for (String id : idToFiles.keySet()) {
            List<File> idFiles = idToFiles.get(id);
            File eventFile = null;
            File referenceFile = null;
            File sentenceFile = null;
            File entityFile = null;
            for (File file : idFiles) {
                String name = file.getName();
                if (name.endsWith("reference.json"))
                    referenceFile = file;
                else if (name.endsWith("events.json"))
                    eventFile = file;
                else if (name.endsWith("entities.json"))
                    entityFile = file;
                else if (name.endsWith("sentences.json"))
                    sentenceFile = file;
            }
            if (eventFile == null || referenceFile == null ||
                sentenceFile == null || entityFile == null) {
                throw new IllegalStateException("Not enough json files (4 required) for: " + id);
            }
            FriesObject friesObject = readJsonFiles(referenceFile, eventFile, entityFile, sentenceFile);
            friesObjects.add(friesObject);
        }
        return friesObjects;
    }

    /**
     * Read all four json files into a single string to create a FriesObject. This method is based
     * on Liam's implementation in the reactome-reach-integration project.
     * @param referenceFile
     * @param eventFile
     * @param entityFile
     * @param sentenceFile
     * @return
     * @throws IOException
     */
    private FriesObject readJsonFiles(File referenceFile, File eventFile, File entityFile, File sentenceFile) throws IOException {
        String referenceText = new String(Files.readAllBytes(Paths.get(referenceFile.getAbsolutePath())),
                                          StandardCharsets.UTF_8);
        String eventText = new String(Files.readAllBytes(Paths.get(eventFile.getAbsolutePath())),
                                      StandardCharsets.UTF_8);
        String entityText = new String(Files.readAllBytes(Paths.get(entityFile.getAbsolutePath())),
                                      StandardCharsets.UTF_8);
        String sentenceText = new String(Files.readAllBytes(Paths.get(sentenceFile.getAbsolutePath())),
                                      StandardCharsets.UTF_8);
        String merged = "{ \"events\": " + eventText + ",\n" +
                        "\"entities\": " + entityText + ",\n" +
                        "\"sentences\": " + sentenceText + ",\n" +
                        "\"reference\": " + referenceText + "}";
        return ReachUtils.readJsonText(merged);
    }

    protected void outputFriesObjects(List<FriesObject> friesObjects) throws JsonProcessingException {
        System.out.println("Total FriesObjects: " + friesObjects.size());
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(Include.NON_NULL);
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        for (FriesObject obj : friesObjects) {
            String text = writer.writeValueAsString(obj);
            System.out.println(text);
        }
    }

}
