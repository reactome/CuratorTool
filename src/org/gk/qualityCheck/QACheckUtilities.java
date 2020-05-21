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
     * @param humanSpeciesInst GKInstance -- Homo sapiens species instance.
     * @return
     * @throws Exception-- Thrown by MySQLAdaptor
     */
    public static Set<GKInstance> findHumanReactionsNotUsedForManualInference(MySQLAdaptor dba, GKInstance humanSpeciesInst) throws Exception {
        Set<GKInstance> reactionsNotUsedForManualInference = new HashSet<>();
        for (GKInstance event : findEventsNotUsedForManualInference(dba)) {
            // Filter for Human ReactionlikeEvents
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) && QACheckUtilities.isHumanDatabaseObject(event, humanSpeciesInst)) {
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
     * @param multiPEInstance GKInstance -- Type Complex, Polymer or EntitySet that contains multiple PhysicalEntities within it.
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
     * @param entitySet
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
            // Recursively searches members/candidates for all PhysicalEntities.
            for (GKInstance setInstance : (Collection<GKInstance>) entitySet.getAttributeValuesList(entitySetAttribute)) {
                physicalEntities.addAll(findAllPhysicalEntities((setInstance)));
            }
        }
        return physicalEntities;
    }

    // Returns true if 'inferredFrom' referral is null.
    public static boolean manuallyInferred(GKInstance event) throws Exception {
        return event.getReferers(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    // Returns true if databaseObject has single Homo sapiens species.
    public static boolean isHumanDatabaseObject(GKInstance databaseObject, GKInstance humanSpeciesInst) throws Exception {
        Collection<GKInstance> objectSpecies = databaseObject.getAttributeValuesList(ReactomeJavaConstants.species);
        return objectSpecies.size() == 1 && objectSpecies.contains(humanSpeciesInst);
    }

    /**
     * Returns true if the incoming physical entity has a non-human species attribute.
     * @param physicalEntity GKInstance -- PhysicalEntity to be checked for non-human species attribute.
     * @param humanSpeciesInst GKInstance -- Homo sapiens species instance.
     * @return boolean -- true if has non-human species, false if has human species.
     * @throws Exception
     */
    public static boolean hasNonHumanSpecies(GKInstance physicalEntity, GKInstance humanSpeciesInst) throws Exception {
        // Check if species is a valid attribute for physicalEntity.
        if (!hasSpeciesAttribute(physicalEntity)) {
            return false;
        }
        // Check that species attribute is populated.
        if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) == null) {
            return false;
        }
        // Check that populated species attribute is not Human.
        if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) == humanSpeciesInst) {
            return false;
        }
        return true;
    }

    public static boolean hasSpeciesAttribute(GKInstance physicalEntity) {
        return physicalEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.species);
    }

    /**
     * Checks pathway hierarchy of incoming GKInstance to see if it is a member of any skiplist pathways.
     * @param nonHumanEvent GKInstance -- Event instance that is checked.
     * @return boolean -- True if member of a skiplist pathway, false if not.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    public static boolean memberSkipListPathway(GKInstance nonHumanEvent) throws Exception {

        if (inSkipList(nonHumanEvent)) {
            return true;
        }
        Collection<GKInstance> hasEventReferrals = nonHumanEvent.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            // Recursively iterate through parent Events to see if member of skiplist pathway.
            for (GKInstance hasEventReferral : hasEventReferrals) {
                return memberSkipListPathway(hasEventReferral);
            }
        }
        return false;
    }

    public static void setSkipList(List<String> skippedDbIds) {
        skiplistDbIDs = skippedDbIds;
    }

    public static boolean inSkipList(GKInstance nonHumanEvent) {
        return skiplistDbIDs.contains(nonHumanEvent.getDBID().toString());
    }


}
