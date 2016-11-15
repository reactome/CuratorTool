/*
 * Created on May 7, 2015
 *
 */
package org.gk.database;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import org.gk.gkEditor.ZoomablePathwayEditor;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.StringUtils;

/**
 * This small class is used to display a diagram for a selected Pathway instance
 * or PathwayDiagram instance.
 * @author gwu
 *
 */
public class DiagramDisplayHandler {
    private Component parentComponent;
    
    /**
     * Default constructor.
     */
    public DiagramDisplayHandler() {
    }
    
    
    public Component getParentComponent() {
        return parentComponent;
    }


    public void setParentComponent(Component parentComponent) {
        this.parentComponent = parentComponent;
    }


    public void showPathwayDiagram(GKInstance inst) {
        if (!inst.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            return;
        try {
            DiagramGKBReader reader = new DiagramGKBReader();
            RenderablePathway diagram = reader.openDiagram(inst);
            ZoomablePathwayEditor pathwayEditor = new ZoomablePathwayEditor();
            pathwayEditor.getPathwayEditor().addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        doPathwayEditorPopup(e);
                    }
                }
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger())
                        doPathwayEditorPopup(e);
                }
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2)
                        doPathwayEditorDoubleClick(e);
                }
            });
            pathwayEditor.getPathwayEditor().setRenderable(diagram);
            // Check if there is any objects have been deleted
            Set<Long> dbIds = new HashSet<Long>();
            List<Renderable> components = diagram.getComponents();
            if (components != null) {
                for (Renderable r : components) {
                    if (r.getReactomeId() == null)
                        continue;
                    dbIds.add(r.getReactomeId());
                }
                // Need to check these instances in the database
                MySQLAdaptor dba = (MySQLAdaptor) inst.getDbAdaptor();
                Collection<?> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseObject,
                                                               ReactomeJavaConstants.DB_ID,
                                                               "=", 
                                                               dbIds);
                for (Iterator<?> it = c.iterator(); it.hasNext();) {
                    GKInstance tmp = (GKInstance) it.next();
                    dbIds.remove(tmp.getDBID());
                }
                if (dbIds.size() > 0) {
                    // Try to highlight instances that have been deleted
                    for (Renderable r : components) {
                        if (r.getReactomeId() == null)
                            continue;
                        if (dbIds.contains(r.getReactomeId())) {
                            r.setForegroundColor(Color.RED);
                            r.setLineColor(Color.RED);
                        }
                    }
                }
            }          
            pathwayEditor.setTitle("<html><u>Diagram View: " + inst.getDisplayName() + "</u></html>");
            pathwayEditor.getPathwayEditor().setEditable(false);
            JFrame frame = new JFrame("Pathway Diagram View");
            frame.getContentPane().add(pathwayEditor, BorderLayout.CENTER);
            frame.setSize(800, 600);
            if (parentComponent == null)
                GKApplicationUtilities.center(frame);
            else
                frame.setLocationRelativeTo(parentComponent);
            
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);
            if (dbIds.size() > 0) {
                String message = null;
                if (dbIds.size() == 1)
                    message = "An object displayed in the diagram has been deleted in the database.\n" +
                              "This object is highlighted in red and with DB_ID " + dbIds.iterator().next() + ".";
                else
                    message = "Some objects displayed in the diagram have been deleted in the database.\n" +
                              "These objects are highlighted in red and with DB_IDs as following:\n" +
                              StringUtils.join(", ", new ArrayList<Long>(dbIds));
                JOptionPane.showMessageDialog(frame,
                                              message,
                                              "Deleted Objects in Diagram",
                                              JOptionPane.WARNING_MESSAGE);
            }
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent, 
                                          "Cannot show diagram: " + e.getMessage(), 
                                          "Error in Show Diagram",
                                          JOptionPane.ERROR_MESSAGE);
            System.err.println("GKDBBrowserPopupManager.showPathwayDiagram(): " + e);
            e.printStackTrace();
        }
    }
    
    private void doPathwayEditorDoubleClick(MouseEvent e) {
        PathwayEditor editor = (PathwayEditor) e.getSource();
        List<?> selection = editor.getSelection();
        if (selection == null || selection.size() != 1)
            return;
        Renderable r = (Renderable) selection.get(0);
        if (r.getReactomeId() == null)
            return;
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
        try {
            GKInstance inst = dba.fetchInstance(r.getReactomeId());
            if (inst == null)
                return;
            FrameManager.getManager().showInstance(inst, false);
        }
        catch(Exception e1) {
            // Don't popup any dialog. Just silently printout exception
            System.err.println("GKDBBrowserPopupManager.doPathwayEditorDoubleClick(): " + e1);
            e1.printStackTrace();
        }
    }
    
    private void doPathwayEditorPopup(MouseEvent e) {
        final PathwayEditor editor = (PathwayEditor) e.getSource();
        JPopupMenu popup = new JPopupMenu();
        // Add a search diagram feature
        JMenuItem searchById = new JMenuItem("Search by ID");
        searchById.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                editor.searchById();
            }
        });
        popup.add(searchById);
        popup.addSeparator();
        // Used to view the selected entity
        List selection = editor.getSelection();
        if (selection != null && selection.size() == 1) {
            final Renderable r = (Renderable) selection.get(0);
            if (r.getReactomeId() != null) {
                Action action = new AbstractAction("View Instance") {
                    public void actionPerformed(ActionEvent e) {
                        try {
                            MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
                            GKInstance inst = dba.fetchInstance(r.getReactomeId());
                            if (inst == null) {
                                JOptionPane.showMessageDialog(editor,
                                                              "Cannot find instance for " + r.getReactomeId() + ". It may have been deleted.",
                                                              "Error in Fetching Instance",
                                                              JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            FrameManager.getManager().showInstance(inst, false);
                        }
                        catch(Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                };
                popup.add(action);
                // Add another action to view referrers
                JMenuItem viewReferrers = new JMenuItem("View Referrers");
                viewReferrers.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent arg0) {
                        try {
                            MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
                            GKInstance inst = dba.fetchInstance(r.getReactomeId());
                            if (inst == null) {
                                JOptionPane.showMessageDialog(editor,
                                        "Cannot find instance for " + r.getReactomeId() + ". It may have been deleted.",
                                        "Error in Fetching Instance",
                                        JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            ReverseAttributePane referrersPane = new ReverseAttributePane();
                            referrersPane.displayReferrersWithCallback(inst, editor);
                        }
                        catch(Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                });
                popup.add(viewReferrers);
                popup.addSeparator();
            }
        }
        Action exportDiagramAction = getExportDiagramAction(editor);
        popup.add(exportDiagramAction);
        Action tightNodeAction = new AbstractAction("Tight Nodes") {
            public void actionPerformed(ActionEvent e) {
                editor.tightNodes();
            }
        };
        popup.add(tightNodeAction);
        Action wrapTextIntoNodesAction = new AbstractAction("Wrap Text into Nodes") {
            public void actionPerformed(ActionEvent e) {
                editor.tightNodes(true);
            }
        };
        popup.add(wrapTextIntoNodesAction);
        popup.show(editor, e.getX(), e.getY());
    }
    
    /**
     * This following action is copied from action in AuthorToolActionCollection. The code
     * should not be copied like this. However, since AuthorToolActionCollection is encapsulated
     * in a class in package org.gk.gkEditor, which is higher than the database package, this method
     * cannot be used directly here. Probably a necessary refactor should be done in the future.
     * @param pathwayEditor
     * @return
     */
    private Action getExportDiagramAction(final PathwayEditor pathwayEditor) {
        Action action = new AbstractAction("Export Diagram") {
            public void actionPerformed(ActionEvent e) {
                try {
                    pathwayEditor.exportDiagram();
                }
                catch(IOException e1) {
                    System.err.println("GKDBBrowserPopupManager.getExportDiagramActio(): " + e1);
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(pathwayEditor,
                                                  "Pathway diagram cannot be exported: " + e1,
                                                  "Error in Diagram Export",
                                                  JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        return action;
    }
    
}
