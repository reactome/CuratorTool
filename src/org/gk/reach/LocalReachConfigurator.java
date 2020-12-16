package org.gk.reach;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CancellationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.gk.util.DialogControlPane;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;

@SuppressWarnings("serial")
public class LocalReachConfigurator {
    // Property keys
    protected final static String ROOT_PROP_KEY = "reachRootDir";
    protected final static String JAR_PROP_KEY = "reachJarDir";
    private JFrame parentFrame;
    private Properties properties;
    private String reachJarURL;
    
    public LocalReachConfigurator() {
        this(null, new Properties());
    }

    public LocalReachConfigurator(JFrame parentFrame, Properties properties) {
        this.parentFrame = parentFrame;
        this.properties = properties;
    }
    
    public void setReachJarURL(String url) {
        this.reachJarURL = url;
    }
    
    public Properties getReachConfig() {
        // Make sure both properties have been set
        if (properties.getProperty(ROOT_PROP_KEY) != null && 
            properties.getProperty(JAR_PROP_KEY) != null)
            return properties;
        // Need to set up the proprties
        ConfigurationDialog dialog = new ConfigurationDialog(parentFrame);
        dialog.setVisible(true);
        if (!dialog.isOKClicked)
            return null;
        String text = dialog.rootTF.getText().trim();
        properties.setProperty(ROOT_PROP_KEY, text);
        text = dialog.jarTF.getText().trim();
        properties.setProperty(JAR_PROP_KEY, text);
        return properties;
    }

    private class ReachListener implements ActionListener {
        protected Component parent;
        protected JTextField textField;
        protected JFileChooser fileChooser;

        public ReachListener(Component parent, JTextField textField, JFileChooser fileChooser) {
            this.parent = parent;
            this.textField = textField;
            this.fileChooser = fileChooser;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            int reply = fileChooser.showOpenDialog(parent);
            if (reply != JFileChooser.APPROVE_OPTION)
                return;

            String path = fileChooser.getSelectedFile().getAbsolutePath();
            if (path == null)
                return;
            textField.setText(path);
        }
    }

    private JTextArea createDescription(String desc, Color background) {
        JTextArea textArea = new JTextArea(desc);
        Font font = textArea.getFont();
        textArea.setFont(font.deriveFont(Font.ITALIC, font.getSize() - 1.0f));
        textArea.setBackground(background);
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setColumns(30);

        return textArea;
    }


    private class ReachJarDownloader extends SwingWorker<String, Integer> {
        private String dir;
        private ProgressPane progressPane;
        private int jarSize;

        public ReachJarDownloader(String dir, ProgressPane progressPane) {
            this.dir = dir;
            this.progressPane = progressPane;
            progressPane.setText(" ");
        }

        @Override
        protected String doInBackground() throws Exception {
            String url = reachJarURL;
            URL urlObj = new URL(url);

            URLConnection connection = urlObj.openConnection();
            jarSize = connection.getContentLength();
            boolean isIndeterminate = false;
            if (jarSize == -1) {
                isIndeterminate = true;
                progressPane.setText("It may take minutes. Please wait...");
            }
            else {
                isIndeterminate = false;
                progressPane.setMaximum(jarSize);
            }
            progressPane.setIndeterminate(isIndeterminate);
            File jarFile = new File(dir, "reach.jar");

            int bufferSize = 1000 * 1024;  // Assign 1000k for buffer
            int totalDownloaded = 0;
            try (InputStream is = urlObj.openStream();
                 BufferedInputStream bis = new BufferedInputStream(is, bufferSize);
                 FileOutputStream fos = new FileOutputStream(jarFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] content = new byte[bufferSize];
                int read = bis.read(content, 0, bufferSize);
                while (read > 0 && !isCancelled()) {
                    bos.write(content, 0, read);
                    read = bis.read(content, 0, bufferSize);

                    if (isIndeterminate)
                        continue; // There is no need to update the process

                    totalDownloaded += read;
                    publish(totalDownloaded);
                }
            }

            return jarFile.getAbsolutePath();
        }

        @Override
        protected void process(List<Integer> bytesRead) {
            int latest = bytesRead.get(bytesRead.size() - 1);
            progressPane.setValue(latest);
            DecimalFormat df = new DecimalFormat("#.0");

            String current = df.format(latest / 1e6) + "MB";
            String max = df.format(jarSize / 1e6) + "MB";
            String percent = df.format(100 * ((double) latest / jarSize)) + "%";
            progressPane.setText(current + " / " + max + " (" + percent + ")");
        }
    }
    
    private class ConfigurationDialog extends JDialog {
        private boolean isOKClicked;
        private JTextField rootTF;
        private JTextField jarTF;
        
        public ConfigurationDialog(JFrame owner) {
            super(owner);
            init();
        }
        
        private void init() {
            // Content pane.
            JPanel contentPane = new JPanel();
            contentPane.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridy = 0;
            getContentPane().add(contentPane, BorderLayout.CENTER);

            // Control pane.
            DialogControlPane controlPane = new DialogControlPane();
            controlPane.setBorder(BorderFactory.createEtchedBorder());
            controlPane.getOKBtn().addActionListener(e -> {
                if (rootTF.getText().trim().length() == 0 || jarTF.getText().trim().length() == 0) {
                    JOptionPane.showMessageDialog(this,
                                                  "Please select a directory and JAR file for REACH.",
                                                  "REACH Setup Error",
                                                  JOptionPane.ERROR_MESSAGE);
                    return;
                }

                isOKClicked = true;
                dispose();
            });
            controlPane.getCancelBtn().addActionListener(e -> dispose());
            getContentPane().add(controlPane, BorderLayout.SOUTH);

            setLocationRelativeTo(getOwner());
            setModal(true);
            setTitle("REACH Configuration");

            // REACH Root.
            JPanel reachRootPane = createReachRootPanel();
            constraints.gridy += 1;
            contentPane.add(reachRootPane, constraints);

            // REACH JAR.
            JPanel reachJarPane = createReachJarPanel();
            constraints.gridy += 1;
            constraints.insets = new Insets(20, 0, 0, 0);
            contentPane.add(reachJarPane, constraints);
            setSize(500, 400);
        }
        
        private String showDownloadPanel() throws Exception {
            String dir = rootTF.getText().trim();
            if (dir.length() == 0)
                dir = System.getProperty("user.home");
            if (dir == null)
                return null;
            ProgressPane progressPane = new ProgressPane();
            ReachJarDownloader downloader = new ReachJarDownloader(dir, progressPane);
            progressPane.enableCancelAction(e -> downloader.cancel(true));
            progressPane.setTitle("Downloading REACH JAR File");
            setGlassPane(progressPane);
            getGlassPane().setVisible(true);

            downloader.execute();
            String jarFile = null;
            try {
                jarFile = downloader.get();
            } catch (CancellationException e) {
                e.printStackTrace();
            }finally {
                getGlassPane().setVisible(false);
            }
            return jarFile;
        }
        
        private JPanel createReachRootPanel() {
            JPanel rootPane = new JPanel();
            rootPane.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridy = 0;

            // Select REACH directory.
            JLabel dirLabel = GKApplicationUtilities.createTitleLabel("Select directory for REACH process:");
            constraints.gridy += 1;
            rootPane.add(dirLabel, constraints);

            rootTF = new JTextField();
            rootTF.setColumns(30);
            rootTF.setEditable(false);
            String reachRoot = properties.getProperty(ROOT_PROP_KEY);
            if (reachRoot != null)
                rootTF.setText(reachRoot);
            constraints.gridy += 1;
            rootPane.add(rootTF, constraints);

            String dirText = "*: This directory will contain the papers and output of the REACH process.";
            JTextArea dirTextArea = createDescription(dirText, rootPane.getBackground());
            constraints.gridy += 1;
            rootPane.add(dirTextArea, constraints);

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setDialogTitle("Select REACH Directory");

            JButton dirBtn = new JButton("Select");
            dirBtn.addActionListener(new ReachListener(this, rootTF, fileChooser));
            constraints.gridy += 1;
            rootPane.add(dirBtn, constraints);

            return rootPane;
        }

        private JPanel createReachJarPanel() {
            JPanel jarPane = new JPanel();
            jarPane.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridy = 0;

            // Select REACH JAR file.
            JLabel jarLabel = GKApplicationUtilities.createTitleLabel("Select REACH JAR file:");
            constraints.gridy += 1;
            jarPane.add(jarLabel, constraints);

            jarTF = new JTextField();
            jarTF.setColumns(30);
            jarTF.setEditable(false);
            String reachJar = properties.getProperty(JAR_PROP_KEY);
            if (reachJar != null)
                jarTF.setText(reachJar);
            constraints.gridy += 1;
            jarPane.add(jarTF, constraints);

            String jarText = "*: If you have already downloaded the REACH JAR file, you may select it here." +
                             " Otherwise click 'Download' to begin the download process.";
            JTextArea jarTextArea = createDescription(jarText, jarPane.getBackground());
            constraints.gridy += 1;
            jarPane.add(jarTextArea, constraints);

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setDialogTitle("Select REACH JAR File:");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("REACH JAR", "jar");
            fileChooser.setFileFilter(filter);

            JPanel btnPane = new JPanel();
            btnPane.setLayout(new FlowLayout());
            constraints.gridy += 1;
            jarPane.add(btnPane, constraints);

            JButton jarSelectBtn = new JButton("Select");
            jarSelectBtn.addActionListener(new ReachListener(this, jarTF, fileChooser));
            constraints.gridy += 1;
            btnPane.add(jarSelectBtn);

            JButton jarDownloadBtn = new JButton("Download");
            jarDownloadBtn.addActionListener(e -> {
                Thread t = new Thread() {
                    public void run() {
                        try {
                            String jar = showDownloadPanel();
                            jarTF.setText(jar == null ? "" : jar);
                        } catch (Exception e) {
                            e.printStackTrace();
                            JOptionPane.showMessageDialog(parentFrame,
                                                          "Error in downloading the Reach jar: " + e.getMessage(),
                                                          "Error in Downloading Reach", 
                                                          JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                t.start();
            });
            constraints.gridx += 1;
            btnPane.add(jarDownloadBtn);

            return jarPane;
        }
    }

}
