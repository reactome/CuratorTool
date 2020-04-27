package org.gk.reach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import org.gk.database.FrameManager;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.reach.model.fries.FriesObject;
import org.gk.schema.InvalidAttributeException;

public class ReachSearch {
    private final String errorTitle = "Allowed Schema Classes: ";
    private final String errorMsg = "Invalid Schema Classes";
    private final String errorTitleReference = "Null Reference";
    private final String errorMsgRefeference = "The reference entity for this instance can not be found.";
    private final String delimiter = ", ";

    public ReachSearch() {
    }

	/**
	 * Filter passed instances to those with valid schema classes.
	 *
	 * @param instances
	 * @param parentCompenent
	 * @throws Exception
	 * @throws InvalidAttributeException
	 */
	private List<GKInstance> filterInstances(List<GKInstance> instances, JComponent parentCompenent) throws InvalidAttributeException, Exception {
	    if (instances == null || instances.size() == 0)
	        return null;
	    if (parentCompenent == null)
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
            FrameManager.getManager().getFrames();
		    JOptionPane.showMessageDialog(parentCompenent,
                                          errorTitle + String.join(delimiter, validSchemaClasses),
                                          errorMsg,
                                          JOptionPane.ERROR_MESSAGE);
		    return null;
		}

		// Check if instances are checked out or not.
		for (GKInstance instance : filteredInstances) {
		    if (instance.isShell()) {
		        FrameManager.getManager().showShellInstance(instance, parentCompenent);

		        // return statement requires Curators to select "Search REACH" again after checking in the instance.
		        // TODO possible way to wait for instance check-in and then continue below?
		        return null;
		    }

		    GKInstance referenceEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.referenceEntity);
		    if (referenceEntity == null)
		        JOptionPane.showMessageDialog(parentCompenent,
                                              errorTitleReference,
                                              errorMsgRefeference,
                                              JOptionPane.ERROR_MESSAGE);
		    if (referenceEntity.isShell()) {
		        FrameManager.getManager().showShellInstance(referenceEntity, parentCompenent);
		        return null;
		    }
		}

        return filteredInstances;
	}

	/**
	 * Build the REACH table based on the filtered instances.
	 * @throws Exception 
	 * @throws InvalidAttributeException 
	 */
	public void buildTable(List<GKInstance> instances, JComponent elv) throws InvalidAttributeException, Exception {
	    List<GKInstance> filteredInstances = filterInstances(instances, elv);
	    if (filteredInstances == null || filteredInstances.size() == 0)
	        return;
		Thread thread = new Thread() {
			public void run() {
				try {
				    ReachResultTableFrame reachResultTableFrame = new ReachResultTableFrame();
					List<FriesObject> dataObjects = reachResultTableFrame.searchReach(filteredInstances);
					reachResultTableFrame.setReachData(dataObjects);
				} catch (Exception e) {
					System.err.println("Error Building Table: " + e);
					e.printStackTrace();
				}
			}
		};
		thread.start();
	}

	public void buildTable(List<FriesObject> dataObjects) throws InvalidAttributeException, Exception {
	    if (dataObjects == null || dataObjects.size() == 0)
	        return;
		Thread thread = new Thread() {
			public void run() {
				try {
				    ReachResultTableFrame reachResultTableFrame = new ReachResultTableFrame();
					reachResultTableFrame.setReachData(dataObjects);
				} catch (Exception e) {
					System.err.println("Error Building Table: " + e);
					e.printStackTrace();
				}
			}
		};
		thread.start();
	}
}
