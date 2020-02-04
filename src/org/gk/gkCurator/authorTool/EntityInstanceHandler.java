/*
 * Created on Jan 18, 2007
 *
 */
package org.gk.gkCurator.authorTool;

import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.property.SearchDBTypeHelper;
import org.gk.render.Renderable;
import org.gk.render.RenderableFactory;

public class EntityInstanceHandler extends InstanceHandler {
    private SearchDBTypeHelper typeHelper;
    
    public EntityInstanceHandler() {
        typeHelper = new SearchDBTypeHelper();
    }
    
    protected Renderable convertToRenderable(GKInstance instance) throws Exception {
        // Have to find the type for instance
        Class<?> type = typeHelper.guessNodeType(instance);
        Renderable r = RenderableFactory.generateRenderable(type, container);
        if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.disease)) {
            boolean isForDisease = (instance.getAttributeValue(ReactomeJavaConstants.disease) != null);
            r.setIsForDisease(isForDisease);
        }
        if (instance.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            boolean isForDrug = InstanceUtilities.hasDrug(instance);
            r.setIsForDrug(isForDrug);
        }
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
