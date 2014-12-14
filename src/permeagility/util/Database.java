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

import permeagility.web.Weblet;

import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.ODatabase.STATUS;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.metadata.security.OUser;

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
	
	public String getName(DatabaseConnection con) {
		if (con != null) {
			try {
				return con.c.toString();
			} catch (Exception e) {
				e.printStackTrace();
				return "Exception "+e.getLocalizedMessage();
			}
		} else {
			return "Unknown";
		}
	}

	public String getDatabaseVersion(DatabaseConnection con) {
		if (con != null) {
			try {
				return con.c.getConfiguration().toString();
			} catch (Exception e) {
				e.printStackTrace();
				return "Exception "+e.getLocalizedMessage();
			}
		} else {
			return "Unknown";
		}
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
	
	/*
	 * Not very reliable. Can't be trusted to know if any of the connections are still good
	 */
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
				if (c != null) c.close();
			}
			activeConnections.removeAll(activeConnections);
			for (DatabaseConnection c : pooledConnections) {
				if (c != null) c.close();
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

	/** Create a plocal database and load starterdb.json if it exists - if no starter DatabaseSetup.checkInstallation will install what is needed */
	public void createLocal() {
		if (url.startsWith("plocal") || url.startsWith("local")) {
			System.out.println("* Creating new database "+url+" in "+System.getProperty("user.dir")+" *");
			ODatabaseDocumentTx	d = new ODatabaseDocumentTx(url);
			if (!d.exists()) {
				d = d.create();
			} else {
				System.out.println("***\n*** Exit condition: Cannot login or create database because it exists - maybe the server is already running?\n***");
				System.exit(-1);
			}
			if (d.exists()) {
				if (new File("starterdb.json").isFile()) {
					System.out.println("Loading starterdb.json....");
					try {
						ODatabaseImport importdb;
						importdb = new ODatabaseImport(d,"starterdb.json", new OCommandOutputListener() {
							public void onMessage(String arg0) {
								System.out.println("Import Message: "+arg0);
							}
						});
						importdb.importDatabase();
						importdb.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				System.out.println("setting "+user+" password to "+password+" you should probably change this now");
				OUser u = d.getMetadata().getSecurity().getUser("server");
				if (u == null) {
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

	/** Check for the existence of a class property or add it This assumes you want a link type, otherwise the linkClass may have adverse effects */
	public static OProperty checkCreateProperty(OClass theClass, String propertyName, OType propertyType, OClass linkClass, StringBuffer errors) {
		OProperty p = theClass.getProperty(propertyName);
		if (p == null) {
			p = theClass.createProperty(propertyName, propertyType, linkClass);
			errors.append(Weblet.paragraph("CheckInstallation: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()+" linked to "+linkClass.getName()));
		}
		return p;
	}

	/** Check for the existence of a class property or add it */
	public static OProperty checkCreateProperty(OClass theClass, String propertyName, OType propertyType, StringBuffer errors) {
		OProperty p = theClass.getProperty(propertyName);
		if (p == null) {
			p = theClass.createProperty(propertyName, propertyType);
			errors.append(Weblet.paragraph("CheckInstallation: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
		}
		if (p != null) {
			if (p.isMandatory()) {
				p.setMandatory(false);
				errors.append(Weblet.paragraph("CheckInstallation: setting non-mandatory on "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
			}
			if (p.isNotNull()) {
				p.setNotNull(false);
				errors.append(Weblet.paragraph("CheckInstallation: setting nullable on "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
			}
		}
		return p;
	}

	/** Check for the existence of a class or add it */
	public static OClass checkCreateClass(OSchema oschema, String className, StringBuffer errors) {
		OClass c = oschema.getClass(className);
		if (c == null) {
			c = oschema.createClass(className);
			errors.append(Weblet.paragraph("CheckInstallation: Created "+className+" class/table"));
		}
		if (c == null) {
			errors.append(Weblet.paragraph("error","CheckInstallation: Error creating "+className+" class/table"));
		}
		if (c != null) {
			if (c.isStrictMode()) {
				c.setStrictMode(false);
				errors.append(Weblet.paragraph("CheckInstallation: Set non-strict "+className+" class/table"));
			}
		}
		return c;
	}

	/** Check for the existence of a class's superclass or set it */
	public static void checkClassSuperclass(OSchema oschema, OClass oclass, String superClassName, StringBuffer errors) {
		OClass s = oschema.getClass(superClassName);
		if (s == null) {
			errors.append(Weblet.paragraph("error","CheckInstallation: Cannot find superclass "+superClassName+" to assign to class "+oclass.getName()));
			return;
		}
		OClass sc = oclass.getSuperClass();
		if (sc == null) {
			oclass.setSuperClass(s);
			errors.append(Weblet.paragraph("CheckInstallation: Assigned superclass "+superClassName+" to class "+oclass.getName()));
			return;
		} else {
			if (!sc.getName().equals(superClassName)) {
				errors.append(Weblet.paragraph("error","CheckInstallation: Trying to assign superclass "+superClassName+" to class "+oclass.getName()+" but it already has "+sc.getName()+" as a superclass"));	
				return;
			}
		}
		return;
	}

}
