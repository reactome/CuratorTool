package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.database.SynchronizationManager;
import org.gk.elv.InstanceCloneHelper;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.junit.Test;

/**
 * This class is used to convert normalReaction slot from multiple valued to single valued.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class UpdateNormalReactionSlot1 {
    private final String PROJECT_DIR = "/Users/wug/Documents/wgm/work/reactome/CuratorsProjects/";
    
    public UpdateNormalReactionSlot1() {
        
    }
    
    public Neo4JAdaptor getDBA() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("reactomerelease.oicr.on.ca",
                                            "test_slice_64",
                                            "",
                                            "");
        return dba;
    }
    
    @Test
    public void generateProject() throws Exception {
        Neo4JAdaptor dba = getDBA();
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                                                ReactomeJavaConstants.normalReaction,
                                                                "IS NOT NULL",
                                                                null);
        List<GKInstance> dbInsts = new ArrayList<>();
        for (GKInstance inst : c) {
            List<GKInstance> normalReactions = inst.getAttributeValuesList(ReactomeJavaConstants.normalReaction);
            if (normalReactions.size() > 1)
                dbInsts.add(inst);
        }
        System.out.println("Total reactions to be checked out: " + dbInsts.size());
        dbInsts.forEach(System.out::println);
        if (true)
            return;
        // We also need to have pathways referring these reactions
        Set<GKInstance> pathways = new HashSet<>();
        for (GKInstance inst : dbInsts) {
            c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                    ReactomeJavaConstants.hasEvent,
                    "=",
                    inst);
            if (c.size() == 0) {
                System.err.println("Cannot find pathway for " + inst);
                continue;
            }
            pathways.addAll(c);
        }
        System.out.println("Total pathways to be checked out: " + pathways.size());
        // Download it
        PersistenceManager manager = PersistenceManager.getManager();
        manager.setActiveNeo4JAdaptor(dba);
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        manager.setActiveFileAdaptor(fileAdaptor);
        SynchronizationManager syncManager = SynchronizationManager.getManager();
        syncManager.checkOut(dbInsts, null);
        syncManager.checkOut(new ArrayList<>(pathways), null);
        String fileName = PROJECT_DIR + "NormalReactionSlotFix_Before.rtpj";
        fileAdaptor.save(fileName);
    }
    
    @Test
    public void splitNormalReactions() throws Exception {
        // Need some configration
        ScriptUtilities.setUpAttrinuteEditConfig();
        
        String srcFileName = PROJECT_DIR + "NormalReactionSlotFix_Before.rtpj";
        String targetFileName = PROJECT_DIR + "NormalReactionSlotFix_After.rtpj";
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(srcFileName);
        Collection<GKInstance> reactions = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        int c = 0;
        for (GKInstance reaction : reactions) {
            List<GKInstance> normalReactions = reaction.getAttributeValuesList(ReactomeJavaConstants.normalReaction);
            if (normalReactions.size() < 2)
                continue;
            System.out.println("Working on " + reaction + "...");
            splitNormalReactions(reaction, normalReactions, fileAdaptor);
            c ++;
        }
        System.out.println("Total finished: " + c);
        fileAdaptor.save(targetFileName);
    }
    
    private void splitNormalReactions(GKInstance reaction,
                                      List<GKInstance> normalReactions,
                                      XMLFileAdaptor fileAdaptor) throws Exception {
        // Get the first for the current reaction
        GKInstance first = normalReactions.get(0);
        reaction.setAttributeValue(ReactomeJavaConstants.normalReaction, first);
        fileAdaptor.markAsDirty(first);
        Collection<GKInstance> pathways = fileAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                ReactomeJavaConstants.hasEvent,
                "=",
                reaction);
        InstanceCloneHelper cloneHelper = new InstanceCloneHelper();
        for (int i = 1; i < normalReactions.size(); i++) {
            GKInstance clone = cloneHelper.cloneInstance(reaction, fileAdaptor);
            GKInstance normal = normalReactions.get(i);
            clone.setAttributeValue(ReactomeJavaConstants.normalReaction, normal);
            for (GKInstance pathway : pathways) {
                pathway.addAttributeValue(ReactomeJavaConstants.hasEvent, clone);
                fileAdaptor.markAsDirty(pathway);
            }
        }
    }

}
