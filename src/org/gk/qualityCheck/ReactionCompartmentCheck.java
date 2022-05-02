/*
 * Created on Apr 3, 2008
 *
 */
package org.gk.qualityCheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.SchemaClass;
import org.gk.schema.GKSchemaClass;
import org.gk.util.GKApplicationUtilities;

/**
 * The following rules will be applied to check if there is any compartment conflict among
 * reaction participants and the reaction:
 * 1. If more than three compartments have been used in the reaction or its participants, the reaction will be flagged.
 * 2. If there are two compartments used in the reaction participants:
 *     1). If these two compartments are not adjacent (it is not possible one compartment can contain another)
 * the reaction should be flagged
 *     2). Reaction compartment should be checked:
 *         a. If the reaction has one compartment, and this one compartment cannot contain these two compartment,
 * the reaction should be flagged
 *         b. If the reaction has two compartments, each of them should be a container of one of two entity compartment (include
 * the identity). Otherwise, it should be flagged as false.
 * 3. If there is only one compartment used in the reaction participants, the reaction compartment should be the same as
 * this entity compartment. Otherwise, the reaction should be flagged.
 * @author wgm
 *
 */
public class ReactionCompartmentCheck extends CompartmentCheck {

    private static final String EXTRA_REACTION_COMPARTMENTS = "One participant compartment but more than one reaction compartment";
    private static final String COMPARTMENT_CONTAINMENT = "Reaction's compartment doesn't contain participants' compartments";
    private static final String COMPARTMENTS_NOT_ADJACENT = "Participants' two compartments are not adjacent";
    private static final String TOO_MANY_REACTION_COMPARTMENTS = "More than 2 compartments in reaction";
    private static final String NO_REACTION_COMPARTMENT = "No reaction compartment";
    private static final String TOO_MANY_PARTICIPANT_COMPARTMENTS = "More than 2 compartments in participants";
    // A list of allowed Event Compartment to EntityCompartment: DB_ID,DB_ID.
    // This list can be applied to single Reaction compartment and single
    // Entity compartment
    private List allowedRxtEntityCompartments;
    private ReactionQACheckHelper qaHelper;

    public ReactionCompartmentCheck() {
        checkClsName = ReactomeJavaConstants.Reaction;
        qaHelper = new ReactionQACheckHelper();
    }

    protected List getAllowedRxtEntityCompartments() throws IOException {
        if (allowedRxtEntityCompartments == null) {
            // Need to load
            allowedRxtEntityCompartments = new ArrayList();
            InputStream input = GKApplicationUtilities.getConfig("AllowedEntityEventCompartments.txt");
            InputStreamReader ris = new InputStreamReader(input);
            BufferedReader bufferedReader = new BufferedReader(ris);
            String line = null;
            int index = 0;
            while ((line = bufferedReader.readLine()) != null) {
                // 70101,13325 #cytosol - glycogen granule
                if (line.startsWith("//"))
                    continue; // Comment
                index = line.indexOf("#");
                String sub = line.substring(0, index).trim();
                allowedRxtEntityCompartments.add(sub);
            }
            bufferedReader.close();
            ris.close();
            input.close();
        }
        return allowedRxtEntityCompartments;
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.Reaction);
    }

    private boolean isAdjacent(Collection<GKInstance> compartments) {
        // Check if these two compartments are adjacent
        Iterator<GKInstance> it = compartments.iterator();
        GKInstance compartment1 = it.next();
        GKInstance compartment2 = it.next();
        Map<Long, List<Long>> neighborMap = getNeighbors();
        List<Long> neighbor = neighborMap.get(compartment1.getDBID());
        if (neighbor == null ||
            !neighbor.contains(compartment2.getDBID()))
            return false; // These two compartments are not adjacent. A wrong case!
        return true;
    }

    @Override
    protected Issue getIssue(GKInstance container, Set<GKInstance> containedCompartments,
            List<GKInstance> containerCompartments) throws Exception {
        if (containedCompartments.size() > 2)
                return new Issue(TOO_MANY_PARTICIPANT_COMPARTMENTS);
        if (containerCompartments.size() > 2)
                return new Issue(TOO_MANY_REACTION_COMPARTMENTS);
        if (containedCompartments.size() == 2) {
            if (!isAdjacent(containedCompartments))
                return new Issue(COMPARTMENTS_NOT_ADJACENT); // These two compartments are not adjacent. A wrong case!
            // Check how many compartments in the reaction
            if (containerCompartments.size() == 0)
                return new Issue(NO_REACTION_COMPARTMENT); // Nothing is assigned
            Iterator<GKInstance> it = containedCompartments.iterator();
            GKInstance compartment1 = it.next();
            GKInstance compartment2 = it.next();
            if (containerCompartments.size() == 1) {
                GKInstance reactionCompartment = (GKInstance) containerCompartments.get(0);
                // Need to check if reactionCompartment is a container of compartment1 and compartment2
                Set containers1 = getAllContainers(compartment1);
                Set containers2 = getAllContainers(compartment2);
                // Ensure that the two entity compartments are contained by
                // the single Reaction compartment.
                if (!containers1.contains(reactionCompartment) ||
                    !containers2.contains(reactionCompartment))
                    return new Issue(COMPARTMENT_CONTAINMENT);
            }
            else if (containerCompartments.size() == 2) {
                GKInstance rxtCompt1 = (GKInstance) containerCompartments.get(0);
                GKInstance rxtCompt2 = (GKInstance) containerCompartments.get(1);
                if (!checkTwoReactionAndTwoEntityCompartments(compartment1,
                                                              compartment2,
                                                              rxtCompt1,
                                                              rxtCompt2))
                    // Ensure that the two reaction compartment are containers
                    // for each of the two entitycompartment, respectively.
                    return new Issue(COMPARTMENT_CONTAINMENT);
            }
        }
        else if (containedCompartments.size() == 1) {
            GKInstance entityCompt = (GKInstance) containedCompartments.iterator().next();
            // Reaction can have only one compartment at most
            if (containerCompartments.size() != 1)
                return new Issue(TOO_MANY_REACTION_COMPARTMENTS);
            GKInstance rxtCompt = (GKInstance) containerCompartments.get(0);
            Set containers = getAllContainers(entityCompt);
            containers.add(entityCompt); // These two compartments can be the same.
            if (!containers.contains(rxtCompt)) {
                // Some specical cases are allowed
                List allowedCases = getAllowedRxtEntityCompartments();
                String key = rxtCompt.getDBID() + "," + entityCompt.getDBID();
                // Reaction compartment should be a container of the
                // sole entity compartment (or the same).
                if (!allowedCases.contains(key))
                    return new Issue(COMPARTMENT_CONTAINMENT);
            }
        }
        return null;
    }

    protected boolean checkTwoReactionAndTwoEntityCompartments(GKInstance entityCompartment1,
                                                             GKInstance entityCompartment2,
                                                             GKInstance rxtCompartment1,
                                                             GKInstance rxtCompartment2) throws Exception {
        Set containers1 = getAllContainers(entityCompartment1);
        containers1.add(entityCompartment1);
        Set containers2 = getAllContainers(entityCompartment2);
        containers2.add(entityCompartment2);
        // Compare rxtCompartment1 to entityCompartment1,
        // and rxtCompartment2 to entityComparmment2
        if (containers1.contains(rxtCompartment1) &&
            containers2.contains(rxtCompartment2))
            return true;
        // Compare rxtCompartment1 to entityCompartment2,
        // and rxtCompartment2 to entityCompartment1
        if (containers1.contains(rxtCompartment2) &&
            containers2.contains(rxtCompartment1))
            return true;
        return false;
    }

    protected Set<GKInstance> getAllContainers(GKInstance compartment) throws Exception {
        return InstanceUtilities.getContainedInstances(compartment,
                                                       ReactomeJavaConstants.componentOf,
                                                       ReactomeJavaConstants.instanceOf);
    }

    @Override
    protected Set<GKInstance> getAllContainedEntities(GKInstance reaction) throws Exception {
        return qaHelper.getAllContainedEntities(reaction);
    }

    protected ResultTableModel getResultTableModel() {
        String[] colNames = new String[] {"Role", "Participant", "Compartment"};
        ResultTableModel tableModel = qaHelper.getResultTableModel(colNames,
                                                                   checkAttribute);
        return tableModel;
    }

    protected void loadAttributes(Collection instances) throws Exception {
        // Only MySQLAdator should be used to load attributes
        Neo4JAdaptor dba = (Neo4JAdaptor) dataSource;
        String[] attNames = new String[] {
                ReactomeJavaConstants.input,
                ReactomeJavaConstants.output,
                ReactomeJavaConstants.compartment,
                ReactomeJavaConstants.catalystActivity
        };
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.Reaction);
        qaHelper.loadAttributes(instances,
                                cls,
                                attNames,
                                dba,
                                progressPane);
        if (progressPane != null && progressPane.isCancelled())
            return ;
        if (progressPane != null)
            progressPane.setText("Load CatalystActivity...");
        loadAttributes(ReactomeJavaConstants.CatalystActivity,
                       ReactomeJavaConstants.physicalEntity,
                       dba);
        if (progressPane != null && progressPane.isCancelled())
            return;
        qaHelper.loadRegulations(dba,
                                 progressPane);
        if (progressPane != null && progressPane.isCancelled())
            return;
        // Need componentOf information
        if (progressPane != null)
            progressPane.setText("Load Compartment componentOf...");
        loadAttributes(ReactomeJavaConstants.Compartment,
                       ReactomeJavaConstants.componentOf,
                       dba);
        if (progressPane != null && progressPane.isCancelled())
            return;
        // No need to load EntitySet's component information. They should be handled
        // by EntitySet compartment checking.
        if (progressPane != null)
            progressPane.setText("Load PhysicalEntity compartment...");

        Collection<GKSchemaClass> subclasses = ((GKSchemaClass)dba.getSchema().getClassByName(ReactomeJavaConstants.PhysicalEntity)).getSubClasses();
        for (GKSchemaClass subclass : subclasses)
        {
            if (subclass.isValidAttribute(ReactomeJavaConstants.compartment))
            {
                loadAttributes(subclass.getName(), ReactomeJavaConstants.compartment, dba);
            }
        }
    }

    /**
     * Override the super class method so that a full catalyst and regulation can be
     * checked out.
     */
    protected void grepCheckOutInstances(GKInstance reaction,
                                         Set checkOutInstances) throws Exception {
        Set components = getAllContainedEntities(reaction);
        checkOutInstances.addAll(components);
        // Need to push into regulations and CatalystActivities too.
        // Note: catalyst and regulator should be handled by method getAllContainedEntities(reaction)
        List cases = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        if (cases != null)
            checkOutInstances.addAll(cases);
        if (reaction.getSchemClass().isValidAttribute(ReactomeJavaConstants.regulatedBy)) {
            List<GKInstance> regulatedBy = reaction.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            if (regulatedBy != null)
                checkOutInstances.addAll(regulatedBy);
        }
        else {
            if (qaHelper.getCachedRegulations() == null) {
                SchemaClass regulationCls = dataSource.getSchema().getClassByName(ReactomeJavaConstants.Regulation);
                Collection regulations = dataSource.fetchInstancesByClass(regulationCls);
                qaHelper.setCachedRegulations(regulations);
            }
            // Check if any regulations should be checked out
            for (Iterator it = qaHelper.getCachedRegulations().iterator(); it.hasNext();) {
                GKInstance regulation = (GKInstance) it.next();
                GKInstance regulated = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                if (regulated == reaction)
                    checkOutInstances.add(regulation);
            }
        }
    }

}