package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public abstract class EntityFunctionalStatusCheck extends SingleAttributeClassBasedCheck {
    protected Map<GKInstance, String> instToIssue;
    
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
    protected void loadAttributes(Collection<GKInstance> instances) throws Exception {
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        dba.loadInstanceAttributeValues(instances, followAttributes);
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
