/*
 * Created on Jul 6, 2016
 *
 */
package org.gk.scripts;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.FileUtilities;

/**
 * This script is to used to sort Regulation into GeneRegulation based on the following criteria:
 * 1). A gene regulation should regulate a blackbox event only.
 * 2). The regulated blackbox event should be only one output only. It may have one input with name ending with "gene".
 * 3). A gene regulation may be a PositiveRegulation or NegativeRegulation based on the original annotations.
 * @author gwu
 *
 */
public class GeneRegulationSorter {
    private MySQLAdaptor dba;
    private String fileName;
    
    /**
     * Default constructor.
     */
    public GeneRegulationSorter() {
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage java org.gk.scripts.GeneRegulationSorter dbHost dbName dbUser dbPwd outputFileName");
            System.exit(0);
        }
        MySQLAdaptor dba = new MySQLAdaptor(args[0], 
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
    
    public void setDBA(MySQLAdaptor dba) {
        this.dba = dba;
    }
    
    public MySQLAdaptor getDBA() throws Exception {
        if (dba != null)
            return dba;
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_063016_new_schema",
                                            "root",
                                            "macmysql01");
        return dba;
    }
    
    public void updateRegulations() throws Exception {
        MySQLAdaptor dba = getDBA();
        Map<GKInstance, String> regulationToNewType = sort(dba);
        // Update the database
        boolean needTransaction = dba.supportsTransactions();
        try {
            if (needTransaction)
                dba.startTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                                                                   defaultPersonId,
                                                                   true);
            for (GKInstance regulation : regulationToNewType.keySet()) {
                dba.fastLoadInstanceAttributeValues(regulation);
                String newType = regulationToNewType.get(regulation);
                SchemaClass newCls = dba.getSchema().getClassByName(newType);
                regulation.setSchemaClass(newCls);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                dba.updateInstance(regulation);
            }
            if (needTransaction)
                dba.commit();
            output(regulationToNewType, fileName);
        }
        catch(Exception e) {
            dba.rollback();
            throw e;
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
    
    private Map<GKInstance, String> sort(MySQLAdaptor dba) throws Exception {
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
        if(!(event.getSchemClass().isa(ReactomeJavaConstants.BlackBoxEvent)))
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
