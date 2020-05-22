package org.gk.qualityCheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class QACheckUtilities {

    private static List<String> skiplistDbIDs = new ArrayList<>();
    private static GKInstance humanSpeciesInst = new GKInstance();

    public static GKInstance getLatestCuratorIEFromInstance(GKInstance instance) throws Exception {
         @SuppressWarnings("unchecked")
        List<GKInstance> modIEs = instance.getAttributeValuesList(ReactomeJavaConstants.modified);
         if (modIEs != null) {
             List<Long> developers = QACheckProperties.getDeveloperDbIds();
             for (int index = modIEs.size() - 1; index >= 0; index--) {
                 GKInstance modIE = modIEs.get(modIEs.size() - 1);
                 GKInstance author = (GKInstance) modIE.getAttributeValue("author");
                 // Skip modification instance for developers.
                 if (author != null && !developers.contains(author.getDBID())) {
                     return modIE;
                 }
    
             }
         }
         // Get the created one
         return (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
     }

    /**
     * Finds all Events in DB that are not used for manual inference, or members of Pathways in skiplist. Finding if
     * manually inferred is done by checking for a null 'inferredFrom' referral.
     * @param dba MySQLAdaptor
     * @return Set<GKInstance> -- All Events in DB that are not used for manual inference.
     * @throws Exception -- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findEventsNotUsedForManualInference(MySQLAdaptor dba) throws Exception {
        Set<GKInstance> eventsNotUsedForInference = new HashSet<>();
        Collection<GKInstance> events = dba.fetchInstancesByClass(ReactomeJavaConstants.Event);
        for (GKInstance event : events) {
            if (!manuallyInferred(event) && !memberSkipListPathway(event)) {
                eventsNotUsedForInference.add(event);
            }
        }
        return eventsNotUsedForInference;
    }

    /**
     * Finds all Events not used for Inference, and then finds subset that are Human ReactionlikeEvents
     * @param dba MySQLAdaptor
     * @return
     * @throws Exception-- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findHumanReactionsNotUsedForManualInference(MySQLAdaptor dba) throws Exception {
        Set<GKInstance> reactionsNotUsedForManualInference = new HashSet<>();
        for (GKInstance event : findEventsNotUsedForManualInference(dba)) {
            // Filter for Human ReactionlikeEvents
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)
                    && QACheckUtilities.isHumanDatabaseObject(event)) {

                reactionsNotUsedForManualInference.add(event);
            }
        }
        return reactionsNotUsedForManualInference;
    }

    /**
     * Finds all PhysicalEntities that exist in a ReactionlikeEvent's inputs, outputs, catalysts and regulations.
     * @param reaction GKInstance -- ReactionlikeEvent that will be searched for PhysicalEntities.
     * @return Set<GKInstance> -- All distinct PhysicalEntitys in ReactionlikeEvent
     * @throws Exception -- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findAllPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> reactionPEs = new HashSet<>();
        reactionPEs.addAll(findAllInputAndOutputPEs(reaction));
        reactionPEs.addAll(findAllCatalystPEs(reaction));
        reactionPEs.addAll(findAllRegulationPEs(reaction));
        return reactionPEs;
    }

    /**
     * Finds all PhysicalEntities that exist in a ReactionlikeEvent's inputs and outputs.
     * @param reaction GKInstance -- ReactionlikeEvent that is being searched for PhysicalEntities.
     * @return Set<GKInstance> -- All distinct PhysicalEntitys in ReactionlikeEvent's inputs and outputs.
     * @throws Exception -- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findAllInputAndOutputPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> inputOutputPEs = new HashSet<>();
        for (String attribute : Arrays.asList(ReactomeJavaConstants.input, ReactomeJavaConstants.output)) {
            for (GKInstance attributePE : (Collection<GKInstance>) reaction.getAttributeValuesList(attribute)) {
                // QA checks calling these methods are concerned with PhysicalEntities that have a species attribute.
                if (hasSpeciesAttribute(attributePE)) {
                    inputOutputPEs.add(attributePE);
                    inputOutputPEs.addAll(findAllPhysicalEntities(attributePE));
                }
            }
        }
        return inputOutputPEs;
    }

    /**
     * Finds all PhysicalEntities that exist in a ReactionlikeEvent's catalystActivity. It checks the catalyst's
     * activeUnit and physicalEntity attributes.
     * @param reaction GKInstance -- ReactionlikeEvent that is being searched for PhysicalEntities.
     * @return Set<GKInstance> -- All distinct PhysicalEntities in ReactionlikeEvent's catalystActivity.
     * @throws Exception -- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findAllCatalystPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> catalystPEs = new HashSet<>();
        List<GKInstance> catalysts = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        for (GKInstance catalyst : catalysts) {
            for (String attribute : Arrays.asList(ReactomeJavaConstants.activeUnit, ReactomeJavaConstants.physicalEntity)) {
                for (GKInstance attributePE : (Collection<GKInstance>) catalyst.getAttributeValuesList(attribute)) {
                    // QA checks calling these methods are concerned with PhysicalEntities that have a species attribute.
                    if (hasSpeciesAttribute(attributePE)) {
                        catalystPEs.add(attributePE);
                        catalystPEs.addAll(findAllPhysicalEntities(attributePE));
                    }
                }
            }
        }
        return catalystPEs;
    }

    /**
     * Finds all PhysicalEntities that exist in a ReactionlikeEvent's regulatedBy. It checks the regulation's
     * activeUnit and regulator attributes.
     * @param reaction GKInstance -- ReactionlikeEvent that is being searched for PhysicalEntities.
     * @return Set<GKInstance> -- All distinct PhysicalEntities in ReactionlikeEvent's regulatedBy.
     * @throws Exception -- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findAllRegulationPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> regulationPEs = new HashSet<>();
        List<GKInstance> regulations = reaction.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
        for (GKInstance regulation : regulations) {
            for (String attribute : Arrays.asList(ReactomeJavaConstants.activeUnit, ReactomeJavaConstants.regulator)) {
                for (GKInstance attributePE : (Collection<GKInstance>) regulation.getAttributeValuesList(attribute)) {
                    // QA checks calling these methods are concerned with PhysicalEntities that have a species attribute.
                    if (hasSpeciesAttribute(attributePE)) {
                        regulationPEs.add(attributePE);
                        regulationPEs.addAll(findAllPhysicalEntities(attributePE));
                    }
                }
            }
        }
        return regulationPEs;
    }

    /**
     * Method that actually finds all distinct PhysicalEntities. Checks if it contains multiple PhysicalEntities (Complexes, Polymers, EntitySets).
     * If it does, it will find all PhysicalEntities within that PhysicalEntity. Otherwise, just adds the single PhysicalEntity.
     * @param physicalEntity GKInstance -- PhysicalEntity that is checked for additional PhysicalEntities, and also added to returned Set.
     * @return Set<GKInstance> -- All distinct PhysicalEntities found in incoming PhysicalEntity. Includes incoming PhysicalEntity.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static Set<GKInstance> findAllPhysicalEntities(GKInstance physicalEntity) throws Exception {
        Set<GKInstance> physicalEntities = new HashSet<>();
        // QA checks calling these methods are concerned with PhysicalEntities that have a species attribute.
        // Since this method can be called by its interior methods, species check needs to happen here as well.
        if (hasSpeciesAttribute(physicalEntity)) {
            // Checks if Complex, Polymer, or EntitySet. Finds all constituent PEs if so.
            if (containsMultiplePEs(physicalEntity)) {
                physicalEntities.add(physicalEntity);
                physicalEntities.addAll(findAllConstituentPEs(physicalEntity));
            // If EWAS, just adds to Set and is returned.
            } else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                physicalEntities.add(physicalEntity);
            }
        }
        return physicalEntities;
    }

    /**
     * Checks if PhysicalEntity contains additional PhysicalEntities within it.
     * @param physicalEntity GKInstance -- PhysicalEntity that is being checked.
     * @return boolean -- true if PhysicalEntity type that contains multiple PEs, false if only type that has single PhysicalEntity.
     */
    public static boolean containsMultiplePEs(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            return true;
        }
        return false;
    }

    /**
     * Finds all PhysicalEntities contained within a Complex, Polymer or EntitySet. Searches recursively by calling parent 'findAllPhysicalEntities' method.
     * @param multiPEInstance GKInstance -- Complex, Polymer or EntitySet instance that will be searched for all PhysicalEntity instances.
     * @return Set<GKInstance> -- All distinct PhysicalEntities that are found in Complex/Polymer/EntitySet.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static Set<GKInstance> findAllConstituentPEs(GKInstance multiPEInstance) throws Exception {
        Set<GKInstance> physicalEntities = new HashSet<>();
        if (multiPEInstance.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
            // Recursively search Complex components for all PhysicalEntities.
            Collection<GKInstance> complexComponents = multiPEInstance.getAttributeValuesList(ReactomeJavaConstants.hasComponent);
            for (GKInstance complexComponent : complexComponents) {
                physicalEntities.addAll(findAllPhysicalEntities(complexComponent));
            }
        } else if (multiPEInstance.getSchemClass().isa(ReactomeJavaConstants.Polymer)) {
            // Recursively search Polymer repeatedUnits for all PhysicalEntities.
            Collection<GKInstance> polymerUnits = multiPEInstance.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit);
            for (GKInstance polymerUnit : polymerUnits) {
                physicalEntities.addAll(findAllPhysicalEntities(polymerUnit));
            }
        } else if (multiPEInstance.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            // Recursively search members and candidates (if EntitySet is a CandidateSet) for all PhysicalEntities.
            physicalEntities.addAll(findEntitySetPhysicalEntities(multiPEInstance));
        }
        return physicalEntities;
    }

    /**
     * Finds all PhysicalEntities that exist within an EntitySet by searching for all member PEs (via 'hasMember' attribute)
     * and all candidate PEs (if entitySet is a CandidateSet; via 'hasCandidate attribute).
     * @param entitySet GKInstance -- EntitySet instance that will be searched for all PhysicalEntities.
     * @return Set<GKInstance> -- All distinct PhysicalEntities that are found in EntitySet.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static Set<GKInstance> findEntitySetPhysicalEntities(GKInstance entitySet) throws Exception {
        Set<GKInstance> physicalEntities = new HashSet<>();
        // Attribute values to be recursively searched for PhysicalEntities include 'hasMember' and 'hasCandidate'.
        Set<String> entitySetAttributes = new HashSet<>(Arrays.asList(ReactomeJavaConstants.hasMember, ReactomeJavaConstants.hasCandidate));
        // If the EntitySet is a DefinedSet, 'hasCandidate' attribute is not searched.
        if (entitySet.getSchemClass().isa(ReactomeJavaConstants.DefinedSet)) {
            entitySetAttributes.remove(ReactomeJavaConstants.hasCandidate);
        }
        for (String entitySetAttribute : entitySetAttributes) {
            // Recursively searches members/candidates in EntitySet for all PhysicalEntities.
            for (GKInstance setInstance : (Collection<GKInstance>) entitySet.getAttributeValuesList(entitySetAttribute)) {
                physicalEntities.addAll(findAllPhysicalEntities((setInstance)));
            }
        }
        return physicalEntities;
    }

    /**
     * Checks if incoming Event is manually inferred by checking inferredFrom referral.
     * @param event GKInstance -- Event instance being checked for inferredFrom referral.
     * @return boolean -- true if inferredFrom referral exists, false if not.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static boolean manuallyInferred(GKInstance event) throws Exception {
        return event.getReferers(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    /**
     * Checks if incoming DatabaseObject (Event or PhysicalEntity) has single Homo sapiens species.
     * @param databaseObject GKInstance -- Event or PhysicalEntity instance being checked for only Homo sapiens species.
     * @return boolean -- true if databaseObject only has a single, Homo sapiens species instance, false if not.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static boolean isHumanDatabaseObject(GKInstance databaseObject) throws Exception {
        Collection<GKInstance> objectSpecies = databaseObject.getAttributeValuesList(ReactomeJavaConstants.species);
        return objectSpecies.size() == 1 && objectSpecies.contains(humanSpeciesInst);
    }

    /**
     * Checks if the incoming DatabaseObject (Event or PhysicalEntity) is non-human.
     * @param databaseObject GKInstance -- Event or PhysicalEntity to be checked for non-human species attribute.
     * @return boolean -- true if has non-human species, false if has human species.
     * @throws Exception
     */
    public static boolean hasNonHumanSpecies(GKInstance databaseObject) throws Exception {
        // Check if species is a valid attribute for physicalEntity.
        if (hasSpeciesAttribute(databaseObject) &&
            databaseObject.getAttributeValue(ReactomeJavaConstants.species) != null &&
            !databaseObject.getAttributeValuesList(ReactomeJavaConstants.species).contains(humanSpeciesInst)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if species is a valid attribute in the incoming PhysicalEntity.
     * @param physicalEntity GKInstance -- PhysicalEntity that is being checked to see if species is a valid attribute.
     * @return boolean -- true if species is a valid attribute, false if not.
     */
    public static boolean hasSpeciesAttribute(GKInstance physicalEntity) {
        return physicalEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.species);
    }

    /**
     * Checks if
     * @param databaseObject
     * @return
     * @throws Exception
     */
    public static boolean hasDisease(GKInstance databaseObject) throws Exception {
        return databaseObject.getAttributeValue(ReactomeJavaConstants.disease) != null;
    }

    /**
     * Finds all parent DbIds of the incoming Event, and then checks if any of them are in the skiplist of Pathway DbIds.
     * @param event GKInstance -- Event that is being checked for membership in a skiplist Pathway.
     * @return boolean -- true if member of skiplist Pathway, false if not.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static boolean memberSkipListPathway(GKInstance event) throws Exception {

        if (skiplistDbIDs != null) {
            // Finds all parent Event DbIds.
            Set<String> hierarchyDbIds = findEventHierarchyDbIds(event, new HashSet<>());
            // Check if any returned Event DbIds (including original Events) are in skiplist.
            for (String skiplistDbId : skiplistDbIDs) {
                if (hierarchyDbIds.contains(skiplistDbId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds parent DbIds of incoming Event through hasEvent referrers. Recurses until there are no more referrers.
     * @param event GKInstance -- Event that is being checked for referrers. Its DbId is added to the Set being built.
     * @param dbIds Set<String> -- Current Set of parent DbIds of Event.
     * @return Set<String> -- Once TopLevelPathway has been found, returns all DbIds, inclusive, between TopLevelPathway and original Event.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    private static Set<String> findEventHierarchyDbIds(GKInstance event, Set<String> dbIds) throws Exception {
        dbIds.add(event.getDBID().toString());
        Collection<GKInstance> hasEventReferrals = event.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            for (GKInstance hasEventReferral : hasEventReferrals) {
                dbIds.addAll(findEventHierarchyDbIds(hasEventReferral, dbIds));
            }
        }
        return dbIds;
    }

    /**
     * Reads skiplist file that contains Pathway DbIds that should not be included in QA check.
     * @return List<String> -- List of DbIds.
     */
    public static List<String> getNonHumanPathwaySkipList() throws IOException {
        return Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt"));
    }

    /**
     * Takes incoming List of DbIds and sets them as global variable to be used throughout QA check.
     * @param skippedDbIds List<String> -- List of DbIds taken from either file or provided in class.
     */
    public static void setSkipList(List<String> skippedDbIds) {
        skiplistDbIDs = skippedDbIds;
    }

    /**
     * Finds Homo sapiens species instance and sets as global variable to be used throughout QA check.
     * @param dba MySQLAdaptor
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static void setHumanSpeciesInst(MySQLAdaptor dba) throws Exception {
        humanSpeciesInst = dba.fetchInstance(48887L);
    }

    /**
     *  Helper method checks that checks if the attribute exists in the incoming instance. Checks for either
     *  created or species values. Returns displayName or, if attribute instance doesn't exist,  null.
     * @param instance GKInstance -- Instance being checked for an attribute.
     * @param attribute String -- Attribute value that will be checked in the incoming instance.
     * @return String -- Either the attribute instance's displayName, or null.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static String getInstanceAttributeName(GKInstance instance, String attribute) throws Exception {
        GKInstance attributeInstance = (GKInstance) instance.getAttributeValue(attribute);
        return attributeInstance != null ? attributeInstance.getDisplayName() : null;
    }
}
