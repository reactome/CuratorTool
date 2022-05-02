/*
 * Created on Apr 25, 2012
 *
 */
package org.gk.scripts;

import java.io.InputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gk.database.AttributeEditConfig;
import org.gk.database.DefaultInstanceEditHelper;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.util.GKApplicationUtilities;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.w3c.dom.Document;

/**
 * @author gwu
 *
 */
public class ScriptUtilities {
    public static final Long GUANMING_WU_DB_ID = 140537L; // For Guanming Wu at CSHL
    
    public static GKInstance getHomoSapiens(Neo4JAdaptor dba) throws Exception {
        Long dbId = 48887L;
        GKInstance inst = dba.fetchInstance(dbId);
        return inst;
    }
    
    /**
     * Use to control some behaviors.
     * @throws Exception
     */
    public static void setUpAttrinuteEditConfig() throws Exception {
        // Need some configuration
        InputStream metaConfig = GKApplicationUtilities.getConfig("curator.xml");
        if (metaConfig == null)
            return;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(metaConfig);
        AttributeEditConfig.getConfig().loadConfig(document);
    }
    
    /**
     * A method to update instance name, _displayName, and adding an IE
     * to the updated instance's modified slot.
     * @param dba
     * @param toBeUpdated
     * @throws SQLException
     * @throws Exception
     */
    public static void updateInstanceNames(Neo4JAdaptor dba, List<GKInstance> toBeUpdated) throws Exception {
        // Update instances to the database
        Driver driver = dba.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
            Transaction tx = session.beginTransaction();
            Long defaultPersonId = 140537L; // For Guanming Wu at CSHL
            GKInstance newIE = ScriptUtilities.createDefaultIE(dba, defaultPersonId, true, tx);
            int count = 0;
            for (GKInstance instance : toBeUpdated) {
                System.out.println(count + ": " + instance);
                // Have to call this first to get the list
                instance.getAttributeValue(ReactomeJavaConstants.modified);
                instance.addAttributeValue(ReactomeJavaConstants.modified, newIE);
                dba.updateInstanceAttribute(instance, ReactomeJavaConstants.modified, tx);
                if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.name))
                    dba.updateInstanceAttribute(instance, ReactomeJavaConstants.name, tx);
                dba.updateInstanceAttribute(instance, ReactomeJavaConstants._displayName, tx);
                count ++;
            }
            tx.commit();
        }
    }
    
    public static String getDate() {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return now;
    }
    
    public static void addIEToModified(GKInstance inst,
                                       GKInstance ie,
                                       Neo4JAdaptor dba,
                                       Transaction tx) throws Exception {
        inst.getAttributeValuesList(ReactomeJavaConstants.modified);
        inst.addAttributeValue(ReactomeJavaConstants.modified, 
                                     ie);
        dba.updateInstanceAttribute(inst,
                                    ReactomeJavaConstants.modified, tx);
    }
    
    public static GKInstance createDefaultIE(Neo4JAdaptor dba,
                                             Long defaultPersonId,
                                             boolean needStore,
                                             Transaction tx) throws Exception {
        GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
        DefaultInstanceEditHelper ieHelper = new DefaultInstanceEditHelper();
        GKInstance newIE = ieHelper.createDefaultInstanceEdit(defaultPerson);
        newIE.addAttributeValue(ReactomeJavaConstants.dateTime,  
                                GKApplicationUtilities.getDateTime());
        InstanceDisplayNameGenerator.setDisplayName(newIE);
        if (needStore)
            dba.storeInstance(newIE, tx);
        return newIE;
    }
    
    /**
     * Get the actual author for an instance.
     * @param inst
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static GKInstance getAuthor(GKInstance inst) throws Exception {
        List<GKInstance> list = inst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (list != null) {
            for (GKInstance ie : list) {
                if (ie.getDisplayName().startsWith("Wu, G")) { // Escape Guanming Wu
                    continue;
                }
                return ie;
            }
        }
        return (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
    }
    
}
