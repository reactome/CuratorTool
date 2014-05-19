/*
 * Created on Mar 8, 2014
 *
 */
package org.gk.scripts;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.junit.Test;

/**
 * @author gwu
 *
 */
public class ProcessMonitor {
    private String processId;
    private String processName;
    private String email;
    
    public ProcessMonitor() {
    }
    
    public void startMonitor() {
        System.out.println("Check \"" + processId + "\" for \"" + email + "\".");
        // Use a very lower thread
        while (true) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"ps", "aux"});
                InputStream is = process.getInputStream();
                InputStreamReader reader = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(reader);
                List<String> lines = new ArrayList<String>();
                String line = null;
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split("\\s+");
                    // The second should be process Id
                    if (tokens[1].equals(processId)) {
                        lines.add(line);
                    }
                }
                br.close();
                reader.close();
                is.close();
                // There should be at least one process, which is this Java running process
                if (lines.size() == 0) {
                    sendEmail();
                    break;
                }
                // Sleep for five minutes
                Thread.sleep(5 * 60 * 1000);
            }
            catch(Exception e) {
                e.printStackTrace();
                break; // Nothing to be done.
            }
        }
    }
    
    private void sendEmail() throws Exception {
        String from = "guanmingwu@yahoo.com";
        String host = "localhost";
        Properties properties = System.getProperties();
        properties.setProperty("mail.smpt.host", host);
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        message.setText(processName + " has finsihed!");
        message.setSubject(processName + " with id " + processId + " is Done!");
        message.addRecipients(Message.RecipientType.TO,
                              email);
        Transport.send(message);
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
    
    
    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }
    
    @Test
    public void test() {
        String line = "gwu      21966  0.0  0.0  20520  3492 ?        S    11:30   0:00 /usr/local/bin/perl -w add_links.pl -db test_reactome_48";
        String[] tokens = line.split("\\s+");
        for (String token : tokens)
            System.out.println(token);
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage java org.gk.scripts.ProcessMonitor processId processName email");
            System.exit(1);
        }
        ProcessMonitor monitor = new ProcessMonitor();
        monitor.setEmail(args[2]);
        monitor.setProcessName(args[1]);
        monitor.setProcessId(args[0]);
        monitor.startMonitor();
    }
    
}
