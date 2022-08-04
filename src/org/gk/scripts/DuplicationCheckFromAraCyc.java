/*
 * Created on Oct 31, 2011
 *
 */
package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.database.DefaultInstanceEditHelper;
import org.gk.database.ReverseAttributePane;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

/**
 * This script is used to check duplication in SmallMolecules generated by importing AraCyc
 * pathways.
 *
 * @author gwu
 */
public class DuplicationCheckFromAraCyc {
    private PersistenceAdaptor dba;

    public DuplicationCheckFromAraCyc() {
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Please providing dbHost, dbName, dbUser, dbPwd and use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            System.exit(0);
        }
        PersistenceAdaptor dba = null;
        boolean useNeo4J = Boolean.parseBoolean(args[4]);
        if (args.length == 6 || args[5].equals("fix")) {
            try {
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
                DuplicationCheckFromAraCyc checker = new DuplicationCheckFromAraCyc();
                checker.setPersistenceAdaptor(dba);
                checker.fixDuplicationsInSimpleEntities();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (args.length == 6 && args[5].equals("check")) {
            try {
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
                DuplicationCheckFromAraCyc checker = new DuplicationCheckFromAraCyc();
                checker.setPersistenceAdaptor(dba);
                checker.checkDuplicationInSimpleEntities();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setPersistenceAdaptor(PersistenceAdaptor dba) {
        this.dba = dba;
    }

    public PersistenceAdaptor getPersistenceAdaptor() throws Exception {
        if (dba != null)
            return dba;
        dba = new MySQLAdaptor("localhost",
                "gk_central_103111",
                "root",
                "macmysql01");
        return dba;
    }

    @Test
    public void fixDuplicationsInSimpleEntities() throws Exception {
        long time1 = System.currentTimeMillis();
        PersistenceAdaptor dba = getPersistenceAdaptor();
        List<GKInstance> araSimpleEntities = fetchAraSimpleEntities(dba);
        ReverseAttributePane referrersHelper = new ReverseAttributePane();
        if (dba instanceof Neo4JAdaptor) {
            Driver driver = ((Neo4JAdaptor) dba).getConnection();
            try (Session session = driver.session(SessionConfig.forDatabase(dba.getDBName()))) {
                Transaction tx = session.beginTransaction();
                // Set up default IE
                Long defaultPersonId = 1112777L; // Guanming Wu for Rice Project
                GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
                DefaultInstanceEditHelper ieHelper = new DefaultInstanceEditHelper();
                GKInstance newIE = ieHelper.createDefaultInstanceEdit(defaultPerson);
                newIE.addAttributeValue(ReactomeJavaConstants.dateTime,
                        GKApplicationUtilities.getDateTime());
                InstanceDisplayNameGenerator.setDisplayName(newIE);
                dba.storeInstance(newIE, tx);
                System.out.println("Default IE: " + newIE);
                // Get a set of referrers for modified slot
                Set<GKInstance> changed = new HashSet<GKInstance>();
                // In case an used instance is from ara-cyc do. Skip if so!
                Set<GKInstance> used = new HashSet<GKInstance>();
                int totalDeleted = 0;
                for (GKInstance araInst : araSimpleEntities) {
                    if (used.contains(araInst))
                        continue; // This instance has been used somewhere
                    Set<?> set = dba.fetchIdenticalInstances(araInst);
                    if (set == null)
                        continue;
                    set.remove(araInst);
                    if (set.size() == 0)
                        return;
                    GKInstance older = getOlderInstance(set);
                    used.add(older);
                    Map<String, List<GKInstance>> attNameToReferrers = referrersHelper.getReferrersMapForDBInstance(araInst);
                    for (String attName : attNameToReferrers.keySet()) {
                        List<GKInstance> referrers = attNameToReferrers.get(attName);
                        for (GKInstance referrer : referrers) {
                            //                    System.out.println("Working on referrer: " + referrer);
                            GKSchemaAttribute attribute = (GKSchemaAttribute) referrer.getSchemClass().getAttribute(attName);
                            if (attribute.isMultiple()) {
                                List<GKInstance> list = referrer.getAttributeValuesList(attribute);
                                int index = list.indexOf(araInst);
                                list.set(index, older);
                                referrer.setAttributeValue(attribute, list);
                            } else
                                referrer.setAttributeValue(attribute, older);
                            // Update referrer
                            changed.add(referrer);
                            dba.updateInstanceAttribute(referrer, attName, tx);
                        }
                    }
                    // Delete araInst
                    ((Neo4JAdaptor) dba).deleteInstance(araInst, tx);
                    totalDeleted++;
                }
                for (GKInstance referrer : changed) {
                    // Have to load attributes first. Otherwise, old values will be gone since they are not loaded
                    List<GKInstance> values = referrer.getAttributeValuesList(ReactomeJavaConstants.modified);
                    referrer.addAttributeValue(ReactomeJavaConstants.modified, newIE);
                    dba.updateInstanceAttribute(referrer, ReactomeJavaConstants.modified, tx);
                }
                tx.commit();
                long time2 = System.currentTimeMillis();
                System.out.println("Total time: " + (time2 - time1));
                System.out.println("Total deleted instances: " + totalDeleted);
            }
        } else {
            // MySQL
            try {
                ((MySQLAdaptor) dba).startTransaction();
                // Set up default IE
                Long defaultPersonId = 1112777L; // Guanming Wu for Rice Project
                GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
                DefaultInstanceEditHelper ieHelper = new DefaultInstanceEditHelper();
                GKInstance newIE = ieHelper.createDefaultInstanceEdit(defaultPerson);
                newIE.addAttributeValue(ReactomeJavaConstants.dateTime,
                        GKApplicationUtilities.getDateTime());
                InstanceDisplayNameGenerator.setDisplayName(newIE);
                dba.storeInstance(newIE, null);
                System.out.println("Default IE: " + newIE);
                // Get a set of referrers for modified slot
                Set<GKInstance> changed = new HashSet<GKInstance>();
                // In case an used instance is from ara-cyc do. Skip if so!
                Set<GKInstance> used = new HashSet<GKInstance>();
                int totalDeleted = 0;
                for (GKInstance araInst : araSimpleEntities) {
                    if (used.contains(araInst))
                        continue; // This instance has been used somewhere
                    Set<?> set = dba.fetchIdenticalInstances(araInst);
                    if (set == null)
                        continue;
                    set.remove(araInst);
                    if (set.size() == 0)
                        return;
                    GKInstance older = getOlderInstance(set);
                    used.add(older);
                    Map<String, List<GKInstance>> attNameToReferrers = referrersHelper.getReferrersMapForDBInstance(araInst);
                    for (String attName : attNameToReferrers.keySet()) {
                        List<GKInstance> referrers = attNameToReferrers.get(attName);
                        for (GKInstance referrer : referrers) {
                            //                    System.out.println("Working on referrer: " + referrer);
                            GKSchemaAttribute attribute = (GKSchemaAttribute) referrer.getSchemClass().getAttribute(attName);
                            if (attribute.isMultiple()) {
                                List<GKInstance> list = referrer.getAttributeValuesList(attribute);
                                int index = list.indexOf(araInst);
                                list.set(index, older);
                                referrer.setAttributeValue(attribute, list);
                            } else
                                referrer.setAttributeValue(attribute, older);
                            // Update referrer
                            changed.add(referrer);
                            dba.updateInstanceAttribute(referrer, attribute.getName(), null);
                        }
                    }
                    // Delete araInst
                    ((MySQLAdaptor) dba).deleteInstance(araInst);
                    totalDeleted++;
                }
                for (GKInstance referrer : changed) {
                    // Have to load attributes first. Otherwise, old values will be gone since they are not loaded
                    List<GKInstance> values = referrer.getAttributeValuesList(ReactomeJavaConstants.modified);
                    referrer.addAttributeValue(ReactomeJavaConstants.modified, newIE);
                    dba.updateInstanceAttribute(referrer, ReactomeJavaConstants.modified, null);
                }
                ((MySQLAdaptor) dba).commit();
                long time2 = System.currentTimeMillis();
                System.out.println("Total time: " + (time2 - time1));
                System.out.println("Total deleted instances: " + totalDeleted);
            } catch (Exception e) {
                ((MySQLAdaptor) dba).rollback();
                throw e;
            }

        }
    }

    /**
     * Get an instance with smallest DB_ID.
     *
     * @param inst
     * @return
     */
    private GKInstance getOlderInstance(Set<?> set) {
        GKInstance rtn = null;
        for (Object obj : set) {
            GKInstance inst = (GKInstance) obj;
            if (rtn == null)
                rtn = inst;
            else if (inst.getDBID() < rtn.getDBID())
                rtn = inst;
        }
        return rtn;
    }

    @Test
    public void checkDuplicationInSimpleEntities() throws Exception {
        PersistenceAdaptor dba = getPersistenceAdaptor();
        List<GKInstance> araInsts = fetchAraSimpleEntities(dba);
        // Search for any duplication
        int total = 0;
        // Use this IE for checking if it is a rice instance
        GKInstance riceIE = dba.fetchInstance(1164949L);
        System.out.println("AraCyc_Instance\tDuplicate\tDuplicated Instance\tIs_Rice_Cyc");
        for (GKInstance araInst : araInsts) {
            Set<?> set = dba.fetchIdenticalInstances(araInst);
            if (set == null)
                continue;
            set.remove(araInst);
            if (set.size() > 0) {
                total++;
                String isRiceInstText = generateIsRiceInstText(set, riceIE);
                System.out.println(araInst + "\t" + set.size() + "\t" + generateTextForSet(set) + "\t" + isRiceInstText);
            }
        }
        System.out.println("Total duplicated SimpleEntity instances: " + total);
    }

    protected List<GKInstance> fetchAraSimpleEntities(PersistenceAdaptor dba)
            throws Exception, InvalidAttributeException {
        Collection<?> c = dba.fetchInstancesByClass(ReactomeJavaConstants.SimpleEntity);
        System.out.println("Total SimpleEntity instances: " + c.size());
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.SimpleEntity);
        dba.loadInstanceAttributeValues(c, cls.getAttribute(ReactomeJavaConstants.created));
        dba.loadInstanceAttributeValues(c, cls.getAttribute(ReactomeJavaConstants.modified));
        // This is an IE for flag for aracyc importing: 
        Long ieId = 1428497L;
        GKInstance ie = dba.fetchInstance(ieId);
        List<GKInstance> araInsts = new ArrayList<GKInstance>();
        for (Object obj : c) {
            GKInstance inst = (GKInstance) obj;
            if (isCycInstance(inst, ie))
                araInsts.add(inst);
        }
        System.out.println("Total SimpleEntity instances from AraCyc: " + araInsts.size());
        return araInsts;
    }

    private String generateIsRiceInstText(Set<?> set,
                                          GKInstance riceIE) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Iterator<?> it = set.iterator(); it.hasNext(); ) {
            GKInstance inst = (GKInstance) it.next();
            boolean isRiceInst = isCycInstance(inst, riceIE);
            builder.append(isRiceInst);
            if (it.hasNext())
                builder.append(", ");
        }
        return builder.toString();
    }

    private boolean isCycInstance(GKInstance inst,
                                  GKInstance ie) throws Exception {
        List<?> values = inst.getAttributeValuesList(ReactomeJavaConstants.created);
        if (values != null && values.contains(ie)) {
            return true;
        }
        values = inst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (values != null && values.contains(ie))
            return true;
        return false;
    }

    private String generateTextForSet(Set<?> set) {
        StringBuilder builder = new StringBuilder();
        for (Iterator<?> it = set.iterator(); it.hasNext(); ) {
            Object obj = it.next();
            builder.append(obj.toString());
            if (it.hasNext())
                builder.append(", ");
        }
        return builder.toString();
    }

}
