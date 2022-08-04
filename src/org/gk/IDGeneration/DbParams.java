/*
 * Created on June 6, 2011
 */
package org.gk.IDGeneration;
import org.gk.model.PersistenceAdaptor;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Neo4JAdaptor;

/** 
 * Lightweight class for holding parameters for various
 * databases.
 *  
 * @author croft
 */
public class DbParams  {
	public String dbName = "";
	public String hostname = "";
	public String username = "";
	public String port = "";
	public String password = "";
	
	public PersistenceAdaptor getDba(boolean useNeo4j) {
		PersistenceAdaptor dba = null;
		
		if (dbName == null) {
			System.err.println("IDGenerationCommandLine.DbParams.getDba: dbName == null. aborting!");
			return dba;			
		}
		if (dbName.isEmpty()) {
			System.err.println("IDGenerationCommandLine.DbParams.getDba: dbName is empty aborting!");
			return dba;			
		}
		
		try {
			if (useNeo4j) {
				dba = new Neo4JAdaptor(hostname, dbName, username, password, Integer.parseInt(port));
			} else {
				dba = new MySQLAdaptor(hostname, dbName, username, password, Integer.parseInt(port));
			}
		} catch (NumberFormatException e) {
			System.err.println("IDGenerationCommandLine.DbParams.getDba: port number is strange: " + port);
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("IDGenerationCommandLine.DbParams.getDba: something went wrong");
			e.printStackTrace();
		}
		
		return dba;
	}
}