package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;

public class EntityFunctionalStatusDiseaseEntityCheck extends EntityFunctionalStatusCheck {
    
    public EntityFunctionalStatusDiseaseEntityCheck() {
        checkAttribute = "disease entity";
        followAttributes = new String[] {ReactomeJavaConstants.diseaseEntity};
    }

    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        GKInstance diseaseEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.diseaseEntity);
        if (diseaseEntity == null) {
            instToIssue.put(instance, "Empty diseaseEntity");
            return false;
        }
        // Check if this diseaseEntity is in the RLE's participant
        Collection<GKInstance> referrers = instance.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
        if (referrers == null || referrers.size() == 0)
            return true; // Just don't care
        for (GKInstance rle : referrers) {
            Set<GKInstance> rleParticipants = InstanceUtilities.getReactionLHSParticipants(rle);
            if (!rleParticipants.contains(diseaseEntity)) {
                instToIssue.put(instance, "DiseaseEntity not an RLE's parcipant");
                return false; // false is not passed
            }
        }
        return true;
    }

    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        ResultTableModel model = new DiseaseEntityTableModel();
        return model;
    }
    
    private class DiseaseEntityTableModel extends EFSEntityTableModel {
        
        protected void fillData(GKInstance instance) throws Exception {
            GKInstance diseaseEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.diseaseEntity);
            if (diseaseEntity == null) {
                setColNames(new String[] {
                        "EFS_DB_ID",
                        "EFS_DisplayName",
                        "Issue"
                });
                List<String> row = new ArrayList<>();
                row.add(instance.getDBID().toString());
                row.add(instance.getDisplayName());
                row.add("Empty diseaseEntity");
                data.add(row);
            }
            else {
                fillEFSData(instance);
            }
        }

        private void fillEFSData(GKInstance efs) throws Exception {
            setColNames(new String[] {
                    "DiseaseRLE_DB_ID",
                    "DiseaseRLE_DisplayName",
                    "DiseaseRLE_Participants"
            });
            // Check if this diseaseEntity is in the RLE's participant
            Collection<GKInstance> referrers = efs.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
            if (referrers == null || referrers.size() == 0)
                return ; 
            for (GKInstance rle : referrers) {
                Set<GKInstance> rleParticipants = InstanceUtilities.getReactionLHSParticipants(rle);
                List<String> row = new ArrayList<>();
                row.add(rle.getDBID().toString());
                row.add(rle.getDisplayName());
                String text = rleParticipants.stream().map(i -> i.toString()).collect(Collectors.joining(";"));
                row.add(text);
                data.add(row);
            }
        }
        
    }

}