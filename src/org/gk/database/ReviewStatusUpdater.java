package org.gk.database;

import static org.gk.model.ReactomeJavaConstants.OneStar;
import static org.gk.model.ReactomeJavaConstants.TwoStars;

import java.awt.Component;
import java.util.Collection;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

/**
 * This class is used to demote the reviewStatus to 2 stars from 3, 4, and 5 stars if the 
 * following edits occurs:
 * 1). ReactionlikeEvent: add/remove catalystActivty, input, output and regulatedBy.
 * 2). Pathway: add/remove hasEvent
 * Note: The above editing may change the pathway structure. For ReactionlikeEvent, if a catalyst or regulator
 * is replaced in the used catalystActivity or regulatedBy, this class will not catch such a change even though
 * it should!!!
 * @author wug
 *
 */
public class ReviewStatusUpdater implements AttributeEditListener {

    public ReviewStatusUpdater() {
    }

    @Override
    public void attributeEdit(AttributeEditEvent e) {
        GKInstance instance = e.getEditingInstance();
        if (instance == null || !instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus))
            return; // Do nothing if there is no reviewStatus in instance
        try {
            GKInstance preStatus = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.reviewStatus);
            if (preStatus == null || 
                preStatus.getDisplayName().equals(OneStar) || 
                preStatus.getDisplayName().equals(TwoStars))
                return; // No need to do anything in the above three cases
            // Check if we need to update reviewStatus
            if (!needDemotion(e))
                return;
            GKInstance twoStars = getLocalTwoStars(e.getEditingComponent());
            if (twoStars == null) {
                String message = "Your edit changes the pathway structure. The reviewStatus needs to be demoted\n" + 
                                 "to 2 stars. However, the curator tool cannot find a 2 stars ReviewStatus!";
                JOptionPane.showMessageDialog(e.getEditingComponent(),
                        message,
                        "No Two Stars ReviewStatus",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Assign the original reviewStatus
            instance.setAttributeValue(ReactomeJavaConstants.previousReviewStatus, preStatus);
            // Used to fire attribute edit events
            AttributeEditEvent reviewStatusEdit = new AttributeEditEvent(this); // Need to set to this
            reviewStatusEdit.setEditingType(AttributeEditEvent.UPDATING);
            reviewStatusEdit.setAttributeName(ReactomeJavaConstants.previousReviewStatus);
            reviewStatusEdit.setEditingInstance(instance);
            // Hack this a little bit so that we can update the view
            reviewStatusEdit.setEditingComponent(null);
            AttributeEditManager.getManager().attributeEdit(reviewStatusEdit);
            instance.setAttributeValue(ReactomeJavaConstants.reviewStatus, twoStars);
            reviewStatusEdit.setAttributeName(ReactomeJavaConstants.reviewStatus);
            AttributeEditManager.getManager().attributeEdit(reviewStatusEdit);
            String message = "Your edit changes the pathway structure. The reviewStatus has been " + 
                             "demoted to 2 stars.";
            JOptionPane.showMessageDialog(e.getEditingComponent(),
                    message,
                    "Demoting to 2 Stars",
                    JOptionPane.WARNING_MESSAGE);
        }
        catch(Exception exp) {
            JOptionPane.showMessageDialog(e.getEditingComponent(),
                    "Updating reviewStatus: " + exp.getMessage(),
                    "Error in Attribute Edit",
                    JOptionPane.ERROR_MESSAGE);
            exp.printStackTrace(); // If we cannot update stable id, just ignore it and let QA handles it.
        }
    }
    
    @SuppressWarnings("unchecked")
    private GKInstance getLocalTwoStars(Component comp) throws Exception {
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        Collection<GKInstance> instances = fileAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReviewStatus,
                ReactomeJavaConstants._displayName,
                "=",
                TwoStars);
        if (instances != null && instances.size() > 0)
            return instances.stream().findAny().get(); // There should be only one
        // Need to check from the database
        MySQLAdaptor dba = PersistenceManager.getManager().getActiveMySQLAdaptor(comp);
        if (dba == null)
            return null; // Must be cancelled.
        instances = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReviewStatus,
                ReactomeJavaConstants._displayName,
                "=",
                TwoStars);
        if (instances == null || instances.size() == 0) {
            JOptionPane.showMessageDialog(comp,
                    "Cannot find two stars ReviewStatus at the database.",
                    "Error in Updating ReviewStatus",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        GKInstance dbInst = instances.stream().findAny().get();
        GKInstance localInst = PersistenceManager.getManager().download(dbInst);
        return localInst;
    }

    private boolean needDemotion(AttributeEditEvent e) {
        GKInstance instance = e.getEditingInstance();
        if (instance.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
            if (e.getAttributeName().equals(ReactomeJavaConstants.hasEvent)) {
                return e.getEditingType() == AttributeEditEvent.ADDING ||
                        e.getEditingType() == AttributeEditEvent.REMOVING;
            }
            return false;
        }
        else if (instance.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
            if (e.getEditingType() == AttributeEditEvent.ADDING ||
                    e.getEditingType() == AttributeEditEvent.REMOVING) {
                String attName = e.getAttributeName();
                if (attName.equals(ReactomeJavaConstants.catalystActivity) ||
                        attName.equals(ReactomeJavaConstants.regulatedBy) ||
                        attName.equals(ReactomeJavaConstants.input) ||
                        attName.equals(ReactomeJavaConstants.output))
                    return true;
                return false;
            }
            return false;
        }
        else
            return false;
    }

}
