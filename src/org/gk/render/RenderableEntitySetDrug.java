package org.gk.render;

public class RenderableEntitySetDrug extends RenderableEntitySet implements DefaultRenderConstants {
    /**
     * Default constructor.
     */
    public RenderableEntitySetDrug() {
        setForegroundColor(DEFAULT_DRUG_FORGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }

    /**
     * @param displayName Display name of this RenderableEntitySetDrugSet
     */
    public RenderableEntitySetDrug(String displayName) {
        super(displayName);
        setForegroundColor(DEFAULT_DRUG_FORGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}
