package org.gk.variant;

import java.io.BufferedReader;
import java.io.FileReader;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class VariantBatchProcessor {
	private String src;
	private String dest;
	private MySQLAdaptor dba;
	PersistenceManager mngr;
	
	public VariantBatchProcessor(String src, String dest, MySQLAdaptor dba) {
		this.src = src;
		this.dest = dest;
		this.dba = dba;
		mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
	}
	
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
                    ewas.setAttributeValueNoCheck(ReactomeJavaConstants.disease, localDisease);                        
                    String speciesID = fileds[13];
                    GKInstance species = dba.fetchInstance(Long.parseLong(speciesID)); 
                    GKInstance localSpecies = mngr.download(species);
                    ewas.setAttributeValue(ReactomeJavaConstants.species, localSpecies); 
                    
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
