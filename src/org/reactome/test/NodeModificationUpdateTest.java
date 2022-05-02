/*
 * Created on Jan 26, 2016
 *
 */
package org.reactome.test;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.PathwayDiagramGeneratorViaAT;
import org.gk.pathwaylayout.PredictedPathwayDiagramGeneratorFromDB;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.render.Node;
import org.gk.render.NodeAttachment;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.util.SwingImageCreator;
import org.junit.Test;

/**
 * This class is used to test update of node modifications for better layout.
 * @author gwu
 *
 */
public class NodeModificationUpdateTest {
    
    /**
     * Default constructor.
     */
    public NodeModificationUpdateTest() {
    }
    
    private void exportImage(RenderablePathway pathway,
                             String fileName) throws Exception {
        PathwayEditor editor = new PathwayEditor();
        editor.setHidePrivateNote(true);
        // Used for some preprocessing
        PathwayDiagramGeneratorViaAT generator = new PathwayDiagramGeneratorViaAT();
//        helper.fineTuneDiagram(pathway);
        editor.setRenderable(pathway);
        editor.setHidePrivateNote(true);
        // Just to make the tightNodes() work, have to do an extra paint
        // to make textBounds correct
        generator.paintOnImage(editor);
        editor.tightNodes(true);
        generator.paintOnImage(editor);
        List<Renderable> comps = pathway.getComponents();
        for (Renderable r : comps) {
            if (r instanceof Node) {
                Node node = (Node) r;
                node.layoutNodeAttachemtns();
            }
        }
        SwingImageCreator.exportImageInPDF(editor, new File(fileName));
    }
    
    @Test
    public void testAutolayout() throws Exception {
        // Make sure if these static variable values are used
        Node.setWidthRatioOfBoundsToText(1.0d);
        Node.setHeightRatioOfBoundsToText(1.0d);
        Neo4JAdaptor dba = getDBA();
        // [PathwayDiagram:548647]: has 216 attachments (most)
//        GKInstance inst = dba.fetchInstance(548647L);
//        [PathwayDiagram:453263] Diagram of TCR signaling
//        inst = dba.fetchInstance(453263L);
//        [PathwayDiagram:5633131] Diagram of Regulation of TP53 Expression and Degradation
//        GKInstance inst = dba.fetchInstance(5633131L);
        Long[] dbIds = new Long[] {
                548647L,
                453263L,
                5633131L,
                480992L
        };
        for (Long dbId : dbIds) {
            GKInstance inst = dba.fetchInstance(dbId);
            DiagramGKBReader reader = new DiagramGKBReader();
            RenderablePathway pathway = reader.openDiagram(inst);
            String fileName = "/Users/gwu/Documents/temp/" + inst.getDisplayName() + "_v3.pdf";
            System.out.println(fileName);
            exportImage(pathway, fileName);
        }
    }
    
    @Test
    public void checkModifications() throws Exception {
        Neo4JAdaptor dba = getDBA();
        Collection<GKInstance> pds = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        DiagramGKBReader reader = new DiagramGKBReader();
        final Map<GKInstance, Integer> pdToNumber = new HashMap<GKInstance, Integer>();
        for (GKInstance pd : pds) {
            GKInstance pathway = (GKInstance) pd.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (pathway == null)
                continue;
            GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
            if (!species.getDisplayName().equals("Homo sapiens"))
                continue;
            RenderablePathway diagram = reader.openDiagram(pd);
            int number = 0;
            List<Renderable> components = diagram.getComponents();
            for (Renderable r : components) {
                if (r instanceof Node) {
                    Node node = (Node) r;
                    List<NodeAttachment> attachments = node.getNodeAttachments();
                    if (attachments != null && attachments.size() == 4) {
                        System.out.println(pd);
                        break;
                    }
                    if (attachments != null)
                        number += attachments.size();
                }
            }
            pdToNumber.put(pd, number);
        }
        List<GKInstance> pdList = new ArrayList<GKInstance>(pdToNumber.keySet());
        Collections.sort(pdList, new Comparator<GKInstance>() {
            public int compare(GKInstance inst1, GKInstance inst2) {
                Integer number1 = pdToNumber.get(inst1);
                Integer number2 = pdToNumber.get(inst2);
                return number2.compareTo(number1);
            }
        });
        for (GKInstance pd : pdList)
            System.out.println(pd + "\t" + pdToNumber.get(pd));
    }

    protected Neo4JAdaptor getDBA() throws SQLException {
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost",
                                            "gk_central_012616",
                                            "root",
                                            "macmysql01");
        return dba;
    }
    
}
