/*
 * Created on Aug 20, 2004
 */
package org.reactome.test;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jp.sbi.celldesigner.blockDiagram.diagram.Annotation.EffectInfo;

import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.schema.GKSchema;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.Schema;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.AuthorToolAppletUtilities;
import org.gk.util.SwingImageCreator;
import org.junit.Test;

/**
 * 
 * @author wugm
 */
public class MySQLAdaptorTest {
    private MySQLAdaptor adaptor = null;
    
    public MySQLAdaptorTest() {
    }
    
    public void generateLocalSchema() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("reactomedev.oicr.on.ca", 
                                            "test_gk_central_new_schema_041212",
                                            "authortool",
                "T001test");
        Schema schema = dba.getSchema();
        AuthorToolAppletUtilities.saveLocalSchema(schema);
    }
    
    public void checkSchema() throws Exception {
        GKSchema schema = (GKSchema) adaptor.getSchema();
        Collection<?> attributes = schema.getAttributes();
        for (Object obj : attributes) {
            GKSchemaAttribute att = (GKSchemaAttribute) obj;
            System.out.println(att.getName());
        }
        String attName = "functionalStatusType";
        SchemaAttribute att1 = schema.getAttributeByName(attName);
        SchemaAttribute att2 = schema.getClassByName("FunctionalStatus").getAttribute(attName);
        System.out.println(att1 == att2);
    }
    
    public void testGetReleaseNumber() throws Exception {
        Integer releaseNumber = adaptor.getReleaseNumber();
        System.out.println("Release number in " + adaptor.getDBName() + ": " + releaseNumber);
    }
    
    public void testReferrers() throws Exception {
        SchemaClass cls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase);
        Collection c = cls.getReferers();
        for (Iterator it = c.iterator(); it.hasNext();) {
            SchemaAttribute att = (SchemaAttribute) it.next();
            System.out.println("Attribute in Referres: " + att.toString());
            Collection c1 = ((GKSchemaClass)cls).getReferersByName(att.getName());
            for (Iterator it1 = c1.iterator(); it1.hasNext();)
                System.out.println("\t" + it1.next());
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testQuery() {
//        try {
//            Collection collection = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.LiteratureReference,
//                                                                     ReactomeJavaConstants.pages,
//                                                                     "like",
//                    "133-%");
//            System.out.println("Query for literature based on startPage:\n" + collection);
//            collection = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.LiteratureReference,
//                                                          ReactomeJavaConstants.pages,
//                                                          "like",
//                    "%-63");
//            System.out.println("Query for literature based on endPage:");
//            for (Iterator it = collection.iterator(); it.hasNext();) {
//                GKInstance instance = (GKInstance) it.next();
//                System.out.println(instance.toString());
//            }
//        }
//        catch(Exception e) {
//            e.printStackTrace();
//        }
        
        try {
            MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                                "gk_central_062713", 
                                                "root", 
                                                "macmysql01",
                                                3306);
            GKInstance inst = dba.fetchInstance(1592491L);
            System.out.println(inst);
            Collection<?> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseObject,
                                                           ReactomeJavaConstants.modified, 
                                                           "=",
                                                           inst);
            System.out.println("Returned: " + c.size());
//            if (true)
//                return;
            // Human Signaling by EGFR DB_ID
            Long dbId = 177929L;
            GKInstance egfr = dba.fetchInstance(dbId);
            System.out.println("Human EGFR signaling: " + egfr);
            // Get the first sub-pathway
            GKInstance firstSub = (GKInstance) egfr.getAttributeValue(ReactomeJavaConstants.hasEvent);
            System.out.println("\nFirst sub: " + firstSub);
            // Get its contained sub-pathways
            List<GKInstance> hasEvent = egfr.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
            System.out.println("\nhasEvent: " + hasEvent.size() + " instances");
            for (GKInstance subEvent : hasEvent)
                System.out.println(subEvent);
            // Query all attribute values
            Collection<SchemaAttribute> attributes = egfr.getSchemClass().getAttributes();
            System.out.println("\nAttribute value counts:");
            for (SchemaAttribute att : attributes) {
                List<?> values = egfr.getAttributeValuesList(att);
                System.out.println(att.getName() + ": " + values.size());
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        
    }
    
    private String displayNameWithSpecies(GKInstance pathway) {
        return pathway.getDisplayName();
    }
    
    public void checkReactionQuery() throws Exception {
        String riceName = "Oryza sativa";
        Collection<?> c = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Species,
                                                           ReactomeJavaConstants._displayName, 
                                                           "=",
                                                           riceName);
        GKInstance rice = (GKInstance) c.iterator().next();
        System.out.println("Rice: " + rice);
        c = adaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        System.out.println("Total reactions: " + c.size());
        c = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                             ReactomeJavaConstants.species,
                                             "=",
                                             rice);
        System.out.println("Total reactions in rice: " + c.size());
        c = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                             ReactomeJavaConstants.species,
                                             "!=",
                                             rice);
        System.out.println("Total reactions not in rice: " + c.size());
        c = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Species,
                                             ReactomeJavaConstants.name, 
                                             "!=",
                                             riceName);
        System.out.println("Total species not rice: " + c.size());
        c = adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                             ReactomeJavaConstants.species,
                                             "=", 
                                             c);
        System.out.println("Based on the above query: " + c.size());
        GKInstance inst = (GKInstance) c.iterator().next();
        GKInstance species = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.species);
        System.out.println("Species in the first reaction: " + species);
    }
    
    /**
     * Test for Willem
     * @return
     */
    public ArrayList getPathwaysFromDatabaseUnitTest(){
        ArrayList returnList =  new ArrayList();
        ArrayList pathwayTree = new ArrayList();
        Hashtable pathway2Parent = new Hashtable();
        Hashtable pathway2Values = new Hashtable();
        ArrayList pathwayInstances = new ArrayList();
        
        try{
            // Get Pathways that have no hasComponent references
            Collection pathways = adaptor.fetchInstancesByClass("Pathway");
            for(Iterator pi = pathways.iterator(); pi.hasNext();){
                GKInstance pathway = (GKInstance)pi.next();
                Collection components = pathway.getReferers("hasComponent");
                if(components != null){
                    pathwayInstances.add(pathway);
                    ArrayList pathwayNames = new ArrayList();
                    pathwayNames.add(displayNameWithSpecies(pathway));
                    pathwayTree.add(pathwayNames);
                }
            }
            // get the rest of the pathways as well
            String[] attributeNames = new String[]{"hasComponent"};
            adaptor.loadInstanceAttributeValues(pathwayInstances, attributeNames);
            for(int pi = 0; pi < pathwayInstances.size(); pi++){
                GKInstance pathway = (GKInstance)pathwayInstances.get(pi);
                Collection components =
                        pathway.getAttributeValuesList("hasComponent");
                if(components.isEmpty() == false){
                    for(Iterator ci = components.iterator(); ci.hasNext();){
                        GKInstance component = (GKInstance)ci.next();
                        
                        if(component.getSchemClass().getName().equalsIgnoreCase("Pathway")){
                            // This component is a pathway
                            ArrayList componentsList = new ArrayList();
                            componentsList.add(component);
                            
                            adaptor.loadInstanceAttributeValues((Collection)componentsList,
                                                                attributeNames);
                            pathwayInstances.addAll(componentsList);
                            
                            pathway2Parent.put(displayNameWithSpecies(component),
                                               displayNameWithSpecies(pathway));
                            // add new entry to to pathwayTree list
                            ArrayList pathwayNames = new ArrayList();
                            pathwayNames.add(displayNameWithSpecies(component));
                            pathwayNames.add(displayNameWithSpecies(pathway));
                            pathwayTree.add(pathwayNames);
                        }
                    }
                }
            }
            // For each GKInstance in pathwayInstances get the amount
            //of reactions in the pathway
            // add this amount to pathway2Values and then the 0th
            //value. For this pathway, but also for it's parents
            for(Iterator ii = pathwayInstances.iterator(); ii.hasNext();){
                GKInstance pathway = (GKInstance)ii.next();
                int reactionCounter = 0;
                int reactionsMapped = 0;
                Collection components =
                        pathway.getAttributeValuesList("hasComponent");
                if(components.isEmpty() == false){
                    for(Iterator ci = components.iterator(); ci.hasNext();){
                        GKInstance component = (GKInstance)ci.next();
                        
                        if(component.getSchemClass().getName().equalsIgnoreCase("Reaction")){
                            // This component is a reaction
                            reactionCounter++;
                        }
                    }
                }
                
                ArrayList parentPathways = new ArrayList();
                parentPathways.add(displayNameWithSpecies(pathway));
                for(int it = 0; it < parentPathways.size(); it++){
                    String pathwayString = (String)parentPathways.get(it);
                    if (pathway2Parent.containsKey(pathwayString)){
                        String parent =
                                (String)pathway2Parent.get(pathwayString);
                        parentPathways.add(parent);
                    }
                    if(pathway2Values.containsKey(pathwayString)){
                        ArrayList values =
                                (ArrayList)pathway2Values.get(pathwayString);
                        int amountReaction =
                                Integer.parseInt((String)values.get(0));
                        int total = amountReaction + reactionCounter;
                        ArrayList valuesToAdd =  new ArrayList();
                        valuesToAdd.add(0, String.valueOf(total));
                        valuesToAdd.add(String.valueOf(reactionsMapped));
                        //pathway2Values.remove(pathwayString);
                        pathway2Values.put(pathwayString, valuesToAdd);
                    }
                    else{
                        ArrayList values = new ArrayList();
                        values.add(String.valueOf(reactionCounter));
                        values.add(String.valueOf(reactionsMapped));
                        pathway2Values.put(pathwayString, values);
                    }
                }
            }
        } catch(Exception e){
            System.out.println("6: Error fetching pathways: " + e);
        }
        
        // return pathwayTree, pathway2Parent and pathway2Values.
        returnList.add(pathwayTree);
        returnList.add(pathway2Parent);
        returnList.add(pathway2Values);
        
        return returnList;
    }
    
    /**
     * We want to get a list of Reactions that are related to EGFR
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testReverseQuery() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            "gk_current_ver41", 
                                            "root", 
                                            "macmysql01");
        
        // Get ReferenceGeneProduct for the specified UniProt accession number
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct,
                                                                ReactomeJavaConstants.identifier,
                                                                "=", 
                                                                "P00533"); // EGFR UniProt accession number
        // Just try the first EGFR protein
        GKInstance rgp = c.iterator().next();
        System.out.println("ReferenceGeneProduct: " + rgp);
        // Get PhysicalEntity using the returned ReferenceGeneProduct
        c = rgp.getReferers(ReactomeJavaConstants.referenceEntity);
        // Multiple PhysicalEntity instances may be returned
        System.out.println("PhysicalEntity: " + c.size());
        for (GKInstance pe : c)
            System.out.println(pe);
        // Get Reactions that use returned PhysicalEntitiy instances
        Set<GKInstance> reactions = new HashSet<GKInstance>();
        String[] attributeNames = new String[] {
                ReactomeJavaConstants.hasComponent, // Complex
                ReactomeJavaConstants.hasMember, // EntitySet
                ReactomeJavaConstants.hasCandidate, // CandidateSet
                ReactomeJavaConstants.repeatedUnit, // Ploymer
                ReactomeJavaConstants.input, // ReactionlikeEvent
                ReactomeJavaConstants.output, // ReactionlikeEvent
                ReactomeJavaConstants.catalystActivity, // ReactionlikeEvent
                ReactomeJavaConstants.physicalEntity, // CatalystActivity
                ReactomeJavaConstants.regulatedEntity, // Regulation
                ReactomeJavaConstants.regulator // Regulation
        };
        Set<GKInstance> current = new HashSet<GKInstance>();
        Set<GKInstance> next = new HashSet<GKInstance>();
        current.addAll(c);
        // To avoid a loop in the reference graph
        Set<GKInstance> checked = new HashSet<GKInstance>();
        while (current.size() > 0) {
            for (GKInstance inst : current) {
                if (checked.contains(inst))
                    continue;
                checked.add(inst);
                for (String attName : attributeNames) {
                    Collection<GKInstance> tmp = inst.getReferers(attName);
                    if (tmp == null || tmp.size() == 0)
                        continue;
                    for (GKInstance referrer : tmp) {
                        if (referrer.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                            reactions.add(referrer);
                        else
                            next.add(referrer);
                    }
                }
            }
            current.clear();
            current.addAll(next);
            next.clear();
        }
        System.out.println("Reactions: " + reactions.size());
        List<GKInstance> instanceList = new ArrayList<GKInstance>(reactions);
        InstanceUtilities.sortInstances(instanceList);
        for (GKInstance event : instanceList)
            System.out.println(event);
    }
    
    /**
     * Get pathway diagram for human cell cycle checkpoint, highlight some proteins 
     * and export as a PDF file.
     * @throws Exception
     */
    @Test
    public void testPathwayDiagram() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_current_ver41",
                                            "root",
                                            "macmysql01");
        // DB_ID for human Cell Cycle Checkpoint
        Long dbId = 69620L;
        GKInstance pathway = dba.fetchInstance(dbId);
        System.out.println("Pathway: " + pathway);
        // Get the PathwayDiagram for this Pathway
        Collection<GKInstance> pathwayDiagrams = pathway.getReferers(ReactomeJavaConstants.representedPathway);
        if (pathwayDiagrams == null || pathwayDiagrams.size() != 1) {
            throw new IllegalStateException("Cannot find a PathwayDigaram or too many PathwayDiagram for " + pathway);
        }
        GKInstance pathwayDiagram = pathwayDiagrams.iterator().next();
        DiagramGKBReader reader = new DiagramGKBReader();
        // RendearblePathway contains all information to render a PathwayDiagram
        RenderablePathway diagram = reader.openDiagram(pathwayDiagram);
        // PathwayEditor is a customized JPanel used to do actually drawing
        PathwayEditor editor = new PathwayEditor();
        editor.setRenderable(diagram);
        // Export this diagram to a PDF file use a utility class
        SwingImageCreator.exportImageInPDF(editor, new File("CellCycleCheckpoint.pdf"));
        // We want to highlight entities related to gene MAD2 in red
        String mad2UniProtId = "Q13257"; // UniProt id for gene MAD2
        // Get a set of DB_IDs for PhysicalEntity objects related to gene MAD2
        Set<Long> mad2dbIds = fetchPhysicalEntityDBIDs(mad2UniProtId, dba);
        // Highlight in red
        // Iterate through contained Renderable objects in the diagram
        List<Renderable> components = diagram.getComponents();
        for (Renderable r : components) {
            if (r.getReactomeId() == null)
                continue; // Simple graph objects
            if (mad2dbIds.contains(r.getReactomeId()))
                r.setBackgroundColor(Color.red); // Highligh in red
        }
        SwingImageCreator.exportImageInPDF(editor, new File("CellCycleCheckpointWithMAD2.pdf"));
    }
    
    @SuppressWarnings("unchecked")
    private Set<Long> fetchPhysicalEntityDBIDs(String uniprotId,
                                               MySQLAdaptor dba) throws Exception {
        Set<Long> dbIds = new HashSet<Long>();
        // Get ReferenceGeneProduct for the specified UniProt id
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct,
                                                                ReactomeJavaConstants.identifier,
                                                                "=", 
                                                                uniprotId); 
        GKInstance rgp = c.iterator().next();
        System.out.println("ReferenceGeneProduct: " + rgp);
        // Get PhysicalEntity using the returned ReferenceGeneProduct
        c = rgp.getReferers(ReactomeJavaConstants.referenceEntity);
        // Get Event that use returned PhysicalEntitiy instances
        Set<GKInstance> reactions = new HashSet<GKInstance>();
        String[] attributeNames = new String[] {
                ReactomeJavaConstants.hasComponent, // Complex
                ReactomeJavaConstants.hasMember, // EntitySet
                ReactomeJavaConstants.hasCandidate, // CandidateSet
        };
        Set<GKInstance> current = new HashSet<GKInstance>();
        Set<GKInstance> next = new HashSet<GKInstance>();
        current.addAll(c);
        while (current.size() > 0) {
            for (GKInstance inst : current) {
                dbIds.add(inst.getDBID());
                for (String attName : attributeNames) {
                    Collection<GKInstance> tmp = inst.getReferers(attName);
                    if (tmp == null || tmp.size() == 0)
                        continue;
                    for (GKInstance inst1 : tmp) {
                        // Avoid a loop in the reference graph
                        if (dbIds.contains(inst1.getDBID()))
                            continue;
                        next.add(inst1);
                    }
                }
            }
            current.clear();
            current.addAll(next);
            next.clear();
        }
        return dbIds;
    }
}
