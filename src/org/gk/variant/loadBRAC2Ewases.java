package org.gk.variant;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.SQLException;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class loadBRAC2Ewases {

	public static void main(String[] args) throws SQLException {		
        try{
            String src = "/Users/shaox/eclipse-workspace/CuratorTool/resources/Disease_BRCA2_EWASs.tsv";
            String dest = "/Users/shaox/Desktop/BRCA2s";

            BufferedReader reader = new BufferedReader(new FileReader(src));
            
            String line = null;
            String[] fileds;
    		
    		PersistenceManager mngr = PersistenceManager.getManager();
    		
    		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                    "gk_central",
                    "authortool",
                    "T001test",
                    3306);

    		mngr.setActiveMySQLAdaptor(dba);
    		
            //XMLFileAdaptor fileAdaptor = mngr.getFileAdaptor(dest);
    		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();

    		
    		int ct = 1;
            while(ct < 152) {                     // total number of lines : 151
                line = reader.readLine();
                if(line != null && ct > 1) {        
                	fileds = line.split("\t");
                	if(ct == 15) {    // do the first one with 2 cross references(line#15) 
                		String name = fileds[0];
                    	System.out.println(name);
                		GKInstance ewas = fileAdaptor.createNewInstance(ReactomeJavaConstants.EntityWithAccessionedSequence);
                		ewas.setDisplayName(name);
                		ewas.setAttributeValueNoCheck(ReactomeJavaConstants.name, name);
                        String referenceEntityID = fileds[1];
                        System.out.println(referenceEntityID);
                        GKInstance referenceEntity = dba.fetchInstance(Long.parseLong(referenceEntityID));
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.referenceEntity, referenceEntity);
                        String start = fileds[2];
                        System.out.println(start);
                        Integer startCoordinate = Integer.parseInt(start);
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.startCoordinate, startCoordinate);
                        String end = fileds[3];
                        System.out.println(end);
                        Integer endCoordinate = Integer.parseInt(end);
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.endCoordinate, endCoordinate);
                        String compartmentID = fileds[4];
                        System.out.println(compartmentID);
                        GKInstance compartment = dba.fetchInstance(Long.parseLong(compartmentID));
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.compartment, compartment);
                        String hasModifiedResidue = fileds[5];
                        System.out.println(hasModifiedResidue);
                        GKInstance nonsenseMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.NonsenseMutation);
                        String coordinate = fileds[6];
                        System.out.println(coordinate);
                        Integer mutCoordinate = Integer.parseInt(coordinate);
                        nonsenseMutation.setAttributeValueNoCheck(ReactomeJavaConstants.coordinate, mutCoordinate);
                        String psiModID = fileds[7];
                        System.out.println(psiModID);
                        GKInstance psiMod = dba.fetchInstance(Long.parseLong(psiModID));
                        nonsenseMutation.setAttributeValueNoCheck(ReactomeJavaConstants.psiMod, psiMod);
                        String mutRefSeqID = fileds[8];
                        System.out.println(mutRefSeqID);
                        GKInstance mutReferenceSequence = dba.fetchInstance(Long.parseLong(mutRefSeqID));
                        nonsenseMutation.setAttributeValueNoCheck(ReactomeJavaConstants.referenceSequence, mutReferenceSequence);
                        fileAdaptor.addNewInstance(nonsenseMutation);
                        
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.hasModifiedResidue, nonsenseMutation);
                        String crossReferenceStr = fileds[9];
                        System.out.println(crossReferenceStr);
                        String referenceDatabaseID = fileds[10];
                        System.out.println(referenceDatabaseID);
                        GKInstance referenceDatabase = dba.fetchInstance(Long.parseLong(referenceDatabaseID));
                        String cosmicIDs = fileds[11];
                        System.out.println(cosmicIDs);

                        String[] cosmicIDsArray = cosmicIDs.split("\\|");
                        int numCosmicIDs = cosmicIDsArray.length;
                        System.out.println("numCosmicIDs = " + numCosmicIDs);
                        for(String cosmicID : cosmicIDsArray){
                        	System.out.println(cosmicID);
                        	String crossRefName = referenceDatabase.getDisplayName() + ":" + cosmicID;
                        	GKInstance crossReference = fileAdaptor.createNewInstance(ReactomeJavaConstants.DatabaseIdentifier);
                        	crossReference.setDisplayName(crossRefName);
                        	crossReference.setAttributeValueNoCheck(ReactomeJavaConstants.referenceDatabase, referenceDatabase);
                        	crossReference.setAttributeValueNoCheck(ReactomeJavaConstants.identifier, cosmicID);
                        	fileAdaptor.addNewInstance(crossReference);
                        	
                        	// how to set (add) more than one instances of crossReference to EWAS ?
                            ewas.setAttributeValueNoCheck(ReactomeJavaConstants.crossReference, crossReference);
                        }
                        
                        String diseaseID = fileds[12];
                        System.out.println(diseaseID);
                        GKInstance disease = dba.fetchInstance(Long.parseLong(diseaseID));
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.disease, disease);                        
                        String speciesID = fileds[13];
                        System.out.println(speciesID);
                        GKInstance species = dba.fetchInstance(Long.parseLong(speciesID));    
                        ewas.setAttributeValueNoCheck(ReactomeJavaConstants.species, species); 
                        
                        fileAdaptor.addNewInstance(ewas);
                	}
                	
                }
            	ct++;
            }
            reader.close();
            ct--;
            System.out.println("The total number of lines processed is " + ct);
            
            fileAdaptor.save(dest);

        } catch(Exception e){
            e.printStackTrace();
        }
        
    }
}    
