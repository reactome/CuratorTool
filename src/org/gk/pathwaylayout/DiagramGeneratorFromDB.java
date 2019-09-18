/*
 * Created on Feb 13, 2009
 *
 */
package org.gk.pathwaylayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.gk.gkEditor.CoordinateSerializer;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.FileUtilities;
import org.gk.util.SwingImageCreator;
import org.junit.Test;

/**
 * This class is used to generate pathway diagrams from stored diagram XML String
 * generated from CT ELV. The generated image files should be used for Web ELV
 * directly.
 * @author wgm
 *
 */
public class DiagramGeneratorFromDB {
    private static final Logger logger = Logger.getLogger(DiagramGeneratorFromDB.class);
    // Information file
    private final String INFO_FILE_NAME = "info";
    private final String COORDINATE_JSON_FILE_NAME = "orderedcoordinates.json";
    private final int MAX_THUMNAIL_WIDTH = 100;
    private final String LATEST_IE_KEY = "latestIE";
    private final String TERM_KEY = "searchableTerms";
    // Connection to the database
    protected MySQLAdaptor dba;
    // Base directory for image
    private String imageBaseDir;
    // The user who created the images
    private String creator;
    // Flag to control if infor file should be created.
    // If this class is used during release, this flag should be false.
    private boolean needInfo = true;
    
    public DiagramGeneratorFromDB() {
    }
    
    public void setMySQLAdaptor(MySQLAdaptor dba) {
        this.dba = dba;
    }
    
    public MySQLAdaptor getMySQLAdaptor() {
        return this.dba;
    }
    
    public void setImageBaseDir(String baseDir) {
        this.imageBaseDir = baseDir;
    }
    
    public String getImageBaseDir() {
        return this.imageBaseDir;
    }
    
    public void setNeedInfo(boolean needInfo) {
        this.needInfo = needInfo;
    }
    
    public void setCreator(String creator) {
        this.creator = creator;
    }
    
    public String getCreator() {
        return this.creator;
    }
    
    /**
     * Generate image files. Client should call this method to generate image files
     * to be used by Web ELV.
     * @param pathwayId DbId of pathway for which to generate images
     * @return true if images have been generated successfully. Otherwise false and images should be
     * created already.
     * @throws Exception Thrown if unable generate images
     */
    @SuppressWarnings("rawtypes")
    public boolean generateImages(Long pathwayId) throws Exception {
        if (dba == null) {
            logger.error("DiagramGeneratorFromDB.generateImage(): No DBA specifieid.");
            throw new IllegalStateException("No DBA specified");
        }
        if (imageBaseDir == null) {
            logger.error("DiagramGeneratorFromDB.generateImage(): image base directory has not been set.");
            throw new IllegalStateException("No image base directory specified");
        }
        // Get the pathway diagram
        GKInstance pathway = dba.fetchInstance(pathwayId);
        if (pathway == null || !pathway.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
            logger.error("Pathway doesn't exist: " + pathwayId);
            throw new IllegalArgumentException("Pathway doesn't exist: " + pathwayId);
        }
        // Find PathwayDiagram
        Collection c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram, 
                                                    ReactomeJavaConstants.representedPathway, 
                                                    "=",
                                                    pathway);
        if (c == null || c.size() == 0) {
            logger.error("Pathway diagram is not available for " + pathway);
            throw new IllegalStateException("Pathway diagram is not available for " + pathway);
        }
        GKInstance diagram = (GKInstance) c.iterator().next();
        // Need to find the top level directory
        File pathwayDir = getPathwayDir(pathway);
        // Check if files have been created already
        if (!needNewImages(pathwayDir, diagram)) {
            logger.info("Image files have already been updated: " + pathway);
            return false; // No new images are needed
        }
        generateELVInstancesAndFiles(pathway, 
                                     diagram, 
                                     pathwayDir);
        return true;
    }
    
    /**
     * Generate all necessary files and instances that are used by web ELV for 
     * the passed pathway and its diagram instances.
     * @param pathway Pathway (GKInstance object) for which to generate files and instances
     * @param diagram Diagram (GKInstance object) for which to generate files and instances
     * @throws Exception Thrown if unable to generate any files or instances
     */
    public void generateELVInstancesAndFiles(GKInstance pathway,
                                             GKInstance diagram) throws Exception {
        File pathwayDir = getPathwayDir(pathway);
        generateELVInstancesAndFiles(pathway, 
                                     diagram, 
                                     pathwayDir);
    }

    /**
     * The actual method to generate all necessary files and instances for 
     * the web ELV.
     * @param pathway Pathway (GKInstance object) for which to generate files and instances
     * @param diagram Diagram (GKInstance object) for which to generate files and instances
     * @param pathwayDir Directory in which to write files
     * @throws Exception Thrown if unable to generate any files or instances
     */
    protected void generateELVInstancesAndFiles(GKInstance pathway,
                                                GKInstance diagram,
                                                File pathwayDir) throws Exception {
        logger.info("generateELVInstancesAndFiles() on diagram: " + diagram);
        RenderablePathway rPathway = createImageFiles(diagram, 
                                                      pathway,
                                                      pathwayDir);
        // Handle Vertex instances
        handleVertex(rPathway, 
                     diagram);
        createCoordinateJSON(diagram, 
                             pathwayDir);
        Set<GKInstance> terms = handleSearchableTerms(diagram,
                                                      pathway,
                                                      pathwayDir);
        createInfoFile(diagram, 
                       terms,
                       pathwayDir);
    }
    
    /**
     * Find the PathwayDiagram related to this Pathway instance. A PathwayDiagram can be referred
     * by PathwayDigram's representedPathway slot. But for a disease pathway, a PathwayDiagram can
     * be linked via its normalPathway, which links to a PathwayDiagram. This change has been made
     * as of November, 2014.
     * @param pathway Pathway for which to retrieve the pathway diagram
     * @return PathwayDiagram instane (GKInstance object)
     * @throws Exception Thrown if unable to retrieve pathway diagram instance using the database adaptor
     * or if unable to retrieve a normal pathway for a disease pathway instance due to an error
     */
    @SuppressWarnings("unchecked")
    public GKInstance getPathwayDiagram(GKInstance pathway) throws Exception {
        // Find PathwayDiagram
        Collection c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram, 
                                                    ReactomeJavaConstants.representedPathway, 
                                                    "=",
                                                    pathway);
        if (c != null && c.size() > 0) {
            GKInstance diagram = (GKInstance) c.iterator().next();
            return diagram;
        }
        // For a disease pathway, its diagram may be linked via its normal pathway slot.
        if (pathway.getSchemClass().isValidAttribute(ReactomeJavaConstants.normalPathway)) {
            GKInstance normalPathway = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.normalPathway);
            if (normalPathway == null)
                return null;
            c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram, 
                                             ReactomeJavaConstants.representedPathway, 
                                             "=",
                                             normalPathway);
            if (c != null && c.size() > 0)
                return (GKInstance) c.iterator().next();
        }
        return null;
    }
    
    @Test
    public void testGenerateImageOnly() throws Exception {
        // Signaling by NGF
//        Long pathwayId = 166520L;
        Long pathwayId = 69620L; // Cell Cycle Checkpoints
        pathwayId = 397014L;
        pathwayId = 4839744L;
        pathwayId = 4839735L;
        // A disease pathway
//        Long pathwayId = 4839735L;
        pathwayId = 2894858L;
        pathwayId = 1226099L;
        pathwayId = 5467345L; // An overlaid gene
        pathwayId = 5619054L; // Use normalPathway
        pathwayId = 4341670L;
        pathwayId = 4687000L;
        pathwayId = 5603029L;
        pathwayId = 6802949L;
//        pathwayId = 5657562L; 
//        dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
//                               "gk_central", 
//                               "authortool", 
//                               "T001test");
        dba = new MySQLAdaptor("localhost",
                               "test_gk_central_efs_new",
                               "root",
                               "macmysql01");
        dba.setUseCache(true);
        imageBaseDir = "tmp";
        // Get the pathway diagram
        GKInstance pathway = dba.fetchInstance(pathwayId);
        if (pathway == null || !pathway.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
            logger.error("Pathway doesn't exist: " + pathwayId);
            throw new IllegalArgumentException("Pathway doesn't exist: " + pathwayId);
        }
        // Find PathwayDiagram
        GKInstance diagram = getPathwayDiagram(pathway);
        if (diagram == null) {
            logger.error("Pathway diagram is not available for " + pathway.getDisplayName());
            throw new IllegalStateException("Pathway diagram is not available for " + pathway.getDisplayName());
        }
        // Need to find the top level directory
        File pathwayDir = new File(imageBaseDir);
        
        DiagramGKBReader reader = new DiagramGKBReader();
        reader.setPersistenceAdaptor(diagram.getDbAdaptor());
        RenderablePathway rPathway = reader.openDiagram(diagram);
        
        // Export a PNG image
        exportImageFile(rPathway, diagram, pathway, pathwayDir, "png");
        
        PathwayDiagramXMLGenerator xmlGenerator = new PathwayDiagramXMLGenerator();
        String xml = xmlGenerator.generateXMLForPathwayDiagram(diagram, 
                                                               pathway);
        FileUtilities fu = new FileUtilities();
        fu.setOutput(imageBaseDir + "/" + pathway.getDBID() + ".xml");
        fu.printLine(xml);
        fu.close();
    }
    
    @Test
    public void testGenerateImageForLocalProject() throws Exception {
        //String srcFileName = "/Users/gwu/Documents/gkteam/Steve/IL7Signaling.rtpj";
//        Long pdId = 1271495L;
//        String srcFileName = "/Users/gwu/Documents/gkteam/Marija/Test Activation of EGFR in Cancer_Fixed_1.rtpj";
//        Long pdId = -48L;
//        String srcFileName = "/Users/gwu/Documents/gkteam/Marija/Signaling by EGFR in Cancer in GK_CENTRAL.rtpj";
//////        String srcFileName = "/Users/gwu/Documents/gkteam/Marija/EGFR_102111.rtpj";
//        Long pdId = 507988L;
        // Karen's Signaling by FGFR in disease
//        String srcFileName = "/Users/gwu/Documents/gkteam/Karen/Signaling by FGFR in disease.rtpj";
//        Long pdId = 500335L;
        // Bijay's MPS project
//        String srcFileName = "/Users/gwu/Documents/gkteam/bijay/MPS.rtpj";
//        Long pdId = 1836730L;
        // Mairja's PIP3 pathway
//        String srcFileName = "/Users/gwu/Documents/gkteam/Marija/Signaling by PI3K in Cancer.rtpj";
//        Long pdId = 1273430L;
//        String srcFileName = "/Users/gwu/Documents/gkteam/bijay/VisualTransductionDisease.rtpj";
//        Long pdId = 2468358L;
        //String srcFileName = "/Users/gwu/Documents/gkteam/Marija/Signaling by Notch in Cancer.rtpj";
        //Long pdId = 2032362L;
//        String srcFileName = "/Users/gwu/Documents/gkteam/Karen/FGFRInDisease.rtpj";
        //        Long pdId = 500335L;
        String srcFileName = "/Users/gwu/Documents/gkteam/Karen/TruncatedAPCMutants.rtpj";
        Long pdId = 451075L;
        
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        PersistenceManager.getManager().setActiveFileAdaptor(fileAdaptor);
        fileAdaptor.setSource(srcFileName);
        GKInstance diagram = fileAdaptor.fetchInstance(pdId);
        boolean isDiseaseRelated = isDiseaseRelatedPDInstance(diagram);
        List<?> pathways = diagram.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
        File parentDir = new File("tmp");
        for (Object obj : pathways) {
            GKInstance pathway = (GKInstance) obj;
            Long pathwayId = pathway.getDBID();
//            if (!pathwayId.equals(2206292L))
//                continue;
            if (pathwayId < 0)
                pathwayId = -pathwayId;
            File pathwayDir = new File(parentDir, pathwayId + "");
            pathwayDir.mkdir();
            System.out.println("Pathway dir: " + pathwayDir.getAbsolutePath());
            createImageFiles(diagram, 
                             pathway,
                             pathwayDir);
        }
    }
    
    /**
     * This method is used to check if a PathwayDiagram instance is related to a disease pathway. 
     * A disease related PathwayDiagram instance should have one and only one normal pathway, and
     * at least one disease pathway used as its representedPathway attribute.
     * @param pdInst Pathway Diagram instance
     * @return true if the pathway diagram instance is used by normal and disease pathways; false otherwise
     * @throws Exception Thrown if unable to retrieve pathway instances from the pathway diagram instance or unable
     * to retrieve disease information from a pathway instance
     */
    public boolean isDiseaseRelatedPDInstance(GKInstance pdInst) throws Exception {
        List<?> pathways = pdInst.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
        // It should have multiple pathways, and one of them should be normal pathway, and others
        // should be disease related
        GKInstance normalPathway = null;
        if (pathways != null && pathways.size() > 1) {
            int diseaseCount = 0;
            int normalCount = 0;
            for (Object obj : pathways) {
                GKInstance pathway = (GKInstance) obj;
                GKInstance disease = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.disease);
                if (disease != null)
                    diseaseCount ++;
                else {
                    normalCount ++;
                    normalPathway = pathway;
                }
            }
            if (normalCount > 0 && diseaseCount > 0) // A shared diagram between normal and disease pathway
                return true;
        }
        // Check with a new normalPathway slot
        return isDiseaseRelatedPDInstanceViaNormalPathway(pdInst, normalPathway);
    }
    
    /**
     * This method is used to check if a PathwayDiagram instance is linked via the normalPathway slot
     * which is introduced in November, 2014.
     * @param pdInst
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private boolean isDiseaseRelatedPDInstanceViaNormalPathway(GKInstance pdInst,
                                                               GKInstance normalPathway) throws Exception {
        if (normalPathway == null || !normalPathway.getSchemClass().isValidAttribute(ReactomeJavaConstants.normalPathway))
            return false;
        Collection<GKInstance> referrers = normalPathway.getReferers(ReactomeJavaConstants.normalPathway);
        if (referrers != null && referrers.size() > 0) {
            for (GKInstance referrer : referrers) {
                if (referrer.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    GKInstance disease = (GKInstance) referrer.getAttributeValue(ReactomeJavaConstants.disease);
                    GKInstance tmp = (GKInstance) referrer.getAttributeValue(ReactomeJavaConstants.normalPathway);
                    if (disease != null && tmp != null && normalPathway.getDBID().equals(tmp.getDBID())) // Compare DB_IDs in case cache is not used.
                        return true;
                }
            }
        }
        return false;
    }
    
    /**
     * TODO: this method can handle searchable term for one single pathway diagram instance only.
     * However, for the release, searchable terms should be generated based on all deployed pathway
     * diagrams. A new method should be used for that purpose.
     * @param diagram
     * @param pathway
     * @param pathwayDir
     * @return
     * @throws Exception
     */
    private Set<GKInstance> handleSearchableTerms(GKInstance diagram,
                                                  GKInstance pathway,
                                                  File pathwayDir) throws Exception {
        // Delete all old Searchable terms created for this pathway diagram.
        File infoFile = new File(pathwayDir, INFO_FILE_NAME);
        if (infoFile.exists()) {
            Properties prop = new Properties();
            FileInputStream fis = new FileInputStream(infoFile);
            prop.load(fis);
            String value = prop.getProperty(TERM_KEY);
            if (value != null && value.length() > 0) {
                String[] ids = value.split(",");
                try {
                    dba.startTransaction();
                    // Delete all old terms
                    for (String id : ids) {
                        GKInstance inst = dba.fetchInstance(new Long(id));
                        // If there is a null, let it roll back. Probably
                        // there is something inconsistence in the database and info file.
                        // Don't delete anything in case this DB_ID is used for something else!
                        dba.deleteInstance(inst);
                    }
                    dba.commit();
                }
                catch(Exception e) {
                    dba.rollback();
                    logger.error("Delete old VertexSearchTerm: " + e, e);
                }
            }
            fis.close();
        }
        // Create new searchable terms
        VertexSearchableTermGenerator termGenerator = new VertexSearchableTermGenerator();
        Set<GKInstance> terms = termGenerator.generateVertexSearchableTerms(diagram, 
                                                                            pathway,
                                                                            dba);
        boolean isTASupported = dba.supportsTransactions();
        try {
            if (isTASupported)
                dba.startTransaction();
            for (GKInstance inst : terms)
                dba.storeInstance(inst);
            if (isTASupported)
                dba.commit();
            return terms;
        }
        catch(Exception e) {
            if (isTASupported)
                dba.rollback();
            logger.error("Store new VertexSearchTerms: " + e, e);
        }
        return null;
    }
    
    /**
     * If there are two layers, background components hidden by foreground components
     * should be escaped.
     * @param rPathway
     * @return
     */
    private List<Renderable> getEscapedBgComponents(RenderablePathway rPathway) {
        List<Renderable> escaped = new ArrayList<Renderable>();
        List<Renderable> fgComps = rPathway.getFgComponents();
        List<Renderable> bgComps = rPathway.getBgCompoennts();
        if (fgComps == null || bgComps == null)
            return escaped;
        for (Renderable bgComp : bgComps) {
            for (Renderable fgComp : fgComps) {
                if (bgComp instanceof HyperEdge && fgComp instanceof HyperEdge) {
                    Point p1 = bgComp.getPosition();
                    Point p2 = fgComp.getPosition();
                    if (p1.equals(p2)) {
                        escaped.add(bgComp);
                        break;
                    }
                }
                else if (bgComp instanceof Node && fgComp instanceof Node) {
                    Rectangle bgRect = bgComp.getBounds();
                    Rectangle fgRect = fgComp.getBounds();
                    // If the background component is coverted fully by the foreground component
                    // Escape it
                    if (fgRect.contains(bgRect)) {
                        escaped.add(bgComp);
                        break;
                    }
                }
            }
        }
        return escaped;
    }
    
    public void handleVertex(Long pathwayId) throws Exception {
        // Get the pathway diagram
        GKInstance pathway = dba.fetchInstance(pathwayId);
        if (pathway == null || !pathway.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
            logger.error("Pathway doesn't exist: " + pathwayId);
            throw new IllegalArgumentException("Pathway doesn't exist: " + pathwayId);
        }
        // Find PathwayDiagram
        Collection c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram, 
                                                    ReactomeJavaConstants.representedPathway, 
                                                    "=",
                                                    pathway);
        if (c == null || c.size() == 0) {
            logger.error("Pathway diagram is not available for " + pathway);
            throw new IllegalStateException("Pathway diagram is not available for " + pathway);
        }
        GKInstance diagram = (GKInstance) c.iterator().next();
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway rPathway = reader.openDiagram(diagram);
        if (rPathway.getComponents() == null || rPathway.getComponents().size() == 0) {
            logger.info(diagram + " is empty!");
            return ; // Just an empty pathway. Don't do anything!
        }
        // Have to make sure images are correct
        //TODO: The following code should be refactored together with generateELVInstanceAndFiles.
        PathwayEditor editor = preparePathwayEditor(diagram, 
                                                    pathway,
                                                    rPathway);
        PathwayDiagramGeneratorViaAT generator = new PathwayDiagramGeneratorViaAT();
//        // Do an extra paint to validate all bounds
        generator.paintOnImage(editor);
        // Make sure all text should be wrapped into nodes bounds
        editor.tightNodes(true);
        // For some reason, have to repaint once to make sure all nodes are correct
        generator.paintOnImage(editor);
        handleVertex(rPathway, diagram);
    }
    
    
    private void handleVertex(RenderablePathway rPathway,
                              GKInstance diagram) throws Exception {
        try {
            List<Long> existedIds = new ArrayList<Long>();
            // Check if any vertex have been created for this diagram. If true, delete them.
            Collection<?> existed = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Vertex, 
                                                              ReactomeJavaConstants.pathwayDiagram,
                                                              "=",
                                                              diagram);
            boolean isTransactionSupported = dba.supportsTransactions();
            if (isTransactionSupported)
                dba.startTransaction();
            if (existed != null && existed.size() > 0) {
                for (Iterator<?> it = existed.iterator(); it.hasNext();) {
                    GKInstance inst = (GKInstance) it.next();
                    existedIds.add(inst.getDBID());
                    dba.deleteInstance(inst);
                }
            }
            // Check for vertex
            existed = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Edge, 
                                                   ReactomeJavaConstants.pathwayDiagram,
                                                   "=",
                                                   diagram);
            if (existed != null && existed.size() > 0) {
                for (Iterator<?> it = existed.iterator(); it.hasNext();) {
                    GKInstance inst = (GKInstance) it.next();
                    existedIds.add(inst.getDBID());
                    dba.deleteInstance(inst);
                }
            }
            // Create new Vertex instances
            List<GKInstance> newVertices = new ArrayList<GKInstance>();
            // Go through each displayed objects and save them
            List<Renderable> components = rPathway.getComponents();
            if (components != null) {
                CoordinateSerializer serializer = new CoordinateSerializer();
                Map<Renderable, GKInstance> rToIMap = new HashMap<Renderable, GKInstance>();
                // Have to hold edges till all nodes have been converted
                List<HyperEdge> edges = new ArrayList<HyperEdge>();
                // Background escape if applicable
                List<Renderable> bgEscaped = getEscapedBgComponents(rPathway);
                for (Renderable r : components) {
                    if (serializer.avoidVertexGeneration(r) || bgEscaped.contains(r))
                        continue;
                    if (r instanceof Node) {
                        GKInstance inst = serializer.convertNodeToInstance((Node)r, 
                                                                            diagram,
                                                                            dba);
                        newVertices.add(inst);
                        rToIMap.put(r, inst);
                    }
                    else if (r instanceof HyperEdge)
                        edges.add((HyperEdge)r);
                }
                for (HyperEdge edge : edges) {
                    List<GKInstance> edgeInstances = serializer.convertEdgeToInstances(edge, 
                                                                                       diagram,
                                                                                       dba,
                                                                                       rToIMap);
                    if (edgeInstances != null)
                        newVertices.addAll(edgeInstances);
                }
            }
            // reuse the DB_IDs
            if (existedIds.size() > 0)  {
                for (int i = 0; i < existedIds.size(); i++) {
                    Long dbId = existedIds.get(i);
                    if (i < newVertices.size()) {
                        GKInstance newInst = newVertices.get(i);
                        newInst.setDBID(dbId);
                    }
                }
            }
            //int hasDbIds = 0;
            for (GKInstance vertex : newVertices) {
                if (vertex.getDBID() != null) {
                    dba.storeInstance(vertex, true);
              //      hasDbIds ++;
                }
                else
                    dba.storeInstance(vertex, false);
            }
            //System.out.println("Has dbId : " + hasDbIds);
            //System.out.println("Has no dbId: " + (newVertices.size() - hasDbIds));
            if (isTransactionSupported)
                dba.commit();
            // refresh to avoid any duplication error
            dba.refresh();
        }
        catch(Exception e) {
            if (dba.supportsTransactions())
                dba.rollback();
            throw e;
        }
    }
    
    private void createInfoFile(GKInstance diagram,
                                Set<GKInstance> terms,
                                File pathwayDir) throws Exception {
        if (!needInfo)
            return;
        GKInstance latestIE = InstanceUtilities.getLatestIEFromInstance(diagram);
        Properties prop = new Properties();
        prop.setProperty(LATEST_IE_KEY,
                         latestIE.getDBID().toString());
        if (terms != null && terms.size() > 0) {
            // logger all newly create terms
            StringBuilder builder = new StringBuilder();
            for (Iterator<GKInstance> it = terms.iterator(); it.hasNext();) {
                GKInstance term = it.next();
                builder.append(term.getDBID());
                if (it.hasNext())
                    builder.append(",");
            }
            prop.setProperty(TERM_KEY,
                             builder.toString());
        }
        if (creator != null)
            prop.setProperty("creator", creator);
        File file = new File(pathwayDir,
                             INFO_FILE_NAME);
        FileOutputStream fos = new FileOutputStream(file);
        prop.store(fos, "information for creating images");
        fos.close();
    }
    
    /**
     * Disease and normal pathway sharing the same pathway diagram. If isNormalInDisease is true,
     * only objects related to normal pathways should be drawn.
     * @param diagram
     * @param pathwayDir
     * @param isForDisease
     * @param isNormalInDisease
     * @return
     * @throws Exception
     */
    protected RenderablePathway createImageFiles(GKInstance diagram,
                                                 GKInstance pathway,
                                                 File pathwayDir) throws Exception {
        DiagramGKBReader reader = new DiagramGKBReader();
        reader.setPersistenceAdaptor(diagram.getDbAdaptor());
        RenderablePathway rPathway = reader.openDiagram(diagram);
        if (rPathway.getComponents() == null || rPathway.getComponents().size() == 0) {
            logger.info(diagram + " is empty!");
            return rPathway; // Just an empty pathway. Don't do anything!
        }
        PathwayEditor editor = preparePathwayEditor(diagram, 
                                                    pathway,
                                                    rPathway);
        PathwayDiagramGeneratorViaAT generator = new PathwayDiagramGeneratorViaAT();
//        // Do an extra paint to validate all bounds
        generator.paintOnImage(editor);
        // Make sure all text should be wrapped into nodes bounds
        editor.tightNodes(true);
        // For some reason, have to repaint once to make sure all nodes are correct
        generator.paintOnImage(editor);
        generator.saveAsTiles(editor, 
                              pathwayDir);
        // Call the following after the above statement to avoid annoying scaling problem
        // because re-validating node bounds
        // A scaling in Graphics context will effect the font metrix calculation.
        // Have to make sure the correction order of image generation.
        generator.saveAsThumbnail(MAX_THUMNAIL_WIDTH,
                                  editor,
                                  pathwayDir);
        return rPathway;
    }
    
    /**
     * Export a PathwayDiagram in an image file format. PDF is not supproted here.
     * @param diagram
     * @param pathway
     * @param pathwayDir
     * @param format
     * @throws Exception
     */
    private void exportImageFile(RenderablePathway rPathway,
                                 GKInstance diagram,
                                 GKInstance pathway,
                                 File pathwayDir,
                                 String format) throws Exception {
        PathwayEditor editor = preparePathwayEditor(diagram, 
                                                    pathway,
                                                    rPathway);
        PathwayDiagramGeneratorViaAT generator = new PathwayDiagramGeneratorViaAT();
        generator.paintOnImage(editor);
        editor.tightNodes(true);
        generator.paintOnImage(editor);
        String fileName = pathway.getDisplayName();
        fileName = fileName.replaceAll("(\\\\|/)", "-");
        // Make sure the file name is not too long
        if (fileName.length() > 255 - 4) // 4 is for .png or .pdf
            fileName = fileName.substring(0, 255 - 4);
        // Note: It seems there is a bug in the PDF exporter to set correct FontRenderContext.
        // Have to call PNG export first to make some rectangles correct.
        File pngFileName = new File(pathwayDir, fileName + "." + format);
        BufferedImage image = SwingImageCreator.createImage(editor);
        ImageIO.write(image, format, pngFileName);
    }
    
    /**
     * Prepare an appropriate PathwayEditor object for drawing the diagram.
     * @param diagram Pathway diagram instance to check if the pathway represented is disease related
     * @param pathway Pathway instance to retrieve disease information
     * @param rPathway RenderablePathway instance which is used to fine tune the diagram
     * @return PathwayEditor instance
     * @throws Exception Thrown if unable to retrieve pathway instances from the pathway diagram instance or unable
     * to retrieve disease information from a pathway instance
     * @throws InvalidAttributeException Thrown if the disease attribute is invalid for the pathway instance passed to
     * the method
     */
    public PathwayEditor preparePathwayEditor(GKInstance diagram,
                                              GKInstance pathway,
                                              RenderablePathway rPathway) throws Exception, InvalidAttributeException {
        // Make tight bounds if needed
        Node.setWidthRatioOfBoundsToText(1.0d);
        Node.setHeightRatioOfBoundsToText(1.0d);

        boolean isDiseaseRelatedPd = isDiseaseRelatedPDInstance(diagram);
        // Check if pathway is a disease pathway
        GKInstance disease = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.disease);
        boolean isNormalPathway = (disease == null);
        
        fineTuneDiagram(rPathway);
        PathwayEditor editor = null;
        if (isDiseaseRelatedPd) {
            DiseasePathwayImageEditor editor1 = new DiseasePathwayImageEditorViaEFS();
            editor1.setIsForNormal(isNormalPathway);
            editor1.setPersistenceAdaptor(diagram.getDbAdaptor());
            editor1.setPathway(pathway);
            editor = editor1;
        }
        else
            editor = new PathwayEditor();
        editor.setHidePrivateNote(true);
        editor.setRenderable(rPathway);
        return editor;
    }
    
    /**
     * Fine tune the pathway diagram before generating images.
     * This is used as a template for sub-class. Nothing has been done in this class.
     * @param rPathway
     */
    protected void fineTuneDiagram(RenderablePathway rPathway) {
    }
    
    private boolean needNewImages(File pathwayDir,
                                  GKInstance diagram) throws Exception {
        File infoFile = new File(pathwayDir, INFO_FILE_NAME);
        if (!infoFile.exists())
            return true;
        // Get the latest IE
        GKInstance latestIE = InstanceUtilities.getLatestIEFromInstance(diagram);
        Properties properties = new Properties();
        FileInputStream fis = new FileInputStream(infoFile);
        properties.load(fis);
        fis.close();
        String storedIE = properties.getProperty(LATEST_IE_KEY);
        if (latestIE.getDBID().toString().equals(storedIE))
            return false;
        return true;
    }

    private File getPathwayDir(GKInstance pathway) throws Exception {
        // Check the database directory
        String dbName = dba.getDBName();
        File dbDir = new File(imageBaseDir, dbName);
        if (!dbDir.exists())
            dbDir.mkdir();
        // Find the species for pathway
        GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
        Long speciesId = species.getDBID();
        // Check the species directory
        File speciesDir = new File(dbDir, speciesId.toString());
        if (!speciesDir.exists())
            speciesDir.mkdir();
        // Check the pathway directory
        File pathwayDir = new File(speciesDir, 
                                   pathway.getDBID().toString());
        if (!pathwayDir.exists())
            pathwayDir.mkdir();
        return pathwayDir;
    }
    
    private void createCoordinateJSON(GKInstance diagram,
                                      File pathwayDir) throws Exception {
        JSONCoordinateGenerator generator = new JSONCoordinateGenerator();
        String json = generator.generateCoordinateJSON(diagram, dba);
        // Write out the file
        File file = new File(pathwayDir, 
                             COORDINATE_JSON_FILE_NAME);
        FileWriter writer = new FileWriter(file);
        PrintWriter printWriter = new PrintWriter(writer);
        printWriter.println(json);
        printWriter.close();
        writer.close();
    }
        
    @Test
    public void testGenerateImages() throws Exception {
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                                            "gk_current_ver44",
//                                            "root",
//                                            "macmysql01");
        MySQLAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
                                            "gk_central",
                                            "authortool",
                                            "T001test");
        setMySQLAdaptor(dba);
        setImageBaseDir("tiles");
        //generateImages(69620L); // Cell Cycle Checkpoints
        //generateImages(71387L); // Metabolism of carbohydrates
//        generateImages(168256L); // Signaling in Immune System
        // A disease pathway
//        Long pathwayId = 4839735L;
        Long pathwayId = 4839744L;
        generateImages(pathwayId);
    }
    
    /**
     * Main method that can be invoked from a command line. The main method expects the following arguments in order:
     * dbHost dbName dbUser dbPwd dbPort imageBaseDir topicFile
     * imagebaseDir: the top directory name for all static files used by Web ELV
     * topicFile: a list of DB_IDs for top-level pathways having diagrams drawn. The format is similar to file
     * ver**_topcis.txt as following:
     * DB_ID    Pathway_Name (tab delimited)
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println("Usage: java -Xmx512m DiagramGeneratorFromDB dbHost dbName dbUser dbPwd dbPort imageBaseDir (topicFile)\n" +
            		            "The topicFile name is optional. If no topicFile name is provided, images for all PathwayDiagram instances\n" +
            		            "will be generated.");
            System.exit(1);
        }
        try {
            MySQLAdaptor dba = new MySQLAdaptor(args[0],
                                                args[1],
                                                args[2],
                                                args[3],
                                                Integer.parseInt(args[4]));
            DiagramGeneratorFromDB generator = new DiagramGeneratorFromDB();
            generator.setMySQLAdaptor(dba);
            generator.setImageBaseDir(args[5]);
            generator.setNeedInfo(false); // Don't need information stored.
            List<Long> dbIds = null;
            if (args.length > 6) {
                // Need to parse the file to get pathway ids
                FileUtilities fu = new FileUtilities();
                fu.setInput(args[6]);
                String line = null;
                dbIds = new ArrayList<Long>();
                while ((line = fu.readLine()) != null) {
                    String[] tokens = line.split("\t");
                    dbIds.add(new Long(tokens[0]));
                }
                fu.close();
            }
            else {
                dbIds = generator.getPathwayIDsForDiagrams(dba);
            }
//            if (true) {
//                System.out.println("Total DBIDs for releasing: " + dbIds.size());
//                return;
//            }
            for (Long dbId : dbIds) {
                generator.generateImages(dbId);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * This helper method is used to grep a list of DB_IDs for pathways having diagrams available.
     * @param dbda
     * @return
     * @throws Exception
     */
    protected List<Long> getPathwayIDsForDiagrams(MySQLAdaptor dba) throws Exception {
        // Get all pathway diagrams
        Collection diagrams = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        SchemaClass diagramCls = dba.getSchema().getClassByName(ReactomeJavaConstants.PathwayDiagram);
        SchemaAttribute att = diagramCls.getAttribute(ReactomeJavaConstants.representedPathway);
        dba.loadInstanceAttributeValues(diagrams, att);
        Set<Long> set = new HashSet<Long>(); // Use the set just in case of duplications.
        for (Iterator it = diagrams.iterator(); it.hasNext();) {
            GKInstance instance = (GKInstance) it.next();
            List<?> values = instance.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
            if (values != null && values.size() > 0) {
                for (Object obj : values) {
                    GKInstance pathway = (GKInstance) obj;
                    set.add(pathway.getDBID());
                }
            }
        }
        return new ArrayList<Long>(set);
    }

}
