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

/**
 * This class is used to migrate Regulation instances directly under ReactionlikeEvent.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class RegulationMigration {
    
    public RegulationMigration() {
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Provide four parameters: dbHost, dbName, dbUser, dbPwd, and operation (one of CheckErrors, CheckWarnings, and Migration)");
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
