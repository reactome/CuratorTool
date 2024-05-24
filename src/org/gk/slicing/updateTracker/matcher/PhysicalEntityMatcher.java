package org.gk.slicing.updateTracker.matcher;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 4/13/2022
 */
public class PhysicalEntityMatcher extends InstanceMatcher {

    public PhysicalEntityMatcher(MySQLAdaptor previousDBA, MySQLAdaptor currentDBA, MySQLAdaptor targetDBA)
        throws Exception {

        super(previousDBA, currentDBA, targetDBA);
    }

    @Override
    protected List<GKInstance> getManuallyCuratedInstances(MySQLAdaptor dbAdaptor) throws Exception {
        List<GKInstance> manuallyCuratedPhysicalEntities = new ArrayList<>();
        for (GKInstance physicalEntity : getAllInstancesFromDBA(dbAdaptor)) {
            if (!isElectronicallyInferred(physicalEntity)) {
                manuallyCuratedPhysicalEntities.add(physicalEntity);
            }
        }
        return manuallyCuratedPhysicalEntities;
    }

    @Override
    protected String getInstanceType() {
        return ReactomeJavaConstants.PhysicalEntity;
    }

    private boolean isElectronicallyInferred(GKInstance instance) throws Exception {
        return hasOnlyNonHumanSpecies(instance) && inferredExclusivelyFromHuman(instance);
    }

    private boolean hasOnlyNonHumanSpecies(GKInstance instance) throws Exception {
        List<String> speciesNames = getSpeciesNames(instance);

        return !speciesNames.isEmpty() &&
            speciesNames.stream().noneMatch(speciesName -> speciesName.contains("Homo sapiens"));
    }

    @SuppressWarnings("unchecked")
    private boolean inferredExclusivelyFromHuman(GKInstance instance) throws Exception {
        List<GKInstance> sourceInstances = instance.getAttributeValuesList(ReactomeJavaConstants.inferredFrom);

        if (sourceInstances == null || sourceInstances.isEmpty()) {
            return false;
        }

        for (GKInstance sourceInstance : sourceInstances) {
            if (!isOnlyHuman(sourceInstance)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOnlyHuman(GKInstance instance) throws Exception {
        List<String> speciesNames = getSpeciesNames(instance);
        return !speciesNames.isEmpty() &&
            speciesNames.stream().allMatch(speciesName -> speciesName.contains("Homo sapiens"));
    }

    @SuppressWarnings("unchecked")
    private List<String> getSpeciesNames(GKInstance instance) throws Exception {
        if (!instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.species)) {
            return new ArrayList<>();
        }

        List<GKInstance> instanceSpeciesList = instance.getAttributeValuesList(ReactomeJavaConstants.species);
        if (instanceSpeciesList == null || instanceSpeciesList.isEmpty()) {
            return new ArrayList<>();
        }
        return instanceSpeciesList.stream().map(GKInstance::getDisplayName).collect(Collectors.toList());
    }
}
