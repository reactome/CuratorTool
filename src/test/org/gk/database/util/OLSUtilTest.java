package test.org.gk.database.util;

import java.util.Map;

import org.gk.database.util.OLSUtil;
import org.junit.Test;

import junit.framework.TestCase;


public class OLSUtilTest extends TestCase{

	@Test
	public void testRefMoleculeGetTermById()
	{
		System.out.println("begin test");
		String term = OLSUtil.getTermById("CHEBI:17794", "chebi");
		System.out.println("term from CHEBI:17794: "+term);
		assertTrue(term.equals("3-phospho-D-glyceric acid"));
		
		term = OLSUtil.getTermById("MOD:01625", "mod");
		System.out.println("term from MOD:01625: "+term);
		assertTrue(term.equals("1-thioglycine"));

		term = OLSUtil.getTermById("DOID:14764", "doid");
		System.out.println("term from DOID:14764: "+term);
		assertTrue(term.equals("Larsen syndrome"));
	}
	
	
	@Test
	public void testRefMoleculeGetTermMetadata()
	{
		System.out.println("begin test");
		Map<String,String> md = OLSUtil.getTermMetadata("CHEBI:17794", "chebi");
		System.out.println("term metadata from CHEBI:17794:");
		
		for (String k: md.keySet())
		{
			System.out.println(k + " : "+md.get(k));
		}
		//check the formula for the molecule
		assertTrue(md.get("FORMULA_synonym").equals("C3H7O7P"));
		
		md = OLSUtil.getTermMetadata("MOD:01625", "mod");
		System.out.println("term metadata from MOD:01625:");
		
		for (String k: md.keySet())
		{
			System.out.println(k + " : "+md.get(k));
		}
		//just check a random synonym, maybe add a full list later...
		assertTrue(md.containsValue("Carboxy->Thiocarboxy"));
		
		md = OLSUtil.getTermMetadata("DOID:14764", "doid");
		System.out.println("term metadata from DOID:14764:");
		
		for (String k: md.keySet())
		{
			System.out.println(k + " : "+md.get(k));
		}
		//DOID:14764 only has one synonym, but will have 3 slightly different keys referring to it.
		assertTrue(md.containsValue("dominant larsen syndrome"));
	}
	
	@Test
	public void testRefMoleculeGetXRefs()
	{
		System.out.println("begin test");
		Map<String,String> xrefs = OLSUtil.getTermXrefs("CHEBI:17794", "chebi");
		System.out.println("term xrefs from ID CHEBI:17794:");
		
		for (String k: xrefs.keySet())
		{
			System.out.println(k + " : "+xrefs.get(k));
		}
		assertTrue(xrefs.containsKey("KEGG COMPOUND:C00197"));

		// no need to test xrefs for non reference molecules since the application does not make use of them.
		


	}
}
