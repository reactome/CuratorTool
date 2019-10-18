package org.gk.gkCurator.authorTool;

import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.property.SearchDBTypeHelper;
import org.gk.render.Renderable;

public class InstanceHandlerNodeType extends InstanceHandler {
    protected SearchDBTypeHelper typeHelper;

    public InstanceHandlerNodeType() {
        typeHelper = new SearchDBTypeHelper();
    }

    @Override
    protected Renderable convertToRenderable(GKInstance instance)
            throws Exception {
        return null;
    }

    @Override
    protected void convertProperties(GKInstance instance, Renderable r,
            Map iToRMap) throws Exception {
    }
}
