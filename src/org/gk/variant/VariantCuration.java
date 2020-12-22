package org.gk.variant;

import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gk.model.ReactomeJavaConstants;

public class VariantCuration {
	
	private MySQLAdaptor mysqladaptor;
	
	private String geneName;
	
	private List<GKInstance> referenceGeneProducts;
	
	private List<GKInstance> wtEwases;
	
	private Set<GKInstance> complexesAndEntitySet = new HashSet<>();
	
	private Map<GKInstance, List<GKInstance>> complexesAndEntitySets = new HashMap<>();
	
	private Map<GKInstance, List<GKInstance>> reactions = new HashMap<>();
			
	public VariantCuration(MySQLAdaptor mysqladaptor, String geneName) {
		setMysqladaptor(mysqladaptor);
		setGeneName(geneName);
	}

	public MySQLAdaptor getMysqladaptor() {
		return mysqladaptor;
	}

	public void setMysqladaptor(MySQLAdaptor mysqladaptor) {
		this.mysqladaptor = mysqladaptor;
	}

	public String getGeneName() {
		return geneName;
	}

	public void setGeneName(String geneName) {
		this.geneName = geneName;
	}
	
	public List<GKInstance> getReferenceGeneProducts() throws Exception {
		if (referenceGeneProducts != null && !referenceGeneProducts.isEmpty())
			return referenceGeneProducts;
		
        GKInstance human = mysqladaptor.fetchInstance(ReactomeJavaConstants.humanID);
        AttributeQueryRequest request = mysqladaptor.createAttributeQueryRequest(ReactomeJavaConstants.ReferenceGeneProduct, 
                                                                                 ReactomeJavaConstants.geneName,
                                                                                 "=",
                                                                                 geneName);
        List<AttributeQueryRequest> queryList = new ArrayList<>();
        queryList.add(request);
        request = mysqladaptor.createAttributeQueryRequest(ReactomeJavaConstants.ReferenceGeneProduct, 
                                                           ReactomeJavaConstants.species,
                                                           "=",
                                                           human);
        queryList.add(request);
        @SuppressWarnings("unchecked")
		Set<GKInstance> referenceEntities = mysqladaptor.fetchInstance(queryList);
        
        List<GKInstance> referenceEntitiesList = new ArrayList<>(referenceEntities);
        
        InstanceUtilities.sortInstances(referenceEntitiesList);
        
        setReferenceGeneProducts(referenceEntitiesList);
		
		return referenceEntitiesList;
	}

	public void setReferenceGeneProducts(List<GKInstance> referenceGeneProducts) {
		this.referenceGeneProducts = referenceGeneProducts;
	}

	@SuppressWarnings("unchecked")
	public List<GKInstance> getWtEwases() throws Exception {
		if (wtEwases != null && !wtEwases.isEmpty())
			return wtEwases;
		
		List<GKInstance> referenceEntities = getReferenceGeneProducts();
		
		if (referenceEntities == null || referenceEntities.isEmpty())
			return null;
		
        Set<GKInstance> ewases = new HashSet<>();        
        
        for(GKInstance ref : referenceEntities) {
        	Set<GKInstance> relatedEwases 
			   = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.EntityWithAccessionedSequence, 
			                                                             ReactomeJavaConstants.referenceEntity,
			                                                             "=",
			                                                             ref);
 		    if (relatedEwases != null && !relatedEwases.isEmpty())
 		       ewases.addAll(relatedEwases);
        	
        }
        
        List<GKInstance> wtEwasList = new ArrayList<>(ewases);
        
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
		
		List<GKInstance> complexesAndEntityList = new ArrayList<>(complexesAndEntitySet);
		
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
		
		Set<GKInstance> reactionSet = new HashSet<>();
		
		Set<GKInstance> reactionsWithRelatedInput 
		   = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
		                                                             ReactomeJavaConstants.input,
		                                                             "=",
		                                                             entity);
		
		if (reactionsWithRelatedInput != null && !reactionsWithRelatedInput.isEmpty())
			reactionSet.addAll(reactionsWithRelatedInput);
		
	    Set<GKInstance> catalystActivitiesRelated 
	       = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.CatalystActivity, 
                                                                     ReactomeJavaConstants.physicalEntity,
                                                                     "=",
                                                                     entity);
	    
	    if (catalystActivitiesRelated != null && !catalystActivitiesRelated.isEmpty()) {
	    	for (GKInstance ca: catalystActivitiesRelated) {
	    		Set<GKInstance> reactionsWithRelatedCatalystActivity 
	 		        = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
	 		                                                                  ReactomeJavaConstants.catalystActivity,
	 		                                                                  "=",
	 		                                                                  ca);	    		
	    		if (reactionsWithRelatedInput != null && !reactionsWithRelatedInput.isEmpty())
	    			reactionSet.addAll(reactionsWithRelatedCatalystActivity);	    			
	    	}
	    	
	    }
	    
	    Set<GKInstance> regulationsRelated 
	       = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Regulation, 
                                                                  ReactomeJavaConstants.regulator,
                                                                  "=",
                                                                  entity);
	    
	    if (regulationsRelated != null && !regulationsRelated.isEmpty()) {
	    	for (GKInstance reg: regulationsRelated) {
	    		Set<GKInstance> reactionsWithRelatedCatalystActivity 
	 		        = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, 
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
		Set<GKInstance> containingComplexAndEntitySets = new HashSet<>();
		
		Set<GKInstance> containingComplexes 
		   = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.Complex, 
                                                                     ReactomeJavaConstants.hasComponent,
                                                                     "=",
                                                                     physicalEntity);	
		
		if (containingComplexes != null && !containingComplexes.isEmpty()) 
			containingComplexAndEntitySets.addAll(containingComplexes);				
				
		Set<GKInstance> containingEntitySets 
		   = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.EntitySet, 
                                                                     ReactomeJavaConstants.hasMember,
                                                                     "=",
                                                                      physicalEntity);
		if (containingEntitySets != null && !containingEntitySets.isEmpty()) {
			containingComplexAndEntitySets.addAll(containingEntitySets);
		}
		
		Set<GKInstance> containingEntitySetsHavingCandidates 
		   = (Set<GKInstance>) mysqladaptor.fetchInstanceByAttribute(ReactomeJavaConstants.CandidateSet, 
                                                                     ReactomeJavaConstants.hasCandidate,
                                                                     "=",
                                                                     physicalEntity);
		if (containingEntitySetsHavingCandidates != null && !containingEntitySetsHavingCandidates.isEmpty()) {
			containingComplexAndEntitySets.addAll(containingEntitySetsHavingCandidates);
		}
		
		return containingComplexAndEntitySets;
	}

}
