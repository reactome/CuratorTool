/*
 * Created on Sep 6, 2005
 *
 */
package org.gk.scripts;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.junit.Test;

/**
 * A utility class is used to list display names in files for Complex, SimpleEntity, Reactions
 * and Pathways.
 * @author guanming
 *
 */
@SuppressWarnings("unchecked")
public class DisplayNameScripts {
    private MySQLAdaptor dba = null;
    
    public DisplayNameScripts() {
    }
    
    public void setMySQLAdaptor(MySQLAdaptor dba) {
        this.dba = dba;
    }
    
    private MySQLAdaptor getDba() throws Exception {
        if (dba != null)
            return dba;
        dba = PersistenceManager.getManager().getActiveMySQLAdaptor();
        return dba;
    }
    
    @Test
    public void updateDisplayNameMarkerReferences() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("", "", "", "");
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.MarkerReference);
        updateDisplayName(dba, c);
    }
    
    @Test
    public void updateDisplayNamesInFragmentModification() throws Exception {
        MySQLAdaptor dba = getDba();
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.FragmentModification);
        updateDisplayName(dba, c);
    }

    private void updateDisplayName(MySQLAdaptor dba, Collection<GKInstance> c) throws SQLException, Exception {
        int total = 0;
        List<GKInstance> toBeUpdated = new ArrayList<GKInstance>();
        for (GKInstance inst : c) {
            String newName = InstanceDisplayNameGenerator.generateDisplayName(inst);
            if (newName.equals(inst.getDisplayName()))
                continue;
            total ++;
            inst.setDisplayName(newName);
            toBeUpdated.add(inst);
        }
        System.out.println("Total: " + total);
        ScriptUtilities.updateInstanceNames(dba, toBeUpdated);
    }
    
    /**
     * Some _displayNames in IEs are not correct for some reason. This method is used to fix those instances.
     * @throws Exception
     */
    @Test
    public void updateDisplayNamesInIEs() throws Exception {
        MySQLAdaptor dba = getDba();
        Collection<GKInstance> c = dba.fetchInstanceByAttribute(ReactomeJavaConstants.InstanceEdit,
                                                                ReactomeJavaConstants._displayName, 
                                                                "like",
                                                                "%-");
//        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.InstanceEdit);
        dba.loadInstanceAttributeValues(c, new String[] {
                ReactomeJavaConstants.dateTime,
                ReactomeJavaConstants.author
        });
        int total = 0;
        List<GKInstance> toBeUpdated = new ArrayList<GKInstance>();
        for (GKInstance inst : c) {
            String name = inst.getDisplayName();
            if (name == null || !name.endsWith("-"))
                continue;
            total ++;
            String newName = InstanceDisplayNameGenerator.generateDisplayName(inst);
            inst.setDisplayName(newName);
            toBeUpdated.add(inst);
//            System.out.println(inst + "\t" + name);
        }
        System.out.println("Total: " + total);
        ScriptUtilities.updateInstanceNames(dba, toBeUpdated);
    }
    
    public void extract(String dirName) throws Exception {
        System.out.println("Starting extracting...");
        extract(dirName, "Complex");
        extract(dirName, "SimpleEntity");
        extract(dirName, "Reaction");
        extract(dirName, "Pathway");
        System.out.println("Extracting done!");
    }
    
    private void extract(String dirName, String clsName) throws Exception {
        System.out.println("Starting extracting " + clsName + " ...");
        List complexes = new ArrayList(dba.fetchInstancesByClass(clsName));
        InstanceUtilities.sortInstances(complexes);
        FileWriter writer = new FileWriter(dirName + File.separator + clsName + ".txt");
        PrintWriter printWriter = new PrintWriter(writer);
        GKInstance instance = null;
        for (Iterator it = complexes.iterator(); it.hasNext();) {
            instance = (GKInstance) it.next();
            printWriter.println(instance.getDisplayName());
        }
        printWriter.close();
        writer.close();
        System.out.println("Ending extracting " + clsName);
    }
    
    public static void main(String[] args) {
        try {
            MySQLAdaptor dba = new MySQLAdaptor("brie8.cshl.edu",
                                                "gk_central",
                                                "authortool",
                                                "T001test",
                                                3306);
            DisplayNameScripts extractor = new DisplayNameScripts();
            extractor.setMySQLAdaptor(dba);
            extractor.extract("resources");
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
