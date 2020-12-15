package org.gk.gkCurator;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.util.DialogControlPane;

/**
 * Create multimers from selected instances in a single operation.
 */
public class MultimerDialog extends JDialog {
    private boolean isOKClicked = false;
    private int num;

    public MultimerDialog(JFrame parent, List<GKInstance> instances) {
        super(parent);
        init(instances);
    }

    public boolean getIsOKClicked() {
        return isOKClicked;
    }

    public void setIsOKClicked(boolean isOKClicked) {
        this.isOKClicked = isOKClicked;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    private void init(List<GKInstance> instances) {
        setTitle("Create Multimers");

        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        contentPane.setBorder(BorderFactory.createEtchedBorder());
        getContentPane().add(contentPane, BorderLayout.CENTER);

        InstanceListPane listPane = new InstanceListPane();
        listPane.setDisplayedInstances(instances);
        listPane.setTitle("Selected EWAS Instances for Multimers:");
        contentPane.add(listPane);

        JPanel numPane = new JPanel();
        Border emptyBorder = BorderFactory.createEmptyBorder(2, 4, 2, 4);
        Border etchedBorder = BorderFactory.createEtchedBorder();
        Border compoundBorder = BorderFactory.createCompoundBorder(etchedBorder, emptyBorder);
        numPane.setBorder(compoundBorder);
        numPane.setLayout(new BoxLayout(numPane, BoxLayout.X_AXIS));
        contentPane.add(numPane);

        JLabel numLabel = new JLabel("Choose Number of Monomers: ");
        Font labelFont = numLabel.getFont().deriveFont(Font.BOLD);
        numLabel.setFont(labelFont);
        numPane.add(numLabel);

        SpinnerNumberModel model = new SpinnerNumberModel(2, 2, Integer.MAX_VALUE, 1);
        JSpinner spinner = new JSpinner(model);
        numPane.add(spinner);

        DialogControlPane controlPane = new DialogControlPane();
        getContentPane().add(controlPane, BorderLayout.SOUTH);

        controlPane.getCancelBtn().addActionListener(e -> dispose());
        controlPane.getOKBtn().addActionListener(e -> {
                isOKClicked = true;
                setNum((int) spinner.getValue());
                dispose();
        });

        setSize(400, 300);
        setLocationRelativeTo(getOwner());
        setModal(true);
    }
}
