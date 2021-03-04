package org.gk.variant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class FragmentReplacedModificationVariantCuration extends VariantCuration {

    private String alteredAminoAcid;

    public FragmentReplacedModificationVariantCuration() {
        super();
    }	

    public String getAlteredAminoAcid() {
        return alteredAminoAcid;
    }

    public void setAlteredAminoAcid(String alteredAminoAcid) {
        this.alteredAminoAcid = alteredAminoAcid;
    }

    @Override
    public GKInstance createModifiedEWAS() throws Exception {
        return createFrameShiftMutationEwas();
    }

    public GKInstance createFrameShiftMutationEwas() throws Exception {       
        if (name == null || name.trim().isEmpty())
            throw new IllegalStateException("Name is null or empty string");

        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();

        Pattern p = Pattern.compile("(\\w+)\\s+[A-Z](\\d+)[A-Z]fs\\*(\\d+)");
        Matcher m = p.matcher(name);
        String mut = null;
        String shft = null;
        String geneName = null;
        if (m.find()) {
            geneName = m.group(1);
            mut = m.group(2);
            shft = m.group(3);
        } else {
            throw new IllegalStateException("The name is not a frame shift mutation.");
        }

        int mutPnt = Integer.parseInt(mut);        
        int shftPnt = Integer.parseInt(shft);
        int start = 1;
        int end = mutPnt + shftPnt - 2;

        GKInstance ewas = createEwas(start, end, geneName);

        GKInstance frgmntRplcMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentReplacedModification);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, mutPnt);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, end);
        String displayNameOfFrgRplcMutation = InstanceDisplayNameGenerator.generateDisplayName(frgmntRplcMutation);
        frgmntRplcMutation.setDisplayName(displayNameOfFrgRplcMutation);
        frgmntRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);    	

        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, frgmntRplcMutation);

        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 

        return ewas;
    }

}
