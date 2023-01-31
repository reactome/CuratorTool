package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

/**
 * Perform updates for MarkerReference
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class CellMarkerReferenceUpdate {
    
    public CellMarkerReferenceUpdate() {
        
    }
    
    @Test
    public void updateCellInReference() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "gk_central",
                "",
                "");
        Collection<GKInstance> instances = dba.fetchInstancesByClass(ReactomeJavaConstants.MarkerReference);
        System.out.println("Total MarkerReferences: " + instances.size());
        List<GKInstance> toBeUpdated = new ArrayList<>();
        int isNotUsed = 0;
        for (GKInstance instance : instances) {
            List<GKInstance> current = instance.getAttributeValuesList(ReactomeJavaConstants.cell);
            if (current != null && current.size() > 0) {
                System.out.println(instance + " has cell assigned!");
                continue;
            }
            Collection<GKInstance> cells = instance.getReferers(ReactomeJavaConstants.markerReference);
            if (cells == null || cells.size() == 0) {
                System.out.println(instance + " is not used.");
                isNotUsed ++;
                continue;
            }
            List<GKInstance> values = new ArrayList<>(cells);
            InstanceUtilities.sortInstances(values);
            instance.setAttributeValue(ReactomeJavaConstants.cell, values);
            toBeUpdated.add(instance);
        }
        System.out.println("To be updated: " + toBeUpdated.size());
        System.out.println("Is not used: " + isNotUsed);
        // Try to update the database
        try {
            dba.startTransaction();
            GKInstance newIE = ScriptUtilities.createDefaultIE(dba, 
                    ScriptUtilities.GUANMING_WU_DB_ID, 
                    true);
            int count = 0;
            for (GKInstance instance : toBeUpdated) {
                System.out.println(count + ": " + instance);
                // Have to call this first to get the list
                instance.getAttributeValue(ReactomeJavaConstants.modified);
                instance.addAttributeValue(ReactomeJavaConstants.modified, newIE);
                dba.updateInstanceAttribute(instance, ReactomeJavaConstants.cell);
                count ++;
            }
            dba.commit();
        }
        catch(Exception e) {
            dba.rollback();
            throw e;
        }
    }

}
