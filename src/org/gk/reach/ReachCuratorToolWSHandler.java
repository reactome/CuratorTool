package org.gk.reach;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.gk.reach.model.fries.FriesObject;
import org.gk.util.DialogControlPane;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;
import org.gk.util.WSInfoHelper;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
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
public class ReachCuratorToolWSHandler {
    private ProgressPane progressPanel;
    
    public ReachCuratorToolWSHandler() {
    }
    
    /**
     * The user interface entry point for submit a job to the WS servlet.
     * @param parent
     */
    public void submitPMCIDs(ReachResultTableFrame parent) {
        String[] wsInfo = getWSUserInfo(parent);
        if (wsInfo == null)
            return; // Do nothing
        PMCIDInputDialog dialog = new PMCIDInputDialog(parent);
        dialog.setVisible(true);
        if (!dialog.isOKClicked)
            return;
        String[] pmcids = dialog.getPMCIDs();
        if (pmcids == null || pmcids.length == 0) {
            JOptionPane.showMessageDialog(parent,
                                          "Cannot find any PMCID entered.",
                                          "No PMCID",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (pmcids.length > 3) {
            JOptionPane.showMessageDialog(parent,
                                          "Up to 3 PMCIDs are supported. Too many PMCIDs entered.",
                                          "Too PMCIDs",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Need to have a new thread and a progress pane for handling this long process
        Thread t = new Thread() {
            public void run() {
                _submitPMCIDs(parent, pmcids, wsInfo);
            }
        };
        t.start();
    }
    
    private String[] getWSUserInfo(JFrame parentFrame) {
        try {
            String[] wsInfo = new WSInfoHelper().getWSInfo(parentFrame);
            if (wsInfo == null) {
                JOptionPane.showMessageDialog(parentFrame,
                                              "No connecting information to the server-side program is provided!",
                                              "Error in Connection",
                                              JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return wsInfo;
        }
        catch(UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private void _submitPMCIDs(ReachResultTableFrame parent, String[] pmcids, String[] wsInfo) {
        progressPanel = new ProgressPane();
        // Just want to enable the cancel action. Do nothing here.
        progressPanel.enableCancelAction(e -> {});
        progressPanel.setTitle("Processing Reach NLP");
        progressPanel.setIndeterminate(true);
        parent.setGlassPane(progressPanel);
        parent.getGlassPane().setVisible(true);
        try {
            List<FriesObject> objects = processReachViaWS(pmcids, wsInfo);
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
                return;
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
    private List<FriesObject> processReachViaWS(String[] pmcids, String[] wsInfo) throws IOException, JDOMException {
        String reachURL = getServletURL();
        if (reachURL == null)
            throw new IllegalStateException("Cannot find the URL for REACH in the configuration.");
        String urlText = reachURL + "?user=" + wsInfo[0] + 
                                    "&key=" + wsInfo[1] + 
                                    "&action=reach" +
                                    "&pmcid=" + String.join(",", pmcids);
        URL url = new URL(urlText);
        // Make sure we have a good return code
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
            JOptionPane.showMessageDialog(progressPanel,
                                          "Somebody is using the REACH NLP at the server. Please wait\n"
                                          + "for several minutes and then try again.",
                                          "Busy Server",
                                          JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        else if (connection.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
            JOptionPane.showMessageDialog(progressPanel,
                                          connection.getHeaderField("error"),
                                          "Error in Request",
                                          JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        if (progressPanel != null)
            progressPanel.setText("Waiting for results...");
        InputStream is = connection.getInputStream(); // Have to use this input stream. Otherwise, nothing to output
        File dir = unzipDownloadFile(is);
        is.close();
        if (progressPanel != null) {
            if (progressPanel.isCancelled())
                return null;
            progressPanel.setText("Reading the results...");
        }
        List<FriesObject> objects = readUnzippedFiles(dir);
        return objects;
    }
    
    private String getServletURL() throws IOException, JDOMException {
        // We want to search reachURL in two places
        String reachURL = null;
        // The user editable curator.prop in the user's .reactome folder
        InputStream is = GKApplicationUtilities.getConfig("curator.prop");
        if (is != null) {
            Properties prop = new Properties();
            prop.load(is);
            reachURL = prop.getProperty("reachURL");
            is.close();
        }
        // The normal curator.xml configuration file
        if (reachURL == null) {
            InputStream metaConfig = GKApplicationUtilities.getConfig("curator.xml");
            if (metaConfig != null) {
                SAXBuilder builder = new SAXBuilder();
                Document doc = builder.build(metaConfig);
                Element elm = (Element) XPath.selectSingleNode(doc.getRootElement(), 
                                                               "reachURL");
                if (elm != null)
                    reachURL = elm.getText();
            }
        }
        return reachURL;
    }
    
    private List<FriesObject> readUnzippedFiles(File dir) throws IOException {
        Map<String, List<File>> idToFiles = new HashMap<>();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0)
            return new ArrayList<>();
        for (File file : files) {
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
    
    /**
     * Unzip the download file from an InputStream, which should be from a URL,
     * into a temporary folder.
     * @param is
     * @throws IOException
     */
    private File unzipDownloadFile(InputStream is) throws IOException {
        File reachTempFile = GKApplicationUtilities.getTempFile("reach");
        if (reachTempFile.exists()) {
            // Clean up this folder
            File[] files = reachTempFile.listFiles();
            if (files != null && files.length > 0)
                for (File file : files)
                    file.delete();
        }
        else {
            if(!reachTempFile.mkdirs())
                throw new IllegalStateException("Cannot create a temp folder for reach: " + reachTempFile.getAbsolutePath());
        }
        unzipDownloadFile(is, reachTempFile);
        return reachTempFile;
    }

    private void unzipDownloadFile(InputStream is, File reachTempFile) throws IOException {
        // Unzip the download file
        BufferedInputStream bis = new BufferedInputStream(is);
        ZipInputStream zis = new ZipInputStream(bis);
        ZipEntry entry = null;
        byte[] buffer = new byte[1024]; // 1K buffer
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(reachTempFile, entry.getName());
            FileOutputStream fos = new FileOutputStream(outFile);
            int length = 0;
            while ((length = zis.read(buffer, 0, buffer.length)) > 0)
                fos.write(buffer, 0, length);
            fos.close();
        }
        zis.closeEntry();
        zis.close();
        is.close();
    }
    
    @Test
    public void testUnzip() throws IOException {
        File zipFile = new File("/Users/wug/Documents/reach/output/reach.json.zip");
        File tempFolder = new File("temp");
        File[] files = tempFolder.listFiles();
        if (files != null)
            for (File file : files)
                file.delete();
        unzipDownloadFile(new FileInputStream(zipFile), tempFolder);
        files = tempFolder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No file is generated!");
        }
        else {
            for (File file : files)
                System.out.println(file.getName());
        }
    }
    
    @Test
    public void testReadUnzippedFiles() throws IOException {
        File dir = new File("temp");
        List<FriesObject> friesObjects = readUnzippedFiles(dir);
        outputFriesObjects(friesObjects);
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
    public void testProcessReachViaWS() throws Exception {
        String[] pmcids = {"PMC6683984", "PMC4750075"};
        // Need the actual user name and key for a successful test.
        List<FriesObject> objects = processReachViaWS(pmcids, new String[]{});
        outputFriesObjects(objects);
    }
    
    private class PMCIDInputDialog extends JDialog {
        private boolean isOKClicked = false;
        private JTextField pmcidTf;
        
        public PMCIDInputDialog(JFrame parent) {
            super(parent);
            init();
        }
        
        public String[] getPMCIDs() {
            String text = pmcidTf.getText().trim();
            String[] tokens = text.split(",");
            List<String> ids = new ArrayList<>();
            for (String token : tokens) {
                token = token.trim();
                if (token.startsWith("PMC"))
                    ids.add(token);
            }
            return ids.toArray(new String[] {});
        }
        
        private void init() {
            setTitle("Enter PMCID");
            
            JPanel contentPane = new JPanel();
            contentPane.setBorder(BorderFactory.createEtchedBorder());
            contentPane.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            JLabel label = GKApplicationUtilities.createTitleLabel("Enter or paste PMCID (CMD+V or CTL+V) below:");
            contentPane.add(label, constraints);
            pmcidTf = new JTextField();
            pmcidTf.addActionListener(e -> {
                isOKClicked = true;
                dispose();
            });
            pmcidTf.setColumns(30);
            constraints.gridy = 1;
            contentPane.add(pmcidTf, constraints);
            JTextArea ta = new JTextArea("*: Up to three PMCIDs are supported. Add \", \" as "
                    + "delimit. The process may take several minutes. You may work in other "
                    + "place during waiting. The results will be displayed once it is done.");
            Font font = ta.getFont();
            ta.setFont(font.deriveFont(Font.ITALIC));
            ta.setBackground(contentPane.getBackground());
            ta.setEditable(false);
            ta.setWrapStyleWord(true);
            ta.setLineWrap(true);
            constraints.gridy = 2;
            contentPane.add(ta, constraints);
            getContentPane().add(contentPane, BorderLayout.CENTER);
            
            DialogControlPane controlPane = new DialogControlPane();
            controlPane.setBorder(BorderFactory.createEtchedBorder());
            controlPane.getOKBtn().setText("Submit");
            controlPane.getOKBtn().addActionListener(e -> {
                isOKClicked = true;
                dispose();
            });
            controlPane.getCancelBtn().addActionListener(e -> dispose());
            getContentPane().add(controlPane, BorderLayout.SOUTH);
            
            setSize(400, 300);
            setLocationRelativeTo(getOwner());
            setModal(true);
        }
    }

}
