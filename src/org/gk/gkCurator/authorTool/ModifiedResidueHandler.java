/*
 * Created on Dec 16, 2008
 *
 */
package org.gk.gkCurator.authorTool;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gk.database.AttributeEditConfig;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.NodeAttachment;
import org.gk.render.RenderableFeature;
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;
import org.w3c.dom.Document;

public class ModifiedResidueHandler {
    
    public ModifiedResidueHandler() {
    }
    
    /**
     * Returns the feature representing the given residue.
     * 
     * As of April, 2019, the feature label is determined by the label slot in PsiMod
     * instances used to describe ModifiedResidue. All others are not considred any more.
     * 
     * The feature label is determined as follows:
     * <ul>
     * <li>If the residue has a <em>modification</em> value which maps
     *     to a {@link AttributeEditConfig#getModifications()} short name,
     *     then that is the label.
     * </ul>
     * <li>Otherwise, if the residue has a <em>psiMod</em> value and the
     *     parsed residue display name contains a
     *     {@link AttributeEditConfig#getPsiModifications()} short name,
     *     then that is the label.
     * </ul>
     * <li>Otherwise, the label is null.
     * </ul>
     * 
     * <em>Note</em>: this method catches exceptions and prints the
     * exception to <code>stdout</code>. In that case, the returned
     * feature might be incomplete and invalid. There is no definitive
     * way for the caller to detect that situation.
     * 
     * @param modifiedResidue
     * @return the feature
     * @throws Exception
     */
   public RenderableFeature convertModifiedResidue(GKInstance modifiedResidue) {
       // Work for ModifiedResidue only
       // Switch to TranslationalModification as of April, 2019
       if (!modifiedResidue.getSchemClass().isa(ReactomeJavaConstants.TranslationalModification))
           return null;
        RenderableFeature feature = new RenderableFeature();
        feature.setReactomeId(modifiedResidue.getDBID());
        try {
            // This is a just sanity check: All TranslationModifications should have this attribute
            if (modifiedResidue.getSchemClass().isValidAttribute(ReactomeJavaConstants.psiMod)) {
                GKInstance psiMod = (GKInstance) modifiedResidue.getAttributeValue(ReactomeJavaConstants.psiMod);
                if (psiMod.getSchemClass().isValidAttribute(ReactomeJavaConstants.label)) {
                    String label = (String) psiMod.getAttributeValue(ReactomeJavaConstants.label);
                    feature.setLabel(label);
                }
            }
        }
        catch(Exception e) {
            System.err.println("ModifiedResidueHandler.convertModifiedResidue(): " + e);
            e.printStackTrace();
        }
        // Assign a random position
        setRandomPosition(feature);
        return feature;
    }
   
   @SuppressWarnings("unchecked")
   private String[] getPsiModKeyWords(GKInstance psiMod) throws Exception {
       Set<String> set = new HashSet<>();
       String name = (String) psiMod.getAttributeValue(ReactomeJavaConstants.name);
       if (name != null) {
           String[] tokens = name.split("(-| )");
           Stream.of(tokens).forEach(token -> set.add(token));
       }
       List<String> synonyms = psiMod.getAttributeValuesList(ReactomeJavaConstants.synonym);
       for (String synonym : synonyms) {
           String[] tokens = synonym.split("(-| )");
           Stream.of(tokens).forEach(token -> set.add(token));
       }
       return set.toArray(new String[set.size()]);
   }
    
    private String searchPsiResidue(String[] tokens) {
        Map<String, String> psiModificationResidues = AttributeEditConfig.getConfig().getPsiModificationResidues();
        if (psiModificationResidues == null)
            return null;
        for (String token : tokens) {
            String tmp = psiModificationResidues.get(token);
            if (tmp != null)
                return tmp;
        }
        return null;
    }
    
    private String searchPsiModification(String[] tokens) {
        Map<String, String> psiModifications = AttributeEditConfig.getConfig().getPsiModifications();
        for (String token : tokens) {
            String tmp = psiModifications.get(token);
            if (tmp != null)
                return tmp;
        }
        return null;
    }
    
    private void setRandomPosition(NodeAttachment attachment) {
        double x = Math.random();
        double y = Math.random();
        // Check if it should be in x or y
        double tmp = Math.random();
        if (tmp < 0.25)
            x = 0.0;
        else if (tmp < 0.50)
            x = 1.0;
        else if (tmp < 0.57)
            y = 0.0;
        else 
            y = 1.0;
        attachment.setRelativePosition(x, y);
    }
    
    
    @Test
    public void checkPsiModMappings() throws Exception {
        AttributeEditConfig config = AttributeEditConfig.getConfig();
        InputStream metaConfig = GKApplicationUtilities.getConfig("curator.xml");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(metaConfig);
        config.loadConfig(document);
        
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        Collection<GKInstance> psiMods = dba.fetchInstancesByClass(ReactomeJavaConstants.PsiMod);
        System.out.println("Total psimods: " + psiMods.size());
        
        System.out.println("\nPSI_MOD\tDB_ID\tResidue\tModification");
        for (GKInstance psiMod : psiMods) {
            String[] tokens = getPsiModKeyWords(psiMod);
            String residue = searchPsiResidue(tokens);
            String label = searchPsiModification(tokens);
            System.out.println(psiMod.getDisplayName() + "\t" + psiMod.getDBID() + "\t" +
                               residue + "\t" + 
                               label);
        }
    }
}
