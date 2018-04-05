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
        if (args.length != 3) {
            System.err.println("Provide three parameters: dbName, dbUser, and dbPwd!");
            System.exit(1);
        }
        MySQLAdaptor dba = new MySQLAdaptor("localhost", args[0], args[1], args[2]);
        RegulationMigration regulationMigration = new RegulationMigration();
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
//        if (regulationMigration.checkErrors(regulations))
//            return;
//        if (regulationMigration.checkWarnings(regulations))
//            return;
        regulationMigration.migrate(dba, regulations);    
    }
    
    public void migrate(MySQLAdaptor dba,
                        Collection<GKInstance> regulations) throws Exception {
        try {
            // Sort Regulations based on DB_IDs so that they can be added based on DB_IDs
            List<GKInstance> regulationList = new ArrayList<>(regulations);
            regulationList.sort((reg1, reg2) -> reg1.getDBID().compareTo(reg2.getDBID()));
            Set<GKInstance> reactions = new HashSet<>();
            for (GKInstance regulation : regulationList) {
                GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                if (regulatedEntity == null || !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                    continue; // Only for ReactionlikeEvent 
                // Another check
                if (regulatedEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.regulatedBy)) {
                    regulatedEntity.addAttributeValue(ReactomeJavaConstants.regulatedBy, regulation);
                    reactions.add(regulatedEntity);
                }
            }
            dba.startTransaction();
            // Don't forget to add IE
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance reaction : reactions) {
                System.out.println("Updating " + reaction + "...");
                dba.updateInstanceAttribute(reaction, ReactomeJavaConstants.regulatedBy);
                reaction.getAttributeValue(ReactomeJavaConstants.modified);
                reaction.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(reaction, ReactomeJavaConstants.modified);
            }
            dba.commit();
        }
        catch(Exception e) {
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
            if (!regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                rtn.add(regulation);
            }
        }
        return rtn;
    }
    
    public boolean checkErrors(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> wrongRegulatedEntity = checkRegulatedType(regulations);
        if (wrongRegulatedEntity.size() > 0) {
            System.err.println("Regulation instances have regulatedEntity values are not ReactionlikeEvent: " + wrongRegulatedEntity.size());
            InstanceUtilities.sortInstances(wrongRegulatedEntity);
            System.out.println("DBID\tDisplayName\tRegulatedEntity_DBID\tRegulatedEntity_Class");
            for (GKInstance regulation : wrongRegulatedEntity) {
                GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                System.out.println(regulation.getDBID() + "\t" + 
                                   regulation.getDisplayName() + "\t" + 
                                   regulatedEntity.getDBID() + "\t" + 
                                   regulatedEntity.getSchemClass().getName());
            }
            return true;
        }
        return false;
    }

}
