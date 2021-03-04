package org.gk.variant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class NonsenseMutationVariantCuration extends VariantCuration {
		
	public NonsenseMutationVariantCuration() {
		super();
		setAminoAcidDict();
	}
	
	@Override
	public GKInstance createModifiedEWAS() throws Exception {
	    return createNonsenseMutationEwas();
	}
	
	public GKInstance createNonsenseMutationEwas() throws Exception {
		if (name == null || name.trim().isEmpty())
			throw new Exception("Name is null or empty string");
		
		Pattern p = Pattern.compile("(\\w+)\\s+([A-Z])(\\d+)\\*");
        Matcher m = p.matcher(name);
        String mut = null;
        String geneName = null;
        String aaCode = null;
        if (m.find()) {
        	geneName = m.group(1);
        	aaCode = m.group(2);
            mut = m.group(3);
        } else {
        	throw new Exception("The name is not a nonsense mutation.");
        }
        
        int mutPnt = Integer.parseInt(mut);
        int start = 1;  // assuming the start coordinate is always 1
        int end = mutPnt - 1;
        
		GKInstance ewas = createEwas(start, end, geneName);
        
        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
        
        GKInstance nonsenseMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.NonsenseMutation);
        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.coordinate, mutPnt);
        //GKInstance psiMod = dba.fetchInstance(Long.parseLong(psiModId));
        GKInstance psiMod = getPsiMod(aaCode, "removal");
        GKInstance localPsiMod = mngr.download(psiMod);
        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        nonsenseMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        String displayNameOfNonsenseMutation = InstanceDisplayNameGenerator.generateDisplayName(nonsenseMutation);
        nonsenseMutation.setDisplayName(displayNameOfNonsenseMutation);        
        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, nonsenseMutation);
        
        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 
        		
		return ewas;
	}

}
