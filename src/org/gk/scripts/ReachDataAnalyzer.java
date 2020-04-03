package org.gk.scripts;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is used to check some files produced by the Reach NLM technology.
 * @author wug
 *
 */
public class ReachDataAnalyzer {
    private FileUtilities fu = new FileUtilities();
    
    public ReachDataAnalyzer() {
        
    }
    
    /**
     * Collection types and subtypes from the events.json files.
     * @throws IOException
     */
    @Test
    public void grepEventTypes() throws IOException {
        String dirName = "/Users/wug/datasets/Reach/fries_13k/";
        File dir = new File(dirName);
        int totalFiles = 0;
        Set<String> types = new HashSet<>();
        Set<String> subTypes = new HashSet<>();
        String line = null;
        Map<String, Set<String>> typeToSubtypes = new HashMap<>();
        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            if (fileName.endsWith(".events.json")) {
                totalFiles ++;
                fu.setInput(file.getAbsolutePath());
                String type = null;
                while ((line = fu.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("\"type\"")) {
                        type = extractJsonValue(line);
                        types.add(type);
                    }
                    else if (line.startsWith("\"subtype\"")) {
                        String subtype = extractJsonValue(line);
                        subTypes.add(subtype);
                        typeToSubtypes.compute(type, (key, set) -> {
                            if (set == null)
                                set = new HashSet<>();
                            set.add(subtype);
                            return set;
                        });
                    }
                }
                fu.close();
                if (totalFiles % 100 == 0) {
                    System.out.println("Parsed files: " + totalFiles);
                }
            }
        }
        // It should be around 13K.
        System.out.println("Total events json files: " + totalFiles);
        System.out.println("\nTotal event types: " + types.size());
        types.forEach(System.out::println);
        System.out.println("\ntotal event subtypes: " + subTypes.size());
        subTypes.forEach(System.out::println);
        System.out.println("\nType -> subtypes:");
        typeToSubtypes.forEach((type, subtypes) -> {
            System.out.println(type + ": " + subtypes);
        });
    }
    
    private String extractJsonValue(String line) {
        int index = line.indexOf(":");
        String value = line.substring(index + 1);
        if (value.startsWith("\"")) 
            value = value.substring(1, value.length() - 2); // e.g. "type":"activation",
        return value;
    }

}
