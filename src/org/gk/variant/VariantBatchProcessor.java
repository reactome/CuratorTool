package org.gk.variant;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;

public class VariantBatchProcessor {
    private String src;
    private String dest;
    private ProgressPane progressPane = null;

    public VariantBatchProcessor(String src, String dest) {
        this.src = src;
        this.dest = dest;
    }
    
    public VariantBatchProcessor() {
    }
    
    public void runBatch(JFrame parent) {
        File file = selectFile(parent);
        if (file == null)
            return;
        setSrc(file.getAbsolutePath());
        // We also need an active PersistenceManager
        PersistenceAdaptor dba = PersistenceManager.getManager().getActivePersistenceAdaptor(parent);
        if (dba == null)
            return; // Cannot do anything
        progressPane = new ProgressPane();
        progressPane.setTitle("Import Variants");
        progressPane.setText("Creating EWAS...");
        parent.setGlassPane(progressPane);
        parent.getGlassPane().setVisible(true);
        // Need to use a thread
        Thread t = new Thread() {
            public void run() {
                try {
                    int counter = createdEwases();
                    JOptionPane.showMessageDialog(parent,
                                                  "Imported EWAS instances with variants: " + counter,
                                                  "Success Import",
                                                  JOptionPane.INFORMATION_MESSAGE);
                    parent.getGlassPane().setVisible(false);
                }
                catch(Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(parent,
                                                  "Error in importing variants: " + e.getMessage(),
                                                  "Error in Import",
                                                  JOptionPane.ERROR_MESSAGE);
                    parent.getGlassPane().setVisible(false);
                }
            }
        };
        t.start();
    }

    protected File selectFile(JFrame parent) {
        FileFilter fileFilter = new FileFilter() {

            @Override
            public String getDescription() {
                return "Tab Delimited Files (*.txt, *.tsv, *.tab)";
            }

            @Override
            public boolean accept(File file) {
                if (file.isDirectory())
                    return true;
                int index = file.getName().lastIndexOf(".");
                String ext = file.getName().substring(index + 1);
                if (ext.equals("txt") || 
                        ext.equals(".tsv") ||
                        ext.equals(".tab"))
                    return true;
                return false;
            }
        };
        JFileChooser fileChooser = GKApplicationUtilities.createFileChooser(GKApplicationUtilities.getApplicationProperties());
        fileChooser.addChoosableFileFilter(fileFilter);
        int reply = fileChooser.showOpenDialog(parent);
        if (reply != JFileChooser.APPROVE_OPTION)
            return null;
        File file = fileChooser.getSelectedFile();
        if (file == null)
            return null;
        GKApplicationUtilities.getApplicationProperties().setProperty("currentDir",
                                                                      file.getParent());
        return file;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public String getDest() {
        return dest;
    }

    public void setDest(String dest) {
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
        int total = -1;
        if (progressPane != null) {
            total = getTotalLines(src);
            progressPane.setMaximum(total);
        }
        int ct = 0;
        if (src == null)
            throw new IllegalStateException("Source file name is missing.");

        BufferedReader reader = new BufferedReader(new FileReader(src));

        String[] fields;

        PersistenceManager mngr = PersistenceManager.getManager(); 
        XMLFileAdaptor fileAdaptor = mngr.getActiveFileAdaptor();

        String line = reader.readLine();
        Map<String, Integer> headerToIndex = parseHeader(line);
        while((line = reader.readLine()) != null) {
            ct ++;
            if (progressPane != null) {
                progressPane.setValue(ct);
                progressPane.setText("Create EWAS (" + ct + "/" + total + ")...");
            }
//            System.out.println(line);
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
        if (dest != null)
            fileAdaptor.save(dest);
        return ct;
    }
    
    private int getTotalLines(String file) throws IOException {
        try (Stream<String> lines = Files.lines(Paths.get(file))) {
            int total = lines.skip(1)
                        .collect(Collectors.counting()).intValue();
            return total;
        }
    }
}
