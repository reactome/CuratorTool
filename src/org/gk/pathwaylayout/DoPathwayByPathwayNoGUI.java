package org.gk.pathwaylayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.*;
import org.gk.render.Node;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderableEntity;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableProtein;
import org.gk.render.RenderableReaction;
import org.gk.render.RenderableRegistry;
import org.gk.schema.Schema;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.junit.Test;

public class DoPathwayByPathwayNoGUI {

    @Test
    public void generateAuthorToolFileForSinglePathwayTest() throws Exception {
        generateAuthorToolFileForSinglePathway(false);
        generateAuthorToolFileForSinglePathway(true);
    }

    /**
     * This method is used to test generate an author tool file based on Graph
     * generated by PathwayByPathway layout engine.
     *
     * @throws Exception Thrown if unable to connect to PersistenceAdaptor or retrieve information
     */
    private void generateAuthorToolFileForSinglePathway(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost",
                    "reactome_25_pathway_diagram",
                    "root",
                    "macmysql01",
                    3306);
        GKInstance apoptosis = dba.fetchInstance(109581L);
        Project project = convertToATProject(apoptosis);
        GKBWriter writer = new GKBWriter();
        writer.save(project, "tmp/Apoptosis_Directly.gkb");
    }

    public Project convertToATProject(GKInstance pathway) throws Exception {
        PersistenceAdaptor dba = pathway.getDbAdaptor();
        GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
        PathwayByPathwayNoGUI pathwayLayout = new PathwayByPathwayNoGUI(dba,
                species,
                pathway);
        return convertToATProject(pathwayLayout);
    }

    public Project convertToATProject(PathwayByPathway pathwayLayout) throws Exception {
        GKInstance pathway = pathwayLayout.focusPathway;
        Map<Vertex, Node> vToNode = new HashMap<Vertex, Node>();
        RenderablePathway renderablePathway = new RenderablePathway(pathway.getDisplayName());
        RenderableRegistry registry = RenderableRegistry.getRegistry();
        registry.add(renderablePathway);
        List<Vertex> reactionVertex = new ArrayList<Vertex>();
        for (Vertex v : pathwayLayout.verteces) {
            GKInstance inst = (GKInstance) v.getUserObject();
            Node node = null;
            if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                //node = new RenderableEntity();
                //node.setDisplayName(inst.getDBID() + "");
                reactionVertex.add(v);
                continue;
            } else if (inst.getSchemClass().isa(ReactomeJavaConstants.GenomeEncodedEntity)) {
                node = new RenderableProtein();
                assignShortName(node, inst);
            } else if (inst.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                node = new RenderableComplex();
                assignShortName(node, inst);
            } else {
                node = new RenderableEntity();
                assignShortName(node, inst);
            }
            Node shortcut = (Node) registry.getSingleObject(node.getDisplayName());
            if (shortcut != null) {
                node = (Node) shortcut.generateShortcut();
            } else
                registry.add(node);
            // To map back
            node.setReactomeId(inst.getDBID());
            renderablePathway.addComponent(node);
            vToNode.put(v, node);
            node.setPosition((int) (v.getBounds().x * PathwayLayoutConstants.ZOOM_LEVELS[0]),
                    (int) (v.getBounds().y * PathwayLayoutConstants.ZOOM_LEVELS[0]));
        }
        Map<Vertex, List<Edge>> rxtVertexToEdges = generateRxtVertexToEdges(pathwayLayout.edges);
        for (Vertex rxtVertex : rxtVertexToEdges.keySet()) {
            GKInstance rxtInstance = (GKInstance) rxtVertex.getUserObject();
            List<Edge> edges = rxtVertexToEdges.get(rxtVertex);
            // Create a reaction
            RenderableReaction reaction = new RenderableReaction();
            reaction.setPosition((int) (rxtVertex.getBounds().x * PathwayLayoutConstants.ZOOM_LEVELS[0]),
                    (int) (rxtVertex.getBounds().y * PathwayLayoutConstants.ZOOM_LEVELS[0]));
            renderablePathway.addComponent(reaction);
            reaction.setDisplayName(rxtInstance.getDisplayName());
            registry.add(reaction);
            reaction.setReactomeId(rxtInstance.getDBID());
            // Need to figure out the inputs and outputs
            for (Edge edge : edges) {
                Vertex source = edge.getSourceVertex();
                Vertex target = edge.getTargetVertex();
                if (source == rxtVertex) {
                    Node output = vToNode.get(target);
                    if (output != null)
                        reaction.addOutput(output);
                } else if (target == rxtVertex) {
                    Node input = vToNode.get(source);
                    if (input != null) {
                        if (edge.edgeType == PathwayLayoutConstants.CATALYST_EDGE) {
                            reaction.addHelper(input);
                        } else if (edge.edgeType == PathwayLayoutConstants.NEGREGULATION_EDGE) {
                            reaction.addInhibitor(input);
                        } else if (edge.edgeType == PathwayLayoutConstants.INPUT_EDGE) {
                            reaction.addInput(input);
                        } else {
                            reaction.addActivator(input);
                        }
                    }
                }
            }
        }
        Project project = new Project();
        project.setProcess(renderablePathway);
        return project;
    }

    private Map<Vertex, List<Edge>> generateRxtVertexToEdges(Set<Edge> edges) {
        Map<Vertex, List<Edge>> reactionVertexToEdges = new HashMap<Vertex, List<Edge>>();
        for (Edge edge : edges) {
            Vertex source = edge.getSourceVertex();
            GKInstance sourceInst = (GKInstance) source.getUserObject();
            Vertex target = edge.getTargetVertex();
            GKInstance targetInst = (GKInstance) target.getUserObject();
            Vertex rxtVertex = null;
            if (sourceInst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                rxtVertex = source;
            } else if (targetInst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                rxtVertex = target;
            }
            if (rxtVertex == null)
                continue;
            List<Edge> list = reactionVertexToEdges.get(rxtVertex);
            if (list == null) {
                list = new ArrayList<Edge>();
                reactionVertexToEdges.put(rxtVertex, list);
            }
            list.add(edge);
        }
        return reactionVertexToEdges;
    }

    private void assignShortName(Node node,
                                 GKInstance instance) throws Exception {
        boolean hasBeenAssigned = false;
        if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.shortName)) {
            String name = (String) instance.getAttributeValue(ReactomeJavaConstants.shortName);
            if (name != null) {
                node.setDisplayName(name);
                hasBeenAssigned = true;
            }
        }
        if (!hasBeenAssigned) {
            String shortName = Utils.findShortestName(instance);
            if (shortName != null) {
                shortName = shortName.replaceAll("\\(name copied from entity in Homo sapiens\\)", "");
                node.setDisplayName(shortName);
            }
        }
    }

    @Test
    public void testGenerateImageForSinglePathwayTest() throws Exception {
        testGenerateImageForSinglePathway(false);
        testGenerateImageForSinglePathway(true);
    }

    /**
     * This method is used to test diagram generation for a single pathway.
     *
     * @throws Exception Thrown if unable to connect to PersistenceAdaptor or retrieve information
     */
    private void testGenerateImageForSinglePathway(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost",
                    "reactome_25_pathway_diagram",
                    "root",
                    "macmysql01",
                    3306);
        GKInstance apoptosis = dba.fetchInstance(109581L);
        GKInstance human = dba.fetchInstance(48887L);
        PathwayByPathwayNoGUI pathwayLayout = new PathwayByPathwayNoGUI(dba,
                human,
                apoptosis);
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Usage java -Djava.awt.headless=true DoPathwayByPathwayNoGUI dbHost dbName dbUser dbPwd dbPort use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            System.exit(0);
        }
        try {
            PersistenceAdaptor dba = null;
            boolean useNeo4J = Boolean.parseBoolean(args[5]);
            if (useNeo4J)
                dba = new Neo4JAdaptor(args[0], args[1], args[2], args[3], Integer.parseInt(args[4]));
            else
                dba = new MySQLAdaptor(args[0], args[1], args[2], args[3], Integer.parseInt(args[4]));
            Schema schema = dba.getSchema();
            SchemaClass ReactionlikeEvent = schema.getClassByName(ReactomeJavaConstants.ReactionlikeEvent);
            SchemaAttribute locatedEvent = schema.getClassByName(ReactomeJavaConstants.ReactionCoordinates).getAttribute(ReactomeJavaConstants.locatedEvent);
            ReverseAttributeQueryRequest aqr1 =
                    new ReverseAttributeQueryRequest(ReactionlikeEvent, locatedEvent, "IS NOT NULL", null);
            QueryRequestList qrl1 = new QueryRequestList();
            qrl1.add(aqr1);
            SchemaClass Species = dba.getSchema().getClassByName(ReactomeJavaConstants.Species);
            SchemaAttribute species = dba.getSchema().getClassByName(ReactomeJavaConstants.ReactionlikeEvent).getAttribute(ReactomeJavaConstants.species);
            ReverseAttributeQueryRequest aqr2 =
                    new ReverseAttributeQueryRequest(Species, species, "=", qrl1);
            Collection<GKInstance> c = dba.fetchInstance(aqr2);
            //c = dba.fetchInstanceByAttribute("Species", "DB_ID", "=", 48887);
            if ((c != null) && !c.isEmpty()) {
                for (GKInstance focusSpecies : c) {
                    System.out.println("Handling pathways from " + focusSpecies);
                    Collection<GKInstance> topPathways = Utils.getTopLevelPathwaysForSpecies(dba, focusSpecies);
                    for (GKInstance pathway : topPathways) {
                        System.out.println("Now handling " + pathway);
                        PathwayByPathwayNoGUI pbp = new PathwayByPathwayNoGUI(dba, focusSpecies, pathway);
                    }
                }
            } else {
                System.out.println("No species with located Reactions!?");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
