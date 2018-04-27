package org.gk.database;

import java.awt.Component;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.PersistenceManager;

/**
 * This class is used to update _displayName of a Regulation when it is added or removed
 * from its targetted ReactionlikeEvent via slot regulatedBy.
 * @author wug
 *
 */
public class RegulationDisplayNameUpdater implements AttributeEditListener {
    private Component parentComp;

    public RegulationDisplayNameUpdater() {
    }

    public void setParentComponent(Component parentComp) {
        this.parentComp = parentComp;
    }
    
    @Override
    public void attributeEdit(AttributeEditEvent e) {
        if (!ReactomeJavaConstants.regulatedBy.equals(e.getAttributeName()))
            return;
        // Get the effected RGKInstance        
        List<GKInstance> regulations = null;
        if (e.getEditingType() == AttributeEditEvent.ADDING) {
            regulations = e.getAddedInstances();
        }
        else if (e.getEditingType() == AttributeEditEvent.REMOVING) {
            regulations = e.getRemovedInstances();
        }
        if (regulations == null || regulations.size() == 0)
            return;
        handleRegulationDisplayName(e.getEditingInstance(), regulations);
    }
    
    private void handleRegulationDisplayName(GKInstance reaction,
                                             List<GKInstance> regulations) {
        // Get regulations that should be checked out fully
        Set<GKInstance> shellInstances = regulations.stream().filter(regulation -> regulation.isShell()).collect(Collectors.toSet());
        if (shellInstances.size() > 0) {
            throw new IllegalStateException("The edited ReactionlikeEvent has shell Regulation removed or added.\n"
                    + "Please don't commit this instance to the database:\n" + reaction);
        }
        regulations.forEach(r -> {
            // Force to clear referrers.
            r.clearReferers();
            InstanceDisplayNameGenerator.setDisplayName(r);
            PersistenceManager.getManager().getActiveFileAdaptor().markAsDirty(r);
        });
        // Generate output
        StringBuilder builder = new StringBuilder();
        if (regulations.size() > 1)
            builder.append("The following Regulations' display names have been updated:\n");
        else
            builder.append("The following Regulation display name has been updated:\n");
        regulations.forEach(r -> builder.append(r.getDisplayName()).append("\n"));
        builder.delete(builder.length() - 1, builder.length());
        JOptionPane.showMessageDialog(parentComp,
                builder.toString(),
                "Regulation Display Name Update", 
                JOptionPane.INFORMATION_MESSAGE);
    }
}
