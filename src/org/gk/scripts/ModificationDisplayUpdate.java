/*
 * Created on Jan 30, 2016
 *
 */
package org.gk.scripts;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.gkCurator.authorTool.ModifiedResidueHandler;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.PathwayDiagramGeneratorViaAT;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.DiagramGKBWriter;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.qualityCheck.DiagramNodeAttachmentCheck;
import org.gk.render.Node;
import org.gk.render.NodeAttachment;
import org.gk.render.Renderable;
import org.gk.render.RenderableFeature;
import org.gk.render.RenderablePathway;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * This class is used to auto layout modifications displayed in nodes in pathway diagrams
 * against a database.
 *
 * @author gwu
 */
@SuppressWarnings("unchecked")
public class ModificationDisplayUpdate {

    /**
     * Default constructor.
     */
    public ModificationDisplayUpdate() {
    }

    @Test
    public void testFillPsiModLabels() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                "test_gk_central_schema_update_gw",
                "root",
                "macmysql01");
        fillPsiModLabels(dba);
    }

    /**
     * Load values and fill PsiMod lables.
     *
     * @param dba
     * @throws Exception
     */
    public void fillPsiModLabels(PersistenceAdaptor dba) throws Exception {
        String srcFileName = "psiModAbbreviations.txt";
        Map<Long, String> dbIdToLabel = Files.lines(Paths.get(srcFileName))
                .map(line -> line.split("\t"))
                .filter(tokens -> tokens[2].length() > 0)
                .collect(Collectors.toMap(tokens -> new Long(tokens[0]),
                        tokens -> tokens[2]));
//       dbIdToLabel.forEach((key, value) -> System.out.println(key + "\t" + value));

        Collection<GKInstance> psiMods = dba.fetchInstancesByClass(ReactomeJavaConstants.PsiMod);
        List<GKInstance> toBeUpdated = new ArrayList<>();
        for (GKInstance psiMod : psiMods) {
            String label = dbIdToLabel.get(psiMod.getDBID());
            if (label == null)
                continue;
            psiMod.setAttributeValue(ReactomeJavaConstants.label,
                    label);
            toBeUpdated.add(psiMod);
        }
        System.out.println("Total to be updated: " + toBeUpdated.size());
        // Start update now
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, tx);
                for (GKInstance psiMod : toBeUpdated) {
                    System.out.println("Updating " + psiMod + "...");
                    dba.updateInstanceAttribute(psiMod, ReactomeJavaConstants.label, tx);
                    ScriptUtilities.addIEToModified(psiMod,
                            defaultIE,
                            dba, tx);
                }
                tx.commit();
            }
        } else {
            // MySQL
            if (((MySQLAdaptor) dba).supportsTransactions())
                ((MySQLAdaptor) dba).startTransaction();
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                    ScriptUtilities.GUANMING_WU_DB_ID,
                    true, null);
            for (GKInstance psiMod : toBeUpdated) {
                System.out.println("Updating " + psiMod + "...");
                dba.updateInstanceAttribute(psiMod, ReactomeJavaConstants.label, null);
                ScriptUtilities.addIEToModified(psiMod,
                        defaultIE,
                        dba, null);
            }
            if (((MySQLAdaptor) dba).supportsTransactions())
                ((MySQLAdaptor) dba).commit();
        }
        System.out.println("Finished update PsiMod's labels.");
    }

    /**
     * This method is used to fix node features based on the new label values in PsiMod
     * classes.
     *
     * @param dba
     * @throws Exception
     */
    public void updateNodeFeatures(PersistenceAdaptor dba) throws Exception {
        long time1 = System.currentTimeMillis();
        // Make sure if these static variable values are used
        Node.setWidthRatioOfBoundsToText(1.0d);
        Node.setHeightRatioOfBoundsToText(1.0d);
        // Update for all pathway diagrams include human, plant reactome diagrams and other species
        Collection<GKInstance> pdsToUpdate = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        System.out.println("Total PathwayDiagrams for checking: " + pdsToUpdate.size());
        updateNodeFeatures(pdsToUpdate, dba);
        long time2 = System.currentTimeMillis();
        System.out.println("Total time: " + (time2 - time1) / 1000.0d + " seconds");
    }

    private void updateNodeFeatures(Collection<GKInstance> pds, PersistenceAdaptor dba) throws Exception {
        DiagramGKBReader diagramReader = new DiagramGKBReader();
        DiagramGKBWriter diagramWriter = new DiagramGKBWriter();
        PathwayEditor editor = new PathwayEditor(); // To validate bounds
        PathwayDiagramGeneratorViaAT generator = new PathwayDiagramGeneratorViaAT();
        DiagramNodeAttachmentCheck check = new DiagramNodeAttachmentCheck();
        ModifiedResidueHandler handler = new ModifiedResidueHandler();
        int count = 0;
        // Now for the update
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                Long defaultPersonId = ScriptUtilities.GUANMING_WU_DB_ID;
                GKInstance ie = ScriptUtilities.createDefaultIE(dba,
                        defaultPersonId,
                        true, tx);
                System.out.println("Default IE: " + ie);

                for (GKInstance pd : pds) {
                    RenderablePathway pathway = diagramReader.openDiagram(pd);
                    List<Node> nodesToBeUpdated = getNodesForUpdateFeatures(pathway, check, dba);
                    if (nodesToBeUpdated.size() == 0)
                        continue;
                    System.out.println(count + ": " + pd);
                    // Do actual update
                    editor.setRenderable(pathway);
                    // Just to make the tightNodes() work, have to do an extra paint
                    // to make textBounds correct
                    generator.paintOnImage(editor);
                    editor.tightNodes(true);
                    // Need to use the graphics context in the image for bounds validation
                    BufferedImage image = generator.paintOnImage(editor); // Second draw
                    Graphics g = image.getGraphics();
                    for (Node node : nodesToBeUpdated) {
                        GKInstance instance = dba.fetchInstance(node.getReactomeId());
                        updateNodeFeatures(node,
                                instance,
                                g,
                                handler);
                    }
                    updateDbDiagram(dba,
                            diagramWriter,
                            ie,
                            pd,
                            pathway, tx);
                    System.out.println("Updated: " + nodesToBeUpdated.size() + " modifications.");
                    count++;
                }
                System.out.println("Total PathwayDiagram updated: " + count);
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // MySQL
            boolean isTransactionSupported = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).startTransaction();
                Long defaultPersonId = ScriptUtilities.GUANMING_WU_DB_ID;
                GKInstance ie = ScriptUtilities.createDefaultIE(dba,
                        defaultPersonId,
                        true, null);
                System.out.println("Default IE: " + ie);

                for (GKInstance pd : pds) {
                    RenderablePathway pathway = diagramReader.openDiagram(pd);
                    List<Node> nodesToBeUpdated = getNodesForUpdateFeatures(pathway, check, dba);
                    if (nodesToBeUpdated.size() == 0)
                        continue;
                    System.out.println(count + ": " + pd);
                    // Do actual update
                    editor.setRenderable(pathway);
                    // Just to make the tightNodes() work, have to do an extra paint
                    // to make textBounds correct
                    generator.paintOnImage(editor);
                    editor.tightNodes(true);
                    // Need to use the graphics context in the image for bounds validation
                    BufferedImage image = generator.paintOnImage(editor); // Second draw
                    Graphics g = image.getGraphics();
                    for (Node node : nodesToBeUpdated) {
                        GKInstance instance = dba.fetchInstance(node.getReactomeId());
                        updateNodeFeatures(node,
                                instance,
                                g,
                                handler);
                    }
                    updateDbDiagram(dba,
                            diagramWriter,
                            ie,
                            pd,
                            pathway, null);
                    System.out.println("Updated: " + nodesToBeUpdated.size()  + " modifications.");
                    count ++;
                }
                System.out.println("Total PathwayDiagram updated: " + count);
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).rollback();
                e.printStackTrace();
            }
        }
    }

    /**
     * Return true an update is needed
     *
     * @param diagram
     * @param check
     * @param dba
     * @return
     * @throws Exception
     */
    private List<Node> getNodesForUpdateFeatures(RenderablePathway diagram,
                                                 DiagramNodeAttachmentCheck check,
                                                 PersistenceAdaptor dba) throws Exception {
        List<Renderable> components = diagram.getComponents();
        if (components == null || components.size() == 0)
            return Collections.EMPTY_LIST;
        List<Node> list = new ArrayList<>();
        for (Renderable r : components) {
            if (r.getReactomeId() == null || !(r instanceof Node))
                continue;
            GKInstance inst = dba.fetchInstance(r.getReactomeId());
            if (inst == null)
                continue;
            if (!(inst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)))
                continue;
            Set<String> issues = check.checkNodeFeatures((Node) r, inst);
            if (issues == null || issues.size() == 0)
                continue;
            list.add((Node) r);
        }
        return list;
    }

    private void updateNodeFeatures(Node node,
                                    GKInstance instance,
                                    Graphics g,
                                    ModifiedResidueHandler handler) throws Exception {
        List<GKInstance> residues = instance.getAttributeValuesList(ReactomeJavaConstants.hasModifiedResidue);
        // Features that are supposed to be displayed
        List<NodeAttachment> features = new ArrayList<>();
        for (GKInstance residue : residues) {
            RenderableFeature feature = handler.convertModifiedResidue(residue);
            if (feature == null)
                continue;
            feature.validateBounds(node.getBounds(), g);
            features.add(feature);
        }
        node.setNodeAttachmentsLocally(features);
        node.layoutNodeAttachemtns();
    }

    public void performLayout(PersistenceAdaptor dba) throws Exception {
        long time1 = System.currentTimeMillis();
        // Make sure if these static variable values are used
        Node.setWidthRatioOfBoundsToText(1.0d);
        Node.setHeightRatioOfBoundsToText(1.0d);

        Set<GKInstance> pdsToUpdate = getPDsWithModifications(dba);
        System.out.println("Total PathwayDiagrams having modifications: " + pdsToUpdate.size());
        performLayout(pdsToUpdate, dba);
        long time2 = System.currentTimeMillis();
        System.out.println("Total time: " + (time2 - time1) / 1000.0d + " seconds");
    }

    private void performLayout(Set<GKInstance> pds,
                               PersistenceAdaptor dba) throws Exception {
        DiagramGKBReader diagramReader = new DiagramGKBReader();
        DiagramGKBWriter diagramWriter = new DiagramGKBWriter();
        PathwayEditor editor = new PathwayEditor(); // To validate bounds
        PathwayDiagramGeneratorViaAT generator = new PathwayDiagramGeneratorViaAT();
        int count = 0;
        // Now for the update
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, tx);
                System.out.println("Default IE: " + ie);

                for (GKInstance pd : pds) {
                    System.out.println(count + ": " + pd);
                    RenderablePathway pathway = diagramReader.openDiagram(pd);
                    editor.setRenderable(pathway);
                    // Just to make the tightNodes() work, have to do an extra paint
                    // to make textBounds correct
                    generator.paintOnImage(editor);
                    editor.tightNodes(true);
                    generator.paintOnImage(editor); // Second draw
                    List<Renderable> comps = pathway.getComponents();
                    int total = 0;
                    for (Renderable r : comps) {
                        if (r instanceof Node) {
                            Node node = (Node) r;
                            if (node.getNodeAttachments() != null)
                                total += node.getNodeAttachments().size();
                            node.layoutNodeAttachemtns();
                        }
                    }
                    updateDbDiagram(dba,
                            diagramWriter,
                            ie,
                            pd,
                            pathway, tx);
                    System.out.println("Updated: " + total + " modifications.");
                    count++;
                }
                System.out.println("Total PathwayDiagram updated: " + count);
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // MySQL
            boolean isTransactionSupported = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).startTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance ie = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, null);
                System.out.println("Default IE: " + ie);

                for (GKInstance pd : pds) {
                    System.out.println(count + ": " + pd);
                    RenderablePathway pathway = diagramReader.openDiagram(pd);
                    editor.setRenderable(pathway);
                    // Just to make the tightNodes() work, have to do an extra paint
                    // to make textBounds correct
                    generator.paintOnImage(editor);
                    editor.tightNodes(true);
                    generator.paintOnImage(editor); // Second draw
                    List<Renderable> comps = pathway.getComponents();
                    int total = 0;
                    for (Renderable r : comps) {
                        if (r instanceof Node) {
                            Node node = (Node) r;
                            if (node.getNodeAttachments() != null)
                                total += node.getNodeAttachments().size();
                            node.layoutNodeAttachemtns();
                        }
                    }
                    updateDbDiagram(dba,
                            diagramWriter,
                            ie,
                            pd,
                            pathway, null);
                    System.out.println("Updated: " + total  + " modifications.");
                    count ++;
                }
                System.out.println("Total PathwayDiagram updated: " + count);
                ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (isTransactionSupported)
                    ((MySQLAdaptor) dba).rollback();
                e.printStackTrace();
            }
        }
    }

    private void updateDbDiagram(PersistenceAdaptor dba,
                                 DiagramGKBWriter diagramWriter,
                                 GKInstance newIE,
                                 GKInstance pd,
                                 RenderablePathway diagram,
                                 Transaction tx) throws Exception {
        String xml = diagramWriter.generateXMLString(diagram);
        pd.setAttributeValue(ReactomeJavaConstants.storedATXML, xml);
        // have to get all modified attributes first. Otherwise, the original
        // values will be lost
        pd.getAttributeValue(ReactomeJavaConstants.modified);
        pd.addAttributeValue(ReactomeJavaConstants.modified, newIE);
        dba.updateInstanceAttribute(pd, ReactomeJavaConstants.storedATXML, tx);
        dba.updateInstanceAttribute(pd, ReactomeJavaConstants.modified, tx);
    }

    /**
     * Get a set of PathwayDiagram instances that show modifications.
     *
     * @param dba
     * @return
     * @throws Exception
     */
    private Set<GKInstance> getPDsWithModifications(PersistenceAdaptor dba) throws Exception {
        Collection<GKInstance> pds = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        DiagramGKBReader reader = new DiagramGKBReader();
        Set<GKInstance> rtn = new HashSet<GKInstance>();
        for (GKInstance pd : pds) {
            GKInstance pathway = (GKInstance) pd.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (pathway == null)
                continue;
            GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
            if (!species.getDisplayName().equals("Homo sapiens")) // Update for human only
                continue;
            RenderablePathway diagram = reader.openDiagram(pd);
            List<Renderable> components = diagram.getComponents();
            for (Renderable r : components) {
                if (r instanceof Node) {
                    Node node = (Node) r;
                    List<NodeAttachment> attachments = node.getNodeAttachments();
                    if (attachments != null && attachments.size() > 0) {
                        rtn.add(pd);
                        break;
                    }
                }
            }
        }
        return rtn;
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Java -Xmx8G org.gk.scripts.ModificationDisplayUpdate dbHost dbName dbUser dbPwd {fill|regulation|ca|update|default} use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            System.exit(1);
        }
        try {
            PersistenceAdaptor dba = null;
            boolean useNeo4J = Boolean.parseBoolean(args[5]);
            if (useNeo4J)
                dba = new Neo4JAdaptor(args[0],
                        args[1],
                        args[2],
                        args[3]);
            else
                dba = new MySQLAdaptor(args[0],
                        args[1],
                        args[2],
                        args[3]);
            String operation = args[4];
            if (operation.equals("fill")) {
                ModificationDisplayUpdate update = new ModificationDisplayUpdate();
                update.fillPsiModLabels(dba);
            } else if (operation.equals("regulation")) {
                RegulationMigration runner = new RegulationMigration();
                runner.handleRegulationReferences(dba);
            } else if (operation.equals("ca")) {
                RegulationMigration runner = new RegulationMigration();
                runner.handleCatalystActivityRefereces(dba);
            } else if (operation.equals("update")) {
                ModificationDisplayUpdate update = new ModificationDisplayUpdate();
                update.updateNodeFeatures(dba);
            } else if (operation.equals("default")) {
                ModificationDisplayUpdate update = new ModificationDisplayUpdate();
                update.performLayout(dba);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
