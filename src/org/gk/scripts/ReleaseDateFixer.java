package org.gk.scripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.function.Consumer;

import org.gk.database.DefaultInstanceEditHelper;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.util.GKApplicationUtilities;


public class ReleaseDateFixer
{
	public static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore) throws Exception
	{
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		DefaultInstanceEditHelper ieHelper = new DefaultInstanceEditHelper();
		GKInstance newIE = ieHelper.createDefaultInstanceEdit(defaultPerson);
		SchemaAttribute dateTimeAttribute = dba.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit).getAttribute(ReactomeJavaConstants.dateTime);
		newIE.addAttributeValue(dateTimeAttribute, GKApplicationUtilities.getDateTime());
		SchemaAttribute noteAttribute = dba.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit).getAttribute(ReactomeJavaConstants.note);
		newIE.setAttributeValue(noteAttribute, "Update missing releaseDate");
		InstanceDisplayNameGenerator.setDisplayName(newIE);
		if (needStore)
		{
			if (dba.supportsTransactions())
			{
				dba.txStoreInstance(newIE);
			}
			else
			{
				dba.storeInstance(newIE);
			}
		}
		System.out.println("  new instance edit with DB ID = " + newIE.getDBID());
		return dba.fetchInstance(newIE.getDBID());
	}

	/**
	 * Fixes release dates for Reactome Events. <br/>
	 * The main input is simple_list.txt where each line is of the form:<br/>
	 * <code>[DB_ID],[release_date]</code> <br/>
	 * for example:<br/> 123456543,2016-11-07 <br/>
	 * The command-line arguments are taken in this sequence: <ol>
	 * <li>The name of the input file. </li>
	 * <li>The ID of the Person to associate these updates with </li>
	 * <li>The database host </li>
	 * <li>The name of the database </li>
	 * <li>The database username</li>
	 * <li>The password for the database</li>
	 * <li>The port number for the database connection.</li>
	 * </ol>
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		String inputFileName = args[0];
		Long personID = Long.valueOf(args[1]);
		String dbHost = args[2];
		String dbName = args[3];
		String dbUser = args[4];
		String dbPassword = args[5];
		int dbPort = Integer.valueOf(args[6]);

		MySQLAdaptor adapter = new MySQLAdaptor(dbHost, dbName, dbUser, dbPassword, dbPort);
		// The entire script will run inside a single transaction.
		adapter.startTransaction();
		//adapter.debug = true;
		
		// The object that will process lines from the input file.
		Consumer<String> processLine = line ->
		{
			
			String parts[] = line.split(",");
			Long dbID = Long.valueOf(parts[0]);
			String releaseDate = parts[1];
			System.out.println("Preparing to work on object: "+dbID);
			try
			{

				GKInstance mainInstance = adapter.fetchInstance(dbID);
				if (mainInstance != null)
				{
					GKInstance instanceEdit = createDefaultIE(adapter, personID, true);
					//adapter.loadInstanceAttributeValues(mainInstance);
					// Do a check to see if it is there already just in case
//						String preexistingReleaseDate = (String) mainInstance.getAttributeValue(mainInstance.getSchemClass().getAttribute(ReactomeJavaConstants.releaseDate));
//						if (preexistingReleaseDate != null)
//						{
//							System.out.println("A Pre-existing release date was found, with a value of: " + preexistingReleaseDate);
//						}

					// Set/add the attributes for releaseDAte and modified
					mainInstance.setAttributeValue(ReactomeJavaConstants.releaseDate, releaseDate);
					mainInstance.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
					
					System.out.println("  Updating event with ID: " + dbID + " to have releaseDate: " + releaseDate);
					
					// Update the attributes. 
					adapter.updateInstanceAttribute(mainInstance, ReactomeJavaConstants.releaseDate);
					adapter.updateInstanceAttribute(mainInstance, ReactomeJavaConstants.modified);
				}
				else
				{
					// This could happen if something is in the Release databases, but has since been deleted from Curator (gk_central)
					System.out.println("  Could not retrieve an object for DB_ID: "+dbID);
				}
			}
			catch (Exception e)
			{
				try
				{
					// *Try* to roll back if anything went wrong.
					adapter.rollback();
				}
				catch (SQLException e1)
				{
					System.out.println("Had trouble rolling back: " + e.getMessage());
					throw new RuntimeException(e1);
				}
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			finally
			{
				try
				{
					adapter.cleanUp();
				}
				catch (Exception e)
				{
					System.out.println("Had trouble cleaning up: " + e.getMessage());
					throw new RuntimeException(e);
				}
			}
		};

		Files.readAllLines(Paths.get(inputFileName)).stream().forEach(line -> processLine.accept(line));
		
		// Commit the whole transaction: all of the new InstanceEdits and all of the updated attributes and events will be in the same transaction.
		adapter.commit();
		adapter.cleanUp();
	}
}
