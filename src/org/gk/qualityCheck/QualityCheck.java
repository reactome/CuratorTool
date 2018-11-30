/*
 * Created on Apr 5, 2005
 *
 */
package org.gk.qualityCheck;

import java.awt.Component;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.schema.GKSchemaClass;

/**
 * An interface to check the quality of the data for a designed data source.
 * @author wgm
 *
 */
public interface QualityCheck {
    
    static final String REPORT_FILE_EXT = ".tsv";
    
    static final String SKIP_LIST_FILE_EXT = ".txt";

    /**
     * Check the whole database and generate a report. This method is designed so that
     * a QualityCheck can be run in a command line without GUIs.
     * @return
     */
    public QAReport checkInCommand() throws Exception;
    
    /**
     * @return the name used to generate the report title and file name
     */
    public String getDisplayName();
    
    /**
     * @return the report simple file name
     */
    default public String getReportFileName() {
        return getDisplayName() + REPORT_FILE_EXT;
    }
    
    /**
     * @return the skip list simple file name
     */
    default public String getSkipListFileName() {
        return getDisplayName() + SKIP_LIST_FILE_EXT;
    }

    /**
     * Check the data in the specify data source. The output should be handled
     * in this method.
     *
     */
    public void check();
    
    /**
     * Check a single GKInstance instance.
     * @param instance
     */
    public void check(GKInstance instance);
    
    /**
     * Check a list of GKInstance objects.
     * @param instances
     */
    public void check(List<GKInstance> instances);
    
    /**
     * Check all GKInstance objects in the specified classes.
     * @param cls
     */
    public void check(GKSchemaClass cls);
    
    /**
     * Project-based check. This check should be used in a project based view (e.g. EventCentricView).
     * @param event usually it should be a pathway container having other events though it should work 
     * with a single reaction too.
     */
    public void checkProject(GKInstance event);
    
    /**
     * Set the data source. A data source is a repository, which can be a database
     * or a local project.
     * @param dataSource
     */
    public void setDatasource(PersistenceAdaptor dataSource);
    
    /**
     * Get the data source specified in this QualityCheck.
     * @return
     */
    public PersistenceAdaptor getDatasource();
   
    /**
     * For result output purpose. This specified Component can be used for some GUIs
     * as owner component.
     * @param comp
     */
    public void setParentComponent(Component comp);
    
    /**
     * Set a system-wide properties to be used for some GUIs.
     * @param prop
     */
    public void setSystemProperties(Properties prop);
    
    /**
     * Set if this QA can escape a set of GKInstances.
     * @param isNeeded
     */
    public void setIsInstancesEscapeNeeded(boolean isNeeded);
    
}
