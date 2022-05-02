/*
 * Created on Jun 2, 2004
 */
package org.gk.pathView;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionListener;

import org.gk.model.GKInstance;
import org.gk.persistence.Neo4JAdaptor;

/**
 * A customized JPanel that can be used to compare events from two reactome
 * databases.
 * @author wugm
 */
public class ComparableBrowserPane extends JPanel {
	// Two browser pane
	PathwayBrowserPanel oldPane; // The left pane
	PathwayBrowserPanel newPane; // The right pane
	//One comparison pane
	ReactionComparisonPane comparisonPane; // The center pane

	public ComparableBrowserPane() {
		init();
	}
	
	private void init() {
		oldPane = new PathwayBrowserPanel();
		oldPane.setSplitPaneOrientation(JSplitPane.VERTICAL_SPLIT);
		oldPane.setMinimumSize(new Dimension(20, 20));
		newPane = new PathwayBrowserPanel();
		newPane.setSplitPaneOrientation(JSplitPane.VERTICAL_SPLIT);
		newPane.setMinimumSize(new Dimension(20, 20));
		comparisonPane = new ReactionComparisonPane();
		// Set up the display
		JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, oldPane, comparisonPane);
		jsp.setResizeWeight(0.5);
		JSplitPane jsp1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jsp, newPane);
		jsp1.setResizeWeight(0.67);
		setLayout(new BorderLayout());
		add(jsp1, BorderLayout.CENTER);
		// To synchronized selection
		comparisonPane.getNewReactionPane().addSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				final java.util.List selection = comparisonPane.getNewReactionPane().getSelection();
				newPane.highliteNodes(selection);
			}
		});
		comparisonPane.getDeleteReactionPane().addSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				java.util.List selection = comparisonPane.getDeleteReactionPane().getSelection();
				oldPane.highliteNodes(selection);
			}
		});
		comparisonPane.getChangedReactionPane().addSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				// Try to use a new thread to speed up performance
				java.util.List selection = comparisonPane.getChangedReactionPane().getSelection();
				newPane.highliteNodes(selection);
				// Have to fetch the corresponding reactions
				java.util.List oldInstances = new ArrayList(selection.size());
				Neo4JAdaptor oldDBA = oldPane.getNeo4JAdaptor();
				for (Iterator it = selection.iterator(); it.hasNext();) {
					GKInstance instance = (GKInstance) it.next();
					try {
						GKInstance oldInstance = oldDBA.fetchInstance(instance.getSchemClass().getName(), 
						                                              instance.getDBID());
						if (oldInstance != null) {
							oldInstances.add(oldInstance);
						}
					}
					catch(Exception e1) {
						e1.printStackTrace();
					}
				}
				oldPane.highliteNodes(oldInstances);
			}
		});
	}
	
	/**
	 * Set Neo4JAdaptors that connect to the reactome databases.
	 * @param oldAdaptor connects to the old database.
	 * @param newAdaptor connects to the new database.
	 */
	public void setNeo4JAdaptors(Neo4JAdaptor oldAdaptor, Neo4JAdaptor newAdaptor) {
		oldPane.setNeo4JAdaptor(oldAdaptor);
		newPane.setNeo4JAdaptor(newAdaptor);
		comparisonPane.setNeo4JAdaptors(oldAdaptor, newAdaptor);
	}
	
	public void select(Collection selection) {
		if (selection == null || selection.size() == 0)
			return;
		// Have to convert GKInstances since dba is refreshed
		java.util.List newList = new ArrayList(selection.size());
		java.util.List oldList = new ArrayList(selection.size());
		Neo4JAdaptor newDBA = newPane.getNeo4JAdaptor();
		Neo4JAdaptor oldDBA = oldPane.getNeo4JAdaptor();
		GKInstance instance = null;
		GKInstance newCopy = null;
		GKInstance oldCopy = null;
		String clsName = null;
		for (Iterator it = selection.iterator(); it.hasNext();) {
			instance = (GKInstance) it.next();
			clsName = instance.getSchemClass().getName();
			try {
				newCopy = newDBA.fetchInstance(clsName, instance.getDBID());
				if (newCopy != null)
					newList.add(newCopy);
				oldCopy = oldDBA.fetchInstance(clsName, instance.getDBID());
				if (oldCopy != null)
					oldList.add(oldCopy);
			}
			catch (Exception e) {
				System.err.println("ComparableBrowserPane.select(): " + e);
				e.printStackTrace();
			}
		}
		newPane.highliteNodes(newList);
		// Have to convert GKInstance objects in selection to ones in oldAdaptor.
		oldPane.highliteNodes(oldList);
	}
	
	public void addSelectionListener(TreeSelectionListener l) {
		newPane.addSelectionListener(l);
		oldPane.addSelectionListener(l);
	}
	
	public void removeSelectionListener(TreeSelectionListener l) {
		newPane.removeSelectionListener(l);
		oldPane.removeSelectionListener(l);
	}
	
	public void search(String name) {
		newPane.search(name);
		oldPane.search(name);
	}
}