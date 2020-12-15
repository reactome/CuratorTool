package org.gk.reach;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.JOptionPane;

import org.gk.reach.model.fries.FriesObject;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;
import org.jdom.JDOMException;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * This class is used to handle a servlet-based Reach NLP work. The servlet is provided by the
 * curator tool servlet, not the generic Reach servlet.
 * @author wug
 *
 */
public class ReachCuratorToolHandler {
    private ProgressPane progressPanel;
    private ReachProperties properties;

    public ReachCuratorToolHandler() {
        properties = new ReachProperties();
    }

    private ReachProperties getReachProperties() {
        return properties;
    }

    private void setReachProperties(ReachProperties properties) {
        this.properties = properties;
    }

    /**
     * The user interface entry point for submit a job to the WS servlet.
     * @param parent
     * @throws IOException
     * @throws IOException
     */
    public void submitPMCIDs(ReachResultTableFrame parent) throws IOException {
        properties = loadProperties();

        if (properties.anyNull())
            properties = configureReach(parent);

        // If the configuration did not set all of the needed REACH properties
        // (e.g. user canceled download), don't show PMCID input dialog.
        if (properties == null || properties.anyNull())
            return;

        setReachProperties(properties);

        PMCIDInputDialog dialog = new PMCIDInputDialog(parent);
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
        if (pmcids.size() > 3) {
            JOptionPane.showMessageDialog(parent,
                                          "Up to 3 PMCIDs are supported. Too many PMCIDs entered.",
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

    private ReachProperties loadProperties() throws IOException {
        File propFile = GKApplicationUtilities.getPropertyFile("curator.prop");
        Properties prop = new Properties();

        try (FileInputStream fis = new FileInputStream(propFile)) {
            prop.load(fis);
        }

        // If any REACH configuration is null, start configuration process.
        ReachProperties properties = new ReachProperties(prop);
        return properties;
    }

    private void _submitPMCIDs(ReachResultTableFrame parent, List<String> pmcids) {
        progressPanel = new ProgressPane();
        // Just want to enable the cancel action. Do nothing here.
        progressPanel.enableCancelAction(e -> {});
        progressPanel.setTitle("Processing Reach NLP");
        progressPanel.setIndeterminate(true);
        parent.setGlassPane(progressPanel);
        parent.getGlassPane().setVisible(true);
        try {
            List<FriesObject> objects = processReach(pmcids, getReachProperties());
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

    /**
     * Use this method to send a http get call to the Reactome curator tool servlet.
     * @param url
     * @return
     * @throws IOException
     */
    private List<FriesObject> processReach(List<String> pmcids, ReachProperties setup) throws IOException, JDOMException {
        Path reachConf = setup.getReachConf();
        Path reachJar = setup.getReachJar();
        Path reachRoot = setup.getReachRoot();

        if (progressPanel != null)
            progressPanel.setText("Waiting for results...");
        ReachProcessManager manager = new ReachProcessManager(reachRoot);
        manager.process(pmcids, reachJar, reachConf);
        if (progressPanel != null) {
            if (progressPanel.isCancelled())
                return null;
            progressPanel.setText("Reading the results...");
        }
        List<FriesObject> objects = createFriesObjects(reachRoot.resolve("output"));
        return objects;
    }

    private List<FriesObject> createFriesObjects(Path dir) throws IOException {
        Map<String, List<File>> idToFiles = new HashMap<>();
        File[] files = dir.toFile().listFiles();
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
    private FriesObject readJsonFiles(File referenceFile,
                                      File eventFile,
                                      File entityFile,
                                      File sentenceFile) throws IOException {
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

    private void outputFriesObjects(List<FriesObject> friesObjects) throws JsonProcessingException {
        System.out.println("Total FriesObjects: " + friesObjects.size());
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(Include.NON_NULL);
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        for (FriesObject obj : friesObjects) {
            String text = writer.writeValueAsString(obj);
            System.out.println(text);
        }
    }

    @Test
    public void testProcessReach() throws Exception {
        List<String> pmcids = Arrays.asList("PMC4750075");
        // Need the actual user name and key for a successful test.
        ReachProperties setup = new ReachProperties();
        List<FriesObject> objects = processReach(pmcids, setup);
        outputFriesObjects(objects);
    }

    ReachProperties configureReach(ReachResultTableFrame parent) throws IOException {
        ReachProperties properties = loadProperties();
        ReachConfigurationDialog setupDialog = new ReachConfigurationDialog(parent, properties);

        // Get REACH root directory.
        setupDialog.showDialog();
        setupDialog.setVisible(true);

        if (!setupDialog.isOKClicked)
            return null;

        Path reachRoot = setupDialog.getReachProperties().getReachRoot();
        Path reachJar = setupDialog.getReachProperties().getReachJar();

        // Get REACH configuration file.
        Path reachConf = createConfigFile(reachRoot);

        // Create REACH setup object.
        properties.setReachConf(reachConf);
        properties.setReachJar(reachJar);
        properties.setReachRoot(reachRoot);

        if (!properties.anyNull())
            saveProperties(properties);

        return properties;
    }

    private void saveProperties(ReachProperties setup) throws IOException {
        Properties prop = new Properties();
        prop.setProperty("reachConf", setup.getReachConf().toString());
        prop.setProperty("reachJar", setup.getReachJar().toString());
        prop.setProperty("reachRoot", setup.getReachRoot().toString());

        File propFile = GKApplicationUtilities.getPropertyFile("curator.prop");
        try (FileInputStream fis = new FileInputStream(propFile);
             FileOutputStream fos = new FileOutputStream(propFile, true)) {
            prop.store(fos, "REACH Properties");
        }
    }

    private Path createConfigFile(Path dir) throws IOException {
        String confTemplate = new String(Files.readAllBytes(Paths.get("resources/applicationTemplate.conf")));
        String confString = String.format(confTemplate, dir);

        Path confFile = dir.resolve("application.conf");
        try (BufferedWriter writer = Files.newBufferedWriter(confFile, StandardCharsets.UTF_8)) {
            writer.write(confString, 0, confString.length());
        }

        return confFile;
    }
}
