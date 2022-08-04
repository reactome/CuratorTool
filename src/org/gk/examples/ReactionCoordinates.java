 package org.gk.examples;

import java.util.*;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.persistence.*;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.Schema;

 public class ReactionCoordinates {

	/*
	 * Please note that this script will not run in release 15 db.
	 * Comment/remove references to GenericEvent.hasInstance and it should be fine though.
	 */
	
	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.err.println("java ReactionCoordinates host database user password port use_neo4j");
			System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
			System.exit(0);
		}
		
		// Connect to the "main" db
		PersistenceAdaptor dba;
		boolean useNeo4J = Boolean.parseBoolean(args[6]);
		if (useNeo4J) {
			dba =
					new Neo4JAdaptor(
							args[0],
							args[1],
							args[2],
							args[3],
							Integer.parseInt(args[4]));
		} else {
			dba =
					new MySQLAdaptor(
							args[0],
							args[1],
							args[2],
							args[3],
							Integer.parseInt(args[4]));
		}
		
		/*
		 * Fetch instances of class "Pathway" having attribute "name" value equal to "Apoptosis"
		 * and attribute "taxon" containing and instance of class "Species" with attribute "name"
		 * equal to "Homo sapiens".
		 */
		System.out.println("\nHuman pathway(s) with name 'Apoptosis':");
		// Construct the query
		List<QueryRequest> query = new ArrayList();
		Schema schema = dba.getSchema();
		query.add(new AttributeQueryRequest(schema, "Pathway","name","=","Apoptosis"));
		QueryRequestList subquery = new QueryRequestList();
		subquery.add(new AttributeQueryRequest(schema, "Species","name","=","Homo sapiens"));
		query.add(new AttributeQueryRequest(schema, "Pathway","taxon","=",subquery));
		// Execute the query
		Set pathways2 = new HashSet(dba.fetchInstance(query));
		// Loop over results
		for (Iterator i = pathways2.iterator(); i.hasNext();) {
			GKInstance pathway = (GKInstance) i.next();
			
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

			for (Iterator pi =  terminalEvents.iterator(); pi.hasNext();) {
				GKInstance r = (GKInstance) pi.next();
				// Get the ReactionCoordinates instance (there should be just one of them for the tim ebeing)
				Collection rc = r.getReferers("locatedEvent");
				if (rc != null) {
					System.out.println(r + "\t" + coordinatesAsString((GKInstance) rc.iterator().next()));
				}
			}
		}
	}
	
	public static String coordinatesAsString (GKInstance reactionCoordinates) throws Exception {
		return "[" + reactionCoordinates.getAttributeValue("sourceX") + "," +
		reactionCoordinates.getAttributeValue("sourceY") + "," +
		reactionCoordinates.getAttributeValue("targetX") + "," +
		reactionCoordinates.getAttributeValue("targetY") + "]";
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
