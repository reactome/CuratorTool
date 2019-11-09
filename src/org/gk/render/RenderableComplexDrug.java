package org.gk.render;

public class RenderableComplexDrug extends RenderableComplex implements DefaultRenderConstants {
    /**
     * Default constructor.
     */
    public RenderableComplexDrug() {
        this(null);
    }

    /**
     * @param displayName Display name of this RenderableEntitySetDrugSet
     */
    public RenderableComplexDrug(String displayName) {
        super(displayName);
        setForegroundColor(DEFAULT_DRUG_FOREGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}
