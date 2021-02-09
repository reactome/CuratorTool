package org.reactome.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
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
		
		VariantCuration variantCuration = new VariantCuration(dba, "PPP2R1A");
		
		List<GKInstance> referenceGeneProducts = variantCuration.getReferenceGeneProducts();
		
		System.out.println("Reference Gene Products for geneName PPP2R1A :");
		
		referenceGeneProducts.forEach(System.out::println);
		
		List<GKInstance> eawsInstances = variantCuration.getWtEwases();
		
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
	public void testCreateNonsenseMutationEwas() throws Exception {
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		VariantBatchProcessor processor = new VariantBatchProcessor("BRCA2 G405*", "50952", "70101", "611092", "COSV66465255", "1500689", dba);
        
        GKInstance ewas = processor.createNonsenseMutationEwas();
        
        System.out.println(ewas);		
	}
	
	@Test
	public void testCreateFrameShiftMutationEwas() throws Exception {
		
		MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		VariantBatchProcessor processor = new VariantBatchProcessor("BRCA2 E2981Rfs*37", "50952", "70101", "1655447", "COSV66454404", "1500689", "RKRFSYTEYLASIIRFIFSVNRRKEIQNLSSCNFKI", dba);
        
        GKInstance ewas = processor.createFrameShiftMutationEwas();
        
        System.out.println(ewas);	
	}

}
