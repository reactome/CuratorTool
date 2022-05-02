/*
 * Created on Sep 11, 2012
 *
 */
package org.gk.scripts;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.SchemaClass;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * Methods related to database schema update should be placed here.
 * @author gwu
 *
 */
public class DatabaseSchemaUpdates {
    
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Provide three arguments: dbName, dbUser, and dbPwd!");
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
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost", dbName, dbUser, dbPwd);
        Collection<GKInstance> ecInstances = dba.fetchInstancesByClass(ReactomeJavaConstants.EntityCompartment);
        System.out.println("Total EntityCompartment: " + ecInstances.size());
        SchemaClass compartment = dba.getSchema().getClassByName(ReactomeJavaConstants.Compartment);
        Driver driver = dba.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
            Transaction tx = session.beginTransaction();
            for (GKInstance inst : ecInstances) {
                System.out.println("Handling " + inst);
                dba.loadInstanceAttributeValues(inst); // Load all attribute values
                inst.setSchemaClass(compartment);
                dba.updateInstance(inst, tx);
            }
            tx.commit();
        }
    }
    
    
    @Test
    public void copyAccessionToIdentifierForSO() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("reactomedev.oicr.on.ca", 
                                            "gk_central", 
                                            "authortool", 
                                            "T001test");
        Collection<GKInstance> instances = dba.fetchInstancesByClass(ReactomeJavaConstants.SequenceOntology);
        System.out.println("Total SequenceOntology instances: " + instances.size());
        Driver driver = dba.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
            Transaction tx = session.beginTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                                                                   defaultPersonId,
                                                                   true, tx);
            for (GKInstance inst : instances) {
                String accession = (String) inst.getAttributeValue(ReactomeJavaConstants.accession);
                if (accession == null)
                    continue; // Just in case
                inst.setAttributeValue(ReactomeJavaConstants.identifier,
                                       accession);
                dba.updateInstanceAttribute(inst,
                                            ReactomeJavaConstants.identifier, tx);
                // Have to load attribute value first
                inst.getAttributeValuesList(ReactomeJavaConstants.modified);
                inst.addAttributeValue(ReactomeJavaConstants.modified, 
                                       defaultIE);
                dba.updateInstanceAttribute(inst,
                                            ReactomeJavaConstants.modified, tx);
            }
            tx.commit();
        }
    }
    
}
