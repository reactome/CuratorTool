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
	 * <li>The ID of the Person to associate these updates with </li>
	 * <li>the database host </li>
	 * <li>the name of the database </li>
	 * <li>the database username</li>
	 * <li>the password for the database</li>
	 * <li>the port number for the database connection.</li>
	 * </ol>
	 * @param args
	 */
	public static void main(String[] args)
	{
		Long personID = Long.valueOf(args[0]);
		String dbHost = args[1];
		String dbName = args[2];
		String dbUser = args[3];
		String dbPassword = args[4];
		int dbPort = Integer.valueOf(args[5]);

		try
		{
			MySQLAdaptor adapter = new MySQLAdaptor(dbHost, dbName, dbUser, dbPassword, dbPort);
			//adapter.debug = true;
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
						adapter.loadInstanceAttributeValues(instanceEdit);
						mainInstance.setAttributeValue(mainInstance.getSchemClass().getAttribute(ReactomeJavaConstants.releaseDate), releaseDate);
						SchemaAttribute editedAttribute = mainInstance.getSchemClass().getAttribute(ReactomeJavaConstants.edited);
						mainInstance.addAttributeValue(editedAttribute, instanceEdit);
						
						System.out.println("  Updating event with ID: " + dbID + " to have releaseDate: " + releaseDate);
						if (adapter.supportsTransactions())
						{
							adapter.txUpdateInstance(mainInstance);
						}
						else
						{
							adapter.updateInstance(mainInstance);
						}
					}
					else
					{
						System.out.println("  Could not retrieve an object for DB_ID: "+dbID);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			};
			// TODO: Parameterize the name/path of the input file.
			Files.readAllLines(Paths.get("simple_list.txt")).stream().forEach(line -> processLine.accept(line));
			adapter.cleanUp();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
