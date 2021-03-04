package org.reactome.test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.gk.variant.VariantCurationFrame;

public class VariantCurationGuiTest {
	
	public static void main(String[] args) throws Exception {
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new VariantCurationFrame("Variant Curation");
                frame.setSize(800, 1200);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
		
	}

}
