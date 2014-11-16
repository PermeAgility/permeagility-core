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

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.ODatabase.STATUS;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
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
	
	public String getVersion(DatabaseConnection con) {
		if (con != null) {
			try {
				return con.c.getUnderlying().getName();
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
				if (c.exists()) {
					c.open(user,password);
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
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

	public void createLocal() {
		if (url.startsWith("plocal") || url.startsWith("local")) {
			if (!(new File("starterdb.json").isFile())) {
				System.out.println("***\n*** Exit condition: Cannot create database because there is no starterdb.json file\n***");
				System.exit(-1);
			}
			System.out.println("* Creating new database "+url+" in "+System.getProperty("user.dir")+" *");
			ODatabaseDocumentTx	d = new ODatabaseDocumentTx(url);
			if (!d.exists()) {
				d = d.create();
			} else {
				System.out.println("***\n*** Exit condition: Cannot login or create database because it exists - maybe the server is already running?\n***");
				System.exit(-1);
			}
			if (d.exists()) {
				ODatabaseImport importdb;
				try {
					importdb = new ODatabaseImport(d,"starterdb.json", new OCommandOutputListener() {
						public void onMessage(String arg0) {
							System.out.println("Import Message: "+arg0);
						}
					});
					importdb.importDatabase();
					importdb.close();
					System.out.println("setting "+user+" password to "+password+" you should probably change this now");
					OUser u = d.getMetadata().getSecurity().getUser("server");
					u.setPassword(password);
					u.save();

				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Error creating database - does not exist (create must have failed)");
			}
		} else {
			System.out.println("Error creating database - can only create plocal databases");			
		}
	}
		
}
