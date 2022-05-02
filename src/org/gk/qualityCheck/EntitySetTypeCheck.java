/*
 * Created on May 1, 2012
 *
 */
package org.gk.qualityCheck;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.GKSchemaClass;

/**
 * Usually an EntitySet should have members having the same type of class (e.g. Complex
 * or EWAS). But in rare case, it may have different types.
 * @author gwu
 *
 */
public class EntitySetTypeCheck extends SingleAttributeClassBasedCheck {
    
    public EntitySetTypeCheck() {
        this.checkAttribute = "Member Type";
        this.checkClsName = ReactomeJavaConstants.EntitySet;
        this.followAttributes = new String[]{ReactomeJavaConstants.hasMember,
                                             ReactomeJavaConstants.hasCandidate};
    }
    
    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        Set<GKInstance> contained = getAllContainedEntities(instance);
        StringBuilder builder = new StringBuilder();
        contained.forEach(i -> builder.append(i.getDBID()).append(":").append(i.getSchemClass().getName()).append("|"));
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }
    
    @Override
    protected String getIssueTitle() {
        return "TypesOfMembers";
    }
    
    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        //TODO: For this check, we may need one layer of members
        Set<GKInstance> contained = getAllContainedEntities(instance);
        // Skip checking for shell instances
        if (containShellInstances(contained))
            return true;
        // Nothing to check if contained is empty: probably 
        // the instance is just starting to annotation or
        // container is used as a place holder
        if (contained.size() == 0)
            return true;
        // Get the type of this EntitySet
        Set<String> memberClasses = new HashSet<String>();
        for (GKInstance member : contained) {
            GKSchemaClass memberType = (GKSchemaClass) member.getSchemClass();
            memberClasses.add(memberType.getName());
        }
        // Set inside set should be fine
        memberClasses.remove(ReactomeJavaConstants.DefinedSet);
        memberClasses.remove(ReactomeJavaConstants.CandidateSet);
        // Just in case: this should not be used
        memberClasses.remove(ReactomeJavaConstants.EntitySet);
        return memberClasses.size() == 1;
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances, ReactomeJavaConstants.EntitySet);
    }

    @Override
    protected void loadAttributes(Collection<GKInstance> instances)
            throws Exception {
        Neo4JAdaptor dba = (Neo4JAdaptor) dataSource;
        loadEntitySetMembers(instances, dba);
    }

    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        ResultTableModel model = new ComponentTableModel() {

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                GKInstance component = (GKInstance) components.get(rowIndex);
                if (columnIndex == 0) {
                    String name = component.getDisplayName();
                    Long dbId = component.getDBID();
                    return name + " [" + dbId + "]";
                }
                return component.getSchemClass().getName();
            }
        };
        model.setColNames(new String[]{"hasMember", "Class"});
        return model;
    }
    
    
}
