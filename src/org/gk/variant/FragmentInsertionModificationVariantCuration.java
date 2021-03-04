package org.gk.variant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class FragmentInsertionModificationVariantCuration extends VariantCuration {

    private String alteredAminoAcid;

    public FragmentInsertionModificationVariantCuration() {
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
        return createFusioneMutationEwas();
    }

    public GKInstance createFusioneMutationEwas() throws Exception {
        if (name == null || name.trim().isEmpty())
            throw new Exception("Name is null or empty string");

        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();

        Pattern p = Pattern.compile("(\\w+)\\((\\d+)\\-(\\d+)\\)\\w*\\-(\\w+)\\((\\d+)\\-(\\d+)\\)\\s*fusion");
        Matcher m = p.matcher(name);
        String geneName1 = null;
        String start1 = null;
        String end1 = null;
        String geneName2 = null;
        String start2 = null;
        String end2 = null;
        if (m.find()) {
            geneName1 = m.group(1);
            start1 = m.group(2);
            end1 = m.group(3);
            geneName2 = m.group(4);
            start2 = m.group(5);
            end2 = m.group(6);            
        } else {
            throw new Exception("The name is not a fusion mutation.");
        }

        int endInt1 = Integer.parseInt(end1); 
        int coord = endInt1 + 1;
        int startInt2 = Integer.parseInt(start2);
        int endInt2 = Integer.parseInt(end2);

        GKInstance ewas = createEwas(Integer.parseInt(start1), endInt1, geneName1);

        GKInstance frgmntInsMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentInsertionModification);
        GKInstance referenceEntity = getReferenceGeneProduct(geneName2);
        GKInstance localReferenceEntity = mngr.download(referenceEntity);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localReferenceEntity);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.coordinate, coord);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, startInt2);
        frgmntInsMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, endInt2);
        String displayNameOfFrgmntInstMutation = InstanceDisplayNameGenerator.generateDisplayName(frgmntInsMutation);
        frgmntInsMutation.setDisplayName(displayNameOfFrgmntInstMutation);

        ewas.setAttributeValue(ReactomeJavaConstants.hasModifiedResidue, frgmntInsMutation);

        if (alteredAminoAcid != null && !alteredAminoAcid.isEmpty()) {            	
            GKInstance fragRplcMutation = fileAdaptor.createNewInstance(ReactomeJavaConstants.FragmentReplacedModification);
            GKInstance fragRplcMutReferenceSequence = getReferenceGeneProduct(geneName1);
            GKInstance localFragRplcMutReferenceSequence = mngr.download(fragRplcMutReferenceSequence);
            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.referenceSequence, localFragRplcMutReferenceSequence);
            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment, alteredAminoAcid);
            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence, endInt1);
            fragRplcMutation.setAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence, endInt1);
            String displayNameOfFragRplcMutation = InstanceDisplayNameGenerator.generateDisplayName(fragRplcMutation);
            fragRplcMutation.setDisplayName(displayNameOfFragRplcMutation);
            ewas.addAttributeValue(ReactomeJavaConstants.hasModifiedResidue, fragRplcMutation);
        }     

        String displayNameOfEwas = InstanceDisplayNameGenerator.generateDisplayName(ewas);
        ewas.setDisplayName(displayNameOfEwas); 

        return ewas;
    }

}
