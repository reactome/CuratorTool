/*
 * Created on Dec 1, 2014
 *
 */
package org.gk.scripts;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

/**
 * This class is used to handle disease related pathways updates.
 * @author gwu
 */
@SuppressWarnings("unchecked")
public class DiseasePathwayUpdates {
    private MySQLAdaptor dba;
    
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("java -Xmx4G org.gk.scripts.DiseasePathwayUpdates dbHost dbName dbUser dbPwd");
            System.exit(0);
        }
        MySQLAdaptor dba = new MySQLAdaptor(args[0],
                                            args[1],
                                            args[2],
                                            args[3]);
        DiseasePathwayUpdates updater = new DiseasePathwayUpdates();
        updater.setDBA(dba);
        updater.useNewNormalPathwaySlot();
    }
    
    /**
     * Default constructor.
     */
    public DiseasePathwayUpdates() {
    }
    
    /**
     * This method is used to re-organize disease pathways to their appropriate places as
     * requested by Antonio.
     * @throws Exception
     */
    @Test
    public void reOrganizeDiseasePathwaysToNormals() throws Exception {
        MySQLAdaptor dba = getDBA();
        
        // Remove disease pathways from disease
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                                                                ReactomeJavaConstants._displayName,
                                                                "=",
                                                                "Disease");
        if (c.size() > 1)
            throw new IllegalStateException("More than one Disease pathway!");
        GKInstance disease = c.iterator().next();
        
        Map<GKInstance, GKInstance> diseaseToNormal = generateDiseasePathwaysToNormalMappping(dba);
        // Check all mappings
        System.out.println("\nFinal mappings:");
        List<GKInstance> diseaseList = new ArrayList<GKInstance>(diseaseToNormal.keySet());
        InstanceUtilities.sortInstances(diseaseList);
        for (GKInstance diseaseInst : diseaseList) {
            GKInstance normal = diseaseToNormal.get(diseaseInst);
            System.out.println(diseaseInst + " -> " + normal);
        }
        System.out.println("Total mapping generated: " + diseaseToNormal.size());
//        if (true)
//            return;
        // Start re-organization
        boolean isTransactionSupported = dba.supportsTransactions();
        try {
            if (isTransactionSupported)
                dba.startTransaction();
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba, 
                                                                   ScriptUtilities.GUANMING_WU_DB_ID,
                                                                   true);
            for (GKInstance diseaseInst : diseaseList) {
                GKInstance normalInst = diseaseToNormal.get(diseaseInst);
                List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                                             diseaseInst);
                dba.updateInstanceAttribute(normalInst,
                                            ReactomeJavaConstants.hasEvent);
                ScriptUtilities.addIEToModified(normalInst,
                                                defaultIE,
                                                dba);
            }

            // Delete re-organized disease pathways from top-most disease pathway.
            List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            for (Iterator<GKInstance> it = hasEvent.iterator(); it.hasNext();) {
                GKInstance inst = it.next();
                String displayName = inst.getDisplayName();
                if (displayName.equals("Infectious disease"))
                    continue;
                it.remove();
            }
            dba.updateInstanceAttribute(disease, ReactomeJavaConstants.hasEvent);
            ScriptUtilities.addIEToModified(disease, defaultIE, dba);
            if (isTransactionSupported)
                dba.commit();
        }
        catch(Exception e) {
            if (isTransactionSupported)
                dba.rollback();
            e.printStackTrace();
        }
    }
    
    /**
     * This class is used to generate a mapping from disease pathways to normal pathways.
     * @throws Exception
     */
    private Map<GKInstance, GKInstance> generateDiseasePathwaysToNormalMappping(MySQLAdaptor dba) throws Exception {
        Map<GKInstance, GKInstance> diseaseToNormal = new HashMap<GKInstance, GKInstance>();
        Map<String, Long> diseaseNameToNormalDBID = getManualMapping();
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                                                                ReactomeJavaConstants._displayName,
                                                                "=",
                                                                "Disease");
        if (c.size() > 1)
            throw new IllegalStateException("More than one Disease pathway!");
        GKInstance disease = c.iterator().next();
        List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        for (GKInstance inst : hasEvent) {
            String displayName = inst.getDisplayName();
            if (displayName.equals("Infectious disease"))
                continue;
            Long normalDbId = diseaseNameToNormalDBID.get(displayName);
            if (normalDbId != null) {
                GKInstance normal = dba.fetchInstance(normalDbId);
                diseaseToNormal.put(inst, normal);
                continue;
            }
            int index = displayName.indexOf("of");
            String normalName = displayName.substring(index + 2).trim();
            c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                                             ReactomeJavaConstants._displayName,
                                             "=",
                                             normalName);
            if (c.size() > 1) {
                System.out.println("More than one pathway: " + normalName);
                continue;
            }
            else if (c.size() == 0) {
                System.out.println("Cannot find pathway: " + normalName);
                continue;
            }
            GKInstance normal = c.iterator().next();
            diseaseToNormal.put(inst, normal);
        }
        
        Set<GKInstance> topDiseasePathways = new HashSet<GKInstance>(diseaseToNormal.keySet());
        // Recursively generate the mapping for individual disease pathways.
        for (GKInstance diseaseInst : topDiseasePathways) {
            hasEvent = diseaseInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            for (GKInstance subDisease : hasEvent) {
                generateDiseasePathwaysToNormalMappping(subDisease,
                                                        diseaseToNormal,
                                                        diseaseNameToNormalDBID);
            }
        }
        
        cleanUpMappings(diseaseToNormal);
        
        return diseaseToNormal;
    }
    
    /**
     * If an ancestor pathway has the same mapping as its descendent, no need for listing
     * the mapping for descendant pathway.
     * @param diseaseToNormal
     */
    private void cleanUpMappings(Map<GKInstance, GKInstance> diseaseToNormal) throws Exception {
        Set<GKInstance> toBeRemoved = new HashSet<GKInstance>();
        for (GKInstance disease : diseaseToNormal.keySet()) {
            GKInstance normal = diseaseToNormal.get(disease);
            Set<GKInstance> contained = InstanceUtilities.getContainedEvents(disease);
            for (GKInstance tmp : contained) {
                GKInstance tmpNormal = diseaseToNormal.get(tmp);
                if (tmpNormal == null)
                    continue;
                if (tmpNormal == normal)
                    toBeRemoved.add(tmp);
            }
        }
//        System.out.println("To be removed: " + toBeRemoved.size());
        diseaseToNormal.keySet().removeAll(toBeRemoved);
    }
    
    /**
     * A recursive method to generate all mappings from sub-pathways.
     * @param diseasePathway
     * @param diseaseToNormal
     * @throws Exception
     */
    private void generateDiseasePathwaysToNormalMappping(GKInstance diseasePathway,
                                                         Map<GKInstance, GKInstance> diseaseToNormal,
                                                         Map<String, Long> diseaseNameToDBId) throws Exception {
//        System.out.println("Checking " + diseasePathway);
        Long normalId = diseaseNameToDBId.get(diseasePathway.getDisplayName());
        if (normalId != null) {
            GKInstance normal = diseasePathway.getDbAdaptor().fetchInstance(normalId);
            diseaseToNormal.put(diseasePathway, normal);
            List<GKInstance> hasEvent = diseasePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            for (GKInstance sub : hasEvent)
                generateDiseasePathwaysToNormalMappping(sub, 
                                                        diseaseToNormal,
                                                        diseaseNameToDBId);
            return;
        }
        // Check if diseasePathway has a normalPathway
        GKInstance normalPathway = (GKInstance) diseasePathway.getAttributeValue(ReactomeJavaConstants.normalPathway);
        if (normalPathway != null) {
            diseaseToNormal.put(diseasePathway, normalPathway);
            return;
        }
        // Let's see if all sub-pathways have the same normal pathways attached
        Set<GKInstance> normalPathways = new HashSet<GKInstance>();
        List<GKInstance> hasEvent = diseasePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        for (GKInstance subDisease : hasEvent) {
            if (subDisease.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            normalPathway = (GKInstance) subDisease.getAttributeValue(ReactomeJavaConstants.normalPathway);
            normalPathways.add(normalPathway);
            if (normalPathway == null) {
                // Want to look for sub-pathways
                generateDiseasePathwaysToNormalMappping(subDisease, 
                                                        diseaseToNormal, 
                                                        diseaseNameToDBId);
            }
        }
        if (normalPathways.size() == 0) {
            // [Pathway:2978092] Abnormal conversion of 2-oxoglutarate to 2-hydroxyglutarate
            // has no-normal, and should be escaped!
            System.err.println("Cannot assign " + diseasePathway + ": no normal pathway!");
            return;
        }
        if (normalPathways.size() > 1) {
            System.err.println(diseasePathway + ": more than one normal pathways! " + normalPathways);
            return;
        }
        diseaseToNormal.put(diseasePathway, normalPathways.iterator().next());
    }
    
//    /**
//     * Get a shared common container pathway for the passed pathways.
//     * @param pathways
//     * @return
//     * @throws Exception
//     */
//    private GKInstance getCommonAncestorPathway(Collection<GKInstance> pathways) throws Exception {
//        // Remove null and see if there is only one pathway left
//        for (Iterator<GKInstance> it = pathways.iterator(); it.hasNext();) {
//            GKInstance tmp = it.next();
//            if (tmp == null)
//                it.remove();
//        }
//        if (pathways.size() == 1)
//            return pathways.iterator().next();
////        Map<GKInstance, List<GKInstance>> pathwayToPath = new HashMap<GKInstance, List<GKInstance>>();
////        for (GKInstance pathway : pathways) {
////            if (pathway == null)
////                return null;
////            List<GKInstance> path = new ArrayList<GKInstance>();
////            path.add(pathway);
////            Set<GKInstance> current = new HashSet<GKInstance>();
////            current.add(pathway);
////            Set<GKInstance> next = new HashSet<GKInstance>();
////            while (true) {
////                for (GKInstance tmp : current) {
////                    Collection<GKInstance> container = tmp.getReferers(ReactomeJavaConstants.hasEvent);
////                    if (container.size() == 0)
////                        break;
////                    // Assume there is only one to make it simple
////                    GKInstance container1 = container.iterator().next();
////                    path.add(container1);
////                    next.add(container1);
////                }
////                if (next.size() == 0)
////                    break;
////                current.clear();
////                next.addAll(current);
////                next.clear();
////            }
////            pathwayToPath.put(pathway, path);
////        }
////        List<List<GKInstance>> pathList = new ArrayList<List<GKInstance>>(pathwayToPath.values());
////        List<GKInstance> path0 = pathList.get(0);
////        for (int i = 1; i < pathList.size(); i ++) {
////            List<GKInstance> path1 = pathList.get(i);
////            Set<GKInstance> removed = new HashSet<GKInstance>();
////            for (int j = 0; j < path0.size(); j++) {
////                GKInstance tmp = path0.get(j);
////                if (!path1.contains(tmp)) {
////                    removed.add(tmp);
////                }
////            }
////            path0.removeAll(removed);
////        }
////        if (path0.size() > 0)
////            return path0.get(0);
//        return null;
//    }
    
    private Map<String, Long> getManualMapping() {
        Map<String, Long> diseaseNameToNormalDBID = new HashMap<String, Long>();
        diseaseNameToNormalDBID.put("Diseases of metabolism", 1430728L);
        diseaseNameToNormalDBID.put("Disorders of transmembrane transporters", 382551L);
        // This disease pathway contains two parts: one is related to PTM, and another
        // to metabolism of carbohydrate. For the time being, it is attached to PTM.
        // But we may consider creating a new Glycosylation pathway?
        diseaseNameToNormalDBID.put("Diseases of glycosylation", 597592L);
        // 4 sub-disease pathways are mapped to metabolis of carbohydrate
        // 2 to Sialic acid metabolism.
        // The majority to 446203, Asparagine N-linked glycosylation
        diseaseNameToNormalDBID.put("Diseases associated with glycosylation precursor biosynthesis",
                                    446203L);
        // Several normal pathways are listed here
        diseaseNameToNormalDBID.put("ABC transporter disorders",
                                    382556L);
        // Normal pathways is pretty big and hasn't its own pathway diagram
        diseaseNameToNormalDBID.put("SLC transporter disorders",
                                    425407L);
        // GPCR ligand binding and Bile acid and bile salt metabolism are listed too
        diseaseNameToNormalDBID.put("Metabolic disorders of biological oxidation enzymes",
                                    211859L);
        diseaseNameToNormalDBID.put("Diseases associated with the TLR signaling cascade",
                                    168898L);
        // A little bit difficult with the algorithm. So just manually assigned it
        diseaseNameToNormalDBID.put("Glycogen storage diseases",
                                    71387L); // Mapped to "Metabolism of carbohydrates"
        diseaseNameToNormalDBID.put("Diseases associated with visual transduction",
                                    2187338L); // Mapped to "Visual phototransduction"
        
        // Normal pathway are buried too deep. Don't bother. Just use manual
        diseaseNameToNormalDBID.put("Signaling by TGF-beta Receptor Complex in Cancer",
                                    170834L);
        diseaseNameToNormalDBID.put("Defects in vitamin and cofactor metabolism",
                                    196849L);
        
        // Easy mapping
        diseaseNameToNormalDBID.put("Signaling by FGFR in disease",
                                    190236L);
        diseaseNameToNormalDBID.put("Signaling by WNT in cancer",
                                    195721L);
        diseaseNameToNormalDBID.put("Diseases of carbohydrate metabolism",
                                    71387L);
        return diseaseNameToNormalDBID;
    }
    
    @Test
    public void checkLinksFromDiseasePathway() throws Exception {
        MySQLAdaptor dba = getDBA();
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.FrontPage);
        GKInstance frontPage = c.iterator().next();
        // Get pathways contained by non-disease top-level pathways
        List<GKInstance> frontPageItem = frontPage.getAttributeValuesList(ReactomeJavaConstants.frontPageItem);
        Set<GKInstance> nonDiseaseEvents = new HashSet<GKInstance>();
        Set<GKInstance> diseaseEvents = new HashSet<GKInstance>();
        GKInstance disease = null;
        for (GKInstance topic : frontPageItem) {
            if (topic.getDisplayName().equals("Disease")) {
                diseaseEvents = InstanceUtilities.getContainedEvents(topic);
                diseaseEvents.add(topic);
                disease = topic;
                continue;
            }
            Set<GKInstance> set = InstanceUtilities.getContainedEvents(topic);
            nonDiseaseEvents.addAll(set);
            nonDiseaseEvents.add(topic);
        }
        System.out.println("Total non-disease events for human: " + nonDiseaseEvents.size());
        System.out.println("Total events in Disease: " + diseaseEvents.size());
        Set<GKInstance> linksFromDisease = new HashSet<GKInstance>();
        getLinksFromDisease(disease, linksFromDisease, nonDiseaseEvents);
        System.out.println("Total links from disease: " + linksFromDisease.size());
        for (GKInstance link : linksFromDisease) {
            System.out.println(link);
        }
    }
    
    /**
     * A recursive method to get links from the disease branch.
     * @param event
     * @param linksFromDisease
     * @throws Exception
     */
    private void getLinksFromDisease(GKInstance event,
                                     Set<GKInstance> linksFromDisease,
                                     Set<GKInstance> nonDiseaseEvents) throws Exception {
        if (nonDiseaseEvents.contains(event) || !event.getSchemClass().isa(ReactomeJavaConstants.Pathway))
            return; // There is no need to go down
        List<GKInstance> hasEvent = event.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        if (hasEvent == null || hasEvent.size() == 0)
            return;
        List<GKInstance> copy = new ArrayList<GKInstance>(hasEvent);
        copy.removeAll(nonDiseaseEvents);
        if (copy.size() == 0) {
            System.err.println(event + " has no disease event!");
            return;
        }
        for (GKInstance child : hasEvent) {
            if (nonDiseaseEvents.contains(child)) {
                linksFromDisease.add(child);
                continue;
            }
            getLinksFromDisease(child, linksFromDisease, nonDiseaseEvents);
        }
    }
    
    /**
     * Re-organize some disease pathways that have not fully curated in a hard-coded way.
     */
    @Test
    public void useNewNormatlPathwaySlotForUnfinishedPathways() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_120114_fireworks",
                                            "root",
                                            "macmysql01");
        // Get the disease pathway
        GKInstance disease = dba.fetchInstance(1643685L);
        // Get all disease pathway pathways
        Set<GKInstance> diseaseEvents = InstanceUtilities.getContainedEvents(disease);
        System.out.println("Total events in disease: " + diseaseEvents.size());
        // We want to focus normal pathways in these top-three pathways, which are linked into the disease hierarchy
        Long[] dbIds = new Long[] {
                1430728L, // Metabolism
                162582L, // Signal transduction
                392499L // Metabolism of proteins
        };
        int total = 0;
        Map<GKInstance, GKInstance> diseaseToNormal = new HashMap<GKInstance, GKInstance>();
        for (Long dbId : dbIds) {
            GKInstance normalPathway = dba.fetchInstance(dbId);
            Set<GKInstance> normalPathwayEvents = InstanceUtilities.getContainedEvents(normalPathway);
            System.out.println("Events in " + normalPathway + ": " + normalPathwayEvents.size());
            // Check if any child pathway is in the normal pathway
            for (GKInstance diseaseEvent : diseaseEvents) {
                if (!diseaseEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway) ||
                    normalPathwayEvents.contains(diseaseEvent)) // This should be a sub event. Don't need to consider it.
                    continue; // This is not a pathway
                // Exclude this
                if (diseaseEvent.getDBID().equals(451927L))
                    continue;
                List<GKInstance> hasEvent = diseaseEvent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                if (hasEvent == null || hasEvent.isEmpty())
                    continue;
                List<GKInstance> copy = new ArrayList<GKInstance>(hasEvent);
                copy.retainAll(normalPathwayEvents);
                // We want to use Pathways only
                for (Iterator<GKInstance> it = copy.iterator(); it.hasNext();) {
                    GKInstance event = it.next();
                    if (!event.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                        it.remove();
                }
                if (copy.size() > 0) {
                    System.out.println(diseaseEvent + "\t" + copy.size() + "\t" + copy);
                    total ++;
                    if (copy.size() == 1) {
                        diseaseToNormal.put(diseaseEvent, copy.iterator().next());
                    }
                }
            }
            System.out.println();   
        }
        System.out.println("Total patwhays that should be udpated: " + total);
        System.out.println("Size of map: " + diseaseToNormal.size());
//        if (true)
//            return;
        // Now we want to update
        try {
            dba.startTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true);
            for (GKInstance diseasePathway : diseaseToNormal.keySet()) {
                GKInstance normalPathway = diseaseToNormal.get(diseasePathway);
                useNormalPathwaySlot(diseasePathway, 
                                     normalPathway, 
                                     ie, 
                                     dba);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
        }
    }
    
    /**
     * Update database by using the new slot, normalPathway, for disease pathways.
     * @throws Exception
     */
    @Test
    public void useNewNormalPathwaySlot() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Get the disease pathway
        GKInstance disease = dba.fetchInstance(1643685L);
        List<GKInstance> otherDiseaes = disease.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent);
        List<GKInstance> allDiseaes = new ArrayList<GKInstance>(otherDiseaes);
        allDiseaes.add(0, disease);
        for (GKInstance disease1 : allDiseaes) {
            useNewNormalPathwaySlot(disease1, dba);
//            break;
        }
    }

    private void useNewNormalPathwaySlot(GKInstance disease, MySQLAdaptor dba) throws Exception {
        GKInstance species = (GKInstance) disease.getAttributeValue(ReactomeJavaConstants.species);
        System.out.println("\nSpecies: " + species.getDisplayName());
//        if (!species.getDisplayName().equals("Mus musculus"))
//            return;
        // A list of disease pathways to be updated.
        Set<GKInstance> toBeChanged = new HashSet<GKInstance>();
        getPathwaysForNormalPathwayUpdate(disease, toBeChanged);
        System.out.println("Total pathways to be changed: " + toBeChanged.size());
        List<GKInstance> list = new ArrayList<GKInstance>(toBeChanged);
        InstanceUtilities.sortInstances(list);
        Set<GKInstance> links = new HashSet<GKInstance>();
        for (GKInstance pathway : list) {
            GKInstance normalPathway = getNormalPathwayForDisease(pathway);
            System.out.println(pathway.getDBID() + "\t" + pathway.getDisplayName() + "\t" + normalPathway);
            links.add(normalPathway);
        }
        System.out.println("Total linked pathways outside of disease: " + links.size());
//        for (GKInstance inst : links)
//            System.out.println(inst);
//        if (true)
//            return;
        // Now for the update
        boolean isTransactionSupported = dba.supportsTransactions();
        try {
            if (isTransactionSupported)
                dba.startTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true);
            for (GKInstance pathway : toBeChanged) {
                useNewNormalPathwaySlot(pathway, ie, dba);
            }
            if (isTransactionSupported)
                dba.commit();
        }
        catch(Exception e) {
            if (isTransactionSupported)
                dba.rollback();
        }
    }

    private MySQLAdaptor getDBA() throws SQLException {
        if (this.dba != null)
            return this.dba;
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                                            "gk_current_ver51_new_schema",
//                                            "root",
//                                            "macmysql01");
//        MySQLAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
//                                            "test_gk_central_020915_wgm",
//                                            "authortool",
//                                            "T001test");
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            "test_slice_57",
                                            "root",
                                            "macmysql01");
        return dba;
    }
    
    public void setDBA(MySQLAdaptor dba) {
        this.dba = dba;
    }
    
    private void useNewNormalPathwaySlot(GKInstance pathway,
                                         GKInstance ie,
                                         MySQLAdaptor dba) throws Exception {
        GKInstance normalPathway = getNormalPathwayForDisease(pathway);
        useNormalPathwaySlot(pathway, 
                             normalPathway,
                             ie, 
                             dba);
    }

    private void useNormalPathwaySlot(GKInstance diseasePathway,
                                      GKInstance normalPathway, 
                                      GKInstance ie,
                                      MySQLAdaptor dba) throws Exception {
        if (normalPathway == null)
            throw new IllegalArgumentException(diseasePathway + " has no normal pathway!");
        diseasePathway.setAttributeValue(ReactomeJavaConstants.normalPathway, normalPathway);
        diseasePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, normalPathway);
        diseasePathway.getAttributeValue(ReactomeJavaConstants.modified);
        diseasePathway.addAttributeValue(ReactomeJavaConstants.modified, ie);
        dba.updateInstanceAttribute(diseasePathway, ReactomeJavaConstants.modified);
        dba.updateInstanceAttribute(diseasePathway, ReactomeJavaConstants.hasEvent);
        dba.updateInstanceAttribute(diseasePathway, ReactomeJavaConstants.normalPathway);
//        System.out.println(diseasePathway + " udpated!");
    }
    
    private GKInstance getNormalPathwayForDisease(GKInstance pathway) throws Exception {
        // Need to find the normal pathway
        Collection<GKInstance> diagrams = pathway.getReferers(ReactomeJavaConstants.representedPathway);
        if (diagrams == null || diagrams.size() != 1)
            return null;
        GKInstance diagram = diagrams.iterator().next();
        List<GKInstance> representedPathways = diagram.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
        // Get shared pathways
        List<GKInstance> hasEvents = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        List<GKInstance> shared = new ArrayList<GKInstance>(hasEvents);
        shared.retainAll(representedPathways);
        if (shared.size() > 0) { // May be shared with its sub-pathways. This may be an annotation error. See here: http://www.reactome.org/PathwayBrowser/#DIAGRAM=4839748&PATH=1643685,4791275
            // If there is only one. Use that one
            if (shared.size() == 1)
                return shared.get(0);
            for (GKInstance inst : shared) {
                if (inst.getAttributeValue(ReactomeJavaConstants.disease) == null)
                    return inst;
            }
            throw new IllegalArgumentException(pathway + " doesn't have a normal pathway!");
        }
        return null;
    }
    
    /**
     * If the passed Pathway instances and one of its normal pathway child share the same PathwayDiagram,
     * we should update this Pathway instance by using the new normalPahtway slot.
     * @param pathway
     * @param toBeChanged
     * @throws Exception
     */
    private void getPathwaysForNormalPathwayUpdate(GKInstance pathway, Set<GKInstance> toBeChanged) throws Exception {
        GKInstance normalPathway = getNormalPathwayForDisease(pathway);
        if (normalPathway != null) {
            toBeChanged.add(pathway);
//            return;
        }
        List<GKInstance> hasEvents = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
//        Collection<GKInstance> diagrams = pathway.getReferers(ReactomeJavaConstants.representedPathway);
//        if (diagrams != null && diagrams.size() == 1) {
//            GKInstance diagram = diagrams.iterator().next();
//            List<GKInstance> representedPathways = diagram.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
//            // Get shared pathways
//            List<GKInstance> shared = new ArrayList<GKInstance>(hasEvents);
//            shared.retainAll(representedPathways);
//            if (shared.size() == 1) {
//                toBeChanged.add(pathway);
//                return;
//            }
//        }
        for (GKInstance hasEvent : hasEvents) {
            if (!hasEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                continue;
            getPathwaysForNormalPathwayUpdate(hasEvent, toBeChanged);
        }
    }
    
}
