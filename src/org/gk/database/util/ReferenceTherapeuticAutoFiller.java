package org.gk.database.util;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReferenceTherapeuticAutoFiller extends AbstractAttributeAutoFiller {
	private final String DB_NAME = "Guide to Pharmacology - Ligands";
    private final String BASE_URL = "https://www.guidetopharmacology.org/services/";
    private final String LIGAND_URL = BASE_URL + "ligands/"; // ID should be after

    public ReferenceTherapeuticAutoFiller() {
        
    }

    @Override
    protected Object getRequiredAttribute(GKInstance instance) throws Exception {
        return instance.getAttributeValue(ReactomeJavaConstants.identifier);
    }

    @Override
    protected String getConfirmationMessage() {
        return "Do you want the tool to fetch information from the " + DB_NAME + " database?";
    }

    @Override
    public void process(GKInstance instance, Component parentComp) throws Exception {
        String identifier = (String) getRequiredAttribute(instance);
        if (identifier == null)
            return; // Do nothing if we don't identifier
        // Just perform a little match test
        if (!identifier.matches("^\\d+$")) {
            JOptionPane.showMessageDialog(parentComp,
                                          "Wrong Drug ID",
                                          "The entered id is wrong. It should be a number.",
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Add ReferenceDatabase
        GKInstance referenceDb = getReferenceDatabasae(DB_NAME);
        if (referenceDb == null) {
            JOptionPane.showMessageDialog(parentComp,
                                          "Error in Fetching",
                                          "Cannot get the ReferenceDatabase instance for " + DB_NAME,
                                          JOptionPane.ERROR_MESSAGE);
            return;
        }
        instance.setAttributeValue(ReactomeJavaConstants.referenceDatabase, referenceDb);
        String url = LIGAND_URL + identifier;
        String text = download(url);
        // The text should have the following information:
//        {
//            "ligandId" : 5962,
//            "name" : "dovitinib",
//            "abbreviation" : "",
//            "inn" : "dovitinib",
//            "type" : "Synthetic organic",
//            "species" : null,
//            "radioactive" : false,
//            "labelled" : false,
//            "approved" : false,
//            "withdrawn" : false,
//            "approvalSource" : "",
//            "subunitIds" : [ ],
//            "complexIds" : [ ],
//            "prodrugIds" : [ ],
//            "activeDrugIds" : [ ]
//          }
        JSONObject jsonObj = new JSONObject(text);
        parseStringValue(instance, jsonObj, ReactomeJavaConstants.name);
        parseStringValue(instance, jsonObj, ReactomeJavaConstants.abbreviation);
        parseStringValue(instance, jsonObj, ReactomeJavaConstants.inn);
        parseStringValue(instance, jsonObj, ReactomeJavaConstants.type);
        boolean approved = jsonObj.getBoolean(ReactomeJavaConstants.approved);       
        instance.setAttributeValue(ReactomeJavaConstants.approved, approved);
        if (approved) {
            parseStringValue(instance, jsonObj, ReactomeJavaConstants.approvalSource);
        }
        collectSynonyms(instance, identifier);
    }
    
    private void collectSynonyms(GKInstance instance, String identifier) throws Exception {
        String url = LIGAND_URL + identifier + "/synonyms";
        String text = download(url);
        JSONArray array = new JSONArray(text);
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String name = obj.getString("name");
            instance.addAttributeValue(ReactomeJavaConstants.name, name);
        }
    }

    private void parseStringValue(GKInstance instance, 
                                  JSONObject jsonObj,
                                  String propName) throws InvalidAttributeException, InvalidAttributeValueException {
        String name = jsonObj.getString(propName);
        if (name != null && name.length() > 0)
            instance.setAttributeValue(propName, name);
    }
    
    private String download(String urlAddress) throws Exception {
        URL url = new URL(urlAddress);
        InputStream is = url.openStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = br.readLine()) != null)
            builder.append(line).append("\n");
        br.close();
        isr.close();
        is.close();
        return builder.toString();
    }
    
}
