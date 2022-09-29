package org.gk.scripts;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is used to generate DOID to its anntoated Entity and Event mapping file.
 * The format is like this:
 * DOID\tDOName\tReactomeStableId\tReactomeName\tReactomeType.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class DOIDMapperFileGenerator {

    public DOIDMapperFileGenerator() {
        
    }


    @Test
    public void generateFileTest() throws Exception {
        generateFile(false);
        generateFile(true);
    }

    private void generateFile(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost",
                    "test_slice_64",
                    "root",
                    "macmysql01");
        Collection<GKInstance> diseases = dba.fetchInstancesByClass(ReactomeJavaConstants.Disease);
        System.out.println("Total disease: " + diseases.size());
        FileUtilities fu = new FileUtilities();
        String fileName = "/Users/wug/temp/DOIDMapping_Release_64_human.txt";
        fu.setOutput(fileName);
        fu.printLine("DOID\tDOName\tReactomeStableId\tReactomeName\tReactomeType");
        for (GKInstance disease : diseases) {
            Collection<GKInstance> referrers = disease.getReferers(ReactomeJavaConstants.disease);
            if (referrers == null || referrers.size() == 0)
                continue;
            String id = (String) disease.getAttributeValue(ReactomeJavaConstants.identifier);
            String name = (String) disease.getAttributeValue(ReactomeJavaConstants.name);
            referrers.forEach(r -> {
                try {
                    GKInstance stableId = (GKInstance) r.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                    String rStableId = (String) stableId.getAttributeValue(ReactomeJavaConstants.identifier);
                    fu.printLine(id + "\t" +
                            name + "\t" + 
                            rStableId + "\t" + 
                            r.getDisplayName() + "\t" + 
                            r.getSchemClass().getName());
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            });
        }
        fu.close();
    }
    
}
