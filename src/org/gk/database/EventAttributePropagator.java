/*
 * Created on Mar 7, 2005
 *
 */
package org.gk.database;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.UIManager;

import org.apache.commons.lang.ArrayUtils;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaAttribute;

/**
 * This class is used to help propagate the attribute assignment from one GKInstance
 * to other GKInstance objects.
 * 
 * @author Fred Loney {@literal <loneyf@ohsu.edu>}
 */
public class EventAttributePropagator {
	/** The attributes which reference children contained by the parent. */
    public static final String[] CHILD_ATT_NAMES = {
            ReactomeJavaConstants.hasEvent,
            ReactomeJavaConstants.hasMember,
            ReactomeJavaConstants.hasSpecialisedForm
    };

    /** Release flag value change propagates to children and inferredFrom targets. */
    public static final String[] DO_RELEASE_PROPAGATION_ATT_NAMES =
            (String[]) ArrayUtils.add(CHILD_ATT_NAMES, ReactomeJavaConstants.inferredFrom);
    
    /** The release flag value attributes. */
    public static final List<String> RELEASE_CHANGE_ATT_NAMES = Arrays.asList(
            ReactomeJavaConstants._doRelease,
            ReactomeJavaConstants._doNotRelease
    );
    
    private Set<String> attributeHasTarget = new HashSet<String>(4);

    private GKInstance root;

    private String valueAttName;

    public EventAttributePropagator(GKInstance event, String attName) {
        this.root = event;
        this.valueAttName = attName;
    }
    
    public boolean changedAttributeTarget(String attName) {
        return attributeHasTarget.contains(attName);
    }
    
    /**
     * Returns whether the given event has at least one direct propagation target.
     * 
     * @return whether there is a target event
     * @throws Exception
     */
    public boolean willProgagate() throws Exception {
        // The role attributes to follow recursively
        String[] roleAtts = getPropagationRoleAttributes(valueAttName);
        for (String role: roleAtts) {
            @SuppressWarnings("unchecked")
            List<GKInstance> values = (List<GKInstance>)root.getAttributeValuesList(role);
            if (!values.isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    public void propagate() throws Exception {
        // The propagation target events.
        Set<GKInstance> descendents = grepAllProgagationInstances(root);
        if (descendents.isEmpty()) {
            return;
        }

        GKInstance tmpEvent = null;
        SchemaAttribute att = root.getSchemClass().getAttribute(valueAttName);
        if (att.isMultiple()) {
            List attValues = root.getAttributeValuesList(valueAttName);
            List oldValues = null;
            for (Iterator it = descendents.iterator(); it.hasNext();) {
                tmpEvent = (GKInstance) it.next();
                att = tmpEvent.getSchemClass().getAttribute(valueAttName);
                oldValues = tmpEvent.getAttributeValuesList(valueAttName);
                if (!InstanceUtilities.compareAttValues(attValues, oldValues, att)) {
                    tmpEvent.setAttributeValueNoCheck(att, attValues);
                    fireAttributeEdit(tmpEvent, valueAttName);
                }
            }
        }
        else {
            Object attValue = root.getAttributeValue(valueAttName);
            Object oldValue = null;
            for (Iterator it = descendents.iterator(); it.hasNext();) {
                tmpEvent = (GKInstance) it.next();
                oldValue = tmpEvent.getAttributeValue(valueAttName);
                if (attValue != oldValue) {
                    tmpEvent.setAttributeValue(valueAttName, attValue);
                    fireAttributeEdit(tmpEvent, valueAttName);
                }
            }
        }
    }
    
    /**
     * Collects the transitive closure of attribute change propagation targets
     * recursively referenced by the specified event.
     * 
     * @param event
     * @param valueAttName the attribute whose value is to be propagated.
     * @return a set of event GKInstances.
     * @throws Exception
     */
    private Set<GKInstance> grepAllProgagationInstances(GKInstance event) throws Exception {
        Set<GKInstance> rtn = new HashSet<GKInstance>();
        // The role attributes to follow recursively
        String[] roleAtts = getPropagationRoleAttributes(valueAttName);
        // Propagate this settings to all contained events
        Set<GKInstance> current = grepProgagationInstances(event, roleAtts);
        Set<GKInstance> next = new HashSet<GKInstance>();
        GKInstance tmpEvent = null;
        while (current.size() > 0) {
            for (Iterator<GKInstance> it = current.iterator(); it.hasNext();) {
                tmpEvent = (GKInstance) it.next();
                // Ignore an event if it is already visited.
                if (!rtn.contains(tmpEvent)) {
                    rtn.add(tmpEvent);
                    next.addAll(grepProgagationInstances(tmpEvent, roleAtts));
                }
            }
            current.clear();
            current.addAll(next);
            next.clear();
        }
        
        return rtn;
    }
   
    /**
     * 
     * @param attName the attribute whose value is to be propagated.
     * @return the role attributes to follow in the propagation.
     */
    private String[] getPropagationRoleAttributes(String attName) {
        if (RELEASE_CHANGE_ATT_NAMES.contains(attName)) {
            return DO_RELEASE_PROPAGATION_ATT_NAMES;
        } else {
            return CHILD_ATT_NAMES;
        }
    }

    /**
     * Grep the event instances directly referenced by the given role
     * attribute names.
     * 
     * @param event the starting event.
     * @param attNames the role attribute names.
     * @return a set of event GKInstances.
     * @throws Exception
     */
    private Set<GKInstance> grepProgagationInstances(GKInstance event, String[] attNames) throws Exception {
        Set<GKInstance> references = new HashSet<GKInstance>();
        for (String attName: attNames) {
            if (event.getSchemClass().isValidAttribute(attName)) {
                @SuppressWarnings("unchecked")
                List<GKInstance> values = (List<GKInstance>)event.getAttributeValuesList(attName);
                if (values != null && !values.isEmpty()) {
                    references.addAll(values);
                    this.attributeHasTarget.add(attName);
                }
            }
        }
        return references;
    }
    
    private void fireAttributeEdit(GKInstance instance, String attName) {
        AttributeEditManager.getManager().attributeEdit(instance, attName);
    }
}
