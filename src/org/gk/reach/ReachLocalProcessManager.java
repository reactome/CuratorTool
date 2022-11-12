package org.gk.reach;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.gk.model.Person;
import org.gk.model.Reference;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import uk.ac.ebi.kraken.model.uniprot.comments.AlternativeProductsEventImpl;

public class ReachLocalProcessManager {
    private Path paperDir;
    private Path outputDir;
    private Path errorLog;

    public ReachLocalProcessManager() {
    }
    
    public void setRootPath(Path reachRoot, boolean needReset) throws IOException {
        if (!reachRoot.toFile().exists())
            Files.createDirectories(reachRoot);

        paperDir = reachRoot.resolve("papers");
        if (!Files.exists(paperDir)) 
            Files.createDirectories(paperDir);
        outputDir = reachRoot.resolve("output");
        if (!Files.exists(outputDir))
            Files.createDirectories(outputDir);
        errorLog = reachRoot.resolve("error.log");
        
        if (needReset) {
            resetFolders(paperDir);
            resetFolders(outputDir);
        }
    }

    public void process(List<String> pmcids, Path reachJar, Path reachConf) throws IOException {
        handlePMCIDs(pmcids);
        startReach(reachJar, reachConf);
    }
    
    private void startReach(Path reachJar, Path reachConf) throws IOException {
        // Start the process
        Runtime runtime = Runtime.getRuntime();
        // TODO make sure log file works and is consistent with other log files (for development).
        Process process = runtime.exec(new String[]{"java",
                                                    "-Dconfig.file=" +
                                                    reachConf.toString(),
                                                    "-jar",
                                                    reachJar.toString()});
        outputProcessError(process);
    }

    protected void outputProcessError(Process process) throws IOException {
        // Just check if there is any error-X
        InputStream is = process.getErrorStream();
        String error = output(is);
        is.close();
        if (error != null && error.length() > 0) {
            try(BufferedWriter writer = Files.newBufferedWriter(errorLog, StandardCharsets.UTF_8)) {
                writer.write(error, 0, error.length());
            }
            return;
        }
    }
    
    protected void startReachViaBash(Path reachJar, Path reachConf) throws IOException {
        File bash_file = new File("run_reach.sh");
        PrintWriter printWriter = new PrintWriter(bash_file);
        printWriter.println("#! /bin/bash");
        printWriter.println(generateBashCommand(reachJar, reachConf));
        printWriter.close();
        // Start the process
        Runtime runtime = Runtime.getRuntime();
        // TODO make sure log file works and is consistent with other log files (for development).
        Process process = runtime.exec(new String[]{"bash",
                                                    bash_file.getName()});
        outputProcessError(process);
        System.out.println("Started Reach via bash.");
    }
    
    private String generateBashCommand(Path reachJar, Path reachConf) {
        return "java -Dconfig.file=" + reachConf.toString() +
                " -jar " + reachJar.toString() + 
                " > out.txt 2>&1";
    }
    
    /**
     * Check if Reach is running by running ps aux
     * @return
     * @throws IOException
     */
    private boolean isReachRunning(Path reachJar, Path reachConf) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        // TODO make sure log file works and is consistent with other log files (for development).
        Process process = runtime.exec(new String[]{"ps",
                                                    "aux"});
        String output = output(process.getInputStream());
        return output.contains("-Dconfig.file=" + reachConf.toString());
    }

    private int handlePMCIDs(List<String> pmcids) throws IOException {
        // The NCBI fetch api supports to download a single nxml file for multiple
        // PMCIDs. However, it is much easier to download them one by one for downstream
        // handling
        Map<String, Reference> pmcid2reference = new HashMap<>();
        for (String pmcid : pmcids) {
            pmcid = pmcid.trim();
            if (pmcid.length() == 0)
                continue; // Do nothing
            Reference reference = downloadPaper(pmcid);
            pmcid2reference.put(pmcid, reference);
        }
        dumpReferences(pmcid2reference);
        return pmcid2reference.size();
    }

    private void dumpReferences(Map<String, Reference> pmcid2Reference) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        for (String pmcid : pmcid2Reference.keySet()) {
            Reference reference = pmcid2Reference.get(pmcid);
            if (reference == null) {
                continue;
            }
            File file = new File(outputDir.toFile(), pmcid + ".reference.json");
            writer.writeValue(file, reference);
        }
    }

    private String output(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line = null;
        StringBuilder builder = new StringBuilder();
        while ((line = br.readLine()) != null) {
            if (line.startsWith("Loading required package"))
                continue; // Escape these information lines
            builder.append(line).append("\n");
        }
        br.close();
        isr.close();
        return builder.toString();
    }

    private Reference downloadPaper(String pmcid) throws IOException {
        // Need to save it into the paper folder
        File paperFile = new File(paperDir.toFile(), pmcid + ".nxml");

        PaperFetch paperFetch = new PaperFetch();
        String nxml = paperFetch.fetchPaper(pmcid);
        paperFetch.writeFile(paperFile.toPath(), nxml);

        // This is for generate a Reference object from nxml
        Reference reference = createReference(nxml);
        return reference;
    }

    @SuppressWarnings("unchecked")
    private Reference createReference(String nxml) throws IOException {
        try {
            Reference reference = new Reference();
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(new ByteArrayInputStream(nxml.getBytes()));
            Element root = document.getRootElement();
            Element journalMeta = (Element) XPath.selectSingleNode(root, "article/front/journal-meta/journal-id[@journal-id-type='nlm-ta']");
            if (journalMeta != null) {
                reference.setJournal(journalMeta.getTextTrim());
            }
            Element articleMeta = (Element) XPath.selectSingleNode(root, "article/front/article-meta");
            List<Element> children = articleMeta.getChildren();
            String fPage = null;
            String lPage = null;
            for (Element child : children) {
                String name = child.getName();
                if (name.equals("article-id")) {
                    String pubIdType = child.getAttributeValue("pub-id-type");
                    if (pubIdType.equals("pmid"))
                        reference.setPmid(Long.parseLong(child.getTextNormalize()));
                    else if (pubIdType.equals("pmc"))
                        reference.setPmcid("PMC" + child.getTextNormalize());
                    else if (pubIdType.equals("doi"))
                        reference.setDoi(child.getTextNormalize());
                }
                else if (name.equals("title-group")) {
                    String title = child.getChildText("article-title");
                    reference.setTitle(title);
                }
                else if (name.equals("contrib-group")) {
                    List<Person> persons = parseAuthors(child);
                    reference.setAuthors(persons);
                }
                else if (name.equals("pub-date") && reference.getYear() == 0) { // There are two types of pub-type: ppub and epub.
                                                    // Since we want to get the year, therefore, take whatever
                                                    // list first
                    int year = Integer.parseInt(child.getChildText("year"));
                    reference.setYear(year);
                }
                else if (name.equals("volume")) {
                    reference.setVolume(child.getText());
                }
                else if (name.equals("fpage"))
                    fPage = child.getText();
                else if (name.equals("lpage"))
                    lPage = child.getText();
            }
            String page = (fPage == null ? "" : fPage) + "-" + (lPage == null ? "" : lPage);
            if (page.length() > 1)
                reference.setPage(page);
            return reference;
        }
        catch(JDOMException e) {
            return null; // Cannot do anything then
        }
    }

    @SuppressWarnings("unchecked")
    private List<Person> parseAuthors(Element element) throws JDOMException {
        List<Person> authors = new ArrayList<>();
        List<Element> authorElms = (List<Element>)XPath.selectNodes(element,
                                                                    "contrib[@contrib-type='author']");
        for (Element authorElm : authorElms) {
            Element nameElm = authorElm.getChild("name");
            String surname = nameElm.getChildText("surname");
            String givenName = nameElm.getChildText("given-names");
            Person person = new Person();
            person.setFirstName(givenName);
            person.setLastName(surname);
            authors.add(person);
        }
        return authors;
    }

    private void resetFolders(Path dir) throws IOException {
        File[] files = dir.toFile().listFiles();
        if (files != null && files.length > 0) {
            for (File file : files)
                file.delete();
        }
    }
    
    protected int getNumberOfProcessedPMCIDs() throws IOException {
        File[] files = outputDir.toFile().listFiles();
        Set<String> pmcids = new HashSet<>();
        for (File file : files) {
            String name = file.getName();
            if (name.matches("PMC\\d+\\.uaz\\.\\w+\\.json")) {
                pmcids.add(name.split("\\.")[0]);
            }
        }
        return pmcids.size();
    }
    
    @Test
    public void testGetProcessedPMCIDs() throws IOException {
        String name = "PMC4039224.uaz.entities.json";
        if (name.matches("PMC\\d+\\.uaz\\.\\w+\\.json")) {
            System.out.println("Matched!");
        }
        outputDir = Paths.get("/Volumes/ssd/results/reach_marija/06062022/output");
        System.out.println("Total processed PMCIDs: " + getNumberOfProcessedPMCIDs());
    }
    

    @Test
    public void testCreateReference() throws IOException, SAXException, ParserConfigurationException {
        Path home = Paths.get(System.getProperty("user.home"));
        Path dir = home.resolve("Documents/reach/papers/");
        String pmcid = "PMC6683984";
        Path paper = Paths.get(pmcid.concat(".nxml"));
        Path file = dir.resolve(paper);
        String nxml = null;

        if (!Files.exists(paper)) {
            PaperFetch paperFetch = new PaperFetch();
            nxml = paperFetch.fetchPaper(pmcid);
            paperFetch.writeFile(file, nxml);
        }
        else
            nxml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        Reference reference = createReference(nxml);
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        String rtn = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reference);
        System.out.println(rtn);
    }

    @Test
    public void testProcess() throws IOException {
        Path home = Paths.get(System.getProperty("user.home"));
        Path root = home.resolve("Documents/reach");
        Path jar = root.resolve("reach.jar");
        Path conf = root.resolve("application.conf");
        process(Arrays.asList("PMC6683984"), jar, conf);
    }
    
    public static void main(String[] args) {
    	try {
    		if (args.length < 4)
    			throw new IllegalArgumentException("Need parameters: reach_root_dir pcmidlist_file reach_jar_file application_conf_file {is_restart}");
    		Path root = Paths.get(args[0]);
    		// Make sure the file doesn't have an empty line at the end to use the following
    		// method.
    		List<String> pmcids = Files.readAllLines(Paths.get(args[0], args[1]));
    		int totalPapers = pmcids.size();
    		ReachLocalProcessManager runner = new ReachLocalProcessManager();
    		if (args.length == 5 && args[4].equals("true")) { // Need to reset
    		    runner.setRootPath(root, true);
    		    // Handle papers first
    		    totalPapers = runner.handlePMCIDs(pmcids);
    		}
    		else 
    		    runner.setRootPath(root, false);
    		Path jar = Paths.get(args[0], args[2]);
            Path conf = Paths.get(args[0], args[3]);
            int previousHandled = -1;
            int previousHandledInSingleProcess = 0;
    		while (true) {
    		    if (!runner.isReachRunning(jar, conf)) {
    		        System.out.println("Reach is not running.");
    		        int currentHandled = runner.getNumberOfProcessedPMCIDs();
    		        if (currentHandled > previousHandled) {// Restart may help 
    		            System.out.println("Starting Reach...");
    		            runner.startReachViaBash(jar, conf);
    		            previousHandled = currentHandled;
    		        }
    		    }
    		    // Wait for one minute and then check if Reach is still running.
    		    Thread.sleep(60 * 1000);
    		    int handledPapers = runner.getNumberOfProcessedPMCIDs();
    		    // If there are updated in one minute, continue the loop.
    		    if (handledPapers > previousHandledInSingleProcess) {
    		        previousHandledInSingleProcess = handledPapers;
    		        continue;
    		    }
    		    // Do one try. If nothing improved, just stop it.
    		    if (handledPapers >= totalPapers || handledPapers == previousHandledInSingleProcess)
    		        break;
    		}
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }
}
