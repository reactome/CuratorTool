package org.gk.reach.model.graphql;

import java.util.List;

import org.gk.reach.ReachUtils;

/**
 * Underscore naming required in {@link ReachUtils#readJsonText(String)}
 * @author stephen
 */
public class Information {
    
    private String binding_site;
    private Context context;
    private String from_location_id;
    private String from_location_text;
    private Boolean hypothesis_information;
    private String interaction_type;
    private List<Modification> modifications;
    private Boolean negative_information;
    private List<Participant> participant_a;
    private List<Participant> participant_b;
    private String to_location_id;
    private String to_location_text;
    
    public Information() {
    }

    public String getBinding_site() {
        return binding_site;
    }

    public void setBinding_site(String binding_site) {
        this.binding_site = binding_site;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public String getFrom_location_id() {
        return from_location_id;
    }

    public void setFrom_location_id(String from_location_id) {
        this.from_location_id = from_location_id;
    }

    public String getFrom_location_text() {
        return from_location_text;
    }

    public void setFrom_location_text(String from_location_text) {
        this.from_location_text = from_location_text;
    }

    public Boolean getHypothesis_information() {
        return hypothesis_information;
    }

    public void setHypothesis_information(Boolean hypothesis_information) {
        this.hypothesis_information = hypothesis_information;
    }

    public String getInteraction_type() {
        return interaction_type;
    }

    public void setInteraction_type(String interaction_type) {
        this.interaction_type = interaction_type;
    }

    public List<Modification> getModifications() {
        return modifications;
    }

    public void setModifications(List<Modification> modifications) {
        this.modifications = modifications;
    }

    public Boolean getNegative_information() {
        return negative_information;
    }

    public void setNegative_information(Boolean negative_information) {
        this.negative_information = negative_information;
    }

    public List<Participant> getParticipant_a() {
        return participant_a;
    }

    public void setParticipant_a(List<Participant> participant_a) {
        this.participant_a = participant_a;
    }

    public List<Participant> getParticipant_b() {
        return participant_b;
    }

    public void setParticipant_b(List<Participant> participant_b) {
        this.participant_b = participant_b;
    }

    public String getTo_location_id() {
        return to_location_id;
    }

    public void setTo_location_id(String to_location_id) {
        this.to_location_id = to_location_id;
    }

    public String getTo_location_text() {
        return to_location_text;
    }

    public void setTo_location_text(String to_location_text) {
        this.to_location_text = to_location_text;
    }

}
