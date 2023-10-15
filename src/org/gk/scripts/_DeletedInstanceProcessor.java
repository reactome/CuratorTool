package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;

/**
 * This class is used to process something related to _Deleted instances.
 * @author wug
 *
 */
public class _DeletedInstanceProcessor {
    private MySQLAdaptor dba;
    
    public _DeletedInstanceProcessor() {
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Provide the following parameters in order: dbHost, dbName, dbUser, dbPwd");
            System.exit(0);
        }
        MySQLAdaptor dba = new MySQLAdaptor(args[0], args[1], args[2], args[3]);
        _DeletedInstanceProcessor processor = new _DeletedInstanceProcessor();
        processor.dba = dba;
        processor.fillReplacementInstanceDB_IDs();
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("curator.reactome.org",
                "test_gk_central_update_schema_by_wgm",
                "wgm",
                "zhe10jiang23");
        return dba;
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void fillReplacementInstanceDB_IDs() throws Exception {
        if (dba == null)
            dba = getDBA();
        Collection<GKInstance> deleteds = dba.fetchInstancesByClass(ReactomeJavaConstants._Deleted);
        dba.loadInstanceAttributeValues(deleteds, 
                new String[]{ReactomeJavaConstants.replacementInstances,
                 ReactomeJavaConstants.modified});
        List<GKInstance> instancesToBeUpdated = new ArrayList<>();
        for (GKInstance deleted : deleteds) {
            List<GKInstance> replacementInstances = deleted.getAttributeValuesList(ReactomeJavaConstants.replacementInstances);
            if (replacementInstances == null || replacementInstances.size() == 0)
                continue;
            List<Integer> replacementInstanceDB_IDs = replacementInstances.stream()
                    .map(GKInstance::getDBID)
                    .map(Long::intValue)
                    .collect(Collectors.toList());
            deleted.setAttributeValue(ReactomeJavaConstants.replacementInstanceDB_IDs, replacementInstanceDB_IDs);
            instancesToBeUpdated.add(deleted);
        }
        // Attach current IE to the modified slot
        GKInstance currentIE = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, false);
        for (GKInstance inst : instancesToBeUpdated) {
            inst.getAttributeValuesList(ReactomeJavaConstants.modified);
            inst.addAttributeValue(ReactomeJavaConstants.modified, currentIE);
        }
        // Let's update the database now
        System.out.println("Total instances to be updated: " + instancesToBeUpdated.size());
        int total = 0;
        try {
            dba.startTransaction();
            System.out.println("Store new IE...");
            dba.storeInstance(currentIE);
            for (GKInstance inst : instancesToBeUpdated) {
                System.out.println("Update " + inst + "...");
                dba.updateInstanceAttribute(inst, ReactomeJavaConstants.replacementInstanceDB_IDs);
                dba.updateInstanceAttribute(inst, ReactomeJavaConstants.modified);
                total ++;
            }
            dba.commit();
            System.out.println("Total insances updated: " + total);
        }
        catch(Exception e) {
            dba.rollback();
            e.printStackTrace();
        }
    }

}
