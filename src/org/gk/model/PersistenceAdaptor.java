/*
 * Created on Nov 11, 2003
 */
package org.gk.model;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.gk.persistence.QueryRequest;
import org.gk.schema.Schema;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.neo4j.driver.Transaction;

/**
 * An interface to connect to a persistence layer to load the necessary 
 * information.
 * @author wugm
 */
public interface PersistenceAdaptor {

	public Collection fetchInstanceByAttribute(SchemaAttribute attribute, 
	                                           String operator,
	                                           Object value) throws Exception;
	                                           
	public Collection fetchInstanceByAttribute(String className,
							                   String attributeName,
							                   String operator,
							                   Object value) throws Exception;
	
	public Collection fetchInstancesByClass(SchemaClass class1) throws Exception;

	public Collection fetchInstancesByClass(String className) throws Exception;

	public Collection fetchInstances(String className, List<Long> dbIds) throws Exception;

	public void loadInstanceAttributeValues(GKInstance instance,
											SchemaAttribute attribute, Boolean recursive) throws Exception;

	public void loadInstanceAttributeValues(GKInstance instance,
	                                        SchemaAttribute attribute) throws Exception;

	public void loadInstanceAttributeValues(Collection instances) throws Exception;

	public void loadInstanceAttributeValues(Collection instances, SchemaAttribute attribute) throws Exception;

	public void loadInstanceAttributeValues(Collection instances, Collection attributes) throws Exception;

	public void loadInstanceAttributeValues(Collection instances, String[] attNames) throws Exception;

	public void loadInstanceReverseAttributeValues(Collection instances, String[] attNames) throws Exception;

	public void updateInstanceAttribute(GKInstance instance, String attributeName, Transaction tx) throws Exception;

	public Long storeInstance(GKInstance instance, Transaction tx) throws Exception;

	public Schema getSchema();	   
	
	public Schema fetchSchema() throws Exception;    
	
	public long getClassInstanceCount(SchemaClass schemaClass) throws Exception;                                    

	public GKInstance fetchInstance(String className, Long dbID) throws Exception;
	
	public GKInstance fetchInstance(Long reactomeId) throws Exception;

	public Collection fetchInstance(QueryRequest aqr) throws Exception;

	public Set fetchInstance(List<QueryRequest> aqrList) throws Exception;

	public Set fetchIdenticalInstances(GKInstance instance) throws Exception;

	public boolean exist(List dbIds) throws Exception;

	public boolean exist(Long dbID) throws NotImplementedException;

	public String getDBName();

	public String getDBHost();

	public String getDBUser();

	public String getDBPwd();

	public void setUseCache(boolean useInstanceCache);

	public void refreshCaches() throws NotImplementedException;

	public void txDeleteInstance(GKInstance instance) throws Exception;

	public void txStoreOrUpdate(Collection instances) throws Exception;

	public long fetchMaxDbId() throws NotImplementedException;

	public Long txStoreInstance(GKInstance instance) throws Exception;

	public void txUpdateInstanceAttribute (GKInstance instance, String attributeName) throws Exception;

	public void cleanUp() throws Exception;

	public Integer getReleaseNumber() throws Exception;

	public Map getAllInstanceCounts() throws Exception;
}
