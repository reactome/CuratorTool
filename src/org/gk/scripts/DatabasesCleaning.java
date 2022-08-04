/*
 * Created on Dec 6, 2011
 *
 */
package org.gk.scripts;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.database.ReverseAttributePane;
import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.util.FileUtilities;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * Some programs to clean-up databases at reactomedev.
 * @author gwu
 *
 */
public class DatabasesCleaning {
    
    public DatabasesCleaning() {
    }
    
    @Test
    public void checkCatalystActivities() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                                            "test_gk_central_efs_new",
                                            "root",
                                            "macmysql01");
        Collection<GKInstance> cas = dba.fetchInstancesByClass(ReactomeJavaConstants.CatalystActivity);
        dba.loadInstanceAttributeValues(cas, new String[] {
                ReactomeJavaConstants.physicalEntity,
                ReactomeJavaConstants.activeUnit
        });
        int casWithComplex = 0;
        int casWithComplexAndActiveUnit = 0;
        for (GKInstance ca : cas) {
            GKInstance pe = (GKInstance) ca.getAttributeValue(ReactomeJavaConstants.physicalEntity);
            if (pe == null || !(pe.getSchemClass().isa(ReactomeJavaConstants.Complex)))
                continue;
            GKInstance species = (GKInstance) pe.getAttributeValue(ReactomeJavaConstants.species);
            if (species == null || !species.getDisplayName().equals("Homo sapiens"))
                continue;
            casWithComplex ++;
            GKInstance activeUnit = (GKInstance) ca.getAttributeValue(ReactomeJavaConstants.activeUnit);
            if (activeUnit != null)
                casWithComplexAndActiveUnit ++;
        }
        System.out.println("casWithComplex: " + casWithComplex);
        System.out.println("casWithComplexAndActiveUnit: " + casWithComplexAndActiveUnit);
        double ratio = casWithComplexAndActiveUnit / (double) casWithComplex;
        System.out.println("Ratio: " + ratio);
    }
    
    @Test
    public void countRNAGenes() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_091218",
                "root",
                "macmysql01");
        Collection<GKInstance> ewases = dba.fetchInstancesByClass(ReactomeJavaConstants.EntityWithAccessionedSequence);
        int count = 0;
        Set<GKInstance> refRNAs = new HashSet<>();
        for (GKInstance inst : ewases) {
            GKInstance ref = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            if (ref == null) {
                continue;
            }
            if (!ref.getSchemClass().isa(ReactomeJavaConstants.ReferenceRNASequence))
                continue;
            GKInstance species = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.species);
            if (!species.getDisplayName().equals("Homo sapiens"))
                continue;
            if (inst.getDisplayName().contains("mRNA"))
                continue;
            System.out.println(inst);
            count ++;
            refRNAs.add(ref);
        }
        System.out.println("Total: " + count);
        System.out.println("Total ReferenceRNAs used: " + refRNAs.size());
    }
    
    @Test
    public void checkReferenceMoleculeDuplications() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
                "gk_central",
                "authortool", 
                "T001test");
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.ReferenceMolecule);
        c.addAll(dba.fetchInstancesByClass(ReactomeJavaConstants.ReferenceGroup));
        System.out.println("Total instances: " + c.size());
        SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.ReferenceEntity).getAttribute(ReactomeJavaConstants.identifier);
        dba.loadInstanceAttributeValues(c, att);
        
        Map<String, Set<GKInstance>> idToInst = new HashMap<>();
        for (GKInstance i : c) {
            String id = (String) i.getAttributeValue(ReactomeJavaConstants.identifier);
            if (id == null)
                continue;
            idToInst.compute(id, (key, set) -> {
                if (set == null)
                    set = new HashSet<>();
                set.add(i);
                return set;
            });
        }
        Set<String> duplicatedIds = idToInst.keySet().stream().filter(id -> idToInst.get(id).size() > 1).collect(Collectors.toSet());
        idToInst.keySet().retainAll(duplicatedIds);
        System.out.println("Total duplicated ids in ReferenceMolecule/Group: " + idToInst.size());
        System.out.println("ChEBI_ID\tDB_ID\tName\tClass\tReferers");
        for (String id : idToInst.keySet()) {   
            Set<GKInstance> set = idToInst.get(id);
            for (GKInstance inst : set) {
                Set<GKInstance> referrers = new HashSet<>();
                Collection<GKInstance> rs = inst.getReferers(ReactomeJavaConstants.referenceEntity);
                if (rs != null)
                    referrers.addAll(rs);
                rs = inst.getReferers(ReactomeJavaConstants.modification);
                if (rs != null)
                    referrers.addAll(rs);
                System.out.println(id + "\t" +
                                   inst.getDBID() + "\t" + 
                                   inst.getDisplayName() + "\t" + 
                                   inst.getSchemClass().getName() + "\t" + 
                                   referrers);
            }
        }
    }
    
    @Test
    public void checkComplexInferred() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_current_ver50",
                                            "root", 
                                            "macmysql01");
        Collection<GKInstance> complexes = dba.fetchInstancesByClass(ReactomeJavaConstants.PhysicalEntity);
        dba.loadInstanceAttributeValues(complexes, new String[]{ReactomeJavaConstants.inferredTo});
        int count = 0;
        int total = 0;
        for (GKInstance complex : complexes) {
            List<GKInstance> list = complex.getAttributeValuesList(ReactomeJavaConstants.inferredTo);
            if (list.size() > 0)
                total ++;
            Set<GKInstance> set = new HashSet<GKInstance>(list);
            if (list.size() > set.size()) {
                System.out.println(complex);
                count ++;
            }
        }
        System.out.println(count);
        System.out.println("Total complexes that have been inferred: " + total);
    }
    
    @Test
    public void checkInstancesInClasses() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_092412",
                                            "root", 
                                            "macmysql01");
        Map<String, Long> classToCounts = dba.getAllInstanceCounts();
        long total = 0;
        for (String clsName : classToCounts.keySet()) {
            Long count = classToCounts.get(clsName);
            total += count;
            System.out.println(clsName + ": " + count);
        }
        System.out.println("Total: " + total);
    }
    
    @Test
    public void searchForInnodbs() throws Exception {
        Connection connect = connect();
        Statement stat = connect.createStatement();
        String query = "SHOW DATABASES";
        ResultSet results = stat.executeQuery(query);
        List<String> dbNames = new ArrayList<String>();
        while (results.next()) {
            String dbName = results.getString(1);
            dbNames.add(dbName);
        }
        results.close();
        System.out.println("Total dbs: " + dbNames.size());
        
        for (String dbName : dbNames) {
            if (dbName.endsWith(";") || dbName.endsWith(")") ||
                dbName.equals("test_slice_30"))
                continue;
            System.out.println(dbName);
            query = "USE " + dbName;
            stat.executeUpdate(query);
            query = "SHOW TABLE STATUS";
            results = stat.executeQuery(query);
            int count = 0;
            while (results.next()) {
                count ++;
                String engine = results.getString(2);
                if (engine == null)
                    continue;
                if (engine.equalsIgnoreCase("innodb")) {
                    System.out.println("\tIs Innodb");
                    break;
                }
            }
            if (count == 0)
                System.out.println("\tIs empty!");
            results.close();
        }
        stat.close();
        connect.close();
    }
    
    @Test
    public void dropInnodbs() throws Exception {
        String dirName = "/Users/gwu/Documents/wgm/work/reactome/";
//        String fileName = dirName + "reactomedev_db_list_120611.txt";
//        String fileName = dirName + "reactomedev_db_list_120611_droplist_1.txt";
//        String fileName = dirName + "reactomedev_db_list_120611_droplist_2.txt";
//        String fileName = dirName + "reactomedev_db_list_120611_droplist_3.txt";
        String fileName = dirName + "reactomedev_db_list_120611_droplist_5.txt";
        FileUtilities fu = new FileUtilities();
        fu.setInput(fileName);
        String line = null;
        List<String> dbNames = new ArrayList<String>();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("( )+");
//            System.out.println(tokens[1]);
//            dbNames.add(tokens[1]);
            System.out.println(tokens[0]);
            dbNames.add(tokens[0]);
        }
        fu.close();
//        if (true)
//            return;
        Connection connect = connect();
        Statement stat = connect.createStatement();
        for (String dbName : dbNames) {
            System.out.println("Drop " + dbName);
            String query = "DROP DATABASE " + dbName;
            int result = stat.executeUpdate(query);
            System.out.println("Result: " + result);
        }
        stat.close();
        connect.close();
    }
    
    @Test
    public void dropTestDatabases() throws Exception {
        Connection connect = connect();
        String query = "SHOW DATABASES";
        Statement stat = connect.createStatement();
        ResultSet results = stat.executeQuery(query);
        List<String> testDatabases = new ArrayList<String>();
        while (results.next()) {
            String name = results.getString(1);
            if (name.startsWith("test_reactome_3232")) {
//            if (name.startsWith("test_") && name.contains("slice")) {
                System.out.println(name);
                testDatabases.add(name);
            }
        }
        results.close();
        System.out.println("Total test databases: " + testDatabases.size());
        for (String name : testDatabases) {
            if (name.equals("test_slice)") || name.endsWith(";") || name.equals("test_slice_30"))
                continue; // Cannot work
            System.out.println("Dropping " + name + "...");
            query = "DROP DATABASE " + name;
            int result = stat.executeUpdate(query);
            System.out.println("Dropped database: " + name);
//            break;
        }
        stat.close();
        connect.close();
    }
    
    @Test
    public void testDropDb() throws Exception {
        Connection connect = connect();
        String query = "SHOW DATABASES";
        Statement stat = connect.createStatement();
        ResultSet results = stat.executeQuery(query);
        while (results.next()) {
            String name = results.getString(1);
            System.out.println(name);
        }
        results.close();
        query = "DROP DATABASE test_slice_32h";
        int result = stat.executeUpdate(query);
        System.out.println("Drop database: " + result);
        stat.close();
        connect.close();
    }
    
    private Connection connect() throws SQLException {
        installDriver();
        String connectionStr = "jdbc:mysql://reactome.oicr.on.ca:3306/test_reactome_39";
        Properties prop = new Properties();
        prop.setProperty("user", "authortool");
        prop.setProperty("password", "T001test");
        prop.setProperty("autoReconnect", "true");
        // The following two lines have been commented out so that
        // the default charset between server and client can be used.
        // Right now latin1 is used in both server and gk_central database.
        // If there is any non-latin1 characters are used in the application,
        // they should be converted to latin1 automatically (not tested).
        //prop.setProperty("useUnicode", "true");
        //prop.setProperty("characterEncoding", "UTF-8");
        prop.setProperty("useOldUTF8Behavior", "true");
        // Some dataTime values are 0000-00-00. This should be convert to null.
        prop.setProperty("zeroDateTimeBehavior", "convertToNull");
        // Please see http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html
        // for new version of JDBC driver: 5.1.7. This is very important, esp. in the slicing tool. Otherwise,
        // an out of memory exception will be thrown very easy even with a very large heap size assignment.
        // However, apparently this property can work with 5.0.8 version too.
        prop.setProperty("dontTrackOpenResources", "true");
//      // test code
//      prop.put("profileSQL", "true");
//      prop.put("slowQueryThresholdMillis", "0");
//      prop.put("logSlowQueries", "true");
//      prop.put("explainSlowQueries", "true");
        
        Connection conn = DriverManager.getConnection(connectionStr, prop);
        return conn;
        //conn = DriverManager.getConnection(connectionStr, username, password);
    }
    
    private void installDriver() {
        String dbDriver = "com.mysql.jdbc.Driver";
        try {
            Class.forName(dbDriver).newInstance();
        } catch (Exception e) {
            System.err.println("Failed to load database driver: " + dbDriver);
            e.printStackTrace();
        }
    }
    
    /**
     * There are 393 instances starting with SO: which have not been used at all! These instances
     * will be deleted using this method.
     * @throws Exception
     */
    @Test
    public void deleteUnwantedSOInstances() throws Exception {
        PersistenceAdaptor dba = new MySQLAdaptor("reactomedev.oicr.on.ca",
                                            "gk_central",
                                            "authortool",
                                            "T001test");
        Collection<GKInstance> soInstances = dba.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseIdentifier,
                                                                          ReactomeJavaConstants._displayName, 
                                                                          "like", 
                                                                          "SO:%");
        System.out.println("Total SO Instances: " + soInstances.size());
        // An internal check 
//        GKInstance so = dba.fetchInstance(5L);
//        soInstances.add(so);
        ReverseAttributePane helper = new ReverseAttributePane();
        for (GKInstance inst : soInstances) {
            Map<String, List<GKInstance>> referrers = helper.getReferrersMapForDBInstance(inst);
            if (referrers != null && referrers.size() > 0)
                throw new IllegalStateException(inst + " has referrers: " + referrers.size());
        }
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                for (GKInstance inst : soInstances) {
                    ((Neo4JAdaptor) dba).deleteInstance(inst, tx);
                }
                tx.commit();
            }
        } else {
            // MySQL
            try {
                ((MySQLAdaptor) dba).startTransaction();
                for (GKInstance inst : soInstances) {
                    ((MySQLAdaptor) dba).deleteInstance(inst);
                }
                ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                ((MySQLAdaptor) dba).rollback();
            }
        }
        dba.cleanUp();
    }
}
