package org.gk.reach;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.Reference;
import org.gk.reach.model.fries.Argument;
import org.gk.reach.model.fries.Entity;
import org.gk.reach.model.fries.Event;
import org.gk.reach.model.fries.FrameCollection;
import org.gk.reach.model.fries.FrameObject;
import org.gk.reach.model.fries.FriesObject;
import org.gk.reach.model.fries.XRef;
import org.gk.reach.model.graphql.Document;
import org.gk.reach.model.graphql.Feature;
import org.gk.reach.model.graphql.Information;
import org.gk.reach.model.graphql.Modification;
import org.gk.reach.model.graphql.Participant;
import org.gk.reach.model.graphql.GraphQLObject;
import org.junit.Test;

/**
 * This converted is used to convert the data model fetched from GraphQL to fries data model.
 * I guess most likely the graphQL is converted from the fries data model. So this is a little 
 * bit weird. However, I believe it is more robust to use the original fries results directly.
 * @author wug
 *
 */
public class GraphQLToFriesConverter {

    public GraphQLToFriesConverter() {
    }
    
    /**
     * Use this method to convert a GraphQL object into a list of FriesObject.
     * @param obj
     * @return
     */
    public List<FriesObject> convertGraphQLObject(GraphQLObject obj) {
        List<FriesObject> rtn = new ArrayList<>();
        List<Document> documents = obj.getData().getAllDocuments();
        // We want to group all extracted relationships from the same paper into 
        // a single FiesObject. To do this, sort the documents into a map first
        Map<String, List<Document>> pmcidToDocs = new HashMap<>();
        for (Document document : documents) {
            String pmcid = document.getPmc_id();
            pmcidToDocs.compute(pmcid, (key, list) -> {
                if (list == null)
                    list = new ArrayList<>();
                list.add(document);
                return list;
            });
        }
        for (String pmcid : pmcidToDocs.keySet()) {
            List<Document> pmcidDocs = pmcidToDocs.get(pmcid);
            FriesObject fo = new FriesObject();
            // Get the reference from the first document
            Reference reference = extractReference(pmcidDocs.get(0));
            fo.setReference(reference);
            FrameCollection<Event> events = new FrameCollection<>();
            fo.setEvents(events);
            for (Document doc : pmcidDocs) {
                Event event = convertDocument(doc);
                events.addFrameObject(event);
            }
            rtn.add(fo);
        }
        return rtn;
    }
    
    private Event convertDocument(Document doc) {
        Event event = new Event();
        event.setTrigger(doc.getTrigger());
        if (doc.getEvidence() != null && doc.getEvidence().size() > 0)
            event.setText(doc.getEvidence().get(0)); // Use the text as the convenient place. In theory, we should use Sentence.
        // Basic information directly from information
        Information information = doc.getExtracted_information();
        event.setType(information.getInteraction_type());
        event.setIsNegated(information.getNegative_information());
        event.setIsHypothesis(information.getHypothesis_information());
        // Convert the modifications, e.g.:
//        "modifications": [
//                          {
//                            "modification_type": "phosphorylation",
//                            "position": null
//                          }
//                        ],
        // Though modifications are listed as a list, however, we can handle one modification only
        if (information.getModifications() != null && information.getModifications().size() > 0) {
            Modification modification = information.getModifications().get(0);
            // Save it for the sub-type for the time being
            event.setSubtype(modification.getModification_type());
        }
        List<Argument> arguments = new ArrayList<>();
        // Both participant_a and _b use lists, however, there should be only one in each of list
        // However, the converting code can handle any number
        if (information.getParticipant_a() != null && information.getParticipant_a().size() > 0) {
            for (Participant participant : information.getParticipant_a()) {
                Argument entity = convertParticipant(participant);
                arguments.add(entity);
            }
        }
        if (information.getParticipant_b() != null && information.getParticipant_b().size() > 0) {
            for (Participant participant : information.getParticipant_b()) {
                Argument entity = convertParticipant(participant);
                arguments.add(entity);
            }
        }
        event.setArguments(arguments);
        return event;
    }
    
    private Argument convertParticipant(Participant p) {
        Argument argument = new Argument();
        Entity fo = new Entity();
        // There are 11 types in participant, for the time being, all of them
        // are converted into Entity, even for bioprocess, which should be mapped
        // to Reactome's pathways
        fo.setType(p.getEntity_type());
        // For easy access
        argument.setType(p.getEntity_type());
        fo.setText(p.getEntity_text());
        String identifier = p.getIdentifier();
        if (identifier != null) {
            // Pass it into a XRef
            String[] tokens = identifier.split(":");
            XRef xref = new XRef();
            if (tokens.length == 2) {
                xref.setNamespace(tokens[0]);
                xref.setId(tokens[1]);
            }
            else
                xref.setId(identifier); // Just in case. This should not occur.
            fo.addXref(xref);
        }
        // Features in Participant should be converted into Modifications
        if (p.getFeatures() != null && p.getFeatures().size() > 0) {
            List<org.gk.reach.model.fries.Modification> fModifications = new ArrayList<>();
            for (Feature feature : p.getFeatures()) {
                org.gk.reach.model.fries.Modification modification = convertFeature(feature);
                fModifications.add(modification);
            }
            fo.setModifications(fModifications);
        }
        argument.setArg(fo);
        return argument;
    }
    
    /**
     * An example:
     *               "features": [
                {
                  "evidence": null,
                  "feature_type": "modification",
                  "modification_type": "unknown",
                  "position": null,
                  "site": null,
                  "to_base": null
                }
     * @param feature
     * @return
     */
    private org.gk.reach.model.fries.Modification convertFeature(Feature feature) {
        org.gk.reach.model.fries.Modification modification = new org.gk.reach.model.fries.Modification();
        modification.setEvidence(feature.getEvidence());
        modification.setType(feature.getFeature_type());
        modification.setSite(feature.getSite());
        return modification;
    }
    
    /**
     * Convert 
     * @param document
     * @return
     */
    private Reference extractReference(Document document) {
        Reference reference = new Reference();
        reference.setJournal(document.getJournal_title());
        reference.setPmcid(document.getPmc_id());
        if (document.getPmid() != null && document.getPmid().length() > 0)
            reference.setPmid(new Long(document.getPmid()));
        String year = document.getPublication_year();
        if (year != null && year.length() > 0)
            reference.setYear(new Integer(year));
        return reference;
    }
    
    @Test
    public void testConvert() throws IOException {
        String sourceFileName = "examples/reachOutputExample_full.json";
        GraphQLObject graphQLObj = ReachUtils.readFileGraphQL(sourceFileName);
        List<FriesObject> friesObjs = convertGraphQLObject(graphQLObj);
        System.out.println("Total FriesObjects: " + friesObjs.size());
        // Check one FriesObject
        FriesObject fo = friesObjs.stream().findAny().get();
        FrameCollection<Event> events = fo.getEvents();
        Event event = events.getFrameObjects()
                            .stream()
                            .filter(e -> e.getArguments().size() > 1)
                            .findAny()
                            .get();
        System.out.println("Event: " + event.getText());
        System.out.println("\tType: " + event.getType());
        List<Argument> arguments = event.getArguments();
        for (Argument argument : arguments) {
            System.out.println("\tArgument: " + argument.getArg().getText());
            FrameObject frameObj = argument.getArg();
            if (frameObj instanceof Entity) {
                Entity entity = (Entity) frameObj;
                XRef xref = entity.getXrefs().get(0);
                System.out.println("\tXref: " + xref.getNamespace() + ":" + xref.getId());
            }
        }
    }
    
}
