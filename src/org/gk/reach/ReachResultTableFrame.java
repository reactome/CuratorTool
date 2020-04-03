package org.gk.reach;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.HyperlinkEvent;

import org.gk.model.GKInstance;
import org.gk.model.Person;
import org.gk.model.Reference;
import org.gk.reach.model.fries.Argument;
import org.gk.reach.model.fries.Event;
import org.gk.reach.model.fries.FrameObject;
import org.gk.reach.model.fries.FriesObject;
import org.gk.reach.model.graphql.GraphQLObject;
import org.gk.util.GKApplicationUtilities;
import org.gk.util.ProgressPane;

@SuppressWarnings("serial")
public class ReachResultTableFrame extends JFrame {
    
    private final String noResultsMsg = "No results found.";
    private final String noResultsTitle = "No Results";
    private final String noRowsSelectedErrorMsg = "There are no rows that are currently selected.";
    private final String noRowsSelectedErrorTitle = "No Rows Selected";
    private final String progressMessage = "Fetching element data...";
    private final String progressTemplate = "<html>%s<br/>(%d / %d) %s</html>";
    private final String title = "Reach: ";
    // Some user interfaces
    private JTextPane evidencePane;
    private JTable eventTable;

    public ReachResultTableFrame() {
        init();
    }

    private void init() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle("Reactome Reach NLP");

        ReachTableModel tableModel = new ReachTableModel();
        eventTable = new JTable(tableModel);
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Table sorter.
        eventTable.setAutoCreateRowSorter(true);

        // Configure results area.
        evidencePane = new JTextPane();
        evidencePane.setEditable(false);
        evidencePane.setContentType("text/html");
        evidencePane.setText("Select a row to populate results.");

        JPanel rowControlPane = new RowFilterPane(eventTable);
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(rowControlPane);
        getContentPane().add(container, BorderLayout.NORTH);

        JPanel evidenceContainer = new JPanel();
        evidenceContainer.setLayout(new BorderLayout());
        JPanel northPane = new JPanel();
        JLabel evidenceLabel = new JLabel("Evidence");
        Font font = evidenceLabel.getFont();
        evidenceLabel.setFont(font.deriveFont(Font.BOLD, font.getSize() + 2));
        northPane.add(evidenceLabel);
        evidenceContainer.add(northPane, BorderLayout.NORTH);
        evidenceContainer.add(new JScrollPane(evidencePane), BorderLayout.CENTER);

        // Split Pane to hold the table and results.
        JSplitPane tableAndResults = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                                    new JScrollPane(eventTable),
                                                    evidenceContainer);
        tableAndResults.setOneTouchExpandable(true);
        tableAndResults.setDividerLocation(475);
        getContentPane().add(tableAndResults, BorderLayout.CENTER);

        // Accept and Cancel buttons
        createSouthPane();

        installTableListeners(eventTable, evidencePane);

        setSize(1000, 800);
        GKApplicationUtilities.center(this);
    }

    private void createSouthPane() {
        JPanel botPanel = new JPanel();
        getContentPane().add(botPanel, BorderLayout.SOUTH);
        JButton acceptBut = new JButton("Accept");
        JButton openBut = new JButton("Open");
        openBut.setToolTipText("Open pre-generated Reach output files");
        JButton cancelBut = new JButton("Cancel");
        
        JButton importBtn = new JButton("Import");
        importBtn.setToolTipText("Import saved table data");
        importBtn.addActionListener(e -> {
            new ReachTablePersister().importTableData(eventTable);
        });
        JButton exportBtn = new JButton("Export");
        exportBtn.addActionListener(e -> {
            new ReachTablePersister().exportTableData(eventTable);
        });
        exportBtn.setToolTipText("Export table data");
        JButton showConnectionBtn = new JButton("Show Connection");
        showConnectionBtn.addActionListener(e -> showConnections());
        showConnectionBtn.setToolTipText("Check connections for participants");
        
        installButtonListeners(acceptBut, openBut, cancelBut, eventTable);
        botPanel.add(acceptBut);
        botPanel.add(openBut);
        botPanel.add(importBtn);
        botPanel.add(exportBtn);
        botPanel.add(showConnectionBtn);
        botPanel.add(cancelBut);
        // Turn these buttons off for the time being
        acceptBut.setVisible(false);
        cancelBut.setText("Close");
    }
    
    private void showConnections() {
        ConnectionTablePane panel = new ConnectionTablePane();
        panel.setTableData(eventTable);
        panel.showInDialog(this);
    }

    /**
     * Use the instanceSet to fetch REACH results and add them to the table.
     *
     * @param instanceSet
     * @return dataObjects, list of Fries objects.
     * @throws Exception
     */
    public List<FriesObject> searchReach(List<GKInstance> instances) throws Exception {
        // Configure progress bar for REACH queries.
        ProgressPane progressPane = new ProgressPane();
        setGlassPane(progressPane);
        getGlassPane().setVisible(true);
        setVisible(true);
        progressPane.setMinimum(0);
        progressPane.enableCancelAction(event -> dispose());
        List<FriesObject> dataObjects = new ArrayList<FriesObject>();
        setTitle(title + instances);
        progressPane.setMaximum(instances.size());
        int progress = 1;
        // Update progress pane.
        for (GKInstance instance : instances) {
            if (progressPane.isCancelled())
                return null;
            progressPane.setValue(progress);
            progressPane.setText(String.format(progressTemplate,
                                               progressMessage,
                                               progress++,
                                               instances.size(),
                                               instance.getDisplayName()));
            List<FriesObject> friesObjects = getDataFromGraphQL(ReachUtils.extractId(instance));
            if (friesObjects.size() == 0)
                continue;
            friesObjects.forEach(dataObjects::add);
        }
        // If no results found.
        if (dataObjects.size() == 0) {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            JOptionPane.showMessageDialog(this, noResultsMsg, noResultsTitle, JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return dataObjects;
    }

    /**
     * Convert a given UniProt id to FriesObjects by querying GraphiQL instance.
     *
     * @param id
     * @return friesObjects
     * @throws IOException
     */
    private List<FriesObject> getDataFromGraphQL(String id) throws IOException {
        // Create REACH queries for an element.
        String template = new String(Files.readAllBytes(Paths.get(ReachConstants.GRAPHQL_SEARCH_TEMPLATE)));
        String graphqlInput = String.format(template, id);
        // Return a list of JSON responses from REACH.
        ReachCall reachCall = new ReachCall();
        String jsonOutput = reachCall.callGraphQL(graphqlInput);

        //Use example graphql file as json
        //      String graphQLOutputExample = "examples/reachOutputExample_full.json";
        //      String jsonOutput = new String(Files.readAllBytes(Paths.get(graphQLOutputExample)), StandardCharsets.UTF_8);

        GraphQLObject graphQLObject = ReachUtils.readJsonTextGraphQL(jsonOutput);
        // Create Fries object for use in table.
        GraphQLToFriesConverter graphQLToFriesConverter = new GraphQLToFriesConverter();
        List<FriesObject> friesObjects = graphQLToFriesConverter.convertGraphQLObject(graphQLObject);
        return friesObjects;

        //Create Fries object from fries file
        //        String friesOutput = "examples/PMC4306850.fries.json";
        //        String jsonOutput = new String(Files.readAllBytes(Paths.get(friesOutput)), StandardCharsets.UTF_8);
        //        List<FriesObject> friesObjects = new ArrayList<FriesObject>();
        //        friesObjects.add(ReachUtils.readJsonText(jsonOutput));
        //        return friesObjects;

    }

    /**
     * Process a given paper with REACH and add results to table.
     *
     * @param paper
     * @return dataObjects, list of Fries objects
     * @throws Exception
     */
    public List<FriesObject> processWithReach(File paper) throws Exception {
        // TODO check that paper is valid.
        if (paper == null)
            return null;
        List<FriesObject> dataObjects = new ArrayList<FriesObject>();
        dataObjects.add(getDataFromReachInstance(paper));
        if (dataObjects.size() == 0) {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            JOptionPane.showMessageDialog(this, noResultsMsg, noResultsTitle, JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return dataObjects;
    }

    /**
     * Convert a given file to a FriesObject by querying a running REACH instance.
     *
     * @param string
     * @return FriesObject
     * @throws IOException
     */
    private FriesObject getDataFromReachInstance(File paper) throws IOException {
        /* TODO Uncomment these lines to query REACH instance.
	    // TODO check that paper is valid.
	    if (paper == null)
	        return;
        // Return a list of JSON responses from REACH.
        ReachCall reachCall = new ReachCall();
        String jsonOutput = reachCall.callReachAPI(paper);
         */

        // REACH output file path for use in debugging table (to prevent having to wait for a REACH query to complete).
        String reachOutputExample = "examples/PMC4306850.fries.json";
        String jsonOutput = new String(Files.readAllBytes(Paths.get(reachOutputExample)), StandardCharsets.UTF_8);

        // Create Fries object for use in table.
        return ReachUtils.readJsonText(jsonOutput);
    }

    /**
     * Add FRIES data to table.
     *
     * @param friesObjects
     * @throws Exception
     */
    void setTableData(List<FriesObject> friesObjects) {
        ReachTableModel tableModel = (ReachTableModel) eventTable.getModel();
        tableModel.setReachData(friesObjects);
    }

    /**
     * Install table row selection listeners.
     *
     * @param eventTable
     * @param evidencePane
     */
    private void installTableListeners(JTable eventTable, JTextPane evidencePane){
        // Row selection listener.
        eventTable.getSelectionModel().addListSelectionListener(event -> handleEventTableSelection());
        eventTable.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                doTablePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                doTablePopup(e);
            }
        });
        evidencePane.addHyperlinkListener(event -> {
            if(event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if(Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(event.getURL().toURI());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void doTablePopup(MouseEvent e) {
        Set<Integer> supportedCols = Arrays.asList(1, 3).stream().collect(Collectors.toSet());
        ReachUtils.doTablePopup(e, eventTable, supportedCols);
    }

    private void handleEventTableSelection() {
        if (eventTable.getSelectedRow() == -1)
            return;
        // Display data in results area.
        int rowIndex = eventTable.convertRowIndexToModel(eventTable.getSelectedRow());
        ReachTableModel model = (ReachTableModel) eventTable.getModel();
        ReachResultTableRowData reachResultTableRowData = model.getReachResultTableRowData(rowIndex);
        // Evidence
        if (reachResultTableRowData == null) {
            evidencePane.setText("No results found.");
            return;
        }
        List<Event> events = reachResultTableRowData.getEvents();
        if (events == null || events.size() == 0) {
            evidencePane.setText("No results found.");
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        List<Reference> references = reachResultTableRowData.getReferences();

        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            String eventString = null;
            if (event.getVerboseText() != null)
                eventString = event.getVerboseText();
            else if (event.getSentence() != null)
                eventString = event.getSentence().getText();
            else
                eventString = event.getText();
            if (eventString == null)
                continue;
            // Add the paper data of the referenced row.
            stringBuilder.append("<ul><li>");
            appendEventString(stringBuilder, event, eventString);
            stringBuilder.append("<ul><li>");
            if (!references.isEmpty())
                stringBuilder.append(getReference(references.get(i)));
            stringBuilder.append("</li></ul>");
            stringBuilder.append("</li></ul>");
        }
        stringBuilder.append("</ul>");
        evidencePane.setText(stringBuilder.toString());
        // Stay at the first location
        evidencePane.scrollRectToVisible(new Rectangle(10, 10));
        evidencePane.setCaretPosition(0); // Force to start from 0
    }

    private void appendEventString(StringBuilder builder, Event event, String eventString) {
        String text = event.getText();
        // This should be the smallest text
        int offset = eventString.indexOf(text);
        if (offset < 0) {
            builder.append(eventString);
            return;
        }
        List<Integer> positions = new ArrayList<>();
        for (Argument arg : event.getArguments()) {
            FrameObject obj = arg.getArg();
            Integer position = obj.getStartPos().getOffset() - event.getStartPos().getOffset() + offset;
            positions.add(position);
            positions.add(position + obj.getEndPos().getOffset() - obj.getStartPos().getOffset());
        }
        Collections.sort(positions);
        Integer current = null;
        if (positions.size() > 0) {
            current = positions.get(0);
            positions.remove(0);
        }
        for (int i = 0; i < eventString.length(); i++) {
            if (current != null && i == current) {
                if (positions.size() % 2 == 1) {
                    builder.append("<b><font color=\"red\">"); // start the tag
                }
                else {
                    builder.append("</font></b>"); // Close the tags
                }
                if (positions.size() > 0) {
                    current = positions.get(0);
                    positions.remove(0);
                }
                else
                    current = null;
            }
            builder.append(eventString.charAt(i));
        }
    }
    
    private String getReference(Reference reference) {
        StringBuilder stringBuilder = new StringBuilder();
        
        String pmcid = reference.getPmcid();
        if (pmcid != null) {
            if (pmcid.endsWith(";"))
                pmcid = pmcid.substring(0, pmcid.length() - 1);
            int index = pmcid.indexOf(":");
            if (index >= 0)
                pmcid = pmcid.substring(index + 1).trim();
        }
        
        // title
        if (reference.getTitle() != null) {
            if (pmcid != null) {
                stringBuilder.append("<a href=\"https://www.ncbi.nlm.nih.gov/pmc/articles/");
                stringBuilder.append(pmcid);
                stringBuilder.append("/\">");
            }
            stringBuilder.append("<b>");
            stringBuilder.append(reference.getTitle());
            stringBuilder.append("</b>");
            if (pmcid != null)
                stringBuilder.append("</a>");
            stringBuilder.append("<br>");
        }

        // authors
        if (reference.getAuthors() != null &&
                reference.getAuthors().size() > 0) {
            List<Person> authors = reference.getAuthors();
            stringBuilder.append(authors.get(0).getFirstName());
            stringBuilder.append(" ");
            stringBuilder.append(authors.get(0).getLastName());
            for (Person author : authors.subList(1, authors.size())) {
                stringBuilder.append(", ");
                stringBuilder.append(author.getFirstName());
                stringBuilder.append(" ");
                stringBuilder.append(author.getLastName());
            }
            stringBuilder.append("<br>");
        }
        else if (reference.getAuthor() != null) {
            stringBuilder.append(reference.getAuthor());
            stringBuilder.append("<br>");
        }

        // journal, year
        stringBuilder.append(reference.getJournal());
        stringBuilder.append(", ");
        stringBuilder.append(reference.getYear());
        stringBuilder.append("<br>");

        // PMCID
        if (pmcid != null) {
            stringBuilder.append("<sub>");
            stringBuilder.append("PMCID: ");
            stringBuilder.append("<a href=\"https://www.ncbi.nlm.nih.gov/pmc/articles/");
            stringBuilder.append(pmcid);
            stringBuilder.append("/\">");
            stringBuilder.append(pmcid);
            stringBuilder.append("</a>");
        }

        // PMID
        stringBuilder.append(" ");
        stringBuilder.append("PMID: ");
        stringBuilder.append("<a href=\"https://pubmed.ncbi.nlm.nih.gov/");
        stringBuilder.append(reference.getPmid());
        stringBuilder.append("/\">");
        stringBuilder.append(reference.getPmid());
        stringBuilder.append("</a>");
        stringBuilder.append("</sub>");
        return stringBuilder.toString();
    }

    /**
     * Install listeners for the "OK" and "CANCEL" buttons.
     *
     * @param acceptBut
     * @param cancelBut
     * @param table
     */
    private void installButtonListeners(JButton acceptBut, JButton openBut, JButton cancelBut, JTable table) {
        cancelBut.addActionListener(event -> dispose());
        acceptBut.addActionListener(event -> {
            List<ReachResultTableRowData> acceptedRows = new ArrayList<ReachResultTableRowData>();
            // Count number of "Accepted" rows.
            for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
                // If the row is "Accepted".
                if (table.getValueAt(rowIndex, table.getColumn(ReachConstants.ACCEPT).getModelIndex()).equals(true)) {
                    ReachTableModel model = (ReachTableModel) table.getModel();
                    ReachResultTableRowData acceptedRow = model.getReachResultTableRowData(table.convertRowIndexToModel(rowIndex));

                    // Collect "Accepted" rows into collection.
                    if (acceptedRow != null)
                        acceptedRows.add(acceptedRow);
                }
            }
            // If number of "Accepted" rows is 0, warn user and exit.
            if (acceptedRows.size() == 0) {
                JOptionPane.showMessageDialog(acceptBut,
                                              noRowsSelectedErrorMsg,
                                              noRowsSelectedErrorTitle,
                                              JOptionPane.ERROR_MESSAGE);
                return;
            }
            getData(acceptedRows);
        });
        openBut.addActionListener(event -> {
            openPreProcessedFiles();
        });
    }

    private void openPreProcessedFiles() {
        ReachTablePersister persister = new ReachTablePersister();
        persister.openPreProcessedFiles(this);
    }

    private void getData(List<ReachResultTableRowData> acceptedRows){
        // TODO Do something with the accepted rows collection.
        // This is a temporary message to display what will be processed.
        StringBuilder message = new StringBuilder();
        message.append("<html>");
        for (ReachResultTableRowData acceptedRow : acceptedRows) {
            message.append("<strong>" + acceptedRow + "</strong>");
            message.append("<ul>");
            message.append("<li><strong>participantA: </strong>" + acceptedRow.getParticipantAText() + "</li>");
            message.append("<li><strong>participantB: </strong>" + acceptedRow.getParticipantBText() + " </li>");
            message.append("<li><strong>interactionType: </strong>" + acceptedRow.getInteractionType() + " </li>");
            message.append("<li><strong>interactionSubtype: </strong>" + acceptedRow.getInteractionSubtype() + " </li>");
            message.append("<li><strong>events:</strong><ul>");
            for (Event event : acceptedRow.getEvents())
                message.append("<li>" + event + "</li>");
            message.append("</ul></li>");
            message.append("</ul>");
        }
        message.append("</html>");
        JOptionPane.showMessageDialog(this, message, "acceptedRows", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) throws Exception {
        ReachResultTableFrame frame = new ReachResultTableFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

}
