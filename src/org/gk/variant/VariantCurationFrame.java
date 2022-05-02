package org.gk.variant;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.gk.model.GKInstance;
import org.gk.persistence.Neo4JAdaptor;

@SuppressWarnings("serial")
public class VariantCurationFrame extends JFrame {	
	
	private Neo4JAdaptor dba;
	
	private VariantCuration variantCuration;
	
	final public Color blueColor = new Color(65, 105, 255);
	
	final public Color whiteColor = new Color(255, 255, 255);
	
	public VariantCurationFrame(String title) {
        super(title);
        
        setLayout(new BorderLayout());
        
        JPanel panel = new JPanel();
        
        panel.setLayout(null);
        
        JLabel geneNameLable = new JLabel("Choose gene: ");
        geneNameLable.setBounds(280, 30, 160, 25);
        panel.add(geneNameLable);
        JTextField geneNameField = new JTextField();
        geneNameField.setBounds(400, 30, 100, 25);
        geneNameField.setBackground(blueColor);
        geneNameField.setForeground(whiteColor);
        panel.add(geneNameField);
        
        JLabel mutationLable = new JLabel("Enter mutation: ");
        mutationLable.setBounds(280, 70, 160, 25);
        panel.add(mutationLable);
        JTextField mutationField = new JTextField();
        mutationField.setBounds(400, 70, 140, 25);
        mutationField.setBackground(blueColor);
        mutationField.setForeground(whiteColor);
        panel.add(mutationField);
        
        JLabel coordinateLable = new JLabel("Coodinate: ");
        coordinateLable.setBounds(280, 110, 160, 25);
        panel.add(coordinateLable);
        JTextField coordinateField = new JTextField();
        coordinateField.setBounds(400, 110, 100, 25);
        coordinateField.setBackground(blueColor);
        coordinateField.setForeground(whiteColor);
        panel.add(coordinateField);
        
        JLabel wtResidueLable = new JLabel("Wild type residue: ");
        wtResidueLable.setBounds(280, 150, 160, 25);
        panel.add(wtResidueLable);
        JTextField wtResidueField = new JTextField();
        wtResidueField.setBounds(400, 150, 100, 25);
        wtResidueField.setBackground(blueColor);
        wtResidueField.setForeground(whiteColor);
        panel.add(wtResidueField);
        
        JLabel replacedResidueLable = new JLabel("Replaced residue: ");
        replacedResidueLable.setBounds(280, 190, 160, 25);
        panel.add(replacedResidueLable);
        JTextField replacedResidueField = new JTextField();
        replacedResidueField.setBounds(400, 190, 100, 25);
        replacedResidueField.setBackground(blueColor);
        replacedResidueField.setForeground(whiteColor);
        panel.add(replacedResidueField);
        
        JLabel ewasLable = new JLabel("");
        ewasLable.setBounds(210, 230, 170, 25);
        panel.add(ewasLable);
        
        JLabel containingEntitiesLable = new JLabel("");
        containingEntitiesLable.setBounds(140, 270, 238, 25);
        panel.add(containingEntitiesLable);

        geneNameField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String geneName = geneNameField.getText();
                System.out.println(geneName);
                if (getDba() == null)
                    setDba(new Neo4JAdaptor("localhost", "gk_central_091120", "root", "lake3%7g", 3306));

                if (getVariantCuration() == null) {
//            			setVariantCuration(new VariantCuration());
                    try {
                        List<GKInstance> eawsInstances = variantCuration.getWtEwases(geneName);
                        List<String> ewasDisplayNames = new ArrayList<>();
                        for (GKInstance ewas : eawsInstances) {
                            ewasDisplayNames.add(ewas.getDisplayName());
                        }
                        JComboBox ewasDropDown = new JComboBox(ewasDisplayNames.toArray());
                        ewasDropDown.setSelectedIndex(-1);
                        ewasDropDown.setBounds(400, 230, 180, 25);
                        ewasDropDown.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                String[] todo = {"todo1", "todo2..."};
                                JComboBox entitiesDropDown = new JComboBox(todo);
                                entitiesDropDown.setSelectedIndex(-1);
                                entitiesDropDown.setBounds(400, 270, 180, 25);
                                panel.add(entitiesDropDown);
                                containingEntitiesLable.setText("Select a normal complex or EntitySet: ");
                            }
                        });
                        panel.add(ewasDropDown);
                    } catch (Exception e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }

                ewasLable.setText("Choose a wild-type EWAS: ");
            }
        });

        this.add(panel);

    }

	public Neo4JAdaptor getDba() {
		return dba;
	}

	public void setDba(Neo4JAdaptor dba) {
		this.dba = dba;
	}

	public VariantCuration getVariantCuration() {
		return variantCuration;
	}

	public void setVariantCuration(VariantCuration variantCuration) {
		this.variantCuration = variantCuration;
	}

}
