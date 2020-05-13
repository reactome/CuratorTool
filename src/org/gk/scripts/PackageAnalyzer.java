package org.gk.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Analyzes package dependencies and outputs to TSV file with the following format:
 *
 *  <pre>
 *    PACKAGE            REFERRING PACKAGE / IMPORT
 *    org.gk.database    org.gk.database.util
 *    org.gk.database    org.gk.gkEditor
 *    org.gk.database    org.gk.graphEditor
 *  </pre>
 *
 */
public class PackageAnalyzer {

    public PackageAnalyzer() {
    }

    public static void main(String[] args) throws IOException {
        List<Path> files = new ArrayList<Path>();

        // Add all files in 'src' directory to 'files' list.
        try (Stream<Path> paths = Files.walk(Paths.get("src"))) {
            // Verify that it is a regular Java file (ends in '.java').
            paths.filter(Files::isRegularFile)
                 .filter(path -> !path.endsWith(".java"))
                 .forEach(files::add);
        }

        // Map of 'package name' and set of 'referring packages'.
        // e.g. 'org.gk.pathView=[org.gk.model, org.gk.schema, org.gk.util, org.gk.persistence, org.gk.database]'
        Map<String, Set<String>> packageMap = new HashMap<String, Set<String>>();

        // For all files in 'src' directory.
        for (Path file : files) {
            String packageName = null;
            String importName = null;
            Set<String> imports = new HashSet<String>();

            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    // Parse package name and add to map if not already present.
                    if (packageName == null && line.startsWith("package")) {
                        packageName = line.replaceAll("package", "")
                                          .replaceAll("[\\s;]", "");
                        continue;
                    }

                    // Read lines and search for 'import org.gk.' statements.
                    if (line.startsWith("import org.gk.")) {
                        // Parse package names from 'import' lines.
                        importName = line.replaceAll("import", "")
                                         .replaceAll("[\\s;]", "");

                        // Remove classname from 'import' statement.
                        String[] arr = importName.split("\\.");
                        String[] pkgArr = Arrays.copyOf(arr, arr.length - 1);
                        String pkg = String.join(".", pkgArr);

                        imports.add(pkg);
                    }
                }
            }

            if (packageName == null || imports.size() == 0)
                continue;

            // Add package names to set.
            if (packageMap.containsKey(packageName)) {
                packageMap.get(packageName).addAll(imports);
            }
            else {
                packageMap.put(packageName, imports);
            }

        }

        // Convert package map to output format and output to file.
        Path outputFile = Paths.get("deps.tsv");
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (Entry<String, Set<String>> entry : packageMap.entrySet()) {
                String packageName = entry.getKey();
                Set<String> imports = entry.getValue();

                for (String importName : imports) {
                    writer.write(packageName, 0, packageName.length());
                    writer.write("\t");
                    writer.write(importName);
                    writer.write("\n");
                }
            }
        }
    }
}
