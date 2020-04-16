package org.gk.reach;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.reach.model.fries.FriesObject;
import org.gk.reach.model.graphql.GraphQLObject;
import org.gk.schema.InvalidAttributeException;
import org.gk.util.BrowserLauncher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ReachUtils {
    
    public static String extractId(GKInstance instance) throws InvalidAttributeException, Exception {
        if (instance == null)
            return null;
        GKInstance referenceEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.referenceEntity);
        if (referenceEntity == null)
            return null;
        String identifier = (String) referenceEntity.getAttributeValue(ReactomeJavaConstants.identifier);
        return identifier;
    }
    
    public static void doTablePopup(MouseEvent e,
                                    JTable table,
                                    Set<Integer> supportedCols) {
        if (!e.isPopupTrigger())
            return;
        // For id row only
        int col = table.columnAtPoint(e.getPoint());
        col = table.convertColumnIndexToModel(col);
        if (!supportedCols.contains(col))
            return;
        int row = table.rowAtPoint(e.getPoint());
        row = table.convertRowIndexToModel(row);
        String value = (String) table.getModel().getValueAt(row, col);
        if (value == null)
            return;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem("Browse Entry");
        menuItem.addActionListener(event -> {
            String url = ReachConstants.getURL(value);
            if (url == null) {
                JOptionPane.showMessageDialog(table,
                                              "Cannot find a URL for this type of id.",
                                              "No URL",
                                              JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                BrowserLauncher.displayURL(url, table);
            }
            catch(Exception ex) {
                JOptionPane.showMessageDialog(table,
                                              "Cannot open the page for " + value + ".",
                                              "Error in Opening",
                                              JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
        popup.add(menuItem);
        popup.show(table, e.getX(), e.getY());
    }

    
    public static List<ReachResultTableRowData> getDisplayedData(JTable table,
                                                                 ReachTableModel model) {
        List<ReachResultTableRowData> rtn = new ArrayList<>();
        List<ReachResultTableRowData> data = model.getTableData();
        for (int i = 0; i < table.getRowCount(); i++) {
            int modelRowIndex = table.convertRowIndexToModel(i);
            rtn.add(data.get(modelRowIndex));
        }
        return rtn;
    }

    public static GraphQLObject readJsonTextGraphQL(String inputJson) throws IOException{
        if (inputJson == null || inputJson.length() == 0)
            return null;
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(inputJson, GraphQLObject.class);
    }
    
    public static GraphQLObject readFileGraphQL(String fileName) throws IOException {
        if (fileName == null || fileName.length() == 0)
            return null;
        String text = new String(Files.readAllBytes(Paths.get(fileName)), StandardCharsets.UTF_8);
        return readJsonTextGraphQL(text);
    }

    public static FriesObject readJsonText(String inputJson) throws IOException{
        return readJsonText(inputJson, FriesObject.class);
    }

    public static <T> T readJsonText(String inputJson, Class<T> cls) throws IOException{
        if (inputJson == null || inputJson.length() == 0 || cls == null)
            return null;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(inputJson, cls);
    }

    public static FriesObject readFile(String fileName) throws IOException {
        if (fileName == null || fileName.length() == 0)
            return null;
        String text = new String(Files.readAllBytes(Paths.get(fileName)), StandardCharsets.UTF_8);
        return readJsonText(text);
    }

}
