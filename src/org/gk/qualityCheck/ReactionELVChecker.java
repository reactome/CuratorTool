/*
 * Created on Apr 8, 2010
 *
 */
package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.HyperEdge;
import org.gk.render.RenderablePathway;
import org.gk.schema.GKSchemaClass;
import org.gk.util.FileUtilities;
import org.junit.Test;


/**
 * This class is used to check the usage of ReactionlikeEvent in ELVs. It will check if a ReactionlieEvent
 * has been used by a PathwayDiagram instance, by how many pathway diagram instances.
 * Note: This class will be used for command line checking or servlet only.
 * @author wgm
 *
 */
public class ReactionELVChecker extends AbstractQualityCheck {

    private static Logger logger = Logger.getLogger(ReactionELVChecker.class);
    
    public ReactionELVChecker() {
    }
    
    @Override
    public void check(GKSchemaClass cls) {
    }

    @Override
    public void check() {
    }

    @Override
    public void check(GKInstance instance) {
    }

    @Override
    public void check(List<GKInstance> instances) {
    }

    @Override
    public void checkProject(GKInstance event) {
    }

    @Override
    protected InstanceListPane getDisplayedList() {
        return null;
    }
    
    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = super.checkInCommand();
        if (report == null)
            return null;
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        Map<GKInstance, Set<GKInstance>> reactionToDiagrams = checkEventUsageInELV(dba);
        if (reactionToDiagrams.size() == 0)
            return report;
        String text = convertEventToDiagramMapToText(reactionToDiagrams);
        convertTextToReport(text, report);
        return report;
    }

    /**
     * This method is used to check the usage of ReactionlikeEvent instances in a passed Database.
     * @param dba
     * @return key should be all ReactionlikeEvent instance, while values is a list of PathwayDiagram
     * instances that have used ReactionlikeEvent. If a ReactionlikeEvent has not been used in any place,
     * the value in the map should be null. All instances of ReactionlikeEvent should be in the keys.
     * @throws Exception
     */
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba) throws Exception {
        Map<GKInstance, Set<GKInstance>> reactionToDiagrams = new HashMap<GKInstance, Set<GKInstance>>();
        // Get all ReactionlikeEvent in the database
        Collection reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        if (reactions == null || reactions.size() == 0)
            return reactionToDiagrams;
        // Make sure all reactions can be returned
        for (Iterator it = reactions.iterator(); it.hasNext();) {
            GKInstance reaction = (GKInstance) it.next();
            reactionToDiagrams.put(reaction, null);
        }
        // Load all PathwayDiagrams in the database
        Collection diagrams = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        if (diagrams == null || diagrams.size() == 0)
            return reactionToDiagrams;
        DiagramGKBReader reader = new DiagramGKBReader();
        for (Iterator it = diagrams.iterator(); it.hasNext();) {
            GKInstance diagram = (GKInstance) it.next();
            String xml = (String) diagram.getAttributeValue(ReactomeJavaConstants.storedATXML);
            if (xml == null || xml.length() == 0)
                continue; // Not correct diagrams!!!
            RenderablePathway renderableDiagram = reader.openDiagram(xml);
            // Check with edges
            List components = renderableDiagram.getComponents();
            if (components == null || components.size() == 0)
                continue; // Just in case it is an empty!
            for (Iterator it1 = components.iterator(); it1.hasNext();) {
                Object r = it1.next();
                if (r instanceof HyperEdge) {
                    HyperEdge edge = (HyperEdge) r;
                    if (edge.getReactomeId() == null)
                        continue;
                    GKInstance rxt = dba.fetchInstance(edge.getReactomeId());
                    if (rxt == null) {
                        logger.error("DB_ID has no Reaction in the database for diagram " + diagram.getDisplayName() + ": " + edge.getReactomeId());
                        continue; // Just in case
                    }
                    Set<GKInstance> set = reactionToDiagrams.get(rxt);
                    if (set == null) {
                        set = new HashSet<GKInstance>();
                        reactionToDiagrams.put(rxt, set);
                    }
                    set.add(diagram);
                }
            }
        }
        return reactionToDiagrams;
    }
    
    private void convertTextToReport(String text, QAReport report) throws Exception {
        String[] lines = text.split("\n");
        // First line should be header
        report.setColumnHeaders(lines[0]);
        for (int i = 1; i < lines.length; i++)
            report.addLine(lines[i]);
    }
    
    /**
     * This method is used to convert a Reaction to Diagrams map to a String.
     * @param reactionToDiagrams
     * @return
     * @throws Exception
     */
    public String convertEventToDiagramMapToText(final Map<GKInstance, Set<GKInstance>> reactionToDiagrams) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("Reaction_DB_ID\tReaction_Name\t_doRelease\tSpecies\tIs_in_Disease\tNumber_of_Diagrams\tDiagram_DB_IDs\tDiagram_Names");
        List<GKInstance> instances = new ArrayList<GKInstance>(reactionToDiagrams.keySet());
        Collections.sort(instances, new Comparator<GKInstance>() {
            public int compare(GKInstance rxt1, GKInstance rxt2) {
                Set<GKInstance> set1 = reactionToDiagrams.get(rxt1);
                int size1 = set1 == null ? 0 : set1.size();
                Set<GKInstance> set2 = reactionToDiagrams.get(rxt2);
                int size2 = set2 == null ? 0 : set2.size();
                int rtn = size1 - size2;
                if (rtn == 0) // Based on diagrams
                    return rxt1.getDBID().compareTo(rxt2.getDBID());
                return rtn; 
            }
        });
        for (GKInstance rxt : instances) {
            builder.append("\n");
            builder.append(rxt.getDBID());
            builder.append("\t").append(rxt.getDisplayName());
            Boolean doRelease = (Boolean) rxt.getAttributeValue(ReactomeJavaConstants._doRelease);
            if (doRelease == null)
                doRelease = Boolean.FALSE;
            builder.append("\t").append(doRelease);
            GKInstance species = (GKInstance) rxt.getAttributeValue(ReactomeJavaConstants.species);
            builder.append("\t").append(species == null ? "" : species.getDisplayName());
            if (rxt.getSchemClass().isValidAttribute(ReactomeJavaConstants.disease)) {
                GKInstance disease = (GKInstance) rxt.getAttributeValue(ReactomeJavaConstants.disease);
                builder.append("\t");
                builder.append(disease == null ? "false" : "true");
            }
            else
                builder.append("\tfalse");
            Set<GKInstance> diagrams = reactionToDiagrams.get(rxt);
            if (diagrams == null) {
                builder.append("\t0\t\t");
            }
            else {
                builder.append("\t").append(diagrams.size());
                builder.append("\t");
                // Get DB_IDs
                for (Iterator<GKInstance> it = diagrams.iterator(); it.hasNext();) {
                    GKInstance diagram = it.next();
                    builder.append(diagram.getDBID());
                    if (it.hasNext())
                        builder.append(", ");
                }
                // Get names of diagrams
                builder.append("\t");
                for (Iterator<GKInstance> it = diagrams.iterator(); it.hasNext();) {
                    GKInstance diagram = it.next();
                    builder.append(diagram.getDisplayName());
                    if (it.hasNext())
                        builder.append(", ");
                }
            }
        }
        return builder.toString();
    }
    
    @Test
    public void testCheckReactionsInELVs() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_071712",
                                            "root",
                                            "macmysql01");
        long time1 = System.currentTimeMillis();
        Map<GKInstance, Set<GKInstance>> reactionToDiagrams = checkEventUsageInELV(dba);
        long time2 = System.currentTimeMillis();
        System.out.println("Total reactions checked: " + reactionToDiagrams.size());
        // Check diagrams 
        Set<GKInstance> diagrams = new HashSet<GKInstance>();
        for (Set<GKInstance> set : reactionToDiagrams.values()) {
            if (set != null)
                diagrams.addAll(set);
        }
        System.out.println("Total diagrams checked: " + diagrams.size());
        System.out.println("Time for checking: " + (time2 - time1));
        String output = convertEventToDiagramMapToText(reactionToDiagrams);
        System.out.println("\n\nOutput:");
        FileUtilities fu = new FileUtilities();
        fu.setOutput("test.txt");
        fu.printLine(output);
        fu.close();
    }
    
}
