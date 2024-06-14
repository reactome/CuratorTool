/*
 * Created on May 2, 2012
 *
 */
package org.gk.property;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.gk.model.Person;
import org.gk.model.Reference;
import org.gk.util.GKApplicationUtilities;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.junit.Test;

/**
 * This class is used to fetch XML from pubmed so that the full name can be fetched for an author.
 * The URL for this query is something like this: http://www.ncbi.nlm.nih.gov/pubmed/18276894?report=XML&amp;format=text
 * @author gwu
 *
 */
public class PMIDXMLInfoFetcher2 {
    // http://www.ncbi.nlm.nih.gov/pubmed/18276894?report=XML&format=text
//    private String url1 = "https://www.ncbi.nlm.nih.gov/pubmed/";
//    private String url2 = "?report=XML&format=text";
    // Use this URL as the default on May 21, 2020.
    private String url1 = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&retmode=xml&id=";
    private String url2 = "";
    // Used to parse XML elements
    
    public PMIDXMLInfoFetcher2() {
        loadConfig();
    }
    
    private void loadConfig() {
        try {
            InputStream is = GKApplicationUtilities.getConfig("curator.xml");
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(is);
            Element elm = (Element) XPath.selectSingleNode(document.getRootElement(), 
                    "pubmedurl");
            //        <pubmedurl>
            //            <url1>https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&retmode=xml&id=</url1>
            //            <!-- empty for the time being -->
            //            <url2></url2>
            //        </pubmedurl>
            String url = elm.getChildText("url1");
            if (url != null)
                this.url1 = url;
            url = elm.getChildText("url2");
            if (url != null)
                this.url2 = url;
            is.close();
        }
        catch(Exception e) {
            System.err.println("PMIDXMLInfoFetcher.loadConfig(): " + e);
            e.printStackTrace();
        }
    }
    
    public Reference fetchInfo(Long pmid) throws Exception {
        String url = url1 + pmid + url2;
        URL pubmed = new URL(url);
        URLConnection connection = pubmed.openConnection();
        InputStream is = connection.getInputStream();
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line = null;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("<!DOCTYPE html"))
                continue; // This line should not be in XML, which is wrong!
            builder.append(line).append("\n");
        }
        br.close();
        isr.close();
        is.close();
        String text = builder.toString();
        // The following code should not be used. The escaped XML is well taken care of
        // by JDOM.
//        text = StringEscapeUtils.unescapeXml(text);
        StringReader sr = new StringReader(text);
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(sr);
        // The root element is <pre> from URL.
        Element root = document.getRootElement();
        List<?> children = root.getChildren();
        for (Object obj : children) {
            Element elm = (Element) obj;
            // For journal article
            if (elm.getName().equals("PubmedArticle")) {
                elm = elm.getChild("MedlineCitation");
                if (elm != null) {
                    Element articleElm = elm.getChild("Article");
                    return parseArticle(articleElm); 
                }
            }
            // For book chapter
            else if (elm.getName().equals("PubmedBookArticle")) {
                elm = elm.getChild("BookDocument");
                if (elm != null) {
                    return parseBookDocument(elm);
                }
            }
        }
        return null;
    }
    
    private Reference parseBookDocument(Element elm) {
        Reference reference = new Reference();
        List<?> children = elm.getChildren();
        for (Object obj : children) {
            Element child = (Element) obj;
            String name = child.getName();
            if (name.equals("ArticleTitle")) {
                String text = child.getTextNormalize();
                if (text != null && text.length() > 0)
                    reference.setTitle(text);
            }
            else if (name.equals("AuthorList")) {
                parseAuthorList(child, reference);
            }
            else if (name.equals("ContributionDate")) {
                Element yearElm = child.getChild("Year");
                if (yearElm != null) {
                    String text = yearElm.getTextNormalize();
                    if (text != null && text.length() > 0) {
                        try {
                            reference.setYear(new Integer(text));
                        }
                        catch(NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return reference;
    }
    
    private String parseTitle(Element titleElm) {
        // Need to handle case like this (PMID: 29146722):
        // <ArticleTitle>The<i>NOTCH4</i>-<i>HEY1</i>Pathway Induces Epithelial-Mesenchymal Transition in Head and Neck Squamous Cell Carcinoma.</ArticleTitle>
        String text = new XMLOutputter().outputString(titleElm);
        // Get rid of the element tag
        int index1 = text.indexOf(">");
        int index2 = text.lastIndexOf("<");
        text = text.substring(index1 + 1, index2).trim();
        return text;
    }
    
    private Reference parseArticle(Element elm) {
        Reference reference = new Reference();
        List<?> list = elm.getChildren();
        for (Object obj : list) {
            Element childElm = (Element) obj;
            String name = childElm.getName();
            if (name.equals("Journal")) {
                parseJournal(childElm, reference);
            }
            else if (name.equals("ArticleTitle")) {
//                String text = childElm.getTextNormalize();
                String text = parseTitle(childElm);
                if (text != null) {
                    if (text.endsWith("."))
                        text = text.substring(0, text.length() - 1); // Get rid the last dot
                    reference.setTitle(text);
                }
            }
            else if (name.equals("Pagination")) {
                String pages = childElm.getChildTextNormalize("MedlinePgn");
                if (pages != null && pages.length() > 0)
                    reference.setPage(pages);
            }
            else if (name.equals("AuthorList")) {
                parseAuthorList(childElm, reference);
            }
        }
        return reference;
    }
    
    private void parseAuthorList(Element elm, Reference reference) {
        List<?> list = elm.getChildren("Author");
        for (Object obj : list) {
            Element child = (Element) obj;
            Person person = createPerson(child);
            reference.addAuthor(person);
        }
    }
    
    private Person createPerson(Element author) {
        Person person = new Person();
        List<?> list = author.getChildren();
        for (Object obj : list) {
            Element elm = (Element) obj;
            String name = elm.getName();
            String text = elm.getTextNormalize();
            if (text.length() == 0)
                continue;
            if (name.equals("LastName")) {
                person.setLastName(text);
            }
            else if (name.equals("ForeName")) {
                person.setFirstName(text);
            }
            else if (name.equals("Initials")) {
                person.setInitial(text);
            }
        }
//        // Make sure the first name is useful
//        if (person.getFirstName() != null) {
//            String firstName = person.getFirstName();
//            String[] tokens = firstName.split(" "); 
//            // Want to have the first token only
//            if (tokens[0].length() > 1)
//                person.setFirstName(tokens[0]);
//            else
//                person.setFirstName(null); // Nothing interesting there
//        }
        return person;
    }
    
    private void parseJournal(Element elm, Reference reference) {
        List<?> list = elm.getChildren();
        for (Object obj : list) {
            Element childElm = (Element) obj;
            String name = childElm.getName();
            if (name.equals("ISOAbbreviation")) {
                reference.setJournal(childElm.getTextNormalize());
            }
            else if (name.equals("JournalIssue")) {
                reference.setVolume(childElm.getChildTextNormalize("Volume"));
                Element dateElm = childElm.getChild("PubDate");
                if (dateElm != null) {
                    String year = dateElm.getChildTextNormalize("Year");
                    if (year != null && year.length() > 0)
                        reference.setYear(new Integer(year));
                    else { // For some old medline based entries (e.g. PMID: 7997270), the format
                           // is like this: <MedlineDate>1994 Dec 22-29</MedlineDate>. However, 
                           // this may not be fixed and should test more.
                        String medlineDate = dateElm.getChildTextNormalize("MedlineDate");
                        if (medlineDate != null && medlineDate.length() > 0) {
                            String tmp = medlineDate.split(" ")[0].trim();
                            if (tmp.matches("(\\d){4}")) { // If it is matched as four digits
                                reference.setYear(new Integer(tmp));
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Test
    public void testFetchInfo() throws Exception {
        Long pmid = 23356980L;
//        pmid = 29146722L; // A title has "<i>" in
        Reference reference = fetchInfo(pmid);
        System.out.println("\"" + reference.getTitle() + "\" in \"" + reference.getJournal() + "\"");
    }
}
