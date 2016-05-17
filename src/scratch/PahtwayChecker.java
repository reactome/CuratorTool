/*
 * Created on Dec 4, 2015
 *
 */
package scratch;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * @author gwu
 *
 */
public class PahtwayChecker {

    public PahtwayChecker() {
    }
    
    @Test
    public void checkPathways() throws IOException {
        String dir = "/Users/gwu/Downloads/";
        String allPathwayFile = dir + "ReactomePathways.txt";
        FileUtilities fu = new FileUtilities();
        fu.setInput(allPathwayFile);
        String line = null;
        Set<String> allPathways = new HashSet<String>();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            if (tokens[2].equals("Homo sapiens"))
                allPathways.add(tokens[1].trim());
        }
        fu.close();
        System.out.println("Total pathways in all: " + allPathways.size());
        
        String gmtFile = dir + "ReactomePathways.gmt";
        fu.setInput(gmtFile);
        Set<String> gmtPathways = new HashSet<String>();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            gmtPathways.add(tokens[0]);
        }
        fu.close();
        System.out.println("Total pathways in gmt: " + gmtPathways.size());
        
        Set<String> shared = new HashSet<String>(gmtPathways);
        shared.retainAll(allPathways);
        System.out.println("Shared: " + shared.size());
        
        // Not shared in GMT
        Set<String> gmtNotShared = new HashSet<String>(gmtPathways);
        gmtNotShared.removeAll(shared);
        System.out.println("Not shared in GMT: " + gmtNotShared.size());
        for (String pathway : gmtNotShared)
            System.out.println(pathway);
        
        Set<String> allNotShared = new HashSet<String>(allPathways);
        allNotShared.removeAll(shared);
        System.out.println("\nNot shared in All: " + allNotShared.size());
        for (String pathway : allNotShared)
            System.out.println(pathway);
    }
    
}
