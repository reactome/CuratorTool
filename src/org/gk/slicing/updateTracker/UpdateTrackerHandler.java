package org.gk.slicing.updateTracker;

import static org.gk.slicing.updateTracker.utils.DBUtils.getCreatedInstanceEdit;
import static org.gk.slicing.updateTracker.utils.DBUtils.getMostRecentReleaseInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.slicing.updateTracker.comparer.EventComparer;
import org.gk.slicing.updateTracker.comparer.InstanceComparer;
import org.gk.slicing.updateTracker.comparer.physicalentity.PhysicalEntityComparerFactory;
import org.gk.slicing.updateTracker.matcher.EventMatcher;
import org.gk.slicing.updateTracker.matcher.InstanceMatcher;
import org.gk.slicing.updateTracker.matcher.PhysicalEntityMatcher;
import org.gk.slicing.updateTracker.model.Action;
import org.gk.slicing.updateTracker.model.UpdateTracker;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTrackerHandler {
    private final static Logger logger = Logger.getLogger(UpdateTracker.class);

    private DbAdaptorMap dbAdaptorMap;
    private long personId;

    public UpdateTrackerHandler(
        MySQLAdaptor sourceDBA, MySQLAdaptor currentSliceDBA, MySQLAdaptor previousSliceDBA, long personId
    ) {
        DbAdaptorMap.DbAdaptorMapBuilder dbAdaptorMapBuilder = new DbAdaptorMap.DbAdaptorMapBuilder();
        dbAdaptorMapBuilder.setOlderDbAdaptor(previousSliceDBA);
        dbAdaptorMapBuilder.setNewerDbAdaptor(currentSliceDBA);
        dbAdaptorMapBuilder.setTargetDbAdaptor(sourceDBA);
        this.dbAdaptorMap = dbAdaptorMapBuilder.build();

        this.personId = personId;
    }

    public void handleUpdateTrackerInstances(boolean uploadUpdateTrackerInstancesToSource) throws Exception {
        EventMatcher eventMatcher = new EventMatcher(getPreviousSliceDBA(), getCurrentSliceDBA(), getSourceDBA());
        EventComparer eventComparer = new EventComparer(eventMatcher);

        PhysicalEntityMatcher physicalEntityMatcher = new PhysicalEntityMatcher(getPreviousSliceDBA(), getCurrentSliceDBA(), getSourceDBA());

        UpdateTracker.UpdateTrackerBuilder updateTrackerBuilder =
            getUpdateTrackerBuilder(getCurrentSliceDBA());

        Set<Map.Entry<GKInstance,GKInstance>> equivalentEventPairs =
            eventMatcher.getCurationPreviousToCurrentInstanceMap().entrySet();
        Set<Map.Entry<GKInstance, GKInstance>> equivalentPhysicalEntityPairs =
            physicalEntityMatcher.getCurationPreviousToCurrentInstanceMap().entrySet();

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
                }

                getCurrentSliceDBA().storeInstance(
                    updateTrackerBuilder
                        .build(currentEvent, actions)
                        .createUpdateTrackerInstance(getCurrentSliceDBA())
                );
            }
        }
        for (Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair : equivalentPhysicalEntityPairs) {
            Set<Action> actions = PhysicalEntityComparerFactory.create(equivalentPhysicalEntityPair).
                getChanges(equivalentPhysicalEntityPair);

            if (!actions.isEmpty()) {
                GKInstance currentPhysicalEntity = equivalentPhysicalEntityPair.getValue();

                if (uploadUpdateTrackerInstancesToSource) {
                    GKInstance sourcePhysicalEntity = getSourceDBA().fetchInstance(currentPhysicalEntity.getDBID());
                    GKInstance updateTracker = updateTrackerBuilder
                                                .build(sourcePhysicalEntity, actions)
                                                .createUpdateTrackerInstance(getSourceDBA());
                    toBeUploadedToSrcDBA.add(updateTracker);
                }

                getCurrentSliceDBA().storeInstance(
                    updateTrackerBuilder
                        .build(currentPhysicalEntity, actions)
                    .createUpdateTrackerInstance(getCurrentSliceDBA())
                );
            }
        }
        commitToSourceDB(toBeUploadedToSrcDBA);
    }

    private List<UpdateTracker> processEquivalentInstancePairs(ComparisonType comparisonType) throws Exception {
        UpdateTracker.UpdateTrackerBuilder updateTrackerBuilder =
            getUpdateTrackerBuilder(getCurrentSliceDBA());

        List<UpdateTracker> updateTrackerList = new ArrayList<>();

        InstanceMatcher instanceMatcher = getInstanceMatcher(comparisonType);

        System.out.println("Getting " + comparisonType.name() + " instance pairs...");
        Set<Map.Entry<GKInstance,GKInstance>> equivalentInstancePairs =
            instanceMatcher.getCurationCurrentToPreviousInstances().entrySet();

        System.out.println("Instance pairs size: " + equivalentInstancePairs.size());
        for (Map.Entry<GKInstance, GKInstance> equivalentInstancePair : equivalentInstancePairs) {
            GKInstance previousInstance = equivalentInstancePair.getKey();
            GKInstance currentInstance = equivalentInstancePair.getValue();

            InstanceComparer instanceComparer;
            if (comparisonType == ComparisonType.EVENT) {
                instanceComparer = new EventComparer((EventMatcher) instanceMatcher);
            } else {
                instanceComparer = PhysicalEntityComparerFactory.create(equivalentInstancePair);
            }

            Set<Action> actions = instanceComparer.getChanges(equivalentInstancePair);

            if (!actions.isEmpty()) {
                GKInstance targetInstance = getDbAdaptorMap().getTargetDbAdaptor().fetchInstance(currentInstance.getDBID());
                UpdateTracker updateTracker = updateTrackerBuilder.build(targetInstance, actions);
                updateTrackerList.add(updateTracker);
            }
        }
        return updateTrackerList;
    }

    private InstanceMatcher getInstanceMatcher(ComparisonType comparisonType)
        throws Exception {
        InstanceMatcher instanceMatcher;
        if (comparisonType == ComparisonType.EVENT) {
            instanceMatcher = new EventMatcher(
                getDbAdaptorMap().getOlderDbAdaptor(),
                getDbAdaptorMap().getNewerDbAdaptor(),
                getDbAdaptorMap().getTargetDbAdaptor());
        } else {
            instanceMatcher = new PhysicalEntityMatcher(
                getDbAdaptorMap().getOlderDbAdaptor(),
                getDbAdaptorMap().getNewerDbAdaptor(),
                getDbAdaptorMap().getTargetDbAdaptor());
        }
        return instanceMatcher;
    }

    private void commitToSourceDB(List<GKInstance> instances) throws Exception {
        if (instances == null || instances.size() == 0)
            return; // Nothing to do.
        boolean needTransaction = getSourceDBA().supportsTransactions();
        try {
            if (needTransaction) {
                getSourceDBA().startTransaction();
            }
            for (GKInstance instance : instances) {
                getSourceDBA().storeInstance(instance);
            }
            if (needTransaction) {
                getSourceDBA().commit();
            }
        } catch(Exception e) {
            if (needTransaction) {
                getSourceDBA().rollback();
            }
            logger.error("UpdateTrackerHandler.commitToSourceDB(): " + e, e);
            throw e;
        }
    }

    private DbAdaptorMap getDbAdaptorMap() {
        return this.dbAdaptorMap;
    }

    private MySQLAdaptor getSourceDBA() {
        return getDbAdaptorMap().getTargetDbAdaptor();
    }

    private MySQLAdaptor getCurrentSliceDBA() {
        return getDbAdaptorMap().getNewerDbAdaptor();
    }

    private MySQLAdaptor getPreviousSliceDBA() {
        return getDbAdaptorMap().getOlderDbAdaptor();
    }

    private long getPersonId() {
        return this.personId;
    }

    private UpdateTracker.UpdateTrackerBuilder getUpdateTrackerBuilder(MySQLAdaptor dbAdaptor) throws Exception {
        GKInstance releaseInstance = getMostRecentReleaseInstance(dbAdaptor);
        GKInstance createdInstanceEdit = getCreatedInstanceEdit(dbAdaptor, getPersonId());

        return UpdateTracker.UpdateTrackerBuilder.createUpdateTrackerBuilder(releaseInstance, createdInstanceEdit);
    }

    private static class DbAdaptorMap {
        private MySQLAdaptor olderDbAdaptor;
        private MySQLAdaptor newerDbAdaptor;
        private MySQLAdaptor targetDbAdaptor;

        private DbAdaptorMap() {}

        public MySQLAdaptor getOlderDbAdaptor() {
            return this.olderDbAdaptor;
        }

        public MySQLAdaptor getNewerDbAdaptor() {
            return this.newerDbAdaptor;
        }

        public MySQLAdaptor getTargetDbAdaptor() {
            return this.targetDbAdaptor;
        }

        private void setOlderDbAdaptor(MySQLAdaptor olderDbAdaptor) {
            this.olderDbAdaptor = olderDbAdaptor;
        }

        private void setNewerDbAdaptor(MySQLAdaptor newerDbAdaptor) {
            this.newerDbAdaptor = newerDbAdaptor;
        }

        private void setTargetDbAdaptor(MySQLAdaptor targetDbAdaptor) {
            this.targetDbAdaptor = targetDbAdaptor;
        }

        private static class DbAdaptorMapBuilder {
            private MySQLAdaptor olderDbAdaptor;
            private MySQLAdaptor newerDbAdaptor;
            private MySQLAdaptor targetDbAdaptor;

            public DbAdaptorMapBuilder() {}

            private void setOlderDbAdaptor(MySQLAdaptor olderDbAdaptor) {
                this.olderDbAdaptor = olderDbAdaptor;
            }

            private void setNewerDbAdaptor(MySQLAdaptor newerDbAdaptor) {
                this.newerDbAdaptor = newerDbAdaptor;
            }

            private void setTargetDbAdaptor(MySQLAdaptor targetDbAdaptor) {
                this.targetDbAdaptor = targetDbAdaptor;
            }

            public DbAdaptorMap build() {
                DbAdaptorMap dbAdaptorMap = new DbAdaptorMap();
                dbAdaptorMap.setOlderDbAdaptor(this.olderDbAdaptor);
                dbAdaptorMap.setNewerDbAdaptor(this.newerDbAdaptor);
                dbAdaptorMap.setTargetDbAdaptor(this.targetDbAdaptor);
                return dbAdaptorMap;
            }
        }
    }

    private enum ComparisonType {
        EVENT,
        PHYSICAL_ENTITY
    }
}
