package org.gk.slicing.updateTracker;

import static org.gk.slicing.updateTracker.utils.DBUtils.getCreatedInstanceEdit;
import static org.gk.slicing.updateTracker.utils.DBUtils.getMostRecentReleaseInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.slicing.updateTracker.comparer.EventComparer;
import org.gk.slicing.updateTracker.comparer.InstanceComparer;
import org.gk.slicing.updateTracker.comparer.physicalentity.PhysicalEntityComparerFactory;
import org.gk.slicing.updateTracker.matcher.EventMatcher;
import org.gk.slicing.updateTracker.matcher.InstanceMatcher;
import org.gk.slicing.updateTracker.matcher.PhysicalEntityMatcher;
import org.gk.slicing.updateTracker.model.Action;
import org.gk.slicing.updateTracker.model.UpdateTracker;
import org.gk.slicing.updateTracker.utils.DBUtils;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTrackerHandler {
    private final static Logger logger = Logger.getLogger(UpdateTracker.class);

    private EventComparer eventComparer;

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
        if (uploadUpdateTrackerInstancesToSource) {
        	logger.info("Storing release instance in source database");
        	storeReleaseInstanceInSourceDatabase();
        }
        logger.info("Creating event update tracker instances");
        createAndStoreUpdateTrackerInstances(ComparisonType.EVENT, uploadUpdateTrackerInstancesToSource);
        logger.info("Creating physical entity update tracker instances");
        createAndStoreUpdateTrackerInstances(ComparisonType.PHYSICAL_ENTITY, uploadUpdateTrackerInstancesToSource);
    }
    
    private void storeReleaseInstanceInSourceDatabase() throws Exception {
    	GKInstance releaseInstanceFromSlice = getMostRecentReleaseInstance(getCurrentSliceDBA());
    	GKInstance newReleaseInstance = cloneReleaseInstance(releaseInstanceFromSlice, getSourceDBA());
    	getSourceDBA().storeInstance(newReleaseInstance);
    }
    
    private GKInstance cloneReleaseInstance(GKInstance releaseInstance, MySQLAdaptor dba) throws Exception {
    	GKInstance newReleaseInstance = new GKInstance(DBUtils.getSchemaClass(dba, ReactomeJavaConstants._Release));
    	newReleaseInstance.setDbAdaptor(dba);
    	newReleaseInstance.setAttributeValue(ReactomeJavaConstants.releaseDate, 
			releaseInstance.getAttributeValue(ReactomeJavaConstants.releaseDate));
    	newReleaseInstance.setAttributeValue(ReactomeJavaConstants.releaseNumber, 
			releaseInstance.getAttributeValue(ReactomeJavaConstants.releaseNumber));
    	InstanceDisplayNameGenerator.setDisplayName(newReleaseInstance);
    	return newReleaseInstance;
    }

    private void createAndStoreUpdateTrackerInstances(
        ComparisonType comparisonType, boolean uploadUpdateTrackerInstancesToSource) throws Exception {

    	UpdateTracker.UpdateTrackerBuilder sourceUpdateTrackerBuilder =
    		getUpdateTrackerBuilder(getSourceDBA());
        UpdateTracker.UpdateTrackerBuilder sliceUpdateTrackerBuilder =
            getUpdateTrackerBuilder(getCurrentSliceDBA());

        InstanceMatcher instanceMatcher = getInstanceMatcher(comparisonType);

        logger.info("Getting " + comparisonType.name() + " instance pairs...");
        Set<Map.Entry<GKInstance,GKInstance>> equivalentInstancePairs =
            instanceMatcher.getCurationCurrentToPreviousInstances().entrySet();

        logger.info("Instance pairs size: " + equivalentInstancePairs.size());
        List<GKInstance> toBeUploadedToSrcDBA = new ArrayList<>();
        for (Map.Entry<GKInstance, GKInstance> equivalentInstancePair : equivalentInstancePairs) {
            Set<Action> actions = getInstanceComparer(comparisonType, equivalentInstancePair)
                .getChanges(equivalentInstancePair);

            if (!actions.isEmpty()) {
                logger.info("Actions " + actions);
                GKInstance currentInstance = equivalentInstancePair.getValue();

                if (uploadUpdateTrackerInstancesToSource) {
                    logger.info("Adding toBeUploadedToSrcDBA " + currentInstance);
                    GKInstance sourceInstance = getSourceDBA().fetchInstance(currentInstance.getDBID());
                    GKInstance updateTracker = sourceUpdateTrackerBuilder
                        .build(sourceInstance, actions)
                        .createUpdateTrackerInstance(getSourceDBA());
                    toBeUploadedToSrcDBA.add(updateTracker);
                }

                logger.info("Storing instance in current slice dba " + currentInstance);
                getCurrentSliceDBA().storeInstance(
                    sliceUpdateTrackerBuilder
                        .build(currentInstance, actions)
                        .createUpdateTrackerInstance(getCurrentSliceDBA())
                );
            }
        }
        commitToSourceDB(toBeUploadedToSrcDBA);
    }

    private InstanceComparer getInstanceComparer(ComparisonType comparisonType, Map.Entry<GKInstance, GKInstance> equivalentInstancePair) throws Exception {
        InstanceComparer instanceComparer;
        if (comparisonType == ComparisonType.EVENT) {
            instanceComparer = getEventComparer();
        } else {
            instanceComparer = PhysicalEntityComparerFactory.create(equivalentInstancePair);
        }
        return instanceComparer;
    }

    private EventComparer getEventComparer() throws Exception {
        if (this.eventComparer == null) {
            this.eventComparer = new EventComparer(new EventMatcher(getPreviousSliceDBA(), getCurrentSliceDBA(), getSourceDBA()));
        }
        return this.eventComparer;
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
        logger.info("Instances to commit source database: " + instances);
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
