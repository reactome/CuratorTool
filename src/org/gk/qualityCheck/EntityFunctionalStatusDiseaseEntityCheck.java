package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;

/**
 * QA check which detects <em>diseaseEntity</em> instances which
 * are either empty or are not a disease reaction participant.
 *
 * @author wug
 */
public class EntityFunctionalStatusDiseaseEntityCheck extends EntityFunctionalStatusCheck {
    
    public EntityFunctionalStatusDiseaseEntityCheck() {
        checkAttribute = "disease entity";
        followAttributes = new String[] {ReactomeJavaConstants.diseaseEntity};
    }
    
    @Override
    public String getDisplayName() {
        return "Disease_Entity_Inconsistent";
    }

    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        GKInstance diseaseEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.diseaseEntity);
        if (diseaseEntity == null) {
            instToIssue.put(instance, "Empty diseaseEntity");
            return false;
        }
        // Check if this diseaseEntity is in the RLE's participant
        Collection<GKInstance> referrers = efsToRLEs.get(instance);
        if (referrers == null || referrers.size() == 0)
            return true; // Just don't care
        for (GKInstance rle : referrers) {
            Set<GKInstance> rleParticipants = InstanceUtilities.getReactionLHSParticipants(rle);
            if (!rleParticipants.contains(diseaseEntity)) {
                instToIssue.put(instance, "DiseaseEntity not an RLE's participant");
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
    
    protected Collection<GKInstance> loadReactions(PersistenceAdaptor dba) throws Exception {
        // We will start with RLEs for quick performance
        Collection<GKInstance> rles = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                                                   ReactomeJavaConstants.entityFunctionalStatus,
                                                                   "IS NOT NULL",
                                                                   null);
        dba.loadInstanceAttributeValues(rles, new String[] {
                ReactomeJavaConstants.entityFunctionalStatus,
                ReactomeJavaConstants.normalReaction
                });
        loadReactionParticipants(rles, dba);
        return rles;
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
            GKInstance diseaseEntity = (GKInstance) efs.getAttributeValue(ReactomeJavaConstants.diseaseEntity);
            if (diseaseEntity == null)
                return ; // Nothing to be done
            for (GKInstance rle : referrers) {
                Set<GKInstance> rleParticipants = InstanceUtilities.getReactionLHSParticipants(rle);
                if (rleParticipants.contains(diseaseEntity))
                    continue; // An EFS may be involved in multiple disease reactions.
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
