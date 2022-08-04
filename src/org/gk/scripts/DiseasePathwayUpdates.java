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
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * This class is used to handle disease related pathways updates.
 *
 * @author gwu
 */
@SuppressWarnings("unchecked")
public class DiseasePathwayUpdates {
    private PersistenceAdaptor dba;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("java -Xmx4G org.gk.scripts.DiseasePathwayUpdates dbHost dbName dbUser dbPwd use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            System.exit(0);
        }
        PersistenceAdaptor dba = null;
        boolean useNeo4J = Boolean.parseBoolean(args[4]);
        if (useNeo4J)
            dba = new Neo4JAdaptor(args[0],
                    args[1],
                    args[2],
                    args[3]);
        else
            dba = new MySQLAdaptor(args[0],
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
     * Revision based on Bijay feedbacks on results generated from V2.
     *
     * @throws Exception
     */
    @Test
    public void reOrganizeDiseaseEventsV3() throws Exception {
        PersistenceAdaptor dba = getDBA();

        GKInstance disease = getDiseasePathway(dba);

        Set<GKInstance> allDiseaseEvents = grepAllDiseaseEvents(disease);

        // Map disease reactions to normal reactions using the new diseaseReaction attribute.
        Map<GKInstance, Set<GKInstance>> normalReactionToDisease = new HashMap<>();
        createNormalReactionToDiseaseMap(allDiseaseEvents,
                normalReactionToDisease);
        // Assign disease reactions to normal reactions
        Set<GKInstance> modifiedNormalReactions = new HashSet<>();
        Set<GKInstance> toBeMovedAll = new HashSet<>();
        for (GKInstance normalReaction : normalReactionToDisease.keySet()) {
            Set<GKInstance> diseaseReactions = normalReactionToDisease.get(normalReaction);
            normalReaction.setAttributeValue("diseaseReaction", new ArrayList<GKInstance>(diseaseReactions));
            toBeMovedAll.addAll(diseaseReactions);
            modifiedNormalReactions.add(normalReaction);
        }
        System.out.println("\nToBeModifiedNormalReactions after assigning disease to normal reactions: " + modifiedNormalReactions.size());
        // Remove assigned disease reactions from pathways
        Set<GKInstance> modifiedDiseasePathways = new HashSet<>();
        modifyHasEventAttribute(allDiseaseEvents,
                toBeMovedAll,
                modifiedDiseasePathways);

        // Start handling GoF pathways

        // Map GOF pathways to normal pathways so that disease pathways can be 
        // moved to new places.
        Map<GKInstance, GKInstance> diseaseToNormal = new HashMap<>();
        List<GKInstance> unmappedEvents = new ArrayList<>();
        createDiseasePathwayToNormalMapV3(dba,
                allDiseaseEvents,
                diseaseToNormal,
                unmappedEvents);

        // Get the set of disease pathways to be re-mapped: pathways have ReactionlikeEvents should be remapped
        Set<GKInstance> toBeRemappedPathways = getPathwaysToBeMoved(allDiseaseEvents);
        System.out.println("\nToBeRemappedPathways: " + toBeRemappedPathways.size());
        // This map is used to do actual mapping
        Map<GKInstance, GKInstance> diseasePathwayToNormal = new HashMap<>();
        for (GKInstance diseaseEvent : diseaseToNormal.keySet()) {
            if (!diseaseEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                continue;
            if (toBeRemappedPathways.contains(diseaseEvent)) {
                diseasePathwayToNormal.put(diseaseEvent, diseaseToNormal.get(diseaseEvent));
                continue;
            }
            // Or higher level
            Set<GKInstance> contained = InstanceUtilities.getContainedEvents(diseaseEvent);
            contained.retainAll(toBeRemappedPathways);
            if (contained.size() > 0) {
                diseasePathwayToNormal.put(diseaseEvent, diseaseToNormal.get(diseaseEvent));
            }
        }
        // However, we should remove all lower-level pathways
        while (true) {
            int preSize = diseasePathwayToNormal.size();
            Set<GKInstance> toBeRemoved = new HashSet<>();
            for (GKInstance diseasePathway : diseasePathwayToNormal.keySet()) {
                toBeRemoved.addAll(InstanceUtilities.getContainedEvents(diseasePathway));
            }
            diseasePathwayToNormal.keySet().removeAll(toBeRemoved);
            if (preSize == diseasePathwayToNormal.size())
                break; // Stop here since nothing has changed.
        }
        for (GKInstance diseasePathway : diseasePathwayToNormal.keySet()) {
            toBeMovedAll.addAll(InstanceUtilities.getContainedEvents(diseasePathway));
            toBeMovedAll.add(diseasePathway);
        }
        allDiseaseEvents.removeAll(toBeMovedAll);
        modifyHasEventAttribute(allDiseaseEvents,
                toBeMovedAll,
                modifiedDiseasePathways);
        System.out.println("\nTo be remapped disease pathways: " + diseasePathwayToNormal.size());
        for (GKInstance diseasePathway : diseasePathwayToNormal.keySet()) {
            GKInstance normalPathway = diseasePathwayToNormal.get(diseasePathway);
            System.out.println(diseasePathway + " -> " +
                    normalPathway + ": " +
                    diseasePathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent));
        }

        // Check disease events that should be kept in the disease hierarchy for manual checking
        Set<GKInstance> keptPathways = new HashSet<>();
        grepKeptPathways(allDiseaseEvents,
                unmappedEvents,
                toBeMovedAll,
                keptPathways);
        // Modify the original disease hierarchy
        keptPathways.addAll(unmappedEvents); // Unmapped pathways should be kept
        modifyKeptPathwaysHasEvents(modifiedDiseasePathways,
                keptPathways);

        // Get pathways to be deleted
        allDiseaseEvents.removeAll(toBeMovedAll);
        allDiseaseEvents.removeAll(keptPathways);
        // Reactions should never be deleted
        for (Iterator<GKInstance> it = allDiseaseEvents.iterator(); it.hasNext(); ) {
            GKInstance inst = it.next();
            if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                it.remove();
        }
        System.out.println("\nTotal pathways to be deleted: " + allDiseaseEvents.size());
        for (GKInstance inst : allDiseaseEvents)
            System.out.println(inst);
//        if (true)
//            return;
        // Start re-organization
        if (dba instanceof Neo4JAdaptor) {
            // Neo4J
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, tx);
                // Perform deletion first
                System.out.println("\nThe following pathways have been deleted: ");
                for (GKInstance inst : allDiseaseEvents) {
                    ((Neo4JAdaptor) dba).deleteInstance(inst, tx);
                    System.out.println(inst);
                }
                System.out.println("Total: " + allDiseaseEvents.size());

                System.out.println("\nThe following reactions have added disease reactions to their diseaseReaction slot:");
                for (GKInstance normalReaction : modifiedNormalReactions) {
                    dba.updateInstanceAttribute(normalReaction,
                            "diseaseReaction", tx);
                    ScriptUtilities.addIEToModified(normalReaction,
                            defaultIE,
                            dba, tx);
                    System.out.println(normalReaction);
                }
                System.out.println("Total: " + modifiedNormalReactions.size());

                System.out.println("\nThe following disease pathways have been attached to normal pathways:");
                for (GKInstance diseaseInst : diseasePathwayToNormal.keySet()) {
                    GKInstance normalInst = diseasePathwayToNormal.get(diseaseInst);
                    List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                    normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                            diseaseInst);
                    dba.updateInstanceAttribute(normalInst,
                            ReactomeJavaConstants.hasEvent, tx);
                    ScriptUtilities.addIEToModified(normalInst,
                            defaultIE,
                            dba, tx);
                    System.out.println(diseaseInst + " -> " + normalInst);
                }
                System.out.println("Total: " + diseasePathwayToNormal.size());

                System.out.println("\nThe following diease pathways have their hasEvent slots changed:");
                int count = 0;
                // Don't forget the top-level pathway
                List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                hasEvent.removeAll(allDiseaseEvents);
                disease.setAttributeValue(ReactomeJavaConstants.hasEvent, hasEvent);
                modifiedDiseasePathways.add(disease);
                for (GKInstance diseaseInst : modifiedDiseasePathways) {
                    if (allDiseaseEvents.contains(diseaseInst))
                        continue;
                    dba.updateInstanceAttribute(diseaseInst, ReactomeJavaConstants.hasEvent, tx);
                    ScriptUtilities.addIEToModified(diseaseInst,
                            defaultIE,
                            dba, tx);
                    System.out.println(diseaseInst);
                    count++;
                }
                System.out.println("Total: " + count);
                // Just a sanity check
                System.out.println("\nCheck top disease pathways:");
                List<GKInstance> topDiseasePathways = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                for (GKInstance topDiseasePathway : topDiseasePathways)
                    System.out.println(topDiseasePathway);
                // Another check
                GKInstance pathway = dba.fetchInstance(2872314L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                pathway = dba.fetchInstance(3701007L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // MySQL
            boolean isTransactionSupported = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).startTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, null);
                // Perform deletion first
                System.out.println("\nThe following pathways have been deleted: ");
                for (GKInstance inst : allDiseaseEvents) {
                    ((MySQLAdaptor) dba).deleteInstance(inst);
                    System.out.println(inst);
                }
                System.out.println("Total: " + allDiseaseEvents.size());

                System.out.println("\nThe following reactions have added disease reactions to their diseaseReaction slot:");
                for (GKInstance normalReaction : modifiedNormalReactions) {
                    dba.updateInstanceAttribute(normalReaction,
                            "diseaseReaction", null);
                    ScriptUtilities.addIEToModified(normalReaction,
                            defaultIE,
                            dba, null);
                    System.out.println(normalReaction);
                }
                System.out.println("Total: " + modifiedNormalReactions.size());

                System.out.println("\nThe following disease pathways have been attached to normal pathways:");
                for (GKInstance diseaseInst : diseasePathwayToNormal.keySet()) {
                    GKInstance normalInst = diseasePathwayToNormal.get(diseaseInst);
                    List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                    normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                            diseaseInst);
                    dba.updateInstanceAttribute(normalInst,
                            ReactomeJavaConstants.hasEvent, null);
                    ScriptUtilities.addIEToModified(normalInst,
                            defaultIE,
                            dba, null);
                    System.out.println(diseaseInst + " -> " + normalInst);
                }
                System.out.println("Total: " + diseasePathwayToNormal.size());

                System.out.println("\nThe following diease pathways have their hasEvent slots changed:");
                int count = 0;
                // Don't forget the top-level pathway
                List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                hasEvent.removeAll(allDiseaseEvents);
                disease.setAttributeValue(ReactomeJavaConstants.hasEvent, hasEvent);
                modifiedDiseasePathways.add(disease);
                for (GKInstance diseaseInst : modifiedDiseasePathways) {
                    if (allDiseaseEvents.contains(diseaseInst))
                        continue;
                    dba.updateInstanceAttribute(diseaseInst, ReactomeJavaConstants.hasEvent, null);
                    ScriptUtilities.addIEToModified(diseaseInst,
                            defaultIE,
                            dba, null);
                    System.out.println(diseaseInst);
                    count ++;
                }
                System.out.println("Total: " + count);
                // Just a sanity check
                System.out.println("\nCheck top disease pathways:");
                List<GKInstance> topDiseasePathways = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                for (GKInstance topDiseasePathway : topDiseasePathways)
                    System.out.println(topDiseasePathway);
                // Another check
                GKInstance pathway = dba.fetchInstance(2872314L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                pathway = dba.fetchInstance(3701007L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).rollback();
                e.printStackTrace();
            }
        }
    }

    /**
     * This method is used to re-organize disease pathways as suggested by Bijay in document:
     * https://docs.google.com/document/d/1YxhvTqP1Lz2mOp5zaRHfKKs4Fyhn2Lx50W83TgtG9hY/edit.
     * The code here basically is modified from method {@link reOrganizeDiseasePathwaysToNormals()
     * reOrganizeDiseasePathwaysToNormals}.
     *
     * @throws Exception
     */
    @Test
    public void reOrganizeDiseaseEventsV2() throws Exception {
        PersistenceAdaptor dba = getDBA();

        GKInstance disease = getDiseasePathway(dba);

        Set<GKInstance> allDiseaseEvents = grepAllDiseaseEvents(disease);

        Map<GKInstance, GKInstance> diseaseToNormal = new HashMap<>();
        List<GKInstance> unmappedEvents = new ArrayList<>();
        createDiseasePathwayToNormalMap(dba,
                allDiseaseEvents,
                diseaseToNormal,
                unmappedEvents);

        Map<GKInstance, Set<GKInstance>> normalReactionToDisease = new HashMap<>();
        createNormalReactionToDiseaseMap(allDiseaseEvents,
                normalReactionToDisease);

        // Assign disease reactions to normal reactions
        Set<GKInstance> modifiedNormalReactions = new HashSet<>();
        Set<GKInstance> toBeMovedAll = new HashSet<>();
        for (GKInstance normalReaction : normalReactionToDisease.keySet()) {
            Set<GKInstance> diseaseReactions = normalReactionToDisease.get(normalReaction);
            normalReaction.setAttributeValue("diseaseReaction", new ArrayList<GKInstance>(diseaseReactions));
            toBeMovedAll.addAll(diseaseReactions);
            modifiedNormalReactions.add(normalReaction);
        }
        System.out.println("\nToBeModifiedNormalReactions after assigning disease to normal reactions: " + modifiedNormalReactions.size());
        // Remove assigned disease reactions from pathways
        Set<GKInstance> modifiedDiseasePathways = new HashSet<>();
        modifyHasEventAttribute(allDiseaseEvents,
                toBeMovedAll,
                modifiedDiseasePathways);

        // Get the set of disease pathways to be re-mapped: pathways have ReactionlikeEvents should be remapped
        Set<GKInstance> toBeRemappedPathways = getPathwaysToBeMoved(allDiseaseEvents);
        System.out.println("\nToBeRemappedPathways: " + toBeRemappedPathways.size());
        Map<GKInstance, GKInstance> diseasePathwayToNormal = new HashMap<>();
        for (GKInstance inst : toBeRemappedPathways) {
            if (unmappedEvents.contains(inst))
                System.out.println(inst + " cannot be mapped!");
            else {
                GKInstance normalPathway = getNormalPathway(inst, diseaseToNormal);
                if (normalPathway != null) {
                    diseasePathwayToNormal.put(inst, normalPathway);
                    toBeMovedAll.addAll(InstanceUtilities.getContainedEvents(inst));
                    toBeMovedAll.add(inst);
                }
            }
        }
        modifyHasEventAttribute(allDiseaseEvents,
                diseasePathwayToNormal.keySet(),
                modifiedDiseasePathways);

        // Check disease events that should be kept in the disease hierarchy for manual checking
        Set<GKInstance> keptPathways = new HashSet<>();
        grepKeptPathways(allDiseaseEvents,
                unmappedEvents,
                toBeMovedAll,
                keptPathways);
        // Modify the original disease hierarchy
        keptPathways.addAll(unmappedEvents); // Unmapped pathways should be kept
        modifyKeptPathwaysHasEvents(modifiedDiseasePathways,
                keptPathways);

        // Get pathways to be deleted
        allDiseaseEvents.removeAll(toBeMovedAll);
        allDiseaseEvents.removeAll(keptPathways);
        // Reactions should never be deleted
        for (Iterator<GKInstance> it = allDiseaseEvents.iterator(); it.hasNext(); ) {
            GKInstance inst = it.next();
            if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                it.remove();
        }
        System.out.println("\nTotal pathways to be deleted: " + allDiseaseEvents.size());
        for (GKInstance inst : allDiseaseEvents)
            System.out.println(inst);
        if (true)
            return;
        // Start re-organization
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, tx);
                // Perform deletion first
                System.out.println("\nThe following pathways have been deleted: ");
                for (GKInstance inst : allDiseaseEvents) {
                    ((Neo4JAdaptor) dba).deleteInstance(inst, tx);
                    System.out.println(inst);
                }
                System.out.println("Total: " + allDiseaseEvents.size());

                System.out.println("\nThe following reactions have added disease reactions to their diseaseReaction slot:");
                for (GKInstance normalReaction : modifiedNormalReactions) {
                    dba.updateInstanceAttribute(normalReaction,
                            "diseaseReaction", tx);
                    ScriptUtilities.addIEToModified(normalReaction,
                            defaultIE,
                            dba, tx);
                    System.out.println(normalReaction);
                }
                System.out.println("Total: " + modifiedNormalReactions.size());

                System.out.println("\nThe following disease pathways have been attached to normal pathways:");
                for (GKInstance diseaseInst : diseasePathwayToNormal.keySet()) {
                    GKInstance normalInst = diseasePathwayToNormal.get(diseaseInst);
                    List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                    normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                            diseaseInst);
                    dba.updateInstanceAttribute(normalInst,
                            ReactomeJavaConstants.hasEvent, tx);
                    ScriptUtilities.addIEToModified(normalInst,
                            defaultIE,
                            dba, tx);
                    System.out.println(diseaseInst + " -> " + normalInst);
                }
                System.out.println("Total: " + diseasePathwayToNormal.size());

                System.out.println("\nThe following diease pathways have their hasEvent slots changed:");
                int count = 0;
                // Don't forget the top-level pathway
                List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                hasEvent.removeAll(allDiseaseEvents);
                disease.setAttributeValue(ReactomeJavaConstants.hasEvent, hasEvent);
                modifiedDiseasePathways.add(disease);
                for (GKInstance diseaseInst : modifiedDiseasePathways) {
                    if (allDiseaseEvents.contains(diseaseInst))
                        continue;
                    dba.updateInstanceAttribute(diseaseInst, ReactomeJavaConstants.hasEvent, tx);
                    ScriptUtilities.addIEToModified(diseaseInst,
                            defaultIE,
                            dba, tx);
                    System.out.println(diseaseInst);
                    count++;
                }
                System.out.println("Total: " + count);
                // Just a sanity check
                System.out.println("\nCheck top disease pathways:");
                List<GKInstance> topDiseasePathways = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                for (GKInstance topDiseasePathway : topDiseasePathways)
                    System.out.println(topDiseasePathway);
                // Another check
                GKInstance pathway = dba.fetchInstance(2872314L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                pathway = dba.fetchInstance(3701007L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // MySQL
            boolean isTransactionSupported = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).startTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, null);
                // Perform deletion first
                System.out.println("\nThe following pathways have been deleted: ");
                for (GKInstance inst : allDiseaseEvents) {
                    ((MySQLAdaptor) dba).deleteInstance(inst);
                    System.out.println(inst);
                }
                System.out.println("Total: " + allDiseaseEvents.size());

                System.out.println("\nThe following reactions have added disease reactions to their diseaseReaction slot:");
                for (GKInstance normalReaction : modifiedNormalReactions) {
                    dba.updateInstanceAttribute(normalReaction,
                            "diseaseReaction", null);
                    ScriptUtilities.addIEToModified(normalReaction,
                            defaultIE,
                            dba, null);
                    System.out.println(normalReaction);
                }
                System.out.println("Total: " + modifiedNormalReactions.size());

                System.out.println("\nThe following disease pathways have been attached to normal pathways:");
                for (GKInstance diseaseInst : diseasePathwayToNormal.keySet()) {
                    GKInstance normalInst = diseasePathwayToNormal.get(diseaseInst);
                    List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                    normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                            diseaseInst);
                    dba.updateInstanceAttribute(normalInst,
                            ReactomeJavaConstants.hasEvent, null);
                    ScriptUtilities.addIEToModified(normalInst,
                            defaultIE,
                            dba, null);
                    System.out.println(diseaseInst + " -> " + normalInst);
                }
                System.out.println("Total: " + diseasePathwayToNormal.size());

                System.out.println("\nThe following diease pathways have their hasEvent slots changed:");
                int count = 0;
                // Don't forget the top-level pathway
                List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                hasEvent.removeAll(allDiseaseEvents);
                disease.setAttributeValue(ReactomeJavaConstants.hasEvent, hasEvent);
                modifiedDiseasePathways.add(disease);
                for (GKInstance diseaseInst : modifiedDiseasePathways) {
                    if (allDiseaseEvents.contains(diseaseInst))
                        continue;
                    dba.updateInstanceAttribute(diseaseInst, ReactomeJavaConstants.hasEvent, null);
                    ScriptUtilities.addIEToModified(diseaseInst,
                            defaultIE,
                            dba, null);
                    System.out.println(diseaseInst);
                    count ++;
                }
                System.out.println("Total: " + count);
                // Just a sanity check
                System.out.println("\nCheck top disease pathways:");
                List<GKInstance> topDiseasePathways = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                for (GKInstance topDiseasePathway : topDiseasePathways)
                    System.out.println(topDiseasePathway);
                // Another check
                GKInstance pathway = dba.fetchInstance(2872314L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                pathway = dba.fetchInstance(3701007L);
                hasEvent = pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                System.out.println(pathway + ": " + hasEvent);
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).rollback();
                e.printStackTrace();
            }
        }
    }

    private Set<GKInstance> grepAllDiseaseEvents(GKInstance disease) throws InvalidAttributeException, Exception {
        Set<GKInstance> allDiseaseEvents = new HashSet<>();
        List<GKInstance> topDiseaseEvents = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        for (GKInstance topDiseaseEvent : topDiseaseEvents) {
            if (topDiseaseEvent.getDisplayName().equals("Infectious disease"))
                continue;
            allDiseaseEvents.addAll(InstanceUtilities.getContainedEvents(topDiseaseEvent));
            allDiseaseEvents.add(topDiseaseEvent);
        }
        // Perform a filtering to remove all normal events
        for (Iterator<GKInstance> it = allDiseaseEvents.iterator(); it.hasNext(); ) {
            GKInstance event = it.next();
            GKInstance diseaseValue = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.disease);
            if (diseaseValue == null)
                it.remove();
        }
        System.out.println("Total disease events: " + allDiseaseEvents.size());
        return allDiseaseEvents;
    }

    private GKInstance getDiseasePathway(PersistenceAdaptor dba) throws Exception {
        // Remove disease pathways from disease
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                ReactomeJavaConstants._displayName,
                "=",
                "Disease");
        if (c.size() > 1)
            throw new IllegalStateException("More than one Disease pathway!");
        GKInstance disease = c.iterator().next();
        return disease;
    }

    private void modifyKeptPathwaysHasEvents(Set<GKInstance> modifiedDiseasePathways,
                                             Set<GKInstance> keptPathways)
            throws Exception {
        System.out.println("\nModifying hasEvent for kept pathways:");
        int count = 0;
        for (GKInstance diseaseEvent : keptPathways) {
            List<GKInstance> hasEvent = diseaseEvent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            boolean hasChanged = false;
            for (Iterator<GKInstance> it = hasEvent.iterator(); it.hasNext(); ) {
                GKInstance inst = it.next();
                if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                    continue;
                if (keptPathways.contains(inst))
                    continue;
                it.remove();
                hasChanged = true;
            }
            if (hasChanged) {
                diseaseEvent.setAttributeValue(ReactomeJavaConstants.hasEvent, hasEvent);
                modifiedDiseasePathways.add(diseaseEvent);
                System.out.println(diseaseEvent);
                count++;
            }
        }
        System.out.println("Total: " + count);
    }

    private void grepKeptPathways(Set<GKInstance> allDiseaseEvents,
                                  List<GKInstance> unmappedEvents,
                                  Set<GKInstance> toBeMovedAll,
                                  Set<GKInstance> keptPathways) {
        System.out.println("\nPathways to be kept in the disease hierarchy:");
        int count = 0;
        for (GKInstance inst : allDiseaseEvents) {
            if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            if (toBeMovedAll.contains(inst))
                continue; // No need to keep
            Set<GKInstance> contained = InstanceUtilities.getContainedEvents(inst);
            boolean isKept = false;
            for (GKInstance inst1 : contained) {
                if (inst1.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    isKept = true;
                    continue;
                }
            }
            if (!isKept) {
                contained.retainAll(unmappedEvents);
                if (contained.size() > 0)
                    isKept = true;
            }
            if (isKept) {
                System.out.println(inst);
                keptPathways.add(inst);
                count++;
            }
        }
        System.out.println("Total: " + count);
    }

    private Set<GKInstance> getPathwaysToBeMoved(Set<GKInstance> allDiseaseEvents) throws Exception {
        Set<GKInstance> toBeRemappedPathways = new HashSet<>();
        for (GKInstance diseaseEvent : allDiseaseEvents) {
            if (diseaseEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            List<GKInstance> hasEvent = diseaseEvent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            for (GKInstance inst : hasEvent) {
                if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    toBeRemappedPathways.add(diseaseEvent);
                    break;
                }
            }
        }
        return toBeRemappedPathways;
    }

    private void modifyHasEventAttribute(Set<GKInstance> allDiseaseEvents,
                                         Set<GKInstance> toBeMoved,
                                         Set<GKInstance> modifiedDiseasePathways) throws Exception {
        for (GKInstance diseaseEvent : allDiseaseEvents) {
            if (diseaseEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            List<GKInstance> hasEvent = diseaseEvent.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            if (hasEvent.removeAll(toBeMoved)) {
                // Filter out normal pathways
                for (Iterator<GKInstance> it = hasEvent.iterator(); it.hasNext(); ) {
                    GKInstance inst = it.next();
                    if (inst.getSchemClass().isa(ReactomeJavaConstants.Pathway) &&
                            (inst.getAttributeValue(ReactomeJavaConstants.disease) == null))
                        it.remove();
                }
                diseaseEvent.setAttributeValue(ReactomeJavaConstants.hasEvent, hasEvent);
                modifiedDiseasePathways.add(diseaseEvent);
            }
        }
    }

    private GKInstance getNormalPathway(GKInstance pathway,
                                        Map<GKInstance, GKInstance> diseaseToNormal) throws Exception {
        GKInstance normalPathway = diseaseToNormal.get(pathway);
        if (normalPathway != null)
            return normalPathway;
        // Search for one level up only
        Collection<GKInstance> referrers = pathway.getReferers(ReactomeJavaConstants.hasEvent);
        if (referrers == null || referrers.size() == 0)
            return null;
        GKInstance container = referrers.iterator().next();
        return getNormalPathway(container, diseaseToNormal);
    }

    private void createNormalReactionToDiseaseMap(Set<GKInstance> allDiseaseEvents,
                                                  Map<GKInstance, Set<GKInstance>> normalReactionToDisease) throws Exception {
        for (GKInstance diseaseEvent : allDiseaseEvents) {
            if (diseaseEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                continue;
            // normalReaction may have multiple values
            List<GKInstance> normalReactions = diseaseEvent.getAttributeValuesList(ReactomeJavaConstants.normalReaction);
            if (normalReactions != null && normalReactions.size() > 0) {
                normalReactions.forEach(normalReaction -> {
                    normalReactionToDisease.compute(normalReaction, (key, set) -> {
                        if (set == null)
                            set = new HashSet<>();
                        set.add(diseaseEvent);
                        return set;
                    });
                });
            }
        }
        System.out.println("\nTotal normal reactions to be modified: " + normalReactionToDisease.size());
        System.out.println("Normal reactions have more than one disease reaction: ");
        int count = 0;
        System.out.println("#NormalReaction\tNumberOfDiseaseReactions");
        for (GKInstance normal : normalReactionToDisease.keySet()) {
            Set<GKInstance> diseaseReactions = normalReactionToDisease.get(normal);
            if (diseaseReactions.size() > 1) {
                System.out.println(normal + "\t" + diseaseReactions.size());
                count++;
            }
        }
        System.out.println("Total number: " + count);
    }

    private void createDiseasePathwayToNormalMapV3(PersistenceAdaptor dba,
                                                   Set<GKInstance> allDiseaseEvents,
                                                   Map<GKInstance, GKInstance> diseaseToNormal,
                                                   List<GKInstance> unmappedEvents) throws Exception {
        for (GKInstance event : allDiseaseEvents) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            GKInstance normal = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.normalPathway);
            if (normal != null)
                diseaseToNormal.put(event, normal);
            else
                unmappedEvents.add(event);
        }
        // Check all mappings
        System.out.println("Mapped Events: " + diseaseToNormal.size());
        List<GKInstance> diseaseList = new ArrayList<GKInstance>(diseaseToNormal.keySet());
        InstanceUtilities.sortInstances(diseaseList);
        for (GKInstance diseaseInst : diseaseList) {
            GKInstance normal = diseaseToNormal.get(diseaseInst);
            System.out.println(diseaseInst + " -> " + normal);
        }
        InstanceUtilities.sortInstances(unmappedEvents);
        System.out.println("\nUnmapped Events: " + unmappedEvents.size());
        for (GKInstance event : unmappedEvents) {
            System.out.println(event);
        }

        // Do some clean up
        // If container pathways have covered, contained pathways should not be considered
        for (GKInstance diseaseInst : diseaseList) {
            Set<GKInstance> containedEvents1 = InstanceUtilities.getContainedEvents(diseaseInst);
            unmappedEvents.removeAll(containedEvents1);
        }
        System.out.println("\nAfter removing events covered by container events: " + unmappedEvents.size());
        for (GKInstance diseaseInst : unmappedEvents) {
            System.out.println(diseaseInst);
        }
        // Perform a recursive mapping based on contained information until nothing can be done
        while (true) {
            int preSize = unmappedEvents.size();
            // If contained pathways have covered
            for (Iterator<GKInstance> it = unmappedEvents.iterator(); it.hasNext(); ) {
                GKInstance diseaseInst = it.next();
                // We will check only the first level hasEvent values
                List<GKInstance> containedEvents1 = diseaseInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                // If all contained events have the same normal pathway, map this contained to
                // the normal pathway
                GKInstance foundNormal = null;
                for (GKInstance containedEvent : containedEvents1) {
                    GKInstance currentNormal = diseaseToNormal.get(containedEvent);
                    if (currentNormal == null) {
                        foundNormal = null;
                        break;
                    }
                    if (foundNormal != null && foundNormal != currentNormal) {
                        foundNormal = null;
                        break;
                    }
                    foundNormal = currentNormal;
                }
                if (foundNormal != null) {
                    diseaseToNormal.put(diseaseInst, foundNormal);
                    it.remove();
                }
            }
            if (preSize == unmappedEvents.size())
                break;
        }
        System.out.println("\nAfter removing events covered by contained events: " + unmappedEvents.size());
        for (GKInstance diseaseInst : unmappedEvents) {
            System.out.println(diseaseInst);
        }
        // Special cases with normal pathways listed still: Remove 3700989 (Transcriptional Regulation by TP53)
        Long[] dbIds = new Long[]{3700989L, 166658L, 446652L};
        for (Long dbId : dbIds) {
            GKInstance tp53 = dba.fetchInstance(dbId);
            unmappedEvents.remove(tp53);
            unmappedEvents.removeAll(InstanceUtilities.getContainedEvents(tp53));
        }
        System.out.println("\nAfter removing normal pathways listed in disease events: " + unmappedEvents.size());
        for (GKInstance diseaseInst : unmappedEvents) {
            System.out.println(diseaseInst);
        }
    }

    private void createDiseasePathwayToNormalMap(PersistenceAdaptor dba,
                                                 Set<GKInstance> allDiseaseEvents,
                                                 Map<GKInstance, GKInstance> diseaseToNormal,
                                                 List<GKInstance> unmappedEvents) throws Exception {
        for (GKInstance event : allDiseaseEvents) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            GKInstance normal = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.normalPathway);
            if (normal != null)
                diseaseToNormal.put(event, normal);
            else
                unmappedEvents.add(event);
        }
        // Check all mappings
        System.out.println("Mapped Events: " + diseaseToNormal.size());
        List<GKInstance> diseaseList = new ArrayList<GKInstance>(diseaseToNormal.keySet());
        InstanceUtilities.sortInstances(diseaseList);
        for (GKInstance diseaseInst : diseaseList) {
            GKInstance normal = diseaseToNormal.get(diseaseInst);
            System.out.println(diseaseInst + " -> " + normal);
        }
        InstanceUtilities.sortInstances(unmappedEvents);
        System.out.println("\nUnmapped Events: " + unmappedEvents.size());
        for (GKInstance event : unmappedEvents) {
            System.out.println(event);
        }

        // Do some clean up
        // If container pathways have covered, contained pathways should not be considered
        for (GKInstance diseaseInst : diseaseList) {
            Set<GKInstance> containedEvents1 = InstanceUtilities.getContainedEvents(diseaseInst);
            unmappedEvents.removeAll(containedEvents1);
        }
        System.out.println("\nAfter removing events covered by container events: " + unmappedEvents.size());
        for (GKInstance diseaseInst : unmappedEvents) {
            System.out.println(diseaseInst);
        }
        // If contained pathways have covered
        for (Iterator<GKInstance> it = unmappedEvents.iterator(); it.hasNext(); ) {
            GKInstance diseaseInst = it.next();
            Set<GKInstance> containedEvents1 = InstanceUtilities.getContainedEvents(diseaseInst);
            for (GKInstance containedEvent : containedEvents1) {
                if (diseaseToNormal.containsKey(containedEvent)) {
                    it.remove();
                    break;
                }
            }
        }
        System.out.println("\nAfter removing events covered by contained events: " + unmappedEvents.size());
        for (GKInstance diseaseInst : unmappedEvents) {
            System.out.println(diseaseInst);
        }
        // Special cases with normal pathways listed still: Remove 3700989 (Transcriptional Regulation by TP53)
        Long[] dbIds = new Long[]{3700989L, 166658L, 446652L};
        for (Long dbId : dbIds) {
            GKInstance tp53 = dba.fetchInstance(dbId);
            unmappedEvents.remove(tp53);
            unmappedEvents.removeAll(InstanceUtilities.getContainedEvents(tp53));
        }
        System.out.println("\nAfter removing normal pathways listed in disease events: " + unmappedEvents.size());
        for (GKInstance diseaseInst : unmappedEvents) {
            System.out.println(diseaseInst);
        }
    }

    /**
     * This method is used to re-organize disease pathways to their appropriate places as
     * requested by Antonio.
     *
     * @throws Exception
     */
    @Test
    public void reOrganizeDiseasePathwaysToNormals() throws Exception {
        PersistenceAdaptor dba = getDBA();

        GKInstance disease = getDiseasePathway(dba);

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
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, tx);
                for (GKInstance diseaseInst : diseaseList) {
                    GKInstance normalInst = diseaseToNormal.get(diseaseInst);
                    List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                    normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                            diseaseInst);
                    dba.updateInstanceAttribute(normalInst,
                            ReactomeJavaConstants.hasEvent, tx);
                    ScriptUtilities.addIEToModified(normalInst,
                            defaultIE,
                            dba, tx);
                }

                // Delete re-organized disease pathways from top-most disease pathway.
                List<GKInstance> hasEvent = disease.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                for (Iterator<GKInstance> it = hasEvent.iterator(); it.hasNext(); ) {
                    GKInstance inst = it.next();
                    String displayName = inst.getDisplayName();
                    if (displayName.equals("Infectious disease"))
                        continue;
                    it.remove();
                }
                dba.updateInstanceAttribute(disease, ReactomeJavaConstants.hasEvent, tx);
                ScriptUtilities.addIEToModified(disease, defaultIE, dba, tx);
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // MySQL
            boolean isTransactionSupported = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).startTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, null);
                for (GKInstance diseaseInst : diseaseList) {
                    GKInstance normalInst = diseaseToNormal.get(diseaseInst);
                    List<GKInstance> normalInstHasEvent = normalInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
                    normalInst.addAttributeValue(ReactomeJavaConstants.hasEvent,
                            diseaseInst);
                    dba.updateInstanceAttribute(normalInst,
                            ReactomeJavaConstants.hasEvent, null);
                    ScriptUtilities.addIEToModified(normalInst,
                            defaultIE,
                            dba, null);
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
                dba.updateInstanceAttribute(disease, ReactomeJavaConstants.hasEvent, null);
                ScriptUtilities.addIEToModified(disease, defaultIE, dba, null);
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).rollback();
                e.printStackTrace();
            }
        }
    }

    /**
     * This class is used to generate a mapping from disease pathways to normal pathways.
     *
     * @throws Exception
     */
    private Map<GKInstance, GKInstance> generateDiseasePathwaysToNormalMappping(PersistenceAdaptor dba) throws Exception {
        Map<GKInstance, GKInstance> diseaseToNormal = new HashMap<GKInstance, GKInstance>();
        Map<String, Long> diseaseNameToNormalDBID = getManualMapping();
        GKInstance disease = getDiseasePathway(dba);
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
            Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                    ReactomeJavaConstants._displayName,
                    "=",
                    normalName);
            if (c.size() > 1) {
                System.out.println("More than one pathway: " + normalName);
                continue;
            } else if (c.size() == 0) {
                System.out.println("Cannot find pathway: " + normalName);
                continue;
            }
            GKInstance normal = c.iterator().next();
            diseaseToNormal.put(inst, normal);
        }

        Set<GKInstance> topDiseasePathways = new HashSet<GKInstance>(diseaseToNormal.keySet());
//         Recursively generate the mapping for individual disease pathways.
        for (GKInstance diseaseInst : hasEvent) {
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
     *
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
     *
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
        PersistenceAdaptor dba = getDBA();
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
     *
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
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_120114_fireworks",
                "root",
                "macmysql01");
        // Get the disease pathway
        GKInstance disease = dba.fetchInstance(1643685L);
        // Get all disease pathway pathways
        Set<GKInstance> diseaseEvents = InstanceUtilities.getContainedEvents(disease);
        System.out.println("Total events in disease: " + diseaseEvents.size());
        // We want to focus normal pathways in these top-three pathways, which are linked into the disease hierarchy
        Long[] dbIds = new Long[]{
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
                for (Iterator<GKInstance> it = copy.iterator(); it.hasNext(); ) {
                    GKInstance event = it.next();
                    if (!event.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                        it.remove();
                }
                if (copy.size() > 0) {
                    System.out.println(diseaseEvent + "\t" + copy.size() + "\t" + copy);
                    total++;
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
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, tx);
                for (GKInstance diseasePathway : diseaseToNormal.keySet()) {
                    GKInstance normalPathway = diseaseToNormal.get(diseasePathway);
                    useNormalPathwaySlot(diseasePathway,
                            normalPathway,
                            ie,
                            dba, tx);
                }
                tx.commit();
            }
        } else {
            // MySQL
            try {
                ((MySQLAdaptor) dba).startTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, null);
                for (GKInstance diseasePathway : diseaseToNormal.keySet()) {
                    GKInstance normalPathway = diseaseToNormal.get(diseasePathway);
                    useNormalPathwaySlot(diseasePathway,
                            normalPathway,
                            ie,
                            dba, null);
                }
                ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                ((MySQLAdaptor) dba).rollback();
            }
        }
    }

    /**
     * Update database by using the new slot, normalPathway, for disease pathways.
     *
     * @throws Exception
     */
    @Test
    public void useNewNormalPathwaySlot() throws Exception {
        PersistenceAdaptor dba = getDBA();
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

    private void useNewNormalPathwaySlot(GKInstance disease, PersistenceAdaptor dba) throws Exception {
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
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, tx);
                for (GKInstance pathway : toBeChanged) {
                    useNewNormalPathwaySlot(pathway, ie, dba, tx);
                }
                tx.commit();
            }
        } else {
            // MySQL
            boolean isTransactionSupported = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).startTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, null);
                for (GKInstance pathway : toBeChanged) {
                    useNewNormalPathwaySlot(pathway, ie, dba, null);
                }
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).rollback();
            }
        }
    }

    private PersistenceAdaptor getDBA() throws SQLException {
        if (this.dba != null)
            return this.dba;
//        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
//                                            "gk_current_ver51_new_schema",
//                                            "root",
//                                            "macmysql01");
//        PersistenceAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
//                                            "test_gk_central_020915_wgm",
//                                            "authortool",
//                                            "T001test");
//        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
//                                            "test_gk_central_031317_new_disease",
//                                            "root",
//                                            "macmysql01");
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                "test_gk_central_063017_new_disease",
                "root",
                "macmysql01");
        return dba;
    }

    public void setDBA(PersistenceAdaptor dba) {
        this.dba = dba;
    }

    private void useNewNormalPathwaySlot(GKInstance pathway,
                                         GKInstance ie,
                                         PersistenceAdaptor dba,
                                         Transaction tx) throws Exception {
        GKInstance normalPathway = getNormalPathwayForDisease(pathway);
        useNormalPathwaySlot(pathway,
                normalPathway,
                ie,
                dba, tx);
    }

    private void useNormalPathwaySlot(GKInstance diseasePathway,
                                      GKInstance normalPathway,
                                      GKInstance ie,
                                      PersistenceAdaptor dba,
                                      Transaction tx) throws Exception {
        if (normalPathway == null)
            throw new IllegalArgumentException(diseasePathway + " has no normal pathway!");
        diseasePathway.setAttributeValue(ReactomeJavaConstants.normalPathway, normalPathway);
        diseasePathway.removeAttributeValueNoCheck(ReactomeJavaConstants.hasEvent, normalPathway);
        diseasePathway.getAttributeValue(ReactomeJavaConstants.modified);
        diseasePathway.addAttributeValue(ReactomeJavaConstants.modified, ie);
        dba.updateInstanceAttribute(diseasePathway, ReactomeJavaConstants.modified, tx);
        dba.updateInstanceAttribute(diseasePathway, ReactomeJavaConstants.hasEvent, tx);
        dba.updateInstanceAttribute(diseasePathway, ReactomeJavaConstants.normalPathway, tx);
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
     *
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
