package org.gk.reach.model.graphql;

import java.util.List;

public class Data {
	private List<Document> allDocuments;

	public Data() {
	}
	
	public List<Document> getAllDocuments() {
		return allDocuments;
	}
	public void setAllDocuments(List<Document> allDocuments) {
		this.allDocuments = allDocuments;
	}
}
