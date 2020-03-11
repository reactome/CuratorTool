package org.gk.render;

import java.awt.Color;

public class RenderableProteinDrug extends RenderableProtein {
    
    public RenderableProteinDrug() {
        setForegroundColor(DEFAULT_DRUG_FOREGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }

}
