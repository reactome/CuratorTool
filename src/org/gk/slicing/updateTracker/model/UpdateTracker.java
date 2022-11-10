package org.gk.slicing.updateTracker.model;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gk.slicing.updateTracker.utils.DBUtils.getSchemaClass;

/**
 * This class is used to model _UpdateTracker class. The constructor is private. The client should 
 * get the embedded UpateTrackerBuilder to construct UpdateTracker objects.
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class UpdateTracker {
    private GKInstance _Release;
    private int releaseNumber;
    private Set<Action> actions;
    private GKInstance updatedInstance;
    private GKInstance createdInstanceEdit;

    private UpdateTracker(GKInstance _Release, int releaseNumber, GKInstance createdInstanceEdit,
                          Set<Action> actions, GKInstance updatedInstance) {
        this._Release = _Release;
        this.releaseNumber = releaseNumber;
        this.createdInstanceEdit = createdInstanceEdit;
        this.actions = actions;
        this.updatedInstance = updatedInstance;
    }

    public GKInstance getReleaseInstance() {
        return this._Release;
    }

    public Set<Action> getActions() {
        return this.actions;
    }

    public GKInstance getUpdatedInstance() {
        return this.updatedInstance;
    }

    private SchemaClass getUpdateTrackerSchemaClass(MySQLAdaptor dbAdaptor) throws Exception {
        return getSchemaClass(dbAdaptor, "_UpdateTracker");
    }

    public GKInstance createUpdateTrackerInstance(MySQLAdaptor dbAdaptor) throws Exception {
        GKInstance updateTrackerInstance = new GKInstance(getUpdateTrackerSchemaClass(dbAdaptor));

        updateTrackerInstance.setDbAdaptor(dbAdaptor);

        GKInstance releaseInstance = getReleaseInstance();
        releaseInstance.setSchemaClass(dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants._Release));
        releaseInstance.setDbAdaptor(dbAdaptor);
        updateTrackerInstance.setAttributeValue(ReactomeJavaConstants._release, releaseInstance);

        updateTrackerInstance.setAttributeValue("action", getActionsAsStrings());
        GKInstance updatedInstance = getUpdatedInstance();
        updatedInstance.setSchemaClass(dbAdaptor.getSchema().getClassByName(getSchemaClassName(updatedInstance)));
        updateTrackerInstance.setAttributeValue("updatedInstance", getUpdatedInstance());

        GKInstance createdInstanceEdit = getCreatedInstanceEdit();
        createdInstanceEdit.setSchemaClass(dbAdaptor.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit));
        createdInstanceEdit.setDbAdaptor(dbAdaptor);
        updateTrackerInstance.setAttributeValue(ReactomeJavaConstants.created, createdInstanceEdit);

        updateTrackerInstance.setDisplayName(generateDisplayName());

        return updateTrackerInstance;
    }


    private String generateDisplayName() {
        return String.format(
            "Update Tracker - %s - v%d:%s",
            getUpdatedInstance().getExtendedDisplayName(),
            getReleaseNumber(),
            getActionsAsStrings()
        );
    }

    private List<String> getActionsAsStrings() {
        return getActions().stream()
            .map(Action::toString)
            .collect(Collectors.toList());
    }

    private GKInstance getCreatedInstanceEdit() {
        return this.createdInstanceEdit;
    }

    private String getSchemaClassName(GKInstance instance) {
        return updatedInstance.getSchemClass().getName();
    }

    private int getReleaseNumber() {
        return this.releaseNumber;
    }
    
    /**
     * The factory class to build UpdateTracker instance.
     * @author wug
     *
     */
    public static class UpdateTrackerBuilder {
        private Integer releaseNumber;
        private GKInstance _Release;
        private GKInstance createdInstanceEdit;

        public static UpdateTrackerBuilder createUpdateTrackerBuilder(GKInstance _Release,
                                                                      GKInstance createdInstanceEdit) {
            return new UpdateTrackerBuilder(_Release, createdInstanceEdit);
        }

        private UpdateTrackerBuilder(GKInstance _Release, GKInstance createdInstanceEdit) {
            this._Release = _Release;
            this.createdInstanceEdit = createdInstanceEdit;
            this.releaseNumber = getReleaseNumber();
        }

        private int getReleaseNumber() {
            if (this.releaseNumber == null) {
                try {
//                    System.out.println(this._Release);
                    this.releaseNumber = (int) this._Release.getAttributeValue(ReactomeJavaConstants.releaseNumber);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to get release number from " +
                        (this._Release != null ? this._Release.getExtendedDisplayName() : null), e);
                }
            }
            return this.releaseNumber;
        }

        public UpdateTracker build(GKInstance updatedInstance, Set<Action> actions) {
            return new UpdateTracker(
                this._Release, this.releaseNumber, this.createdInstanceEdit, actions, updatedInstance);
        }
    }
}
