/*
 * Created on Oct 20, 2011
 *
 */
package org.reactome.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.junit.Test;

/**
 * This class is used to check the usage of some characters.
 * @author gwu
 *
 */
public class CharacterUsageChecker {
    
    public CharacterUsageChecker() {
        
    }
    
    @Test
    public void checkPersonSurname() throws Exception {
        Neo4JAdaptor dba = new Neo4JAdaptor("reactomedev.oicr.on.ca", 
                                            "gk_central",
                                            "authortool", 
                                            "T001test");
        Collection<GKInstance> c = dba.fetchInstancesByClass(ReactomeJavaConstants.Person);
        System.out.println("Total person instances: " + c.size());
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.Person);
        SchemaAttribute attribute = cls.getAttribute(ReactomeJavaConstants.surname);
        dba.loadInstanceAttributeValues(c, attribute);
        // Check some special case
        GKInstance example = dba.fetchInstance(1482970L);
        c.clear();
        c.add(example);
        List<GKInstance> list = new ArrayList<GKInstance>();
        for (GKInstance inst : c) {
            String surname = (String) inst.getAttributeValue(ReactomeJavaConstants.surname);
            char[] array = surname.toCharArray();
            for (char ch : array) {
                System.out.println(ch + "->" + (int)ch);
                if (!Character.isLetter(ch) && 
                    !Character.isSpaceChar(ch) &&
                     (ch != '-') &&
                     (ch != '\'') &&
                     (ch != '.')) {
//                    System.out.println(inst);
                    list.add(inst);
                    break;
                }
            }
        }
        System.out.println("Total offended cases: " + list.size());
        Collections.sort(list, new Comparator<GKInstance>() {
            public int compare(GKInstance inst1, GKInstance inst2) {
                return inst1.getDBID().compareTo(inst2.getDBID());
            }
        });
        for (GKInstance inst : list)
            System.out.println(inst);
    }
    
    @Test
    public void directCheck() {
        String text = "Barańska";
        char[] chars = text.toCharArray();
        for (char ch : chars) {
            System.out.println(ch + "->" + (int)ch);
        }
        String another = "Bara?ska";
        if (another.equals(text))
            System.out.println("Same!");
    }
    
}
