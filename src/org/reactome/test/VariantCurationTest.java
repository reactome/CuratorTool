package org.reactome.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.variant.VariantCuration;
import org.gk.variant.VariantBatchProcessor;
import org.junit.Test;

public class VariantCurationTest {	
	
	@Test
    public void testVariantCurationData() throws Exception {
		
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                                            "gk_central",
                                            "authortool",
                                            "T001test",
                                            3306);
		
		PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);	
		
		VariantCuration variantCuration = new VariantCuration();				
		
		String geneName = "PPP2R1A";
		
		GKInstance referenceGeneProduct = variantCuration.getReferenceGeneProduct(geneName);
		
		System.out.println("Reference Gene Product for " + geneName + " : " + referenceGeneProduct);
				
		List<GKInstance> eawsInstances = variantCuration.getWtEwases(geneName);
		
		System.out.println("EWAS instances for geneName PPP2R1A :");
		
		eawsInstances.forEach(System.out::println);
		
		// should get the following:
		// [EntityWithAccessionedSequence:165968] PPP2R1A [nucleoplasm]
		// [EntityWithAccessionedSequence:165982] PPP2R1A [cytosol]
        // [EntityWithAccessionedSequence:8958544] PPP2R1A [extracellular region]
		// [EntityWithAccessionedSequence:2466048] PPP2R1A [chromosome, centromeric region]
		
		System.out.println("*************************************************");
		
		List<GKInstance> containingEntities = new ArrayList<>();
		
		for(GKInstance ewas : eawsInstances) {
			containingEntities = variantCuration.getComplexesAndEntitySets(ewas);
			System.out.println("All the containing PhysicalEntity instances \n for :" + ewas.toString());
			if (containingEntities != null && !containingEntities.isEmpty()) {
				containingEntities.forEach(System.out::println);
			} else {				
				System.out.println("None found.");
			}
			System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		}
			
		GKInstance pp2aCytol = dba.fetchInstance(206817L);
		
		List<GKInstance> reactions = variantCuration.getReactions(pp2aCytol);
		
		System.out.println("All the reactions with relate to " + pp2aCytol.toString());
		
		reactions.forEach(System.out::println);		
	}       
	
	@Test
    public void testBatchProcessingNonsenseVariants() throws Exception {
    	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
    	String src = "/Users/shaox/eclipse-workspace/CuratorTool/resources/Disease_BRCA2_EWASs.tsv";
        String dest = "/Users/shaox/Desktop/BRAC2NonsenseMutants_" + dtf.format(now) + ".rtpj";
		
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                                            "gk_central",
                                            "authortool",
                                            "T001test",
                                            3306);	
		
		VariantBatchProcessor processor = new VariantBatchProcessor(src, dest, dba);
		int numberOfRecordsProcessed = processor.processEwases();
		// should be 150
        System.out.println("The total number of records processed is " + numberOfRecordsProcessed);	
	}
	
	@Test
    public void testBatchProcessingFragmntRplcedVariants() throws Exception {
    	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
    	String src = "/Users/shaox/eclipse-workspace/CuratorTool/resources/BRCA2_FrameShiftMutants_FragmentReplacement_sheet1.tsv";
        String dest = "/Users/shaox/Desktop/BRCA2_FrameShift_EWAS_" + dtf.format(now) + ".rtpj";
		
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                                            "gk_central",
                                            "authortool",
                                            "T001test",
                                            3306);	
		
		VariantBatchProcessor processor = new VariantBatchProcessor(src, dest, dba);
		int numberOfRecordsProcessed = processor.processEwases();
		// should be 169
        System.out.println("The total number of records processed is " + numberOfRecordsProcessed);	
	}
	
	@Test
    public void testBatchProcessingFusionVariants() throws Exception {
    	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
    	String src = "/Users/shaox/eclipse-workspace/CuratorTool/resources/test_fusion_mutations.tsv";
        String dest = "/Users/shaox/Desktop/testFusionMutants_" + dtf.format(now) + ".rtpj";
		
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                                            "gk_central",
                                            "authortool",
                                            "T001test",
                                            3306);	
		
		VariantBatchProcessor processor = new VariantBatchProcessor(src, dest, dba);
		int numberOfRecordsProcessed = processor.processEwases();
		// should be 5
        System.out.println("The total number of records processed is " + numberOfRecordsProcessed);	
	}
	
	@Test
	public void testCreateReplacedResidueEwas() throws Exception {
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/replaced_residue_ewas_" + dtf.format(now) + ".rtpj";        
        
        VariantCuration variantCuration = new VariantCuration(); 
        String name = "PPP2R1A";
        variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        variantCuration.setStartCoordinate("1");
        variantCuration.setEndCoordinate("256");
        variantCuration.setCoordinate("179");
        variantCuration.setWtResidue("P");
        variantCuration.setReplacedResidue("R");
        variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSV99061731|COSM7335723");
        variantCuration.setDiseaseIds("1500689");        
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createReplacedResidueEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);	
	}
	
	@Test
	public void testCreateSingleNonsenseMutationEwas() throws Exception {
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/nonse_mut_ewas_" + dtf.format(now) + ".rtpj";        
        
        VariantCuration variantCuration = new VariantCuration(); 
        String name = "BRCA2 Q373*";
        variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSV99061731|COSM7335723");
        variantCuration.setDiseaseIds("1500689");        
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createNonsenseMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);	
	}
	
	@Test
	public void testCreateSingleFrameShiftMutationEwas() throws Exception {
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/frame_shift_ewas_" + dtf.format(now) + ".rtpj";        
        
        VariantCuration variantCuration = new VariantCuration(); 
        String name = "BRCA2 E2981Rfs*37";
        variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSV66454404");
        variantCuration.setDiseaseIds("1500689");
        variantCuration.setAlteredAminoAcid("RKRFSYTEYLASIIRFIFSVNRRKEIQNLSSCNFKI");      
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createFrameShiftMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);		
	}
	
	@Test
	public void testCreateSingleFusioneMutationEwas() throws Exception {
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/fusion_mut_ewas_" + dtf.format(now) + ".rtpj";        
        
        VariantCuration variantCuration = new VariantCuration(); 
		String name = "KLC1(1-420)-ALK(1058-1620) fusion";
		variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSF1276");
        variantCuration.setDiseaseIds("1500689");
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActiveMySQLAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createFusioneMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);		
		
	}

}
