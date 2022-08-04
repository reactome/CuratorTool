package org.gk.qualityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;

/**
 * QA check which detects <em>normalEntity</em> instances which
 * are either empty or are not a normal reaction participant.
 *
 * @author wug
 */
public class EntityFunctionalStatusNormalEntityCheck extends EntityFunctionalStatusCheck {

    public EntityFunctionalStatusNormalEntityCheck() {
        checkAttribute = "normal entity";
        followAttributes = new String[] {ReactomeJavaConstants.normalEntity};    
    }
    
    @Override
    public String getDisplayName() {
        return "Normal_Entity_Inconsistent";
    }
    
    @Override
    protected ResultTableModel getResultTableModel() throws Exception {
        return new NormalEntityTableModel();
    }

    @Override
    protected boolean checkInstance(GKInstance instance) throws Exception {
        // Check if this diseaseEntity is in the RLE's participant
        Collection<GKInstance> referrers = efsToRLEs.get(instance);
        if (referrers == null || referrers.size() == 0)
            return true; // Just don't care
        GKInstance normalEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.normalEntity);
        for (GKInstance rle : referrers) {
            GKInstance normalRLE = (GKInstance) rle.getAttributeValue(ReactomeJavaConstants.normalReaction);
            if (normalRLE == null)
                continue;
            if (normalEntity == null) {
                instToIssue.put(instance, "has normalReaction but empty normalEntity");
                return false;
            }
            // Make sure normalEntity is contained by normalRLE
            Set<GKInstance> normalParticipants = InstanceUtilities.getReactionLHSParticipants(normalRLE);
            if (!normalParticipants.contains(normalEntity)) {
                instToIssue.put(instance, "normalEntity not a normal RLE's participant");
                return false;
            }
        }
        return true;
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
        Set<GKInstance> normalRLEs = new HashSet<>();
        for (GKInstance rle : rles) {
            GKInstance normalRLE = (GKInstance) rle.getAttributeValue(ReactomeJavaConstants.normalReaction);
            if (normalRLE != null)
                normalRLEs.add(normalRLE);
        }
        loadReactionParticipants(normalRLEs, dba);
        return rles;
    }

    private class NormalEntityTableModel extends EFSEntityTableModel {
        
        protected void fillData(GKInstance efs) throws Exception {
            setColNames(new String[] {
                    "NormalRLE_DB_ID",
                    "NormalRLE_DisplayName",
                    "NormalRLE_Participants"
            });
            // Check if this diseaseEntity is in the RLE's participant
            Collection<GKInstance> referrers = efs.getReferers(ReactomeJavaConstants.entityFunctionalStatus);
            if (referrers == null || referrers.size() == 0)
                return ; 
            for (GKInstance rle : referrers) {
                GKInstance normalRLE = (GKInstance) rle.getAttributeValue(ReactomeJavaConstants.normalReaction);
                Set<GKInstance> rleParticipants = InstanceUtilities.getReactionLHSParticipants(normalRLE);
                List<String> row = new ArrayList<>();
                row.add(normalRLE.getDBID().toString());
                row.add(normalRLE.getDisplayName());
                String text = rleParticipants.stream().map(i -> i.toString()).collect(Collectors.joining(";"));
                row.add(text);
                data.add(row);
            }
        }
        
    }    
    
}
