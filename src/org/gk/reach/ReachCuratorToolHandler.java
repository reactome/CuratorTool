package org.gk.reach;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.gk.reach.model.fries.FriesObject;
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;

/**
 * This class is used to handle a servlet-based Reach NLP work. The servlet is provided by the
 * curator tool servlet, not the generic Reach servlet.
 * @author wug
 *
 */
public class ReachCuratorToolHandler extends ReachProcessHandler {
    private Properties setup;
    
    public ReachCuratorToolHandler() {
        maxPmcidNumber = Integer.MAX_VALUE; // No limit for this
    }
    
    @Override
    protected boolean ensureRequirements(JFrame container) {
        LocalReachConfigurator configurator = new LocalReachConfigurator(container,
                                                                         GKApplicationUtilities.getApplicationProperties());
        try {
            String reachJarURL = ReachUtils.getConfigReachURL("reachJarURL");
            configurator.setReachJarURL(reachJarURL);
            setup = configurator.getReachConfig();
            return setup != null;
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(container,
                                          "Cannot set up a local Reach process: " + e.getMessage(),
                                          "Error in Setting Local Reach", 
                                          JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    @Override
    protected List<FriesObject> processReach(List<String> pmcids) throws Exception {
        String reachJar = setup.getProperty(LocalReachConfigurator.JAR_PROP_KEY);
        Path jarPath = Paths.get(reachJar);
        String reachRoot = setup.getProperty(LocalReachConfigurator.ROOT_PROP_KEY);
        Path rootPath = Paths.get(reachRoot);
        Path reachConf = createConfigFile(rootPath);
        if (progressPanel != null)
            progressPanel.setText("Waiting for results...");
        ReachLocalProcessManager manager = new ReachLocalProcessManager();
        manager.setRootPath(rootPath);
        manager.process(pmcids, jarPath, reachConf);
        if (progressPanel != null) {
            if (progressPanel.isCancelled())
                return null;
            progressPanel.setText("Reading the results...");
        }
        List<FriesObject> objects = readJsonFiles(new File(reachRoot, "output"));
        return objects;
    }
    
    /**
     * Reach needs a configuration file with the actual root. This method generates this file
     * by replacing the %s in the template. 
     * @param dir
     * @return
     * @throws IOException
     */
    private Path createConfigFile(Path dir) throws IOException {
        String confTemplate = new String(Files.readAllBytes(Paths.get("resources/reachApplicationTemplate.conf")));
        String confString = String.format(confTemplate, dir);

        Path confFile = dir.resolve("application.conf");
        try (BufferedWriter writer = Files.newBufferedWriter(confFile, StandardCharsets.UTF_8)) {
            writer.write(confString, 0, confString.length());
        }

        return confFile;
    }

    @Test
    public void testProcessReach() throws Exception {
        List<String> pmcids = Arrays.asList("PMC4750075");
        // Need the actual user name and key for a successful test.
        List<FriesObject> objects = processReach(pmcids);
        outputFriesObjects(objects);
    }
}
