package org.gk.model;

import static org.junit.Assert.*;

import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

public class InstanceUtilitiesTest {

    @Test
    public void testIsDrug() throws Exception {
        MySQLAdaptor testDBA = new MySQLAdaptor("localhost",
                "reactome",
                "liam",
                ")8J7m]!%[<");

        // Protein (non-EntitySet/Complex).
        GKInstance protein = testDBA.fetchInstance(976898L);
        assertEquals(false, InstanceUtilities.isDrug(protein));

        // Drug (17-AAG [cytosol]).
        GKInstance drug = testDBA.fetchInstance(1217506L);
        assertEquals(true, InstanceUtilities.isDrug(drug));

        // Control EntitySet (GSK [cytosol]). Has two members, none of which are drugs.
        GKInstance entitySet = testDBA.fetchInstance(5632097L);
        assertEquals(false, InstanceUtilities.isDrug(entitySet));

        // Add drug instance (17-AAG [cytosol]) to member set.
        entitySet.addAttributeValue(ReactomeJavaConstants.hasMember, drug);

        // Test to confirm it now contains a drug instance (should return true).
        assertEquals(true, InstanceUtilities.isDrug(entitySet));

        // Remove added drug and retest (should return false).
        entitySet.removeAttributeValueNoCheck(ReactomeJavaConstants.hasMember, drug);
        assertEquals(false, InstanceUtilities.isDrug(entitySet));

        // Control Complex (activated NPR1/NH1 [cytosol]).
        GKInstance complex = testDBA.fetchInstance(6788198L);
        assertEquals(false, InstanceUtilities.isDrug(complex));

        // Add drug instance to component set.
        complex.addAttributeValue(ReactomeJavaConstants.hasComponent, drug);

        // Test to confirm it now contains a drug instance (should return true).
        assertEquals(true, InstanceUtilities.isDrug(complex));

        // Remove added drug and retest (should return false).
        complex.removeAttributeValueNoCheck(ReactomeJavaConstants.hasComponent, drug);
        assertEquals(false, InstanceUtilities.isDrug(complex));
    }

}
