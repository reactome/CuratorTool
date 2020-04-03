package org.gk.reach.model.fries;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                  property = "frame-id")
public class FrameObject implements Serializable { // This class should not be made abstract. Otherwise, the Jackson API throws an exception
    
    @JsonProperty("frame-id")
    private String frameId;
    private String text;
    @JsonProperty("start-pos")
    private Position startPos;
    @JsonProperty("end-pos")
    private Position endPos;
    
    public FrameObject() {
    }

    public String getFrameId() {
        return frameId;
    }

    public void setFrameId(String frameId) {
        this.frameId = frameId;
    }
    
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Position getStartPos() {
        return startPos;
    }

    public void setStartPos(Position startPos) {
        this.startPos = startPos;
    }

    public Position getEndPos() {
        return endPos;
    }

    public void setEndPos(Position endPos) {
        this.endPos = endPos;
    }

}
