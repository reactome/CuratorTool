package org.gk.database;

import java.awt.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.SchemaClass;
import org.junit.Test;

/**
 * Methods handling stable identifiers are collected here. This class implements the following approach based on Joel's
 * document in http://devwiki.reactome.org/index.php/Development_Teleconferences#Minutes_and_Agendas:
 * If instance is a physical entity:
      If one species instance is attached, use it to get the prefix
      If more than one species instance is attached, the prefix is ‘NUL’
      If no species instances are attached:
            Get all species instances recursively from the physical entity
            (i.e. components from complexes, members/candidates from sets, repeated unit from polymers)
                If one species instance is attached, use it to get the prefix
                If more than one species instance is attached, the prefix is ‘NUL’
                If no species instance is attached, the prefix is ‘ALL’
  If instance is an event:
      If one species instance is attached, use it to get the prefix
      If no or more than one species is attached, the prefix is ‘NUL’
  If instance is a regulation:
      If the regulated entity is an event, follow the rules for when the instance is an event
      If the regulated entity is a catalyst activity:
           If the catalyst activity has a physical entity, follow the rules for when the instance is a physical entity
           If the catalyst activity doesn’t have a physical entity, the prefix is ‘NUL’
      If the regulated entity doesn’t exist or is not a catalyst activity or an event, the prefix is ‘NUL’
 * @author Gwu
 */
@SuppressWarnings("unchecked")
public class StableIdentifierGenerator {
	private final String NUL_SPECIES = "NUL";
	private final String ALL_SPECIES = "ALL";
	private Set<String> stidClasses;
	// Used to get active Neo4JAdaptor
	private Component parentComponent;
	
	public StableIdentifierGenerator() {
	}

    public Component getParentComponent() {
        return parentComponent;
    }

    public void setParentComponent(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    /**
	 * Check if a GKInstance needs to have a stable id.
	 * @param instance Instance to check
	 * @return true if stable id is needed
	 */
	public boolean needStid(GKInstance instance) {
	    Set<String> classNames = getClassNamesWithStableIds(instance.getDbAdaptor());
	    for (String className : classNames) {
	        if (instance.getSchemClass().isa(className))
	            return true;
	    }
	    return false;
	}
	
	public boolean needStid(GKSchemaClass cls, PersistenceAdaptor adaptor) {
	    Set<String> clsNames = getClassNamesWithStableIds(adaptor);
	    for (String clsName : clsNames) {
	        if (cls.isa(clsName))
	            return true;
	    }
	    return false;
	}
	
	public Set<String> getClassNamesWithStableIds(PersistenceAdaptor adaptor) {
	    if (stidClasses == null) {
	        stidClasses = new HashSet<String>();
	        stidClasses.add(ReactomeJavaConstants.PhysicalEntity);
	        stidClasses.add(ReactomeJavaConstants.Event);
	        if (!adaptor.getSchema().getClassByName(ReactomeJavaConstants.ReactionlikeEvent).isValidAttribute(ReactomeJavaConstants.regulatedBy)) {
	            // Before Regulation is migrated to RLE, we will generate stable id for Reguliation
	            stidClasses.add(ReactomeJavaConstants.Regulation);
	        }
	    }
	    return stidClasses;
	}
	
	/**
	 * Create a StableIdentifier instance for the passed GKInstance object.
	 * @param instance Instance for which to create stable id instance
	 * @param created Created instance edit instance to attach to newly created stable id instance
	 * @param fileAdaptor XMLFileAdaptor to which to save newly created stable id instance
	 * @return Stable identifier instance
	 * @throws Exception Thrown if unable to generate an identifier for the instance or if unable to set attribute
	 * values for the newly created StableIdentifier instance
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
	 * @param instance Instance for which to generate a stable identifier
	 * @return String containing the generated stable identifier value
	 * @throws Exception Thrown if unable to get species abbreviation for instance
	 */
    public String generateIdentifier(GKInstance instance) throws Exception {
        String species = getSpeciesForSTID(instance);
	    String id = "R-" + species + "-" + instance.getDBID();
        return id;
    }
    
    @Test
    public void testGenerateIdentifier() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("localhost",
                                            "gk_central_052417",
                                            "root",
                                            "macmysql01");
        Long dbId = 8982440L;
//        dbId = 68419L;
        GKInstance inst = dba.fetchInstance(dbId);
        String id = generateIdentifier(inst);
        System.out.println(inst + " -> " + id);
    }
	
	private String getSpeciesForSTID(GKInstance inst) throws Exception {
		String species = NUL_SPECIES;
//		if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.isChimeric)) {
//		    Boolean isChimeric = (Boolean) inst.getAttributeValue(ReactomeJavaConstants.isChimeric);
//		    if (isChimeric != null && isChimeric)
//		        return species; // Pre-assumptive for null for all isChimeric instances
//		}
		if (inst.getSchemClass().isa(ReactomeJavaConstants.Regulation))
			species = getSpeciesAbbrFromRegulation(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.Event))
			species = getSpeciesFromEvent(inst);
		else if (inst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
			species = getSpeciesFromPhysicalEntity(inst);
		return species;
	}
	
	private String getSpeciesFromPhysicalEntity(GKInstance physicalEntity) throws Exception {
	    Set<GKInstance> speciesSet = grepAllSpeciesInPE(physicalEntity, true);
	    if (speciesSet.size() == 0)
	        return ALL_SPECIES;
	    else if (speciesSet.size() > 1)
	        return NUL_SPECIES;
	    else {
	        GKInstance species = speciesSet.iterator().next();
	        return getSpeciesAbbreviation(species);
	    }
	}
	
	/**
	 * Regulation doesn't have species. We want to get the species from its regulatedEntity.
	 * The implementation here is different from the original Perl script.
	 * @param regulation
	 * @throws Exception
	 */
	private String getSpeciesAbbrFromRegulation(GKInstance regulation) throws Exception {
	    if (!regulation.getSchemClass().isValidAttribute(ReactomeJavaConstants.regulatedEntity)) {
	        throw new IllegalArgumentException("Regulation will not be assigned stable identifier!");
	    }
		GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
		// This is a mandatory value. If nothing specified, most likely the regulation
		// should not be released.
		if (regulatedEntity == null)
			return NUL_SPECIES;
		// There are only two cases below
		if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.Event)) {
			return getSpeciesFromEvent(regulatedEntity);
		}
		else if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
			GKInstance catalyst = (GKInstance) regulatedEntity.getAttributeValue(ReactomeJavaConstants.physicalEntity);
			if (catalyst == null) {
				return NUL_SPECIES;
			}
			else {
				return getSpeciesFromPhysicalEntity(catalyst);
			}
		}
		// Default: We should not get to here.
		return NUL_SPECIES;
	}
	
	/**
	 * Species is a mandatory value. If nothing there, we will use NUL.
	 * @param event
	 * @throws Exception
	 */
	private String getSpeciesFromEvent(GKInstance event) throws Exception {
	    List<GKInstance> speciesSet = event.getAttributeValuesList(ReactomeJavaConstants.species);
	    if (speciesSet == null || speciesSet.size() == 0 || speciesSet.size() > 1)
	        return NUL_SPECIES;
		GKInstance species = speciesSet.get(0);
		return getSpeciesAbbreviation(species);
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
	
	private Set<GKInstance> grepAllSpeciesInPE(GKInstance pe,
	                                           boolean needRecursion) throws Exception {
	    Set<GKInstance> speciesSet = new HashSet<>();
	    if (pe.getSchemClass().isValidAttribute(ReactomeJavaConstants.species)) {
	        List<GKInstance> speciesList = pe.getAttributeValuesList(ReactomeJavaConstants.species);
	        if (speciesList != null && speciesList.size() > 0) {
	            speciesSet.addAll(speciesList);
	        }
	    }
	    if (speciesSet.size() == 0 && needRecursion) {
	        grepAllSpeciesInPE(pe, speciesSet);
	    }
	    return speciesSet;
	}
	
	private void grepAllSpeciesInPE(GKInstance pe, Set<GKInstance> speciesSet) throws Exception {
	    Set<GKInstance> wrappedPEs = InstanceUtilities.getContainedInstances(pe,
	                                                                         ReactomeJavaConstants.hasComponent,
	                                                                         ReactomeJavaConstants.hasCandidate,
	                                                                         ReactomeJavaConstants.hasMember,
	                                                                         ReactomeJavaConstants.repeatedUnit);
	    for (GKInstance wrappedPE : wrappedPEs) {
	        Set<GKInstance> wrappedSpecies = grepAllSpeciesInPE(wrappedPE, false);
	        speciesSet.addAll(wrappedSpecies);
	    }
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
	public void testSpeciesAbbreviation() throws Exception {
		PersistenceManager persistenceManager = PersistenceManager.getManager();
		// To avoid null exception
		persistenceManager.setDBConnectInfo(new Properties());
		Neo4JAdaptor dbAdaptor = persistenceManager.getActiveNeo4JAdaptor(null);
		Set<GKInstance> speciesInstances = (Set<GKInstance>) dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants.Species);
		System.out.println("Species\tAbbrevitaion");
		for (GKInstance speciesInstance : speciesInstances) {
		    String abbreviation = getSpeciesAbbreviation(speciesInstance);
			System.out.println(speciesInstance.getDisplayName() + "\t" + abbreviation);
		}
	}
	
	private String getSpeciesAbbreviation(GKInstance species) throws Exception {
		if (!species.getSchemClass().isa(ReactomeJavaConstants.Species)) {
			throw new IllegalArgumentException("Instance " + species.getDBID() + " is not a species instance");
		}
		if (species.isShell()) {
		    // We need to query the database to get the abbreviation
		    Neo4JAdaptor dba = PersistenceManager.getManager().getActiveNeo4JAdaptor(parentComponent);
		    // Want to replace species with the db copy
		    species = dba.fetchInstance(species.getDBID());
		    if (species == null)
		        throw new IllegalArgumentException("Cannot find species in the database: " + species);
		}
		// If species is shell, it should be replaced by a db copy already. However,
		// the db copy is not checked out for easy management.
		String abbreviation = (String) species.getAttributeValue(ReactomeJavaConstants.abbreviation);
		if (abbreviation == null || abbreviation.isEmpty()) {
			throw new IllegalArgumentException(species.getDisplayName() + " has no abbreviation");
		}
		return abbreviation;
	}
}
