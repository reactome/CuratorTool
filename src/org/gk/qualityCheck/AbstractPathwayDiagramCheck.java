/*
 * Created on Sep 29, 2010
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.ContainerNode;
import org.gk.render.Node;
import org.gk.render.ProcessNode;
import org.gk.render.ReactionNode;
import org.gk.render.Renderable;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderablePathway;
import org.gk.schema.InvalidAttributeException;

/**
 * This is the base class for QA checks which validate a pathway diagram.
 *
 * <em>Note</em>: this class extends {@link SingleAttributeClassBasedCheck}
 * even though a diagram check does not necessarily set or validate one
 * specific <code>PathwayDiagram</code> attribute.
 * {@link SingleAttributeClassBasedCheck} is extended because it provides
 * convenient reporting functionality.  
 *
 * @author Fred Loney loneyf@ohsu.edu
 */
abstract public class AbstractPathwayDiagramCheck extends SingleAttributeClassBasedCheck {

    /**
     * The pathway diagram {instance: issue db ids} map.
     */
    private HashMap<GKInstance, Collection<Long>> pdToIssueDbIds;
    
    /**
     * The parsed diagram XML reader.
     */
    protected DiagramGKBReader reader;

    /**
     * Sets the {@link SingleAttributeClassBasedCheck#checkClsName}
     * to <code>PathwayDiagram</code>.
     * 
     * The {@link SingleAttributeClassBasedCheck#checkAttribute} is
     * not set, since no specific attribute is suitable for display
     * purposes.
     */
    public AbstractPathwayDiagramCheck() {
        checkClsName = ReactomeJavaConstants.PathwayDiagram;
    }
    
    /**
     * The headers for the diagram db id, pathway name, pathway db id,
     * issue and author columns.
     * 
     * @see #getReportLines(GKInstance)
     */
    @Override
    protected String[] getColumnHeaders() {
        return new String[] {
                "Diagram_DBID", "Pathway_DisplayName", "Pathway_DBID",
                getIssueTitle(), "Created", "Modified"
        };
    }

    /**
     * The values for the diagram db id, pathway name, pathway db id,
     * issue and most recent author columns.
     * 
     * @param instance the pathway diagram instance with an issue
     * @see #getIssue(GKInstance)
     */
    @Override
    protected String[][] getReportLines(GKInstance instance) throws Exception {
        GKInstance pathway =
                (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.representedPathway);
        GKInstance created = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
        GKInstance modified = QACheckUtilities.getLatestCuratorIEFromInstance(instance);
        String[] line = new String[] {
                instance.getDBID().toString(),
                pathway.getDisplayName(),
                pathway.getDBID().toString(),
                getIssue(instance),
                created.getDisplayName(),
                modified.getDisplayName()
        };
        return new String[][] { line };
    }

    @Override
    protected void checkOutSelectedInstances(JFrame frame) {
        int reply = JOptionPane.showConfirmDialog(frame, 
                                                  "To fix a problem in a pathway diagram, please use the database event view to\n" +
                                                  "check out the pathway represented by the diagram. But you can still check out\n" + 
                                                  "the selected PathwayDiagram instance. Do you want to continue checking out?",
                                                  "Checking Out?",
                                                  JOptionPane.YES_NO_OPTION);
        if (reply == JOptionPane.NO_OPTION)
            return;
        super.checkOutSelectedInstances(frame);
    }
    
    /**
     * @param instance the PathwayDiagram instance
     * @return the  db ids to report
     */
    abstract protected Collection<Long> doCheck(GKInstance instance) throws Exception;

    /**
     * @param instance the PathwayDiagram instance
     * @return the QA report issue db column value
     */
    protected String getIssueDbIdsColumnValue(GKInstance instance) throws Exception {
        return getIssueDbIds(instance).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    protected Collection<Long> getIssueDbIds(GKInstance instance) {
        return pdToIssueDbIds.get(instance);
    }
    
    /**
     * Runs this QA check on the given PathwayDiagram instance
     * and returns whether the diagram has a reportable issue.
     * 
     * @param instance the PathwayDiagram instance
     * @return whether all of the diagram HyperEdge and ProcessNode
     *   <code>reactomeId</code> values are found in the database
     */
    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        if (!instance.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            throw new IllegalArgumentException(instance + " is not a PathwayDiagram instance!");
        // Make the invalid db ids map, if necessary.
        if (pdToIssueDbIds == null) {
            pdToIssueDbIds = new HashMap<GKInstance, Collection<Long>>();
        }
        // The invalid instances.
        Collection<Long> issueDbIds = doCheck(instance);
        if (!issueDbIds.isEmpty()) {
            pdToIssueDbIds.put(instance, issueDbIds);
        }
        return issueDbIds.size() == 0;
    }

    @Override
    protected void checkInstances(Collection<GKInstance> instances) throws Exception {
        // Clear the missing reaction db ids map, if necessary.
        if (pdToIssueDbIds != null) {
            pdToIssueDbIds.clear();
        }
        super.checkInstances(instances);
    }

    /**
     * Returns all diagram XML element <code>reactomeId</code> attribute
     * values found in the diagram.
     * 
     * @param instance the PathwayDiagram instance
     * @return the db ids, or null if the diagram XML is missing or empty
     * @throws InvalidAttributeException
     * @throws Exception
     */
    protected Collection<Long> extractReactomeIds(GKInstance instance) throws InvalidAttributeException, Exception {
        String xml = (String) instance.getAttributeValue(ReactomeJavaConstants.storedATXML);
        if (xml == null || xml.length() == 0)
            return null;
        // Don't try to load the diagram, which is very slow!!!
        //RenderablePathway diagram = reader.openDiagram(instance);
//        List<Renderable> components = diagram.getComponents();
//        if (components == null || components.size() == 0)
//            return true;  // Just ignore it
        Set<Long> dbIds = new HashSet<Long>();
        // Use this REGEXP to find all reactomeIds
        String regexp = "reactomeId=\"(-?(\\d)+)";
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(xml);
        int start = 0;
        while (matcher.find(start)) {
            start = matcher.end();
            String dbId = matcher.group(1); 
            dbIds.add(new Long(dbId));
        }
        return dbIds;
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, 
                                         ReactomeJavaConstants.PathwayDiagram);
    }

    /**
     * Returns the issue display table model.
     * 
     * The table title is specific to the missing db id check.
     * Subclasses should override either this method or
     * #getResultTableModel(String).
     */
    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        return new PathwayDiagramTableModel(getResultTableIssueDBIDColName());
    }

    /**
     * Returns the issue display table issue header.
     * 
     * return title the table header of the column used to display issues.
     */
    abstract protected String getResultTableIssueDBIDColName();

    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        // Need to load all complexes in case some complexes are used by complexes for checking
        if (progressPane != null)
            progressPane.setText("Load PathwayDiagram attributes ...");
        loadAttributes(ReactomeJavaConstants.PathwayDiagram, 
                       ReactomeJavaConstants.storedATXML, 
                       dba);
    }

    protected RenderablePathway getRenderablePathway(GKInstance pathwayDiagramInst) throws Exception {
        if (reader == null)
            reader = new DiagramGKBReader();
        RenderablePathway diagram = reader.openDiagram(pathwayDiagramInst);
        return diagram;
    }

    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        return getIssueDbIds(instance).stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    /**
     * Returns whether the given diagram component represents a
     * database PhysicalEntity.
     * 
     * @param component the component to check
     * @return whether the component represents a PhysicalEntity
     */
    protected boolean isPhysicalEntityNode(Renderable component) {
        return component.getReactomeId() != null &&
                component instanceof Node &&
                (component instanceof RenderableComplex ||
                 !(component instanceof ContainerNode ||
                         component instanceof ProcessNode ||
                         component instanceof ReactionNode));
    }

    /**
     * Returns the diagram components which represent a database PhysicalEntity.
     * 
     * @param diagram the diagram to check
     * @return the entity nodes
     */
    protected Collection<Node> getPhysicalEntityNodes(RenderablePathway diagram) {
        @SuppressWarnings("unchecked")
        List<Renderable> components = diagram.getComponents();
        if (components == null) {
            return Collections.emptyList();
        }
        
        return components.stream()
                .filter(cmpnt -> isPhysicalEntityNode(cmpnt))
                .map(cmpnt -> (Node) cmpnt)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("serial")
    private class PathwayDiagramTableModel extends ResultTableModel {
        private String[] values = null;
        
        public PathwayDiagramTableModel(String title) {
            setColNames(new String[]{"Pathway", title});
        }

        @Override
        public void setInstance(GKInstance instance) {
            if (values == null)
                values = new String[2];
            try {
                // The reported pathway.
                GKInstance pathway = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.representedPathway);
                values[0] = pathway.getDisplayName() + " [" + pathway.getDBID() + "]";
                // The reported db ids.
                Collection<Long> dbIds = pdToIssueDbIds.get(instance);
                if (dbIds == null) {
                    values[1] = "";
                } else {
                    values[1] = dbIds.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));
                }
                fireTableStructureChanged();
            }
            catch(Exception e) {
                System.err.println("PathwayDiagramTableModel.setInstance(): " + e);
                e.printStackTrace();
            }
        }

        public int getRowCount() {
            return values == null ? 0 : 1;
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            if (values == null)
                return null;
            return values[columnIndex];
        }
        
    }
}
