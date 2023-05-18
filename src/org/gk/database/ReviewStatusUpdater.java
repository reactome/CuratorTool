package org.gk.database;

import java.awt.Component;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;
import org.gk.persistence.XMLFileAdaptor;

/**
 * This class is used to perform the following review status update: 
 * The implementation here is based on the following actions (see details 
 * https://docs.google.com/presentation/d/1aQ5hLPl3mzKf2kFvK0KMcPqriEleuRdq/edit?usp=share_link&ouid=106679115272778152377&rtpof=true&sd=true).
 * 1). When an internalReviewed is assigned to an Event, one of the following occurs (promotion):
 *      a). Null -> 3 stars
 *      b). one star -> 3 stars
 *      c). 2 stars -> 4 stars
 * 2). When a reviewed is assigned to an Event, one of the following occurs (promotion):
 *      a). Null -> 5 stars
 *      b). 2 stars -> 5 stars
 *      c). 3 stars -> 5 stars
 *      d). 4 stars -> 5 stars
 * 3). When any structural update occurs, one of the following occurs (demotion):
 *      a). 3 stars -> 1 star
 *      b). 5 stars -> 2 stars
 *      c). 4 stars -> 2 stars
 * The curator tool will not catch any undo once the change is committed into gk_central
 * @author wug
 *
 */
public class ReviewStatusUpdater implements AttributeEditListener {
    // A flag to control all ReviewStatus instances have been downloaded.
    private Map<String, GKInstance> name2inst;

    public ReviewStatusUpdater() {
    }

    @Override
    public void attributeEdit(AttributeEditEvent e) {
        GKInstance instance = e.getEditingInstance();
        if (instance == null || !instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.reviewStatus))
            return; // Do nothing if there is no reviewStatus in instance
        try {
            // Keep the original one just in case it is needed
            GKInstance preStatus = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.reviewStatus);
            GKInstance newStatus = null;
            boolean isDemoted = false;
            if (isInternalReviewedAdded(e)) {
                ensureReviewStatusInLocal(e.getEditingComponent());
                if (preStatus == null ||
                    preStatus.getDisplayName().equals(ReactomeJavaConstants.OneStar)) {
                    newStatus = name2inst.get(ReactomeJavaConstants.ThreeStars);
                }
                else if (preStatus.getDisplayName().equals(ReactomeJavaConstants.TwoStars)) {
                    newStatus = name2inst.get(ReactomeJavaConstants.FourStars);
                }
            }
            else if (isExternalReviewedAdded(e)) {
                ensureReviewStatusInLocal(e.getEditingComponent());
                if (preStatus == null ||
                    preStatus.getDisplayName().equals(ReactomeJavaConstants.TwoStars) ||
                    preStatus.getDisplayName().equals(ReactomeJavaConstants.ThreeStars) ||
                    preStatus.getDisplayName().equals(ReactomeJavaConstants.FourStars)) {
                    newStatus = name2inst.get(ReactomeJavaConstants.FiveStars);
                }
            }
            else if (isStructralUpdate(e)) {
                ensureReviewStatusInLocal(e.getEditingComponent());
                if (preStatus != null) {
                    if (preStatus.getDisplayName().equals(ReactomeJavaConstants.ThreeStars)) {
                        newStatus = name2inst.get(ReactomeJavaConstants.OneStar);
                    }
                    else if (preStatus.getDisplayName().equals(ReactomeJavaConstants.FourStars) ||
                             preStatus.getDisplayName().equals(ReactomeJavaConstants.FiveStars))
                        newStatus = name2inst.get(ReactomeJavaConstants.TwoStars);
                } 
                if (newStatus != null)
                    isDemoted = true;
                // Mark it as long as there is a structural update
                instance.setIsStructuralUpdated(true);
            }
            if (newStatus == null)
                return; // Nothing to do
            // Assign the original reviewStatus even though it is null
            instance.setAttributeValue(ReactomeJavaConstants.previousReviewStatus, preStatus);
            // Used to fire attribute edit events
            AttributeEditEvent reviewStatusEdit = new AttributeEditEvent(this); // Need to set to this
            reviewStatusEdit.setEditingType(AttributeEditEvent.UPDATING);
            reviewStatusEdit.setAttributeName(ReactomeJavaConstants.previousReviewStatus);
            reviewStatusEdit.setEditingInstance(instance);
            // Hack this a little bit so that we can update the view
            reviewStatusEdit.setEditingComponent(null);
            AttributeEditManager.getManager().attributeEdit(reviewStatusEdit);
            // Assign new status
            instance.setAttributeValue(ReactomeJavaConstants.reviewStatus, newStatus);
            reviewStatusEdit.setAttributeName(ReactomeJavaConstants.reviewStatus);
            AttributeEditManager.getManager().attributeEdit(reviewStatusEdit);
            if (isDemoted) {
                String message = "Your edit changes the pathway structure. The reviewStatus has been demoted.";
                JOptionPane.showMessageDialog(e.getEditingComponent(),
                        message,
                        "ReviewStatus Demoted",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
        catch(Exception exp) {
            JOptionPane.showMessageDialog(e.getEditingComponent(),
                    "Updating reviewStatus: " + exp.getMessage(),
                    "Error in Attribute Edit",
                    JOptionPane.ERROR_MESSAGE);
            exp.printStackTrace(); // If we cannot update stable id, just ignore it and let QA handles it.
        }
    }

    protected boolean isExternalReviewedAdded(AttributeEditEvent e) {
        return isReviewedAdded(e, ReactomeJavaConstants.reviewed);
    }

    protected boolean isInternalReviewedAdded(AttributeEditEvent e) {
        return isReviewedAdded(e, ReactomeJavaConstants.internalReviewed);
    }
    
    @SuppressWarnings("unchecked")
    private void ensureReviewStatusInLocal(Component comp) throws Exception {
        if (name2inst != null)
            return;
        JFrame parentFrame = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, comp);
        SynchronizationManager.getManager().downloadControlledVocabulary(parentFrame,
                ReactomeJavaConstants.ReviewStatus,
                true);
        // Extract the downloaded instances
        XMLFileAdaptor fileAdaptor = PersistenceManager.getManager().getActiveFileAdaptor();
        Collection<GKInstance> reviewStatusInsts = fileAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReviewStatus);
        name2inst = reviewStatusInsts.stream().collect(Collectors.toMap(inst -> inst.getDisplayName(),
                                                                        Function.identity()));
    }
    
    private boolean isReviewedAdded(AttributeEditEvent e, String reviewSlotName) {
        // Most likely check out from DB
        if (e.getAttributeName() == null)
            return false;
        GKInstance instance = e.getEditingInstance();
        if (instance.isShell())
            return false; // Unlikely. Just defensive programming
        // Apply only for Event
        if (!instance.getSchemClass().isa(ReactomeJavaConstants.Event))
            return false;
        if (!e.getAttributeName().equals(reviewSlotName))
            return false;
        if (e.getEditingType() != AttributeEditEvent.ADDING) // Care about adding only
            return false;
        return true; 
    }

    private boolean isStructralUpdate(AttributeEditEvent e) {
        // If there is no attribute name, assume not a structural update
        if (e.getAttributeName() == null)
            return false;
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
