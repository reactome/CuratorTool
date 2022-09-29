package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.util.FileUtilities;

public class HumanGenesDumper {
    
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Provide use_neo4j argument");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            return;
        }
        PersistenceAdaptor dba;
        boolean useNeo4J = Boolean.parseBoolean(args[1]);
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("localhost",
                    "gk_central_091119",
                    "root",
                    "macmysql01");
        GKInstance human = ScriptUtilities.getHomoSapiens(dba);
        Collection<GKInstance> humanGenes = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct,
                                                                         ReactomeJavaConstants.species, 
                                                                         "=", 
                                                                         human);
        dba.loadInstanceAttributeValues(humanGenes, new String[]{ReactomeJavaConstants.geneName});
        Set<String> geneSymbols = new HashSet<>();
        for (GKInstance inst : humanGenes) {
            List<String> geneName = inst.getAttributeValuesList(ReactomeJavaConstants.geneName);
            geneSymbols.addAll(geneName);
        }
        System.out.println("Total human genes: " + geneSymbols.size());
        geneSymbols.stream().sorted().forEach(System.out::println);
        
        // Output all Reactome human genes
        FileUtilities fu = new FileUtilities();
        fu.setOutput("HumanGenesInReactome_091119.txt");
        List<String> geneList = new ArrayList<>(geneSymbols);
        Collections.sort(geneList);
        for (String gene : geneList)
            fu.printLine(gene);
        fu.close();
    }

}
