package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public abstract class EntityFunctionalStatusCheck extends SingleAttributeClassBasedCheck {
    protected Map<GKInstance, String> instToIssue;
    protected Map<GKInstance, Set<GKInstance>> efsToRLEs = new HashMap<>();
    
    public EntityFunctionalStatusCheck() {
        checkClsName = ReactomeJavaConstants.EntityFunctionalStatus;
        instToIssue = new HashMap<>();
    }

    @Override
    protected Set<GKInstance> filterInstancesForProject(Set<GKInstance> instances) {
        return filterInstancesForProject(instances,
                                         ReactomeJavaConstants.EntityFunctionalStatus);
    }

    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    protected String getIssue(GKInstance instance) throws Exception {
        return instToIssue.get(instance);
    }

    @Override
    protected String getResultPanePostfix(GKInstance instance) {
        try {
            return ": " + getIssue(instance);
        }
        catch(Exception e) {
            return "";
        }
    }
    
    @Override
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        Collection<GKInstance> rles = loadReactions(dba);
        dba.loadInstanceAttributeValues(instances, followAttributes);
        // Referrers check is slow. Therefore, create a map for it.
        efsToRLEs.clear();
        for (GKInstance rle : rles) {
            GKInstance efs = (GKInstance) rle.getAttributeValue(ReactomeJavaConstants.entityFunctionalStatus);
            if (efs == null)
                continue; // Should not occur
            efsToRLEs.compute(efs, (key, set) -> {
                if (set == null)
                    set = new HashSet<>();
                set.add(rle);
                return set;
            });
        }
    }
    
    protected abstract Collection<GKInstance> loadReactions(MySQLAdaptor dba) throws Exception;

    protected void loadReactionParticipants(Collection<GKInstance> rles, MySQLAdaptor dba) throws Exception {
        dba.loadInstanceAttributeValues(rles, new String[] {
                ReactomeJavaConstants.input,
                ReactomeJavaConstants.regulatedBy,
                ReactomeJavaConstants.catalystActivity,
                });
        Set<GKInstance> cas = new HashSet<>();
        for (GKInstance rle : rles) {
            List<GKInstance> values = rle.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
            cas.addAll(values);
        }
        dba.loadInstanceAttributeValues(cas, new String[] {ReactomeJavaConstants.physicalEntity});
        Set<GKInstance> regulations = new HashSet<>();
        for (GKInstance rle : rles) {
            List<GKInstance> values = rle.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            regulations.addAll(values);
        }
        dba.loadInstanceAttributeValues(regulations, new String[] {ReactomeJavaConstants.regulator});
    }

    abstract class EFSEntityTableModel extends ResultTableModel {
        protected List<List<String>> data;
        
        public EFSEntityTableModel() {
            data = new ArrayList<>();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<String> row = data.get(rowIndex);
            return row.get(columnIndex);
        }

        @Override
        public void setInstance(GKInstance instance) {
            data.clear();
            try {
                fillData(instance);
                fireTableStructureChanged();
            }
            catch(Exception e) {
                System.err.println("DiseaseEntityTableModel.setInstance(): " + e);
                e.printStackTrace();
            }
        }
        
        protected abstract void fillData(GKInstance instance) throws Exception;
    }
}
