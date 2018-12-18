/*
 * Created on Mar 11, 2011
 *
 */
package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.HyperEdge;
import org.gk.render.ProcessNode;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.StringUtils;
import org.junit.Test;


/**
 * This QA checks if all reactions contained by a Pathway have been drawn in its PathwayDiagram. If
 * a PathwayDiagram contains a sub-pathway, reactions contained by this sub-pathway will not be
 * checked.
 * @author wgm
 *
 */
public class DiagramReactionsCheck extends PathwayDiagramCheck {
    protected DiagramGKBReader reader;
    
    protected Map<GKInstance, String> instToIssue;

    private HashMap<GKInstance, Collection<Long>> instToMissingRxtIds;

    public DiagramReactionsCheck() {
    }
    
    @Test
    public void testCheckInstance() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_082211",
                                            "root",
                                            "macmysql01");
        setDatasource(dba);
        GKInstance diagram = dba.fetchInstance(987018L);
        GKInstance pathway = (GKInstance) diagram.getAttributeValue(ReactomeJavaConstants.representedPathway);
        Set<Long> missingRxtIds = getMissingReactionIds(diagram, pathway);
        System.out.println("Total missing ids: " + missingRxtIds.size());
    }
    
    @Override
    public String getDisplayName() {
        return "Diagram_Missing_Reactions";
    }
    
    /**
     * Formats the issue string as a comma-delimited list of
     * the missing reaction <code>reactomeId</code> values
     * 
     * @param instance the PathwayDiagram instance
     * @return the QA report issue string
     */
    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        Collection<Long> missingRxtIds = instToMissingRxtIds.get(instance);
        StringBuilder builder = new StringBuilder();
        missingRxtIds.forEach(id -> builder.append(","));
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }
    
    @Override
    protected String getIssueTitle() {
        return "IDsOfReactionsMissingInDiagrams";
    }

    @Override
    protected void checkInstances(Collection<GKInstance> instances) throws Exception {
        // Clear the missing reaction db ids map, if necessary.
        if (instToMissingRxtIds != null) {
            instToMissingRxtIds.clear();
        }
        super.checkInstances(instances);
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
        // Make the missing reaction db ids map, if necessary.
        if (instToMissingRxtIds == null) {
            instToMissingRxtIds = new HashMap<GKInstance, Collection<Long>>();
        }
        Set<Long> missingRxtIds = getMissingReactionIds(instance);
        if (!missingRxtIds.isEmpty()) {
            instToMissingRxtIds.put(instance, missingRxtIds);
        }
        return missingRxtIds.size() == 0;
    }

    /**
     * Validates that the given instance is a PathwayDiagram and
     * returns the diagram reaction <code>reactomeId</code> values
     * which are not in the represented Pathway event hierarchy.
     * 
     * @param instance the PathwayDiagram instance
     * @return the mimssing db ids
     * @throws IllegalArgumentException is the instance is not a PathwayDiagram
     * @throws Exception
     */
    protected Set<Long> getMissingReactionIds(GKInstance instance) throws Exception {
        if (!instance.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            throw new IllegalArgumentException(instance + " is not a PathwayDiagram instance!");
        GKInstance pathway = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.representedPathway);
        Set<Long> missingRxtIds = getMissingReactionIds(instance, pathway);
        return missingRxtIds;
    }
    
    /**
     * Detects the diagram reaction representation <code>reactomeId</code>
     * values which are not found in the database.
     * 
     * If the <em>pathway</em> argument is null, then an empty set is returned.
     * 
     * @param diagram
     * @param pathway
     * @return 
     * @throws Exception
     */
    private Set<Long> getMissingReactionIds(GKInstance diagram,
                                            GKInstance pathway) throws Exception {
        if (pathway == null) {
            return Collections.emptySet();
        }
        Set<Long> rtn = new HashSet<Long>();
        // Get all contained Event ids by this Pathway
        Set<Long> dbIds = extractReactomeIds(diagram);
        if (dbIds == null || dbIds.size() == 0) {
            return rtn; // Nothing has been drawn yet in this diagram
        }
        // Get all contained reactions
        Set<GKInstance> reactions = InstanceUtilities.grepPathwayEventComponents(pathway);
        filterOutDoNotReleaseEvents(reactions);
        // Check DB_IDs to see if any Pathway there
        GKInstance event = null;
        for (Long dbId : dbIds) {
            event = dataSource.fetchInstance(dbId);
            if (event == null && parentComp != null) {
                JOptionPane.showMessageDialog(parentComp,
                                              "Instance with DB_ID displayed in " + diagram + "\ncannot be found: " + dbId,
                                              "Null Instance",
                                              JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (event != null && event.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                Set<GKInstance> reactions1 = InstanceUtilities.grepPathwayEventComponents(event);
                reactions.removeAll(reactions1);
            }
        }
        // Check if any reaction is missing
        for (GKInstance rxt : reactions) {
            if (!dbIds.contains(rxt.getDBID()))
                rtn.add(rxt.getDBID());
        }
        return rtn;
    }
    
    private void filterOutDoNotReleaseEvents(Set<GKInstance> events) throws Exception {
        if (events == null || events.size() == 0)
            return;
        for (Iterator<GKInstance> it = events.iterator(); it.hasNext();) {
            GKInstance event = it.next();
            // Only need to check reactions having been released
            Boolean _doRelease = (Boolean) event.getAttributeValue(ReactomeJavaConstants._doRelease);
            if (_doRelease == null || !_doRelease)
                it.remove();
        }
    }

    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        super.loadAttributes(instances);
        // Need to load all Pathways and its contained Events
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        // To check pathways
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.PathwayDiagram);
        SchemaAttribute att = cls.getAttribute(ReactomeJavaConstants.representedPathway);
        dba.loadInstanceAttributeValues(instances, att);
        if (progressPane != null)
            progressPane.setText("Load Events and their attributes...");
        Collection<?> c = dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway);
        cls = dba.getSchema().getClassByName(ReactomeJavaConstants.Pathway);
        att = cls.getAttribute(ReactomeJavaConstants.hasEvent);
        dba.loadInstanceAttributeValues(c, att);
        att = cls.getAttribute(ReactomeJavaConstants._doRelease);
        dba.loadInstanceAttributeValues(c, att);
        c = dba.fetchInstancesByClass(ReactomeJavaConstants.Reaction);
        cls = dba.getSchema().getClassByName(ReactomeJavaConstants.ReactionlikeEvent);
        if (cls.isValidAttribute(ReactomeJavaConstants.hasMember)) {
            att = cls.getAttribute(ReactomeJavaConstants.hasMember);
            dba.loadInstanceAttributeValues(c, att);
        }
        att = cls.getAttribute(ReactomeJavaConstants._doRelease);
        dba.loadInstanceAttributeValues(c, att);
    }

    /**
     * Overrides the base class method behaviour to return only the
     * <code>reactomeId</code> values of HyperEdge and ProcessNode
     * renderables in the given diagram.
     * 
     * @param instance the PathwayDiagram instance
     */
    @Override
    protected Set<Long> extractReactomeIds(GKInstance instance) throws InvalidAttributeException, Exception {
        String xml = (String) instance.getAttributeValue(ReactomeJavaConstants.storedATXML);
        if (xml == null || xml.length() == 0)
            return null;
        if (reader == null)
            reader = new DiagramGKBReader();
        RenderablePathway diagram = reader.openDiagram(xml);
        List<?> list = diagram.getComponents();
        if (list == null || list.size() == 0)
            return null;
        Set<Long> rtn = new HashSet<Long>();
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            Object obj = it.next();
            if (obj instanceof HyperEdge || obj instanceof ProcessNode) {
                Renderable r = (Renderable) obj;
                if (r.getReactomeId() != null)
                    rtn.add(r.getReactomeId());
            }
        }
        return rtn;
    }
    
    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        return new DiagramReactionsTableModel();
    }
    
    @SuppressWarnings("serial")
	protected class DiagramReactionsTableModel extends ResultTableModel {
        protected String[] values = null;
        
        public DiagramReactionsTableModel() {
            setColNames(new String[]{"Pathway", "Missing Reaction IDs"});
        }

        @Override
        public void setInstance(GKInstance instance) {
            if (values == null)
                values = new String[2];
            try {
                GKInstance pathway = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.representedPathway);
                values[0] = pathway.getDisplayName() + " [" + pathway.getDBID() + "]";
                // Get DB_IDs that are not in the database
                Set<Long> missingRxtIds = getMissingReactionIds(instance, pathway);
                values[1] = StringUtils.join(", ", new ArrayList<Long>(missingRxtIds));
                fireTableStructureChanged();
            }
            catch(Exception e) {
                System.err.println("DiagramReactionsTableModel.setInstance(): " + e);
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
