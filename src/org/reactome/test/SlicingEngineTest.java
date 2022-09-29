/*
 * Created on Feb 2, 2006
 *
 */
package org.reactome.test;

import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
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
    }

    @Test
    public void checkTest() throws Exception {
        check(false);
        check(true);
    }

    private void check(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost", "test_slice_54a", "root", "macmysql01");
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
