package org.gk.reach;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class PaperFetch {

    public PaperFetch() {
    }

    public String convertId(String pmid) throws Exception {
        String pmcid = null;
        StringBuilder stringBuilder = new StringBuilder(ReachConstants.CONVERT_URL);
        stringBuilder.append(ReachConstants.TOOL_EMAIL);
        stringBuilder.append(ReachConstants.IDS_QUERY);
        stringBuilder.append(pmid);
        ReachHttpCall reachCall = new ReachHttpCall();
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

    public String fetchPaper(String pmcid) throws IOException {
        if (!pmcid.toUpperCase().startsWith(ReachConstants.PMC))
            return null;

        // If paperId begins with "PMC", remove "PMC" and fetch paper.
        pmcid = pmcid.substring(ReachConstants.PMC.length());

        StringBuilder stringBuilder = new StringBuilder(ReachConstants.PAPER_URL);
        stringBuilder.append(ReachConstants.PMC_QUERY);
        stringBuilder.append(ReachConstants.ID_QUERY);
        stringBuilder.append(pmcid);
        stringBuilder.append(ReachConstants.TOOL_EMAIL);
        ReachHttpCall reachCall = new ReachHttpCall();

        return reachCall.callHttpGet(stringBuilder.toString());
    }

    public void writeFile(Path path, String contents) throws IOException {
       try (BufferedWriter writer = Files.newBufferedWriter(path,
                                                           StandardCharsets.UTF_8,
                                                           StandardOpenOption.CREATE,
                                                           StandardOpenOption.WRITE)) {
            writer.write(contents, 0, contents.length());
        }
    }

    @Test
    public void testFetchPaper() throws Exception {
    	// Apparently this is not open access so abstracts only.
    	// See: https://www.ncbi.nlm.nih.gov/pmc/utils/oa/oa.fcgi?id=21325
        String pmcid = "PMC21325";
        String nxml = fetchPaper(pmcid);
        System.out.println("Downloaded nxml: " + nxml);
    }
}
