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
        MySQLAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
                                            "test_gk_central_020915_wgm",
                                            "authortool",
                                            "T001test");
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
