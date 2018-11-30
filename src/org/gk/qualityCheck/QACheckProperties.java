package org.gk.qualityCheck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * QACheckProperties is the singleton common QA check properties service.
 * 
 * <em>Note</em>: this service is only used for command line execution.
 *
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class QACheckProperties {

    private static final String CUTOFF_DATE_PROP = "cutoffDate";

    private final static Logger logger = Logger.getLogger(QACheckProperties.class);
    
    private static final String QA_PROP_RESOURCE = "/qa.properties";
    
    private static final String QA_PROP_FILE = "resources" + QA_PROP_RESOURCE;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    private static final Properties PROPERTIES = loadProperties();

    public static String getProperty(String key) {
        return PROPERTIES.getProperty(key);
    }

    public static Date getDate(String key) throws ParseException {
        String value = getProperty(key);
        return  value == null ? null : DATE_FORMAT.parse(value);
    }

    public static Float getFloat(String key) {
        String value = getProperty(key);
        return value == null ? null : Float.parseFloat(value);
    }

    public static Date getCutoffDate() {
        return (Date) PROPERTIES.get(CUTOFF_DATE_PROP);
    }
    
    private static Properties loadProperties() {
        Properties properties = new Properties();
        File propsFile = new File(QA_PROP_FILE);
        try {
            InputStream is = null;
            if (propsFile.exists()) {
                is = new FileInputStream(propsFile);
            } else {
                // Backup is the resource, e.g. if running JUnit
                // without the current directory set to ./resources.
                is = QACheckProperties.class.getResourceAsStream(QA_PROP_RESOURCE);
            }
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            // Not a fatal error.
            logger.error("QA property file could not be loaded: " + e);
        }
        // Cast the instance escape cut-off date to a date.
        String cutoffDateStr = properties.getProperty(CUTOFF_DATE_PROP);
        if (cutoffDateStr != null) {
            DateFormat df = DATE_FORMAT;
            try {
                Date cutoffDate = df.parse(cutoffDateStr);
                properties.put(CUTOFF_DATE_PROP, cutoffDate);
                logger.info("Skip list cut-off date: " + cutoffDate);
            } catch (ParseException e) {
                // Not a fatal error.
                logger.error("Cut-off date property value format invalid: " + cutoffDateStr);
            }
        }

        return properties;
    }
}
