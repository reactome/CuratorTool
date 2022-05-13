package org.gk.scripts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.gk.database.DefaultInstanceEditHelper;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.Neo4JAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.Schema;
import org.gk.util.GKApplicationUtilities;
import org.neo4j.driver.*;

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
 *
 * @author sshorser
 */
public class ClearDuplicatedOldIdentifiers {
    private static final String OLD_IDENTIFIER = "oldIdentifier";
    private static final String OLD_IDENTIFIER_VERSION = "oldIdentifierVersion";

    public static void main(String[] args) throws SQLException {
        System.out.println("de-duplicating oldIdentifiers...");
        String host = args[0];
        String database = args[1];
        String username = args[2];
        String password = args[3];
        int port = Integer.valueOf(args[4]);
        String firstName = args[5];
        String lastName = args[6];
        Neo4JAdaptor adapter = new Neo4JAdaptor(host, database, username, password, port);
        DefaultInstanceEditHelper helper = new DefaultInstanceEditHelper();
        GKInstance person = null;

        try {
            List<Neo4JAdaptor.QueryRequest> query = new ArrayList();
            query.add(adapter.createAttributeQueryRequest("Person", "firstname", "=", firstName));
            query.add(adapter.createAttributeQueryRequest("Person", "surname", "=", lastName));
            Collection instances = adapter.fetchInstance(query);
            if (instances.iterator().hasNext()) {
                person = (GKInstance) instances.iterator().next();
            }
            if (person == null) {
                throw new Error("No person could be found! Check your search criteria and try again.");
            }

            GKInstance instanceEdit = helper.createDefaultInstanceEdit(person);
            instanceEdit.setAttributeValue(ReactomeJavaConstants.note, "oldIdentifier de-duplication");
            instanceEdit.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
            String displayName = InstanceDisplayNameGenerator.generateDisplayName(instanceEdit);
            instanceEdit.setAttributeValue(ReactomeJavaConstants._displayName, displayName);
            adapter.txStoreInstance(instanceEdit);

            for (Map<String, Object> stableId : adapter.fetchStableIdentifiersWithDuplicateDBIds()) {
                Long dbId = Long.parseLong(stableId.get(Schema.DB_ID_NAME).toString());
                String identifier = stableId.get("identifier").toString();
                String oldIdentifier = stableId.get(OLD_IDENTIFIER).toString();
                try {
                    GKInstance stableIdentifierInstance = adapter.fetchInstance(Long.valueOf(dbId));
                    System.out.println("StableIdentifier db_id: " + dbId + " identifier: " +
                            identifier + " oldIdentifier: " +
                            oldIdentifier);
                    // clear the value in the oldIdentifier field. I wonder why oldIdentifier isn't in ReactomeJavaConstants?
                    setOldIdentifier(adapter, instanceEdit, dbId, null, stableIdentifierInstance);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // And there are these 3 duplicated oldIdentifiers that Joel said should not stay as NULL but the one of the instances' oldIdentifier should be
            // set to another, *specific* oldIdentifier value, because they are used by DOIs.
            // I could have excluded these from the list above and then only set the wrong instance to had a NULL oldIdentifier,
            // but it seemed easier to let both records get set to NULL and then restore the correct one.
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
        } catch (InvalidAttributeException e1) {
            e1.printStackTrace();
            throw new Error(e1);
        } catch (InvalidAttributeValueException e1) {
            e1.printStackTrace();
            throw new Error(e1);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error(e);
        }

        try {
            adapter.cleanUp();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error(e);
        }
        System.out.println("FINISHED!");
    }


    /**
     * Overload that sets oldIdentifierVersion to NULL.
     *
     * @param adapter
     * @param instanceEdit
     * @param db_id
     * @param oldIdentifierToUse
     * @param inst
     * @throws InvalidAttributeException
     * @throws InvalidAttributeValueException
     * @throws Exception
     */
    private static void setOldIdentifier(Neo4JAdaptor adapter, GKInstance instanceEdit, Long db_id, String oldIdentifierToUse, GKInstance inst)
            throws InvalidAttributeException, InvalidAttributeValueException, Exception {
        setOldIdentifier(adapter, instanceEdit, db_id, oldIdentifierToUse, null, inst);
    }

    /**
     * set the value of the oldIdentifier attribute.
     *
     * @param adapter                   - the DB Adapter to use.
     * @param instanceEdit              - The InstanceEdit to associate this modification with.
     * @param db_id                     - The DB_ID of the instance to modify (this is really only used for logging).
     * @param oldIdentifierToUse        - The value to set on oldIdentifier.
     * @param oldIdentifierVersionToUse - The value to set on oldIdentifierVersion
     * @param inst                      - The instance.
     * @throws InvalidAttributeException
     * @throws InvalidAttributeValueException
     * @throws Exception
     */
    private static void setOldIdentifier(Neo4JAdaptor adapter, GKInstance instanceEdit, Long db_id, String oldIdentifierToUse, String oldIdentifierVersionToUse, GKInstance inst)
            throws InvalidAttributeException, InvalidAttributeValueException, Exception {
        //Set the oldIdentifier and oldIdentifierVersion
        inst.setAttributeValue(OLD_IDENTIFIER_VERSION, oldIdentifierVersionToUse);
        inst.setAttributeValue(OLD_IDENTIFIER, oldIdentifierToUse);
        //Load instance edit history into memory first, so the "addAttributeValue" call doesn't wipe out history.
        inst.getAttributeValuesList(ReactomeJavaConstants.modified);
        inst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
        Driver driver = adapter.getConnection();
        try (Session session = driver.session(SessionConfig.forDatabase(adapter.getDBName()))) {
            Transaction tx = session.beginTransaction();
            adapter.updateInstanceAttribute(inst, OLD_IDENTIFIER_VERSION, tx);
            adapter.updateInstanceAttribute(inst, OLD_IDENTIFIER, tx);
            adapter.updateInstanceAttribute(inst, ReactomeJavaConstants.modified, tx);
            tx.commit();
            System.out.println("updated " + db_id);
        }
    }
}
