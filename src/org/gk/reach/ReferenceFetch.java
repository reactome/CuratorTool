package org.gk.reach;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.gk.model.Person;
import org.gk.model.Reference;
import org.gk.reach.model.paperMetadata.PaperMetadata;

public class ReferenceFetch {
    public ReferenceFetch() {
    }

    public Reference fetchReference(String paperId) throws IOException {
        StringBuilder stringBuilder = new StringBuilder(ReachConstants.REFERENCE_URL);
        if (paperId.toUpperCase().startsWith(ReachConstants.PMC))
            stringBuilder.append(ReachConstants.PMC_QUERY);
        else if (paperId.toUpperCase().startsWith(ReachConstants.PMID))
            stringBuilder.append(ReachConstants.PUBMED_QUERY);
        else return null;

        stringBuilder.append(ReachConstants.ID_QUERY);
        // Remove "PMC" or "PMID" from paperId.
        paperId = paperId.replaceAll("[\\D.]", "");
        stringBuilder.append(paperId);
        stringBuilder.append(ReachConstants.RETMODE);
        stringBuilder.append(ReachConstants.TOOL_EMAIL);

        ReachCall reachCall = new ReachCall();
        String metadata = reachCall.callHttpGet(stringBuilder.toString());
        PaperMetadata paperMetadataObj = ReachUtils.readJsonText(metadata, PaperMetadata.class);
        Map<String, Object> paperData = paperMetadataObj.getResult().getPaperData().get(paperId);
        return createReference(paperData);
    }

    private Reference createReference(Map<String, Object> paperData) {
        // authors
        List<Map<String, String>> authorMaps = (List<Map<String, String>>) paperData.get("authors");
        List<Person> authorList = new ArrayList<Person>();
        List<String> authorName = null;
        for (Map<String, String> authorMap : authorMaps) {
            authorName = Arrays.asList(authorMap.get("name").split(" "));
            Person author = new Person();
            author.setLastName(authorName.get(0));
            author.setFirstName(authorName.get(1));
            authorList.add(author);
        }

        // pages
        String pages = (String) paperData.get("pages");

        // pmid, pmcid
        List<Map<String, String>> articleids = (List<Map<String, String>>) paperData.get("articleids");
        Long pmid = null;
        String pmcid = null;
        String idtype = null;
        for (Map<String, String> map : articleids) {
            idtype = map.get("idtype");
            if (idtype.equals("pmid"))
                pmid = Long.parseLong(map.get("value"));
            else if (idtype.equals("pmcid"))
                pmcid = map.get("value");
        }

        // source (i.e. journal)
        String source = (String) paperData.get("source");

        // title
        String title = (String) paperData.get("title");

        // volume
        String volume = (String) paperData.get("volume");

        // year
        String pubdate = (String) paperData.get("pubdate");
        int year = Integer.parseInt(pubdate.split(" ")[0]);


        // Create and return new Reference.
        Reference reference = new Reference();
        reference.setAuthors(authorList);
        reference.setJournal(source);
        reference.setPage(pages);
        reference.setPmid(pmid);
        reference.setPmcid(pmcid);
        reference.setTitle(title);
        reference.setVolume(volume);
        reference.setYear(year);
        return reference;

    }

	public static void main(String[] args) throws IOException {
	    ReferenceFetch referenceFetch = new ReferenceFetch();
	    Reference reference = referenceFetch.fetchReference("PMC3539452");
	    System.out.println(reference.getTitle());
	}
}
