package org.gk.scripts;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.gk.database.DefaultInstanceEditHelper;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.util.GKApplicationUtilities;

/**
 * This tool will find all StableIdentifiers such that there is more than 1 record with the same oldIdentifier. <br/>
 * It will then set the oldIdentifier to NULL for each of these records.<br/>
 * An InstanceEdit will be created with the node "oldIdentifier de-duplication", all of these changes
 * will be associated with this InstanceEdit.<br/>
 * NOTE: There are three StableIdentifiers which will not be left as NULL but will actually be corrected:
 * <ul>
 * <li>6781524 will have oldIdentifier = REACT_267785</li>
 * <li>6781535 will have oldIdentifier = REACT_267790</li>
 * <li>6781519 will have oldIdentifier = REACT_267875</li>
 * </ul>
 * @author sshorser
 *
 */
public class ClearDuplicatedOldIdentifiers
{
	private static final String OLD_IDENTIFIER = "oldIdentifier";

	public static void main(String[] args) throws SQLException
	{
		System.out.println("de-duplicating oldIdentifiers...");
		String host = args[0];
		String database = args[1];
		String username = args[2];
		String password = args[3];
		int port = Integer.valueOf(args[4]);
		String firstName = args[5];
		String lastName = args[6];
		MySQLAdaptor adapter = new MySQLAdaptor(host, database, username, password, port);

		DefaultInstanceEditHelper helper = new DefaultInstanceEditHelper();
		ResultSet personResults = adapter.executeQuery("select * from Person where Person.firstname = '"+firstName+"' and Person.surname = '"+lastName+"';", null);
		GKInstance person = null;
		
		// There should only ever be one record, but resultSet is still something that needs to be iterated over....
		while (personResults.next())
		{
			try
			{
				person = adapter.fetchInstance(personResults.getLong("db_id"));
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (person == null)
		{
			throw new Error("No person could be found! Check your search criteria and try again.");
		}
		GKInstance instanceEdit = helper.createDefaultInstanceEdit(person);
		
		try
		{
			instanceEdit.setAttributeValue(ReactomeJavaConstants.note, "oldIdentifier de-duplication");
			instanceEdit.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
			adapter.storeInstance(instanceEdit);
		}
		catch (InvalidAttributeException e1)
		{
			e1.printStackTrace();
			throw new Error(e1);
		}
		catch (InvalidAttributeValueException e1)
		{
			e1.printStackTrace();
			throw new Error(e1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		
		String queryString = "select StableIdentifier.* "
							+ " from StableIdentifier "
							+" inner join ( "
							+	" select count(db_id), StableIdentifier.oldIdentifier "
							+	" from StableIdentifier "
							+	" where oldIdentifier is not null "
							+	" group by StableIdentifier.oldIdentifier "
							+	" having count(db_id) > 1) as subq "
							+ " on subq.oldIdentifier = StableIdentifier.oldIdentifier "
							+ " order by StableIdentifier.oldIdentifier, StableIdentifier.identifier;";
		ResultSet results = adapter.executeQuery(queryString, null);
		while (results.next())
		{
			Long db_id = results.getLong("db_id");
			try
			{
				GKInstance stableIdentifierInstance = adapter.fetchInstance(Long.valueOf(db_id));
				System.out.println("StableIdentifier db_id: " + db_id + " identifier: " + results.getString("identifier") + " oldIdentifier: " + results.getString(OLD_IDENTIFIER));
				// clear the value in the oldIdentifier field. I wonder why oldIdentifier isn't in ReactomeJavaConstants?				
				setOldIdentifier(adapter, instanceEdit, db_id, null, stableIdentifierInstance);
			}
			catch (NumberFormatException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		// And there are these 3 duplicated oldIdentifiers that Joel said should not stay as NULL but the one of the instances' oldIdentifier should be 
		// set to another, *specific* oldIdentifier value, because they are used by DOIs.
		// I could have excluded these from the list above and then only set the wrong instance to had a NULL oldIdentifier,
		// but it seemed easier to let both records get set to NULL and then restore the correct one.
		try
		{
			Long db_id = 6781524L;
			String oldIdentifierToUse = "REACT_267785"; 
			GKInstance inst = adapter.fetchInstance(db_id);
			setOldIdentifier(adapter, instanceEdit, db_id, oldIdentifierToUse, inst);
			
			db_id = 6781535L;
			GKInstance inst2 = adapter.fetchInstance(db_id);
			oldIdentifierToUse = "REACT_267790";
			setOldIdentifier(adapter, instanceEdit, db_id, oldIdentifierToUse, inst2);
			
			db_id = 6781519L;
			GKInstance inst3 = adapter.fetchInstance(db_id);
			oldIdentifierToUse = "REACT_267875";
			setOldIdentifier(adapter, instanceEdit, db_id, oldIdentifierToUse, inst3);

		}
		catch (Exception e1)
		{
			e1.printStackTrace();
			throw new Error(e1);
		}
		
		
		try
		{
			adapter.cleanUp();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error(e);
		}
		System.out.println("FINISHED!");
	}

	/**
	 * set the value of the oldIdentifier attribute.
	 * @param adapter - the DB Adapter to use.
	 * @param instanceEdit - The InstanceEdit to associate this modification with.
	 * @param db_id - The DB_ID of the instance to modify (this is really only used for logging).
	 * @param oldIdentifierToUse - The value to set on oldIdentifier.
	 * @param inst - The instance.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private static void setOldIdentifier(MySQLAdaptor adapter, GKInstance instanceEdit, Long db_id, String oldIdentifierToUse, GKInstance inst)
			throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		inst.setAttributeValue(OLD_IDENTIFIER, oldIdentifierToUse);
		inst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
		adapter.updateInstanceAttribute(inst, OLD_IDENTIFIER);
		adapter.updateInstanceAttribute(inst, ReactomeJavaConstants.modified);
		System.out.println("updated "+db_id);
	}
}
