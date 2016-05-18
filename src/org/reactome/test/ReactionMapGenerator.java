/*
 * Created on Apr 15, 2016
 *
 */
package org.reactome.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.wsdl.Input;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This test class is used to generate a network of reactions. Reaction1 and Reaction2 will be linked
 * together if reaction1 is annotated as a precedingEvent of Reaction2, or one of outputs, which is not
 * a SimpleEntity, is an input, catalyst, regulator of Reaction2.
 * @author gwu
 *
 */
@SuppressWarnings("unchecked")
public class ReactionMapGenerator {
    private final String DIR_NAME = "/Users/gwu/Documents/wgm/work/reactome/ReactionNetwork/";
    
    /**
     * Default constructor.
     */
    public ReactionMapGenerator() {
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_041416",
                                            "root",
                                            "macmysql01");
        return dba;
    }
    
    @Test
    public void checkMCLClusterResults() throws Exception {
        String file = DIR_NAME + "ReactionNewtork_MCL.txt";
        String outFile = DIR_NAME + "ReactionNetwork_MCL_Reaction.txt";
        FileUtilities fu = new FileUtilities();
        fu.setInput(file);
        fu.setOutput(outFile);
        fu.printLine("Reactome\tMCL_Cluster");
        String line = null;
        int cluster = 1;
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            System.out.println(cluster + "\t" + tokens.length);
            for (String token : tokens)
                fu.printLine(token + "\t" + cluster);
            cluster ++;
        }
        fu.close();
    }
    
    @Test
    public void simplifyNetwork() throws Exception {
        String source = DIR_NAME + "ReactionNetwork.txt";
        String target = DIR_NAME + "ReactionNetworkPair.txt";
        FileUtilities fu = new FileUtilities();
        fu.setInput(source);
        fu.setOutput(target);
        String line = null;
        fu.printLine("Rxt1\tRxt2");
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split(" ");
            fu.printLine(tokens[0] + "\t" + tokens[2]);
        }
        fu.close();
    }
    
    @Test
    public void generate() throws Exception {
        MySQLAdaptor dba = getDBA();
        GKInstance human = dba.fetchInstance(48887L); 
        // Load instances
        Collection<GKInstance> reactions = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                                                        ReactomeJavaConstants.species,
                                                                        "=",
                                                                        human);
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        // Load attributes
        dba.loadInstanceAttributeValues(reactions, 
                                        new String[] {ReactomeJavaConstants.input,
                                                      ReactomeJavaConstants.output,
                                                      ReactomeJavaConstants.catalystActivity,
                                                      ReactomeJavaConstants.precedingEvent});
        dba.loadInstanceAttributeValues(regulations,
                                        new String[] {ReactomeJavaConstants.physicalEntity,
                                                      ReactomeJavaConstants.regulatedEntity});
        List<GKInstance> reactionList = new ArrayList<GKInstance>(reactions);
        filterReaction(reactionList);
        FileUtilities fu = new FileUtilities();
        fu.setOutput("tmp/ReactionNetwork.txt");
        for (int i = 0; i < reactionList.size() - 1; i++) {
            GKInstance reaction1 = reactionList.get(i);
            System.out.println(i + ": " + reaction1);
            for (int j = i + 1; j < reactionList.size(); j++) {
                GKInstance reaction2 = reactionList.get(j);
                boolean isPreceding = isPrecedingTo(reaction1, reaction2);
                if (isPreceding)
                    fu.printLine(reaction1.getDBID() + " preceding " + reaction2.getDBID());
                else {
                    isPreceding = isPrecedingTo(reaction2, reaction1);
                    if (isPreceding)
                        fu.printLine(reaction2.getDBID() + " preceding " + reaction1.getDBID());
                }
            }
        }
        fu.close();
    }
    
    /**
     * Filter reactions annotated for diseases
     * @param reactionList
     * @throws Exception
     */
    private void filterReaction(List<GKInstance> reactionList) throws Exception {
        for (Iterator<GKInstance> it = reactionList.iterator(); it.hasNext();) {
            GKInstance rxt = it.next();
            GKInstance disease = (GKInstance) rxt.getAttributeValue(ReactomeJavaConstants.disease);
            if (disease != null)
                it.remove();
        }
    }
    
    private boolean shouldEscape(GKInstance output) throws Exception {
        if (output.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity))
            return true;
        if (output.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            List<GKInstance> hasMember = output.getAttributeValuesList(ReactomeJavaConstants.hasMember);
            for (GKInstance hasMember1 : hasMember) {
                if (hasMember1.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity))
                    return true;
            }
        }
        return false;
    }
    
    /**
     * Check if reaction1 is preceding to reaction2.
     * @param rxt1
     * @param rxt2
     * @return
     * @throws Exception
     */
    private boolean isPrecedingTo(GKInstance rxt1,
                                  GKInstance rxt2) throws Exception {
        // If rxt1 is in the rxt2's precedingEvent list
        List<GKInstance> precedingEvent2 = rxt2.getAttributeValuesList(ReactomeJavaConstants.precedingEvent);
        if (precedingEvent2.contains(rxt1))
            return true;
        // Check if rxt1's output is in rxt2 input, catalyst, or regulator. 
        // For this test and simplicity, only non-simple molecule entity is tested
        List<GKInstance> output1 = rxt1.getAttributeValuesList(ReactomeJavaConstants.output);
        if (output1.size() == 0)
            return false;
        for (GKInstance output : output1) {
            if (shouldEscape(output))
                continue;
            List<Input> input = rxt2.getAttributeValuesList(ReactomeJavaConstants.input);
            if (input.contains(output))
                return true;
            GKInstance cas = (GKInstance) rxt2.getAttributeValue(ReactomeJavaConstants.catalystActivity);
            if (cas != null && cas.getAttributeValue(ReactomeJavaConstants.physicalEntity) == output)
                return true;
            Collection<GKInstance> regulations = rxt2.getReferers(ReactomeJavaConstants.regulatedEntity);
            if (regulations != null && regulations.size() > 0) {
                for (GKInstance regulation : regulations) {
                    if (regulation.getAttributeValue(ReactomeJavaConstants.regulator) == output)
                        return true;
                }
            }
        }
        return false;
    }
}
