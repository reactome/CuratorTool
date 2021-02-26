package org.gk.variant;

import java.io.BufferedReader;
import java.io.FileReader;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class VariantBatchProcessor {
	private String src;
	private String dest;
	
	public VariantBatchProcessor(String src, String dest) {
		this.src = src;
		this.dest = dest;
	}
	
	@SuppressWarnings({ "resource", "unused" })
	public int createdEwases() throws Exception {
		int ct = 0;
		if (src == null || dest == null)
  		    throw new Exception("Source file or destination result file name missing.");
		
		try{
            BufferedReader reader = new BufferedReader(new FileReader(src));
            
            String line = null;
            String[] fileds;
            
            PersistenceManager mngr = PersistenceManager.getManager(); 
    		XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
            
            // To do: create a private method to for common set statements in order to reduce redundancy 	
    		// To do: debug the following:
    		// When the last field, speciesId, is deleted from the sample input file, there is java.lang.ArrayIndexOutOfBoundsException.
    		// Maybe a work-around would be to move the required mutationType field to the last field.
    		// Or, perhaps, if the input file is from the real world Excel file, this error would not happen.
            while((line = reader.readLine()) != null) {             
                if(ct > 0) {                    // skip the first description line
                	fileds = line.split("\t");
            		String name = fileds[0];
            		String mutationType = fileds[1];             // key field to decide on which subclass will be used
            		String compartmentID = fileds[2];
            		String alteredAminoAcidFragment = fileds[3];  // optional
            		String referenceDatabaseId = fileds[4];       // optional; when empty, set to COSMID
            		String refIdentifiers = fileds[5];
            		String dieaseIds = fileds[6];     // optional; when empty, set to cancer
            		String pmids = fileds[7];         // optional
            		String speciesIds = fileds[8];    // optional; when empty, be set default to human
            		            		            		
            		if (mutationType.equalsIgnoreCase(ReactomeJavaConstants.NonsenseMutation)) {
            			NonsenseMutationVariantCuration variantCuration = new NonsenseMutationVariantCuration(); 
            	        variantCuration.setName(name);
            	        variantCuration.setCompartmentId(compartmentID);
            	        if (referenceDatabaseId != null && !referenceDatabaseId.isEmpty())
            	           variantCuration.setReferenceDatabaseId(referenceDatabaseId);
            	        
            	        variantCuration.setCosmicIds(refIdentifiers);
            	        if (dieaseIds != null && !dieaseIds.isEmpty())
            	           variantCuration.setDiseaseIds(dieaseIds); 
            	        
            	        if (pmids != null && !pmids.isEmpty())
             	           variantCuration.setPmids(pmids); 
            	        
            	        if (speciesIds != null && !speciesIds.isEmpty())
             	           variantCuration.setSpeciesId(Long.parseLong(speciesIds)); 
            	        
            	        GKInstance ewas = variantCuration.createNonsenseMutationEwas();
            			
            		} else if (mutationType.equalsIgnoreCase(ReactomeJavaConstants.FragmentReplacedModification)) {
            	        FrameShiftVariantCuration variantCuration = new FrameShiftVariantCuration(); 
            	        variantCuration.setName(name);
            	        variantCuration.setCompartmentId(compartmentID);
            	        if (referenceDatabaseId != null && !referenceDatabaseId.isEmpty())
             	           variantCuration.setReferenceDatabaseId(referenceDatabaseId);
             	        
             	        variantCuration.setCosmicIds(refIdentifiers);
             	        if (dieaseIds != null && !dieaseIds.isEmpty())
             	           variantCuration.setDiseaseIds(dieaseIds); 
             	        
             	        if (pmids != null && !pmids.isEmpty())
              	           variantCuration.setPmids(pmids); 
             	        
             	        if (speciesIds != null && !speciesIds.isEmpty())
              	           variantCuration.setSpeciesId(Long.parseLong(speciesIds));
             	       
             	        if (alteredAminoAcidFragment != null && !alteredAminoAcidFragment.isEmpty())
             	           variantCuration.setAlteredAminoAcid(alteredAminoAcidFragment);
             	        else
             	    	   throw new Exception("line " + ct + " is not a FragmentReplacedModification mutation.");
             	                    	        
            	        GKInstance ewas = variantCuration.createFrameShiftMutationEwas();
            	        
            			
            		} else if (mutationType.equalsIgnoreCase(ReactomeJavaConstants.FragmentInsertionModification)) {
            			
            			FusioneVariantCuration variantCuration = new FusioneVariantCuration();
            			
            			variantCuration.setName(name);
            	        variantCuration.setCompartmentId(compartmentID);
            	        if (referenceDatabaseId != null && !referenceDatabaseId.isEmpty())
             	           variantCuration.setReferenceDatabaseId(referenceDatabaseId);
             	        
             	        variantCuration.setCosmicIds(refIdentifiers);
             	        if (dieaseIds != null && !dieaseIds.isEmpty())
             	           variantCuration.setDiseaseIds(dieaseIds); 
             	        
             	        if (pmids != null && !pmids.isEmpty())
              	           variantCuration.setPmids(pmids); 
             	        
             	        if (speciesIds != null && !speciesIds.isEmpty())
              	           variantCuration.setSpeciesId(Long.parseLong(speciesIds));
             	       
             	        if (alteredAminoAcidFragment != null && !alteredAminoAcidFragment.isEmpty())
             	           variantCuration.setAlteredAminoAcid(alteredAminoAcidFragment); 
             	        
             	        GKInstance ewas = variantCuration.createFusioneMutationEwas();
            			
            		}           		
            		
                }  // end of if (ct > 0)
                ct++;
                
            }     // end of while loop
            
            reader.close();
            ct--; 
            
            fileAdaptor.save(dest);
            
        } catch(Exception e){
            e.printStackTrace();
        }
		
		return ct;
		
	}
	
	

}
