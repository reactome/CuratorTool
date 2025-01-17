/*
 * Created on Sep 29, 2010
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

/**
 * This QA check verifies that the <code>DB_ID</code> values
 * of instances represented in a diagram exist in the database hosting 
 * the diagram.
 * 
 * Although this class performs a specific QA check, subclasses
 * can extend it to perform other diagram checks.
 *
 * @author Fred Loney loneyf@ohsu.edu
 */
public class DiagramInstanceDeletionCheck extends AbstractPathwayDiagramCheck {

    @Override
    public String getDisplayName() {
        return "Deleted_Objects_In_Diagram";
    }
    
    /**
     * Returns a collection of DB_IDs for instances that have not been in the
     * database any more. This may be caused by deletion of instances originally
     * drawn in the pathway diagram
     * 
     * @param instance the PathwayDiagram instance
     * @return the missing DB_IDs
     */
    @Override
    protected Collection<Long> doCheck(GKInstance instance) throws Exception {
        return getMissingDbIds(instance);
    }

    private Collection<Long> getMissingDbIds(GKInstance instance) throws Exception {
        if (!instance.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            throw new IllegalArgumentException(instance + " is not a PathwayDiagram instance!");
        Collection<Long> dbIds = extractReactomeIds(instance);
        if (dbIds == null || dbIds.size() == 0)
            return Collections.emptyList();
        Collection<Long> existing;
        if (dataSource instanceof MySQLAdaptor) {
            MySQLAdaptor dba = (MySQLAdaptor) dataSource;
            existing = dba.existing(dbIds);
        } 
        else {
            @SuppressWarnings("unchecked")
            Collection<GKInstance> instances = dataSource.fetchInstanceByAttribute(
                    ReactomeJavaConstants.DatabaseObject,
                    ReactomeJavaConstants.DB_ID,
                    "=",
                    dbIds);
            existing = instances.stream()
                                .map(GKInstance::getDBID)
                                .collect(Collectors.toSet());
        }
 
        // The most common case.
        if (existing.size() == dbIds.size()) {
            return Collections.emptyList();
        }
        // Return the set difference.
        Set<Long> missing = new HashSet<Long>(dbIds);
        missing.removeAll(existing);
        
        return missing;
    }

    @Override
    protected String getResultTableIssueDBIDColName() {
        return "DB_IDs for Deleted Objects";
    }
    
    @Test
    public void testCheckInCommand() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_122118",
                                            "root",
                                            "macmysql01");
        super.testCheckInCommand(dba);
    }

}
