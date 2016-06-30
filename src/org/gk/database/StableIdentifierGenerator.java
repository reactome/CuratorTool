package org.gk.database;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.schema.SchemaClass;
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
public class StableIdentifierGenerator {
	private final String NUL_SPECIES = "NUL";
	private final String ALL_SPECIES = "ALL";
	private Map<String, String> speciesToAbbreviation;
	private Set<String> stidClasses;
	
	public StableIdentifierGenerator() {
	}
	
	/**
	 * Check if a GKInstance needs to have a stable id.
	 * @param instance
	 * @return
	 */
	public boolean needStid(GKInstance instance) {
	    Set<String> classNames = getClassNamesWithStableIds();
	    for (String className : classNames) {
	        if (instance.getSchemClass().isa(className))
	            return true;
	    }
	    return false;
	}
	
	public Set<String> getClassNamesWithStableIds() {
	    if (stidClasses == null) {
	        stidClasses = new HashSet<String>();
	        String[] names = new String[] {
	                ReactomeJavaConstants.PhysicalEntity,
	                ReactomeJavaConstants.Event,
	                ReactomeJavaConstants.Regulation
	        };
	        for (String name : names)
	            stidClasses.add(name);
	    }
	    return stidClasses;
	}
	
	/**
	 * Create a StableIdentifier instance for the passed GKInstance object.
	 * @param instance
	 * @return
	 * @throws Exception
	 */
	public GKInstance generateStableId(GKInstance instance,
	                                   GKInstance created,
	                                   XMLFileAdaptor fileAdaptor) throws Exception {
	    if (!needStid(instance))
	        return null;
	    PersistenceAdaptor adaptor = instance.getDbAdaptor();
	    SchemaClass stableIdCls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.StableIdentifier);
	    GKInstance stableId = new GKInstance(stableIdCls);
	    stableId.setDbAdaptor(adaptor);
	    String id = generateIdentifier(instance);
	    stableId.setAttributeValue(ReactomeJavaConstants.identifier, id);
	    stableId.setAttributeValue(ReactomeJavaConstants.identifierVersion, "1");
	    if (created != null)
	        stableId.setAttributeValue(ReactomeJavaConstants.created,
	                                   created);
	    stableId.setIsInflated(true);
	    InstanceDisplayNameGenerator.setDisplayName(stableId);
	    if (fileAdaptor != null) {
	        stableId.setDBID(fileAdaptor.getNextLocalID());
	        fileAdaptor.addNewInstance(stableId);
	    }
	    return stableId;
	}

	/**
	 * The actual method to generate a stable identifier for a GKInstance.
	 * @param instance
	 * @return
	 * @throws Exception
	 */
    public String generateIdentifier(GKInstance instance) throws Exception {
        String species = getSpeciesForSTID(instance);
	    String id = "R-" + species + "-" + instance.getDBID();
        return id;
    }
	
	private String getSpeciesForSTID(GKInstance inst) throws Exception {
		String species = NUL_SPECIES;
		if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.isChimeric)) {
		    Boolean isChimeric = (Boolean) inst.getAttributeValue(ReactomeJavaConstants.isChimeric);
		    if (isChimeric != null && isChimeric)
		        return species; // Pre-assumptive for null for all isChimeric instances
		}
		if (inst.getSchemClass().isa(ReactomeJavaConstants.Regulation))
			species = getSpeciesAbbrFromRegulation(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.Event))
			species = getSpeciesFromEvent(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity))
			species = getSpeciesFromSimpleEntity(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
			species = getSpeciesFromEntitySet(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.Polymer))
		    species = getSpeciesFromPolymer(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.OtherEntity))
		    species = ALL_SPECIES; // Species is not a valid attribute for OtherEntity
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
		Set<String> speciesNames = new HashSet<String>();
		for (GKInstance member : members) {
			// Don't count EntitySet itself
			if (member.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
				continue;
			String memberSpecies = getSpeciesForSTID(member);
			speciesNames.add(memberSpecies);
		}
		if (speciesNames.size() == 1)
		    return speciesNames.iterator().next();
		return NUL_SPECIES; // Too complicated
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
	
	private String getSpeciesFromPolymer(GKInstance polymer) throws Exception {
	    GKInstance species = (GKInstance) polymer.getAttributeValue(ReactomeJavaConstants.species);
	    if (species != null)
	        return getSpeciesAbbreviation(species);
	    GKInstance repeatedUnit = (GKInstance) polymer.getAttributeValue(ReactomeJavaConstants.repeatedUnit);
	    if (repeatedUnit == null)
	        return NUL_SPECIES;
	    return getSpeciesForSTID(repeatedUnit); // Use repeatedUnit's species
	}
	
	public GKInstance getSpeciesFromPE(GKInstance pe) throws Exception {
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
