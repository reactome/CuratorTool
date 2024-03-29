/*
 * Created on Feb 2, 2006
 *
 */
package org.reactome.test;

import java.util.Collection;
import java.util.List;

import org.apache.log4j.PropertyConfigurator;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.slicing.SlicingEngine;
import org.junit.Test;

/**
 * This TestCase is used to check some functions in SlicingEngine class.
 * @author guanming
 *
 */
public class SlicingEngineTest {

    public SlicingEngineTest() {
        // Set up log4j
        PropertyConfigurator.configure("SliceLog4j.properties");
    }
    
    @Test
    public void testHandleDeletions() throws Exception {
        MySQLAdaptor sourceDBA = new MySQLAdaptor("localhost", "gk_central_101223_new_schema", "", "");
        SlicingEngine engine = new SlicingEngine();
        engine.setSource(sourceDBA);
        engine.handleDeletions();
    }
    
    @Test
    public void check() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost", "test_slice_54a", "", "");
        SchemaClass eventCls = dba.fetchSchema().getClassByName(ReactomeJavaConstants.Event);
        Collection<?> events = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Event,
                                                            ReactomeJavaConstants.species,
                                                            "=",
                                                            186860);
        System.out.println("Total rice events: " + events.size());
        int count = 0;
        for (Object obj : events) {
            GKInstance event = (GKInstance) obj;
            for (Object att : event.getSchemaAttributes()) {
                GKSchemaAttribute att1 = (GKSchemaAttribute) att;
                if (!att1.getAllowedClasses().contains(eventCls))
                    continue;
                Collection referrers = event.getReferers((SchemaAttribute)att);
                if (referrers.size() == 0)
                    continue;
                for (Object referrer : referrers) {
                    GKInstance referrer1 = (GKInstance) referrer;
                    if (referrer1.getSchemClass().isa(ReactomeJavaConstants.Event)) {
                        GKInstance species = (GKInstance) referrer1.getAttributeValue(ReactomeJavaConstants.species);
                        if (species != null && !species.getDBID().equals(186860L)) {
                            System.out.println(event + " <- " + referrer1);
                        }
                    }
                }
            }
            count ++;
            if (count % 10 == 0)
                System.out.println("Checked: " + count);
        }
        System.out.println("Finished check!");
    }
    
    public void testGetSpecies() throws Exception {
        SlicingEngine engine = new SlicingEngine();
        engine.setSpeciesFileName("Species.txt");
        List speciesIDs = engine.getSpeciesIDs();
        System.out.println("Species: " + speciesIDs);
    }
}
