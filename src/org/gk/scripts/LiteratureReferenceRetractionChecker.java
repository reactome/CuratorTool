package org.gk.scripts;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.qualityCheck.QACheckUtilities;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is used to check if any pubmed ids that are used in Reactome have been retracted.
 * The retraction list is downloaded from https://api.labs.crossref.org/data/retractionwatch?name@email.org
 * and saved locally for performance.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class LiteratureReferenceRetractionChecker {
    // This is hard-coded and should be updated every year.
    private String dir = "/Volumes/ssd/datasets/retraction_watch/2023/";
    
    private String retractionFile = dir + "retractions.csv";
    private String outputFile = dir + "retraction_in_Reactome_11292023.tsv";
    
    public LiteratureReferenceRetractionChecker() {
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_101723",
                "root",
                "macmysql01");
        return dba;
    }
   
    
    @SuppressWarnings("deprecation")
    private Set<String> loadRetractedPubmedIds() throws IOException {
        FileReader fileReader = new FileReader(retractionFile);
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(fileReader);
        Set<String> pubmedids = new HashSet<>();
        for (CSVRecord record : records) {
//            String pubmedId = record.get("RetractionPubMedID");
            String pubmedId = record.get("OriginalPaperPubMedID");
            if (pubmedId.equals("0"))
                continue;
            pubmedids.add(pubmedId);
        }
        System.out.println("Total retracted pubmed ids: " + pubmedids.size());
        return pubmedids;
    }
    
    @Test
    public void checkLiteratureReferences() throws Exception {
        FileUtilities fu = new FileUtilities();
        fu.setOutput(outputFile);
        fu.printLine("LitRef_DBID\tLitRef_DisplayName\tPubMedId\tReferrer_DBID\tReferrer_DisplayName\tReferrer_SchemaClass\tReferer_FirstIE\tReferrer_LastIE");
        // Use string on purpose in case there is any format error.
        Set<String> retractedPubmedIds = loadRetractedPubmedIds();
        MySQLAdaptor dba = getDBA();
        Collection<GKInstance> literatureReferences = dba.fetchInstancesByClass(ReactomeJavaConstants.LiteratureReference);
        dba.loadInstanceAttributeValues(literatureReferences, new String[]{ReactomeJavaConstants.pubMedIdentifier});
        for (GKInstance literatureReference : literatureReferences) {
            Integer pubmedid = (Integer) literatureReference.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier);
            if (pubmedid == null)
                continue;
            if (retractedPubmedIds.contains(pubmedid.toString())) {
                System.out.println(literatureReference);
                generateReport(literatureReference, fu);
            }
        }
        fu.close();
    }
    
    private String getIEText(GKInstance ie) throws Exception {
        String displayName = ie.getDisplayName();
        int index = displayName.indexOf("]");
        return displayName.substring(index + 1).trim();
    }
    
    private void generateReport(GKInstance literatureReference, FileUtilities fu) throws Exception {
        Set<GKInstance> referrers = ScriptUtilities.getAllReferrersForDBInstance(literatureReference);
        Integer pubmedId = (Integer) literatureReference.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier);
        for (GKInstance referrer : referrers) {
            GKInstance lastIE = QACheckUtilities.getLatestCuratorIEFromInstance(referrer);
            GKInstance firstIE = QACheckUtilities.getFirstCuratorIEFromInstance(referrer);
            fu.printLine(literatureReference.getDBID() + "\t" + 
                    literatureReference.getDisplayName() + "\t" + 
                    pubmedId + "\t" + 
                    referrer.getDBID() + "\t" + 
                    referrer.getDisplayName() + "\t" + 
                    referrer.getSchemClass().getName() + "\t" + 
                    getIEText(firstIE) + "\t" + 
                    getIEText(lastIE));
        }
    }

}
