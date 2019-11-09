package org.gk.render;

public class RenderableEntitySetDrug extends RenderableEntitySet implements DefaultRenderConstants {
    /**
     * Default constructor.
     */
    public RenderableEntitySetDrug() {
        this(null);
    }

    /**
     * @param displayName Display name of this RenderableEntitySetDrugSet
     */
    public RenderableEntitySetDrug(String displayName) {
        super(displayName);
        setForegroundColor(DEFAULT_DRUG_FOREGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}
