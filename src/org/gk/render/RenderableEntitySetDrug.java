package org.gk.render;

public class RenderableEntitySetDrug extends RenderableEntitySet implements DefaultRenderConstants {
    public RenderableEntitySetDrug() {
        setForegroundColor(DEFAULT_DRUG_FORGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}