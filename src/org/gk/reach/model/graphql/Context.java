package org.gk.reach.model.graphql;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Context {
    
    @JsonProperty("CellType")
    private List<String> cellType;
    @JsonProperty("Organ")
    private List<String> organ;
    @JsonProperty("Species")
    private List<String> species;
	
    public Context() {
    }

    public List<String> getCellType() {
        return cellType;
    }

    public void setCellType(List<String> cellType) {
        this.cellType = cellType;
    }

    public List<String> getOrgan() {
        return organ;
    }

    public void setOrgan(List<String> organ) {
        this.organ = organ;
    }

    public List<String> getSpecies() {
        return species;
    }

    public void setSpecies(List<String> species) {
        this.species = species;
    }
	
}
