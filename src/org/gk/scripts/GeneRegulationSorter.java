/*
 * Created on Jul 6, 2016
 *
 */
package org.gk.scripts;

import java.util.*;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.FileUtilities;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * This script is to used to sort Regulation into GeneRegulation based on the following criteria:
 * 1). A gene regulation should regulate a blackbox event only.
 * 2). The regulated blackbox event should be only one output only. It may have one input with name ending with "gene".
 * 3). A gene regulation may be a PositiveRegulation or NegativeRegulation based on the original annotations.
 *
 * @author gwu
 */
public class GeneRegulationSorter {
    private PersistenceAdaptor dba;
    private String fileName;

    /**
     * Default constructor.
     */
    public GeneRegulationSorter() {
    }

    /**
     * Use this method to generate some numbers for grant writing.
     *
     * @throws Exception
     */
    @Test
    public void countGeneRegulations() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_102319",
                "root",
                "macmysql01");
        Set<String> tfs = new HashSet<>();
        Set<GKInstance> rles = new HashSet<>();
        Collection<GKInstance> geneRegulations = dba.fetchInstancesByClass(ReactomeJavaConstants.PositiveGeneExpressionRegulation);
        countGeneRegulations(tfs, rles, geneRegulations);
        geneRegulations = dba.fetchInstancesByClass(ReactomeJavaConstants.NegativeGeneExpressionRegulation);
        countGeneRegulations(tfs, rles, geneRegulations);
        tfs.stream().sorted().forEach(System.out::println);
        System.out.println("Total genes: " + tfs.size());
        System.out.println("Total reactions: " + rles.size());
    }

    private void countGeneRegulations(Set<String> tfs, Set<GKInstance> rles, Collection<GKInstance> geneRegulations)
            throws Exception, InvalidAttributeException {
        for (GKInstance geneRegulation : geneRegulations) {
            Collection<GKInstance> referrers = geneRegulation.getReferers(ReactomeJavaConstants.regulatedBy);
            if (referrers != null)
                rles.addAll(referrers);
            GKInstance regulator = (GKInstance) geneRegulation.getAttributeValue(ReactomeJavaConstants.regulator);
            if (regulator == null)
                continue;
            Set<GKInstance> refGenes = InstanceUtilities.grepReferenceEntitiesForPE(regulator);
            for (GKInstance refGene : refGenes) {
                if (refGene.getSchemClass().isValidAttribute(ReactomeJavaConstants.geneName)) {
                    GKInstance species = (GKInstance) refGene.getAttributeValue(ReactomeJavaConstants.species);
                    if (species == null || !species.getDisplayName().equals("Homo sapiens"))
                        continue;
                    String geneName = (String) refGene.getAttributeValue(ReactomeJavaConstants.geneName);
                    if (geneName != null)
                        tfs.add(geneName);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Usage java org.gk.scripts.GeneRegulationSorter dbHost dbName dbUser dbPwd outputFileName use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");

            System.exit(0);
        }
        PersistenceAdaptor dba = null;
        boolean useNeo4J = Boolean.parseBoolean(args[5]);
        if (useNeo4J)
            dba = new Neo4JAdaptor(args[0],
                    args[1],
                    args[2],
                    args[3]);
        else
            dba = new MySQLAdaptor(args[0],
                    args[1],
                    args[2],
                    args[3]);
        GeneRegulationSorter sorter = new GeneRegulationSorter();
        sorter.setDBA(dba);
        sorter.setFileName(args[4]);
        sorter.updateRegulations();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setDBA(PersistenceAdaptor dba) {
        this.dba = dba;
    }

    public PersistenceAdaptor getDBA() throws Exception {
        if (dba != null)
            return dba;
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_063016_new_schema",
                "root",
                "macmysql01");
        return dba;
    }

    public void updateRegulations() throws Exception {
        PersistenceAdaptor dba = getDBA();
        Map<GKInstance, String> regulationToNewType = sort(dba);
        // Update the database
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        defaultPersonId,
                        true, tx);
                for (GKInstance regulation : regulationToNewType.keySet()) {
                    dba.loadInstanceAttributeValues(Collections.singleton(regulation));
                    String newType = regulationToNewType.get(regulation);
                    SchemaClass newCls = dba.getSchema().getClassByName(newType);
                    regulation.setSchemaClass(newCls);
                    regulation.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    ((Neo4JAdaptor) dba).updateInstance(regulation, tx);
                }
                tx.commit();
                output(regulationToNewType, fileName);
            }
        } else {
            // MySQL
            boolean needTransaction = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (needTransaction)
                    ((MySQLAdaptor) dba).startTransaction();
                Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        defaultPersonId,
                        true, null);
                for (GKInstance regulation : regulationToNewType.keySet()) {
                    ((MySQLAdaptor) dba).fastLoadInstanceAttributeValues(regulation);
                    String newType = regulationToNewType.get(regulation);
                    SchemaClass newCls = dba.getSchema().getClassByName(newType);
                    regulation.setSchemaClass(newCls);
                    regulation.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    ((MySQLAdaptor) dba).updateInstance(regulation);
                }
                if (needTransaction)
                    ((MySQLAdaptor) dba).commit();
                output(regulationToNewType, fileName);
            }
            catch(Exception e) {
                ((MySQLAdaptor) dba).rollback();
                throw e;
            }
        }
    }

    private void output(Map<GKInstance, String> regulationToNewType,
                        String fileName) throws Exception {
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine("DB_ID\tDisplayName\tCurrentType\tNewType");
        for (GKInstance regulation : regulationToNewType.keySet()) {
            String newType = regulationToNewType.get(regulation);
            fu.printLine(regulation.getDBID() + "\t" +
                    regulation.getDisplayName() + "\t" +
                    regulation.getSchemClass().getName() + "\t" +
                    newType);
        }
        fu.close();
    }

    private Map<GKInstance, String> sort(PersistenceAdaptor dba) throws Exception {
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.Regulation).getAttribute(ReactomeJavaConstants.regulatedEntity);
        dba.loadInstanceAttributeValues(regulations, att);
        att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.modified);
        dba.loadInstanceAttributeValues(regulations, att);
        Map<GKInstance, String> regulationToNewType = new HashMap<GKInstance, String>();
        for (GKInstance regulation : regulations) {
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null)
                continue;
            if (!isGeneRegulatedEvent(regulatedEntity))
                continue;
            String newType = regulation.getSchemClass().isa(ReactomeJavaConstants.NegativeRegulation) ?
                    ReactomeJavaConstants.NegativeGeneExpressionRegulation :
                    ReactomeJavaConstants.PositiveGeneExpressionRegulation;
            regulationToNewType.put(regulation, newType);
        }
        return regulationToNewType;
    }

    private boolean isGeneRegulatedEvent(GKInstance event) throws Exception {
        if (!(event.getSchemClass().isa(ReactomeJavaConstants.BlackBoxEvent)))
            return false;
        GKInstance species = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.species);
        if (species == null || !species.getDisplayName().equals("Homo sapiens"))
            return false; // Work with human events only for the time being
        List<GKInstance> inputs = event.getAttributeValuesList(ReactomeJavaConstants.input);
        List<GKInstance> outputs = event.getAttributeValuesList(ReactomeJavaConstants.output);
        if (inputs != null && inputs.size() > 1)
            return false; // One or null input
        if (outputs == null || outputs.size() != 1)
            return false; // One and only one output
        GKInstance input = null;
        if (inputs != null && inputs.size() == 1)
            input = inputs.get(0);
        if (input != null) {
            // Check gene expression
            String inputName = (String) input.getAttributeValue(ReactomeJavaConstants.name);
            if (!inputName.toLowerCase().endsWith("gene"))
                return false; // If there is an input, its name should be ended with gene
        }
        return true;
    }

}
