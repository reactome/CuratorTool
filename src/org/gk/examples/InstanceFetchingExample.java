package org.gk.examples;

import java.util.*;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.PersistenceAdaptor;
import org.gk.persistence.*;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.Schema;

public class InstanceFetchingExample {

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.err.println("java InstanceFetchingExample host database user password port use_neo4j");
            System.err.println("use_neo4j = true, connect to Neo4J DB; otherwise connect to MySQL");
            System.exit(0);
        }
        // Connect to db
        PersistenceAdaptor dba;
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


        // Fetch instances of class "Pathway" having attribute "name" value equal to "Apoptosis".
        System.out.println("Pathway(s) with name 'Apoptosis':");
        // Construct and execute query
        Collection pathways1 = dba.fetchInstanceByAttribute("Pathway", "name", "=", "Apoptosis");
        for (Iterator i = pathways1.iterator(); i.hasNext(); ) {
            GKInstance pathway = (GKInstance) i.next();
            System.out.println(displayNameWithSpecies(pathway));
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
        query.add(new AttributeQueryRequest(schema, "Pathway", "name", "=", "Apoptosis"));
        QueryRequestList subquery = new QueryRequestList();
        subquery.add(new AttributeQueryRequest(schema, "Species", "name", "=", "Homo sapiens"));
        query.add(new AttributeQueryRequest(schema, "Pathway", "taxon", "=", subquery));
        // Execute the query
        Set pathways2 = new HashSet(dba.fetchInstance(query));
        // Loop over results
        for (Iterator i = pathways2.iterator(); i.hasNext(); ) {
            GKInstance pathway = (GKInstance) i.next();
            System.out.println(displayNameWithSpecies(pathway));

            // Print out the names of values of the "HasComponent" attribute of this pathway.
            System.out.println("\nImmediate components of pathway " + pathway + ":");
            for (Iterator ci = pathway.getAttributeValuesList("hasComponent").iterator(); ci.hasNext(); ) {
                GKInstance component = (GKInstance) ci.next();
                System.out.println("\t" + displayNameWithSpecies(component));
            }

            /*
             * Print out the list of "terminal" events, i.e. thise not containing or generalising
             * other Events.
             * To find those we 1st specify intructions for getting from a given instance to all components
             * of the pathway and then "grep" for the terminal Events
             */
//			System.out.println("\n\"Terminal\" components of pathway " + pathway + ":");
//			List instructions1 = new ArrayList();
//			instructions1.add(new ClassAttributeFollowingInstruction("Pathway", new String[]{"hasComponent"}, new String[]{}));
//			instructions1.add(new ClassAttributeFollowingInstruction("GenericEvent", new String[]{"hasInstance"}, new String[]{}));
//			Collection terminalEvents = grepTerminalEvents(InstanceUtilities.followInstanceAttributes(pathway,instructions1).values());
//			for (Iterator pi =  terminalEvents.iterator(); pi.hasNext();) {
//				GKInstance pe = (GKInstance) pi.next();
//				System.out.println("\t" + pe);
//			}			

            /*
             * Print a list of all PhysicalEntities directly involved, i.e. consumed, produced or catalysing
             * teh component reactions of this Pathway.
             */
            System.out.println("\nPhysicalEntities involved in pathway " + pathway + ":");
            List instructions2 = new ArrayList();
            // Create instructions for getting from Pathway to PhysicalEntities
            instructions2.add(new ClassAttributeFollowingInstruction("Pathway", new String[]{"hasComponent"}, new String[]{}));
            //instructions2.add(new ClassAttributeFollowingInstruction("GenericEvent", new String[]{"hasInstance"}, new String[]{}));
            instructions2.add(new ClassAttributeFollowingInstruction("Reaction", new String[]{"input", "output", "catalystActivity"}, new String[]{}));
            instructions2.add(new ClassAttributeFollowingInstruction("CatalystActivity", new String[]{"physicalEntity"}, new String[]{}));
            // Get the all the instances which are encountered on the "route" specified by the instructions
            Map instances2 = InstanceUtilities.followInstanceAttributes(pathway, instructions2);
            // "Grep" out PhysicalEntities.
            Map physicalEntities = InstanceUtilities.grepSchemaClassInstances(instances2, new String[]{"PhysicalEntity"}, true);
            for (Iterator pi = physicalEntities.values().iterator(); pi.hasNext(); ) {
                GKInstance pe = (GKInstance) pi.next();
                System.out.println("\t" + pe);
            }
        }
    }

    public static String displayNameWithSpecies(GKInstance instance) throws InvalidAttributeException, Exception {
        if (instance.getSchemClass().isValidAttribute("taxon")) {
            return instance + " [" + ((GKInstance) instance.getAttributeValue("taxon")).getAttributeValue("name") + "]";
        } else if (instance.getSchemClass().isValidAttribute("species")) {
            return instance + " [" + ((GKInstance) instance.getAttributeValue("species")).getAttributeValue("name") + "]";
        } else {
            return instance.toString();
        }
    }

    public static Collection grepTerminalEvents(Collection events) throws InvalidAttributeException, Exception {
        Collection out = new ArrayList();
        for (Iterator i = events.iterator(); i.hasNext(); ) {
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
