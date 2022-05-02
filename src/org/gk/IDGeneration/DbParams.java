/*
 * Created on June 6, 2011
 */
package org.gk.IDGeneration;
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
	
	public Neo4JAdaptor getDba() {
		Neo4JAdaptor dba = null;
		
		if (dbName == null) {
			System.err.println("IDGenerationCommandLine.DbParams.getDba: dbName == null. aborting!");
			return dba;			
		}
		if (dbName.isEmpty()) {
			System.err.println("IDGenerationCommandLine.DbParams.getDba: dbName is empty aborting!");
			return dba;			
		}
		
		try {
			dba = new Neo4JAdaptor(hostname, dbName, username, password, Integer.parseInt(port));
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