package org.gk.reach.model.fries;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"frame-type", "object-type", "found-by"})
public class Event extends FrameObject {

    private List<Argument> arguments;
    private String type; // e.g. regulation
    private String subtype;
    @JsonProperty("is-direct")
    private Boolean isDirect;
    private String trigger;
    @JsonIdentityReference
    private Sentence sentence;
    @JsonProperty("is-hypothesis")
    private Boolean isHypothesis;
    @JsonProperty("is-negated")
    private Boolean isNegated;
    @JsonProperty("verbose-text")
    private String verboseText;

    public Event() {
    }

    public Boolean getIsNegated() {
        return isNegated;
    }

    public void setIsNegated(Boolean isNegated) {
        this.isNegated = isNegated;
    }

    public Boolean getIsHypothesis() {
        return isHypothesis;
    }

    public void setIsHypothesis(Boolean isHypothesis) {
        this.isHypothesis = isHypothesis;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public Boolean getIsDirect() {
        return isDirect;
    }

    public void setIsDirect(Boolean isDirect) {
        this.isDirect = isDirect;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public Sentence getSentence() {
        return sentence;
    }

    public void setSentence(Sentence sentence) {
        this.sentence = sentence;
    }

    public void setVerboseText(String verboseText) {
        this.verboseText = verboseText;
    }

    public String getVerboseText() {
        return verboseText;
    }

}
