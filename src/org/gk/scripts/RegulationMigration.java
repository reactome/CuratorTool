package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is used to migrate Regulation instances directly under ReactionlikeEvent.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class RegulationMigration {
    
    public RegulationMigration() {
    }
    
    @Test
    public void checkRegulationSummation() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Fetch all human RLEs
        Collection<GKInstance> humanRLEs = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                ReactomeJavaConstants.species,
                "=",
                ScriptUtilities.getHomoSapiens(dba));
        dba.loadInstanceAttributeValues(humanRLEs, new String[] {
                ReactomeJavaConstants.regulatedBy,
                ReactomeJavaConstants.inferredFrom,
                ReactomeJavaConstants.summation
        });
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        dba.loadInstanceAttributeValues(regulations, new String[] {
                ReactomeJavaConstants.summation
        });
        
        String output = "RLERegulationSummation.txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(output);
        fu.printLine("RLE_DB_ID\tRLE_DisplayName\tRegulation_DB_ID\tRegulation_DisplayName\t" + 
                     "RLE_Summation\tRLE_Summation_Text\t" +
                     "Regulation_Summation\tRegulation_Summation_Text\t" +
                     "IsSummationSame\t" + 
                     "RLE_Summation_Literature\tRegulation_Summation_Literature\t" + 
                     "IsReferenceSame");
        StringBuilder builder = new StringBuilder();
        for (GKInstance humanRLE : humanRLEs) {
            List<GKInstance> summations = collectRLEValues(humanRLE, ReactomeJavaConstants.summation);
            List<GKInstance> rleRegulations = humanRLE.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            for (GKInstance regulation : rleRegulations) {
                List<GKInstance> rSummations = regulation.getAttributeValuesList(ReactomeJavaConstants.summation);
                if (rSummations.size() == 0)
                    continue;
                if (summations.size() == 0) {
                    for (GKInstance rSummation : rSummations) {
                        builder.append(humanRLE.getDBID()).append("\t").append(humanRLE.getDisplayName());
                        builder.append("\t").append(regulation.getDBID()).append("\t").append(regulation.getDisplayName());
                        builder.append("\t").append("\t");
                        String text = (String) rSummation.getAttributeValue(ReactomeJavaConstants.text);
                        text = text.replaceAll("\n|\t", " ");
                        builder.append("\t").append(rSummation.getDBID()).append("\t").append(text);
                        builder.append("\t").append(Boolean.FALSE);
                        builder.append("\t");
                        builder.append("\t");
                        List<GKInstance> rReferences = rSummation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                        for (GKInstance lit : rReferences) {
                            builder.append(lit.getDBID()).append(";");
                        }
                        if (rReferences.size() > 0)
                            builder.deleteCharAt(builder.length() - 1);
                        builder.append("\t").append(rReferences.size() == 0);
                        fu.printLine(builder.toString());
                        builder.setLength(0);
                    }
                }
                else {
                    // For easy checking, we will permute all pair-wise
                    for (GKInstance rleSummation : summations) {
                        for (GKInstance rSummation : rSummations) {
                            builder.append(humanRLE.getDBID()).append("\t").append(humanRLE.getDisplayName());
                            builder.append("\t").append(regulation.getDBID()).append("\t").append(regulation.getDisplayName());
                            String text = (String) rleSummation.getAttributeValue(ReactomeJavaConstants.text);
                            text = text.replaceAll("\n|\t", " ");
                            builder.append("\t").append(rleSummation.getDBID()).append("\t").append(text);
                            text = (String) rSummation.getAttributeValue(ReactomeJavaConstants.text);
                            text = text.replaceAll("\n|\t", " ");
                            builder.append("\t").append(rSummation.getDBID()).append("\t").append(text);
                            builder.append("\t").append(rleSummation == rSummation);
                            List<GKInstance> literatures = rleSummation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                            builder.append("\t");
                            for (GKInstance lit : literatures)
                                builder.append(lit.getDBID()).append(";");
                            if (literatures.size() > 0) // Need to delete the last extra character
                                builder.deleteCharAt(builder.length() - 1);
                            builder.append("\t");
                            List<GKInstance> rReferences = rSummation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                            for (GKInstance lit : rReferences) {
                                builder.append(lit.getDBID()).append(";");
                            }
                            if (rReferences.size() > 0)
                                builder.deleteCharAt(builder.length() - 1);
                            InstanceUtilities.sortInstances(literatures);
                            InstanceUtilities.sortInstances(rReferences);
                            builder.append("\t").append(literatures.equals(rReferences));
                            fu.printLine(builder.toString());
                            builder.setLength(0);
                        }
                    }
                }
            }
        }
        fu.close();
    }
    
    private List<GKInstance> collectRLEValues(GKInstance rle, String attribute) throws Exception {
        List<GKInstance> summations = rle.getAttributeValuesList(attribute);
        Set<GKInstance> set = new HashSet<>(summations);
        List<GKInstance> inferredFrom = rle.getAttributeValuesList(ReactomeJavaConstants.inferredFrom);
        for (GKInstance inst : inferredFrom) {
            summations = inst.getAttributeValuesList(attribute);
            set.addAll(summations);
        }
        List<GKInstance> list = new ArrayList<>(set);
        InstanceUtilities.sortInstances(list);
        return list;
    }
    
    @Test
    public void checkRegulationLiteratureReference() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Fetch all human RLEs
        Collection<GKInstance> humanRLEs = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                ReactomeJavaConstants.species,
                "=",
                ScriptUtilities.getHomoSapiens(dba));
        dba.loadInstanceAttributeValues(humanRLEs, new String[] {
                ReactomeJavaConstants.regulatedBy,
                ReactomeJavaConstants.inferredFrom,
                ReactomeJavaConstants.literatureReference
        });
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        dba.loadInstanceAttributeValues(regulations, new String[] {
                ReactomeJavaConstants.literatureReference
        });
        
        String output = "RLERegulationReference.txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(output);
        fu.printLine("RLE_DB_ID\tRLE_DisplayName\tRegulation_DB_ID\tRegulation_DisplayName\t" + 
                     "RLE_Literatures\tRegulation_Literatures\t" + 
                     "Shared\tRLE_NotShared\tRegulation_NotShared");
        StringBuilder builder = new StringBuilder();
        for (GKInstance humanRLE : humanRLEs) {
            List<GKInstance> literatures = collectRLEValues(humanRLE, ReactomeJavaConstants.literatureReference);
            List<GKInstance> rleRegulations = humanRLE.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            for (GKInstance regulation : rleRegulations) {
                List<GKInstance> rReferences = regulation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                if (rReferences == null || rReferences.size() == 0)
                    continue;
                builder.append(humanRLE.getDBID()).append("\t").append(humanRLE.getDisplayName());
                builder.append("\t").append(regulation.getDBID()).append("\t").append(regulation.getDisplayName());
                builder.append("\t");
                // Need to consider if there is no references in an RLE.
                for (GKInstance lit : literatures)
                    builder.append(lit.getDBID()).append(";");
                if (literatures.size() > 0) // Need to delete the last extra character
                    builder.deleteCharAt(builder.length() - 1);
                builder.append("\t");
                for (GKInstance lit : rReferences) {
                    builder.append(lit.getDBID()).append(";");
                }
                builder.deleteCharAt(builder.length() - 1);
                List<GKInstance> shared = getShared(literatures, rReferences);
                builder.append("\t").append(shared.size());
                List<GKInstance> copy = new ArrayList<>(literatures);
                copy.removeAll(shared);
                builder.append("\t").append(copy.size());
                copy = new ArrayList<>(rReferences);
                copy.removeAll(shared);
                builder.append("\t").append(copy.size());
                fu.printLine(builder.toString());
                builder.setLength(0);
            }
        }
        fu.close();
    }
    
    private List<GKInstance> getShared(List<GKInstance> list1, List<GKInstance> list2) {
        List<GKInstance> shared = new ArrayList<>(list1);
        shared.retainAll(list2);
        return shared;
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_101618",
                "root",
                "macmysql01");
        return dba;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Provide four parameters: dbHost, dbName, dbUser, dbPwd, and operation (one of CheckErrors, CheckWarnings, Migration, UpdateDisplayNames, and DeleteNotReleasedStableIds)");
            System.exit(1);
        }
        MySQLAdaptor dba = new MySQLAdaptor(args[0], args[1], args[2], args[3]);
        RegulationMigration regulationMigration = new RegulationMigration();
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        if (args[4].equals("CheckErrors")) {
            regulationMigration.checkErrors(regulations);
        }
        else if (args[4].equals("CheckWarnings")) {
            regulationMigration.checkWarnings(regulations);
        }
        else if (args[4].equals("Migration")) {
            if (regulationMigration.checkErrors(regulations))
                return;
            // Since they are warnings, the migration can still go ahead
            regulationMigration.checkWarnings(regulations);
            regulationMigration.migrate(dba, regulations);    
        }
        else if (args[4].equals("UpdateDisplayNames")) {
            regulationMigration.updateDisplayNames(regulations, dba);
        }
        else if (args[4].equals("DeleteNotReleasedStableIds")) {
            regulationMigration.deleteNotReleasedStableIds(regulations, dba);
        }
    }
    
    /**
     * Delete StableIdentifiers that are used for Regulations but not released.
     * @param regulations
     * @param dba
     * @throws Exception
     */
    public void deleteNotReleasedStableIds(Collection<GKInstance> regulations,
                                           MySQLAdaptor dba) throws Exception {
        try {
            dba.startTransaction();
            int c = 0;
            // Don't forget to add IE
            System.out.println("Starting updating Regulations...");
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance regulation : regulations) {
                System.out.println("Checking " + regulation + "...");
                GKInstance stableId = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                if (stableId == null)
                    continue;
                Boolean released = (Boolean) stableId.getAttributeValue(ReactomeJavaConstants.released);
                if (released != null && released)
                    continue;
                System.out.println("Deleting " + stableId);
                // Delete stable id
                regulation.setAttributeValue(ReactomeJavaConstants.stableIdentifier, null);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.stableIdentifier);
                dba.deleteInstance(stableId);
                // Update modified
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
                c ++;
            }
            dba.commit();
            System.out.println("Total deletions: " + c);
        }
        catch(Exception e) {
            System.err.println(e);
            dba.rollback();
        }
    }
    
    /**
     * Update the display names for all Regulations based on regulators only.
     * @throws Exception
     */
    public void updateDisplayNames(Collection<GKInstance> regulations,
                                   MySQLAdaptor dba) throws Exception {
        try {
            dba.startTransaction();
            // Don't forget to add IE
            System.out.println("Starting updating Regulations...");
            int c = 0;
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance regulation : regulations) {
                String displayName = regulation.getDisplayName();
                String newName = InstanceDisplayNameGenerator.generateDisplayName(regulation);
                if (displayName.equals(newName))
                    continue;
                System.out.println("Updating " + regulation + "...");
                InstanceDisplayNameGenerator.setDisplayName(regulation);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants._displayName);
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
                c ++;
            }
            dba.commit();
            System.out.println("Total updated: " + c);
        }
        catch(Exception e) {
            System.err.println(e);
            dba.rollback();
        }
    }
    
    // As of April 9, the following check should pass for gk_central@reactomecurator.
    public void checkRegulationFromCasToRle(Collection<GKInstance> regulations) throws Exception {
        for (GKInstance regulation : regulations) {
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null || !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
//                System.out.println(regulation + " doesn't have regulatedEntity!");
                continue;
            }
//            System.out.println("Checking " + regulation);
            // Get the target RLE
            Collection<GKInstance> c = regulatedEntity.getReferers(ReactomeJavaConstants.catalystActivity);
            if (c == null || c.size() == 0) {
                throw new IllegalStateException(regulatedEntity + " is not used!");
            }
            if (c.size() > 1) {
                System.out.println(regulation + ": " + regulatedEntity + " is used in more than one RLE!");
            }
            for (GKInstance rle : c) {
                if (!rle.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    throw new IllegalStateException(regulatedEntity + " is used for non-RLE event: " + rle);
                }
            }
        }
    }
    
    public void migrate(MySQLAdaptor dba,
                        Collection<GKInstance> regulations) throws Exception {
        try {
            // Sort Regulations based on DB_IDs so that they can be added based on DB_IDs
            List<GKInstance> regulationList = new ArrayList<>(regulations);
            regulationList.sort((reg1, reg2) -> reg1.getDBID().compareTo(reg2.getDBID()));
            Set<GKInstance> reactions = new HashSet<>(); // To be updated
            // These regulations need to update their _displayName since their targets are updated
            Set<GKInstance> regulationsToBeUpdated = new HashSet<>();
            for (GKInstance regulation : regulationList) {
                GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                if (regulatedEntity == null)
                    continue; // Do nothing
                if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    System.err.println(regulation + " has a pathway as its regulatedEntity: " + regulatedEntity);
                    continue;
                }
                boolean needRegulationUpdate = false;
                Set<GKInstance> targets = new HashSet<>();
                if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    targets.add(regulatedEntity);
                }
                else if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
                    Collection<GKInstance> referrers = regulatedEntity.getReferers(ReactomeJavaConstants.catalystActivity);
                    // Just in case this CAS is reused. Usually it should not be true
                    if (referrers != null && referrers.size() > 0)
                        targets.addAll(referrers);
                    needRegulationUpdate = true;
                }
                if (targets.size() == 0) {
                    System.err.println("Cannot find a target for " + regulation);
                    continue; 
                }
                if (targets.size() > 1) {
                    System.out.println(regulation + " will be assigned to multiple ReactionlikeEvent instances!");
                }
                // Another check
                for (GKInstance target : targets) {
                    if (target.getSchemClass().isValidAttribute(ReactomeJavaConstants.regulatedBy)) {
                        target.addAttributeValue(ReactomeJavaConstants.regulatedBy, regulation);
                        reactions.add(target);
                    }
                }
                if (needRegulationUpdate) {
                    regulationsToBeUpdated.add(regulation);
                }
            }
            dba.startTransaction();
            // Don't forget to add IE
            System.out.println("Starting updating ReactionlikeEvent...");
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance reaction : reactions) {
                System.out.println("Updating " + reaction + "...");
                dba.updateInstanceAttribute(reaction, ReactomeJavaConstants.regulatedBy);
                reaction.getAttributeValuesList(ReactomeJavaConstants.modified);
                reaction.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(reaction, ReactomeJavaConstants.modified);
            }
            dba.commit();
            dba.startTransaction();
            System.out.println("Starting updating Regulation...");
            // To get the referrers, the changes for regulatedBy should be in the database first
            for (GKInstance regulation : regulationsToBeUpdated) {
                System.out.println("Updating " + regulation + "...");
                InstanceDisplayNameGenerator.setDisplayName(regulation);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants._displayName);
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
            }
            dba.commit();
        }
        catch(Exception e) {
            System.err.println(e);
            dba.rollback();
        }
    }
    
    public boolean checkWarnings(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> reactions = checkContainedInPathway(regulations);
        if (reactions.size() > 0) {
            System.out.println("ReactionlikeEvent instances are listed in more than one Pathways' hasEvent slot and regulated by Regulations with containedInPathway != null: " + reactions.size());
            System.out.println("DB_ID\tDisplayName\tCurator\tPathways");
            StringBuilder builder = new StringBuilder();
            for (GKInstance reaction : reactions) {
                Collection<GKInstance> pathways = reaction.getReferers(ReactomeJavaConstants.hasEvent);
                builder.append(reaction.getDBID()).append("\t");
                builder.append(reaction.getDisplayName()).append("\t");
                GKInstance ie = InstanceUtilities.getLatestIEFromInstance(reaction);
                GKInstance author = (GKInstance) ie.getAttributeValue(ReactomeJavaConstants.author);
                builder.append(author.getDisplayName()).append("\t");
                for (GKInstance pathway : pathways)
                    builder.append(pathway).append("\t");
                builder.delete(builder.length() - 1, builder.length());
                System.out.println(builder.toString());
                builder.setLength(0);
            }
            return true;
        }
        return false;
    }
    
    /**
     * If a ReactionlikeEvent has been annotated for several pathways, it may be regulated in some of them,
     * but not others. This check picks up ReactionlikeEvents, which have been annotated in regulatedEntity
     * for Regulation instances whose containedInPathway are not null, for curators to make manual edits.
     * @param regulations
     * @throws Exception
     */
    public List<GKInstance> checkContainedInPathway(Collection<GKInstance> regulations) throws Exception {
        Set<GKInstance> reactions = new HashSet<>();
        for (GKInstance regulation : regulations) {
            GKInstance pathway = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.containedInPathway);
            if (pathway == null)
                continue;
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null || !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            Collection<GKInstance> pathways = regulatedEntity.getReferers(ReactomeJavaConstants.hasEvent);
            if (pathways == null || pathways.size() == 0)
                continue;
            if (pathways.size() > 1) {
                reactions.add(regulatedEntity);
            }
        }
        return new ArrayList<>(reactions);
    }
    
    /**
     * Make sure regulatedType is ReactionlikeEvent since we will migrate Regulation
     * to ReactionlikeEvent only.
     * @return a list of Regulation having Pathways as regulatedEntity.
     * @throws Exception
     */
    public List<GKInstance> checkRegulatedType(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> rtn = new ArrayList<>();
        for (GKInstance regulation : regulations) {
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null) {
                continue;
                // This should be fine since we will keep Regulation instances as they are
//                throw new IllegalStateException(regulation + " has null regulatedEntity!");
            }
            if (!regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) &&
                !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
                rtn.add(regulation);
            }
        }
        return rtn;
    }
    
    public boolean checkErrors(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> wrongRegulatedEntity = checkRegulatedType(regulations);
        boolean hasPathwayAsRegulatedEntity = false;
        if (wrongRegulatedEntity.size() > 0) {
            System.err.println("Regulation instances have regulatedEntity values are not ReactionlikeEvent or CatalystActivity: " + wrongRegulatedEntity.size());
            InstanceUtilities.sortInstances(wrongRegulatedEntity);
            System.out.println("DBID\tDisplayName\tRegulatedEntity_DBID\tRegulatedEntity_Class");
            for (GKInstance regulation : wrongRegulatedEntity) {
                GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                System.out.println(regulation.getDBID() + "\t" + 
                                   regulation.getDisplayName() + "\t" + 
                                   regulatedEntity.getDBID() + "\t" + 
                                   regulatedEntity.getSchemClass().getName());
                if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                    hasPathwayAsRegulatedEntity = true;
            }
        }
        checkRegulationFromCasToRle(regulations);
        if (hasPathwayAsRegulatedEntity)
            return true;
        return false;
    }

}
