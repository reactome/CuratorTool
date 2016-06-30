package org.gk.scripts;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.database.StableIdentifierGenerator;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
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
	
	public StableIdentifierHandler() {
	}
	
	private MySQLAdaptor getDBA() throws Exception {
		MySQLAdaptor dba = new MySQLAdaptor("localhost",
											"gk_central_061316", 
											"root", 
											"macmysql01");
		return dba;
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
	 * Test to generate a map from instances without stable identifiers to
	 * possible stable identifiers.
	 * @throws Exception
	 */
	@Test
	public void generateInstToSTIDMap() throws Exception {
		MySQLAdaptor dba = getDBA();
		String[] clsNames = getClassNamesWithStableIds();
		FileUtilities fu = new FileUtilities();
		fu.setOutput("/Users/Gwu/Documents/temp/GK_Central_Instance_STID_062716.txt");
		fu.printLine("DB_ID\tDisplayName\tClass\tSTID");
		StableIdentifierGenerator stidGenerator = new StableIdentifierGenerator();
		int count = 0;
		for (String clsName : clsNames) {
			Collection<GKInstance> insts = dba.fetchInstancesByClass(clsName);
			dba.loadInstanceAttributeValues(insts,
										    new String[]{ReactomeJavaConstants.stableIdentifier});
			for (GKInstance inst : insts) {
				if (inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier) != null)
					continue;
				GKInstance stid = stidGenerator.generateStableId(inst, null, null);
				fu.printLine(inst.getDBID() + "\t" + 
							inst.getDisplayName() + "\t" + 
							inst.getSchemClass().getName() + "\t" +
							stid.getDisplayName());
				count ++;
			}
		}
		fu.close();
		System.out.println("Total instances: " + count);
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
