/*
 * Created on Jun 27, 2003
 */
package org.gk.model;

import java.io.Serializable;

import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;

/**
 * 
 * @author wgm
 */
public interface Instance extends Serializable {
	
	public SchemaClass getSchemClass(); 
	
	public void setSchemaClass(SchemaClass schemaClass);
	
	public java.util.Collection getSchemaAttributes();
	
	public void setAttributeValue(String attributeName, Object value) throws Exception;
	
	public void setAttributeValue(SchemaAttribute attribute, Object value) throws Exception;
	/**
	 * Get the value of the attribute. If there is more than one values,
	 * the first value should be returned.
	 * @param attributeName Name of attribute
	 * @return Value of attribute
	 * @throws Exception Thrown if unable to get attribute value
	 */
	public Object getAttributeValue(String attributeName) throws Exception;
	
	public Object getAttributeValue(SchemaAttribute attribute) throws Exception;
	
	// It seems not necessary with these two methods. Also it is very 
	// confusing with same method signature but different return types.
	/**
	 * A list of objects that are values of a SchemaAttribute.
	 * @param attributeName
	 * @return
	 */
	//public java.util.List getAttributeValues(String attributeName);
	
	//public java.util.List getAttributeValues(SchemaAttribute attribute);
	
	/**
	 * For a multiple attribute.
	 * @param attributeName Name of attribute for which to add a value
	 * @param value Value to add for the attribute
	 * @throws Exception Thrown if unable to add value for attribute name
	 */
	public void addAttributeValue(String attributeName, Object value) throws Exception;
	
	public void addAttributeValue(SchemaAttribute attribute, Object value) throws Exception;
	
	/**
	 * Get the list of Instances that refer to this Instance.
	 * @param attributeName Name of attribute used by other instances to refer to this instance
	 * @return List of Instances that refer to this instance by the attribute name
	 * @throws Exception Thrown if unable to get referrers for attribute name
	 */
	public java.util.Collection getReferers(String attributeName) throws Exception;
	public java.util.Collection getReferers(SchemaAttribute attribute) throws Exception;
	
	public Long getDBID();
	
	public void setDBID(Long id);
	
	public String getDisplayName();
	
	public void setDisplayName(String name);
}
