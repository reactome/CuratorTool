package org.gk.scripts;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.gk.database.StableIdentifierGenerator;
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
 * Methods handling stable identifiers are collected here.
 *
 * @author Gwu
 */
@SuppressWarnings("unchecked")
public class StableIdentifierHandler {
    private PersistenceAdaptor dba;
    private String outFileName;
    private Long defaultPerson = 140537L; // For Guanming Wu at CSHL

    public static void main(String[] args) throws Exception {
        // Make sure we have enough parameters to create a PersistenceAdaptor object
        if (args.length < 7) {
            System.err.println("Usage java org.gk.scripts.StableIdentifierHandler dbHost dbName dbUser dbPwd outputFile {defaultPersonId} use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            System.exit(0);
        }
        StableIdentifierHandler handler = new StableIdentifierHandler();
        PersistenceAdaptor dba = null;
        boolean useNeo4J = Boolean.parseBoolean(args[6]);
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
        handler.setDBA(dba);
        handler.setOutputFileName(args[4]);
        if (args.length == 6)
            handler.setDefaultPerson(new Long(args[5]));
        handler.assignReleasedToStid();
        handler.generateStableIdsInDB();
    }

    public StableIdentifierHandler() {
    }

    public void setDefaultPerson(Long defaultPersonId) {
        this.defaultPerson = defaultPersonId;
    }

    public void setOutputFileName(String outputFile) {
        this.outFileName = outputFile;
    }

    private PersistenceAdaptor getDBA() throws Exception {
        if (dba == null) {
            PersistenceAdaptor dba = new MySQLAdaptor("localhost",
                    "gk_central_061316",
                    "root",
                    "macmysql01");
            return dba;
        }
        return dba;
    }

    public void setDBA(PersistenceAdaptor dba) {
        this.dba = dba;
    }

    private String[] getClassNamesWithStableIds() {
        String[] names = new String[]{
                ReactomeJavaConstants.PhysicalEntity,
                ReactomeJavaConstants.Event,
                ReactomeJavaConstants.Regulation
        };
        return names;
    }

    /**
     * Validate the implementation based on Joel's method on Nov 10, 2016 by comparing
     * would-be results with Joel's Perl output.
     *
     * @throws Exception
     */
    @Test
    public void validateNewApproach() throws Exception {
        PersistenceAdaptor gkcentral = new MySQLAdaptor("",
                "",
                "",
                "");
        String fileName = "/Users/gwu/Documents/wgm/work/reactome/StableIds/stable_ids_gk_central.txt";
        FileUtilities fu = new FileUtilities();
        fu.setInput(fileName);
        String line = fu.readLine();
        StableIdentifierGenerator generator = new StableIdentifierGenerator();
        int correctCases = 0;
        int incorrectCases = 0;
        while ((line = fu.readLine()) != null) {
            if (line.trim().length() == 0)
                continue;
            String[] tokens = line.split("\t");
            GKInstance inst = gkcentral.fetchInstance(new Long(tokens[0]));
            // An instance may be deleted
            if (inst == null)
                continue;
            String stableId = generator.generateIdentifier(inst);
            // The results from Joel's script should be like this:
            String supposedId = "R-" + tokens[4] + "-" + inst.getDBID();
            if (stableId.equals(supposedId)) {
//	            System.out.println("Correct: " + inst);
                correctCases++;
            } else {
                System.out.println(inst + "\t" + supposedId + "\t" + stableId);
                incorrectCases++;
            }
        }
        fu.close();
        System.out.println("Total correct cases: " + correctCases);
        System.out.println("Total incorrect cases: " + incorrectCases);
    }

    /**
     * Compare stables ids in two databases: e.g. a slicing database and gk_central.
     *
     * @throws Exception
     */
    @Test
    public void compareStableIds() throws Exception {
        PersistenceAdaptor gkcentral = new MySQLAdaptor("reactomecurator.oicr.on.ca",
                "gk_central",
                "authortool",
                "T001test");
        PersistenceAdaptor slice = new MySQLAdaptor("reactomerelease.oicr.on.ca",
                "test_slice_58a",
                "{CHANGE_ME}",
                "{CHANGE_ME}");
        Collection<GKInstance> releasedStidsInGkcentral = gkcentral.fetchInstanceByAttribute(ReactomeJavaConstants.StableIdentifier,
                ReactomeJavaConstants.released,
                "=",
                "true");

        System.out.println("Total StableIds in gk_central having released = TRUE: " + releasedStidsInGkcentral.size());
        Collection<GKInstance> stidsInSlice = slice.fetchInstancesByClass(ReactomeJavaConstants.StableIdentifier);
        System.out.println("Total StableIds in slice: " + stidsInSlice.size());
        Set<Long> stidDBIds = new HashSet<Long>();
        for (GKInstance stid : stidsInSlice)
            stidDBIds.add(stid.getDBID());
        int index = 1;
        for (Iterator<GKInstance> it = releasedStidsInGkcentral.iterator(); it.hasNext(); ) {
            GKInstance stid = it.next();
            if (stidDBIds.contains(stid.getDBID()))
                continue;
            Collection<GKInstance> referred = stid.getReferers(ReactomeJavaConstants.stableIdentifier);
            GKInstance inst = null;
            if (referred != null)
                inst = referred.iterator().next();
            System.out.println(index++ + "\t" + stid + "\t" +
                    (inst == null ? "null" : inst));
        }
    }

    /**
     * Because of the bug in the release 81 of the curator tool, generated StableIdentifiers have not
     * been assigned to their respective instances.
     *
     * @throws Exception
     */
    @Test
    public void fixStableIdsInDB() throws Exception {
        PersistenceAdaptor dba = getDBA();
        Map<GKInstance, GKInstance> instToStid = generateInstToSTID(dba);
        System.out.println("Total instances having no stable ids: " + instToStid.size());
        int count = 0;
        Map<GKInstance, GKInstance> instToDbStid = new HashMap<GKInstance, GKInstance>();
        for (GKInstance inst : instToStid.keySet()) {
            GKInstance localStid = instToStid.get(inst);
            String identifier = (String) localStid.getAttributeValue(ReactomeJavaConstants.identifier);
            // Find if such an instance existing already
            Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.StableIdentifier,
                    ReactomeJavaConstants.identifier,
                    "=",
                    identifier);
            if (c == null || c.size() == 0) {
                System.out.println(inst + "\tnull");
                count++;
            } else if (c.size() > 1)
                System.out.println(inst + "\t" + c);
            else {
                System.out.println(inst + "\t" + c.iterator().next());
                instToDbStid.put(inst, c.iterator().next());
            }
        }
        System.out.println("Instances don't have stableIds created: " + count);
        instToStid.keySet().removeAll(instToDbStid.keySet());
        System.out.println("The following instances have no StableIds in the database:");
        System.out.println(instToStid.keySet());

        // Perform fix now
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, tx); // Will store this IE directly
                for (GKInstance inst : instToDbStid.keySet()) {
                    System.out.println("Fix " + inst);
                    // Store StableIdentifier
                    GKInstance stid = (GKInstance) instToDbStid.get(inst);
                    // Update The original GKInstance now attached with StableIdentifier
                    inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stid);
                    inst.getAttributeValue(ReactomeJavaConstants.modified);
                    inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.stableIdentifier, tx);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified, tx);
                }
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            // MySQL
            boolean needTransaction = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (needTransaction)
                    ((MySQLAdaptor) dba).startTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        ScriptUtilities.GUANMING_WU_DB_ID,
                        true, null); // Will store this IE directly
                for (GKInstance inst : instToDbStid.keySet()) {
                    System.out.println("Fix " + inst);
                    // Store StableIdentifier
                    GKInstance stid = (GKInstance) instToDbStid.get(inst);
                    // Update The original GKInstance now attached with StableIdentifier
                    inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stid);
                    inst.getAttributeValue(ReactomeJavaConstants.modified);
                    inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.stableIdentifier, null);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified, null);
                }
                if (needTransaction)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (needTransaction)
                    ((MySQLAdaptor) dba).rollback();
                e.printStackTrace();
                throw e;
            }
        }
    }

    public void generateStableIdsInDB() throws Exception {
        PersistenceAdaptor dba = getDBA();
        Map<GKInstance, GKInstance> instToStid = generateInstToSTID(dba);
        // Load modified for some perform gain
        SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.modified);
        dba.loadInstanceAttributeValues(instToStid.keySet(), att);

        // Save stable ids in the database
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        this.defaultPerson,
                        true, tx); // Will store this IE directly
                for (GKInstance inst : instToStid.keySet()) {
                    // Store StableIdentifier
                    GKInstance stid = (GKInstance) instToStid.get(inst);
                    stid.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
                    dba.storeInstance(stid, tx);
                    // Update The original GKInstance now attached with StableIdentifier
                    inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stid);
                    inst.getAttributeValue(ReactomeJavaConstants.modified);
                    inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.stableIdentifier, tx);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified, tx);
                }
                tx.commit();
            }
        } else {
            // MySQL
            boolean needTransaction = ((MySQLAdaptor) dba).supportsTransactions();
            try {
                if (needTransaction)
                    ((MySQLAdaptor) dba).startTransaction();
                GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                        this.defaultPerson,
                        true, null); // Will store this IE directly
                for (GKInstance inst : instToStid.keySet()) {
                    // Store StableIdentifier
                    GKInstance stid = (GKInstance) instToStid.get(inst);
                    stid.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
                    dba.storeInstance(stid, null);
                    // Update The original GKInstance now attached with StableIdentifier
                    inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stid);
                    inst.getAttributeValue(ReactomeJavaConstants.modified);
                    inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.stableIdentifier, null);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified, null);
                }
                if (needTransaction)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                if (needTransaction)
                    ((MySQLAdaptor) dba).rollback();
                throw e;
            }
        }
        String fileName = outFileName;
        if (fileName == null)
            fileName = "StableIdentifierHandler_Local_Test_070516.txt";
        outputInstToStid(instToStid, fileName);
    }

    private Map<GKInstance, GKInstance> generateInstToSTID(PersistenceAdaptor dba) throws Exception {
        Map<GKInstance, GKInstance> instToStid = new HashMap<GKInstance, GKInstance>();
        StableIdentifierGenerator stidGenerator = new StableIdentifierGenerator();
        SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.stableIdentifier);
        for (String clsName : stidGenerator.getClassNamesWithStableIds(dba)) {
            Collection<GKInstance> insts = dba.fetchInstancesByClass(clsName);
            dba.loadInstanceAttributeValues(insts, att);
            for (GKInstance inst : insts) {
                if (inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier) != null)
                    continue;
                GKInstance stid = stidGenerator.generateStableId(inst, null, null);
                if (stid == null)
                    continue; // Just ignore it
                instToStid.put(inst, stid);
            }
        }
        return instToStid;
    }

    /**
     * Assign released = true to all existing StableIdentifiers
     *
     * @throws Exception
     */
    public void assignReleasedToStid() throws Exception {
        PersistenceAdaptor dba = getDBA();
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.StableIdentifier);
        // For this update, we will not add IE to avoid problem
        dba.loadInstanceAttributeValues(c, new String[]{ReactomeJavaConstants.released});
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                for (GKInstance inst : c) {
                    inst.setAttributeValue(ReactomeJavaConstants.released, Boolean.TRUE);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.released, tx);
                }
                tx.commit();
            }
        } else {
            // MySQL
            boolean needTransaction = false;
            try {
                needTransaction = ((MySQLAdaptor) dba).supportsTransactions();
                if (needTransaction)
                    ((MySQLAdaptor) dba).startTransaction();
                for (GKInstance inst : c) {
                    inst.setAttributeValue(ReactomeJavaConstants.released, Boolean.TRUE);
                    dba.updateInstanceAttribute(inst, ReactomeJavaConstants.released, null);
                }
                if (needTransaction)
                    ((MySQLAdaptor) dba).commit();
            }
            catch(Exception e) {
                ((MySQLAdaptor) dba).rollback();
                throw e; // Re-throw this Exception for the client so that it can stop the running.
            }
        }
    }

    /**
     * Test to generate a map from instances without stable identifiers to
     * possible stable identifiers.
     *
     * @throws Exception
     */
    @Test
    public void generateInstToSTIDMap() throws Exception {
        PersistenceAdaptor dba = getDBA();
        Map<GKInstance, GKInstance> instToStid = generateInstToSTID(dba);
//		String fileName = "/Users/Gwu/Documents/temp/GK_Central_Instance_STID_062716.txt";
        String fileName = "/Users/Gwu/Documents/temp/GK_Central_Instance_STID_080116.txt";
        outputInstToStid(instToStid, fileName);
        System.out.println("Total instances: " + instToStid.size());
    }

    private void outputInstToStid(Map<GKInstance, GKInstance> instToStid,
                                  String fileName) throws IOException {
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine("DB_ID\tDisplayName\tClass\tSTID");
        int count = 0;
        for (GKInstance inst : instToStid.keySet()) {
            GKInstance stid = instToStid.get(inst);
            fu.printLine(inst.getDBID() + "\t" +
                    inst.getDisplayName() + "\t" +
                    inst.getSchemClass().getName() + "\t" +
                    stid.getDisplayName());
            count++;
        }
        fu.close();
    }

    @Test
    public void checkRegulations() throws Exception {
        int count = 0;
        StableIdentifierGenerator stidGenerator = new StableIdentifierGenerator();
        Collection<GKInstance> c = getDBA().fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        for (GKInstance regulation : c) {
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null)
                continue;
            if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
                count++;
                GKInstance catalyst = (GKInstance) regulatedEntity.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                if (catalyst == null) {
                    System.out.println("Cannot find species for " + regulation);
                } else {
                    GKInstance species = stidGenerator.getSpeciesFromPE(catalyst);
                    if (species != null) {
                        System.out.println(regulation + "\t" + species);
                    } else if (catalyst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity) ||
                            catalyst.getSchemClass().isa(ReactomeJavaConstants.OtherEntity))
                        System.out.println(regulation + "\tALL");
                    else
                        System.out.println("Cannot find species for " + regulation);
                }
            }
        }
        System.out.println("Regulations: " + count);
    }

    @Test
    public void checkInstancesForStableIds() throws Exception {
        Set<GKInstance> instancesNoStableIds = new HashSet<GKInstance>();
        PersistenceAdaptor dba = getDBA();
        String[] names = getClassNamesWithStableIds();
        for (String name : names) {
            Collection<GKInstance> insts = dba.fetchInstancesByClass(name);
            dba.loadInstanceAttributeValues(insts,
                    new String[]{ReactomeJavaConstants.stableIdentifier});
            for (GKInstance inst : insts) {
                GKInstance stableId = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                if (stableId == null)
                    instancesNoStableIds.add(inst);
            }
        }
        System.out.println("Total instances having no stable ids: " + instancesNoStableIds.size());
    }

}
