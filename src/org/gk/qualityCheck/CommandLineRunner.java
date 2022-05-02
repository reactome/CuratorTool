package org.gk.qualityCheck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.util.FileUtilities;
import org.gk.util.GKApplicationUtilities;

/**
 * Use this method to run all QAes in a command line shell.
 * @author wug
 *
 */
public class CommandLineRunner {

    private static Logger logger = Logger.getLogger(CommandLineRunner.class);

    // TODO - on QA check refactoring, get the summary constants
    // below from a common class.

    /** The summary simple file name. */
    private static final String SUMMARY_FILE_NM = "summary.tsv";

    /** The summary file field delimiter. */
    private static final String SUMMARY_DELIMITER = "\t";

    /** The summary file headings. */
    private static final String[] SUMMARY_HDGS = {
            "Report", "Issue Count"
    };

    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure("resources/log4j.properties");
        
        File authFile = getAuthFile();
        Properties authProps = new Properties();
        authProps.load(new FileInputStream(authFile));
        
        // The command line arguments take precedence.
        Map<String, String> cmdLineProps = new HashMap<String, String>();
        String option = null;
        for (String arg: args) {
            if (arg.startsWith("--")) {
                // An option without an argument has value true.
                if (option != null) {
                    cmdLineProps.put(option, Boolean.TRUE.toString());
                }
                option = arg.substring(2);
            } else if (option == null) {
                    String msg = "Unrecognized argument: " + arg;
                    throw new IllegalArgumentException(msg);
            } else {
                cmdLineProps.put(option, arg);
                option = null;
            }
        }
        // A final option without an argument has value true.
        if (option != null) {
            cmdLineProps.put(option, Boolean.TRUE.toString());
        }
        // Augment or override the auth file values.
        authProps.putAll(cmdLineProps);

        Neo4JAdaptor dba = new Neo4JAdaptor(authProps.getProperty("dbHost"),
                authProps.getProperty("dbName"),
                authProps.getProperty("dbUser"),
                authProps.getProperty("dbPwd"));
        
        List<QualityCheck> checks = getAllQAChecks();
        File dir = getOutputDir();
        File summaryFile = new File(dir, SUMMARY_FILE_NM);
        FileUtilities summary = new FileUtilities();
        summary.setOutput(summaryFile.getPath());
        summary.printLine(String.join(SUMMARY_DELIMITER, SUMMARY_HDGS));
        for (QualityCheck check : checks) {
            check.setDatasource(dba);
            logger.info("Run " + check.getClass().getName() + "...");
            QAReport report = check.checkInCommand();
            if (report == null) {
                logger.error("Cannot generate report!");
                continue;
            }
            String title = check.getDisplayName().replace('_', ' ');
            String summaryLine = String.join(SUMMARY_DELIMITER, title,
                    Integer.toString(report.getReportLines().size()));
            summary.printLine(summaryLine);
            if (report.isEmpty()) {
                logger.info("Nothing to report!");
                continue;
            }
            String baseName = check.getReportFileName();
            File file = new File(dir, baseName);
            report.output(baseName, dir.getAbsolutePath());
            logger.info("Output to " + file.getPath());
        }
        summary.close();
        logger.info("Summary is printed as " + summaryFile.getPath());
    }
    
    private static File getOutputDir() throws IOException {
        File dir = new File("QA_Output");
        if (dir.exists())
            GKApplicationUtilities.delete(dir);
        dir.mkdir();
        return dir;
    }
    
    private static List<QualityCheck> getAllQAChecks() throws IOException {
        File file = new File("resources/CommandLineQAList.txt");
        if (!file.exists())
            throw new IllegalStateException("Make sure resources/CommandLineQAList.txt exists, which lists all QAes should be run in a shell.");
        try (Stream<String> lines = Files.lines(Paths.get(file.getAbsolutePath()))) {
            List<QualityCheck> rtn = new ArrayList<>();
            lines.filter(line -> !line.startsWith("#"))
                 .forEach(line -> {
                     try {
                         Class<?> cls = Class.forName(line);
                         QualityCheck qa = (QualityCheck) cls.newInstance();
                         rtn.add(qa);
                     }
                     catch(Exception e) {
                         logger.error("Error in getAllQAes: ", e);
                     }
                 });
            return rtn;
        }
    }
    
    private static File getAuthFile() {
        File file = new File("resources/auth.properties");
        if (file.exists())
            return file;
        throw new IllegalStateException("Make sure resources/auth.properties exists, which provides database connection information");
    }
    
}
