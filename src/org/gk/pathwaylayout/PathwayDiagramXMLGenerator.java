/*
 * Created on Nov 5, 2011
 *
 */
package org.gk.pathwaylayout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.DiagramGKBWriter;
import org.gk.persistence.GKBWriter;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Project;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Test;

/**
 * This class is used to generate an XML for a PathwayDiagram instance based on stored
 * XML string in the database. The generated XML should be sufficient to be used for display
 * a pahway diagram as in the curator tool. In other words, all rendering information should
 * be included in the generated XML.
 * @author gwu
 *
 */
public class PathwayDiagramXMLGenerator {
    
    public PathwayDiagramXMLGenerator() {
        // Make sure if these static variable values are used
        Node.setWidthRatioOfBoundsToText(1.0d);
        Node.setHeightRatioOfBoundsToText(1.0d);
    }
    
    /**
     * Generate an XML file for a specified PathwayDiagram instance.
     * @param pd
     * @param file
     * @throws Exception
     */
    public void generateXMLForPathwayDiagram(GKInstance pd, 
                                             File file) throws Exception {
        generateXMLForPathwayDiagram(pd, new FileOutputStream(file));
    }
    
    /**
     * Generate an XML for a specified PathwayDiagram to an OutputStream object.
     * @param pd
     * @param os
     * @throws Exception
     */
    public void generateXMLForPathwayDiagram(GKInstance pd, 
                                              OutputStream os) throws Exception {
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway pathway = reader.openDiagram(pd);
        // Force to make sure all points are correct validated
        PathwayEditor pathwayEditor = new PathwayEditor();
        pathwayEditor.setRenderable(pathway);
        PathwayDiagramGeneratorViaAT helper = new PathwayDiagramGeneratorViaAT();
        helper.paintOnImage(pathwayEditor);
//        // Do a tight node for avoiding text overflow
        if (isHumanPathway(pd)) {
            // Tight nodes should be applied to human pathways only
            // Entities in predicted diagrams have weird "name copied from...",
            // which may bloat the sizes of nodes there.
            pathwayEditor.tightNodes(true);
            helper.paintOnImage(pathwayEditor);
        }
        GKBWriter writer = getDiagramWriter(pd);
        writer.save(new Project(pathway), 
                    os);
    }
    
    private boolean isHumanPathway(GKInstance pd) throws Exception {
        List<GKInstance> pathways = pd.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
        if (pathways == null || pathways.size() == 0)
            return true;
        for (GKInstance pathway : pathways) {
            List<GKInstance> species = pathway.getAttributeValuesList(ReactomeJavaConstants.species);
            for (GKInstance tmp : species) {
                if (tmp.getDBID().equals(48887L))
                    return true;
            }
        }
        return false;
    }
    
    private GKBWriter getDiagramWriter(GKInstance pd) {
        DiagramGKBWriter writer = new DiagramGKBWriter();
        writer.setNeedRegistryCheck(false);
        writer.setNeedDisplayName(true);
        writer.setPersistenceAdaptor(pd.getDbAdaptor());
        return writer;
    }
    
    /**
     * Generate an XML String object for a specified PathwayDiagram instance.
     * @param pd
     * @return
     * @throws Exception
     */
    public String generateXMLForPathwayDiagram(GKInstance pd) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        generateXMLForPathwayDiagram(pd, bos);
        String text = bos.toString();
        return text;
    }
    
    private String generateXMLForDiseasePathwayDigram(GKInstance pd,
                                                      GKInstance pathway) throws Exception {
        DiseasePathwayImageEditor diseaseHelper = new DiseasePathwayImageEditor();
        diseaseHelper.setPathway(pathway);
        diseaseHelper.setPersistenceAdaptor(pd.getDbAdaptor());
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway rPathway = reader.openDiagram(pd);
        diseaseHelper.setRenderable(rPathway);
        // Do a paint to validate some bounds
        PathwayDiagramGeneratorViaAT diagramHelper = new PathwayDiagramGeneratorViaAT();
        diagramHelper.paintOnImage(diseaseHelper);
        // Tight nodes and repaint
        diseaseHelper.tightNodes(true); // For overflowed nodes
        diagramHelper.paintOnImage(diseaseHelper);
        GKBWriter writer = getDiagramWriter(pd);
        Element rootElm = writer.createRootElement(rPathway);
        // Add a label to show this is a disease pathway diagram
        rootElm.setAttribute("isDisease", Boolean.TRUE + "");
        List<GKInstance> diseases = pathway.getAttributeValuesList(ReactomeJavaConstants.disease);
        boolean isForNormal = (diseases == null || diseases.size() == 0);
        rootElm.setAttribute("forNormalDraw", isForNormal + "");
        // Append some new information
        List<Renderable> normalComps = diseaseHelper.getNormalComponents();
        appendElement("normalComponents", 
                      rootElm, 
                      normalComps);
        List<Renderable> diseaseComps = diseaseHelper.getDiseaseComponents();
        appendElement("diseaseComponents",
                      rootElm,
                      diseaseComps);
        List<Node> crossedObjects = diseaseHelper.getCrossedObjects();
        appendElement("crossedComponents",
                      rootElm, 
                      crossedObjects);
        List<Renderable> overlaidComps = diseaseHelper.getOverlaidObjects();
        appendElement("overlaidComponents", rootElm, overlaidComps);
        List<Node> lofNodes = diseaseHelper.getLofNodes();
        appendElement("lofNodes", rootElm, lofNodes);
        // Need to output
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        outputter.output(rootElm, bos);
        return bos.toString();
    }
    
    private void appendElement(String elmName,
                               Element root,
                               List<? extends Renderable> comps) {
        String text = generateTextForListOfComponents(comps);
        if (text == null)
            return;
        Element elm = new Element(elmName);
        elm.setText(text);
        root.addContent(elm);
    }
    
    private String generateTextForListOfComponents(List<? extends Renderable> comps) {
        if (comps == null || comps.size() == 0)
            return null;
        StringBuilder builder = new StringBuilder();
        for (Iterator<? extends Renderable> it = comps.iterator(); it.hasNext();) {
            Renderable r = it.next();
            builder.append(r.getID());
            if (it.hasNext())
                builder.append(",");
        }
        return builder.toString();
    }
    
    public String generateXMLForPathwayDiagram(GKInstance pd,
                                               GKInstance pathway) throws Exception {
       DiagramGeneratorFromDB diagramHelper = new DiagramGeneratorFromDB();
       boolean isDisease = diagramHelper.isDiseaseRelatedPDInstance(pd);
       if (isDisease) {
           return generateXMLForDiseasePathwayDigram(pd, pathway);
       }
       else
           return generateXMLForPathwayDiagram(pd);
    }
    
    private GKInstance fetchPathwayDiagram(GKInstance pathway) throws Exception {
        Collection<GKInstance> c = pathway.getDbAdaptor().fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram,
                ReactomeJavaConstants.representedPathway,
                "=",
                pathway);
        if (c.size() == 0)
            throw new IllegalStateException("Cannot find a PathwayDiagram for" + pathway);
        if (c.size() > 1)
            throw new IllegalStateException("Find more than one PathwayDigram for" + pathway);
        return c.stream().findAny().get();
    }
    
    @Test
    public void testGenerateXMLForPathwayDigram() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("reactomerelease.oicr.on.ca",
                                            "test_slice_64",
                                            "",
                                            "");
        // EGFR Pathway diagram
        Long pathwayId = 177929L;
        // PIP3 signaling
        pathwayId = 2219528L;
        // Synthesis of DNA
        pathwayId = 69239L;
        
        pathwayId = 6802955L;
        
        pathwayId = 5619099L;
        
        pathwayId = 2453864L;
        
        pathwayId = 376176L;
        
        GKInstance pathway = dba.fetchInstance(pathwayId);
        GKInstance pdInst = fetchPathwayDiagram(pathway);
        String xmlText = generateXMLForPathwayDiagram(pdInst, pathway);
        System.out.println(xmlText);
    }
    
}
