package org.gk.render;

/**
 * This customized Renderer is for ChemicalDrug.
 * @author wug
 *
 */
public class RenderableChemicalDrug extends RenderableChemical {

    public RenderableChemicalDrug() {
        setForegroundColor(DEFAULT_DRUG_FOREGROUND); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }

}
