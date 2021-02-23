package org.gk.variant;

import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.model.ReactomeJavaConstants;

public class VariantCuration {
			
	private String dest;
	
	private String name;
	
	private String mutationType;
	
	private String coordinate;
	
	private String wtResidue;
	
	private String replacedResidue;
	
	private String startCoordinate; 
	
	private String endCoordinate; 
			
	private List<GKInstance> wtEwases;
	
	private Set<GKInstance> complexesAndEntitySet = new HashSet<>();
	
	private Map<GKInstance, List<GKInstance>> complexesAndEntitySets = new HashMap<>();
	
	private Map<GKInstance, List<GKInstance>> reactions = new HashMap<>();
		
	private String compartmentId;
	
	private String psiModId;
	
	private String referenceDatabaseId;
	
	private String cosmicIds;
	
	private String diseaseIds;
	
	private String alteredAminoAcid;
	
	private Hashtable<String, String> aaDict = new Hashtable<String, String>();
	
	public VariantCuration() {
		aaDict.put("A", "L-alanine");
		aaDict.put("R", "L-arginine");
		aaDict.put("N", "L-asparagine");
		aaDict.put("D", "L-aspartic acid");
		aaDict.put("C", "L-cysteine");
		aaDict.put("E", "L-glutamic acid");
		aaDict.put("Q", "L-glutamine");
		aaDict.put("H", "L-histidine");
		aaDict.put("I", "L-isoleucine");
		aaDict.put("L", "L-leucine");
		aaDict.put("K", "L-lysine");
		aaDict.put("M", "L-methionine");
		aaDict.put("F", "L-phenylalanine");
		aaDict.put("P", "L-proline");
		aaDict.put("U", "L-selenocysteine");
		aaDict.put("S", "L-serine");
		aaDict.put("T", "L-threonine");
		aaDict.put("W", "L-tryptophan");
		aaDict.put("Y", "L-tyrosine");
		aaDict.put("V", "L-valine");
		aaDict.put("G", "glycine");
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	private List<GKInstance> getReferenceGeneProducts(String gene) throws Exception {		
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();		
		
        GKInstance human = dba.fetchInstance(ReactomeJavaConstants.humanID);
        AttributeQueryRequest request = dba.createAttributeQueryRequest(ReactomeJavaConstants.ReferenceGeneProduct, 
                                                                        ReactomeJavaConstants.geneName,
                                                                        "=",
                                                                        gene);
        List<AttributeQueryRequest> queryList = new ArrayList<>();
        queryList.add(request);
        request = dba.createAttributeQueryRequest(ReactomeJavaConstants.ReferenceGeneProduct, 
                                                  ReactomeJavaConstants.species,
                                                  "=",
                                                  human);
        queryList.add(request);
        @SuppressWarnings("unchecked")
		Set<GKInstance> referenceEntities = dba.fetchInstance(queryList);
        
        List<GKInstance> referenceEntitiesList = new ArrayList<>(referenceEntities);
        
        InstanceUtilities.sortInstances(referenceEntitiesList);
        		
		return referenceEntitiesList;
	}

	@SuppressWarnings("unchecked")
	public List<GKInstance> getWtEwases(String gene) throws Exception {
		if (wtEwases != null && !wtEwases.isEmpty())
			return wtEwases;
		
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();	
		
		GKInstance referenceEntity = getReferenceGeneProduct(gene);
		
        Set<GKInstance> ewases = new HashSet<>();      
        
    	Set<GKInstance> relatedEwases 
		   = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.EntityWithAccessionedSequence, 
		                                                    ReactomeJavaConstants.referenceEntity,
		                                                    "=",
		                                                    referenceEntity);
		if (relatedEwases != null && !relatedEwases.isEmpty())
		    ewases.addAll(relatedEwases);
        
        
        Set<GKInstance> wtEwases = getNonDiseaseEntities(ewases);
        
        List<GKInstance> wtEwasList = new ArrayList<>(wtEwases);
        
        InstanceUtilities.sortInstances(wtEwasList);
        
        setWtEwases(wtEwasList);

        return wtEwasList;
	}

	public void setWtEwases(List<GKInstance> wtEwases) {
		this.wtEwases = wtEwases;
	}

	public List<GKInstance> getComplexesAndEntitySets(GKInstance ewas) throws Exception {		
		if (ewas == null)
			return null;
		
		// check the cached value first
		if(complexesAndEntitySets.containsKey(ewas)) {
			return complexesAndEntitySets.get(ewas);
		}
		
		complexesAndEntitySet = new HashSet<>();
		
		Set<GKInstance> containingEntities = getContainingPhysicalEntities(ewas);
		if (containingEntities != null && !containingEntities.isEmpty()) {
			complexesAndEntitySet.addAll(containingEntities);
			
			// call the recursive method to add the containing complex and EntitySet instances to complexesAndEntitySets property
			addContainingPhysicalEntities(containingEntities);
		}
		
		Set<GKInstance> nonDiseaseComplexesAndEntities = getNonDiseaseEntities(complexesAndEntitySet);
		
		List<GKInstance> complexesAndEntityList = new ArrayList<>(nonDiseaseComplexesAndEntities);
		
		InstanceUtilities.sortInstances(complexesAndEntityList);
		
		complexesAndEntitySets.put(ewas, complexesAndEntityList);
		
		return complexesAndEntityList;
	}
	
	@SuppressWarnings("unchecked")
	public List<GKInstance> getReactions(GKInstance entity) throws Exception {

		if (entity == null)
			return null;
		
		// check the cached value first
		if(reactions.containsKey(entity)) {
			return reactions.get(entity);
		}
		
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();
		
		Set<GKInstance> reactionSet = new HashSet<>();
		
		Set<GKInstance> reactionsWithRelatedInput 
		   = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
		                                                    ReactomeJavaConstants.input,
		                                                    "=",
		                                                    entity);
		
		if (reactionsWithRelatedInput != null && !reactionsWithRelatedInput.isEmpty())
			reactionSet.addAll(reactionsWithRelatedInput);
		
	    Set<GKInstance> catalystActivitiesRelated 
	       = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.CatalystActivity, 
                                                            ReactomeJavaConstants.physicalEntity,
                                                            "=",
                                                            entity);
	    
	    if (catalystActivitiesRelated != null && !catalystActivitiesRelated.isEmpty()) {
	    	for (GKInstance ca: catalystActivitiesRelated) {
	    		Set<GKInstance> reactionsWithRelatedCatalystActivity 
	 		        = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
	 		                                                         ReactomeJavaConstants.catalystActivity,
	 		                                                         "=",
	 		                                                         ca);	    		
	    		if (reactionsWithRelatedInput != null && !reactionsWithRelatedInput.isEmpty())
	    			reactionSet.addAll(reactionsWithRelatedCatalystActivity);	    			
	    	}
	    	
	    }
	    
	    Set<GKInstance> regulationsRelated 
	       = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.Regulation, 
                                                            ReactomeJavaConstants.regulator,
                                                            "=",
                                                            entity);
	    
	    if (regulationsRelated != null && !regulationsRelated.isEmpty()) {
	    	for (GKInstance reg: regulationsRelated) {
	    		Set<GKInstance> reactionsWithRelatedCatalystActivity 
	 		        = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
	 		                                                         ReactomeJavaConstants.regulatedBy,
	 		                                                         "=",
	 		                                                         reg);	    		
	    		if (reactionsWithRelatedInput != null && !reactionsWithRelatedInput.isEmpty())
	    			reactionSet.addAll(reactionsWithRelatedCatalystActivity);	    			
	    	}
	    	
	    }
	    
		List<GKInstance> reactionList = new ArrayList<>(reactionSet);
		
		InstanceUtilities.sortInstances(reactionList);
    	
		reactions.put(entity, reactionList);
		
		return reactionList;
	}
	
	// recursive method
	private void addContainingPhysicalEntities(Set<GKInstance> physicalEntities) throws Exception {
		for(GKInstance pe : physicalEntities) {
			Set<GKInstance> containingComplexAndEntitySets = getContainingPhysicalEntities(pe);
			
			if (containingComplexAndEntitySets != null && !containingComplexAndEntitySets.isEmpty()) {
				// add them to the complexesAndEntitySets property
				complexesAndEntitySet.addAll(containingComplexAndEntitySets);
				
				// call the recursive method to get the next level of containing entities
				addContainingPhysicalEntities(containingComplexAndEntitySets);
			} else {
				return;
			}
			
		}				    		
    		
	}
	
	@SuppressWarnings("unchecked")
	private Set<GKInstance> getContainingPhysicalEntities(GKInstance physicalEntity) throws Exception {
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();
		
		Set<GKInstance> containingComplexAndEntitySets = new HashSet<>();
		
		Set<GKInstance> containingComplexes 
		   = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.Complex, 
                                                            ReactomeJavaConstants.hasComponent,
                                                            "=",
                                                            physicalEntity);	
		
		if (containingComplexes != null && !containingComplexes.isEmpty()) 
			containingComplexAndEntitySets.addAll(containingComplexes);				
				
		Set<GKInstance> containingEntitySets 
		   = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.EntitySet, 
                                                            ReactomeJavaConstants.hasMember,
                                                            "=",
                                                            physicalEntity);
		if (containingEntitySets != null && !containingEntitySets.isEmpty()) {
			containingComplexAndEntitySets.addAll(containingEntitySets);
		}
		
		Set<GKInstance> containingEntitySetsHavingCandidates 
		   = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.CandidateSet, 
                                                            ReactomeJavaConstants.hasCandidate,
                                                            "=",
                                                            physicalEntity);
		if (containingEntitySetsHavingCandidates != null && !containingEntitySetsHavingCandidates.isEmpty()) {
			containingComplexAndEntitySets.addAll(containingEntitySetsHavingCandidates);
		}
		
		return containingComplexAndEntitySets;
	}
	
	private Set<GKInstance> getNonDiseaseEntities(Set<GKInstance> entities) throws Exception {
		if (entities == null || entities.isEmpty())
			return entities;
		
		Set<GKInstance> wildTypeEntities = new HashSet<>();
		for (GKInstance entity : entities) {
			if (entity.getAttributeValue(ReactomeJavaConstants.disease) == null) {
				wildTypeEntities.add(entity);
			}
		}
		
		return wildTypeEntities;		
	}

	public String getCompartmentId() {
		return compartmentId;
	}

	public void setCompartmentId(String compartmentId) {
		this.compartmentId = compartmentId;
	}

	public String getPsiModId() {
		return psiModId;
	}

	public void setPsiModId(String psiModId) {
		this.psiModId = psiModId;
	}

	public String getCosmicIds() {
		return cosmicIds;
	}

	public void setCosmicIds(String cosmicIds) {
		this.cosmicIds = cosmicIds;
	}

	public String getDiseaseIds() {
		return diseaseIds;
	}

	public void setDiseaseIds(String diseaseIds) {
		this.diseaseIds = diseaseIds;
	}
	
	public String getAlteredAminoAcid() {
		return alteredAminoAcid;
	}
	
	public void setAlteredAminoAcid(String alteredAminoAcid) {
		this.alteredAminoAcid = alteredAminoAcid;
	}
	
	public GKInstance getReferenceGeneProduct(String gene) throws Exception {	
		if (gene == null)
			return null;
		
		List<GKInstance> refs = getReferenceGeneProducts(gene);;
		if (refs != null && !refs.isEmpty()) {
			for(GKInstance ref : refs) {
				if (ref.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct)) {
					return ref;
				}			
			}
		}
		return null;	
	}

	public String getDest() {
		return dest;
	}

	public void setDest(String dest) {
		this.dest = dest;
	}
	
	public GKInstance createReplacedResidueEwas() throws Exception {
		if (name == null || name.trim().isEmpty())
			throw new Exception("Gene name entered is empty string");
        		
		GKInstance ewas = createEwas(Integer.parseInt(startCoordinate), Integer.parseInt(endCoordinate), name);
        
        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
        
        GKInstance replacedResidueMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.ReplacedResidue);
        replacedResidueMutation.setAttributeValue(ReactomeJavaConstants.coordinate, Integer.parseInt(coordinate));
        GKInstance psiMod1 = getPsiMod(wtResidue, "removal");
        GKInstance localPsiMod1 = mngr.download(psiMod1);
        replacedResidueMutation.addAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod1);
        GKInstance psiMod2 = getPsiMod(replacedResidue, "residue");
        GKInstance localPsiMod2 = mngr.download(psiMod2);
        replacedResidueMutation.addAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod2);
        GKInstance referenceEntity = getReferenceGeneProduct(name);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        replacedResidueMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        String displayNameOfReplacedResidueMutation = InstanceDisplayNameGenerator.generateDisplayName(replacedResidueMutation);
        replacedResidueMutation.setDisplayName(displayNameOfReplacedResidueMutation);        
        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, replacedResidueMutation);
        
		String mutName = name + " " + wtResidue + coordinate + replacedResidue;
        ewas.setAttributeValue(ReactomeJavaConstants.name, mutName);
        
        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 
        		
		return ewas;
	}
	
	public GKInstance createNonsenseMutationEwas() throws Exception {
		if (name == null || name.trim().isEmpty())
			throw new Exception("Name is null or empty string");
		
		Pattern p = Pattern.compile("(\\w+)\\s+([A-Z])(\\d+)\\*");
        Matcher m = p.matcher(name);
        String mut = null;
        String geneName = null;
        String aaCode = null;
        if (m.find()) {
        	geneName = m.group(1);
        	aaCode = m.group(2);
            mut = m.group(3);
        } else {
        	throw new Exception("The name is not a nonsense mutation.");
        }
        
        int mutPnt = Integer.parseInt(mut);
        int start = 1;
        int end = mutPnt - 1;
        
		GKInstance ewas = createEwas(start, end, geneName);
        
        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
        
        GKInstance nonsenseMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.NonsenseMutation);
        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.coordinate, mutPnt);
        //GKInstance psiMod = dba.fetchInstance(Long.parseLong(psiModId));
        GKInstance psiMod = getPsiMod(aaCode, "removal");
        GKInstance localPsiMod = mngr.download(psiMod);
        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        String displayNameOfNonsenseMutation = InstanceDisplayNameGenerator.generateDisplayName(nonsenseMutation);
        nonsenseMutation.setDisplayName(displayNameOfNonsenseMutation);        
        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, nonsenseMutation);
        
        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 
        		
		return ewas;
	}

	public String getReferenceDatabaseId() {
		return referenceDatabaseId;
	}

	public void setReferenceDatabaseId(String referenceDatabaseId) {
		this.referenceDatabaseId = referenceDatabaseId;
	}
	
	public GKInstance createFrameShiftMutationEwas() throws Exception {       
        if (name == null || name.trim().isEmpty())
			throw new Exception("Name is null or empty string");
        
        PersistenceManager mngr = PersistenceManager.getManager(); 
		XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
		
		Pattern p = Pattern.compile("(\\w+)\\s+[A-Z](\\d+)[A-Z]fs\\*(\\d+)");
        Matcher m = p.matcher(name);
        String mut = null;
        String shft = null;
        String geneName = null;
        if (m.find()) {
        	geneName = m.group(1);
            mut = m.group(2);
            shft = m.group(3);
        } else {
        	throw new Exception("The name is not a frame shift mutation.");
        }
        
        int mutPnt = Integer.parseInt(mut);        
        int shftPnt = Integer.parseInt(shft);
        int start = 1;
        int end = mutPnt + shftPnt - 2;
        
		GKInstance ewas = createEwas(start, end, geneName);
        
        GKInstance frgmntRplcMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentReplacedModification);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, mutPnt);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, end);
        String displayNameOfFrgRplcMutation = InstanceDisplayNameGenerator.generateDisplayName(frgmntRplcMutation);
        frgmntRplcMutation.setDisplayName(displayNameOfFrgRplcMutation);
    	frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);    	

        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, frgmntRplcMutation);
        
        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 
		
		return ewas;
	}
	
	public GKInstance createFusioneMutationEwas() throws Exception {
		if (name == null || name.trim().isEmpty())
			throw new Exception("Name is null or empty string");
		
		PersistenceManager mngr = PersistenceManager.getManager(); 
		XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
		
		Pattern p = Pattern.compile("(\\w+)\\((\\d+)\\-(\\d+)\\)\\-(\\w+)\\((\\d+)\\-(\\d+)\\)\\s*fusion");
        Matcher m = p.matcher(name);
        String geneName1 = null;
        String start1 = null;
        String end1 = null;
        String geneName2 = null;
        String start2 = null;
        String end2 = null;
        if (m.find()) {
        	geneName1 = m.group(1);
            start1 = m.group(2);
            end1 = m.group(3);
            geneName2 = m.group(4);
            start2 = m.group(5);
            end2 = m.group(6);            
        } else {
        	throw new Exception("The name is not a fusion mutation.");
        }
		
        int endInt1 = Integer.parseInt(end1); 
        int coord = endInt1 + 1;
        int startInt2 = Integer.parseInt(start2);
        int endInt12 = Integer.parseInt(end2);
        
		GKInstance ewas = createEwas(Integer.parseInt(start1), endInt1, geneName1);
        
        GKInstance frgmntInsMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentInsertionModification);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName2);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.coordinate, coord);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, startInt2);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, endInt12);
        String displayNameOfFrgmntInstMutation = InstanceDisplayNameGenerator.generateDisplayName(frgmntInsMutation);
        frgmntInsMutation.setDisplayName(displayNameOfFrgmntInstMutation);

        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, frgmntInsMutation);
        
        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 
		
		return ewas;
	}
	
	private GKInstance createEwas(int start, int end, String gene) throws Exception {  
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();
		XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
        
		GKInstance ewas = fileAdaptor.createNewInstance(ReactomeJavaConstants.EntityWithAccessionedSequence);
		ewas.setAttributeValue(ReactomeJavaConstants.name, name);
		GKInstance referenceEntity = getReferenceGeneProduct(gene);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        ewas.setAttributeValue(ReactomeJavaConstants.referenceEntity, localReferenceEntity);
        ewas.setAttributeValue(ReactomeJavaConstants.startCoordinate, start);
        ewas.setAttributeValue(ReactomeJavaConstants.endCoordinate, end);
        GKInstance compartment = dba.fetchInstance(Long.parseLong(compartmentId));
        GKInstance localCompartment = mngr.download(compartment);
        ewas.setAttributeValue(ReactomeJavaConstants.compartment, localCompartment);        
        
        GKInstance referenceDatabase = dba.fetchInstance(Long.parseLong(referenceDatabaseId));
        GKInstance localMutReferenceDatabase = mngr.download(referenceDatabase);

        String[] cosmicIDsArray = cosmicIds.split("\\|");
        for(String cosmicID : cosmicIDsArray){
        	GKInstance crossReference = fileAdaptor.createNewInstance(ReactomeJavaConstants.DatabaseIdentifier);
        	crossReference.setAttributeValue(ReactomeJavaConstants.referenceDatabase, localMutReferenceDatabase);
        	crossReference.setAttributeValue(ReactomeJavaConstants.identifier, cosmicID);
        	String displaynameCrossRef = InstanceDisplayNameGenerator.generateDisplayName(crossReference);
        	crossReference.setDisplayName(displaynameCrossRef);
        	
            ewas.addAttributeValue(ReactomeJavaConstants.crossReference, crossReference);
        }
        
        String[] diseaseArray = diseaseIds.split("\\|");
        for(String diseaseID : diseaseArray){
            GKInstance disease = dba.fetchInstance(Long.parseLong(diseaseID));
            GKInstance localDisease = mngr.download(disease);
            ewas.addAttributeValue(ReactomeJavaConstants.disease, localDisease);                       	
        }                      
        GKInstance species = dba.fetchInstance(ReactomeJavaConstants.humanID); 
        GKInstance localSpecies = mngr.download(species);
        ewas.setAttributeValue(ReactomeJavaConstants.species, localSpecies); 
        
		return ewas;
	}
	
	@SuppressWarnings("unchecked")
	public GKInstance getPsiMod(String aaCode, String surfix) throws Exception {
		if (aaCode == null || aaCode.isEmpty())
			throw new Exception("Amino acid code is empty.");
		
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();
		
		String aa = aaDict.get(aaCode);
		
		String psiModName = aa + " " + surfix;
		
		Set<GKInstance> gkInstances 
		   = (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.PsiMod, 
                                                            ReactomeJavaConstants.name,
                                                            "=",
                                                            psiModName);
		
		for (GKInstance psiMod : gkInstances) {
			return psiMod;
		}
				
		return null;
	}
	
	public String getMutationType() {
		return mutationType;
	}
	
	public void setmMtationType(String mutationType) {
		this.mutationType = mutationType;
	}
	
	public String getCoordinate() {
		return coordinate;
	}
	
	public void setCoordinate(String coordinate) {		
	    this.coordinate = coordinate;	
	}
	
	public String getWtResidue() {
		return wtResidue;
	}
	
	public void setWtResidue(String wtResidue) {
		this.wtResidue = wtResidue;
	}
	
	public String getReplacedResidue() {
	    return replacedResidue;
    }
	
	public void setReplacedResidue(String replacedResidue) {
		this.replacedResidue = replacedResidue;
	}
	
	public String getStartCoordinate() {
		return startCoordinate;
	}
	
	public void setStartCoordinate(String startCoordinate) {
		this .startCoordinate = startCoordinate;
	}
	
	public String getEndCoordinate() {
		return endCoordinate;
	}
	
	public void setEndCoordinate(String endCoordinate) {
		this .endCoordinate = endCoordinate;
	}
}
