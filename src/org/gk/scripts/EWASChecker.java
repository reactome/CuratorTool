package org.gk.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * A collection of methods to check EWAS such as checking the usage of start coordinates in EWAS for the same
 * ReferenceEntity.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class EWASChecker {
    
    public EWASChecker() {
        
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_101223",
                "root",
                "macmysql01");
        return dba;
    }
    
    /**
     * In UniProt, the start coordinate is MET. In Reactome's annotation, sometime 1 is used. But sometimes,
     * 2 is used. This is to check this inconsistence usage. The check if for human EWAS only.
     * @throws Exception
     */
    @Test
    public void checkStartCoordinates() throws Exception {
        MySQLAdaptor dba = getDBA();
        GKInstance human = ScriptUtilities.getHomoSapiens(dba);
        Collection<GKInstance> ewases = dba.fetchInstanceByAttribute(ReactomeJavaConstants.EntityWithAccessionedSequence,
                ReactomeJavaConstants.species,
                "=",
                human);
        System.out.println("Total human EWASes: " + ewases.size());
        // Create a map from ReferenceEntities to EWASes so that we can compare
        Map<GKInstance, List<GKInstance>> ref2ewases = new HashMap<>();
        List<Integer> toBeCheckeced = Stream.of(1, 2).collect(Collectors.toList());
        for (GKInstance ewas : ewases) {
            // We care about ewases having startCoordinate is 1 or 2 only
            Integer startCoordiate = (Integer) ewas.getAttributeValue(ReactomeJavaConstants.startCoordinate);
            if (!toBeCheckeced.contains(startCoordiate))
                continue;
            GKInstance re = (GKInstance) ewas.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            if (re == null)
                continue;
            List<GKInstance> list = ref2ewases.get(re);
            if (list == null) {
                list = new ArrayList<>();
                ref2ewases.put(re, list);
            }
            list.add(ewas);
        }
        // Print out into a file
        String fileName = "Human_EWAS_StartCoordinate_Use_gk_central_10122023.tsv";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        StringBuilder builder = new StringBuilder();
        // Table header
        builder.append("ReferenceGeneProduct_DBID\t"
                + "ReferenceGeneProduct_DisplayName\t"
                + "EWAS_StartCoordinate_1_DBIDs\t"
                + "EWAS_StartCoordinate_1_DisplayNames\t"
                + "EWAS_StartCoordinate_2_DBIDs\t"
                + "EWAS_StartCoordinate_3_DisplayNames");
        System.out.println(builder.toString());
        fu.printLine(builder.toString());
        builder.setLength(0);
        int totalCases = 0;
        for (GKInstance ref : ref2ewases.keySet()) {
            List<GKInstance> list = ref2ewases.get(ref);
            if (list.size() == 1)
                continue; // Nothing needs to be done
            // Split it into two parts
            List<GKInstance> list1 = new ArrayList<>(); // startCoordinate = 1
            List<GKInstance> list2 = new ArrayList<>(); // startCoordinate = 2
            for (GKInstance ewas : list) {
                Integer startCoordinate = (Integer) ewas.getAttributeValue(ReactomeJavaConstants.startCoordinate);
                if (startCoordinate == 1)
                    list1.add(ewas);
                else if (startCoordinate == 2)
                    list2.add(ewas);
            }
            // Nothing to print since only one coordinate is used
            if (list1.size() == 0 || list2.size() == 0)
                continue;
            builder.append(ref.getDBID()).append("\t").append(ref.getDisplayName()).append("\t");
            builder.append(list1.stream().map(GKInstance::getDBID).map(dbId -> dbId.toString()).collect(Collectors.joining("; "))).append("\t");
            builder.append(list1.stream().map(GKInstance::getDisplayName).collect(Collectors.joining("; "))).append("\t");
            builder.append(list2.stream().map(GKInstance::getDBID).map(dbId -> dbId.toString()).collect(Collectors.joining("; "))).append("\t");
            builder.append(list2.stream().map(GKInstance::getDisplayName).collect(Collectors.joining("; ")));
            System.out.println(builder.toString());
            fu.printLine(builder.toString());
            builder.setLength(0);
            totalCases ++;
        }
        System.out.println("Total cases: " + totalCases);
        fu.close();
    }

}
