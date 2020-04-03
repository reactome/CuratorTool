package org.gk.reach.model.graphql;

import java.util.List;

import org.gk.reach.ReachUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Underscore naming required in {@link ReachUtils#readJsonText(String)}
 * @author stephen
 */
@JsonIgnoreProperties({"reader_type", "reading_complete", "reading_started", 
    "score", "submitter", "mesh_heading", "verbose_text"})
public class Document {
    
	private String _id;
	private List<String> evidence;
	private Information extracted_information;
	private String journal_title;
	private String model_relation;
	private String pmc_id;
	private String pmid;
	private String publication_year;
	private String trigger;
	private String article_type;
	private String doi;
	
	public Document() {
	    
	}

    public String getArticle_type() {
        return article_type;
    }
    public List<String> getEvidence() {
        return evidence;
    }
    public void setEvidence(List<String> evidence) {
        this.evidence = evidence;
    }
    public void setArticle_type(String article_type) {
        this.article_type = article_type;
    }
    public String getDoi() {
        return doi;
    }
    public void setDoi(String doi) {
        this.doi = doi;
    }
	
	public String get_id() {
		return _id;
	}
	public void set_id(String _id) {
		this._id = _id;
	}
	
	public Information getExtracted_information() {
		return extracted_information;
	}
	public void setExtracted_information(Information extracted_information) {
		this.extracted_information = extracted_information;
	}
	public String getJournal_title() {
		return journal_title;
	}
	public void setJournal_title(String journal_title) {
		this.journal_title = journal_title;
	}
	public String getModel_relation() {
		return model_relation;
	}
	public void setModel_relation(String model_relation) {
		this.model_relation = model_relation;
	}
	public String getPmc_id() {
		return pmc_id;
	}
	public void setPmc_id(String pmc_id) {
		this.pmc_id = pmc_id;
	}
	public String getPmid() {
		return pmid;
	}
	public void setPmid(String pmid) {
		this.pmid = pmid;
	}
	public String getPublication_year() {
		return publication_year;
	}
	public void setPublication_year(String publication_year) {
		this.publication_year = publication_year;
	}
	
	public String getTrigger() {
		return trigger;
	}
	public void setTrigger(String trigger) {
		this.trigger = trigger;
	}
}
