package org.gk.reach;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class PaperFetch {

    public PaperFetch() {
    }

    private String convertId(String pmid) throws IOException, SAXException, ParserConfigurationException {
        String pmcid = null;
        StringBuilder stringBuilder = new StringBuilder(ReachConstants.CONVERT_URL);
        stringBuilder.append(ReachConstants.TOOL_EMAIL);
        stringBuilder.append(ReachConstants.IDS_QUERY);
        stringBuilder.append(pmid);
        ReachCall reachCall = new ReachCall();
        String response = reachCall.callHttpGet(stringBuilder.toString());

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new InputSource(new StringReader(response)));
        document.getDocumentElement().normalize();

        // TODO Simply search for string?
        Node node = document.getElementsByTagName(ReachConstants.RECORD).item(0).getAttributes().item(1).getFirstChild();
        pmcid = node.getNodeValue().substring(ReachConstants.PMC.length());
        return pmcid;
    }

    public Path fetchPaper(String paperId) throws IOException, SAXException, ParserConfigurationException {
        String pmcid = null;
        // If paperId begins with "PMC", remove "PMC" and fetch paper.
        if (paperId.toUpperCase().startsWith(ReachConstants.PMC))
            pmcid = paperId.substring(ReachConstants.PMC.length());

        // Otherwise, pass paperId to the PMID -> PMCID converter and then fetch paper.
        else
            pmcid = convertId(paperId);

        StringBuilder stringBuilder = new StringBuilder(ReachConstants.PAPER_URL);
        stringBuilder.append(ReachConstants.PMC_QUERY);
        stringBuilder.append(ReachConstants.ID_QUERY);
        stringBuilder.append(pmcid);
        stringBuilder.append(ReachConstants.TOOL_EMAIL);
        ReachCall reachCall = new ReachCall();
        String nxml = reachCall.callHttpGet(stringBuilder.toString());

        if (nxml == null || nxml.length() == 0)
            return null;
        Path path = Paths.get(paperId.concat(ReachConstants.NXML_EXT));
        try(BufferedWriter writer = Files.newBufferedWriter(path,
                                                            Charset.forName("UTF-8"), // Make it compatible in other OS.
                                                            StandardOpenOption.CREATE,
                                                            StandardOpenOption.WRITE)) {
            writer.write(nxml, 0, nxml.length());
        }
        return path;
    }
    
    @Test
    public void testFetchPaper() throws Exception {
        String pmcid = "PMC6683984";
        Path path = fetchPaper(pmcid);
        System.out.println("Downloaded file: " + path.getFileName());
    }
}
