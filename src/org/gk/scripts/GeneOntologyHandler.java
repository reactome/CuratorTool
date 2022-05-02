package org.gk.scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * Methods related to GO should be placed here.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class GeneOntologyHandler {
    
    public GeneOntologyHandler() {
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage java org.gk.scripts.GeneOntologyHandler dbHost dbName dbUser dbPwd");
            return;
        }
        Neo4JAdaptor dba = new Neo4JAdaptor(args[0],
                                            args[1],
                                            args[2], 
                                            args[3]);
        GeneOntologyHandler handler = new GeneOntologyHandler();
//        Neo4JAdaptor dba = handler.getDBA();
        handler.fillSurroundedBySlots(dba);
    }
    
    public void fillSurroundedBySlots(Neo4JAdaptor dba) throws Exception {
        Map<String, Set<String>> idToSurrounedByIds = loadGOSurrounedBy();
        List<GKInstance> toBeUpdated = new ArrayList<>();
        for (String id : idToSurrounedByIds.keySet()) {
            // This is not in gk_central yet
            if (id.equals("GO:0062113"))
                continue;
            GKInstance compartment = fetchInstanceById(id, dba);
            Set<String> otherIds = idToSurrounedByIds.get(id);
            for (String otherId : otherIds) {
                GKInstance otherCompartment = fetchInstanceById(otherId, dba);
                compartment.addAttributeValue(ReactomeJavaConstants.surroundedBy,
                                              otherCompartment);
            }
            toBeUpdated.add(compartment);
        }
        Driver driver = dba.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
            Transaction tx = session.beginTransaction();
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                    ScriptUtilities.GUANMING_WU_DB_ID,
                    true, tx);
            for (GKInstance comp : toBeUpdated) {
                System.out.println("Updating " + comp + "...");
                dba.updateInstanceAttribute(comp, ReactomeJavaConstants.surroundedBy, tx);
                ScriptUtilities.addIEToModified(comp, defaultIE, dba, tx);
            }
            tx.commit();
            System.out.println("Total updated: " + toBeUpdated.size());
        }
    }
    
    private Neo4JAdaptor getDBA() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost",
                                            "gk_central_041919",
                                            "root",
                                            "macmysql01");
        return dba;
    }
    
    private GKInstance fetchInstanceById(String id, Neo4JAdaptor dba) throws Exception {
        if (id.startsWith("GO:")) {
            id = id.substring(3);
        }
        Collection<GKInstance> list = dba.fetchInstanceByAttribute(ReactomeJavaConstants.GO_CellularComponent,
                                                             ReactomeJavaConstants.accession,
                                                             "=",
                                                             id);
        if (list.size() == 0 || list.size() > 1)
            throw new IllegalStateException("Have either no or more than one instance for " + id);
        return list.stream().findFirst().get();
    }
    
    private Map<String, Set<String>> loadGOSurrounedBy() throws IOException {
        Map<String, Set<String>> compToOthers = new HashMap<>();
        String fileName = "Surrounded_by_table_version2.tsv";
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.skip(1)
                  .map(line -> line.split("\t"))
                  .filter(tokens -> tokens.length > 4)
                  .forEach(tokens -> {
                      compToOthers.compute(tokens[0], (comp, set) -> {
                          if (set == null)
                              set = new HashSet<>();
                          set.add(tokens[3]);
                         return set;
                      });
                  });
            return compToOthers;
        }
    }
    
    /**
     * Generate a network for the surroundedBy relationships that can be viewed in Cytoscape.
     */
    @Test
    public void outputGOSurrounedBy() throws IOException {
        Map<String, Set<String>> compToOthers = loadGOSurrounedBy();
        System.out.println("Term1\tType\tTerm2");
        compToOthers.forEach((go, set) -> {
            set.stream().forEach(other -> System.out.println(go + "\t" + "surrounedBy\t" + other));
        });
    }

}
