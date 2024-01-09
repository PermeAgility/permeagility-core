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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.web.Server;

import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.RID;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Schema;
import permeagility.web.Security;

/** This abstracts the native database connections a bit and give us somewhere to put some helper functions */
public class DatabaseConnection {

	public static boolean DEBUG = false;
    public static boolean DBA_BYPASS_RESTRICTED = false;  // if true, dba accounts can see all data 
    
	Database db = null;
	com.arcadedb.database.Database c = null;
	long lastAccess = System.currentTimeMillis();

    // Row counts are shared by all users
	private static ConcurrentHashMap<String,Long> tableCountCache = new ConcurrentHashMap<>();

  	protected DatabaseConnection() {}

	protected DatabaseConnection(Database _db, com.arcadedb.database.Database _c) {
		db = _db;
		c = _c;
		if (DEBUG) System.out.println("New database connection initiated to database "+c.getName()+" for user "+db.getUser());
	}

	public void close() { 
        if (!Database.EMBEDDED_SERVER) c.close(); 
    }
	
    public boolean isConnected() { return c != null && c.isOpen(); }

	/** Verification of password when changing password */
	public boolean isPassword(String pass) {
		return db.isPassword(pass);
	}

	/** Should only be called when the password is changed by the user (this does not actually change the password) */
	public void setPassword(String pass) { db.setPassword(pass); }

	/** Return the user name */
	public String getUser() { return db.getUser(); }

	/** Return the user's locale */
	public Locale getLocale() { return db.getLocale(); }

	/** Allows overriding the locale for the connection. This is for supporting non-users changing the language for the request */
	public void setLocale(Locale l) { db.setLocale(l); }

	/** Get the native connection object. A ODatabaseDocumentTx */
	public com.arcadedb.database.Database getDb() { return c; }

	/** Get the Schema object */
	public com.arcadedb.schema.Schema getSchema() { return c.getSchema(); }

public void begin() { /* c.begin(); */ }
public void commit() { /* c.commit(); */ }
public void rollback() {  /* c.rollback();*/ }

	/** Returns total number of rows in table from cache if found */
	public long getRowCount(String table) {
		// Find in cache?
		Long count = tableCountCache.get(table);
		if (count != null) {
			if (DEBUG) System.out.println("DatabaseConnection.getRowCount cache hit on table "+table);
			return count;
		}
		if (DEBUG) System.out.println("Counting rows of "+table);
		try {
			long cnt = c.countType(table, false);
			if (cnt > 0) tableCountCache.put(table, cnt);  // Don't put into cache until actual rows
			return cnt;
		} catch (Exception e) {
			System.out.println("DatabaseConnection.getRowCount Error counting class "+table);
            e.printStackTrace();
			return -1;
		}
	}

	/** Called when the table is changed to clear the rowcount cache */
	public static void rowCountChanged(String table) { tableCountCache.remove(table); }

	/** Called when the database is restored */
	public static void clearRowCounts() { tableCountCache.clear(); }

	/** Create a document - supply the classname */
	public MutableDocument create(String typeName) {
        // if (!c.isTransactionActive()) {
        //    System.out.println("DatabaseConnection.create: no active transaction");
        //}
		return c.newDocument(typeName);
	}

	/** Execute a query and return a QueryResult object */
    public QueryResult query(String expression) {
       // boolean closethis = false;
       // if (!c.isTransactionActive()) {
       //     System.out.println("DatabaseConnection.query: no active transaction, will create a default one for this query: "+expression);
       //     begin();
       //     closethis = true;
       // }
    	if (DEBUG) System.out.println("DatabaseConnection.query="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
        ResultSet result = c.query("SQL", expression);
        QueryResult qr = new QueryResult(result);
       // if (closethis) {
       //     commit();
       // }
    	return qr;
    }

	/** Execute a query and return a QueryResult object */
    public QueryResult queryTable(String table) { return queryTable(table, null, null, null); }
    public QueryResult queryTable(String table, String where) { return queryTable(table, where, null, null); }
    public QueryResult queryTable(String table, String where, String order) { return queryTable(table, where, order, null); }
    public QueryResult queryTable(String table, String where, String order, String skipLimit) {
       // boolean closethis = false;
       // if (!c.isTransactionActive()) {
       //     System.out.println("DatabaseConnection.query: no active transaction, will create a default one for this query");
       //     begin();
       //     closethis = true;
       // }
        if (where != null && !where.isBlank()) where = " WHERE "+where; else where = "";
        if (order != null && !order.isBlank()) order = " ORDER BY "+order; else order = "";
        if (skipLimit != null) skipLimit = " "+skipLimit; else skipLimit = "";
        DocumentType docType = getSchema().getType(table);
        if (docType.isSubTypeOf("restricted")) {
            if (DBA_BYPASS_RESTRICTED && Security.isDBA(this)) {
                System.out.println("Bypassing restricted for dba user "+getUser());
            } else {
                if (where.contains("_allow")) {  // bybass adding _allows if an _allow is already specified in the query
                    System.out.println("DatabaseConnection.queryTable restricted table "+table+" query has _allow in it (not added)");
                } else {
                    List<RID> roles = Security.getUserRoles(this);
                    where += (where.isBlank() ? " WHERE " : " AND ") 
                        + "("+makeAllowExpression("_allowRead", roles)+" OR "+makeAllowExpression("_allow", roles)+ ")";
                    System.out.println("DatabaseConnection.queryTable on "+table+" is restricted where="+where+";");
                }
            }
        }

        String expression = "SELECT FROM "+table+where+order+skipLimit;

    	if (DEBUG) System.out.println("DatabaseConnection.queryTable="+expression+";");
       	lastAccess = System.currentTimeMillis();
        ResultSet result = c.query("SQL", expression);
        QueryResult qr = new QueryResult(result);
        //if (closethis) {
        //    commit();
       // }
    	return qr;
    }

    public String makeAllowExpression(String name, List<RID> list) {
        StringBuilder roleExp = new StringBuilder();
        for (RID ur : Security.getUserRoles(this)) {
            if (roleExp.length() > 0) roleExp.append(" OR ");
            roleExp.append(name+" CONTAINS "+ur.toString());
        }
        return roleExp.toString();
    }

    /** Execute a query and return a QueryResultCache object */
    public QueryResultCache queryToCache(String expression) {
       // boolean closethis = false;
       // if (!c.isTransactionActive()) {
       //     System.out.println("DatabaseConnection.query: no active transaction, will create a default one for this query");
       //     begin();
       //     closethis = true;
       // }
    	if (DEBUG) System.out.println("DatabaseConnection.queryToCache="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
        ResultSet result = c.query("SQL", expression);
        QueryResultCache qr = new QueryResultCache(result);
       // if (closethis) {
       //     commit();
       // }
    	return qr;
    }

    /** Get the first document found by this query */
    public Document queryDocument(String expression) {
       //boolean closethis = false;
        //if (!c.isTransactionActive()) {
        //    System.out.println("DatabaseConnection(queryDocument): no active transaction, will create a default one for this query: "+expression);
        //    begin();
        //    closethis = true;
       // }
    	if (DEBUG) System.out.println("DatabaseConnection.queryDocument="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
       	ResultSet result = c.query("SQL",expression);
        Document d = result.hasNext() ? result.toDocuments().get(0) : null;
        //if (closethis) {
        //    commit();
        //}
  		return d;
    }

    /** Execute an update statement or a script if newline characters found */
    public Object update(String expression) {
        //boolean closethis = false;
        //if (!c.isTransactionActive()) {
        //    System.out.println("DatabaseConnection.update: no active transaction, will create a default one for this query");
        //    begin();
        //    closethis = true;
        //}
       if (DEBUG) System.out.println("DatabaseConnection.update="+expression+";");
       	lastAccess = System.currentTimeMillis();
        Object ro = c.command(expression.contains("\n") ? "SQLSCRIPT" : "SQL",expression);
        //if (closethis) {
        //    commit();
        //}
        if (ro instanceof ResultSet) {
            ArrayList<Document> docs = new ArrayList<Document>();
            ResultSet rs = (ResultSet)ro;
            if (rs.hasNext()) {
                Result r = rs.next();
                docs.add(r.toElement());
                if (!rs.hasNext()) {
                    return docs.get(0);
                } else {
                    do {
                        r = rs.next();
                        docs.add(r.toElement());
                    } while (rs.hasNext()); 
                    return docs;
                }
            }
        }
      	return ro;  // run the query return the resulting object
    }

    public Object updateScript(String expression) {
       if (DEBUG) System.out.println("DatabaseConnection.updateScript="+expression+";");
       	lastAccess = System.currentTimeMillis();
        Object ro = c.command("SQLSCRIPT",expression);
      	return ro;  // run the query return the resulting object
    }

    public Document get(String rid) {
        if (rid == null || rid.isEmpty()) {
            return null;
        }
        return get(new RID(getDb(),rid.startsWith("#") ? rid : "#"+rid));
    }

    public Document get(RID rid) {
        lastAccess = System.currentTimeMillis();
       // if (!c.isTransactionActive()) {
       //     System.out.println("DatabaseConnection.get: no active transaction, will create a default one for this query");
       // }
       	return c.lookupByRID(rid, true).asDocument();
    }

    /** Flush the local cache */
    public void flush() {
        System.out.println("DatabaseConnection(flush) called - not implemented");
        //c.getLocalCache().invalidate();
    }

    public Collection<Property> getColumns(String table) {
        return getColumns(table, null);
    }

    /**
     * Returns column names and column details including type, and linked class
     * overrides column order based on value in columnList in columns table and add inherited columns as well
     * returns an empty list if table not found
     */
    public Collection<Property> getColumns(String table, String columnOverride) {
        ArrayList<Property> result = new ArrayList<>();

        // Original list of columns
        Schema schema = getSchema();
        DocumentType tableClass = schema.getType(table);
        if (tableClass == null) {
            System.out.println("Server: getColumns(tableClass not found for "+table+")");
            return result;
        }
        List<DocumentType> sups = tableClass.getSuperTypes();
        for (DocumentType sup : sups) {
            result.addAll(sup.getProperties());
        }
        result.addAll(tableClass.getProperties());
        if (DEBUG) {
            for (Property p : result) {
                System.out.println("Found "+table+"."+p.getName()+" of type "+p.getType().toString()+" of Type "+p.getOfType());
            }
        }
        
        // List of columns to override natural (apparently random) order
        QueryResult columnList = null;
        if (columnOverride == null) {
            columnList = query("SELECT FROM "+Setup.TABLE_COLUMNS+" WHERE name='"+table+"'");
        }
        boolean addDynamicColumns = true;
        String list = (columnOverride == null ? (columnList != null && columnList.size() > 0 ? columnList.getStringValue(0, "columnList") : "") : columnOverride);
        String columnNames[] = list.split(",");
        for (String name : columnNames) {
            if (name.trim().equals("-")) {
                if (Server.DEBUG) System.out.println("ColumnOverride="+columnOverride+" no dynamic columns");
                addDynamicColumns = false;
            }
        }
        ArrayList<Property> newList = new ArrayList<>();
        for (String name : columnNames) {
            name = name.trim();
            if (!name.startsWith("-") && !name.equals("")) {
                if (name.startsWith("button")) {
                    //Property bd = new ButtonProperty();
                    //bd.setName(name.replace("(","_").replace(":","_").replace(")",""));
                    //newList.add(bd);
                } else {
                    boolean found = false;
                    for (Property p : result) {
                        String pname = p.getName();
                        if (pname.equals(name)) {
                            found = true;
                            if (pname.equals("_allow") || pname.equals("_allowRead") || pname.equals("_allowUpdate") || pname.equals("_allowDelete")) {
                                if (Security.getTablePriv(this, "OIdentity") > 0) {
                                    newList.add(p);
                                }
                            } else {
                                newList.add(p);
                            }
                            break;
                        }
                    }
                    if (!found) {
                        System.out.println("Could not find column '"+name+"' in the columns for table "+table+" even though this column was explicitly mentioned in the columns table - huh!");
                    }
                }
            }
        }
        if (addDynamicColumns) {
            for (Property col : result) {
                String name = col.getName();
                boolean found = false;
                for (String cn : columnNames) {
                    String cnt = cn.trim();
                    if (cnt.equals(name) || (cnt.startsWith("-") && cnt.substring(1).equals(name))) {
                        found = true;
                    }
                }
                if (!found) {
                    newList.add(col);
                }
            }
        }
        if (newList.size() > 0 || columnOverride != null) {
            return newList;
        }
        return result;  // will be empty but not null
    }

}
