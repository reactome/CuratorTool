package org.gk.scripts;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.qualityCheck.QACheckUtilities;
import org.gk.util.FileUtilities;
import org.gk.util.StringUtils;
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
//    private String dir = "/Volumes/ssd/datasets/retraction_watch/2023/";
    private String dir = "/Volumes/ssd/datasets/retraction_watch/2024/";
    
    private String retractionFile = dir + "retractions.csv";
//    private String outputFile = dir + "retraction_in_Reactome_11292023.tsv";
    private String outputFile = dir + "retraction_in_Reactome_10012024.tsv";
    
    public LiteratureReferenceRetractionChecker() {
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        // 2023
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                "gk_central_101723",
//                "root",
//                "macmysql01");
        // 2024
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_10012024",
                "root",
                "macmysql01");
        return dba;
    }
    
    @Test
    public void importRetractionStatus() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Collect all RetractionStatus instances
        Collection<GKInstance> insts = dba.fetchInstancesByClass(ReactomeJavaConstants.RetractionStatus);
        Map<String, GKInstance> name2inst = new HashMap<>();
        for (GKInstance inst : insts) {
            String name = inst.getDisplayName();
            name2inst.put(name,  inst);
        }
        // This is a file generated in 2023
        // https://docs.google.com/spreadsheets/d/1tW4z8vCHp2KYXUZzx1PwPrc_a0tbC9Ol4H7nPkVUpok/edit?gid=867377212#gid=867377212
        String statusFileName = dir + "retraction_in_Reactome_2023_Final_ All_Retraction_instances_by_curator.tsv";
        FileUtilities fu = new FileUtilities();
        fu.setInput(statusFileName);
        String line = fu.readLine(); // header
        Map<GKInstance, GKInstance> inst2status = new HashMap<>();
        while ((line = fu.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0)
                continue; // Escape empty line. Not sure why there are so many!
            String[] tokens = line.split("\t");
            Long dbId = Long.parseLong(tokens[0]);
            GKInstance inst = dba.fetchInstance(dbId);
            if (inst == null) {
                System.out.println("Cannot find instance for dbId: " + dbId);
                continue;
            }
            String status = tokens[3];
            GKInstance statusInst = name2inst.get(status);
            if (statusInst == null) {
                System.out.println("CAnnot find instance for retractionStatus:" + status);
                continue;
            }
            GKInstance saved = inst2status.get(inst);
            if (saved == null) {
                inst2status.put(inst, statusInst);
            }
            else if (saved != statusInst) {
                System.err.println(dbId + " has different status: " + saved + " " + status);
            }
        }
        fu.close();
        System.out.println("Size of inst2status: " + inst2status.size());
//        for (GKInstance lit : inst2status.keySet()) {
//            GKInstance status = inst2status.get(lit);
//            System.out.println(lit + "\t" + status);
//        }
        try {
            dba.startTransaction();
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance inst : inst2status.keySet()) {
                GKInstance status = inst2status.get(inst);
                inst.addAttributeValue(ReactomeJavaConstants.retractionStatus, status);
                dba.updateInstanceAttribute(inst, ReactomeJavaConstants.retractionStatus);
                ScriptUtilities.addIEToModified(inst, ie, dba);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
        }
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
        fu.printLine("LitRef_DBID\tLitRef_DisplayName\tPubMedId\tReferrer_DBID"
                + "\tReferrer_DisplayName\tReferrer_SchemaClass\tReferer_FirstIE"
                + "\tReferrer_LastIE\tReferrer_Release_Status\tReferrer_Other_PMIDs");
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
    
    private String getEventReleaseStatus(GKInstance inst) throws Exception {
        if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.stableIdentifier)) {
            GKInstance stId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (stId == null)
                return "";
            Boolean released = (Boolean) stId.getAttributeValue(ReactomeJavaConstants.released);
            if (released == null)
                return "";
            return released.toString();
        }
        return "";
    }
    
    private String getOtherPMIDs(GKInstance inst, Integer pmid) throws Exception {
        if (!inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.literatureReference))
            return "";
        List<GKInstance> refs = inst.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
        List<Integer> pmids = new ArrayList<>();
        for (GKInstance ref : refs) {
            if (!ref.getSchemClass().isValidAttribute(ReactomeJavaConstants.pubMedIdentifier))
                continue;
            Integer pmid1 = (Integer) ref.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier);
            if (pmid1 != null && !pmid1.equals(pmid))
                pmids.add(pmid1);
        }
        if (pmids.size() == 0)
            return "";
        Collections.sort(pmids);
        return StringUtils.join(";", pmids);
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
                    getIEText(lastIE) + "\t" + 
                    getEventReleaseStatus(referrer) + "\t" + 
                    getOtherPMIDs(referrer, pubmedId)
                    );
        }
    }

}
