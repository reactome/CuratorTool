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
                 GKInstance modIE = modIEs.get(modIEs.size() - 1);
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

}
