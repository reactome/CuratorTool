package org.gk.reach;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.gk.database.FrameManager;
import org.gk.database.GKDatabaseBrowser;
import org.gk.reach.model.fries.FriesObject;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;

public class ReachProcess {
    private final String errorTitle = "Paper Not Found";
    private final String errorMsg = "No paper found for id %s";
    private File paper;
    private PaperFetch paperFetch;

    public ReachProcess() {
        paperFetch = new PaperFetch();
    }

    private void setPaper(File paper) {
        this.paper = paper;
    }

    public File getPaper() {
        return paper;
    }

    /**
     * Constructor that takes the parent component to display input field and error message.
     *
     * @param paper
     * @param parentCompenent
     */
    public void promptForPaperId(Component parentCompenent) {
        if (parentCompenent == null)
            return;

        JFrame frame = new JFrame("Submit PMC/PMID");
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        GKApplicationUtilities.center(frame);

        JTextField textField = new JTextField(32);
        textField.addActionListener((ae) -> frame.add(new JLabel(textField.getText())));
        JLabel label = new JLabel("Enter the PMC or PMID of the paper to process:");
        label.setLabelFor(textField);
        JPanel idPanel = new JPanel(new FlowLayout());
        idPanel.add(label);
        idPanel.add(textField);
        frame.add(idPanel, BorderLayout.CENTER);

        Worker worker = new Worker();
        JButton submit = new JButton("Submit");
        submit.addActionListener((ae) -> {
            if (textField.getText().length() == 0) {
                ReachResultTableFrame reachResultTableFrame = new ReachResultTableFrame();
                try {
                    reachResultTableFrame.setTableData(new ArrayList<FriesObject>());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                worker.setPaperIds(textField.getText());
                worker.execute();
            }
            frame.dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener((ae) -> frame.dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(submit);
        buttonPanel.add(cancel);
        frame.add(buttonPanel, BorderLayout.PAGE_END);
        frame.setVisible(true);

        // TODO --
        // Prompt user for PMID/PMCID.
        // Download paper based on this id (in format REACH can process).
        // setPaper(paper);
        // Process paper with REACH.
        // Build table with results.

        // Error message skeleton.
        //	    String id = "";
        //		if (friesObjects.size() == 0) {
        //		    JOptionPane.showMessageDialog(parentCompenent,
        //                                          String.format(errorMsg, id),
        //                                          errorTitle,
        //                                          JOptionPane.ERROR_MESSAGE);
        //		    return;
        //    }
    }

    class Worker extends SwingWorker<Void, Void> {
        ReachSearch reachSearch;
        private String paperIds;
        private List<FriesObject> friesObjects;
        PaperFetch paperFetch;

        public Worker() {
            reachSearch = new ReachSearch();
            friesObjects = new ArrayList<FriesObject>();
            paperFetch = new PaperFetch();
        }

        public void setPaperIds(String paperIds) {
            this.paperIds = paperIds;
        }

        class ReachCaller implements Callable<FriesObject> {
            private ReachCall reachCall;
            private Path paper;

            public ReachCaller(Path paper) {
                reachCall = new ReachCall();
                this.paper = paper;
            }

            @Override
            public FriesObject call() throws Exception {
                return reachCall.callReachAPI(paper);
            }
        }

        @Override
        public Void doInBackground() throws Exception {
            List<Future<FriesObject>> futures = new ArrayList<Future<FriesObject>>();
            List<Path> papers = new ArrayList<Path>();

            Set<String> splitPaperIds = new HashSet<String>(Arrays.asList(paperIds.split("[, ]")));
            String pmcid = null;
            for (String paperId : splitPaperIds) {
                papers.add(paperFetch.fetchPaper(paperId));
            }

            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(papers.size());

            JFrame frame = new JFrame("Submit PMC/PMID");
            frame.setSize(300, 200);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            GKApplicationUtilities.center(frame);

            JPanel progressPane = new JPanel(new BorderLayout(10, 10));
            progressPane.setSize(300, 200);
            JButton cancel = new JButton("Cancel");
            progressPane.add(cancel, BorderLayout.SOUTH);
            cancel.addActionListener(event -> {
                cancel(true);
                executor.shutdownNow();
                frame.dispose();
            });
            frame.add(progressPane);
            frame.setVisible(true);

            JLabel label = new JLabel("Fetching papers from NCBI...");
            progressPane.add(label, BorderLayout.CENTER);

            String paperTitles = new String();
            for (Path paper : papers) {
                ReachCaller reachCall = new ReachCaller(paper);
                Future<FriesObject> result = executor.submit(reachCall);
                futures.add(result);
                paperTitles = paperTitles.concat(paper.toString());
            }

            JLabel paperLabel = new JLabel();
            paperLabel.setText("Submitted: " + paperTitles);
            progressPane.add(paperLabel, BorderLayout.NORTH);
            progressPane.validate();
            progressPane.repaint();

            FriesObject friesObject = null;
            for (Future<FriesObject> future : futures) {
                label.setText("Awaiting results from REACH server...");
                friesObject = future.get();
                if (friesObject != null)
                    friesObjects.add(future.get());
            }

            executor.shutdown();
            return null;
        }

        @Override
        public void done() {
            try {
                if (friesObjects.size() > 0)
                    reachSearch.buildTable(friesObjects);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
