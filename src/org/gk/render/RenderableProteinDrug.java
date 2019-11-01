package org.gk.render;

import java.awt.Color;

public class RenderableProteinDrug extends RenderableProtein {
    
    public RenderableProteinDrug() {
        setForegroundColor(Color.RED);
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}
