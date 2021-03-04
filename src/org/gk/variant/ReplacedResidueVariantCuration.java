package org.gk.variant;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class ReplacedResidueVariantCuration extends VariantCuration {
	private String geneName;
	
	private String coordinate;
	
	private String wtResidue;
	
	private String replacedResidue;
	
	private String startCoordinate;
	
	private String endCoordinate;
	
	public ReplacedResidueVariantCuration() {
		super();
		setAminoAcidDict();
	}
	
	public String getGeneName() {
		return geneName;
	}

	public void setGeneName(String geneName) {
		this.geneName = geneName;
	}
	
	public String getCoordinate() {
		return coordinate;
	}

	public void setCoordinate(String coordinate) {
		this.coordinate = coordinate;
	}

	public String getWtResidue() {
		return wtResidue;
	}

	public void setWtResidue(String wtResidue) {
		this.wtResidue = wtResidue;
	}

	public String getReplacedResidue() {
		return replacedResidue;
	}

	public void setReplacedResidue(String replacedResidue) {
		this.replacedResidue = replacedResidue;
	}

	public String getStartCoordinate() {
		return startCoordinate;
	}

	public void setStartCoordinate(String startCoordinate) {
		this.startCoordinate = startCoordinate;
	}

	public String getEndCoordinate() {
		return endCoordinate;
	}

	public void setEndCoordinate(String endCoordinate) {
		this.endCoordinate = endCoordinate;
	}
	
	@Override
	public GKInstance createModifiedEWAS() throws Exception {
	    return createReplacedResidueEwas();
	}
	
	public GKInstance createReplacedResidueEwas() throws Exception {
		if (geneName == null || geneName.trim().isEmpty())
			throw new Exception("Gene name entered is empty string");
        		
		GKInstance ewas = createEwas(Integer.parseInt(startCoordinate), Integer.parseInt(endCoordinate), geneName);
        
        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();
        
        GKInstance replacedResidueMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.ReplacedResidue);
        replacedResidueMutation.setAttributeValue(ReactomeJavaConstants.coordinate, Integer.parseInt(coordinate));
        GKInstance psiMod1 = getPsiMod(wtResidue, "removal");
        GKInstance localPsiMod1 = mngr.download(psiMod1);
        replacedResidueMutation.addAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod1);
        GKInstance psiMod2 = getPsiMod(replacedResidue, "residue");
        GKInstance localPsiMod2 = mngr.download(psiMod2);
        replacedResidueMutation.addAttributeValue(ReactomeJavaConstants.psiMod, localPsiMod2);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        replacedResidueMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        String displayNameOfReplacedResidueMutation = InstanceDisplayNameGenerator.generateDisplayName(replacedResidueMutation);
        replacedResidueMutation.setDisplayName(displayNameOfReplacedResidueMutation);        
        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, replacedResidueMutation);
        
		String mutName = geneName + " " + wtResidue + coordinate + replacedResidue;
        ewas.setAttributeValue(ReactomeJavaConstants.name, mutName);
        
        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 
        		
		return ewas;
	}
	

}
