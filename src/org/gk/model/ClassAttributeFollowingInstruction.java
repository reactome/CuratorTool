/*
 * Created on Sep 16, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package org.gk.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author vastrik
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class ClassAttributeFollowingInstruction {
	private String className;
	private Collection attributes = new ArrayList();
	private Collection reverseAttributes = new ArrayList();
		
	public ClassAttributeFollowingInstruction (String className,
											   Collection attributes,
											   Collection reverseAttributes) {
		this.className = className;
		this.attributes.addAll(attributes);
		this.reverseAttributes.addAll(reverseAttributes);
	}
	
	public ClassAttributeFollowingInstruction (String className,
											   String[] attributes,
											   String[] reverseAttributes) {
			this.className = className;
			if (attributes != null)
			    this.attributes.addAll(Arrays.asList(attributes));
			if (reverseAttributes != null)
			    this.reverseAttributes.addAll(Arrays.asList(reverseAttributes));
	}
		
	/**
	 * @return String collection of attributes
	 */
	public Collection getAttributes() {
		return attributes;
	}

	/**
	 * @return Class name as String
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * @return String collection of reverse attributes
	 */
	public Collection getReverseAttributes() {
		return reverseAttributes;
	}

	/**
	 * @param collection String collection of attributes
	 */
	public void setAttributes(Collection collection) {
		attributes = collection;
	}

	/**
	 * @param string Class name as String
	 */
	public void setClassName(String string) {
		className = string;
	}

	/**
	 * @param collection String collection of reverse attributes
	 */
	public void setReverseAttributes(Collection collection) {
		reverseAttributes = collection;
	}
}
