package org.gk.render;

import java.awt.Color;

public class RenderableRNADrug extends RenderableRNA implements DefaultRenderConstants {
    
    public RenderableRNADrug() {
        setForegroundColor(Color.RED);
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}