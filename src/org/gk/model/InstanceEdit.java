/*
 * Created on Jul 28, 2003
 */
package org.gk.model;

import java.io.Serializable;

/**
 * For describing the instance editing event.
 * @author wgm
 */
public class InstanceEdit implements Serializable {
	private String authorName;
	private String date;
	
	public InstanceEdit() {
	}
	
	public InstanceEdit(String author, String date) {
		this.authorName = author;
		this.date = date;
	}

	/**
	 * @return Name of the author of this instance edit
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * @return Date of this instance edit as a String
	 */
	public String getDate() {
		return date;
	}

	/**
	 * @param author Set the name of the author of this instance edit
	 */
	public void setAuthorName(String author) {
		authorName = author;
	}

	/**
	 * @param date Set the date of the author of this instance edit
	 */
	public void setDate(String date) {
		this.date = date;
	}

}
