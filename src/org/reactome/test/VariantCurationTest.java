package org.reactome.test;

import java.util.ArrayList;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.variant.VariantCuration;
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
	

}
