package org.gk.reach;

import java.util.Arrays;
import java.util.List;

public class ReachConstants {
    public static final String ACCEPT = "Accept?";
    public static final String BRIEF_TEXT = "Brief Text";
    public static final String JOURNAL = "Journal";
    public static final String PMC_ID = "PMC ID";
    public static final String PMID = "PMID";
    public static final String PARTICIPANT_A = "Entity A";
    public static final String PARTICIPANT_B = "Entity B";
    public static final String INTERACTION_TYPE = "Interaction Type";
    public static final String INTERACTION_SUBTYPE = "Interaction Subtype";
    public static final String EXTRACTED_INFORMATION = "Extracted Information";
    public static final String PARTICIPANT_A_ID = "Entity A ID";
    public static final String PARTICIPANT_B_ID = "Entity B ID";
    public static final String IDENTIFIER = "Identifier";
    public static final String NAME = "Name";
    public static final String YEAR = "Year";
    public static final String NCBI_URL = "https://www.ncbi.nlm.nih.gov";
    public static final String PMC_URL = NCBI_URL.concat("/pmc/articles/");
    public static final String PMID_URL = NCBI_URL.concat("/pubmed/");
    public static final String QUERY_PREFIX = "?text=";
    public static final String REACH_API_URL = ""; // TODO determine local running REACH url.
    public static final String REACH_SEND_TYPE = ""; // TODO determine send type for file upload.
    public static final String GRAPHQL_API_URL = "https://reach-api.nrnb-docker.ucsd.edu/";
    public static final String GRAPHQL_SEND_TYPE = "application/GraphQL";
    public static final String GRAPHQL_SEARCH_TEMPLATE = "resources/reachSearchTemplate.graphql";
    public static final String REACH_REQUEST_TYPE = "application/Json";

    public static final String PAPER_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?";
    public static final String CONVERT_URL = "https://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/";
    public static final String TOOL_EMAIL = "&tool=Reactome-REACH&email=help@reactome.org";
    public static final String PMC_QUERY = "&db=pmc";
    public static final String PUBMED_QUERY = "&db=pubmed";
    public static final String ID_QUERY = "&id=";
    public static final String IDS_QUERY = "&ids=";
    public static final String PMC = "PMC";
    public static final String NXML_EXT = ".nxml";
    public static final String RECORD = "record";
    public static final String REFERENCE_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?";
    public static final String RETMODE = "&retmode=json";

    public static final List<String> COLUMN_NAMES_PROCESS = Arrays.asList(PARTICIPANT_A,
                                                                          PARTICIPANT_A_ID,
                                                                          "Entity A Type",
                                                                          PARTICIPANT_B,
                                                                          PARTICIPANT_B_ID,
                                                                          "Entity B Type",
                                                                          INTERACTION_TYPE,
                                                                          INTERACTION_SUBTYPE,
                                                                          "Occurences",
                                                                          "Citations");
//                                                                          ACCEPT); // Turn this off for the time being

    public static final List<String> ARTICLE_INFO = Arrays.asList(PMC_ID,
                                                                  PMID,
                                                                  BRIEF_TEXT,
                                                                  JOURNAL,
                                                                  YEAR);
    /**
     * The list of URLs implemented in this method is based on the original Reach code:
     * https://github.com/clulab/reach/blob/afff6776cf2449c6de1c16c79776a499e1f06dc6/export/src/main/scala/org/clulab/reach/export/cmu/CMURow.scala#L63-L75:
     * case "uniprot" => "Protein" 
      case "pfam" => "Protein Family"
      case "interpro" => "Protein Family"
      case "be" => "Protein Family|Protein Complex"
      case "pubchem" => "Chemical"
      case "hmdb" => "Chemical"
      case "chebi" => "Chemical"
      case "go" => "Biological Process"
      case "mesh" => "Biological Process"
      case _ => "Other"
     * @param xrefId
     * @return
     */
    public static String getURL(String xrefId) {
        String[] tokens = xrefId.split(":");
        String db = tokens[0].toLowerCase();
        String id = tokens[1];
        if (db.equals("uniprot")) 
            return "https://www.uniprot.org/uniprot/" + id;
        else if (db.equals("pfam"))
            return "http://pfam.xfam.org/family/" + id;
        else if (db.equals("interpro"))
            return "https://www.ebi.ac.uk/interpro/entry/InterPro/" + id;
//        else if (db.equals("be"))
//            return "";
        else if (db.equals("hmdb"))
            return "https://hmdb.ca/metabolites/" + id;
        else if (db.equals("go"))
            return "https://www.ebi.ac.uk/QuickGO/term/GO:" + id;
        else if (db.equals("pubchem"))
            return "https://pubchem.ncbi.nlm.nih.gov/compound/" + id;
        else if (db.equals("chebi"))
            return "https://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:" + id;
        else if (db.equals("mesh"))
            return "https://id.nlm.nih.gov/mesh/2017/" + id;
        else
            return null;
    }
}
