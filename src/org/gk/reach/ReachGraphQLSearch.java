package org.gk.reach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.reach.model.fries.FriesObject;
import org.gk.reach.model.graphql.GraphQLObject;
import org.gk.schema.InvalidAttributeException;
import org.gk.util.ProgressPane;

public class ReachGraphQLSearch {
    private final String progressMessage = "Fetching element data...";
    private final String progressTemplate = "<html>%s<br/>(%d/%d) %s</html>";
    private final String delimiter = ", ";

    public ReachGraphQLSearch() {
    }

	/**
	 * Filter passed instances to those with valid schema classes.
	 *
	 * @param instances
	 * @param parentCompenent
	 * @throws Exception
	 * @throws InvalidAttributeException
	 */
	private List<GKInstance> filterInstances(List<GKInstance> instances, JComponent parentCompenent)
	        throws InvalidAttributeException, Exception {
	    if (instances == null || instances.size() == 0)
	        return null;
	    List<String> validSchemaClasses = Arrays.asList(ReactomeJavaConstants.Complex,
                                                        ReactomeJavaConstants.EntitySet,
                                                        ReactomeJavaConstants.EntityWithAccessionedSequence);
		List<GKInstance> filteredInstances = new ArrayList<GKInstance>();
		for (GKInstance instance : instances) {
		    if (validSchemaClasses.stream().anyMatch(instance.getSchemClass()::isa))
		        filteredInstances.add(instance);
		}
		if (filteredInstances.size() == 0) {
		    JOptionPane.showMessageDialog(parentCompenent,
                                          "No instance is selected for " + String.join(delimiter, validSchemaClasses),
                                          "No Valid Instance Selected",
                                          JOptionPane.INFORMATION_MESSAGE);
		    return null;
		}
        return filteredInstances;
	}
	
	/**
	 * To avoid the annoying shell instance issue, we will switch to instances in the database to get identifiers.
	 * @param localInsts
	 * @return
	 * @throws Exception
	 */
	private List<GKInstance> switchToDBInstance(List<GKInstance> localInsts, JComponent parentComponent) throws Exception {
	    PersistenceAdaptor dba = PersistenceManager.getManager().getActivePersistenceAdaptor(parentComponent);
	    if (dba == null)
	        return null; // Error in connecting or cancelled
	    List<GKInstance> dbInsts = new ArrayList<>(localInsts.size());
	    for (GKInstance localInst : localInsts) {
	        GKInstance dbInst = dba.fetchInstance(localInst.getDBID());
	        if (dbInst == null)
	            continue;
	        dbInsts.add(dbInst);
	    }
	    if (dbInsts.size() == 0) {
	        JOptionPane.showMessageDialog(parentComponent,
                                          "Cannot find any instance in the database. No query will be done.",
                                          "No Instance in DB",
                                          JOptionPane.INFORMATION_MESSAGE);
            return null;
	    }
	    return dbInsts;
	}

	/**
	 * Build the REACH table based on the filtered instances.
	 * @throws Exception 
	 * @throws InvalidAttributeException 
	 */
	public void searchReach(List<GKInstance> instances, JComponent elv) throws InvalidAttributeException, Exception {
	    List<GKInstance> filteredInstances = filterInstances(instances, elv);
	    if (filteredInstances == null || filteredInstances.size() == 0)
	        return;
	    List<GKInstance> dbInstances = switchToDBInstance(filteredInstances, elv);
	    if (dbInstances == null || dbInstances.size() == 0)
	        return;
	    List<String> identifiers = extractIds(dbInstances, elv);
	    if (identifiers ==  null || identifiers.size() == 0)
	        return;
		Thread thread = new Thread() {
			public void run() {
				try {
				    List<FriesObject> dataObjects = _searchReach(identifiers, elv);
				    if (dataObjects == null || dataObjects.size() == 0)
				        return;
				    ReachResultTableFrame reachResultTableFrame = new ReachResultTableFrame();
				    reachResultTableFrame.setLocationRelativeTo(elv);
				    reachResultTableFrame.setReachData(dataObjects);
				    reachResultTableFrame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(elv,
		                                          "Error in querying Reach: " + e.getMessage(),
		                                          "Error in Query Reach",
		                                          JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		thread.start();
	}
	
    /**
     * Use the instanceSet to fetch REACH results and add them to the table.
     *
     * @param instanceSet
     * @return dataObjects, list of Fries objects.
     * @throws Exception
     */
    private List<FriesObject> _searchReach(List<String> identifiers, JComponent elv) throws Exception {
        RootPaneContainer window = (RootPaneContainer) SwingUtilities.getAncestorOfClass(RootPaneContainer.class, elv);
        // Configure progress bar for REACH queries.
        ProgressPane progressPane = new ProgressPane();
        window.setGlassPane(progressPane);
        window.getGlassPane().setVisible(true);
        progressPane.setMinimum(0);
        progressPane.enableCancelAction(event -> window.getGlassPane().setVisible(false));
        List<FriesObject> dataObjects = new ArrayList<FriesObject>();
        progressPane.setMaximum(identifiers.size());
        int progress = 1;
        // Update progress pane.
        for (String identifier : identifiers) {
            if (progressPane.isCancelled())
                return null;
            progressPane.setValue(progress);
            progressPane.setText(String.format(progressTemplate,
                                               progressMessage,
                                               progress++,
                                               identifiers.size(),
                                               identifier));
            List<FriesObject> friesObjects = fetchDataFromGraphQL(identifier);
            if (friesObjects.size() == 0)
                continue;
            dataObjects.addAll(friesObjects);
        }
        // If no results found.
        if (dataObjects.size() == 0) {
            JOptionPane.showMessageDialog(elv, 
                                          "Nothing can be found for this query.", 
                                          "No Result", 
                                          JOptionPane.INFORMATION_MESSAGE);
            window.getGlassPane().setVisible(false);
            return null;
        }
        window.getGlassPane().setVisible(false);
        return dataObjects;
    }
    
    private List<String> extractIds(List<GKInstance> instances,
                                    JComponent comp) throws Exception {
        Set<String> proteinIds = new HashSet<>();
        for (GKInstance instance : instances) {
            Set<GKInstance> refEntities = InstanceUtilities.grepReferenceEntitiesForPE(instance);
            for (GKInstance refEntity : refEntities) {
                if (refEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceSequence)) {
                    String id = (String) refEntity.getAttributeValue(ReactomeJavaConstants.identifier);
                    if (id != null)
                        proteinIds.add(id);
                }
            }
        }
        if (proteinIds.size() == 0) {
            JOptionPane.showMessageDialog(comp, 
                                          "No valid identifier can be extracted from the selected instance.", 
                                          "No Identifier", 
                                          JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return proteinIds.stream().sorted().collect(Collectors.toList());
    }
    
    /**
     * Querying a GraphiQL instance containing pre-processed Reach results for a UniProt id.
     * @param id
     * @return friesObjects
     * @throws IOException
     */
    private List<FriesObject> fetchDataFromGraphQL(String id) throws Exception {
        // Create REACH queries for an element.
        String template = new String(Files.readAllBytes(Paths.get(ReachConstants.GRAPHQL_SEARCH_TEMPLATE)));
        String graphqlInput = String.format(template, id);
        // Return a list of JSON responses from REACH.
        ReachHttpCall reachCall = new ReachHttpCall();
        String jsonOutput = reachCall.callGraphQL(graphqlInput);
        //Use example graphql file as json
        //      String graphQLOutputExample = "examples/reachOutputExample_full.json";
        //      String jsonOutput = new String(Files.readAllBytes(Paths.get(graphQLOutputExample)), StandardCharsets.UTF_8);

        GraphQLObject graphQLObject = ReachUtils.readJsonTextGraphQL(jsonOutput);
        // Create Fries object for use in table.
        GraphQLToFriesConverter graphQLToFriesConverter = new GraphQLToFriesConverter();
        List<FriesObject> friesObjects = graphQLToFriesConverter.convertGraphQLObject(graphQLObject);
        return friesObjects;
    }
	
}
