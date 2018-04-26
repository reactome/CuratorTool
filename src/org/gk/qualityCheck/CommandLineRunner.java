package org.gk.qualityCheck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
        
        File file = getAuthFile();
        Properties properties = new Properties();
        properties.load(new FileInputStream(file));
        MySQLAdaptor dba = new MySQLAdaptor(properties.getProperty("dbHost"),
                properties.getProperty("dbName"),
                properties.getProperty("dbUser"),
                properties.getProperty("dbPwd"));
       
        List<QualityCheck> qaes = getAllQAes();
        File dir = getOutputDir();
        for (QualityCheck qa : qaes) {
            qa.setDatasource(dba);
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
        throw new IllegalStateException("Make sure resourcs/auth.properties exists, which provides database connection information");
    }
    
}
