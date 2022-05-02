/*
 * Created on May 2, 2005
 */
package org.gk.scripts;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import org.gk.model.GKInstance;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.util.StringUtils;


/**
 * 
 * @author wgm
 */
public class DatabaseComparison {
    private Neo4JAdaptor localDBA;
    private Neo4JAdaptor remoteDBA;
    
    public DatabaseComparison(Neo4JAdaptor localDBA, Neo4JAdaptor remoteDBA) {
        this.localDBA = localDBA;
        this.remoteDBA = remoteDBA;
    }
    
    public void compare(String className) throws Exception {
        compare(className, localDBA, remoteDBA);
        compare(className, remoteDBA, localDBA);
    }
    
    private void compare(String className, Neo4JAdaptor sourceDBA, Neo4JAdaptor targetDBA) throws Exception {
        Collection sourceInstances = sourceDBA.fetchInstancesByClass(className);
        List<Long> dbIDs = new ArrayList();
        GKInstance reaction = null;
        for (Iterator it = sourceInstances.iterator(); it.hasNext();) {
            reaction = (GKInstance) it.next();
            dbIDs.add(reaction.getDBID());
        }
        Set<Long> missingDBIds = targetDBA.existing(dbIDs, false, true);
        for (Iterator it = missingDBIds.iterator(); it.hasNext();) {
            System.out.println(StringUtils.join(", ", List.copyOf(missingDBIds)));
            System.out.println("Total: " + missingDBIds.size());
        }
    }
    

    public static void main(String[] args) {
        try {
            Neo4JAdaptor localDBA = new Neo4JAdaptor("localhost",
                                                     "test_slicing",
                                                     "wgm",
                                                     "wgm",
                                                     3306);
            Neo4JAdaptor remoteDBA = new Neo4JAdaptor("brie8",
                                                      "test_slicing",
                                                      "authortool",
                                                      "T001test",
                                                      3306);
            DatabaseComparison comparer = new DatabaseComparison(localDBA, remoteDBA);
            comparer.compare("Reaction");
            comparer.compare("Pathway");
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}
