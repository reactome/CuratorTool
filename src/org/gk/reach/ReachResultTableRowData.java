package org.gk.reach;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.gk.model.Reference;
import org.gk.reach.model.fries.Argument;
import org.gk.reach.model.fries.Entity;
import org.gk.reach.model.fries.Event;
import org.gk.reach.model.fries.XRef;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Class to provide a stable API for retrieving REACH-derived data from a REACH table.
 */
@JsonInclude(Include.NON_NULL)
public class ReachResultTableRowData implements Serializable {
    protected List<Event> events;
    protected List<Reference> references;
    private Boolean isAccepted;
    
    public ReachResultTableRowData() {
        events = new ArrayList<Event>();
        references = new ArrayList<Reference>();
        isAccepted = false;
    }
    
    public Entity getParticipantA() {
        if (events == null || events.size() == 0)
            return null;
        List<Argument> arguments = events.get(0).getArguments();
        if (arguments == null || arguments.size() == 0)
            return null;
        return (Entity) arguments.get(0).getArg();
    }
    
    public String getParticipantAText() {
        return getParticipantText(getParticipantA());
    }
    
    public String getParticipantBText() {
        return getParticipantText(getParticipantB());
    }
    
    private String getParticipantText(Entity entity) {
        if (entity == null)
            return null;
        return entity.getText();
    }
    
    public String getParticipantAId() {
        Entity entity = getParticipantA();
        return getParticipantId(entity);
    }
    
    public String getParticipantBId() {
        Entity entity = getParticipantB();
        return getParticipantId(entity);
    }

    private String getParticipantId(Entity entity) {
        if (entity ==  null)
            return null;
        List<XRef> xrefs = entity.getXrefs();
        if (xrefs == null || xrefs.size() == 0)
            return null;
        XRef xref = xrefs.get(0); // Usually there should be one only if any
        if (xref == null)
            return null;
        String id = xref.getId();
        if (id.contains(":")) // e.g. GO
            return id; 
        String ns = xref.getNamespace();
        if (ns == null)
            return id; // We have done our best
        return ns + ":" + id;
    }

    public Entity getParticipantB() {
        if (events == null || events.size() == 0)
            return null;
        List<Argument> arguments = events.get(0).getArguments();
        if (arguments == null || arguments.size() < 2)
            return null;
        return (Entity) arguments.get(1).getArg();
    }

    public String getInteractionType() {
        if (events == null || events.size() == 0)
            return null;
        return events.get(0).getType();
    }

    public String getInteractionSubtype() {
        if (events == null || events.size() == 0)
            return null;
        return events.get(0).getSubtype();
    }
    
    public void setIsAccepted(boolean isAccepted) {
        this.isAccepted = isAccepted;
    }
    
    public boolean getIsAccepted() {
        return isAccepted;
    }

    public List<Event> getEvents() {
        return events;
    }
    public List<Reference> getReferences(){
    	return references;
    }

    void addEvent(Event event) {
        if (events == null)
            return;
        events.add(event);
    }
    
    void addReference(Reference reference) {
    	if (reference == null)
    		return;
    	references.add(reference);
    }
    
    public String getRowKey() {
        return (getParticipantAText() +
                getParticipantAId() + 
                getParticipantBText() +
                getParticipantBId() + 
                getInteractionType() +
                getInteractionSubtype()).toUpperCase();
    }

}
