 package org.gk.examples;

import java.util.*;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.persistence.*;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.Schema;

 public class SkypainterDbExample {

	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.err.println("java InstanceFetchingExample host database user password port use_neo4j");
			System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
			System.exit(0);
		}
		
		// Connect to the "main" db
		PersistenceAdaptor dba = null;
		boolean useNeo4J = Boolean.parseBoolean(args[5]);
		if (useNeo4J)
			dba = new Neo4JAdaptor(
					args[0],
					args[1],
					args[2],
					args[3],
					Integer.parseInt(args[4]));
		else
			dba = new MySQLAdaptor(
					args[0],
					args[1],
					args[2],
					args[3],
					Integer.parseInt(args[4]));

		/* IMPORTANT!!!
		 * skypainter db should be named as <MAIN DB NAME>_dn, i.e. if your main db
		 * is test_reactome_14, the skypainter db should be called have test_reactome_14_dn.
		 */
		PersistenceAdaptor dba_dn = null;
		if (useNeo4J)
			dba = new Neo4JAdaptor(
					args[0],
					args[1] + "_dn",
					args[2],
					args[3],
					Integer.parseInt(args[4]));
		else
			dba_dn = new MySQLAdaptor(
							args[0],
							args[1] + "_dn",
							args[2],
							args[3],
							Integer.parseInt(args[4]));


		
		/*
		 * Fetch instances of class "Pathway" having attribute "name" value equal to "Apoptosis"
		 * and attribute "taxon" containing and instance of class "Species" with attribute "name"
		 * equal to "Homo sapiens".
		 */
		System.out.println("\nHuman pathway(s) with name 'Apoptosis':");
		// Construct the query
		Schema schema = dba.getSchema();
		List<QueryRequest> query = new ArrayList();
		query.add(new AttributeQueryRequest(schema, "Pathway","name","=","Apoptosis"));
		QueryRequestList subquery = new QueryRequestList();
		subquery.add(new AttributeQueryRequest(schema, "Species","name","=","Homo sapiens"));
		query.add(new AttributeQueryRequest(schema, "Pathway","taxon","=",subquery));
		// Execute the query
		Set pathways2 = new HashSet(dba.fetchInstance(query));
		// Loop over results
		for (Iterator i = pathways2.iterator(); i.hasNext();) {
			GKInstance pathway = (GKInstance) i.next();
			System.out.println(displayNameWithSpecies(pathway));
			
			/*
			 * Find the "terminal" events, i.e. thise not containing or generalising
			 * other Events.
			 * To find those we 1st specify intructions for getting from a given instance to all components
			 * of the pathway and then "grep" for the terminal Events
			 */
			List instructions1 = new ArrayList();
			instructions1.add(new ClassAttributeFollowingInstruction("Pathway", new String[]{"hasComponent"}, new String[]{}));
			instructions1.add(new ClassAttributeFollowingInstruction("GenericEvent", new String[]{"hasInstance"}, new String[]{}));
			Collection terminalEvents = grepTerminalEvents(InstanceUtilities.followInstanceAttributes(pathway,instructions1).values());
			// Stick their DB_IDs into a list
			List db_ids = new ArrayList();
			for (Iterator pi =  terminalEvents.iterator(); pi.hasNext();) {
				GKInstance r = (GKInstance) pi.next();
				db_ids.add(r.getDBID());
			}
			/* 
			 * Use the DB_ID list to fetch Reaction instances from the skypainter db (mind you, this contains just the
			 * indirectIdentifiers, displayName and sky coordinates)
			 */ 
			Collection reactions_w_indirectIdentifiers = dba_dn.fetchInstanceByAttribute("Reaction","DB_ID","=",db_ids);
			// Load the indirectIdentifier values in one go to save some time.
			dba_dn.loadInstanceAttributeValues(reactions_w_indirectIdentifiers, new String[]{"indirectIdentifier"});
			/*
			 * Loop over the "real" Reaction instances and get their skypainter db counterparts
			 * from dba_dn. Since the instances are cached by dba fetching them by DB_ID won't
			 * result in new sql being issued (unless teh instance with given DB_ID isn't found in cache).
			 */
			for (Iterator pi =  terminalEvents.iterator(); pi.hasNext();) {
				GKInstance r = (GKInstance) pi.next();
				GKInstance r2 = dba_dn.fetchInstance(r.getDBID());
				if ((r2 != null) && (r2.getAttributeValuesList("indirectIdentifier") != null)) {
					// Print out the Reaction name and everything in the indirectIdentifier slot
					System.out.println(displayNameWithSpecies(r) + "\t" + r2.getAttributeValuesList("indirectIdentifier"));
				}
			}
		}
	}
	
	public static String displayNameWithSpecies (GKInstance instance) throws InvalidAttributeException, Exception {
		if (instance.getSchemClass().isValidAttribute("taxon")) {
			return instance + " [" + ((GKInstance) instance.getAttributeValue("taxon")).getAttributeValue("name") + "]";
		} else if (instance.getSchemClass().isValidAttribute("species")) {
			return instance + " [" + ((GKInstance) instance.getAttributeValue("species")).getAttributeValue("name") + "]";
		} else {
			return instance.toString();
		}
	}
	
	public static Collection grepTerminalEvents (Collection events) throws InvalidAttributeException, Exception {
		Collection out = new ArrayList();
		for (Iterator i = events.iterator(); i.hasNext();) {
			GKInstance event = (GKInstance) i.next();
			if (event.getSchemClass().isa("ConcreteReaction")) {
				out.add(event);
				continue;
			} else if (event.getSchemClass().isa("GenericReaction") && (event.getAttributeValue("hasInstance") == null)) {
				out.add(event);
				continue;
			} else if (event.getSchemClass().isa("ConcretePathway") && (event.getAttributeValue("hasComponent") == null)) {
				out.add(event);
				continue;
			} else if (event.getSchemClass().isa("GenericPathway") 
					  && (event.getAttributeValue("hasInstance") == null)
					  && (event.getAttributeValue("hasComponent") == null)) {
				out.add(event);
				continue;
			} 
		}
		return out;
		
	}
}
