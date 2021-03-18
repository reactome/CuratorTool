package org.gk.database.util;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.util.GKApplicationUtilities;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

/**
 * This class is used to create drug instances automatically from ReferenceTherapeutic instances.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class DrugAutoCreator {
    private String defaultDrug;
    private Map<String, String> typeToDrug;
    
    public DrugAutoCreator() {
        init();
    }
    
    private void init() {
        try {
            InputStream is = GKApplicationUtilities.getConfig("curator.xml");
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(is);
            Element elm = (Element) XPath.selectSingleNode(document.getRootElement(), 
                                                           "ReferenceTherapeutic2Drug");
//            <ReferenceTherapeutic2Drug default="ChemicalDrug">
//              <map type="Synthetic organic" drug="ChemicalDrug" />
//              <map type="Peptide" drug="ProteinDrug" />
//              <map type="Antibody" drug="ProteinDrug" />
//              <map type="Inorganic" drug="ChemicalDrug" />
//              <map type="Natural product" drug="ChemicalDrug" />
//              <map type="Metabolite" drug="ChemicalDrug" />
//            </ReferenceTherapeutic2Drug>
            if (elm == null)
                return;
            defaultDrug = elm.getAttributeValue("default");
            List<Element> children = elm.getChildren();
            typeToDrug = new HashMap<>();
            for (Element child : children) {
                String type = child.getAttributeValue("type");
                String drug = child.getAttributeValue("drug");
                typeToDrug.put(type, drug);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public Action getCreateDrugFromRTAction(List<GKInstance> selection,
                                            Component parentComp) {
        Action action = new AbstractAction("Create Drug from RefTherapeutic") {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                DrugAutoCreator creator = new DrugAutoCreator();
                creator.convert(selection,
                                PersistenceManager.getManager().getActiveFileAdaptor(), 
                                parentComp);
            }
        };
        return action;
    }
    
    /**
     * Check if the passed list of GKInstance can be used to create Drugs.
     * @param instances
     * @return
     */
    public boolean isActionable(List<GKInstance> instances) {
        if (instances == null || instances.size() == 0)
            return false;
        instances = instances.stream()
                .filter(inst -> inst.getSchemClass().isa(ReactomeJavaConstants.ReferenceTherapeutic))
                .filter(inst -> !inst.isShell())
                .collect(Collectors.toList());
        return instances.size() > 0;
    }
    
    public void convert(List<GKInstance> refTherapeutics,
                        XMLFileAdaptor fileAdaptor,
                        Component parentCompt) {
        // Do a filtering
        if (refTherapeutics == null || refTherapeutics.size() == 0)
            return;
        // Only use RT
        refTherapeutics = refTherapeutics.stream()
                                         .filter(inst -> inst.getSchemClass().isa(ReactomeJavaConstants.ReferenceTherapeutic))
                                         .filter(inst -> !inst.isShell())
                                         .collect(Collectors.toList());
        if (refTherapeutics.size() == 0) {
            JOptionPane.showMessageDialog(parentCompt,
                                          "No ReferenceTherapeutic instance is selected. Note: Shell instances \n"
                                          + "cannot be used to create drug.",
                                          "No ReferenceTherapeutic Selection",
                                          JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (defaultDrug == null || typeToDrug == null) {
            JOptionPane.showMessageDialog(parentCompt,
                                          "No configuration for mapping types to drugs is set yet.",
                                          "Error in Configuation",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            for (GKInstance rt : refTherapeutics) {
                String type = (String) rt.getAttributeValue(ReactomeJavaConstants.type);
                String drugClsName = null;
                if (type != null)
                    drugClsName = typeToDrug.get(drugClsName);
                if (drugClsName == null)
                    drugClsName = defaultDrug;
                GKInstance drugInst = fileAdaptor.createNewInstance(drugClsName);
                copyAttributes(drugInst, rt);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parentCompt,
                                          "Error in creating Drug(s): " + e.getMessage(),
                                          "Error in Drug Creation",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(parentCompt,
                                      "Check to make sure auto-created instances have correct drug types!",
                                      "Manual Check Required",
                                      JOptionPane.WARNING_MESSAGE);
        return;
    }
    
    private void copyAttributes(GKInstance drugInst, GKInstance rt) throws Exception {
        // referenceEntity
        drugInst.setAttributeValue(ReactomeJavaConstants.referenceEntity, rt);
        // Copy names
        List<String> names = rt.getAttributeValuesList(ReactomeJavaConstants.name);
        drugInst.setAttributeValue(ReactomeJavaConstants.name, names);
        InstanceDisplayNameGenerator.setDisplayName(drugInst);
    }

}
