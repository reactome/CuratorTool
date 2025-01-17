/*
 * Created on Aug 17, 2009
 *
 */
package org.gk.qualityCheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.util.GKApplicationUtilities;

public abstract class CompartmentCheck extends SingleAttributeClassBasedCheck {
    private final String NEIGHBOR_FILE_NAME = "AdjacentCompartments.txt";

    protected final List<GKInstance> EMPTY_LIST = new ArrayList<GKInstance>();

    /**
     * Item reported in the QA check.
     *
     * @author Fred Loney loneyf@ohsu.edu
     */
    // TODO - this class and the issue collector mechanism should be pulled up
    // into the superclass as the generic QA check mechanism. Ideally, issues
    // should be streamed rather than collected. This would avoid the redundancy
    // found in, e.g., SpeciesCheck.
    protected static class Issue {
         
        private String text;
        
        private Collection<GKInstance> instances;
        
        Issue(String text, Collection<GKInstance> instances) {
            this.text = text;
            this.instances = instances;
        }
        
        Issue(String text) {
            this(text, null);
        }
        
        public String toString() {
            String message = text.toString();
            if (instances == null || instances.isEmpty()) {
                return message;
            } else {
                String dbIdsStr = instances.stream()
                        .map(GKInstance::getDisplayName)
                        .collect(Collectors.joining(", "));
                return message + ": " + dbIdsStr;
            }
        }

    }
    
    /**
     * The container {instance: issue} map.
     */
    private Map<GKInstance, Issue> containerToIssue;
    protected Map<Long, List<Long>> neighbors = null;
    
    public CompartmentCheck() {
        checkAttribute = "compartment";
        containerToIssue = new HashMap<GKInstance, Issue>();
    }
    
    protected Map<Long, List<Long>> getNeighbors() {
        if (neighbors == null)
            neighbors = loadNeighbors();
        return neighbors;
    }
    
    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        return checkCompartment(instance);
    }
    
    /**
     * Gets a String describing the issue of the offended instance.
     * This is the issue column value.
     * 
     * @return the instance issue column value
     * @see #getIssueTitle()
     */
    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        Issue issue = containerToIssue.get(instance);
        return issue == null ? null : issue.toString();
    }

    /**
     * The class specific way to check compartment values.
     * @param container 
     * 
     * @param containedCompartments the contained entities compartments
     * @param containerCompartments the container entity compartments
     * @return the issue to report
     * @throws Exception
     */
    protected abstract Issue getIssue(GKInstance container, Set<GKInstance> containedCompartments,
            List<GKInstance> containerCompartments) throws Exception;
    
    protected Map<Long, List<Long>> loadNeighbors() {
        Map<Long, List<Long>> map = new HashMap<Long, List<Long>>();
        try {
            InputStream input = GKApplicationUtilities.getConfig(NEIGHBOR_FILE_NAME);
            InputStreamReader ris = new InputStreamReader(input);
            BufferedReader bufferedReader = new BufferedReader(ris);
            String line = null;
            int index = 0;
            while ((line = bufferedReader.readLine()) != null) {
                // 70101,12045 #cytosol - ER membrane
                index = line.indexOf("#");
                String sub = line.substring(0, index).trim();
                String[] tokens = sub.split(",");
                Long dbId1 = new Long(tokens[0]);
                Long dbId2 = new Long(tokens[1]);
                List<Long> neighborIds = (List<Long>) map.get(dbId1);
                if (neighborIds == null) {
                    neighborIds = new ArrayList<Long>();
                    map.put(dbId1, neighborIds);
                }
                neighborIds.add(dbId2);
            }
            bufferedReader.close();
            ris.close();
            input.close();
        }
        catch(IOException e) {
            System.err.println("CompartmentCheck.loadNeighbors(): " + e);
            e.printStackTrace();
        }
        return map;
    }
    
    /**
     * Check if the compartment setting in a container is consistent with its contained instances.
     * @param container
     * @return false for error in a compartment setting
     * @throws Exception
     */
    protected boolean checkCompartment(GKInstance container) throws Exception {
        Set<GKInstance> contained = getAllContainedEntities(container);
        // Skip checking for shell instances
        if (containShellInstances(contained))
            return true;
        // Nothing to check if contained is empty: probably 
        // the instance is just starting to annotation or
        // container is used as a place holder
        if (contained.size() == 0)
            return true;
        // Get the compartment setting: compartments should be a list since
        // it is used as a multiple value attribute.
        Set<GKInstance> containedCompartments = getContainedCompartments(contained);
        @SuppressWarnings("unchecked")
        List<GKInstance> containerCompartments =
                container.getAttributeValuesList(ReactomeJavaConstants.compartment);
        // To make compare easier
        if (containerCompartments == null)
            containerCompartments = EMPTY_LIST;
        Issue issue = getIssue(container, containedCompartments, containerCompartments);
        if (issue == null) {
            return true;
        } else {
            containerToIssue.put(container, issue);
            return false;
        }
    }

    protected Set<GKInstance> getContainedCompartments(Set<GKInstance> contained) throws Exception {
        Set<GKInstance> containedCompartments = new HashSet<GKInstance>();
        for (Iterator<GKInstance> it = contained.iterator(); it.hasNext();) {
            GKInstance comp = (GKInstance) it.next();
            @SuppressWarnings("unchecked")
            List<GKInstance> compartments =
                    comp.getAttributeValuesList(ReactomeJavaConstants.compartment);
            if (compartments != null)
                containedCompartments.addAll(compartments);
        }
        return containedCompartments;
    }
    
}
