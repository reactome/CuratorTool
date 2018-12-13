package org.gk.scripts;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.database.util.ReferencePeptideSequenceAutoFiller;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.junit.Test;

/**
 * Create a local project based on some specification.
 * @author wug
 *
 */
public class LocalProjectCreator {
    
    public LocalProjectCreator() {
        
    }
    
    /**
     * Reset DB_ID for local projects so that the project can be re-committed
     * into database to fix a database issue.
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void fixLocalProject() throws Exception {
        String dir = "/Users/wug/Documents/wgm/work/reactome/CuratorsProjects/";
        String fileName = dir + "NR1H2,3_2018.rtpj";
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(fileName);
        // Get all instances using these two IEs
        Long[] ieDBIDs = {9609255L, 9609302L};
        Set<GKInstance> ies = new HashSet<>();
        Set<GKInstance> referrers = new HashSet<>();
        for (Long dbId : ieDBIDs) {
            GKInstance ie = fileAdaptor.fetchInstance(dbId);
            ies.add(ie);
            Map<?, ?> refs = fileAdaptor.getReferrersMap(ie);
            refs.forEach((key, value) -> {
               List<GKInstance> set = (List<GKInstance>) value;
               referrers.addAll(set);
            });
        }
        // This number should be 58
        System.out.println("Total referrers: " + referrers.size());
        // Make change to all referrers
        for (GKInstance referrer : referrers) {
            GKInstance created = (GKInstance) referrer.getAttributeValue(ReactomeJavaConstants.created);
            if (ies.contains(created)) {
                // This is a new instance. Set it back to a new instance
                referrer.removeAttributeValueNoCheck(ReactomeJavaConstants.created, created);
                referrer.setIsDirty(true);
                referrer.setDBID(-referrer.getDBID());
            }
            // Since there are two updates, a new instance may be updated again. Therefore
            // don't use else. Just do another check
            List<GKInstance> modified = referrer.getAttributeValuesList(ReactomeJavaConstants.modified);
            if (modified == null || modified.size() == 0)
                continue; // For sure this is a new instance
            // We can remove the IEs directly from this list since it is kept in the cache
            if (modified.removeAll(ies)) {
                referrer.setIsDirty(true);
            }
        }
        // IEs should be deleted
        for (GKInstance ie : ies) {
            fileAdaptor.deleteInstance(ie);
        }
        fileAdaptor.clearDeleteRecord(Arrays.asList(ieDBIDs));
        String outFileName = dir + "NR1H2,3_2018_Reset.rtpj";
        fileAdaptor.save(outFileName);
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
                                            "gk_central",
                                            "authortool",
                                            "T001test");
        return dba;
    }
    
    /**
     * Based on a list of UniProt ids to generate a local project by pulling
     * necessary information from UniProt.
     * @throws Exception
     */
    @Test
    public void generateProjectForProteins() throws Exception {
        String dir = "/Users/wug/Documents/wgm/work/reactome/CuratorsProjects/";
        String idFile = dir + "UniProt_HHV5Merlin_IDs_Only.txt";
        Set<String> ids = Files.lines(Paths.get(idFile))
                               .collect(Collectors.toSet());
        System.out.println("Total ids: " + ids.size());
        
        MySQLAdaptor dba = getDBA();
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        manager.setActiveMySQLAdaptor(dba);
        
        // We need this species for the filler
        Long dbId = 2671791L; // Human cytomegalovirus
        GKInstance dbInst = dba.fetchInstance(dbId);
        GKInstance localInst = manager.getLocalReference(dbInst);
        manager.updateLocalFromDB(localInst, dbInst);
        
        ReferencePeptideSequenceAutoFiller filler = new ReferencePeptideSequenceAutoFiller();
        filler.setPersistenceAdaptor(fileAdaptor);
        
        for (String id : ids) {
            @SuppressWarnings("unchecked")
            Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct,
                                                                    ReactomeJavaConstants.identifier, 
                                                                    "=",
                                                                    id);
            if (c.size() == 0) {
                // Need to pull out instances
                System.out.println("Fetching from UniProt for " + id);
                GKInstance instance = fileAdaptor.createNewInstance(ReactomeJavaConstants.ReferenceGeneProduct);
                instance.setAttributeValue(ReactomeJavaConstants.identifier, id);
                filler.process(instance);
            }
            else if (c.size() == 1) {
                System.out.println(id + " is in the database!");
                dbInst = c.stream().findFirst().get();
                localInst = manager.getLocalReference(dbInst);
                manager.updateLocalFromDB(localInst, dbInst);
            }
            else
                System.out.println("More than one instance for " + id);
        }
        fileAdaptor.save(dir + "HHV5Merlin_RefEntities_Marc_022118.rtpj");
    }
    
    @Test
    public void generateLocalProjectForDrugs() throws Exception {
        String dir = "/Users/wug/Box Sync/Working/reactome/drug/";
        String fileName = dir + "SEs_with_disease_slot_filled.tsv";
//        String curatorName = "Jassal";
//        String curatorName = "Orlic-Milacic";
        String curatorName = "Rothfels";
        List<Long> dbIds = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.skip(1)
                  .map(line -> line.split("\t"))
                  .forEach(tokens -> {
                      if (tokens[2].startsWith(curatorName) && tokens[3].equals("yes"))
                          dbIds.add(new Long(tokens[0]));
                  });
        }
        System.out.println("Total DB_IDs for " + curatorName + ": " + dbIds.size());
        
        String outDirName = "/Users/wug/Documents/wgm/work/reactome/drugs/";
        String outFileName = outDirName + curatorName + "_Drug_081317.rtpj";
        createLocalProject(dbIds, outFileName);
    }
    
    private void createLocalProject(Collection<Long> dbIds,
                                    String fileName) throws Exception {
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        MySQLAdaptor dba = getDBA();
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        manager.setActiveMySQLAdaptor(dba);
        dbIds.forEach(dbId -> {
            try {
                GKInstance dbInst = dba.fetchInstance(dbId);
                GKInstance localInst = manager.getLocalReference(dbInst);
                manager.updateLocalFromDB(localInst, dbInst);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        });
        fileAdaptor.save(fileName);
    }

}
