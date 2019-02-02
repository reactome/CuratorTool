/*
 * Created on Aug 18, 2009
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * This QA check reports Complexes whose species does not exactly
 * equal the species of its components. Both the <code>species</code>
 * and <code>relatedSpecies</code> values are used. 
 * 
 * @see SpeciesCheck
 * @author wgm
 */
@SuppressWarnings("unchecked")
public class ComplexSpeciesCheck extends SpeciesCheck {
    
    public ComplexSpeciesCheck() {
        checkClsName = ReactomeJavaConstants.Complex;
        followAttributes = new String[] {
                ReactomeJavaConstants.hasComponent,
        };
    }

    @Override
    public String getDisplayName() {
        return "Complex_Components_Species_Mismatch";
    }

    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        ResultTableModel model = new ComponentTableModel();
        String[] colNames = new String[] {
                "Component",
                "Species"
        };
        model.setColNames(colNames);
        return model;
    }
    
    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.Complex);
    }
    
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        Set<GKInstance> toBeLoaded = loadComplexHasComponent(instances, dba);
        if (toBeLoaded == null)
            return; 
        // No need to load EntitySet's member information. They should be handled
        // by EntitySet species checking.
        if (progressPane != null)
            progressPane.setText("Load species...");
        loadSpeciesAttributeVAlues(toBeLoaded, dba);
    }
    
}
