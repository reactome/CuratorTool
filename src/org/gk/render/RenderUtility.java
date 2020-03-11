/*
 * Created on Jul 18, 2003
 */
package org.gk.render;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.util.DrawUtilities;

/**
 * Some handy methods are collected here.
 * @author wgm
 */
public class RenderUtility {
    
    private static List<List<Point>> copyListsOfPoints(List<List<Point>> listOfPoints) {
        if (listOfPoints == null)
            return null;
        List<List<Point>> copy = new ArrayList<>();
        for (List<Point> points : listOfPoints) {
            List<Point> subCopy = copyPoints(points);
            copy.add(subCopy);
        }
        return copy;
    }
    
    private static List<Point> copyPoints(List<Point> points) {
        if (points == null)
            return null;
        List<Point> copy = points.stream().map(p -> new Point(p)).collect(Collectors.toList());
        return copy;
    }
    
    /**
     * Create a shallow copy of this HyperEdge. All attributes in the returned copy are shared
     * with this object.
     * 
     * @param edge HyperEdge object to copy
     * @return Copy of the HyperEdge object passed
     */
    public static HyperEdge copyHyperEdge(HyperEdge edge) {
        try {
            HyperEdge copy = edge.getClass().newInstance();
            // Position should be copied first
            Point position = edge.getPosition();
            copy.setPosition(position == null ? null : new Point(position));
            copy.setBackbonePoints(copyPoints(edge.getBackbonePoints()));
            copy.setInputPoints(copyListsOfPoints(edge.getInputPoints()));
            copy.setOutputPoints(copyListsOfPoints(edge.getOutputPoints()));
            copy.setHelperPoints(copyListsOfPoints(edge.getHelperPoints()));
            copy.setInhibitorPoints(copyListsOfPoints(edge.getInhibitorPoints()));
            copy.setActivatorPoints(copyListsOfPoints(edge.getActivatorPoints()));
            copy.setNeedInputArrow(edge.isNeedInputArrow());
            copy.setNeedOutputArrow(edge.isNeedOutputArrow());
            copy.setLineWidth(edge.getLineWidth());
            if (edge.getAttributes() != null)
                copy.attributes = new HashMap<>(edge.getAttributes()); // Need to copy this to avoid any clash (e.g. display name)
            copy.setForegroundColor(edge.getForegroundColor());
            copy.setBackgroundColor(edge.getBackgroundColor());
            copy.setLineColor(edge.getLineColor());
            copy.setIsVisible(edge.isVisible());
            Rectangle bounds = edge.getBounds();
            copy.setBounds((bounds == null ? null : new Rectangle(bounds)));
            copy.setRenderer(edge.getRenderer());
            // Make a new copy of connect info
            HyperEdgeConnectInfo connectInfoCopy = new HyperEdgeConnectInfo();
            copy.connectInfo = connectInfoCopy;
            ConnectInfo connectInfo = edge.getConnectInfo();
            if (connectInfo.getConnectWidgets() != null) {
                for (ConnectWidget widget : connectInfo.getConnectWidgets()) {
                    ConnectWidget widgetClone = widget.shallowCopy();
                    replaceWidgetPoints(widgetClone, copy);
                    widgetClone.setConnectedNode(widget.getConnectedNode());
                    widgetClone.setEdge(copy);
                    widgetClone.connect();
                }
            }
            return copy;
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private static void replaceWidgetPoints(ConnectWidget widget, HyperEdge edge) {
        List<List<Point>> listOfPoints = null;
        List<Point> backbonePoints = edge.getBackbonePoints();
        Point controlPoint = null;
        List<Point> points = null;
        if (widget.getRole() == HyperEdge.INPUT) {
            listOfPoints = edge.getInputPoints();
            if (listOfPoints == null) {
                points = backbonePoints;
            }
            else {
                points = listOfPoints.get(widget.getIndex());
                controlPoint = backbonePoints.get(0);
            }
        }
        else if (widget.getRole() == HyperEdge.OUTPUT) {
            listOfPoints = edge.getOutputPoints();
            if (listOfPoints == null) {
                points = backbonePoints;
            }
            else {
                points = listOfPoints.get(widget.getIndex());
                controlPoint = backbonePoints.get(backbonePoints.size() - 1);
            }
        }
        else if (widget.getRole() == HyperEdge.CATALYST) {
            listOfPoints = edge.getHelperPoints();
            points = listOfPoints.get(widget.getIndex());
            controlPoint = edge.getPosition();
        }
        else if (widget.getRole() == HyperEdge.ACTIVATOR) {
            listOfPoints = edge.getActivatorPoints();
            points = listOfPoints.get(widget.getIndex());
            controlPoint = edge.getPosition();
        }
        else if (widget.getRole() == HyperEdge.INHIBITOR) {
            listOfPoints = edge.getInhibitorPoints();
            points = listOfPoints.get(widget.getIndex());
            controlPoint = edge.getPosition();
        }
        replaceWidgetPoints(widget, points, controlPoint);
    }

    private static void replaceWidgetPoints(ConnectWidget widget,
                                            List<Point> points,
                                            Point controlP) {
        if (points.size() == 1) {
            widget.setPoint(points.get(0));
            if (controlP != null)
                widget.setControlPoint(controlP);
        }
        else {
            for (Point p : points) {
                if (p.equals(widget.getControlPoint()))
                    widget.setControlPoint(p);
                else if (p.equals(widget.getPoint()))
                    widget.setPoint(p);
            }
        }
    }
    
    public static void hideCompartmentInNodeName(RenderablePathway diagram) {
        List<Renderable> components = diagram.getComponents();
        if (components == null || components.size() == 0)
            return;
        hideCompartmentInNodeName(components);
    }

    public static void hideCompartmentInNodeName(List<Renderable> components) {
        for (Renderable r : components) {
            if (r instanceof Node) {
                Node node = (Node) r;
                hideCompartmentInNodeName(node);
            }
        }
    }
    
    public static void hideCompartmentInNodeName(Node node) {
        // Only for node
        String oldName = node.getDisplayName();
        if (oldName != null && oldName.endsWith("]")) {
            int index = oldName.indexOf("[");
            if (index < 0)
                return ; // No compartment
            String newName = oldName.substring(0, index).trim();
            node.setDisplayName(newName);
        }
    }

    public static void drawName(Node node, Graphics2D g2) {
        // Nothing to draw
        if (node.getTextBounds() == null ||
            node.getTextLayouts() == null)
            return;
        // Draw the text: Draw the text at the center of the bounding rectangle
        if (node.getSelectionPosition() == SelectionPosition.TEXT) {
            g2.setPaint(DefaultRenderConstants.SELECTION_WIDGET_COLOR);
        }
        else if (node.getForegroundColor() == null) {
            if (node.getIsForDrug())
                g2.setPaint(DefaultRenderConstants.DEFAULT_DRUG_FOREGROUND);
            else
                g2.setPaint(DefaultRenderConstants.DEFAULT_FOREGROUND);
        }
        else
            g2.setPaint(node.getForegroundColor());
        Rectangle bounds = node.getTextBounds();
        DrawUtilities.drawString(node.getTextLayouts(), 
                                 bounds, 
                                 node.boundsBuffer, 
                                 g2);
    }
    
    public static Dimension getDimension(Renderable pathway) {
        if (pathway.getComponents() == null) {
            return new Dimension(10, 10); // The minimum
        }
        Renderable renderable = null;
        Rectangle bounds = null;
        int maxX, maxY;
        Point p;
        Dimension size = new Dimension();
        List list = pathway.getComponents();
        for (Iterator it = list.iterator(); it.hasNext();) {
            renderable = (Renderable) it.next();
            if (!renderable.isVisible())
                continue; // Don't need to consider a hidden object
            bounds = renderable.getBounds();
            if (bounds != null) {
                maxX = bounds.x + bounds.width;
                if (maxX > size.width)
                    size.width = maxX;
                maxY = bounds.y + bounds.height;
                if (maxY > size.height)
                    size.height = maxY;
            }
            else if (renderable.getPosition() != null) {
                p = renderable.getPosition();
                if (p.x > size.width)
                    size.width = p.x;
                if (p.y > size.height)
                    size.height = p.y;
            }
        }
        // Give it an extra space so that all Renderables can be displayed correctly
        size.width += 8;
        size.height += 8;
        return size;
    }
    
    public static void copyRenderInfo(Renderable source, Renderable target) {
        Point sourceP = source.getPosition();
        target.setPosition(new Point(sourceP));
        target.setBounds(new Rectangle(source.getBounds()));
        Rectangle textBounds = source.getTextBounds();
        if (textBounds != null) // Some nodes may don't have text bounds
            target.setTextPosition(textBounds.x, textBounds.y);
        target.setForegroundColor(source.getForegroundColor());
        target.setBackgroundColor(source.getBackgroundColor());
        target.setIsSelected(source.isSelected());
    }
	
	public static void switchRenderInfo(Renderable source, Renderable target) {
		copyRenderInfo(source, target);
		target.setBackgroundColor(Color.GREEN);
	    // Have to move all connecting info from source to target
        if (source instanceof Node && target instanceof Node) {
            ConnectInfo targetInfo = target.getConnectInfo();
            if (targetInfo != null)
                targetInfo.clear();
            ConnectInfo sourceInfo = source.getConnectInfo();
            if (sourceInfo == null)
                return;
            java.util.List connectWidgets = sourceInfo.getConnectWidgets();
            if (connectWidgets != null && connectWidgets.size() > 0) {
                ConnectWidget widget = null;
                if (targetInfo == null) {
                    targetInfo = new NodeConnectInfo();
                    target.setConnectInfo(targetInfo);
                }
                // The list of connectWidgets is changed after disconnect.
                List tmpList = new ArrayList(connectWidgets);
                for (Iterator it = tmpList.iterator(); it.hasNext();) {
                    widget = (ConnectWidget) it.next();
                    widget.disconnect();
                    widget.setConnectedNode((Node)target);
                    widget.connect();
                    // Should be called already
//                    widget.invalidate();
                    // Should be called in widget.connect()
//                    targetInfo.addConnectWidget(widget);
                }
            }
            Node sourceNode = (Node) source;
            Node targetNode = (Node) target;
            targetNode.setNodeAttachmentsLocally(sourceNode.getNodeAttachments());
        }
	}
	
    public static void setInteractionName(RenderableInteraction fl) {
        Renderable input = fl.getInputNode(0);
        String name1 = (input == null) ? "" : input.getDisplayName();
        Renderable output = fl.getOutputNode(0);
        String name2 = (output == null) ? "" : output.getDisplayName();
        InteractionType type = fl.getInteractionType();
        if (type != null)
            fl.setDisplayName(name1 + " " + type.getTypeName() + "s " + name2);
        else
            fl.setDisplayName(name1 + " precedes " + name2);
    }
	
	/**
	 * Get a named Renderable from the hierarchical tree that contains the specified renderable.
	 * 
	 * @param container A Renderable object that is contained in the hierarchical tree that
	 * the search is performed against
	 * @param name The name of the searching Renderable
	 * @return The Renderable whose displayName is the same as name or null if none found
	 */
	public static Renderable getComponentByName(Renderable container, String name) {
		Renderable topMost = getTopMostContainer(container);
		if (topMost.getDisplayName().equals(name))
			return topMost;
		java.util.List list0 = topMost.getComponents();
		java.util.List list = new ArrayList();
		if (list0 != null) {
			list.addAll(list0);
			while (list.size() > 0) {
				Renderable tmp = (Renderable) list.get(0);
				if (tmp.getDisplayName() != null &&
					tmp.getDisplayName().equals(name)) {
					return tmp;
				}
				if (tmp.getComponents() != null && !(tmp instanceof RenderableReaction))
					list.addAll(tmp.getComponents());
				list.remove(0);
			}
		}
		return null;
	}
	
	public static Renderable getTopMostContainer(Renderable container) {
		Renderable current = container;
		Renderable next = container.getContainer();
		while (next != null) {
			current = next;
			next = current.getContainer();
		}
		return current;
	}
	
	public static java.util.List getAllEvents(Renderable r) {
		Renderable top = getTopMostContainer(r);
		java.util.List events = new ArrayList();
		getAllEvents(top, events);
		return events;
	}
	
	private static void getAllEvents(Renderable renderable, java.util.List events) {
		if (renderable instanceof RenderablePathway) {
			events.add(renderable);
			if (renderable.getComponents() != null) {
				for (Iterator it = renderable.getComponents().iterator(); it.hasNext();) {
					Renderable r = (Renderable) it.next();
					if (r instanceof Shortcut)
						continue;
					getAllEvents(r, events);
				}
			}
		}
		else if (renderable instanceof ReactionNode)
			events.add(renderable);
	}

	
	/**
	 * Check if a specified property can be set for a Renderable. Some properties
	 * should be checked before setting. For example, if a pathway has Homo sapiens as taxon
	 * property, all descendents of this pathway should have Homo sapiens as its taxon property.
	 * All other value setting will give rise to a false return.
	 * 
	 * @param renderable Renderable object to check for the specified property name and value
	 * @param propName The property name
	 * @param propValue The value of the property
	 * @return true for the case the propValue can be set, while false for not
	 */
	public static boolean checkProperty(Renderable renderable, String propName, Object propValue) {
		Renderable container = renderable.getContainer();
		while (container != null) {
			Object value = container.getAttributeValue(propName);
			if (value != null && !value.equals(propValue))
				return false;
			container = container.getContainer();
		}
		return true;
	}
	
	/**
	 * Set the property value for a container's descendent.
	 * 
	 * @param renderable The container
	 * @param propName The name of the property
	 * @param propValue The value of the property
	 */
	public static void setPropertyForDescendents(Renderable renderable, String propName, Object propValue) {
		if (renderable instanceof RenderableReaction)
			return; // Should block for this type of objects.
		java.util.List list = new ArrayList();
		if (renderable.getComponents() != null)
			list.addAll(renderable.getComponents());
		java.util.List list1 = new ArrayList();
		while (list.size() > 0) {
			for (Iterator it = list.iterator(); it.hasNext();) {
				Renderable tmp = (Renderable) it.next();
				tmp.setAttributeValue(propName, propValue);
				if (tmp instanceof RenderableReaction) {
					it.remove();
					continue;
				}
				if (tmp.getComponents() != null)
					list1.addAll(tmp.getComponents());
				it.remove();
			}	
			list.addAll(list1);
			list1.clear();
		}
	}
	
	/**
	 * A method to put all descendants of a Renderable into a list. Shortcuts are not
     * in the returned list.
     * 
	 * @param container Renderable object for which to get descendants
	 * @return List of Renderable objects that are the descendants.
	 */
	public static java.util.List getAllDescendents(Renderable container) {
		Set descendants = new HashSet();
        if (container.getComponents() != null && container.getComponents().size() > 0)
			getAllDescendents(container, descendants);
		return new ArrayList(descendants);
	}
	
	private static void getAllDescendents(Renderable container, 
	                                      Set descendants) {
		for (Iterator it = container.getComponents().iterator(); it.hasNext();) {
			Renderable renderable = (Renderable) it.next();
            if (renderable instanceof Shortcut)
                continue;
			descendants.add(renderable);
			if (renderable.getComponents() == null ||
                renderable.getComponents().size() == 0)
			    continue;
			getAllDescendents(renderable, descendants);
		}
	}
	
	/**
	 * Generate a shortcut to a RenderableComplex. All contained components will
	 * be generated recursively.
	 * 
	 * @param complex RenderableComplex object for which to generate a shortcut
	 * @return Shortcut (RenderableComplex object) to the complex passed
	 */
	public static RenderableComplex generateComplexShortcut(RenderableComplex complex) {
	    RenderableComplex shortcut = (RenderableComplex) complex.generateShortcut();
	    generateShortcutRecursively(shortcut, 
	                                complex);
	    return shortcut;
	}
	
    private static void generateShortcutRecursively(Renderable shortcut, 
                                                    Renderable complex) {
        List list = complex.getComponents();
        if (list.size() == 0)
            return;
        RenderableComplex shortcutComplex = (RenderableComplex) shortcut;
        for (Iterator it = list.iterator(); it.hasNext();) {
            Renderable r = (Renderable) it.next();
            Renderable rShortcut = (Renderable) r.generateShortcut();
            shortcutComplex.addComponent(rShortcut);
            rShortcut.setContainer(shortcutComplex);
            if (r.getComponents() == null || r.getComponents().size() == 0)
                continue;
            // Recursively generate shortcuts
            if (r instanceof RenderableComplex)
                generateShortcutRecursively(rShortcut, 
                                            r);
        }
    }
	
	/**
	 * Get all contained Renderable objects including shortcuts.
	 * 
	 * @param container Renderable object for which to get contained objects
	 * @return Set of Renderable objects contained in the passed Renderable object
	 */
	public static Set<Renderable> getAllContainedComponents(Renderable container) {
	    Set<Renderable> all = new HashSet<Renderable>();
	    getAllContainedComponents(container, all);
	    return all;
	}
	
	private static void getAllContainedComponents(Renderable container, 
	                                              Set<Renderable> all) {
	    List components = container.getComponents();
	    if (components == null || components.size() == 0)
	        return;
	    for (Iterator it = components.iterator(); it.hasNext();) {
	        Renderable r = (Renderable) it.next();
	        all.add(r);
	        getAllContainedComponents(r, all);
	    }
	}
	
	/**
	 * This method is used to get a list of complex components in a hierarchy. This 
	 * basically uses an out-search for the tree: the index of a contained component
	 * should be smaller than the index of its container component.
	 * 
	 * @param complex ContainerNode object for which to get components recursively
	 * @return List of components (Renderable objects) in the ContainerNode passed
	 */
	public static List<Renderable> getComponentsInHierarchy(ContainerNode complex) {
	    List<Renderable> allInHierachy = new ArrayList<Renderable>();
	    List<Renderable> components = complex.getComponents();
	    getComponentsInHierarchy(components, allInHierachy);
	    return allInHierachy;
	}
	
	private static void getComponentsInHierarchy(List<Renderable> components,
	                                             List<Renderable> allInHierachy) {
	    if (components == null || components.size() == 0)
	        return;
	    for (Renderable r : components) {
	        List<Renderable> children = r.getComponents();
	        getComponentsInHierarchy(children, 
	                                 allInHierachy);
	        allInHierachy.add(r);
	    }
	}
	
	/**
	 * Register and assign unique IDs to a list of Renderable objects and their descendents.
	 * 
	 * @param nodes List of Renderable objects to register
	 * @param process The topmost Renderable object.
	 */
	public static void registerNodes(java.util.List nodes, Renderable process) {
		RenderableRegistry registry = RenderableRegistry.getRegistry();
		// Assign unique DB_ID
		java.util.List current = new ArrayList(nodes);
		java.util.List next = new ArrayList();
		while (current.size() > 0) {
			for (Iterator it = current.iterator(); it.hasNext();) {
				Renderable r = (Renderable)it.next();
				if (r.getID() < 0) // No ID assigned
					r.setID(process.generateUniqueID());
				if (r instanceof Shortcut || r instanceof RenderableReaction)
					continue;
				registry.add(r); // Registry
				if (r.getComponents() != null && r.getComponents().size() > 0)
					next.addAll(r.getComponents());
			}
			current.clear();
			current.addAll(next);
			next.clear();
		}
	}

	/**
	 * Convert a GKInstance object to a Renderable object. The contained instances will be handled recursively.
	 * 
	 * @param instance GKInstance to covert
	 * @param needProp true if the properties in GKInstance should be transferred to Renderable object. False for no
	 * transferring. However, the specified instance will be used as model instance for the converted renderable object.
	 * @return Renderable object created from converting the GKInstance passed
	 * @throws Exception Thrown if unable to retrieve attribute values from the instance
	 */
	public static Renderable convertToNode(GKInstance instance, boolean needProp) throws Exception {
		return InstanceToRenderableConverter.convertToNode(instance, needProp);
	}
	
	/**
	 * Copy properties from source object to target object.
	 * 
	 * @param source Renderable object from which to obtain properties
	 * @param target Renderable object to which to copy properties
	 */
	public static void copyProperties(Renderable source, Renderable target) {
        target.setDisplayName(source.getDisplayName());
		target.setAttributeValue("names", source.getAttributeValue("names"));
		target.setAttributeValue("taxon", source.getAttributeValue("taxon"));
		target.setAttributeValue("DB_ID", source.getAttributeValue("DB_ID"));
		target.setAttributeValue("created", source.getAttributeValue("created"));
		target.setAttributeValue("modified", source.getAttributeValue("modified"));
        target.setAttributeValue("localization", source.getAttributeValue("localization"));
		if (target instanceof RenderableEntity) {
			target.setAttributeValue("databaseIdentifier", source.getAttributeValue("databaseIdentifier"));
			target.setAttributeValue("modifications", source.getAttributeValue("modifications"));
		}
		else {
			target.setAttributeValue("summation", source.getAttributeValue("summation"));
			target.setAttributeValue("references", source.getAttributeValue("references"));
		}
	}
	
	/**
	 * Sort a list of Renderable objects based on displayNames.
	 * 
	 * @param renderables List of Renderable objects to sort
	 */
	public static void sort(java.util.List renderables) {
		Comparator nameSorter = new Comparator() {
			public int compare(Object obj1, Object obj2) {
				Renderable r1 = (Renderable) obj1;
                String name1 = r1.getDisplayName();
                if (name1 == null)
                    name1 = "";
				Renderable r2 = (Renderable) obj2;
                String name2 = r2.getDisplayName();
                if (name2 == null)
                    name2 = "";
				return name1.compareTo(name2);
			}
		};
		Collections.sort(renderables, nameSorter);
	}
	
	/**
	 * Search for a Renderable object from a specified list matched by the specified display name.
	 * 
	 * @param renderables List of Renderable objects to search
	 * @param name Name of the Renderable object to find
	 * @return Renderable object found or null if none found
	 */
	public static Renderable searchNode(java.util.List renderables, String name) {
		if (renderables == null || renderables.size() == 0)
			return null;
		Renderable r = null;
		for (Iterator it = renderables.iterator(); it.hasNext();) {
			r = (Renderable) it.next();
			if (r.getDisplayName().equals(name))
				return r;
		}
		return null;
	}
	
	/**
	 * Rename a Renderable object.
	 * 
	 * @param renderable Renderable object to rename
	 * @param newDisplayName New name to set for the Renderable object
	 */
	public static void rename(Renderable renderable, String newDisplayName) {
		String oldName = renderable.getDisplayName();
		renderable.setDisplayName(newDisplayName);
		RenderableRegistry.getRegistry().changeName(renderable, oldName);
		// Need to update shortcuts' display if renderable is a Node
		if (renderable instanceof Node) {
			java.util.List shortcuts = renderable.getShortcuts();
			if (shortcuts != null) {
				for (Iterator it = shortcuts.iterator(); it.hasNext();) {
					Node node = (Node) it.next();
					node.invalidateBounds();
				}
			}
		}
	}
	
	public static void center(Renderable renderable, Dimension size) {
		if (renderable.getComponents() != null) {
			Rectangle rect = new Rectangle();
			Rectangle bounds = null;
			Renderable r = null;
			for (Iterator it = renderable.getComponents().iterator(); it.hasNext();) {
				r = (Renderable) it.next();
				bounds = r.getBounds();
				if (bounds == null)
					continue;
				if (rect.isEmpty()) { // Make a copy
					rect.x = bounds.x;
					rect.y = bounds.y;
					rect.width = bounds.width;
					rect.height = bounds.height;
				}
				else {
					rect = rect.union(bounds);
				}
			}
			int deltaX = 0;
			int deltaY = 0;
			if (size.width > rect.width) {
				deltaX = (size.width - rect.width) / 2;
				if (deltaX > rect.x)
					deltaX -= rect.x;
			}
			if (size.height > rect.height) {
				deltaY = (size.height - rect.height) / 2;
				if (deltaY > rect.y)
					deltaY -= rect.y;
			}
			// Move it
			if (deltaX > 0 || deltaY > 0) {
				for (Iterator it = renderable.getComponents().iterator(); it.hasNext();) {
					r = (Renderable) it.next();
					r.move(deltaX, deltaY);
				}
			}
		}
	}
    
    /**
     * Use this method to check whether a circular reference will be created if the contained is
     * inserted into the container.
     * 
     * @param container The Renderable object containing contained
     * @param contained The Renderable object is contained by container
     * @return the offended Renderable name will be returned if a circular reference is found.
     * Otherwise, null will be returned
     */
    public static String searchCircularRef(Renderable container, Renderable contained) {
        Renderable r = container;
        String displayName = contained.getDisplayName();
        while (r != null) {
            if (container.getDisplayName().equals(displayName)) {
                return displayName;
            }
            r = r.getContainer();
        }
        // Need to check contained
        if (contained.getComponents() != null) {
            for (Iterator it = contained.getComponents().iterator(); it.hasNext();) {
                Renderable tmp = (Renderable) it.next();
                if (tmp instanceof FlowLine ||
                    tmp instanceof RenderableEntity ||
                    tmp instanceof ReactionNode ||
                    tmp instanceof RenderableReaction)
                    continue;
                String rtn = searchCircularRef(container, tmp);
                if (rtn != null)
                    return rtn;
            }
        }
        return null;
    }
    
    public static Node getShortcutTarget(Node node) {
        Node target = node;
        while (target instanceof Shortcut) {
            target = (Node) ((Shortcut)target).getTarget();
        }
        return target;
    }
}
