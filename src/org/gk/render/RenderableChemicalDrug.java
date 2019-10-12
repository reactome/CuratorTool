package org.gk.render;

/**
 * This customized Renderer is for ChemicalDrug.
 * @author wug
 *
 */
public class RenderableChemicalDrug extends RenderableChemical implements DefaultRenderConstants {

    public RenderableChemicalDrug() {
        setForegroundColor(DEFAULT_DRUG_LABEL); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}