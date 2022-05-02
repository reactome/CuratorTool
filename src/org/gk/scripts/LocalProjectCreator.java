package org.gk.scripts;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.database.util.ReferencePeptideSequenceAutoFiller;
import org.gk.elv.InstanceCloneHelper;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * Create a local project based on some specification.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class LocalProjectCreator {
    
    public LocalProjectCreator() {
    }
    
    /**
     * This method is used to fix instances that have been overwriten in their modified slot.
     */
    @Test
    public void fixModifed() throws Exception {
        // Instances there to be fixed
        Neo4JAdaptor targetDBA = new Neo4JAdaptor("",
                "gk_central",
                "",
                "");
        // Instances having correct modified slot values.
        Neo4JAdaptor sourceDBA = new Neo4JAdaptor("localhost",
                "before_cov_manual_updates",
                "",
                "");
        // List of IEs with which instances should be checked
        Collection<GKInstance> ies = targetDBA.fetchInstanceByAttribute(ReactomeJavaConstants.InstanceEdit,
                ReactomeJavaConstants._displayName,
                "=",
                "Cook, Justin, 2020-08-27");
        System.out.println("Total IEs: " + ies.size());
        // Instances touched
        Collection<GKInstance> touchedInstances = targetDBA.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseObject,
                ReactomeJavaConstants.modified,
                "=",
                ies);
        System.out.println("Total referred instances: " + touchedInstances.size());
        // Check one by one
        int totalChanged = 0;
        Driver driver = targetDBA.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(targetDBA.getDBName()))) {
            Transaction tx = session.beginTransaction();
            for (GKInstance inst : touchedInstances) {
                boolean isChanged = false;
                System.out.println("Checking " + inst + "...");
                // Set the source
                GKInstance srcInst = sourceDBA.fetchInstance(inst.getDBID());
                List<GKInstance> modified = inst.getAttributeValuesList(ReactomeJavaConstants.modified);
                List<Long> modifiedIds = modified.stream().map(GKInstance::getDBID).collect(Collectors.toList());
                List<GKInstance> sourceModified = srcInst.getAttributeValuesList(ReactomeJavaConstants.modified);
                List<Long> sourceModifiedIds = sourceModified.stream().map(GKInstance::getDBID).collect(Collectors.toList());
                if (modifiedIds.equals(sourceModifiedIds)) {
                    System.out.println("Pass: Same list of modified!");
                    continue;
                }
                int originalSize = modified.size();
                // Need to update the target instance
                for (int i = sourceModified.size() - 1; i >= 0; i--) {
                    Long sourceId = sourceModifiedIds.get(i);
                    // Just in case
                    if (modifiedIds.contains(sourceId)) {
                        System.out.println("InstanceEdit with DB_ID has been there: " + sourceId);
                        continue;
                    }
                    GKInstance targetIE = targetDBA.fetchInstance(sourceId);
                    // Have to make sure it exists
                    if (targetIE == null)
                        throw new IllegalStateException("InstanceEdit for DB_ID, " + sourceId + ", cannot be found!");
                    if (!targetIE.getSchemClass().isa(ReactomeJavaConstants.InstanceEdit)) {
                        throw new IllegalStateException(targetIE + " is not an InstanceEdit!");
                    }
                    modified.add(0, targetIE);
                    isChanged = true;
                }
                if (!isChanged) {
                    System.out.println("Nothing to be changed after checking.");
                    continue;
                }
                System.out.println("Update modified by adding: " + originalSize + " -> " + modified.size());
                inst.setAttributeValue(ReactomeJavaConstants.modified, modified);
                targetDBA.updateInstanceAttribute(inst, ReactomeJavaConstants.modified, tx);
                totalChanged++;
            }
            tx.commit();
            System.out.println("Total changed instance: " + totalChanged);
        }
    }
    
    /**
     * Fix a local project for Marc by switch species into the relatedSpecies slot.
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void fixHCMVProject() throws Exception {
        String dirName = "/Users/wug/Documents/wgm/work/reactome/CuratorsProjects/";
        String srcFileName = dirName + "HCMV_Fix_GW.rtpj";
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(srcFileName);
        
        Long dbId = 9610379L;
        GKInstance lateEvent = fileAdaptor.fetchInstance(dbId);
        
        Neo4JAdaptor dba = new Neo4JAdaptor("curator.reactome.org",
                                            "gk_central",
                                            "authortool",
                                            "T001test");
        GKInstance dbLateEvent = dba.fetchInstance(dbId);
        
        List<GKInstance> hasEvent = lateEvent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        List<GKInstance> dbHasEvent = dbLateEvent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        Set<Long> dbHasEventIds = dbHasEvent.stream().map(inst -> inst.getDBID()).collect(Collectors.toSet());
        int total = 0;
        InstanceCloneHelper cloneHelper = new InstanceCloneHelper();
        Map<GKInstance, GKInstance> original2clone = new HashMap<>();
        GKInstance human = fileAdaptor.fetchInstance(48887L);
        for (GKInstance hasEventInst : hasEvent) {
            if (dbHasEventIds.contains(hasEventInst.getDBID()))
                continue;
            System.out.println("Working on: " + hasEventInst);
            // Make a clone
            GKInstance clone = cloneHelper.cloneInstance(hasEventInst, fileAdaptor);
            String name = clone.getDisplayName();
            if (name.startsWith("Clone of")) {
                name = name.replace("Clone of ", "");
                clone.setDisplayName(name);
            }
            // Switch instances to relatedInstance and set human as its instance
            List<GKInstance> species = clone.getAttributeValuesList(ReactomeJavaConstants.species);
            clone.setAttributeValue(ReactomeJavaConstants.relatedSpecies, species);
            clone.setAttributeValue(ReactomeJavaConstants.species, human);
            // Remove stable id
            clone.setAttributeValue(ReactomeJavaConstants.stableIdentifier, null);
            original2clone.put(hasEventInst, clone);
            total ++;
        }
        System.out.println("Total: " + total);
        // Handle the pathway diagram
        GKInstance diagramofHCMVInfection = fileAdaptor.fetchInstance(9609686L);
        RenderablePathway diagram = new DiagramGKBReader().openDiagram(diagramofHCMVInfection);
        List<Renderable> components = diagram.getComponents();
        for (Renderable r : components) {
            dbId = r.getReactomeId();
            if (dbId == null)
                continue;
            GKInstance inst = fileAdaptor.fetchInstance(dbId);
            if (inst == null)
                throw new IllegalStateException(dbId + " is not found!");
            GKInstance clone = original2clone.get(inst);
            if (clone == null)
                continue;
            r.setReactomeId(clone.getDBID());
        }
        diagramofHCMVInfection.setIsDirty(true);
        fileAdaptor.addDiagramForPathwayDiagram(diagramofHCMVInfection, diagram);
        
        for (int i = 0; i < hasEvent.size(); i++) {
            GKInstance hasEventInst = hasEvent.get(i);
            GKInstance clone = original2clone.get(hasEventInst);
            if (clone == null)
                continue;
            hasEvent.set(i, clone);
        }
        lateEvent.setIsDirty(true);
        
        fileAdaptor.save(dirName + "HCMV_Fix_GW_Fix.rtpj");
    }
    
    @Test
    public void fixProject() throws Exception {
        String dirName = "/Users/wug/Documents/wgm/work/reactome/covid-19/";
        String srcFileName = dirName + "Ralf_Project_Fix.rtpj";
        String targetFileName = dirName + "Ralf_Project_Fix_1.rtpj";
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost",
                                            "gk_central_2020_07_16",
                                            "root",
                                            "macmysql01");
        Long[] ieDbIds = {9695980L, 9695985L};
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(srcFileName);
        int count = 0;
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        manager.setActiveNeo4JAdaptor(dba);
        for (Long dbId : ieDbIds) {
            GKInstance ie = fileAdaptor.fetchInstance(dbId);
            Collection<GKInstance> referrers = fileAdaptor.getReferers(ie);
            System.out.println(ie + " <- " + referrers.size());
            for (GKInstance referrer : referrers) {
                Long referId = referrer.getDBID();
                GKInstance dbCopy = dba.fetchInstance(referId);
                if (dbCopy == null)
                    continue;
                System.out.println(referrer + " -> " + dbCopy);
                count ++;
                // Need to do some fix now
                // Flip the current DB_ID
                referrer.setDBID(-referId);
                fileAdaptor.dbIDUpdated(referId, referrer);
                referrer.setIsDirty(true);
                GKInstance localCopy = manager.download(dbCopy);
                localCopy.setIsDirty(true);
            }
        }
        dba.cleanUp();
//        fileAdaptor.save(targetFileName);
        System.out.println("Total wrong instances: " + count);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void removeIEInModified() throws Exception {
        String dirName = "/Users/wug/Documents/wgm/work/reactome/covid-19/";
        String srcFileName = dirName + "Inferred_SARS-CoV-2_Infection_local_v2_marc.rtpj";
        String targetFileName = dirName + "Inferred_SARS-CoV-2_Infection_local_v2_marc_ie_removed.rtpj";
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(srcFileName);
        Collection<GKInstance> instances = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.DatabaseObject);
        Long toBeRemovedID = 9694660L;
        int counter = 0;
        for (GKInstance inst : instances) {
            if (!inst.isDirty())
                continue;
            counter ++;
            List<GKInstance> modified = inst.getAttributeValuesList(ReactomeJavaConstants.modified);
            GKInstance lastIE = modified.get(modified.size() - 1);
            if (!lastIE.getDBID().equals(toBeRemovedID))
                throw new IllegalStateException(inst + " doesn't have the IE to be removed!");
            modified.remove(modified.size() - 1);
        }
        System.out.println("Total dirty instances: " + counter);
        fileAdaptor.save(targetFileName);
    }
    
    /**
     * Use this method to flip DB_IDs from positive to negative. This is used to create a new curator tool
     * project for pathways orthologously predicted or coming from other sources.
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void flipInstancesDBIDs() throws Exception {
        String dirName = "/Users/wug/Documents/wgm/work/reactome/covid-19/";
        String srcFileName = dirName + "Inferred_SARS-CoV-2_Infection.rtpj";
        String targetFileName = dirName + "Inferred_SARS-CoV-2_Infection_local_v2.rtpj";
        Long oldMaxDBID = 9691821L; // Pre-configured
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(srcFileName, true);
        // The original database used to generate the source project
        Neo4JAdaptor sourceDBA = new Neo4JAdaptor("localhost",
                                                  "test_cov_inference",
                                                  "root",
                                                  "macmysql01");
        // Have to make sure all new instances have been fully downloaded.
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        manager.setActiveNeo4JAdaptor(sourceDBA);
        while (true) {
            Collection<GKInstance> instances = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.DatabaseObject);
            boolean hasShell = false;
            for (GKInstance inst : instances) {
                if (inst.isShell() && inst.getDBID() > oldMaxDBID) { // Only for inferred instances
                    GKInstance dbCopy = sourceDBA.fetchInstance(inst.getDBID());
                    System.out.println("Download " + inst);
                    manager.updateLocalFromDB(inst, dbCopy);
                    hasShell = true;
                }
            }
            if (!hasShell)
                break;
        }
        Collection<GKInstance> instances = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.DatabaseObject);
        for (GKInstance inst : instances) {
            Long dbId = inst.getDBID();
            if (dbId > oldMaxDBID) {
                inst.setDBID(-dbId);
                inst.setIsDirty(true);
            }
        }
        // Need to handle pathway diagrams
        fileAdaptor.assignInstancesToDiagrams();
        fileAdaptor.save(targetFileName);
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
    
    private Neo4JAdaptor getDBA() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("reactomecurator.oicr.on.ca",
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
        
        Neo4JAdaptor dba = getDBA();
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        manager.setActiveNeo4JAdaptor(dba);
        
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
        Neo4JAdaptor dba = getDBA();
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveFileAdaptor(fileAdaptor);
        manager.setActiveNeo4JAdaptor(dba);
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
