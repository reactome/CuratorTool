package org.gk.reach.model.paperMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class Result {
    private Map<String, Map<String, Object>> paperData;
    private List<String> uids;

    public Result() {
        paperData = new HashMap<String, Map<String, Object>>();
    }

    // NCBI returns paper data with dynamic JSON fields (the paper's id),
    // so generalize here and simply return a map.
    @JsonAnyGetter
    public Map<String, Map<String, Object>> getPaperData() {
        return paperData;
    }
    @JsonAnySetter
    public void setPaperData(String key, Map<String, Object> value) {
        paperData.put(key, value);
    }

    public void setUids(List<String> uids) {
        this.uids = uids;
    }
    public List<String> getUids() {
        return uids;
    }
}
