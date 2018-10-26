package org.gk.qualityCheck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.gk.persistence.MySQLAdaptor;
import org.gk.util.GKApplicationUtilities;

/**
 * Use this method to run all QAes in a command line shell.
 * @author wug
 *
 */
public class CommandLineRunner {
    private static Logger logger = Logger.getLogger(CommandLineRunner.class);

    public static void main(String[] args) throws Exception {
        PropertyConfigurator.configure("resources/log4j.properties");
        
        File authFile = getAuthFile();
        Properties authProps = new Properties();
        authProps.load(new FileInputStream(authFile));
        
        File qaPropsFile = getQAPropertiesFile();
        Properties qaProps = new Properties();
        qaProps.load(new FileInputStream(qaPropsFile));
        String cutoffDateStr = qaProps.getProperty("cutoffDate");
        Date cutoffDate = null;
        if (cutoffDateStr != null) {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            cutoffDate = df.parse(cutoffDateStr);
        }

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

        MySQLAdaptor dba = new MySQLAdaptor(authProps.getProperty("dbHost"),
                authProps.getProperty("dbName"),
                authProps.getProperty("dbUser"),
                authProps.getProperty("dbPwd"));
        
        List<QualityCheck> qaes = getAllQAes();
        File dir = getOutputDir();
        for (QualityCheck qa : qaes) {
            qa.setDatasource(dba);
            qa.setCutoffDate(cutoffDate);
            logger.info("Run " + qa.getClass().getName() + "...");
            QAReport report = qa.checkInCommand();
            if (report == null) {
                logger.error("Cannot generate report!");
                continue;
            }
            if (report.isEmpty()) {
                logger.info("Nothing to report!");
                continue;
            }
            report.output(qa.getClass().getSimpleName() + ".txt", dir.getAbsolutePath());
            logger.info("Output to " + dir.getName() + File.separator + qa.getClass().getSimpleName() + ".txt");
        }
    }
    
    private static File getOutputDir() throws IOException {
        File dir = new File("QA_Output");
        if (dir.exists())
            GKApplicationUtilities.delete(dir);
        dir.mkdir();
        return dir;
    }
    
    private static List<QualityCheck> getAllQAes() throws IOException {
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
    
    private static File getQAPropertiesFile() {
        File file = new File("resources/qa.properties");
        if (file.exists())
            return file;
        throw new IllegalStateException("Make sure resources/qa.properties exists, which provides common QA settings");
    }
    
}
