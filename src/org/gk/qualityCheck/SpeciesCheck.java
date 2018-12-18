/*
 * Created on Aug 17, 2009
 *
 */
package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * This class is used to check species setting. Subclasses supply the
 * class and attributes to check. Instances of the class are reported
 * if the instance species does not exactly equal the species of
 * instances referenced by the attribute. Both the <code>species</code>
 * and <code>relatedSpecies</code> values are used. 
 * 
 * @author wgm
 */
@SuppressWarnings("unchecked")
public abstract class SpeciesCheck extends SingleAttributeClassBasedCheck {
    
    public SpeciesCheck() {
        checkAttribute = "species";
    }
   
    @Override
    protected String getIssueTitle() {
        return "ExtraSpecies";
    }
    
    @Override
    protected String getIssue(GKInstance container) throws Exception {
        Set<GKInstance> contained = getAllContainedEntities(container);
        Set<GKInstance> containedSpecies = new HashSet<>();
        for (GKInstance inst : contained) {
            if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.species))
                containedSpecies.addAll(inst.getAttributeValuesList(ReactomeJavaConstants.species));
            if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies))
                containedSpecies.addAll(inst.getAttributeValuesList(ReactomeJavaConstants.relatedSpecies));
        }
        Set<GKInstance> containerSpecies = new HashSet<>();
        containerSpecies.addAll(container.getAttributeValuesList(ReactomeJavaConstants.species));
        if (container.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies))
            containerSpecies.addAll(container.getAttributeValuesList(ReactomeJavaConstants.relatedSpecies));
        // Keep not shared species
        Set<GKInstance> shared = new HashSet<>(containedSpecies);
        shared.retainAll(containerSpecies);
        containedSpecies.removeAll(shared);
        containerSpecies.removeAll(shared);
        StringBuilder builder = new StringBuilder();
        if (containerSpecies.size() > 0) {
            builder.append("Container:");
            containerSpecies.forEach(s -> builder.append(s.getDisplayName()).append(","));
            builder.deleteCharAt(builder.length() - 1);
        }
        if (containedSpecies.size() > 0) {
            if (builder.length() > 0)
                builder.append("; ");
            builder.append("Contained:");
            containedSpecies.forEach(s -> builder.append(s.getDisplayName()).append(","));
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    protected boolean checkSpecies(GKInstance container) throws Exception {
        Set<GKInstance> contained = getAllContainedEntities(container);
        // Skip checking for shell instances
        if (containShellInstances(contained))
            return true;
        // Nothing to check if contained is empty: probably 
        // the instance is just starting to annotation or
        // container is used as a place holder
        if (contained.size() == 0)
            return true;
        // Check only for valid instances
        Set<GKInstance> tmp = new HashSet<GKInstance>(contained);
        contained.clear();
        for (GKInstance in : tmp) {
            if (in.getSchemClass().isValidAttribute(ReactomeJavaConstants.species) ||
                in.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies))
                contained.add(in);
        }
        if (contained.size() == 0)
            return true;
        // Get the species setting: species should be a single value attribute
        // Species are multiple for EntitySet and Complex.
        Set<GKInstance> containedSpecies = new HashSet<GKInstance>();
        for (GKInstance comp : contained) {
            // All should be valid: no need to check if it is valid attribute for species
            if (comp.getSchemClass().isValidAttribute(ReactomeJavaConstants.species))
                containedSpecies.addAll(comp.getAttributeValuesList(ReactomeJavaConstants.species));
            if (comp.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies))
                containedSpecies.addAll(comp.getAttributeValuesList(ReactomeJavaConstants.relatedSpecies));
        }
        // As with compartment, need to compare the container species setting and contained species setting
        Set<GKInstance> containerSpecies = new HashSet<>();
        containerSpecies.addAll(container.getAttributeValuesList(ReactomeJavaConstants.species));
        if (container.getSchemClass().isValidAttribute(ReactomeJavaConstants.relatedSpecies))
            containerSpecies.addAll(container.getAttributeValuesList(ReactomeJavaConstants.relatedSpecies));
        return compareSpecies(new ArrayList<>(containerSpecies),
                              new ArrayList<>(containedSpecies));
    }
    
    private boolean compareSpecies(List<GKInstance> containerValues,
                                   List<GKInstance> containedValues) {
        InstanceUtilities.sortInstances(containedValues);
        InstanceUtilities.sortInstances(containerValues);
        return containerValues.equals(containedValues);
    }
    
    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        return checkSpecies(instance);
    }
    
    protected void loadSpeciesAttributeVAlues(Collection<GKInstance> instances,
                                              MySQLAdaptor dba) throws Exception {
        // Have to check all kinds of PEs. Otherwise, null will be assigned
        // to the species attribute because of a bug in MySQLAdaptor.
        loadAttributes(instances,
                       ReactomeJavaConstants.GenomeEncodedEntity, 
                       ReactomeJavaConstants.species, 
                       dba);
        loadAttributes(instances,
                       ReactomeJavaConstants.Complex,
                       ReactomeJavaConstants.species,
                       dba);
        loadAttributes(instances,
                       ReactomeJavaConstants.EntitySet,
                       ReactomeJavaConstants.species,
                       dba);
        loadAttributes(instances, 
                       ReactomeJavaConstants.Polymer,
                       ReactomeJavaConstants.species,
                       dba);
        loadAttributes(instances,
                       ReactomeJavaConstants.SimpleEntity,
                       ReactomeJavaConstants.species,
                       dba);
    }

}
