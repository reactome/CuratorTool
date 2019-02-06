/*
 * Created on Sep 29, 2010
 *
 */
package org.gk.qualityCheck;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

/**
 * This QA check verifies that the <code>reactomeId</code> values
 * of objects represented in a diagram exists in the data source.
 * 
 * Although this class performs a specific QA check, subclasses
 * can extend it to perform other diagram checks.
 *
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class DiagramMissingInstanceCheck extends AbstractPathwayDiagramCheck {

    @Override
    public String getDisplayName() {
        return "Deleted_Objects_In_Diagram";
    }
    
    /**
     * Returns the diagram entity db ids which are not in the database.
     * 
     * @param instance the PathwayDiagram instance
     * @return the missing db ids
     */
    @Override
    protected Collection<Long> doCheck(GKInstance instance) throws Exception {
        return getMissingDbIds(instance);
    }

    private Collection<Long> getMissingDbIds(GKInstance instance)
            throws InvalidAttributeException, Exception, SQLException {
        if (!instance.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            throw new IllegalArgumentException(instance + " is not a PathwayDiagram instance!");
        Collection<Long> dbIds = extractReactomeIds(instance);
        if (dbIds == null || dbIds.size() == 0)
            return Collections.emptyList();
        Collection<Long> existing;
        if (dataSource instanceof MySQLAdaptor) {
            MySQLAdaptor dba = (MySQLAdaptor) dataSource;
            existing = dba.existing(dbIds);
        } else {
            @SuppressWarnings("unchecked")
            Collection<GKInstance> instances = dataSource.fetchInstanceByAttribute(
                    ReactomeJavaConstants.DatabaseObject,
                    ReactomeJavaConstants.DB_ID,
                    "=",
                    dbIds);
            if (instances.size() == dbIds.size()) {
                return Collections.emptyList();
            }
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
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, 
                                         ReactomeJavaConstants.PathwayDiagram);
    }

    @Override
    protected String getResultTableIssueDBIDColName() {
        return "DB_IDs without Instances";
    }

}
