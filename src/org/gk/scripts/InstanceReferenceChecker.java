/*
 * Created on Feb 8, 2005
 *
 */
package org.gk.scripts;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.GKSchema;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.junit.Test;
import org.neo4j.driver.*;

/**
 * A checker to see any instance reference has registered in DatabaseObject table
 * and its class in attribute table is the same as in DatabaseObject table.
 * @author wgm
 */
public class InstanceReferenceChecker {
    private StringBuffer errorMessage = new StringBuffer();
    private PersistenceAdaptor dba = null;

    public InstanceReferenceChecker() {

    }

//    public InstanceReferenceChecker(MySQLAdaptor dba) {
//        this.dba = dba;
//    }

    @Test
    public void checkReferenceMoleculeAndClassUsageTest() throws Exception {
        checkReferenceMoleculeAndClassUsage(false);
        checkReferenceMoleculeAndClassUsage(true);
    }

    private void checkReferenceMoleculeAndClassUsage(Boolean useNeo4J) throws Exception {
        PersistenceAdaptor dba;
        if (useNeo4J)
            dba = new Neo4JAdaptor("localhost",
                    "graph.db",
                    "neo4j",
                    "reactome");
        else
            dba = new MySQLAdaptor("reactomedev.oicr.on.ca",
                    "gk_central",
                    "authortool",
                    "T001test");
//        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.ReferenceGroupCount);
//        System.out.println("Total ReferenceMolecule: " + c.size());
        Collection<GKInstance> c1 = dba.fetchInstancesByClass(ReactomeJavaConstants.ReferenceMoleculeClass);
        System.out.println("Total ReferenceMoleculeClass: " + c1.size());
        Set<GKInstance> all = new HashSet<GKInstance>();
//        all.addAll(c);
        all.addAll(c1);
        System.out.println("ReferenceMolecule\tReferrers");
        for (GKInstance inst : all) {
            Collection<?> refAtts = inst.getSchemClass().getReferers();
            Set<GKInstance> allReferrers = new HashSet<GKInstance>();
            for (Iterator<?> it1 = refAtts.iterator(); it1.hasNext();) {
                GKSchemaAttribute att = (GKSchemaAttribute) it1.next();
                Collection<GKInstance> referrers = inst.getReferers(att);
                if (referrers != null)
                    allReferrers.addAll(referrers);
            }
            if (allReferrers.size() > 0) {
                System.out.println(inst + "\t" + join(allReferrers));
            }
        }
    }

    private String join(Set<GKInstance> instances) {
        StringBuilder builder = new StringBuilder();
        for (Iterator<GKInstance> it = instances.iterator(); it.hasNext();) {
            builder.append(it.next());
            if (it.hasNext())
                builder.append("\t");
        }
        return builder.toString();
    }

    public void setDBA(PersistenceAdaptor dba) {
        this.dba = dba;
    }

    public String check() throws Exception {
        if (dba == null) throw new IllegalStateException("InstanceReferenceChecker.check(): No database specified.");
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            GKSchema schema = (GKSchema) dba.getSchema();
            Set instanceAtts = new HashSet();
            GKSchemaAttribute att = null;
            for (Iterator it = schema.getOriginalAttributes().iterator(); it.hasNext(); ) {
                att = (GKSchemaAttribute) it.next();
                if (att.isInstanceTypeAttribute()) {
                    instanceAtts.add(att);
                }
            }
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                GKSchemaClass cls = null;
                StringBuilder query;
                Map attMap = new HashMap();
                Map tableNameMap = new HashMap();
                for (Iterator it = instanceAtts.iterator(); it.hasNext(); ) {
                    att = (GKSchemaAttribute) it.next();
                    cls = (GKSchemaClass) att.getOrigin();
                    System.out.println("Checking " + att.getName() + " in " + cls.getName() + "...");
                    query = new StringBuilder("MATCH (n:").append(cls.getName());
                    if (!att.isInstanceTypeAttribute()) {
                        query.append(") RETURN n.dbId, n.schemaClass");
                    } else {
                        query.append(")-[:" + att.getName() + "]->(f) RETURN f.dbId, f.schemaClass");
                    }

                    Result result = session.run(query.toString());
                    while (result.hasNext()) {
                        Record record = result.next();
                        long dbID = record.get(0).asLong();
                        String clsName = record.get(1).asString();
                        String tmpCls = (String) attMap.get(dbID);
                        if (tmpCls != null && !tmpCls.equals(clsName)) {
                            errorMessage.append(dbID + ", " + tmpCls + " in ");
                            errorMessage.append(tableNameMap.get(dbID) + " is different from ");
                            errorMessage.append(cls.getName());
                            errorMessage.append("\n");
                        } else {
                            attMap.put(dbID, clsName);
                            tableNameMap.put(dbID, cls.getName());
                        }
                    }
                }
                System.out.println("The size of the map: " + attMap.size());
                // Check DB_ID in the database
                for (Iterator it1 = attMap.keySet().iterator(); it1.hasNext(); ) {
                    Long dbID = (Long) it1.next();
                    query = new StringBuilder("MATCH (n{dbId:").append(dbID).append("}) RETURN n.schemaClass");
                    Result result = session.run(query.toString());
                    int c = 0;
                    while (result.hasNext()) {
                        c++;
                        String clsName = result.next().get(0).asString();
                        String tmpClsName = (String) attMap.get(dbID);
                        if (!tmpClsName.equals(clsName)) {
                            errorMessage.append(dbID + ", " + tmpClsName);
                            errorMessage.append(" in " + tableNameMap.get(dbID));
                            errorMessage.append(" is different in DatabaseObject (");
                            errorMessage.append(clsName);
                            errorMessage.append(")\n");
                        }
                    }
                    if (c == 0) {
                        errorMessage.append(dbID + ", " + attMap.get(dbID));
                        errorMessage.append(" in " + tableNameMap.get(dbID));
                        errorMessage.append(" is not in the DatabaseObject");
                        errorMessage.append("\n");
                    } else if (c > 1) {
                        errorMessage.append(dbID + " occurs in more than once in DatabaseObject");
                        errorMessage.append("\n");
                    }
                }
                // Print out an empty line
                System.out.println();
                if (errorMessage.length() == 0) {
                    System.out.println("Nothing Wrong!");
                } else {
                    FileWriter fileWriter = new FileWriter("error.txt");
                    BufferedWriter writer = new BufferedWriter(fileWriter);
                    writer.write(errorMessage.toString());
                    writer.close();
                    fileWriter.close();
                    System.out.println("Done. See errors in error.txt.");
                }
                return errorMessage.toString();
            }
        } else {
            // MySQL
            GKSchema schema = (GKSchema) dba.getSchema();
            Set instanceAtts = new HashSet();
            GKSchemaAttribute att = null;
            for (Iterator it = schema.getOriginalAttributes().iterator(); it.hasNext();) {
                att = (GKSchemaAttribute) it.next();
                if (att.isInstanceTypeAttribute()) {
                    instanceAtts.add(att);
                }
            }
            System.out.println("Instance Atts: " + instanceAtts.size());
            Connection conn = ((MySQLAdaptor) dba).getConnection();
            Statement stat = conn.createStatement();
            ResultSet resultSet = null;
            GKSchemaClass cls = null;
            Map attMap = new HashMap();
            Map tableNameMap = new HashMap();
            String query = null;
            for (Iterator it = instanceAtts.iterator(); it.hasNext();) {
                att = (GKSchemaAttribute) it.next();
                cls = (GKSchemaClass) att.getOrigin();
                System.out.println("Checking " + att.getName() + " in " + cls.getName() + "...");
                String tableName = null;
                if (!att.isMultiple()) {
                    tableName = cls.getName();
                }
                else {
                    tableName = cls.getName() + "_2_" + att.getName();
                }
                query = "SELECT distinct " + att.getName() + ", " + att.getName() + "_class FROM " + tableName;
                resultSet = stat.executeQuery(query);
                while (resultSet.next()) {
                    long dbID = resultSet.getLong(1);
                    String clsName = resultSet.getString(2);
                    if (dbID == 0 && clsName == null) {
                        //errorMessage.append("0, null occur for " + att.getName() + " in " + tableName);
                        //errorMessage.append("\n");
                        continue;
                    }
                    Long dbIDLong = new Long(dbID);
                    String tmpCls = (String) attMap.get(dbIDLong);
                    if (tmpCls != null && !tmpCls.equals(clsName)) {
                        errorMessage.append(dbID + ", " + tmpCls + " in ");
                        errorMessage.append(tableNameMap.get(dbIDLong) + " is different from ");
                        errorMessage.append(tableName);
                        errorMessage.append("\n");
                    }
                    else {
                        attMap.put(dbIDLong, clsName);
                        tableNameMap.put(dbIDLong, tableName);
                    }
                }
                resultSet.close();
            }
            stat.close();
            System.out.println("The size of the map: " + attMap.size());
            // Check DB_ID in the database
            query = "SELECT _class FROM DatabaseObject WHERE DB_ID = ?";
            PreparedStatement prepStat = conn.prepareStatement(query);
            int c = 0;
            //Set notInSet = new HashSet();
            for (Iterator it = attMap.keySet().iterator(); it.hasNext();) {
                Long dbID = (Long) it.next();
                prepStat.setLong(1, dbID.longValue());
                resultSet = prepStat.executeQuery();
                c = 0;
                while (resultSet.next()) {
                    c++;
                    String clsName = resultSet.getString(1);
                    String tmpClsName = (String) attMap.get(dbID);
                    if (!tmpClsName.equals(clsName)) {
                        errorMessage.append(dbID + ", " + tmpClsName);
                        errorMessage.append(" in " + tableNameMap.get(dbID));
                        errorMessage.append(" is different in DatabaseObject (");
                        errorMessage.append(clsName);
                        errorMessage.append(")\n");
                    }
                }
                if (c == 0) {
                    errorMessage.append(dbID + ", " + attMap.get(dbID));
                    errorMessage.append(" in " + tableNameMap.get(dbID));
                    errorMessage.append(" is not in the DatabaseObject");
                    errorMessage.append("\n");
                }
                else if (c > 1) {
                    errorMessage.append(dbID + " occurs in more than once in DatabaseObject");
                    errorMessage.append("\n");
                }
                resultSet.close();
            }
            prepStat.close();
            // Print out an empty line
            System.out.println();
            if (errorMessage.length() == 0) {
                System.out.println("Nothing Wrong!");
            }
            else {
                FileWriter fileWriter = new FileWriter("error.txt");
                BufferedWriter writer = new BufferedWriter(fileWriter);
                writer.write(errorMessage.toString());
                writer.close();
                fileWriter.close();
                System.out.println("Done. See errors in error.txt.");
            }
            return errorMessage.toString();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Provide use_neo4j argument");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            return;
        }
        boolean useNeo4J = Boolean.parseBoolean(args[1]);
        try {
            PersistenceAdaptor dba;
            if (useNeo4J)
                dba = new Neo4JAdaptor("localhost",
                        "graph.db",
                        "neo4j",
                        "reactome");
            else
                dba = new MySQLAdaptor("localhost",
                    "gk_central_050208",
                    "wgm",
                    "wgm",
                    3310);
            InstanceReferenceChecker checker = new InstanceReferenceChecker();
            checker.dba = dba;
            checker.check();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
