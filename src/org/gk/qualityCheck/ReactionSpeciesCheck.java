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
import org.gk.schema.SchemaClass;

/**
 * This QA check reports Reactions whose species does not exactly
 * equal the species of its participants. Both the <code>species</code>
 * and <code>relatedSpecies</code> values are used. 
 *
 * @author Fred Loney loneyf@ohsu.edu
 */
public class ReactionSpeciesCheck extends SpeciesCheck {
    private ReactionQACheckHelper qaHelper;
    
    public ReactionSpeciesCheck() {
        checkClsName = ReactomeJavaConstants.ReactionlikeEvent;
        qaHelper = new ReactionQACheckHelper();
    }

    @Override
    public String getDisplayName() {
        return "Reaction_Participants_Species_Mismatch";
    }

    @Override
    protected Set<GKInstance> getAllContainedEntities(GKInstance container)
            throws Exception {
        return qaHelper.getAllContainedEntities(container);
    }

    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        String[] colNames = new String[] {"Role", "Participant", "Species"};
        ResultTableModel model = qaHelper.getResultTableModel(colNames, 
                                                              checkAttribute);
        return model;
    }
    
    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.Reaction);
    }

    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        // Only MySQLAdator should be used to load attributes
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        String[] attNames = new String[] {
                ReactomeJavaConstants.input,
                ReactomeJavaConstants.output,
                ReactomeJavaConstants.species,
                ReactomeJavaConstants.catalystActivity
        };
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.Reaction);
        qaHelper.loadAttributes(instances,
                                cls,
                                attNames,
                                dba,
                                progressPane);
        if (progressPane != null && progressPane.isCancelled())
            return ;
        if (progressPane != null)
            progressPane.setText("Load CatalystActivity...");
        loadAttributes(ReactomeJavaConstants.CatalystActivity,
                       ReactomeJavaConstants.physicalEntity,
                       dba);
        if (progressPane != null && progressPane.isCancelled())
            return;
        qaHelper.loadRegulations(dba, 
                                 progressPane);
        if (progressPane != null && progressPane.isCancelled())
            return;
        if (progressPane != null)
            progressPane.setText("Load GenomeEncodedEntity species...");
        loadAttributes(ReactomeJavaConstants.GenomeEncodedEntity, 
                       ReactomeJavaConstants.species, 
                       dba);
        if (progressPane != null)
            progressPane.setText("Load Complex species...");
        loadAttributes(ReactomeJavaConstants.Complex, 
                       ReactomeJavaConstants.species, 
                       dba);
        if (progressPane != null)
            progressPane.setText("Load EntitySet species...");
        loadAttributes(ReactomeJavaConstants.EntitySet, 
                       ReactomeJavaConstants.species, 
                       dba);
    }
    
}
