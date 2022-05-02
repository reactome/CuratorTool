/*
 * Created on Dec 1, 2004
 *
 */
package org.gk.pathView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;

/**
 * This model class is used to help process data for comparing reactions in the sky.
 * @author wgm
 */
public class ComparingReactionsInSkyModel {
    private Neo4JAdaptor dba;

    public ComparingReactionsInSkyModel() {
    }

    public ComparingReactionsInSkyModel(Neo4JAdaptor dba) {
        this.dba = dba;
    }

    public void setNeo4JAdaptor(Neo4JAdaptor adapter) {
        this.dba = adapter;
    }

    /**
     * Get the list of reactions that should be in the sky.
     * @return
     */
    public List getReactionsInSky() {
        if (dba == null)
            return new ArrayList();
        try {
            Collection c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                                        ReactomeJavaConstants._doRelease,
                                                        "=",
                                                        "true");
            return new ArrayList(c);
        }
        catch(Exception e) {
            System.err.println("ComparingReactionsInSkyModel.getReactionsInSky(): " + e);
            e.printStackTrace();
        }
        return new ArrayList();
    }
}