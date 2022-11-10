package org.gk.slicing.updateTracker;

import static org.gk.slicing.updateTracker.utils.DBUtils.getCreatedInstanceEdit;
import static org.gk.slicing.updateTracker.utils.DBUtils.getMostRecentReleaseInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.slicing.updateTracker.comparer.EventComparer;
import org.gk.slicing.updateTracker.matcher.EventMatcher;
import org.gk.slicing.updateTracker.model.Action;
import org.gk.slicing.updateTracker.model.UpdateTracker;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTrackerHandler {
    private final static Logger logger = Logger.getLogger(UpdateTracker.class);
    
    private MySQLAdaptor sourceDBA;
    private MySQLAdaptor currentSliceDBA;
    private MySQLAdaptor previousSliceDBA;
    private long personId;

    public UpdateTrackerHandler(
        MySQLAdaptor sourceDBA, MySQLAdaptor currentSliceDBA, MySQLAdaptor previousSliceDBA, long personId
    ) {
        this.sourceDBA = sourceDBA;
        this.currentSliceDBA = currentSliceDBA;
        this.previousSliceDBA = previousSliceDBA;
        this.personId = personId;
    }

    public void handleUpdateTrackerInstances(boolean uploadUpdateTrackerInstancesToSource) throws Exception {
        EventMatcher eventMatcher = new EventMatcher(getPreviousSliceDBA(), getCurrentSliceDBA(), getSourceDBA());
        EventComparer eventComparer = new EventComparer(eventMatcher);

        UpdateTracker.UpdateTrackerBuilder updateTrackerBuilder =
            getUpdateTrackerBuilder(getCurrentSliceDBA());

        Set<Map.Entry<GKInstance,GKInstance>> equivalentEventPairs =
            eventMatcher.getCurationPreviousToCurrentInstanceMap().entrySet();
        List<GKInstance> toBeUploadedToSrcDBA = new ArrayList<>();
        for (Map.Entry<GKInstance, GKInstance> equivalentEventPair : equivalentEventPairs) {
            Set<Action> actions = eventComparer.getChanges(equivalentEventPair);

            if (!actions.isEmpty()) {
                GKInstance currentEvent = equivalentEventPair.getValue();

                if (uploadUpdateTrackerInstancesToSource) {
                    GKInstance sourceEvent = getSourceDBA().fetchInstance(currentEvent.getDBID());
                    GKInstance updateTracker = updateTrackerBuilder
                                              .build(sourceEvent, actions)
                                              .createUpdateTrackerInstance(getSourceDBA());
                    toBeUploadedToSrcDBA.add(updateTracker);
//                    getSourceDBA().storeInstance(
//                        updateTrackerBuilder
//                            .build(sourceEvent, actions)
//                            .createUpdateTrackerInstance(getSourceDBA())
//                    );
                }

                getCurrentSliceDBA().storeInstance(
                    updateTrackerBuilder
                        .build(currentEvent, actions)
                        .createUpdateTrackerInstance(getCurrentSliceDBA())
                );
            }
        }
        commitToSourceDB(toBeUploadedToSrcDBA);
    }
    
    private void commitToSourceDB(List<GKInstance> instances) throws Exception {
        if (instances == null || instances.size() == 0)
            return; // Nothing to do.
        boolean needTransaction = sourceDBA.supportsTransactions();
        try {
            if (needTransaction)
                sourceDBA.startTransaction();
            for (GKInstance instance : instances) {
                sourceDBA.storeInstance(instance);
            }
            if (needTransaction)
                sourceDBA.commit();
        }
        catch(Exception e) {
            if (needTransaction)
                sourceDBA.rollback();
            logger.error("UpdateTrackerHandler.commitToSourceDB(): " + e, e);
            throw e;
        }
    }

    private MySQLAdaptor getSourceDBA() {
        return this.sourceDBA;
    }

    private MySQLAdaptor getCurrentSliceDBA() {
        return this.currentSliceDBA;
    }

    private MySQLAdaptor getPreviousSliceDBA() {
        return this.previousSliceDBA;
    }

    private long getPersonId() {
        return this.personId;
    }

    private UpdateTracker.UpdateTrackerBuilder getUpdateTrackerBuilder(MySQLAdaptor dbAdaptor) throws Exception {
        GKInstance releaseInstance = getMostRecentReleaseInstance(dbAdaptor);
        GKInstance createdInstanceEdit = getCreatedInstanceEdit(dbAdaptor, getPersonId());

        return UpdateTracker.UpdateTrackerBuilder.createUpdateTrackerBuilder(releaseInstance, createdInstanceEdit);
    }
}
