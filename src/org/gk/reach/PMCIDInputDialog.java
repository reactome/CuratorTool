package org.gk.reach;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.gk.util.DialogControlPane;
import org.gk.util.GKApplicationUtilities;

public class PMCIDInputDialog extends JDialog {
    private boolean isOKClicked = false;
    private JTextField pmcidTf;
    
    public PMCIDInputDialog(JFrame parent) {
        super(parent);
        init();
    }
    
    public boolean isOKClicked() {
        return this.isOKClicked;
    }
    
    public String[] getPMCIDs() {
        String text = pmcidTf.getText().trim();
        String[] tokens = text.split(",");
        List<String> ids = new ArrayList<>();
        for (String token : tokens) {
            token = token.trim();
            if (token.startsWith("PMC"))
                ids.add(token);
        }
        return ids.toArray(new String[] {});
    }
    
    private void init() {
        setTitle("Enter PMCID");
        
        JPanel contentPane = new JPanel();
        contentPane.setBorder(BorderFactory.createEtchedBorder());
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JLabel label = GKApplicationUtilities.createTitleLabel("Enter or paste PMCID (CMD+V or CTL+V) below:");
        contentPane.add(label, constraints);
        pmcidTf = new JTextField();
        pmcidTf.addActionListener(e -> {
            isOKClicked = true;
            dispose();
        });
        pmcidTf.setColumns(30);
        constraints.gridy = 1;
        contentPane.add(pmcidTf, constraints);
        JTextArea ta = new JTextArea("*: Up to three PMCIDs are supported. Add \", \" as "
                + "delimit. The process may take several minutes. You may work in other "
                + "place during waiting. The results will be displayed once it is done.");
        Font font = ta.getFont();
        ta.setFont(font.deriveFont(Font.ITALIC));
        ta.setBackground(contentPane.getBackground());
        ta.setEditable(false);
        ta.setWrapStyleWord(true);
        ta.setLineWrap(true);
        constraints.gridy = 2;
        contentPane.add(ta, constraints);
        getContentPane().add(contentPane, BorderLayout.CENTER);
        
        DialogControlPane controlPane = new DialogControlPane();
        controlPane.setBorder(BorderFactory.createEtchedBorder());
        controlPane.getOKBtn().setText("Submit");
        controlPane.getOKBtn().addActionListener(e -> {
            isOKClicked = true;
            dispose();
        });
        controlPane.getCancelBtn().addActionListener(e -> dispose());
        getContentPane().add(controlPane, BorderLayout.SOUTH);
        
        setSize(400, 300);
        setLocationRelativeTo(getOwner());
        setModal(true);
    }
}