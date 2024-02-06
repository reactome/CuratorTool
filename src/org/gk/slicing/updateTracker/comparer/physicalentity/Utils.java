package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import java.util.Map;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 6/28/2023
 */
public class Utils {

    public static boolean bothComplexes(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.Complex, equivalentPhysicalEntityPair);
    }

    public static boolean bothSets(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.EntitySet, equivalentPhysicalEntityPair);
    }

    public static boolean bothPolymers(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.Polymer, equivalentPhysicalEntityPair);
    }

    public static boolean bothHaveSchemaClass(
        String schemaClass, Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {

        GKInstance earlierPhysicalEntity = equivalentPhysicalEntityPair.getKey();
        GKInstance newPhysicalEntity = equivalentPhysicalEntityPair.getValue();

        return earlierPhysicalEntity != null &&
            newPhysicalEntity != null &&
            earlierPhysicalEntity.getSchemClass().isa(schemaClass) &&
            newPhysicalEntity.getSchemClass().isa(schemaClass);
    }
}
