package org.gk.slicing.updateTracker.matcher;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class EventMatcher extends InstanceMatcher {

    public EventMatcher(MySQLAdaptor previousDBA, MySQLAdaptor currentDBA, MySQLAdaptor targetDBA) throws Exception {
        super(previousDBA, currentDBA, targetDBA);
    }

    @Override
    protected List<GKInstance> getManuallyCuratedInstances(MySQLAdaptor dbAdaptor) throws Exception {
        List<GKInstance> manuallyCuratedEvents = new ArrayList<>();
        for (GKInstance event : getAllInstancesFromDBA(dbAdaptor)) {
            if (!isElectronicallyInferred(event)) {
                manuallyCuratedEvents.add(event);
            }
        }
        return manuallyCuratedEvents;
    }

    @Override
    protected String getInstanceType() {
        return ReactomeJavaConstants.Event;
    }

    private boolean isElectronicallyInferred(GKInstance instance) throws Exception {
        GKInstance evidenceType = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.evidenceType);

        return evidenceType != null && evidenceType.getDisplayName().contains("electronic annotation");
    }

    @Override
    protected boolean differentSchemaClasses(GKInstance previousEvent, GKInstance currentEvent) {
        if (previousEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
            return !currentEvent.getSchemClass().isa(ReactomeJavaConstants.Pathway);
        } else if (isReaction(previousEvent)) {
            if (currentEvent.getDbAdaptor().getSchema().isValidClass(ReactomeJavaConstants.ReactionlikeEvent)) {
                return !currentEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent);
            } else {
                return !currentEvent.getSchemClass().getName().equals(ReactomeJavaConstants.Reaction);
            }
        } else {
            throw new IllegalStateException("Unknown event class for previous event: " + previousEvent);
        }
    }

    private boolean isReaction(GKInstance event) {
        if (event.getDbAdaptor().getSchema().isValidClass(ReactomeJavaConstants.ReactionlikeEvent)) {
            return event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent);
        } else {
            return event.getSchemClass().isa(ReactomeJavaConstants.Reaction);
        }
    }

    private boolean differentSpecies(GKInstance previousEvent, GKInstance currentEvent) throws Exception {
        return !getSpeciesNames(previousEvent).equals(getSpeciesNames(currentEvent));
    }

    private String getSpeciesNames(GKInstance event) throws Exception {
        List<GKInstance> speciesList = event.getAttributeValuesList(ReactomeJavaConstants.species);
        if (speciesList.isEmpty()) {
            return "";
        } else if (speciesList.size() > 1) {
            return speciesList.stream().map(GKInstance::getDisplayName).sorted().collect(Collectors.joining(","));
        }

        GKInstance species = speciesList.get(0);
        return species.getDisplayName();
    }
}