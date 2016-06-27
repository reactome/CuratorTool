package org.gk.scripts;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
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
	private final String NUL_SPECIES = "NUL";
	private final String ALL_SPECIES = "ALL";
	private Map<String, String> speciesToAbbreviation;
	
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
		fu.setOutput("/Users/Gwu/Documents/temp/GK_Central_Instance_STID.txt");
		fu.printLine("DB_ID\tDisplayName\tClass\tSpecies\tSTID");
		int count = 0;
		for (String clsName : clsNames) {
			Collection<GKInstance> insts = dba.fetchInstancesByClass(clsName);
			dba.loadInstanceAttributeValues(insts,
										    new String[]{ReactomeJavaConstants.stableIdentifier});
			for (GKInstance inst : insts) {
				if (inst.getAttributeValue(ReactomeJavaConstants.stableIdentifier) != null)
					continue;
				String species = getSpeciesForSTID(inst);
				String stid = "R-" + species + "-" + inst.getDBID() + ".1";
				fu.printLine(inst.getDBID() + "\t" + 
							inst.getDisplayName() + "\t" + 
							inst.getSchemClass().getName() + "\t" +
							species + "\t" +
							stid);
				count ++;
			}
		}
		fu.close();
		System.out.println("Total instances: " + count);
	}
	
	private String getSpeciesForSTID(GKInstance inst) throws Exception {
		String species = NUL_SPECIES;
		if (inst.getSchemClass().isa(ReactomeJavaConstants.Regulation))
			species = getSpeciesAbbrFromRegulation(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.Event))
			species = getSpeciesFromEvent(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity))
			species = getSpeciesFromSimpleEntity(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
			species = getSpeciesFromEntitySet(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
			species = getSpeciesFromPhysicalEntity(inst);
		return species;
	}
	
	private String getSpeciesFromEntitySet(GKInstance entitySet) throws Exception {
		GKInstance species = getSpeciesFromPE(entitySet);
		if (species != null)
			return getSpeciesAbbreviation(species);
		// If the passed entitySet is a set of SimpleEntity, we should use ALL.
		// Otherwise, we use NUL.
		Set<GKInstance> members = InstanceUtilities.getContainedInstances(entitySet,
																		  ReactomeJavaConstants.hasMember);
		// Check what kind of members based on their schema classes
		Set<String> clsNames = new HashSet<String>();
		for (GKInstance member : members) {
			// Don't count EntitySet itself
			if (member.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
				continue;
			clsNames.add(member.getSchemClass().getName());
		}
		if (clsNames.size() == 1 && clsNames.equals(ReactomeJavaConstants.SimpleEntity))
			return ALL_SPECIES;
		return NUL_SPECIES;
	}
	
	private String getSpeciesFromPhysicalEntity(GKInstance physicalEntity) throws Exception {
		GKInstance species = getSpeciesFromPE(physicalEntity);
		if (species == null)
			return NUL_SPECIES;
		return getSpeciesAbbreviation(species);
	}
	
	/**
	 * Regulation doesn't have species. We want to get the species from its regulatedEntity.
	 * The implementation here is different from the original Perl script.
	 * @param regulation
	 * @throws Exception
	 */
	private String getSpeciesAbbrFromRegulation(GKInstance regulation) throws Exception {
		GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
		// This is a mandatory value. If nothing specified, most likely the regulation
		// should not be released.
		if (regulatedEntity == null)
			return NUL_SPECIES;
		// There are only two cases below
		if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.Event)) {
			// Species is required for all Events. Is we cannot see species for an Event,
			// We will assign null
			GKInstance species = (GKInstance) regulatedEntity.getAttributeValue(ReactomeJavaConstants.species);
			if (species == null) 
				return NUL_SPECIES;
			else {
				return getSpeciesAbbreviation(species);
			}
		}
		else if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
			GKInstance catalyst = (GKInstance) regulatedEntity.getAttributeValue(ReactomeJavaConstants.physicalEntity);
			if (catalyst == null) {
				return NUL_SPECIES;
			}
			else {
				GKInstance species = getSpeciesFromPE(catalyst);
				if (species != null) {
					return getSpeciesAbbreviation(species);
				}
				else if (catalyst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity) ||
				    catalyst.getSchemClass().isa(ReactomeJavaConstants.OtherEntity))
					return ALL_SPECIES;
				else 
					return NUL_SPECIES;
			}
		}
		return NUL_SPECIES;
	}
	
	/**
	 * Species is a mandatory value. If nothing there, we will use NUL.
	 * @param event
	 * @throws Exception
	 */
	private String getSpeciesFromEvent(GKInstance event) throws Exception {
		GKInstance species = (GKInstance) event.getAttributeValue(ReactomeJavaConstants.species);
		if (species == null)
			return NUL_SPECIES;
		return getSpeciesAbbreviation(species);
	}
	
	/**
	 * Species is an optional value. If no species is specified, the returned value
	 * should be ALL, which means this SimpleEntity is universal.
	 * @param simpleEntity
	 * @throws Exception
	 */
	private String getSpeciesFromSimpleEntity(GKInstance simpleEntity) throws Exception {
		GKInstance species = (GKInstance) simpleEntity.getAttributeValue(ReactomeJavaConstants.species);
		if (species == null)
			return ALL_SPECIES;
		return getSpeciesAbbreviation(species);
	}
	
	private GKInstance getSpeciesFromPE(GKInstance pe) throws Exception {
		if (!pe.getSchemClass().isValidAttribute(ReactomeJavaConstants.species))
			return null;
		GKInstance species = (GKInstance) pe.getAttributeValue(ReactomeJavaConstants.species);
		if (species != null)
			return species;
		// Get species from hasComponent
		if (pe.getSchemClass().isa(ReactomeJavaConstants.Complex))
			return getWrappedSpecies(pe, ReactomeJavaConstants.hasComponent);
		if (pe.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
			return getWrappedSpecies(pe, ReactomeJavaConstants.hasMember);
		if (pe.getSchemClass().isa(ReactomeJavaConstants.Polymer))
			return getWrappedSpecies(pe, ReactomeJavaConstants.repeatedUnit);
		return null;
	}
	
	private GKInstance getWrappedSpecies(GKInstance pe,
									     String attName) throws Exception {
		if (!pe.getSchemClass().isValidAttribute(attName))
			return null;
		List<GKInstance> values = pe.getAttributeValuesList(attName);
		for (GKInstance value : values) {
			if (value.getSchemClass().isValidAttribute(ReactomeJavaConstants.species)) {
				GKInstance species = (GKInstance) value.getAttributeValue(ReactomeJavaConstants.species);
				if (species != null)
					return species;
			}
		}
		// Perform recursive calling: e.g. complex is a value
		// of hasComponent of a container Complex.
		for (GKInstance value : values)
			return getWrappedSpecies(value, attName);
		return null;
	}
	
	@Test
	public void checkRegulations() throws Exception {
		int count = 0;
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
					GKInstance species = getSpeciesFromPE(catalyst);
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
	
	private String getSpeciesAbbreviation(GKInstance species) throws Exception {
		if (speciesToAbbreviation == null) {
			speciesToAbbreviation = new HashMap<String, String>();
			Map<String, String> manualMap = loadManualSpeciesMap();
			speciesToAbbreviation.putAll(manualMap);
		}
		String speciesName = species.getDisplayName();
		String abbreviation = speciesToAbbreviation.get(speciesName);
		if (abbreviation != null)
			return abbreviation;
		int index = speciesName.indexOf(" ");
		// First letter in the first part and first two letters in the second part
		// All upper case
		abbreviation = speciesName.substring(0, 1).toUpperCase() + speciesName.substring(index + 1,
																					     index + 3).toUpperCase();
		speciesToAbbreviation.put(speciesName, abbreviation);
		return abbreviation;
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
