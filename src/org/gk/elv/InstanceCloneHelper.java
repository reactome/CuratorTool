/*
 * Created on Dec 4, 2008
 *
 */
package org.gk.elv;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.gk.database.AttributeEditConfig;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.schema.SchemaClass;

/**
 * This helper class is used to clone an Instance.
 * @author wgm
 *
 */
@SuppressWarnings("unchecked")
public class InstanceCloneHelper {
    
    public InstanceCloneHelper() {
    }
    
    public List<GKInstance> cloneInstances(List<GKInstance> selection,
                                           Component parentComp) {
        List<GKInstance> newInstances = new ArrayList<GKInstance>();
        if (selection != null && selection.size() > 0) {
            // To be selected
            XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
            Map<GKInstance, GKInstance> oldToNew = new HashMap<>();
            for (GKInstance instance : selection) {
                if (instance.isShell())
                    continue; // Should not occur since Clone action should be disabled
                GKInstance copy = oldToNew.get(instance);
                if (copy != null)
                    continue; // This may be cloned already
                copy = cloneInstance(instance, fileAdaptor);
                oldToNew.put(instance, copy);
                // Check if components in a Complex should be copied recursively
                if (instance.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                    int reply = JOptionPane.showConfirmDialog(parentComp,
                                                              "Do you want to clone the contained subunits recursively?",
                                                              "Complex Clone",
                                                              JOptionPane.YES_NO_OPTION);
                    if (reply == JOptionPane.YES_OPTION) {
                        cloneComplexRecursively(copy, 
                                             oldToNew,
                                             fileAdaptor,
                                             parentComp);
                    }
                }
                else if (instance.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    int reply = JOptionPane.showConfirmDialog(parentComp,
                                                              "Do you want to clone the contained events recursively?",
                                                              "Pathway Clone",
                                                              JOptionPane.YES_NO_OPTION);
                    if (reply == JOptionPane.YES_OPTION) {
                        clonePathwayRecursively(copy, 
                                                oldToNew,
                                                fileAdaptor,
                                                parentComp);
                    }
                }
            }
            newInstances.addAll(oldToNew.values());
        }
        return newInstances;
    }
    
    private Set<String> getAttributeNamesNotClonable() {
        Set<String> rtn = new HashSet<>();
        List<String> uneditableAttNames = AttributeEditConfig.getConfig().getUneditableAttNames();
        if (uneditableAttNames != null)
            rtn.addAll(uneditableAttNames);
        // Something else too
        rtn.add(ReactomeJavaConstants.authored);
        rtn.add(ReactomeJavaConstants.edited);
        rtn.add(ReactomeJavaConstants.reviewed);
        rtn.add(ReactomeJavaConstants.revised);
        rtn.add(ReactomeJavaConstants._doRelease);
        rtn.add(ReactomeJavaConstants.releaseStatus);
        rtn.add(ReactomeJavaConstants.releaseDate);
        return rtn;
    }
    
    public GKInstance cloneInstance(GKInstance instance, 
                                    XMLFileAdaptor fileAdaptor) {
        if (instance.isShell())
            throw new IllegalArgumentException("CuratorActionCollection.cloneInstance(): " +
                    "A shell instance cannot be cloned.");
        GKInstance copy = (GKInstance) instance.clone();
        // Have to check un-editable attributes.
        // It seems that this is not an elegant way?
        Set<String> notClonableSlots = getAttributeNamesNotClonable();
        if (notClonableSlots != null && notClonableSlots.size() > 0) {
            SchemaClass cls = copy.getSchemClass();
            for (String attName : notClonableSlots) {
                if (cls.isValidAttribute(attName))
                    copy.setAttributeValueNoCheck(attName, null);
            }
            // It is possible the display name is gone. Use "Copy of " for _displayName.
            copy.setDisplayName("Clone of " + instance.getDisplayName());
        }
        // Have to set a new DB_ID explicitly
        copy.setDBID(fileAdaptor.getNextLocalID());
        fileAdaptor.addNewInstance(copy);
        return copy;
    }
    
    private void cloneInstancesRecursively(GKInstance container, 
                                           String attName,
                                           Map<GKInstance, GKInstance> oldToNew,
                                           XMLFileAdaptor fileAdaptor,
                                           Component parentComp) {
        if (!container.getSchemClass().isValidAttribute(attName))
            return; // There is no need to do anything
        // Need to check if a shell instance is included in the hasComponent hierarchy.
        // If true, deep cloning cannot work
        List<GKInstance> values = null;
        try {
            values = container.getAttributeValuesList(attName);
        }
        catch(Exception e) {
            System.err.println("CuratorActionCollection.cloneInstancesRecursively(): " + e);
            e.printStackTrace();
        }
        if (values == null || values.size() == 0)
            return ; // Nothing to do. Just return
        Set<GKInstance> current = new HashSet<>();
        Set<GKInstance> next = new HashSet<>();
        current.addAll(values);
        while (current.size() > 0) {
            try {
                for (GKInstance tmp : current) {
                    if (tmp.isShell()) {
                        JOptionPane.showMessageDialog(parentComp,
                                                      "A shell instance is contained in the hierarchy.\n"
                                                     + "A recursize cloning cannot be applied for " + container.getDisplayName() + ".",
                                                      "Error in Cloning",
                                                      JOptionPane.ERROR_MESSAGE);
                        return ; // Return an empty Array to make the caller happy
                    }
                    if (tmp.getSchemClass().isValidAttribute(attName)) {
                        List<GKInstance> list = tmp.getAttributeValuesList(attName);
                        if (list != null && list.size() > 0)
                            next.addAll(list);
                    }
                }
            }  
            catch (Exception e) {
                System.err.println("CuratorActionCollection.cloneInstancesRecursively(): " + e);
                e.printStackTrace();
            }
            current.clear();
            current.addAll(next);
            next.clear();
        }
        // Map: from old GKInstance to cloned GKInstance
        // Use this map to avoid clone the same Subunit more than once 
        // since a subunit can be contained more than multiple times in
        // one Complex or its contained complex
        cloneInstancesRecursively(container, attName, oldToNew, fileAdaptor);
    }
    
    private void cloneInstancesRecursively(GKInstance container,
                                           String attName,
                                           Map<GKInstance, GKInstance> oldToNew,
                                           XMLFileAdaptor fileAdaptor) {
        if (!container.getSchemClass().isValidAttribute(attName))
            return;
        List<GKInstance> values = null;
        try {
            values = container.getAttributeValuesList(attName);
        }
        catch(Exception e) {
            System.err.println("CuratorActionCollection.cloneInstancesRecursively(): " + e);
            e.printStackTrace();
        }
        if (values == null || values.size() == 0)
            return;
        List<GKInstance> newValues = new ArrayList<>(values.size());
        for (GKInstance value : values) {
            GKInstance clone = (GKInstance) oldToNew.get(value);
            if (clone == null) {
                clone = cloneInstance(value, fileAdaptor);
                oldToNew.put(value, clone);
                if (value.getSchemClass().isValidAttribute(attName))
                    cloneInstancesRecursively(clone, attName, oldToNew, fileAdaptor);
            }
            newValues.add(clone);
        }
        try {
            container.setAttributeValue(attName, newValues);
        }
        catch(Exception e) {
            System.err.println("CuratorActionCollection.cloneInstancesRecursively(): " + e);
            e.printStackTrace();
        }
    }
    
    private void cloneComplexRecursively(GKInstance complex, 
                                         Map<GKInstance, GKInstance> oldToNew,
                                         XMLFileAdaptor fileAdaptor,
                                         Component parentComp) {
        cloneInstancesRecursively(complex,
                                  ReactomeJavaConstants.hasComponent,
                                  oldToNew,
                                  fileAdaptor,
                                  parentComp);
    }
    
    private void clonePathwayRecursively(GKInstance pathway, 
                                         Map<GKInstance, GKInstance> oldToNew,
                                         XMLFileAdaptor fileAdaptor,
                                         Component parentComp) {
        cloneInstancesRecursively(pathway,
                                  ReactomeJavaConstants.hasEvent,
                                  oldToNew,
                                  fileAdaptor,
                                  parentComp);
    }
    
}
