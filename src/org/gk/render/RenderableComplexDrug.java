package org.gk.render;

public class RenderableComplexDrug extends RenderableComplex implements DefaultRenderConstants {
    public RenderableComplexDrug() {
        setForegroundColor(DEFAULT_DRUG_FORGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}