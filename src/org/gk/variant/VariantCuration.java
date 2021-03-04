package org.gk.variant;

import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.property.PMIDXMLInfoFetcher2;
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

import org.gk.model.ReactomeJavaConstants;
import org.gk.model.Reference;

public abstract class VariantCuration {
			
	protected String dest;
	
	protected String name;
			
	protected List<GKInstance> wtEwases;
	
	protected Set<GKInstance> complexesAndEntitySet = new HashSet<>();
	
	protected Map<GKInstance, List<GKInstance>> complexesAndEntitySets = new HashMap<>();
	
	protected Map<GKInstance, List<GKInstance>> reactions = new HashMap<>();
		
	protected String compartmentId;
	
	protected String psiModId;
	
	protected String referenceDatabaseId;
	
	protected String cosmicIds;     // to do: refactor this from String to Long
	
	protected String diseaseIds;    // keep this as String since there could be multiple diseaseIds delimited by |
		
	protected String pmids;
	
	protected Long speciesId;
	
	protected Hashtable<String, String> aaDict = new Hashtable<String, String>();
	
	public VariantCuration() {
		setSpeciesId(ReactomeJavaConstants.humanID);    // default species set to human
		setReferenceDatabaseId(ReactomeJavaConstants.cosmicID.toString());   // default is COSMIC
		setDiseaseIds(ReactomeJavaConstants.cancerID.toString());  // default is cancel
	}
	
	public String getDest() {
		return dest;
	}

	public void setDest(String dest) {
		this.dest = dest;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public String getPmids() {
		return pmids;
	}

	public void setPmids(String pmids) {
		this.pmids = pmids;
	}

	public String getReferenceDatabaseId() {
		return referenceDatabaseId;
	}

	public void setReferenceDatabaseId(String referenceDatabaseId) {
		this.referenceDatabaseId = referenceDatabaseId;
	}
	
	public Long getSpeciesId() {
		return speciesId;
	}

	public void setSpeciesId(Long speciesId) {
		this.speciesId = speciesId;
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
	
	// recursive method
	protected void addContainingPhysicalEntities(Set<GKInstance> physicalEntities) throws Exception {
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
	protected Set<GKInstance> getContainingPhysicalEntities(GKInstance physicalEntity) throws Exception {
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
	
	protected Set<GKInstance> getNonDiseaseEntities(Set<GKInstance> entities) throws Exception {
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
	
	protected List<GKInstance> getReferenceGeneProducts(String gene) throws Exception {		
		PersistenceManager mngr = PersistenceManager.getManager(); 
		MySQLAdaptor dba = mngr.getActiveMySQLAdaptor();		
		
        GKInstance human = dba.fetchInstance(speciesId);
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
	
	public abstract GKInstance createModifiedEWAS() throws Exception;
	
	@SuppressWarnings("unchecked")
	protected GKInstance createEwas(int start, int end, String gene) throws Exception {  
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
        GKInstance species = dba.fetchInstance(speciesId); 
        GKInstance localSpecies = mngr.download(species);
        ewas.setAttributeValue(ReactomeJavaConstants.species, localSpecies); 
        
        if (pmids != null && !pmids.isEmpty()) {
        	 String[] pmidArray = pmids.split("\\|");
             for(String pmid : pmidArray) {          	
				Set<GKInstance> pubsFound = 
            			 (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.LiteratureReference, 
            					                                        ReactomeJavaConstants.pubMedIdentifier, 
            					                                        "=", 
            					                                        pmid);
                 if(pubsFound == null) {
                     PMIDXMLInfoFetcher2 fetcher = new PMIDXMLInfoFetcher2();                            
                     Long pmidLong = Long.parseLong(pmids);                            
                     Integer pmidInt = Integer.parseInt(pmid);                           
                     Reference ref = fetcher.fetchInfo(pmidLong);                                                      
                     GKInstance newPub;                            
                     if(ref == null) {
                     	System.out.println("fetcher.fetchInfo(pmidLong) NOT WORKING");
                     } else {                           	                            	
                     	newPub = fileAdaptor.createNewInstance(ReactomeJavaConstants.LiteratureReference);
                     	newPub.setAttributeValue(ReactomeJavaConstants.author, ref.getAuthor());
                     	newPub.setAttributeValue(ReactomeJavaConstants.journal, ref.getJournal());
                     	newPub.setAttributeValue(ReactomeJavaConstants.pages, ref.getPage());
                     	newPub.setAttributeValue(ReactomeJavaConstants.pubMedIdentifier, pmidInt);
                     	newPub.setAttributeValue(ReactomeJavaConstants.title, ref.getTitle());
                     	newPub.setAttributeValue(ReactomeJavaConstants.volume, Integer.parseInt(ref.getVolume()));
                     	newPub.setAttributeValue(ReactomeJavaConstants.year, new Integer(ref.getYear()));                            	
                     	
                     	String displayNameOfNewPub = InstanceDisplayNameGenerator.generateDisplayName(newPub);
                     	newPub.setDisplayName(displayNameOfNewPub); 
                     	ewas.addAttributeValue(ReactomeJavaConstants.literatureReference, newPub); 
                     }
                 } else {
                 	for (GKInstance pub : pubsFound) {
                 		GKInstance localPub = mngr.download(pub);
                 		ewas.addAttributeValue(ReactomeJavaConstants.literatureReference, localPub);  

                 	}
                 }  
            	 
             }     	
        	
        }
        
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
	
	protected void setAminoAcidDict() {
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
	
}
