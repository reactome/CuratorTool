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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
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


public class ReachConfigurationDialog extends JDialog {
    public boolean isOKClicked = false;
    private DialogControlPane controlPane;
    private ReachProperties properties;

    public ReachConfigurationDialog(ReachResultTableFrame parent, ReachProperties properties) {
        super(parent);
        controlPane = new DialogControlPane();
        this.properties = properties;
    }

    private void setReachProperties(ReachProperties properties) {
        this.properties = properties;
    }

    public ReachProperties getReachProperties() {
        return properties;
    }

    void showDialog() {
        // Content pane.
        JPanel contentPane = new JPanel();
	    contentPane.setLayout(new GridBagLayout());
	    GridBagConstraints constraints = new GridBagConstraints();
	    constraints.anchor = GridBagConstraints.WEST;
	    constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridy = 0;
        getContentPane().add(contentPane, BorderLayout.CENTER);

        // Control pane.
        controlPane.setBorder(BorderFactory.createEtchedBorder());
        controlPane.getOKBtn().addActionListener(e -> {
            if (properties.getReachRoot() == null || properties.getReachJar() == null) {
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

    protected JPanel createReachRootPanel() {
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

        JTextField dirName = new JTextField();
        dirName.setColumns(30);
        dirName.setEditable(false);
        Path reachRoot = properties.getReachRoot();
        if (reachRoot != null)
            dirName.setText(reachRoot.toString());
        constraints.gridy += 1;
        rootPane.add(dirName, constraints);

        String dirText = "*: This directory will contain the papers and output of the REACH process.";
        JTextArea dirTextArea = createDescription(dirText, rootPane.getBackground());
        constraints.gridy += 1;
        rootPane.add(dirTextArea, constraints);

        // TODO use default directory from curator.prop file.
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select REACH Directory");

        JButton dirBtn = new JButton("Select");
        dirBtn.addActionListener(new ReachRootListener(this, dirName, fileChooser));
        constraints.gridy += 1;
        rootPane.add(dirBtn, constraints);

        return rootPane;
    }

    protected JPanel createReachJarPanel() {
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

        JTextField jarName = new JTextField();
        jarName.setColumns(30);
        jarName.setEditable(false);
        Path reachJar = properties.getReachJar();
        if (reachJar != null)
            jarName.setText(reachJar.toString());
        constraints.gridy += 1;
        jarPane.add(jarName, constraints);

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
        jarSelectBtn.addActionListener(new ReachJarListener(this, jarName, fileChooser));
        constraints.gridy += 1;
        btnPane.add(jarSelectBtn);

        Thread t = new Thread() {
            public void run() {
                try {
                    Path jar = showDownloadPanel();
                    properties.setReachJar(jar);
                    jarName.setText(jar.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        JButton jarDownloadBtn = new JButton("Download");
        jarDownloadBtn.addActionListener(e -> t.start());
        constraints.gridx += 1;
        btnPane.add(jarDownloadBtn);

        return jarPane;
    }

    private class ReachListener implements ActionListener {
        protected Component parent;
        protected JTextField textField;
        protected JFileChooser fileChooser;
        protected Path path;

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

            path = fileChooser.getSelectedFile().toPath();
            if (path == null)
                return;

            textField.setText(path.toString());
        }
    }

    private class ReachRootListener extends ReachListener {
        public ReachRootListener(Component parent, JTextField textField, JFileChooser fileChooser) {
            super(parent, textField, fileChooser);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            if (path != null)
                properties.setReachRoot(path);
        }
    }

    private class ReachJarListener extends ReachListener {
        public ReachJarListener(Component parent, JTextField textField, JFileChooser fileChooser) {
            super(parent, textField, fileChooser);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);
            if (path != null)
                properties.setReachJar(path);
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

    private Path showDownloadPanel() throws Exception {
        Path dir = properties.getReachRoot();

        if (dir == null)
            dir = Paths.get(System.getProperty("user.home"));

        if (dir == null)
            return null;

        ProgressPane progressPane = new ProgressPane();
        ReachJarDownloader downloader = new ReachJarDownloader(dir, progressPane);
        progressPane.enableCancelAction(e -> downloader.cancel(true));
        progressPane.setTitle("Downloading REACH JAR File");
        setGlassPane(progressPane);
        getGlassPane().setVisible(true);

        downloader.execute();
        Path jarFile = null;
        try {
            jarFile = downloader.get();
        } catch (CancellationException e) {
            getGlassPane().setVisible(false);
            e.printStackTrace();
        }
        getGlassPane().setVisible(false);
        return jarFile;
    }

    private class ReachJarDownloader extends SwingWorker<Path, Integer> {
        private Path dir;
        private boolean isIndeterminate;
        private ProgressPane progressPane;
        private int jarSize;

        public ReachJarDownloader(Path dir, ProgressPane progressPane) {
            this.dir = dir;
            this.progressPane = progressPane;
            progressPane.setText(" ");
            isIndeterminate = true;
        }

        @Override
        protected Path doInBackground() throws Exception {
            // TODO set correct URL for JAR file. This database file serves as
            // a placeholder to verify download and progress panel works.
            String url = "https://reactome.org/download/current/databases/gk_current.sql.gz";
            URL urlObj = new URL(url);

            URLConnection connection = urlObj.openConnection();
            jarSize = connection.getContentLength();
            if (jarSize == -1)
                progressPane.setIndeterminate(true);
            else {
                isIndeterminate = false;
                progressPane.setMaximum(jarSize);
            }

            Path jarFile = dir.resolve("reach.jar");

            int bufferSize = 1000 * 1024;  // Assign 1000k for buffer
            int totalDownloaded = 0;
            try (InputStream is = urlObj.openStream();
                    BufferedInputStream bis = new BufferedInputStream(is, bufferSize);
                    FileOutputStream fos = new FileOutputStream(jarFile.toFile());
                    BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] content = new byte[bufferSize];
                int read = bis.read(content, 0, bufferSize);
                while (read > 0 && !isCancelled()) {
                    bos.write(content, 0, read);
                    read = bis.read(content, 0, bufferSize);

                    if (isIndeterminate)
                        continue;

                    totalDownloaded += read;
                    publish(totalDownloaded);
                }
            }

            return jarFile;
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

}
