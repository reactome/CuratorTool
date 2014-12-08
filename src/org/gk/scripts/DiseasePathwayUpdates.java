/*
 * Created on Dec 1, 2014
 *
 */
package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    
    /**
     * Default constructor.
     */
    public DiseasePathwayUpdates() {
    }
    
    /**
     * Update database by using the new slot, normalPathway, for disease pathways.
     * @throws Exception
     */
    @Test
    public void useNewNormalPathwaySlot() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_120114_fireworks",
                                            "root",
                                            "macmysql01");
        // Get the disease pathway
        GKInstance disease = dba.fetchInstance(1643685L);
        // A list of disease pathways to be updated.
        Set<GKInstance> toBeChanged = new HashSet<GKInstance>();
        getPathwaysForNormalPathwayUpdate(disease, toBeChanged);
        System.out.println("Total pathways to be changed: " + toBeChanged.size());
        List<GKInstance> list = new ArrayList<GKInstance>(toBeChanged);
        InstanceUtilities.sortInstances(list);
        for (GKInstance pathway : list)
            System.out.println(pathway.getDBID() + "\t" + pathway.getDisplayName());
        // Now for the update
        try {
            dba.startTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true);
            for (GKInstance pathway : toBeChanged) {
                useNewNormalPathwaySlot(pathway, ie, dba);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
        }
    }
    
    private void useNewNormalPathwaySlot(GKInstance pathway,
                                         GKInstance ie,
                                         MySQLAdaptor dba) throws Exception {
        GKInstance normalPathway = getNormalPathwayForDisease(pathway);
        if (normalPathway == null)
            throw new IllegalArgumentException(pathway + " has no normal pathway!");
        pathway.setAttributeValue(ReactomeJavaConstants.normalPathway, normalPathway);
        pathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, normalPathway);
        pathway.getAttributeValue(ReactomeJavaConstants.modified);
        pathway.addAttributeValue(ReactomeJavaConstants.modified, ie);
        dba.updateInstanceAttribute(pathway, ReactomeJavaConstants.modified);
        dba.updateInstanceAttribute(pathway, ReactomeJavaConstants.hasEvent);
        dba.updateInstanceAttribute(pathway, ReactomeJavaConstants.normalPathway);
        System.out.println(pathway + " udpated!");
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
        if (shared.size() == 1) {
            return shared.get(0);
        }
        return null;
    }
    
    /**
     * If the passed Pathway instances and one of its normal pathway chilld share the same PathwayDiagram,
     * we should update this Pathway instance by using the new normalPahtway slot.
     * @param pathway
     * @param toBeChanged
     * @throws Exception
     */
    private void getPathwaysForNormalPathwayUpdate(GKInstance pathway, Set<GKInstance> toBeChanged) throws Exception {
        GKInstance normalPathway = getNormalPathwayForDisease(pathway);
        if (normalPathway != null) {
            toBeChanged.add(pathway);
            return;
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
