package org.gk.scripts;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.database.StableIdentifierGenerator;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.util.FileUtilities;
import org.gk.util.GKApplicationUtilities;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.junit.Test;

/**
 * Methods handling stable identifiers are collected here.
 * @author Gwu
 */
@SuppressWarnings("unchecked")
public class StableIdentifierHandler {
    private MySQLAdaptor dba;
    private String outFileName;
    private Long defaultPerson = 140537L; // For Guanming Wu at CSHL
    
    public static void main(String[] args) throws Exception {
        // Make sure we have enough parameters to create a MySQLAdaptor object
        if (args.length < 5) {
            System.err.println("Usage java org.gk.scripts.StableIdentifierHandler dbHost dbName dbUser dbPwd outputFile {defaultPersonId}");
            System.exit(0);
        }
        StableIdentifierHandler handler = new StableIdentifierHandler();
        MySQLAdaptor dba = new MySQLAdaptor(args[0],
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
	
	private MySQLAdaptor getDBA() throws Exception {
	    if (dba == null) {
	        MySQLAdaptor dba = new MySQLAdaptor("localhost",
	                                            "gk_central_061316", 
	                                            "root", 
	                                            "macmysql01");
	        return dba;
	    }
	    return dba;
	}
	
	public void setDBA(MySQLAdaptor dba) {
	    this.dba = dba;
	}
	
	private String[] getClassNamesWithStableIds() {
		String[] names = new String[] {
				ReactomeJavaConstants.PhysicalEntity,
				ReactomeJavaConstants.Event,
				ReactomeJavaConstants.Regulation
		};
		return names;
	}
	
	/**
	 * Compare stables ids in two databases: e.g. a slicing database and gk_central.
	 * @throws Exception
	 */
	@Test
	public void compareStableIds() throws Exception {
	    MySQLAdaptor gkcentral = new MySQLAdaptor("reactomecurator.oicr.on.ca",
	                                         "gk_central",
	                                         "authortool",
	                                         "T001test");
	    MySQLAdaptor slice = new MySQLAdaptor("reactomerelease.oicr.on.ca",
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
	    for (Iterator<GKInstance> it = releasedStidsInGkcentral.iterator(); it.hasNext();) {
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
	 * @throws Exception
	 */
	@Test
	public void fixStableIdsInDB() throws Exception {
	    MySQLAdaptor dba = getDBA();
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
	            count ++;
	        }
	        else if (c.size() > 1)
	            System.out.println(inst + "\t" + c);
	        else {
	            System.out.println(inst + "\t" + c.iterator().next());
	            instToDbStid.put(inst,  c.iterator().next());
	        }
	    }
	    System.out.println("Instances don't have stableIds created: " + count);
	    instToStid.keySet().removeAll(instToDbStid.keySet());
	    System.out.println("The following instances have no StableIds in the database:");
	    System.out.println(instToStid.keySet());
	    
	    // Perform fix now
        boolean needTransaction = dba.supportsTransactions();
        try {
            if (needTransaction)
                dba.startTransaction();
            GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
                                                                   ScriptUtilities.GUANMING_WU_DB_ID,
                                                                   true); // Will store this IE directly
            for (GKInstance inst : instToDbStid.keySet()) {
                System.out.println("Fix " + inst);
                // Store StableIdentifier
                GKInstance stid = (GKInstance) instToDbStid.get(inst);
                // Update The original GKInstance now attached with StableIdentifier
                inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stid);
                inst.getAttributeValue(ReactomeJavaConstants.modified);
                inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
                dba.updateInstanceAttribute(inst, ReactomeJavaConstants.stableIdentifier);
                dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified);
            }
            if (needTransaction)
                dba.commit();
        }
        catch(Exception e) {
            if (needTransaction)
                dba.rollback();
            e.printStackTrace();
            throw e;
        }
	}
	
	public void generateStableIdsInDB() throws Exception {
	    MySQLAdaptor dba = getDBA();
	    Map<GKInstance, GKInstance> instToStid = generateInstToSTID(dba);
	    // Load modified for some perform gain
	    SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.modified);
	    dba.loadInstanceAttributeValues(instToStid.keySet(), att);
	    
	    // Save stable ids in the database
	    boolean needTransaction = dba.supportsTransactions();
	    try {
	        if (needTransaction)
	            dba.startTransaction();
	        GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba,
	                                                               this.defaultPerson,
	                                                               true); // Will store this IE directly
	        for (GKInstance inst : instToStid.keySet()) {
	            // Store StableIdentifier
	            GKInstance stid = (GKInstance) instToStid.get(inst);
	            stid.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
	            dba.storeInstance(stid);
	            // Update The original GKInstance now attached with StableIdentifier
	            inst.setAttributeValue(ReactomeJavaConstants.stableIdentifier, stid);
	            inst.getAttributeValue(ReactomeJavaConstants.modified);
	            inst.addAttributeValue(ReactomeJavaConstants.modified, defaultIE);
	            dba.updateInstanceAttribute(inst, ReactomeJavaConstants.stableIdentifier);
	            dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified);
	        }
	        if (needTransaction)
	            dba.commit();
	    }
	    catch(Exception e) {
	        if (needTransaction)
	            dba.rollback();
	        throw e;
	    }
	    String fileName = outFileName;
	    if (fileName == null)
	        fileName = "StableIdentifierHandler_Local_Test_070516.txt";
	    outputInstToStid(instToStid, fileName);
	}
	
	private Map<GKInstance, GKInstance> generateInstToSTID(MySQLAdaptor dba) throws Exception {
	    Map<GKInstance, GKInstance> instToStid = new HashMap<GKInstance, GKInstance>();
	    StableIdentifierGenerator stidGenerator = new StableIdentifierGenerator();
	    SchemaAttribute att = dba.getSchema().getClassByName(ReactomeJavaConstants.DatabaseObject).getAttribute(ReactomeJavaConstants.stableIdentifier);
	    for (String clsName : stidGenerator.getClassNamesWithStableIds()) {
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
	 * @throws Exception
	 */
	public void assignReleasedToStid() throws Exception {
	    MySQLAdaptor dba = getDBA();
	    Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.StableIdentifier);
	    // For this update, we will not add IE to avoid problem
	    dba.loadInstanceAttributeValues(c, new String[]{ReactomeJavaConstants.released});
	    boolean needTransaction = false;
	    try {
	        needTransaction = dba.supportsTransactions();
	        if (needTransaction)
	            dba.startTransaction();
	        for (GKInstance inst : c) {
	            inst.setAttributeValue(ReactomeJavaConstants.released, Boolean.TRUE);
	            dba.updateInstanceAttribute(inst, ReactomeJavaConstants.released);
	        }
	        if (needTransaction)
	            dba.commit();
	    }
	    catch(Exception e) {
	        dba.rollback();
	        throw e; // Re-throw this Exception for the client so that it can stop the running.
	    }
	}
	
	/**
	 * Test to generate a map from instances without stable identifiers to
	 * possible stable identifiers.
	 * @throws Exception
	 */
	@Test
	public void generateInstToSTIDMap() throws Exception {
		MySQLAdaptor dba = getDBA();
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
		    count ++;
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
				count ++;
				GKInstance catalyst = (GKInstance) regulatedEntity.getAttributeValue(ReactomeJavaConstants.physicalEntity);
				if (catalyst == null) {
					System.out.println("Cannot find species for " + regulation);
				}
				else {
					GKInstance species = stidGenerator.getSpeciesFromPE(catalyst);
					if (species != null) {
						System.out.println(regulation + "\t" + species);
					}
					else if (catalyst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity) ||
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
		MySQLAdaptor dba = getDBA();
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
	
	@Test
	public void testLoadManualSpeciesMap() throws Exception {
		Map<String, String> speciesToAbb = loadManualSpeciesMap();
		for (String species : speciesToAbb.keySet()) {
			String abb = speciesToAbb.get(species);
			System.out.println(species + ": " + abb);
		}
	}
	
	private Map<String, String> loadManualSpeciesMap() throws IOException, JDOMException {
        InputStream is = GKApplicationUtilities.getConfig("curator.xml");
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(is);
        Element elm = (Element) XPath.selectSingleNode(document.getRootElement(), 
                                                       "species_stid");
        Map<String, String> speciesToAbbr = new HashMap<String, String>();
        List<Element> list = elm.getChildren();
        for (Element speciesElm : list) {
            String species = speciesElm.getAttributeValue("name");
            String abbreviation = speciesElm.getAttributeValue("abbreviation");
            speciesToAbbr.put(species, abbreviation);
        }
        return speciesToAbbr;
	}

}
