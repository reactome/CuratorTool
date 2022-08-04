/*
 * Created on Jan 12, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package org.gk.persistence;

import org.gk.model.PersistenceAdaptor;

/**
 * @author vastrik
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class TransactionsNotSupportedException extends Exception {

	/**
	 * @param dba PersistenceAdaptor object connected to the database that does not support transactions
	 */
	public TransactionsNotSupportedException(PersistenceAdaptor dba) {
		super("Database " + dba.getDBName() + "@" + dba.getDBHost() + " does not support transactions");
	}


}
