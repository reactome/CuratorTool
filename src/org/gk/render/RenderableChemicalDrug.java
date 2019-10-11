package org.gk.render;

import java.awt.Color;

/**
 * This customized Renderer is for ChemicalDrug.
 * @author wug
 *
 */
public class RenderableChemicalDrug extends RenderableChemical implements DefaultRenderConstants {

    public RenderableChemicalDrug() {
        setForegroundColor(Color.RED); // As the default for the drugs
        setBackgroundColor(DEFAULT_DRUG_BACKGROUND);
    }
}