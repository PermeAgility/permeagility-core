/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.util;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.security.OSecurity;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/** This abstracts the native database connections a bit and give us somewhere to put some helper functions */
public class DatabaseConnection {

	public static boolean DEBUG = false;
	
	Database db = null;
	ODatabaseDocumentTx c = null;
	long lastAccess = System.currentTimeMillis();
	
	private static ConcurrentHashMap<String,Long> tableCountCache = new ConcurrentHashMap<String,Long>();
	
	protected DatabaseConnection(Database _db, ODatabaseDocumentTx _c) {
		db = _db;
		c = _c;
		if (DEBUG) System.out.println("New database connection initiated "+c.getName());
	}
	
	protected void close() {
			c.close();
			c = null;
	}
	
	public boolean isConnected() {
		return c != null;
	}
	
	/** Verification of password when changing password */
	public boolean isPassword(String pass) {
		return db.isPassword(pass);
	}

	/** Should only be called when the password is changed by the user (this does not actually change the password) */
	public void setPassword(String pass) {
		db.setPassword(pass);
	}
	
	/** Return the user name */
	public String getUser() {
		return db.getUser();
	}
	
	/** Return the user's locale */
	public Locale getLocale() {
		return db.getLocale();
	}
	
	/** Allows overriding the locale for the connection. This is for supporting non-users changing the language for the request */
	public void setLocale(Locale l) {
		db.setLocale(l);
	}
	
	/** Get the native connection object. A ODatabaseDocumentTx */ 
	public ODatabaseDocumentTx getDb() {
		return c;
	}

	/** Get the OrientDB OSchema object */
	public OSchema getSchema() {
		if (c == null) {
			return null;
		} else {
			return c.getMetadata().getSchema();
		}
	}

	/** Get the OrientDB OSecurity object */
	public OSecurity getSecurity() {
		if (c == null) {
			return null;
		} else {
			return c.getMetadata().getSecurity();
		}
	}
	
	/** Create a document - supply the classname */
	public ODocument create(String className) {
		return c.newInstance(className);
	}

	public void begin() {
		c.begin();
	}

	public void commit() {
		c.commit();
	}
	
	public void rollback() {
		c.rollback();
	}

	/** Returns total number of rows in table from cache if found */
	public long getRowCount(String table) {
		// Find in cache?
		Long count = tableCountCache.get(table);
		if (count != null) {
			//System.out.println("Row count cache hit on table "+table);
			return count.longValue();
		}
		//System.out.println("Counting rows of "+table);
		try {
			long cnt = c.countClass(table);
			tableCountCache.put(table, new Long(cnt));
			return cnt;
		} catch (Exception e) {
			System.out.println("Error counting class "+table);
			return -1;
		}
	}
	
	/** Called when the table is changed to clear the rowcount cache */
	public static void rowCountChanged(String table) {
		tableCountCache.remove(table);
	}

	/** Called when the database is restored */
	public static void clearRowCounts() {
		tableCountCache.clear();
	}

	/** Change the user's password - must supply old and new */
	public boolean changePassword(String oldPassword, String newPassword) {
		if (!db.isPassword(oldPassword)) {
			System.out.println("Attempt to change password with incorrect old password");
			return false;
		}
		try {
			Object o = update("UPDATE OUser SET password='"+newPassword+"' WHERE name='"+getUser()+"'");
			System.out.println(getUser()+" password changed successfully "+o);
			db.setPassword(newPassword);
			return true;
		} catch (Exception e) {
			System.out.println("Cannot change password for user "+getUser());
			e.printStackTrace();
			return false;
		}
	}
	
	/** Execute a query and return a QueryResult object */
    public synchronized QueryResult query(String expression) {
    	if (DEBUG) System.out.println("DatabaseConnection.DEBUG(query)="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
    	List<ODocument> result = c.query(new OSQLSynchQuery<ODocument>(expression));
    	return new QueryResult(result);
    }

    /** Get the first document found by this query */
    public synchronized ODocument queryDocument(String expression) {
    	if (DEBUG) System.out.println("DatabaseConnection.DEBUG(queryDocument)="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
    	List<ODocument> result = c.query(new OSQLSynchQuery<ODocument>(expression));
    	if (result != null && result.size() > 0) {
    		return result.get(0);
    	} else {
    		return null;
    	}
    }

    /** Execute and update statement */
    public synchronized Object update(String expression) {
        if (DEBUG) System.out.println("DatabaseConnection.DEBUG(update)="+expression+";");
       	lastAccess = System.currentTimeMillis();
        return c.command(new OCommandSQL(expression)).execute();    // run the query return the resulting object
    }

    /** Get a document by its RID */
    public synchronized ODocument get(String rid) {
       	lastAccess = System.currentTimeMillis();
       	return c.getRecord(new ORecordId(rid));
    }
    
    /** Not used very often if at all. Dumps the contents of a result set to System.out */
    public static void dump(QueryResult rows) {
    	if (rows.size() > 0) {
    		for (String header : rows.get(0).fieldNames()) {
    			System.out.print(header+"\t");
    		}
	    	System.out.println("");
	    	for (ODocument row : rows.result) {
	    		for (Object data : row.fieldValues()) {
	    			System.out.print(""+data+"\t");
	    		}
	    	}
	    	System.out.println("");
    	} else {
    		System.out.println("No rows");
    	}
    }

    /** FLush the local cache */
	public void flush() {
		c.getLocalCache().invalidate();		
	}
 
}
