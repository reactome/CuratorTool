package org.gk.slicing.updateTracker.comparer.physicalentity;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

import java.util.Map;

import static org.gk.slicing.updateTracker.comparer.physicalentity.Utils.*;


/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 6/22/2023
 */
public class PhysicalEntityComparerFactory {

    public static AbstractPhysicalEntityComparer create(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        if (bothGEEs(equivalentPhysicalEntityPair)) {
            if (bothEWASs(equivalentPhysicalEntityPair)) {
                return new EWASComparer();
            }
            return new GEEComparer();
        } else if (bothComplexes(equivalentPhysicalEntityPair)) {
            return new ComplexComparer();
        } else if (bothSets(equivalentPhysicalEntityPair)) {
            if (bothCandidateSets(equivalentPhysicalEntityPair)) {
                return new CandidateSetComparer();
            }
            return new EntitySetComparer();
        } else if (bothPolymers(equivalentPhysicalEntityPair)) {
            return new PolymerComparer();
        } else if (bothSimpleEntities(equivalentPhysicalEntityPair)) {
            return new SimpleEntityComparer();
        } else if (bothDrugs(equivalentPhysicalEntityPair)) {
            return new DrugComparer();
        } else if (bothOtherEntities(equivalentPhysicalEntityPair)) {
            return new OtherEntityComparer();
        } else if (bothCells(equivalentPhysicalEntityPair)) {
            return new CellComparer();
        } else {
            System.err.println("The schema classes for " + equivalentPhysicalEntityPair +
                " do not match or are unknown/unsupported - using basic physical entity comparer");
            return new DefaultPhysicalEntityComparer();
        }
    }

    private static boolean bothGEEs(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.GenomeEncodedEntity, equivalentPhysicalEntityPair);
    }

    private static boolean bothEWASs(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.EntityWithAccessionedSequence, equivalentPhysicalEntityPair);
    }

    private static boolean bothCandidateSets(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.CandidateSet, equivalentPhysicalEntityPair);
    }

    private static boolean bothSimpleEntities(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.SimpleEntity, equivalentPhysicalEntityPair);
    }

    private static boolean bothDrugs(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.Drug, equivalentPhysicalEntityPair);
    }

    private static boolean bothOtherEntities(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.OtherEntity, equivalentPhysicalEntityPair);
    }

    private static boolean bothCells(Map.Entry<GKInstance, GKInstance> equivalentPhysicalEntityPair) {
        return bothHaveSchemaClass(ReactomeJavaConstants.Cell, equivalentPhysicalEntityPair);
    }
}
