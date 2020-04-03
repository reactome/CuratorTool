package org.gk.reach.model.fries;

import java.io.File;
import java.io.Serializable;

import org.gk.model.Reference;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The topmost class for the Reach NLP results generated from one specific paper.
 * @author wug
 *
 */
public class FriesObject implements Serializable {
    
    private FrameCollection<Event> events;
    private FrameCollection<Entity> entities;
    private FrameCollection<Sentence> sentences;
    // Represents the paper
    private Reference reference;
    
    public FriesObject() {
    }
    
    public Reference getReference() {
        return reference;
    }

    public void setReference(Reference reference) {
        this.reference = reference;
    }

    public FrameCollection<Event> getEvents() {
        return events;
    }

    public void setEvents(FrameCollection<Event> events) {
        this.events = events;
    }

    public FrameCollection<Entity> getEntities() {
        return entities;
    }

    public void setEntities(FrameCollection<Entity> entities) {
        this.entities = entities;
    }

    public FrameCollection<Sentence> getSentences() {
        return sentences;
    }

    public void setSentences(FrameCollection<Sentence> sentences) {
        this.sentences = sentences;
    }

    @Test
    public void testRead() throws Exception {
        File file = new File("examples/PMC4306850.fries.json");
        ObjectMapper mapper = new ObjectMapper();
        FriesObject fo = mapper.readValue(file, FriesObject.class);
        FrameCollection<Sentence> sentences = fo.getSentences();
        if (sentences == null || sentences.getFrameObjects() == null)
            System.out.println("No sentences!");
        else
            System.out.println("Total sentences: " + sentences.getFrameObjects().size());
        FrameCollection<Entity> entities = fo.getEntities();
        if (entities == null || entities.getFrameObjects() == null) 
            System.out.println("No entities!");
        else {
            System.out.println("Total entities: " + entities.getFrameObjects().size());
            Entity entity = entities.getFrameObjects().stream().findAny().get();
            System.out.println("Text: " + entity.getText());
            System.out.println("Frame-id: " + entity.getFrameId());
            System.out.println("type: " + entity.getType());
            System.out.println("Sentence: " + entity.getSentence().getText());
        }
        FrameCollection<Event> events = fo.getEvents();
        if (events == null || events.getFrameObjects() == null)
            System.out.println("No events!");
        else {
            System.out.println("Total events: " + events.getFrameObjects().size());
            Event event = events.getFrameObjects().stream().findAny().get();
            System.out.println("text: " + event.getText());
            System.out.println("type: " + event.getType());
            System.out.println("subtype: " + event.getSubtype());
            System.out.println("sentence: " + event.getSentence().getText());
        }
    }

}
