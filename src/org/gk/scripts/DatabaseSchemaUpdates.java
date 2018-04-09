/*
 * Created on Sep 11, 2012
 *
 */
package org.gk.scripts;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.junit.Test;

/**
 * Methods related to database schema update should be placed here.
 * @author gwu
 *
 */
public class DatabaseSchemaUpdates {
    
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.print("Provide three arguments: dbName, dbUser, and dbPwd!");
            return;
        }
        DatabaseSchemaUpdates updater = new DatabaseSchemaUpdates();
        updater.changeEntityCompartmentToCompartment(args[0],
                                                     args[1],
                                                     args[2]);
    }
    
    public DatabaseSchemaUpdates() {
    }
    
    /**
     * We want to delete the Compartment class and then change EntityCompartment class name
     * to Compartment. However, in order to do this, we need to change all EntityCompartment
     * instances to Compartment and then delete EntityCompartment class.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void changeEntityCompartmentToCompartment(String dbName,
                                                     String dbUser,
                                                     String dbPwd) throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost", dbName, dbUser, dbPwd);
        Collection<GKInstance> ecInstances = dba.fetchInstancesByClass(ReactomeJavaConstants.EntityCompartment);
        System.out.println("Total EntityCompartment: " + ecInstances.size());
        SchemaClass compartment = dba.getSchema().getClassByName(ReactomeJavaConstants.Compartment);
        dba.startTransaction();
        for (GKInstance inst : ecInstances) {
            System.out.println("Handling " + inst);
            dba.fastLoadInstanceAttributeValues(inst); // Load all attribute values
            inst.setSchemaClass(compartment);
            dba.updateInstance(inst);
        }
        try {
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
        }
    }
    
    
    @Test
    public void copyAccessionToIdentifierForSO() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("reactomedev.oicr.on.ca", 
                                            "gk_central", 
                                            "authortool", 
                                            "T001test");
        Collection<GKInstance> instances = dba.fetchInstancesByClass(ReactomeJavaConstants.SequenceOntology);
        System.out.println("Total SequenceOntology instances: " + instances.size());
        try {
            dba.startTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                                                                   defaultPersonId,
                                                                   true);
            for (GKInstance inst : instances) {
                String accession = (String) inst.getAttributeValue(ReactomeJavaConstants.accession);
                if (accession == null)
                    continue; // Just in case
                inst.setAttributeValue(ReactomeJavaConstants.identifier,
                                       accession);
                dba.updateInstanceAttribute(inst,
                                            ReactomeJavaConstants.identifier);
                // Have to load attribute value first
                inst.getAttributeValuesList(ReactomeJavaConstants.modified);
                inst.addAttributeValue(ReactomeJavaConstants.modified, 
                                       defaultIE);
                dba.updateInstanceAttribute(inst,
                                            ReactomeJavaConstants.modified);
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
        }
    }
    
}
