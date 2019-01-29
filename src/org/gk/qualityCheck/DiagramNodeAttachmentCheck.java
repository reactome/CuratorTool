/*
 * Created on Apr 25, 2014
 *
 */
package org.gk.qualityCheck;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * This check is to make sure hasModified values in EntityWithAccessionedSequence instances
 * are correctly displayed in pathway diagrams. The output should be similar to one from
 * T114 and T115 in the diagram-converter.
 * 
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class DiagramNodeAttachmentCheck extends PathwayDiagramCheck {

    private static final String[] HEADERS = {
            "Diagram_DBID",
            "Pathway_DisplayName", 
            "Pathway_DBID",
            "Entity_DBID", 
            "Entity_DisplayName", 
            "Attachment_DBIDs", 
            "Issue",
            "Created", 
            "Modified"
    };
    
    private static final String[] LOAD_ATTS = {
            ReactomeJavaConstants.hasModifiedResidue
    };
     
    private static class IssueDetail {
        GKInstance entity;
        Collection<Long> residueDbIds;
        String issue;
    }
    
    // The reaction issue reporting details.
    private Map<Long, List<IssueDetail>> ewasDbIdToDetails = new HashMap<>();
    
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
            List<IssueDetail> details = ewasDbIdToDetails.get(ewasDBID);
            for (IssueDetail detail: details) {
                String residueDbIds = detail.residueDbIds.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                String[] line = {
                        pathwayDiagramInst.getDBID().toString(),
                        pathway.getDisplayName(),
                        pathway.getDBID().toString(),
                        detail.entity.getDBID().toString(),
                        detail.entity.getDisplayName(),
                        residueDbIds,
                        detail.issue,
                        created.getDisplayName(),
                        modified.getDisplayName()
                };
                lines.add(line);
            }
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
    @SuppressWarnings("unchecked")
    private void checkAttachments(Collection<Node> nodes) throws Exception {
        Map<Long, GKInstance> dbIdToInstMap = loadInstances(nodes);
        // Load the config containing the attachment short names.
        loadAttributeEditConfig();
        // Check each EWAS node.
        for (Node node: nodes) {
            Long ewasDbId = node.getReactomeId();
            GKInstance instance = dbIdToInstMap.get(ewasDbId);
            if (instance == null)
                continue;
            List<NodeAttachment> attachments = node.getNodeAttachments();
            if (attachments == null) {
                attachments = Collections.emptyList();
            }
            // The non-null diagram attachment reactomeIds.
            Set<Long> attachmentDbIds = attachments.stream()
                    .map(NodeAttachment::getReactomeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            // The database modified residue instances.
            List<GKInstance> residues = instance.getAttributeValuesList(ReactomeJavaConstants.hasModifiedResidue);
            Map<Long, GKInstance> dbIdToResidueMap = new HashMap<Long, GKInstance>();
            for (GKInstance residue: residues) {
                dbIdToResidueMap.put(residue.getDBID(), residue);
            }
            
            // Check for missing or extra diagram attachments.
            Set<Long> residueDbIds = dbIdToResidueMap.keySet();
            List<IssueDetail> details = new ArrayList<IssueDetail>();
            if (!residueDbIds.equals(attachmentDbIds)) {
                Set<Long> missing = new HashSet<Long>(residueDbIds);
                missing.removeAll(attachmentDbIds);
                if (!missing.isEmpty()) {
                    IssueDetail detail = new IssueDetail();
                    detail.entity = instance;
                    detail.residueDbIds = missing;
                    detail.issue = "Missing attachments";
                    details.add(detail);
                }
                Set<Long> extra = new HashSet<Long>(attachmentDbIds);
                extra.removeAll(residueDbIds);
                if (!extra.isEmpty()) {
                    IssueDetail detail = new IssueDetail();
                    detail.entity = instance;
                    detail.residueDbIds = extra;
                    detail.issue = "Extra attachments";
                    details.add(detail);
                }
            }
            
            // Check the labels.
            Map<String, Set<Long>> missing = new HashMap<>(); 
            Map<String, Set<Long>> extra = new HashMap<>(); 
            Map<String, Map<String, Set<Long>>> mismatch = new HashMap<>();
            for (NodeAttachment attachment: attachments) {
                Long dbId = attachment.getReactomeId();
                if (dbId == null) {
                    continue;
                }
                GKInstance residue = dbIdToResidueMap.get(dbId);
                if (residue == null) {
                    continue;
                }
                // The label displayed in the diagram.
                String diagLabel = attachment.getLabel();
                // Coerce an empty label to null for comparison.
                if (diagLabel != null && diagLabel.isEmpty()) {
                    diagLabel = null;
                }
                // The label inferred from the database content.
                String dbLabel = getResidueLabel(residue);
                if (dbLabel != null && dbLabel.isEmpty()) {
                    dbLabel = null;
                }
                
                // If the labels differ, then determine whether the diagram
                // label is missing, extraneous or doesn't match the expected
                // label.
                if (!Objects.equals(diagLabel, dbLabel)) {
                    if (diagLabel == null) {
                        Set<Long> missingLabelDbIds = missing.get(dbLabel);
                        if (missingLabelDbIds == null) {
                            missingLabelDbIds = new HashSet<Long>();
                            missing.put(dbLabel, missingLabelDbIds);
                        }
                        missingLabelDbIds.add(dbId);
                    }
                    else if (dbLabel == null) {
                        Set<Long> extraLabelDbIds = extra.get(dbLabel);
                        if (extraLabelDbIds == null) {
                            extraLabelDbIds = new HashSet<Long>();
                            extra.put(diagLabel, extraLabelDbIds);
                        }
                        extraLabelDbIds.add(dbId);
                    }
                    else {
                        Map<String, Set<Long>> found = mismatch.get(dbLabel);
                        if (found == null) {
                            found = new HashMap<String, Set<Long>>();
                            mismatch.put(dbLabel, found);
                        }
                        Set<Long> mismatchDbIds = found.get(diagLabel);
                        if (mismatchDbIds == null) {
                            mismatchDbIds = new HashSet<Long>();
                            found.put(diagLabel, mismatchDbIds);
                        }
                    }
                }
                
                // Capture the report issue details.
                for (Entry<String, Set<Long>> entry: missing.entrySet()) {
                    IssueDetail detail = new IssueDetail();
                    detail.entity = instance;
                    detail.residueDbIds = entry.getValue();
                    detail.issue = "Missing label: " + entry.getKey();
                    details.add(detail);
                }
                for (Entry<String, Set<Long>> entry: extra.entrySet()) {
                    IssueDetail detail = new IssueDetail();
                    detail.entity = instance;
                    detail.residueDbIds = entry.getValue();
                    detail.issue = "Extra label: " + entry.getKey();
                    details.add(detail);
                }
                for (Entry<String, Map<String, Set<Long>>> entry: mismatch.entrySet()) {
                    String expected = entry.getKey();
                    Map<String, Set<Long>> dtlMap = entry.getValue();
                    for (Entry<String, Set<Long>> dtlEntry: dtlMap.entrySet()) {
                        String found = dtlEntry.getKey();
                        IssueDetail detail = new IssueDetail();
                        detail.entity = instance;
                        detail.residueDbIds = dtlEntry.getValue();
                        detail.issue = "Mismatched label: expected " + expected + " found " + found;
                        details.add(detail);
                    }
                }
                if (!details.isEmpty()) {
                    ewasDbIdToDetails.put(ewasDbId, details);
                }
            }
        }
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
    
    /**
     * Returns the label for the given residue, determined as follows:
     * <ul>
     * <li>If the residue has a <em>modification</em> value which maps
     *     to a {@link AttributeEditConfig#getModifications()} short name,
     *     then that is the label.
     * </ul>
     * <li>Otherwise, if the residue has a <em>psiMod</em> value and the
     *     parsed residue display name contains a
     *     {@link AttributeEditConfig#getPsiModifications()} short name,
     *     then that is the label.
     * </ul>
     * <li>Otherwise, the label is null.
     * </ul>
     * 
     * This code is adapted from
     * {@link ModifiedResidueHandler#convertModifiedResidue(GKInstance)}.
     * 
     * @param modifiedResidue
     * @return the label
     * @throws Exception
     */
    private String getResidueLabel(GKInstance modifiedResidue) throws Exception {
        ModifiedResidueHandler handler = new ModifiedResidueHandler();
        RenderableFeature feature = handler.convertModifiedResidue(modifiedResidue);
        return feature.getLabel();
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

     
}
