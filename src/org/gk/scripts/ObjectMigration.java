package org.gk.scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;

/**
 * Performs DatabaseObject migration between database.
 * Reads a DatabaseObject from one database, stores it in another.
 * @author sshorser
 *
 */
public class ObjectMigration {

	public static void main(String args[]) throws SQLException, IOException
	{
		String srcHost = args[0];
		String srcDatabase = args[1];
		String srcUsername = args[2];
		String srcPassword = args[3];
		int srcPort = Integer.valueOf(args[4]);
		MySQLAdaptor srcAdapter = new MySQLAdaptor(srcHost, srcDatabase, srcUsername, srcPassword, srcPort);
		
		String targHost = args[5];
		String targDatabase = args[6];
		String targUsername = args[7];
		String targPassword = args[8];
		int targPort =Integer.valueOf(args[9]);
		MySQLAdaptor targetAdapter = new MySQLAdaptor(targHost, targDatabase, targUsername, targPassword, targPort);
		
		// List of object IDs to copy.
		String fileName = args[10];

		System.out.println("Copying Objects from " + srcHost +": " + srcDatabase + " to "+ targHost +": " + targDatabase );
		
		Files.readAllLines(Paths.get(fileName)).stream().forEach(line -> {
			long dbID = Long.valueOf(line);
			
			//find the object in srcDatabase
			try {
				GKInstance srcObject = srcAdapter.fetchInstance(dbID);
				// load all of the attributes for the object.
				srcAdapter.loadInstanceAttributeValues(srcObject);
				
				// Now, we need to put this into target database.
				GKInstance targObject = targetAdapter.fetchInstance(dbID);
				
				// If the object isn't even in the target database, this is easy: just save it directly to target.
				if (targObject == null)
				{
					System.out.println("Object " + dbID + " was not in the target database so it will be inserted.");
					// srcObject.setDbAdaptor(targetAdapter);
					//targetAdapter.storeInstance(srcObject);
				}
				else
				{
					targetAdapter.startTransaction();
					targetAdapter.loadInstanceAttributeValues(targObject);
					// It would be nice to maybe hightlight some differences before clobbering the data in the target database.
					printObjectDiffs(srcObject, targObject);
					//System.out.println("Updating object "+dbID+" into targetDatabase");
					// srcObject.setDbAdaptor(targetAdapter);
					//targetAdapter.storeInstance(srcObject, true);
					//targetAdapter.updateInstance(srcObject);
					targetAdapter.rollback();
				}
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		});
	}
	
	private static void printObjectDiffs(GKInstance object1, GKInstance object2) throws Exception
	{
		Collection<SchemaAttribute> attributes1 = object1.getSchemaAttributes();
		Collection<SchemaAttribute> attributes2 = object2.getSchemaAttributes();
		
		if (attributes1.size() != attributes2.size())
		{
			System.out.println("objects differ by number of attributes: object1: " + attributes1.size()  + ";  object2: " + attributes2.size());
		}
		
		// Ok now we loop through the attributes of one and compare to the other.
		for (SchemaAttribute attrib : attributes1)
		{
			System.out.println("checking attribute "+attrib.getName() );
			
			List<Object> valuesList1 = null;
			try
			{
				valuesList1 = object1.getAttributeValuesList(attrib.getName());
				System.out.println("  AttributeValue for Object 1: " + valuesList1);
			}
			catch (InvalidAttributeException e)
			{
				System.out.println("  It would appear that the attribute \""+attrib.getName()+ "\" is not valid for object1");
			}
			
			List<Object> valuesList2 = null;
			try
			{
				valuesList2 = object2.getAttributeValuesList(attrib.getName());
				System.out.println("  AttributeValue for Object 2: " + valuesList2);
			}
			catch (InvalidAttributeException e)
			{
				System.out.println("  It would appear that the attribute \""+attrib.getName()+ "\" is not valid for object2");
			}
			
			if (valuesList1.size() != valuesList2.size())
			{
				System.out.println("  Mismatch on size of values lists: object 1: " + valuesList1.size()+ "  object 2: " +valuesList2.size());
			}
			else
			{
				if (valuesList1.size() > 1)
				{
					System.out.println("  multi-valued attribute matches on size on both sides: " +valuesList1.size());
				}
			}
		
				
			
			if (valuesList1 != null && valuesList2 != null)
			{
				
				for (int i = 0; i < Math.max(valuesList1.size(), valuesList2.size()); i++)
				{
					String v1 = null;
					String v2 = null;
					
					if (i < valuesList1.size())
					{
						v1 = valuesList1.get(i).toString();
					}

					if (i < valuesList2.size())
					{
						v2 = valuesList2.get(i).toString();
					}
					
					if (v1 !=null && v2 != null && v1.equals(v2))
					{
						System.out.println("    Match on " + attrib.getName() + "  :" + valuesList2.toString());
					}
					else
					{
						System.out.println("   MISMATCH!");
					}
					
				}
			}
		}
	}
}
