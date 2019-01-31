/*
 * Created on Apr 25, 2014
 *
 */
package org.gk.qualityCheck;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gk.database.AttributeEditConfig;
import org.gk.gkCurator.authorTool.ModifiedResidueHandler;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Node;
import org.gk.render.NodeAttachment;
import org.gk.render.Renderable;
import org.gk.render.RenderableFeature;
import org.gk.render.RenderableGene;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableProtein;
import org.gk.render.RenderableRNA;
import org.gk.schema.InvalidAttributeException;
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * This check is to make sure hasModified values in EntityWithAccessionedSequence instances
 * are correctly displayed in pathway diagrams. The output should be similar to one from
 * T114 and T115 in the diagram-converter.
 * 
 * @author Fred Loney <loneyf@ohsu.edu> & Guanming Wu <wug@ohsu.edu>
 */
@SuppressWarnings("unchecked")
public class DiagramNodeAttachmentCheck extends PathwayDiagramCheck {
    // The reaction issue reporting details.
    private Map<Long, String> ewasDbIdToDetails = new HashMap<>();
    private Map<Long, GKInstance> ewasDbIdToInst = new HashMap<>(); // Just a quick cache

    private static final String[] HEADERS = {
            "Diagram_DBID",
            "Pathway_DisplayName", 
            "Pathway_DBID",
            "Entity_DBID", 
            "Entity_DisplayName", 
            "Issues",
            "Created", 
            "Modified"
    };
    
    private static final String[] LOAD_ATTS = {
            ReactomeJavaConstants.hasModifiedResidue
    };
    
    @Override
    public String getDisplayName() {
        return "Diagram_Entity_Modification_Mismatch";
    }

    @Override
    protected String[] getColumnHeaders() {
        return HEADERS;
    }

    @Override
    protected Collection<Long> doCheck(GKInstance pathwayDiagramInst) throws Exception {
        // Clear the detail map.
        ewasDbIdToDetails.clear();
        ewasDbIdToInst.clear();
        // The diagram pathway.
        RenderablePathway diagram = getRenderablePathway(pathwayDiagramInst);
        // The diagram EWAS nodes.
        Collection<Node> ewasNodes = getPhysicalEntityNodes(diagram).stream()
                                                                    .filter(node -> isEWASNode(node))
                                                                    .collect(Collectors.toList());
        // Check the EWAS nodes.
        checkAttachments(ewasNodes);
        
        return ewasDbIdToDetails.keySet();
    }

    private boolean isEWASNode(Node node) {
        return node instanceof RenderableGene ||
               node instanceof RenderableProtein ||
               node instanceof RenderableRNA;
    }

    @Override
    protected String[][] getReportLines(GKInstance pathwayDiagramInst) throws Exception {
        GKInstance pathway = (GKInstance) pathwayDiagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
        Collection<Long> ewasDbIds = getIssueDbIds(pathwayDiagramInst);
        List<String[]> lines = new ArrayList<String[]>();
        GKInstance created = (GKInstance) pathwayDiagramInst.getAttributeValue(ReactomeJavaConstants.created);
        GKInstance modified = InstanceUtilities.getLatestCuratorIEFromInstance(pathwayDiagramInst);
        for (Long ewasDBID : ewasDbIds) {
            GKInstance instance = ewasDbIdToInst.get(ewasDBID);
            String[] line = {
                    pathwayDiagramInst.getDBID().toString(),
                    pathway.getDisplayName(),
                    pathway.getDBID().toString(),
                    instance.getDBID().toString(),
                    instance.getDisplayName(),
                    ewasDbIdToDetails.get(ewasDBID),
                    created.getDisplayName(),
                    modified.getDisplayName()
            };
            lines.add(line);
        }
        return lines.toArray(new String[lines.size()][]);
    }

    @Override
    protected String getResultTableIssueDBIDColName() {
        return "Node Attachment Mismatch";
    }

    /**
     * Reports missing nodes, extra nodes and mismatched labels.
     * 
     * @param nodes the diagram EWAS nodes
     * @throws Exception
     */
    private void checkAttachments(Collection<Node> nodes) throws Exception {
        Map<Long, GKInstance> dbIdToInstMap = loadInstances(nodes);
        // Load the config containing the attachment short names.
        loadAttributeEditConfig();
        // Check each EWAS node.
        for (Node node: nodes) {
            Long ewasDbId = node.getReactomeId();
            GKInstance instance = dbIdToInstMap.get(ewasDbId);
            if (instance == null) // This type of error should be caught in other QA check
                continue;
            checkAttachments(node, instance);
        }
    }
    
    private void checkAttachments(Node node, GKInstance instance) throws Exception {
        Set<String> issues = checkNodeFeatures(node, instance);
        if (issues == null || issues.size() == 0)
            return ;
        String issueText = issues.stream().sorted().collect(Collectors.joining(", "));
        // Merge these two types into one
        issueText = issueText.replace("extra feature, missing feature",
                                      "mismatched feature");
        ewasDbIdToDetails.put(instance.getDBID(), issueText);
        ewasDbIdToInst.put(instance.getDBID(), instance);
    }

    public Set<String> checkNodeFeatures(Node node, GKInstance instance) throws InvalidAttributeException, Exception {
        List<NodeAttachment> attachments = node.getNodeAttachments();
        if (attachments == null)
            attachments = new ArrayList<>(); // For easy comparison for avoid null check
        // Currently displayed features
        // Note: All RenderableFetaure should have their own DB_IDs.
        Map<Long, Set<RenderableFeature>> diagramIdToFetaures = new HashMap<>();
        List<RenderableFeature> noIdFeatures = new ArrayList<>();
        for (NodeAttachment attachment : attachments) {
            if (!(attachment instanceof RenderableFeature))
                continue;
            Long id = attachment.getReactomeId();
            if (id == null) {
                noIdFeatures.add((RenderableFeature)attachment);
                continue;
            }
            diagramIdToFetaures.compute(id, (key, set) -> {
                if (set == null)
                    set = new HashSet<>();
                set.add((RenderableFeature)attachment);
                return set;
            });
        }
        
        ModifiedResidueHandler handler = new ModifiedResidueHandler();
        List<GKInstance> residues = instance.getAttributeValuesList(ReactomeJavaConstants.hasModifiedResidue);
        // Features that are supposed to be displayed
        Map<Long, Set<RenderableFeature>> modelIdToFeatures = new HashMap<>();
        for (GKInstance residue : residues) {
            RenderableFeature feature = handler.convertModifiedResidue(residue);
            modelIdToFeatures.compute(residue.getDBID(), (key, set) -> {
                if (set == null)
                    set = new HashSet<>();
                set.add(feature);
                return set;
            });
        }
        Set<String> issues = compareFeatures(diagramIdToFetaures, modelIdToFeatures);
        if (noIdFeatures.size() > 0)
            issues.add("extra feature"); // Counted as extra feature
        return issues;
    }
    
    /**
     * The same ModifiedResidue may be annotated multiple times for an EWAS instance (e.g.
     * a phosphorylation with unknown location).
     * @param diagramIdToFetaure
     * @param modelIdToFeature
     * @return
     */
    private Set<String> compareFeatures(Map<Long, Set<RenderableFeature>> diagramIdToFetaure,
                                        Map<Long, Set<RenderableFeature>> modelIdToFeature) {
        Set<String> issues = new HashSet<>();
        // From the data model view
        modelIdToFeature.forEach((id, modelFeatures) -> {
            // Check if we have this feature drawn
            Set<RenderableFeature> diagramFeatures = diagramIdToFetaure.get(id);
            if (diagramFeatures == null) {
                issues.add("missing feature");
            }
            else {
                compareFeatures(modelFeatures, diagramFeatures, issues);
            }
        });
        // From the diagram view
        diagramIdToFetaure.forEach((id, diagramFeatures) -> {
            // Check if this feature is annotated
            Set<RenderableFeature> modelFeatures = modelIdToFeature.get(id);
            if (modelFeatures == null) {
                issues.add("extra feature");
            }
            else {
                compareFeatures(modelFeatures, diagramFeatures, issues);
            }
        });
        return issues;
    }

    private void compareFeatures(Set<RenderableFeature> modelFeatures, 
                                 Set<RenderableFeature> diagramFeatures,
                                 Set<String> issues) {
        // The following issues are not exhausted. There is no need.
        // We just want to flag them with one issue.
        if (modelFeatures.size() > diagramFeatures.size())
            issues.add("missing feature");
        else if (modelFeatures.size() < diagramFeatures.size())
            issues.add("extra feature");
        else {
            // Check from the model feature
            for (RenderableFeature modelFeature : modelFeatures) {
                // Make sure it can be mapped
                if (!isFeatureIncluded(modelFeature, diagramFeatures)) {
                    issues.add("missing feature");
                    break;
                }
            }
            // Check from the diagram feature
            for (RenderableFeature diagramFeature : diagramFeatures) {
                if (!isFeatureIncluded(diagramFeature, modelFeatures)) {
                    issues.add("extra feature");
                    break;
                }
            }
        }
    }
    
    /**
     * Check if a RenderableFeature has been included in the passed set.
     * @param feature
     * @param features
     * @return
     */
    private boolean isFeatureIncluded(RenderableFeature feature,
                                      Set<RenderableFeature> features) {
        for (RenderableFeature f : features) {
            String fLabel = f.getLabel() == null ? "" : f.getLabel();
            String featureLabel = feature.getLabel() == null ? "" : feature.getLabel();
            // Since residue is not display, this check will ignore residue
//            String fResidue = f.getResidue() == null ? "" : f.getResidue();
//            String featureResidue = feature.getResidue() == null ? "" : feature.getResidue();
            if (//f.getFeatureType() == feature.getFeatureType() && // So far for all psiMod-based features,
                                                                  // featureType is always null!!!
                fLabel.equals(featureLabel))
                return true;
        }
        return false;
    }

    protected Map<Long, GKInstance> loadInstances(Collection<Node> nodes) throws Exception {
        // Fetch the corresponding instances.
        List<Long> dbIds = nodes.stream()
                .map(Renderable::getReactomeId)
                .collect(Collectors.toList());
        PersistenceAdaptor dataSource = getDatasource();
        Collection<GKInstance> instances;
        if (dataSource instanceof MySQLAdaptor) {
            MySQLAdaptor dba = (MySQLAdaptor)dataSource;
            instances = dba.fetchInstances(
                    ReactomeJavaConstants.EntityWithAccessionedSequence, dbIds);
            dba.loadInstanceAttributeValues(instances, LOAD_ATTS);
        } else {
            instances = new HashSet<GKInstance>();
            for (Long dbId: dbIds) {
                GKInstance instance = dataSource.fetchInstance(dbId);
                instances.add(instance);
            }
        }
        Map<Long, GKInstance> dbIdToInstMap = instances.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(GKInstance::getDBID, Function.identity()));
        return dbIdToInstMap;
    }
    
    private AttributeEditConfig loadAttributeEditConfig() throws Exception {
        AttributeEditConfig config = AttributeEditConfig.getConfig();
        InputStream metaConfig = GKApplicationUtilities.getConfig("curator.xml");
        if (metaConfig == null)
            return config;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(metaConfig);
        config.loadConfig(document);
        return config;
    }
    
    @Test
    public void testCheckInCommand() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        super.testCheckInCommand(dba);
    }

    @Test
    public void testCheckOneDiagram() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        setDatasource(dba);
        Long dbId = 9026069L;
        GKInstance instance = dba.fetchInstance(dbId);
        Collection<Long> wrongIds = doCheck(instance);
        System.out.println("Wrong ids: " + wrongIds);
    }
}
