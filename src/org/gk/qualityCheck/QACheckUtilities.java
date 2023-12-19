package org.gk.qualityCheck;

import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;

public class QACheckUtilities {

    public static GKInstance getLatestCuratorIEFromInstance(GKInstance instance) throws Exception {
        @SuppressWarnings("unchecked")
        List<GKInstance> modIEs = instance.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (modIEs != null) {
            List<Long> developers = QACheckProperties.getDeveloperDbIds();
            for (int index = modIEs.size() - 1; index >= 0; index--) {
                GKInstance modIE = modIEs.get(index);
                GKInstance author = (GKInstance) modIE.getAttributeValue("author");
                // Skip modification instance for developers.
                if (author != null && !developers.contains(author.getDBID())) {
                    return modIE;
                }

            }
        }
        // Get the created one
        return (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
    }

    /**
     * Get the first IE for the passed instance. It is the created IE if it exists. Otherwise, it is the first
     * IE in the modified slot.
     * @param instance
     * @return
     * @throws Exception
     */
    public static GKInstance getFirstCuratorIEFromInstance(GKInstance instance) throws Exception {
        GKInstance createdIE = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
        if (createdIE != null)
            return createdIE;
        @SuppressWarnings("unchecked")
        List<GKInstance> modIEs = instance.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (modIEs != null) {
            List<Long> developers = QACheckProperties.getDeveloperDbIds();
            for (int i = 0; i < modIEs.size(); i++) {
                GKInstance modIE = modIEs.get(i);
                GKInstance author = (GKInstance) modIE.getAttributeValue("author");
                // Skip modification instance for developers.
                if (author != null && !developers.contains(author.getDBID())) {
                    return modIE;
                }
            }
        }
        return null;
    }

}
