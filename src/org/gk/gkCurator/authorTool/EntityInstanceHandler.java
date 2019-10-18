/*
 * Created on Jan 18, 2007
 *
 */
package org.gk.gkCurator.authorTool;

import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.render.Renderable;
import org.gk.render.RenderableFactory;

public class EntityInstanceHandler extends InstanceHandlerNodeType {
    
    protected Renderable convertToRenderable(GKInstance instance) throws Exception {
        // Have to find the type for instance
        Class<?> type = typeHelper.guessNodeType(instance);
        Renderable r = RenderableFactory.generateRenderable(type, container);
        return r;
    }

    protected void convertProperties(GKInstance instance, 
                                     Renderable r, 
                                     Map iToRMap) throws Exception {
    }

    @Override
    public void simpleConvertProperties(GKInstance instance, 
                                        Renderable r,
                                        Map<GKInstance, Renderable> toRMap) throws Exception {
        handleModifiedResidues(instance, r);
    }

    private void handleModifiedResidues(GKInstance instance,
                                        Renderable r) throws Exception {
        ModifiedResidueHandler handler = new ModifiedResidueHandler();
        handler.convertModifiedResiduesToNodeFeatures(instance, r, null);
    }
}
