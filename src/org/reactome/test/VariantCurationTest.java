package org.reactome.test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.variant.FragmentReplacedModificationVariantCuration;
import org.gk.variant.FragmentInsertionModificationVariantCuration;
import org.gk.variant.NonsenseMutationVariantCuration;
import org.gk.variant.ReplacedResidueVariantCuration;
import org.gk.variant.VariantBatchProcessor;
import org.junit.Test;

public class VariantCurationTest {	
	
	@Test
	public void testCreateReplacedResidueEwas() throws Exception {
		PersistenceAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/single_replaced_residue_ewas_" + dtf.format(now) + ".rtpj";        
        
        ReplacedResidueVariantCuration variantCuration = new ReplacedResidueVariantCuration(); 
        String name = "PPP2R1A";
        variantCuration.setGeneName(name);
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
		mngr.setActivePersistenceAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createReplacedResidueEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);	
	}
	
	@Test
	public void testCreateNonsenseMutationEwas() throws Exception {
		PersistenceAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/single_nonse_mut_ewas_" + dtf.format(now) + ".rtpj";        
        
        NonsenseMutationVariantCuration variantCuration = new NonsenseMutationVariantCuration(); 
        String name = "BRCA2 Q373*";
        variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        //variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSV99061731|COSM7335723");
        //variantCuration.setDiseaseIds("1500689");        
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActivePersistenceAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createNonsenseMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);	
	}
	
	@Test
	public void testCreateFrameShiftMutationEwas() throws Exception {
		PersistenceAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/single_frame_shift_ewas_" + dtf.format(now) + ".rtpj";        
        
        FragmentReplacedModificationVariantCuration variantCuration = new FragmentReplacedModificationVariantCuration(); 
        String name = "BRCA2 E2981Rfs*37";
        variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        //variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSV66454404");
        //variantCuration.setDiseaseIds("1500689");
        variantCuration.setAlteredAminoAcid("RKRFSYTEYLASIIRFIFSVNRRKEIQNLSSCNFKI");      
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActivePersistenceAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createFrameShiftMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);		
	}
	
	@Test
	public void testCreateFusioneMutationEwas() throws Exception {
		PersistenceAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/single_fusion_mut_ewas_" + dtf.format(now) + ".rtpj";        
        
        FragmentInsertionModificationVariantCuration variantCuration = new FragmentInsertionModificationVariantCuration(); 
		String name = "KLC1(1-420)-ALK(1058-1620) fusion";
		variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        //variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setCosmicIds("COSF1276");
        //variantCuration.setDiseaseIds("1500689");
        variantCuration.setPmids("21656749|24475247");
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActivePersistenceAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createFusioneMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);		
		
	}
	
	@Test
	public void testCreateFragmentInsertionEwas() throws Exception {
		PersistenceAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "authortool",
                "T001test",
                3306);

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
        String destination = "/Users/shaox/Desktop/single_fusion_fragmnt_insertion_ewas_" + dtf.format(now) + ".rtpj";        
        
        FragmentInsertionModificationVariantCuration variantCuration = new FragmentInsertionModificationVariantCuration(); 
		String name = "HIP1(1-719)InsL-ALK(1059-1620) fusion";
		variantCuration.setName(name);
        variantCuration.setCompartmentId("70101");
        //variantCuration.setReferenceDatabaseId("1655447");
        variantCuration.setAlteredAminoAcid("DL");
        variantCuration.setCosmicIds("COSF1615");
        variantCuration.setDiseaseIds("1500689|8853117");
        variantCuration.setPmids("24518094");
        
        PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActivePersistenceAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
        GKInstance ewas = variantCuration.createFusioneMutationEwas();
        
        fileAdaptor.save(destination);
        
        System.out.println(ewas);		
		
	}
	
	@Test
    public void testBatchProcessingVariousVariants() throws Exception {
    	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
    	LocalDateTime now = LocalDateTime.now();  
    	String dir = "/Users/wug/Documents/wgm/work/reactome/Marija/";
    	// March 3 & 4, 2021 for TP53
//    	String src = dir + "TP53_Nonsense_Mutations.tsv";
//    	String dest = dir + "TP53_Nonsense_Mutations_" + dtf.format(now) + ".rtpj";
    	
    	String src = dir + "TP53_frameshift_mutations.tsv";
    	String dest = dir + "TP53_frameshift_mutations_" + dtf.format(now) + ".rtpj";
    	
		PersistenceAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                                            "gk_central",
                                            "authortool",
                                            "T001test",
                                            3306);	
		
		PersistenceManager mngr = PersistenceManager.getManager();
		mngr.setActivePersistenceAdaptor(dba);
		XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
		mngr.setActiveFileAdaptor(fileAdaptor);
		
		VariantBatchProcessor processor = new VariantBatchProcessor(src, dest);
		int numberOfRecordsProcessed = processor.createdEwases();
		// should be 6
        System.out.println("The total number of records processed is " + numberOfRecordsProcessed);	
	}

}
