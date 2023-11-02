/*
 * Created on Sep 20, 2010
 *
 */
package org.reactome.test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.XMLFileAdaptor;
import org.gk.util.GKApplicationUtilities;
import org.junit.Test;

/**
 * This class groups somet methods that are used to fix projects.
 * @author wgm
 *
 */
public class ProjectFixer {
   
    public ProjectFixer() {
    }
 
    @Test
    public void fixBruceProject022814() throws Exception {
        String dir = "/Users/gwu/Documents/gkteam/BruceMay/";
        String projectName = dir + "DNAmethylation-140228.rtpj";
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(projectName);
        
        // Get four IEs created that day
        Collection<?> ies = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.InstanceEdit);
        Set<GKInstance> touchedInsts = new HashSet<GKInstance>();
        Set<GKInstance> flaggedIes = new HashSet<GKInstance>();
        for (Object obj : ies) {
            GKInstance inst = (GKInstance) obj;
            if (inst.getDisplayName().contains("2014-02-28")) {
                List<GKInstance> insts = fileAdaptor.getReferers(inst);
                touchedInsts.addAll(insts);
                flaggedIes.add(inst);
            }
        }
        System.out.println("Total touched instances: " + touchedInsts.size());
        // Want to fix out what are newly created and what are updated
        int newInst = 0;
        int updated = 0;
        for (GKInstance inst : touchedInsts) {
            if (inst.getDBID() < 0)
                throw new IllegalStateException(inst + " has negative DB_ID!");
            System.out.println(inst);
            GKInstance created = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
            if (flaggedIes.contains(created)) {
                newInst ++;
                // There should be no IEs in the modified slot
                GKInstance modified = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.modified);
                if (modified != null)
                    throw new IllegalStateException(inst + " has been updated!");
                inst.setDBID(-inst.getDBID());
            }
            else 
                updated ++;
            inst.setIsDirty(true);
        }
        System.out.println("New instances: " + newInst);
        System.out.println("Updated instances: " + updated);
        for (GKInstance inst : flaggedIes)
            fileAdaptor.deleteInstance(inst);
        fileAdaptor.save(dir + "DNAmethylation-140228_fixed.rtpj");
        
        MySQLAdaptor dba = new MySQLAdaptor("reactomecurator.oicr.on.ca",
                                            "gk_central",
                                            "authortool",
                                            "T001test");
        int deleted = 0;
        int diffCls = 0;
        for (GKInstance inst : touchedInsts) {
            if (!dba.exist(inst.getDBID()))
                deleted++;
            else {
                GKInstance dbInst = dba.fetchInstance(inst.getDBID());
                if (!dbInst.getSchemClass().getName().equals(inst.getSchemClass().getName())) {
                    diffCls ++;
                }
            }
        }
        System.out.println("Deleted: " + deleted);
        System.out.println("diffed: " + diffCls);
        
    }
    
    @Test
    public void checkInstanceForIEs() throws Exception {
        String projectName = "/Users/wgm/Documents/gkteam/mcaudy/KRAB-KAP1_binding_CT52.rtpj";
        XMLFileAdaptor fileAdaptor = new XMLFileAdaptor();
        fileAdaptor.setSource(projectName);
        Collection<?> ies = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.InstanceEdit);
        Calendar calendar = GKApplicationUtilities.getCalendar("20100917000000");
        for (Iterator<?> it = ies.iterator(); it.hasNext();) {
            GKInstance ie = (GKInstance) it.next();
            if (ie.isShell())
                continue; // Don't need to check
            String dateText = (String) ie.getAttributeValue(ReactomeJavaConstants.dateTime);
            Calendar ieCalendar = GKApplicationUtilities.getCalendar(dateText);
            if (ieCalendar.get(Calendar.DATE) >= calendar.get(Calendar.DATE)) {
                System.out.println(ie);
                // Get referrers
                Collection<?> referrers = fileAdaptor.getReferers(ie);
                System.out.println("Referrers: " + referrers.size());
                // Check if this IE is used in created or modified
                for (Iterator<?> it1 = referrers.iterator(); it1.hasNext();) {
                    GKInstance referrer = (GKInstance) it1.next();
                    if (referrer.getSchemClass().isa(ReactomeJavaConstants.InstanceEdit))
                        continue; // Don't care about IEs
                    List<?> values = referrer.getAttributeValuesList(ReactomeJavaConstants.created);
                    if (values.contains(ie)) {
                        System.out.println("\tCreated: " + referrer);
                        values.remove(ie);
                        referrer.setDBID(-referrer.getDBID());
                        referrer.setIsDirty(true);
                    }
                    values = referrer.getAttributeValuesList(ReactomeJavaConstants.modified);
                    if (values.contains(ie)) {
                        System.out.println("\tModified: " + referrer);
                        values.remove(ie);
                    }
                    if (referrer.getSchemClass().isValidAttribute(ReactomeJavaConstants.authored)) {
                        values = referrer.getAttributeValuesList(ReactomeJavaConstants.authored);
                        if (values.contains(ie)) {
                            System.out.println("\tAuthored: " + referrer);
                        }
                    }
                }
            }
        }
        fileAdaptor.save("/Users/wgm/Documents/gkteam/mcaudy/KRAB-KAP1_binding_CT52_fixed.rtpj");
    }
    
    @Test
    public void checkCalendarText() throws ParseException {
        String dateTime = "20230124015948";
        System.out.println("dateTime: " + dateTime);
        Calendar calendar = GKApplicationUtilities.getCalendar(dateTime);
        System.out.println("Month: " + calendar.get(Calendar.MONTH));
        System.out.println("Day: " + calendar.get(Calendar.DAY_OF_MONTH));
        System.out.println("Hour: " + calendar.get(Calendar.HOUR_OF_DAY));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        String text = formatter.format(calendar.getTime());
        System.out.println(text);
        
        Calendar now = GKApplicationUtilities.getCalendar();
        System.out.println("This month: " + now.get(Calendar.MONTH));
        System.out.println("This day: " + now.get(Calendar.DAY_OF_MONTH));
        System.out.println("This hour: " + now.get(Calendar.HOUR_OF_DAY));
        System.out.println("Current dataTime: " + GKApplicationUtilities.getDateTime());
        TimeZone timeZone = TimeZone.getTimeZone("GMT");
        SimpleDateFormat textFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
        textFormatter.setTimeZone(timeZone);
        System.out.println("Formated calendar: " + textFormatter.format(now.getTime()));
        
        text = "2023-01-24 01:59:48.0";
        // Use GMT time zone
        calendar = Calendar.getInstance(timeZone);
        calendar.setTime(formatter.parse(text));
        System.out.println("Converted back: " + formatter.format(calendar.getTime()));
        
        String[] testCases = {
                "20230124015948",
                "2023-01-24 01:59:48.0"
        };
        for (String test : testCases) {
            System.out.println(test);
            if (test.matches("(\\d){14}")) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
                Date date = format.parse(test);
                System.out.println("Date: " + formatter.format(date));
            }
            else if (test.matches("(\\d){4}-(\\d){2}-(\\d){2} (\\d){2}:(\\d){2}:(\\d){2}.(\\d)*")) {
                Date date = formatter.parse(test);
                System.out.println("Date: " + formatter.format(date));
            }
        }
        
    }
    
    @Test
    public void checkPathwayParticipants() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_current_ver36", 
                                            "root",
                                            "macmysql01");
        Long dbId = 202969L;
        GKInstance topLevelPathway = dba.fetchInstance(dbId);
        Set<GKInstance> participants = InstanceUtilities.grepPathwayParticipants(topLevelPathway);
        System.out.println("Number of participants: " + participants.size());
        GKInstance first = participants.iterator().next();
        List<GKInstance> list = new ArrayList<GKInstance>(participants);
        InstanceUtilities.sortInstances(list);
        for (GKInstance inst : list)
            System.out.println(inst);
    }
    
}
