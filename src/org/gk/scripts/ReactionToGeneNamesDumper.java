/*
 * Created on Aug 22, 2012
 *
 */
package org.gk.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JFileChooser;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.util.FileUtilities;
import org.gk.util.StringUtils;
import org.junit.Test;

/**
 * This class is used to dump human ReactionLikeEvent to gene names.
 * @author gwu
 *
 */
public class ReactionToGeneNamesDumper {
    
    public ReactionToGeneNamesDumper() {
        
    }
    
    @Test
    public void checkReactionsInProteinList() throws Exception {
        //Set<String> proteins = getProteins();
        Set<String> proteins = getGenes();
        System.out.println("Total proteins: " + proteins.size());
        
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost",
                                            "test_slice_59",
                                            "root", 
                                            "macmysql01");
        GKInstance human = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
                                                                        ReactomeJavaConstants.species, 
                                                                        "=",
                                                                        human);
        System.out.println("Total reactions: " + reactions.size());
        int count = 0;
        for (GKInstance reaction : reactions) {
//            Set<String> rxtProteins = getProteinsInReaction(reaction);
            List<String> rxtProteins = getGenesInReaction(reaction);
            if (rxtProteins.size() == 0)
                continue;
            rxtProteins.retainAll(proteins);
            if (rxtProteins.size() == 0)
                continue;
            count ++;
        }
        System.out.println("Reactions having proteins: " + count);
    }
    
    private Set<String> getGenes() throws IOException {
        String fileName = "/Users/gwu/Dropbox/NURSA_Funding/CoregulatorsInNURSA.txt";
        Set<String> rtn = new HashSet<String>();
        FileUtilities fu = new FileUtilities();
        fu.setInput(fileName);
        String line = fu.readLine();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            rtn.add(tokens[1]);
        }
        return rtn;
    }
    
    private Set<String> getProteins() throws IOException {
        String fileName = "/Users/gwu/Dropbox/NURSA_Funding/NRsInReactome.txt";
        Set<String> rtn = new HashSet<String>();
        FileUtilities fu = new FileUtilities();
        fu.setInput(fileName);
        String line = null;
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
//            if (rtn.contains(tokens[1]))
//                System.out.println("Duplicated: " + tokens[1]);
            rtn.add(tokens[1]);
        }
        fu.close();
        return rtn;
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void dump() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost",
                                            "gk_current_ver41",
                                            "root", 
                                            "macmysql01");
        GKInstance human = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
                                                                        ReactomeJavaConstants.species, 
                                                                        "=",
                                                                        human);
        System.out.println("Total reactions: " + reactions.size());
        String fileName = "tmp/HumanReactionsToGenes.txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine("DB_ID\tName\tType\tNumberOfGenes\tGenes");
        for (GKInstance rxt : reactions) {
            List<String> genes = getGenesInReaction(rxt);
            fu.printLine(rxt.getDBID() + "\t" +
                    rxt.getDisplayName() + "\t" + 
                    rxt.getSchemClass().getName() + "\t" + 
                    genes.size() + "\t" +
                    StringUtils.join(",", genes));
        }
        fu.close();
    }
    
    private Set<String> getProteinsInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> refEntities = InstanceUtilities.grepRefPepSeqsFromPathway(reaction);
        Set<String> proteins = new HashSet<String>();
        for (GKInstance refEntity : refEntities) {
            if (refEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct)) {
                String identifier = (String) refEntity.getAttributeValue(ReactomeJavaConstants.identifier);
                if (identifier != null)
                    proteins.add(identifier);
            }
        }
        return proteins;
    }
    
    private List<String> getGenesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> refEntities = InstanceUtilities.grepRefPepSeqsFromPathway(reaction);
        Set<String> geneNames = new HashSet<String>();
        for (GKInstance refEntity : refEntities) {
            if (refEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.geneName)) {
                String geneName = (String) refEntity.getAttributeValue(ReactomeJavaConstants.geneName);
                if (geneName != null)
                    geneNames.add(geneName);
            }
        }
        List<String> list = new ArrayList<String>(geneNames);
        Collections.sort(list);
        return list;
    }
    
}
