/* 
 * Copyright 2015 PermeAgility Incorporated.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package permeagility.util;

import java.util.Date;
import java.util.Locale;
import com.arcadedb.Constants;
import com.arcadedb.ContextConfiguration;
import com.arcadedb.database.DatabaseContext;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.Document;
import com.arcadedb.engine.ComponentFile;
import com.arcadedb.engine.ComponentFile.MODE;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.ServerDatabase;

import permeagility.web.Security;
import permeagility.web.Server;

/**
 * This class holds a connection to a database
 * 
 */
public class Database  {

    protected static boolean EMBEDDED_SERVER = true;
    private static DatabaseFactory dbFactory = null;
    private static DatabaseFactory dbFactoryRO = null;

    private static ContextConfiguration config = new ContextConfiguration();
    private static ArcadeDBServer server = null;

    private String url = null;
    private String user = null;
    private String password = null;
    private Date lastAccessed = null;
    private Locale locale = Locale.getDefault(); 
    private boolean isValid = false;
    private com.arcadedb.database.Database db = null;
    private ServerDatabase sdb = null;
    private DatabaseConnection con = null;

    public Database(String dbUrl, String dbUser, String dbPass) throws Exception {
        url = dbUrl;
        user = dbUser;
        password = Security.digest(dbPass);
        if (dbFactory == null && server == null) {
            startMeUp();    // first connection is passwordless to allow server setup/startup    
            return;
        }
        System.out.println("Created new DatabaseObject for user "+dbUser);
        if (EMBEDDED_SERVER) {
            sdb = server.getDatabase(url);
        } else {
            db = dbFactory.open();
        }
        //if ()
        System.out.println("Validating the user login information for "+user);
        con = EMBEDDED_SERVER ? new DatabaseConnection(this,sdb) : new DatabaseConnection(this,db);
        //con.begin();
        Document udoc = con.queryDocument("SELECT FROM user WHERE name='"+user+"' AND status='ACTIVE' AND password='"+password+"'");
        //con.commit();
        if (udoc != null) {
            isValid = true;
        } else {
            System.out.println("Database Warning: Failed attempt to login user "+user);
            isValid = false;
        }
    }

    private void startMeUp() {
        if (dbFactory == null && !EMBEDDED_SERVER) {
            dbFactory = new DatabaseFactory(url);
            if (!dbFactory.exists()) {
                createLocal("");
            }
            dbFactoryRO = new DatabaseFactory(url);
        }
        if (server == null && EMBEDDED_SERVER) {
            //config.setValue(GlobalConfiguration.HA_SERVER_LIST, "192.168.10.1,192.168.10.2,192.168.10.3");
            //config.setValue(GlobalConfiguration.HA_REPLICATION_INCOMING_HOST, "0.0.0.0");
            //config.setValue(GlobalConfiguration.HA_ENABLED, true);
//            config.setValue(GlobalConfiguration.SERVER_ROOT_PASSWORD, "886E0728");
            server = new ArcadeDBServer(config);
            server.start();
            if (!server.existsDatabase(url)) {
                sdb = server.createDatabase(url, MODE.READ_WRITE);
                if (sdb.isOpen()) {
                    System.out.println("Database is open");
                }
            }
        }
        if (EMBEDDED_SERVER) {
            if (sdb == null) sdb = server.getDatabase(url);
            sdb.setAutoTransaction(true);
        } else {
            db = dbFactory.open();
        }
        con = EMBEDDED_SERVER ? new DatabaseConnection(this,sdb) : new DatabaseConnection(this,db);
        isValid = true;
    }

    public DatabaseConnection getReadOnlyConnection() {
        if (EMBEDDED_SERVER) {
            return con;
        } else {
             return new DatabaseConnection(this,dbFactoryRO.open(ComponentFile.MODE.READ_ONLY));
        }
    }

    public DatabaseConnection getConnection() {
        if (isValid && con != null) { 
            return con;
        } else {
            return null;
        }
    }

    public void close() {
        if (EMBEDDED_SERVER) {
            System.out.println("Calling server.stop()");
            server.stop();
        } else {
            try {
                dbFactory.close();
                System.out.println("database closed.");
            } catch (Exception e) {
                System.out.println("Error closing db "+e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public boolean isConnected() {
        if (EMBEDDED_SERVER) {
            return isValid && con != null;
        }
        return dbFactory != null && dbFactory.exists();
    }

    public boolean isPassword(String pass) {
        if (pass == null) pass = "";
        return pass.equals(password);
    }

    public void setPoolSize(int ps) {   }
    public String getClientVersion() { return Constants.getVersion();  }

    public void setPassword(String pass) { password = pass; }

    public String getUser() { return user;  }

    public Date getLastAccessed() { return lastAccessed; }

    public void setLocale(Locale l) { locale = l; }

    public Locale getLocale() { return locale;  }

    /** Create a local database and load starterdb.json if it exists - if no starter DatabaseSetup.Schema update will install what is needed */
    public void createLocal(String backupFile) {
            System.out.println("*** Creating new database "+url+" in "+System.getProperty("user.dir"));
            com.arcadedb.database.Database d;
            if (!dbFactory.exists()) {
                d = dbFactory.create();
                System.out.println("Database "+d.getDatabasePath()+" created.");
                if (d.isOpen()) {
                    System.out.println("Database is open");
                    d.close();
                    return;
                } else {
                    System.out.println("Database is NOT open");
                }
            } else {
                dbFactory.close();
                System.out.println("***\n*** Exit condition: Cannot login or create database because it exists - maybe the server is already running?\n***");
                Server.exit(-3);
            }
     //       if (dbFactory.exists()) {
     //           System.out.println("The database exists!");
     //           if (backupFile != null && new File(backupFile).isFile()) {
     //               System.out.println("Loading backup from "+backupFile+"...");
                    /* try {
                         ODatabaseImport importdb;
                        importdb = new ODatabaseImport(d,backupFile, new OCommandOutputListener() {
                            public void onMessage(String arg0) {
                                System.out.println("Import Message: "+arg0);
                            }
                        });
                        importdb.importDatabase();
                        importdb.close();
                        d.getLocalCache().invalidate();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } */
      //          } else {
      //                  System.out.println("Cannot restore from "+backupFile+" as it does not exist - not a problem - will create new");
      //          }
            //    if (d.getStatus() != STATUS.OPEN) {
            //        System.err.println("Problem after import - status is not open");
            //        Server.exit(-5);
            //    } else {
  /*                   OSecurity osec = d.getMetadata().getSecurity();
                    if (osec.getRole("server") == null) {
                        System.out.println("Server role does not exist, Creating the server role");
                        ORole serverRole = osec.createRole("server", ALLOW_MODES.ALLOW_ALL_BUT);
                        serverRole.addRule(ORule.ResourceGeneric.BYPASS_RESTRICTED, null, 31);  // Needed so we can backup and restore all data
                        serverRole.save();
                    }
                    if (osec.getUser("server") == null) {
                        System.out.println("Server user does not exist, Creating the server user");
                        osec.createUser("server",(serverPass == null ? "server" : serverPass),"server");                        
                    }
    */
            //    }
                //dbf.close();
   //         } else {
    //            System.out.println("Error creating database - does not exist (create must have failed)");
    //            dbFactory.close();
    //        }
        return;
    }

}
