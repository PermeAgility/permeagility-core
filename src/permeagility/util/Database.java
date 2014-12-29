/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.util;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

import permeagility.web.Server;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.ODatabase.STATUS;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.metadata.security.OSecurity;
import com.orientechnologies.orient.core.metadata.security.OUser;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class Database implements Serializable {

	private static final long serialVersionUID = -3694830690200123194L;  // So that we can serialize the sessions

	public int POOL_SIZE = 1;
	public int RETRY_LIMIT = 100;
	public int RETRY_SLEEP = 20;
	public double POOL_GROWTH_STEP = 5;  // When growing the pool, increase by this size
	public double POOL_GROWTH_FACTOR = 0.75; // When active connections reaches this portion of the MAX, increase the pool 
	public boolean ALLOW_POOL_GROWTH = true;   // Will increase size by if active guest connections > 75% of pool 

	private String url = null;
	private String user = null;
	private String password = null;
	private Date lastAccessed = null;
	private Locale locale = Locale.getDefault(); 

	private transient ConcurrentLinkedDeque<DatabaseConnection> pooledConnections =  new ConcurrentLinkedDeque<DatabaseConnection>(); 
	private transient ConcurrentLinkedDeque<DatabaseConnection> activeConnections = new ConcurrentLinkedDeque<DatabaseConnection>(); 	
	
	public Database(String dbUrl, String dbUser, String dbPass) throws Exception {
		url = dbUrl;
		user = dbUser;
		password = dbPass;
		fillPool();		
	}

	public String getClientVersion() {
		return OConstants.getVersion();
	}
	
	public void setPassword(String pass) {
		password = pass;
	}
	
	public boolean isPassword(String pass) {
		if (pass == null) pass = "";
		return pass.equals(password);
	}
	
	public void setPoolSize(int ps) {
		POOL_SIZE = ps;
		try {
			fillPool();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getUser() {
		return user;
	}
		
	public Date getLastAccessed() {
		return lastAccessed;
	}
	
	public void setLocale(Locale l) {
		locale = l;
	}
	
	public Locale getLocale() {
		return locale;
	}
	
	public int getActiveCount() {
		return activeConnections.size();
	}
	
	public int getPooledCount() {
		return pooledConnections.size();
	}
	
	/* Not very reliable. Can't be trusted to know if any of the connections are still good */
	public boolean isConnected() {
		return pooledConnections.size() + activeConnections.size() > 0;
	}

	public DatabaseConnection getConnection() {
		lastAccessed = new Date();
		if (pooledConnections.isEmpty()) {
			try {
				fillPool();
			} catch (Exception e) {
				System.out.println("DatabaseConnection.getConnection - unable to get connection: "+e.getMessage());
				return null;
			}
		}
		DatabaseConnection c = null;
		int i = 0;
		try {
			for (i = 0; i < RETRY_LIMIT && c == null; i++) {
				synchronized(pooledConnections) {
					c = pooledConnections.poll();
  					if (c == null || !c.isConnected()) {
  						Thread.sleep(RETRY_SLEEP);
  					}
				}
			}
		} catch (Exception e) {
			System.out.println("!"+e.getMessage());
		}
		if (c == null) {
			System.out.println("!connection("+this.user+")");
			if (i == RETRY_LIMIT) {
				try {  // Force an exception, I want to see what it was trying to do
					System.out.println("Too many retries "+RETRY_LIMIT);
					throw new Exception("Too many retries "+RETRY_LIMIT);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			activeConnections.add(c);
			// Attach the database connection to this thread
			ODatabaseRecordThreadLocal.INSTANCE.set(c.getDb());
			return c;
		}
		return null;
	}
	
	public void close() {
		try {
			for (DatabaseConnection c : activeConnections) {
				if (c!= null) c.close();
			}
			activeConnections.removeAll(activeConnections);
			for (DatabaseConnection c : pooledConnections) {
				if (c!= null) c.close();
			}
			pooledConnections.removeAll(pooledConnections);
		} catch (Exception e) {
			System.out.println("Error closing db "+e.getMessage());
		}
		
	}

	public Database fillPool() {
		if (ALLOW_POOL_GROWTH && (double)activeConnections.size()/(double)POOL_SIZE > POOL_GROWTH_FACTOR) {
			POOL_SIZE += POOL_GROWTH_STEP;
			System.out.println("Increasing database pool size for user "+user+" to "+POOL_SIZE);
		}
		while ( pooledConnections.size() < POOL_SIZE) {
			ODatabaseDocumentTx c = new ODatabaseDocumentTx(url);
			try {
				//if (c.exists()) {
					c.open(user,password);
				//}
			} catch (Exception e) {
				System.out.println("CONNECT ERROR: "+e.getMessage());
				//e.printStackTrace();
			}
			if (c.getStatus() != STATUS.OPEN) {
				System.out.println("Unable to open a connection for the pool for user "+user);
				//System.out.println("Unable to open a connection for the pool using "+user+"/"+password);   // For debugging only
				break;
			}
			DatabaseConnection dbc = new DatabaseConnection(this,c);
			pooledConnections.add(dbc);
		}
		System.out.println("Database.fillPool() "+user+" active:"+activeConnections.size()+" pooled:"+pooledConnections.size());
		return this;
	}

	/** Put the connection back in the pool */
	public void freeConnection(DatabaseConnection dbc) {
		activeConnections.remove(dbc);
		if (dbc != null && dbc.c != null) {
			pooledConnections.add(dbc);
		}
		//System.out.println("Connection "+dbc.getUser()+" freed");
	}

	/** If the server suspects the connection is bad, it calls this */
	public void closeConnection(DatabaseConnection dbc) {
		activeConnections.remove(dbc);
		dbc.close();
	}

	/** Create a plocal database and load starterdb.json if it exists - if no starter DatabaseSetup.Schema update will install what is needed */
	public void createLocal(String backupFile) {
		if (url.startsWith("plocal") || url.startsWith("local")) {
			System.out.println("* Creating new database "+url+" in "+System.getProperty("user.dir")+" *");
			ODatabaseDocumentTx	d = new ODatabaseDocumentTx(url);
			if (!d.exists()) {
				d = d.create();
			} else {
				System.out.println("***\n*** Exit condition: Cannot login or create database because it exists - maybe the server is already running?\n***");
				Server.exit(-3);
			}
			if (d.exists()) {
				if (backupFile != null && new File(backupFile).isFile()) {
					System.out.println("Loading "+backupFile+"....");
					try {
						ODatabaseImport importdb;
						importdb = new ODatabaseImport(d,backupFile, new OCommandOutputListener() {
							public void onMessage(String arg0) {
								System.out.println("Import Message: "+arg0);
							}
						});
//						importdb.setMerge(true);
						importdb.setDeleteRIDMapping(true);
						importdb.setIncludeSecurity(true);
						importdb.setIncludeRecords(true);
						importdb.setIncludeSchema(true);
						importdb.setIncludeInfo(true);
						importdb.setIncludeClusterDefinitions(true);
						importdb.setIncludeIndexDefinitions(true);
						importdb.setIncludeManualIndexes(true);
						importdb.importDatabase();
						importdb.close();
						d.getLocalCache().invalidate();		
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("Cannot restore from "+backupFile+" as it does not exist - not a problem");
				}
				OSecurity osec = d.getMetadata().getSecurity();
				OUser u = osec.getUser("server");
				if (u == null) {
					System.out.println("**** Security: Creating server user with server you should probably change this now");
					u = d.getMetadata().getSecurity().createUser("server", "server", "admin");
					u.save();
				} else {
					u.setPassword(password);
					u.save();
				}
			} else {
				System.out.println("Error creating database - does not exist (create must have failed)");
			}
		} else {
			System.out.println("Error creating database - can only create plocal databases");			
		}
	}

	// The following methods are meant to only be invoked by admin/dba during a module installation :-)  they will likely fail for everyone else

	/** Add the column to the columns table to preserve initial order (always append only if not already there)  */
	public static void addColumnToColumns(DatabaseConnection con, String theClass, String propertyName) {
		ODocument d = con.queryDocument("SELECT FROM "+Setup.TABLE_COLUMNS+" WHERE name='"+theClass+"'");
		if (d == null) {
			d = con.create(Setup.TABLE_COLUMNS);
			d.field("name",theClass);
		}
		String cl = d.field("columnList");
		if (cl == null || cl.equals("")) {
			d.field("columnList",propertyName);
		} else {
			String clc[] = cl.split(",");
			for (String cln : clc) {
				if (cln.trim().equals(propertyName) || cln.trim().equals("-"+propertyName)) {
					return;  // Its already here
				}
			}
			if (!cl.contains(propertyName))
			d.field("columnList",d.field("columnList")+","+propertyName);			
		}		
		d.save();
		return;
	}
	
	// The following methods are meant to only be invoked by admin/dba during a module installation :-)  they will likely fail for everyone else
	// Add the column to the columns table to preserve initial order (always append)
	public static void removeColumnFromColumns(DatabaseConnection con, String theClass, String propertyName) {
		ODocument d = con.queryDocument("SELECT FROM "+Setup.TABLE_COLUMNS+" WHERE name='"+theClass+"'");
		if (d == null) {
			return;
		}
		String cl = d.field("columnList");
		if (cl == null || cl.equals("")) {
			return;
		} else {
			String cols[] = cl.split(",");
			StringBuilder newCols = new StringBuilder();
			for (String c : cols) {  // Build a new list
				if (!c.equals(propertyName)) {  // Without the property in the list
					if (newCols.length()>0) newCols.append(",");
					newCols.append(c);
				}
			}
			d.field("columnList",newCols);			
		}		
		d.save();
		return;
	}
	

	
	/** Check for the existence of a class property or add it This assumes you want a link type, otherwise the linkClass may have adverse effects */
	public static OProperty checkCreateProperty(DatabaseConnection con, OClass theClass, String propertyName, OType propertyType, OClass linkClass, StringBuilder errors) {
		OProperty p = theClass.getProperty(propertyName);
		if (p == null) {
			p = theClass.createProperty(propertyName, propertyType, linkClass);
			errors.append(Weblet.paragraph("Schema update: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()+" linked to "+linkClass.getName()));
		}
		addColumnToColumns(con, theClass.getName(),propertyName);
		return p;
	}

	/** Check for the existence of a class property or add it */
	public static OProperty checkCreateProperty(DatabaseConnection con, OClass theClass, String propertyName, OType propertyType, StringBuilder errors) {
		OProperty p = theClass.getProperty(propertyName);
		if (p == null) {
			p = theClass.createProperty(propertyName, propertyType);
			errors.append(Weblet.paragraph("Schema update: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
			
		}
		if (p != null) {
			if (p.isMandatory()) {
				p.setMandatory(false);
				errors.append(Weblet.paragraph("Schema update: setting non-mandatory on "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
			}
			if (p.isNotNull()) {
				p.setNotNull(false);
				errors.append(Weblet.paragraph("Schema update: setting nullable on "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
			}
		}
		addColumnToColumns(con, theClass.getName(),propertyName);
		return p;
	}

	/** Check for the existence of a class or add it */
	public static OClass checkCreateClass(OSchema oschema, String className, StringBuilder errors) {
		OClass c = oschema.getClass(className);
		if (c == null) {
			c = oschema.createClass(className);
			errors.append(Weblet.paragraph("Schema update: Created "+className+" class/table"));
		}
		if (c == null) {
			errors.append(Weblet.paragraph("error","Schema update: Error creating "+className+" class/table"));
		}
		if (c != null) {
			if (c.isStrictMode()) {
				c.setStrictMode(false);
				errors.append(Weblet.paragraph("Schema update: Set non-strict "+className+" class/table"));
			}
		}
		return c;
	}

	/** Check for the existence of a class's superclass or set it */
	public static void checkClassSuperclass(OSchema oschema, OClass oclass, String superClassName, StringBuilder errors) {
		OClass s = oschema.getClass(superClassName);
		if (s == null) {
			errors.append(Weblet.paragraph("error","Schema update: Cannot find superclass "+superClassName+" to assign to class "+oclass.getName()));
			return;
		}
		OClass sc = oclass.getSuperClass();
		if (sc == null) {
			oclass.setSuperClass(s);
			errors.append(Weblet.paragraph("Schema update: Assigned superclass "+superClassName+" to class "+oclass.getName()));
			return;
		} else {
			if (!sc.getName().equals(superClassName)) {
				errors.append(Weblet.paragraph("error","Schema update: Trying to assign superclass "+superClassName+" to class "+oclass.getName()+" but it already has "+sc.getName()+" as a superclass"));	
				return;
			}
		}
		return;
	}

}
