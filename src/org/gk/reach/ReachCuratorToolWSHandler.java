package org.gk.reach;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.gk.reach.model.fries.FriesObject;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;
import org.gk.util.WSInfoHelper;
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
public class ReachCuratorToolWSHandler extends ReachProcessHandler {
    private ProgressPane progressPanel;
    private String[] wsInfo;
    
    public ReachCuratorToolWSHandler() {
        maxPmcidNumber = 3;
    }
    
    @Override
    protected boolean ensureRequirements(JFrame container) {
        String[] wsInfo = getWSUserInfo(container);
        if (wsInfo == null)
            return false; // Do nothing
        this.wsInfo = wsInfo;
        return true;
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
    
    @Override
    protected List<FriesObject> processReach(List<String> pmcids) throws Exception {
        return processReachViaWS(pmcids, wsInfo);
    }
    
    /**
     * Use this method to send a http get call to the Reactome curator tool servlet.
     * @param url
     * @return
     * @throws IOException
     */
    private List<FriesObject> processReachViaWS(List<String> pmcids, String[] wsInfo) throws IOException, JDOMException {
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
        List<FriesObject> objects = readJsonFiles(dir);
        return objects;
    }
    
    private String getServletURL() throws IOException, JDOMException {
        // We want to search reachURL in two places
        String reachURL = GKApplicationUtilities.getApplicationProperties().getProperty("reachURL");
        // The normal curator.xml configuration file
        if (reachURL == null) {
            reachURL = getReachURL("reachURL");
        }
        return reachURL;
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
        List<FriesObject> friesObjects = readJsonFiles(dir);
        outputFriesObjects(friesObjects);
    }
    
    @Test
    public void testProcessReachViaWS() throws Exception {
        String[] pmcids = {"PMC6683984", "PMC4750075"};
        // Need the actual user name and key for a successful test.
        List<FriesObject> objects = processReachViaWS(Arrays.asList(pmcids), new String[]{});
        outputFriesObjects(objects);
    }
    
}
