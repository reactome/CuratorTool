package org.gk.variant;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.model.Reference;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.property.PMIDXMLInfoFetcher2;

public class VariantBatchProcessor {
	private String src;
	private String dest;
	private MySQLAdaptor dba;
	PersistenceManager mngr;
	
	private String name;
	
	private String referenceEntityId;
	
	private String compartmentId;
	
	private String psiModId;
	
	private String cosmicIds;
	
	private String diseaseIds;
	
	private String alteredAminoAcid;
	
	public VariantBatchProcessor(String src, String dest, MySQLAdaptor dba) {
		this.src = src;
		this.dest = dest;
		this.dba = dba;
		mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
	}
	
	public VariantBatchProcessor(String name, String reference, String compartment, String psi, String cosmids, String diseases, MySQLAdaptor dba) {
		this.name = name;
		referenceEntityId = reference;
		compartmentId = compartment;
		psiModId = psi;
		cosmicIds = cosmids;
		diseaseIds = diseases;
		this.dba = dba;
		mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
	}
	
	public VariantBatchProcessor(String name, String reference, String compartment, String psi, String cosmids, String diseases, String alteredAa, MySQLAdaptor dba) {
		this.name = name;
		referenceEntityId = reference;
		compartmentId = compartment;
		psiModId = psi;
		cosmicIds = cosmids;
		diseaseIds = diseases;
		alteredAminoAcid = alteredAa;
		this.dba = dba;
		mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
	}
	
	@SuppressWarnings("unchecked")
	public int processEwases() throws Exception {
		int ct = 0;
		if (src == null || dest == null || dba == null || mngr == null)
			return 0;
        
        try{
            BufferedReader reader = new BufferedReader(new FileReader(src));
            
            String line = null;
            String[] fileds;
    		    		
    		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
    		mngr.setActiveFileAdaptor(fileAdaptor);
    		    		
            while((line = reader.readLine()) != null) {             
                if(ct > 0) {                    // skip the first description line
                	fileds = line.split("\t");
            		String name = fileds[0];
            		GKInstance ewas = fileAdaptor.createNewInstance(ReactomeJavaConstants.EntityWithAccessionedSequence);
            		ewas.setAttributeValue(ReactomeJavaConstants.name, name);
                    String referenceEntityID = fileds[1];
                    GKInstance referenceEntity = dba.fetchInstance(Long.parseLong(referenceEntityID));
                    GKInstance localReferenceEntity = mngr.download(referenceEntity);
                    ewas.setAttributeValue(ReactomeJavaConstants.referenceEntity, localReferenceEntity);
                    String start = fileds[2];
                    Integer startCoordinate = Integer.parseInt(start);
                    ewas.setAttributeValue(ReactomeJavaConstants.startCoordinate, startCoordinate);
                    String end = fileds[3];
                    Integer endCoordinate = Integer.parseInt(end);
                    ewas.setAttributeValue(ReactomeJavaConstants.endCoordinate, endCoordinate);
                    String compartmentID = fileds[4];
                    GKInstance compartment = dba.fetchInstance(Long.parseLong(compartmentID));
                    GKInstance localCompartment = mngr.download(compartment);
                    ewas.setAttributeValue(ReactomeJavaConstants.compartment, localCompartment);
                    
                    String hasModifiedResidue = fileds[5];
                    if (hasModifiedResidue.equals(ReactomeJavaConstants.NonsenseMutation)) {
                        GKInstance nonsenseMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.NonsenseMutation);
                        String coordinate = fileds[6];
                        Integer mutCoordinate = Integer.parseInt(coordinate);
                        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.coordinate, mutCoordinate);
                        String psiModID = fileds[7];
                        GKInstance psiMod = dba.fetchInstance(Long.parseLong(psiModID));
                        GKInstance localPsiMod = mngr.download(psiMod);
                        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod);
                        String mutRefSeqID = fileds[8];
                        GKInstance mutReferenceSequence = dba.fetchInstance(Long.parseLong(mutRefSeqID));
                        GKInstance localMutReferenceSequence = mngr.download(mutReferenceSequence);
                        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localMutReferenceSequence);
                        String displayNameOfNonsenseMutation = InstanceDisplayNameGenerator.generateDisplayName(nonsenseMutation);
                        nonsenseMutation.setDisplayName(displayNameOfNonsenseMutation);
                        
                        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, nonsenseMutation);
                        
                        String referenceDatabaseID = fileds[10];
                        GKInstance referenceDatabase = dba.fetchInstance(Long.parseLong(referenceDatabaseID));
                        GKInstance localMutReferenceDatabase = mngr.download(referenceDatabase);
                        String cosmicIDs = fileds[11];

                        String[] cosmicIDsArray = cosmicIDs.split("\\|");
                        for(String cosmicID : cosmicIDsArray){
                        	GKInstance crossReference = fileAdaptor.createNewInstance(ReactomeJavaConstants.DatabaseIdentifier);
                        	crossReference.setAttributeValue(ReactomeJavaConstants.referenceDatabase, localMutReferenceDatabase);
                        	crossReference.setAttributeValue(ReactomeJavaConstants.identifier, cosmicID);
                        	String displaynameCrossRef = InstanceDisplayNameGenerator.generateDisplayName(crossReference);
                        	crossReference.setDisplayName(displaynameCrossRef);
                        	
                            ewas.addAttributeValue(ReactomeJavaConstants.crossReference, crossReference);
                        }
                        
                        String diseaseID = fileds[12];
                        GKInstance disease = dba.fetchInstance(Long.parseLong(diseaseID));
                        GKInstance localDisease = mngr.download(disease);
                        ewas.setAttributeValue(ReactomeJavaConstants.disease, localDisease);                        
                        String speciesID = fileds[13];
                        GKInstance species = dba.fetchInstance(Long.parseLong(speciesID)); 
                        GKInstance localSpecies = mngr.download(species);
                        ewas.setAttributeValue(ReactomeJavaConstants.species, localSpecies); 
                        
                    } else if (hasModifiedResidue.equals(ReactomeJavaConstants.FragmentReplacedModification)) {
                    	GKInstance frgmntRplcMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentReplacedModification);
                    	String alteredAminoAcid= fileds[6];
                    	String frameEnd= fileds[7];
                    	String frgRplRefSeqID= fileds[8];
                    	String frameStart= fileds[9];
                    	GKInstance frgRplcReferenceSequence = dba.fetchInstance(Long.parseLong(frgRplRefSeqID));
                        GKInstance localFrgRplcReferenceSequence = mngr.download(frgRplcReferenceSequence);
                        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localFrgRplcReferenceSequence);
                        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);
                        Integer frgRplcStartCoordinate = Integer.parseInt(frameStart);
                        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, frgRplcStartCoordinate);
                        Integer frgRplcEndCoordinate = Integer.parseInt(frameEnd);
                        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, frgRplcEndCoordinate);
                        String displayNameOfFrgRplcMutation = InstanceDisplayNameGenerator.generateDisplayName(frgmntRplcMutation);
                        frgmntRplcMutation.setDisplayName(displayNameOfFrgRplcMutation);
                        ewas.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, frgmntRplcMutation);

                    	frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);
                    	
                    	String referenceDatabaseID = fileds[11];
                        GKInstance referenceDatabase = dba.fetchInstance(Long.parseLong(referenceDatabaseID));
                        GKInstance localMutReferenceDatabase = mngr.download(referenceDatabase);
                        String cosmicIDs = fileds[12];

                        String[] cosmicIDsArray = cosmicIDs.split("\\|");
                        for(String cosmicID : cosmicIDsArray){
                        	GKInstance crossReference = fileAdaptor.createNewInstance(ReactomeJavaConstants.DatabaseIdentifier);
                        	crossReference.setAttributeValue(ReactomeJavaConstants.referenceDatabase, localMutReferenceDatabase);
                        	crossReference.setAttributeValue(ReactomeJavaConstants.identifier, cosmicID);
                        	String displaynameCrossRef = InstanceDisplayNameGenerator.generateDisplayName(crossReference);
                        	crossReference.setDisplayName(displaynameCrossRef);
                        	
                            ewas.addAttributeValue(ReactomeJavaConstants.crossReference, crossReference);
                        }
                        
                        String diseaseID = fileds[13];
                        GKInstance disease = dba.fetchInstance(Long.parseLong(diseaseID));
                        GKInstance localDisease = mngr.download(disease);
                        ewas.setAttributeValue(ReactomeJavaConstants.disease, localDisease);                        
                        String speciesID = fileds[14];
                        GKInstance species = dba.fetchInstance(Long.parseLong(speciesID)); 
                        GKInstance localSpecies = mngr.download(species);
                        ewas.setAttributeValue(ReactomeJavaConstants.species, localSpecies); 
                    	
                    } else if (hasModifiedResidue.equals(ReactomeJavaConstants.FragmentInsertionModification) 
                    		   || hasModifiedResidue.equals("Fragment_Insertion_Modification")) {                    	
                    	GKInstance fragInsertMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentInsertionModification);
                        String coordinate = fileds[6];
                        Integer mutCoordinate = Integer.parseInt(coordinate);
                        fragInsertMutation.setAttributeValue(ReactomeJavaConstants.coordinate, mutCoordinate);
                        
                        String insRefSeqID = fileds[7];
                        GKInstance mutReferenceSequence = dba.fetchInstance(Long.parseLong(insRefSeqID));
                        GKInstance localMutReferenceSequence = mngr.download(mutReferenceSequence);
                        fragInsertMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localMutReferenceSequence);

                        String insStart = fileds[8];
                        Integer insStartCoordinate = Integer.parseInt(insStart);
                        fragInsertMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, insStartCoordinate);
                        String insEnd = fileds[9];
                        Integer insEndCoordinate = Integer.parseInt(insEnd);
                        fragInsertMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, insEndCoordinate);
                        
                        String displayNameOfFragInsertMutation = InstanceDisplayNameGenerator.generateDisplayName(fragInsertMutation);
                        fragInsertMutation.setDisplayName(displayNameOfFragInsertMutation);
                        
                        ewas.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, fragInsertMutation);
                        
                        String fragmentReplaceModification = fileds[10];
                        
                        String fragRplcRefSeqID = fileds[11];
                        
                        String alteredAA = fileds[12];
                        
                        String fragRplStart = fileds[13];
                        
                        String fragRplEnd = fileds[14];
                        
                        if (fragmentReplaceModification != null &&
                        	!fragmentReplaceModification.isEmpty() &&
                        	(fragmentReplaceModification.equals(ReactomeJavaConstants.FragmentReplacedModification)
                            || fragmentReplaceModification.equals("FragmentReplaceModification"))
                           ) {
                        	
                        	GKInstance fragRplcMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentReplacedModification);
                        	GKInstance fragRplcMutReferenceSequence = dba.fetchInstance(Long.parseLong(fragRplcRefSeqID));
                            GKInstance localFragRplcMutReferenceSequence = mngr.download(fragRplcMutReferenceSequence);
                            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localFragRplcMutReferenceSequence);
                            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAA);
                            Integer fragRplcStartCoordinate = Integer.parseInt(fragRplStart);
                            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, fragRplcStartCoordinate);
                            Integer fragRplcEndCoordinate = Integer.parseInt(fragRplEnd);
                            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, fragRplcEndCoordinate);
                            String displayNameOfFragRplcMutation = InstanceDisplayNameGenerator.generateDisplayName(fragRplcMutation);
                            fragRplcMutation.setDisplayName(displayNameOfFragRplcMutation);
                            ewas.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, fragRplcMutation);
                        }                           	                                                                        
                        
                        String referenceDatabaseID = fileds[16];
                        GKInstance referenceDatabase = dba.fetchInstance(Long.parseLong(referenceDatabaseID));
                        GKInstance localMutReferenceDatabase = mngr.download(referenceDatabase);
                        String identifies = fileds[17];

                        String[] idArray = identifies.split("\\|");
                        for(String id : idArray){
                        	GKInstance crossReference = fileAdaptor.createNewInstance(ReactomeJavaConstants.DatabaseIdentifier);
                        	crossReference.setAttributeValue(ReactomeJavaConstants.referenceDatabase, localMutReferenceDatabase);
                        	crossReference.setAttributeValue(ReactomeJavaConstants.identifier, id);
                        	String displaynameCrossRef = InstanceDisplayNameGenerator.generateDisplayName(crossReference);
                        	crossReference.setDisplayName(displaynameCrossRef);
                        	
                            ewas.addAttributeValue(ReactomeJavaConstants.crossReference, crossReference);
                        }
                        
                        String diseaseIDs = fileds[18];                        
                        String[] diseaseArray = diseaseIDs.split("\\|");
                        for(String diseaseID : diseaseArray){
                            GKInstance disease = dba.fetchInstance(Long.parseLong(diseaseID));
                            GKInstance localDisease = mngr.download(disease);
                            ewas.addAttributeValue(ReactomeJavaConstants.disease, localDisease);                       	
                        }
                        
                        String speciesID = fileds[19];
                        GKInstance species = dba.fetchInstance(Long.parseLong(speciesID)); 
                        GKInstance localSpecies = mngr.download(species);
                        ewas.setAttributeValue(ReactomeJavaConstants.species, localSpecies);   
                        
                        String pmid = fileds[20];
                        
                		Set<GKInstance> pubsFound = 
                		   (Set<GKInstance>) dba.fetchInstanceByAttribute(ReactomeJavaConstants.LiteratureReference, 
                		                                                  ReactomeJavaConstants.pubMedIdentifier,
                		                                                  "=",
                		                                                  pmid);
                        
                        if(pubsFound == null) {
                            PMIDXMLInfoFetcher2 fetcher = new PMIDXMLInfoFetcher2();                            
                            Long pmidLong = Long.parseLong(pmid);                            
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
                    
                    

                    
                    String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
                    ewas.setDisplayName(displayNameOfEwas); 
                	
                }
            	ct++;
            }
            reader.close();
            ct--;             
            fileAdaptor.save(dest);
        } catch(Exception e){
            e.printStackTrace();
        }
		return ct;
				
	}
	

}
