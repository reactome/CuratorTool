package org.gk.variant;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

public class VariantBatchProcessor {
    private String src;
    private String dest;

    public VariantBatchProcessor(String src, String dest) {
        this.src = src;
        this.dest = dest;
    }

    private Map<String, Integer> parseHeader(String line) {
        Map<String, Integer> nameToIndex = new HashMap<>();
        String[] tokens = line.split("\t");
        for (int i = 0; i < tokens.length; i++) {
            nameToIndex.put(tokens[i], i);
        }
        return nameToIndex;
    }

    @SuppressWarnings({"resource"})
    public int createdEwases() throws Exception {
        int ct = 0;
        if (src == null || dest == null)
            throw new IllegalStateException("Source file or destination result file name missing.");

        try{
            BufferedReader reader = new BufferedReader(new FileReader(src));

            String[] fields;

            PersistenceManager mngr = PersistenceManager.getManager(); 
            XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();

            String line = reader.readLine();
            Map<String, Integer> headerToIndex = parseHeader(line);
            while((line = reader.readLine()) != null) {
                ct ++;
                System.out.println(line);
                fields = line.split("\t");
                String name = fields[headerToIndex.get("name")];
                String mutationType = fields[headerToIndex.get("hasModifiedResidue")]; // key field to decide on which subclass will be used
                String compartmentID = fields[headerToIndex.get("compartment.DB_ID")];
                // Required for FragmentInsertionModification and FragmentReplacedModification
                String alteredAminoAcidFragment = null;
                if (headerToIndex.containsKey("FragmentReplacedModification.alteredAminoAcidFragment"))
                    alteredAminoAcidFragment = fields[headerToIndex.get("FragmentReplacedModification.alteredAminoAcidFragment")];  
                String referenceDatabaseId = fields[headerToIndex.get("DatabaseIdentifer.referenceDatabase.DB_ID")];
                String refIdentifiers = fields[headerToIndex.get("DatabaseIdentifier.identifier")];
                String dieaseIds = fields[headerToIndex.get("disease.DB_ID")];  
                // Optional
                String pmids = null;
                if (headerToIndex.containsKey("pmid"))
                    pmids = fields[headerToIndex.get("pmid")];         
                // Required
                String speciesIds = fields[headerToIndex.get("species.DB_ID")]; 
                // Use the template to save the code
                VariantCuration variantCuration = null;
                if (mutationType.equalsIgnoreCase(ReactomeJavaConstants.NonsenseMutation)) 
                    variantCuration = new NonsenseMutationVariantCuration(); 
                else if (mutationType.equalsIgnoreCase(ReactomeJavaConstants.FragmentReplacedModification)) {
                    variantCuration = new FragmentReplacedModificationVariantCuration(); 
                    if (alteredAminoAcidFragment != null && !alteredAminoAcidFragment.isEmpty())
                        ((FragmentReplacedModificationVariantCuration)variantCuration).setAlteredAminoAcid(alteredAminoAcidFragment);
                    else
                        throw new IllegalStateException("line " + ct + " is not a FragmentReplacedModification mutation.");
                } 
                else if (mutationType.equalsIgnoreCase(ReactomeJavaConstants.FragmentInsertionModification)) {
                    variantCuration = new FragmentInsertionModificationVariantCuration();
                    if (alteredAminoAcidFragment != null && !alteredAminoAcidFragment.isEmpty())
                        ((FragmentInsertionModificationVariantCuration)variantCuration).setAlteredAminoAcid(alteredAminoAcidFragment); 
                }   
                if (variantCuration != null) {
                    variantCuration.setName(name);
                    variantCuration.setCompartmentId(compartmentID);
                    if (referenceDatabaseId != null && !referenceDatabaseId.isEmpty())
                        variantCuration.setReferenceDatabaseId(referenceDatabaseId);

                    variantCuration.setCosmicIds(refIdentifiers);
                    if (dieaseIds != null && !dieaseIds.isEmpty())
                        variantCuration.setDiseaseIds(dieaseIds); 

                    if (pmids != null && !pmids.isEmpty())
                        variantCuration.setPmids(pmids); 

                    if (speciesIds != null && !speciesIds.isEmpty())
                        variantCuration.setSpeciesId(Long.parseLong(speciesIds));
                    variantCuration.createModifiedEWAS();
                }
            } // end of while loop
            reader.close();
            fileAdaptor.save(dest);
        } 
        catch(Exception e){
            e.printStackTrace();
        }
        return ct;
    }
}
