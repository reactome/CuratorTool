package org.gk.slicing.updateTracker.utils;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 */
public class DBUtils {
    private static Map<MySQLAdaptor, GKInstance> dbAdaptorToCreatedInstanceEdit = new HashMap<>();

    public static GKInstance getMostRecentReleaseInstance(MySQLAdaptor dbAdaptor) throws Exception {
        Collection<GKInstance> releaseInstances = dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants._Release);

        if (releaseInstances == null || releaseInstances.isEmpty()) {
            return null;
        }

        return releaseInstances
            .stream()
            .sorted(Comparator.comparing(DBUtils::getReleaseVersion).reversed())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No release instance(s) from " + dbAdaptor));
    }

    public static GKInstance getCreatedInstanceEdit(MySQLAdaptor dbAdaptor, long personDbId) throws Exception {
        GKInstance createdInstanceEdit = dbAdaptorToCreatedInstanceEdit.get(dbAdaptor);

        if (createdInstanceEdit == null) {
            createdInstanceEdit = new GKInstance(getInstanceEditSchemaClass(dbAdaptor));
            createdInstanceEdit.setAttributeValue(ReactomeJavaConstants.note, "Update Tracker Creator");
            createdInstanceEdit.setAttributeValue(ReactomeJavaConstants.dateTime, getCurrentDateTime());
            createdInstanceEdit.setAttributeValue(
                ReactomeJavaConstants.author, getPersonInstance(dbAdaptor, personDbId)
            );
            InstanceDisplayNameGenerator.setDisplayName(createdInstanceEdit);
            dbAdaptorToCreatedInstanceEdit.put(dbAdaptor, createdInstanceEdit);
        }

        return createdInstanceEdit;
    }

    public static SchemaClass getSchemaClass(MySQLAdaptor dbAdaptor, String className) throws Exception {
        if (dbAdaptor.getSchema() == null) {
            dbAdaptor.fetchSchema();
        }

        return dbAdaptor.getSchema().getClassByName(className);
    }

    private static GKInstance getPersonInstance(MySQLAdaptor dbAdaptor, long personId) throws Exception {
        return dbAdaptor.fetchInstance(ReactomeJavaConstants.Person, personId);
    }

    private static SchemaClass getInstanceEditSchemaClass(MySQLAdaptor dbAdaptor) throws Exception {
        return getSchemaClass(dbAdaptor, ReactomeJavaConstants.InstanceEdit);
    }

    private static Integer getReleaseVersion(GKInstance releaseInstance) {
        try {
            return Integer.parseInt(releaseInstance.getAttributeValue(ReactomeJavaConstants.releaseNumber).toString());
        } catch (Exception e) {
            throw new RuntimeException("Unable to get release number from release instance " + releaseInstance, e);
        }
    }

    private static String getCurrentDateTime() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
        LocalDateTime now = LocalDateTime.now();
        return dateTimeFormatter.format(now);
    }
}
