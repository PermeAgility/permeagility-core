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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.web.Server;

import com.orientechnologies.orient.core.collate.OCollate;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OClass.INDEX_TYPE;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.metadata.security.OSecurity;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import permeagility.web.Security;

/** This abstracts the native database connections a bit and give us somewhere to put some helper functions */
public class DatabaseConnection {

	public static boolean DEBUG = false;

	Database db = null;
	ODatabaseDocumentTx c = null;
	long lastAccess = System.currentTimeMillis();

        // Row counts are shared by all users
	private static ConcurrentHashMap<String,Long> tableCountCache = new ConcurrentHashMap<>();

	protected DatabaseConnection(Database _db, ODatabaseDocumentTx _c) {
		db = _db;
		c = _c;
		if (DEBUG) System.out.println("New database connection initiated "+c.getName());
	}

	protected void close() {
			c.activateOnCurrentThread();
			c.close();
			c = null;
	}

	public boolean isConnected() {
		return c != null;
	}

        // The NewConnection functions are for thread processes that need another connection after the user has moved on from this one
        public DatabaseConnection getNewConnection() {
            return db.getConnection();
        }

        public void freeNewConnection(DatabaseConnection con) {
            db.freeConnection(con);
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
		c.activateOnCurrentThread();
		return c;
	}

	/** Get the OrientDB OSchema object */
	public OSchema getSchema() {
		if (c == null) {
			return null;
		} else {
			c.activateOnCurrentThread();
			return c.getMetadata().getSchema();
		}
	}

	/** Get the OrientDB OSecurity object */
	public OSecurity getSecurity() {
		if (c == null) {
			return null;
		} else {
			c.activateOnCurrentThread();
			return c.getMetadata().getSecurity();
		}
	}

	/** Create a document - supply the classname */
	public ODocument create(String className) {
		c.activateOnCurrentThread();
		return c.newInstance(className);
	}

	public void begin() {
		c.activateOnCurrentThread();
		c.begin();
	}

	public void commit() {
		c.activateOnCurrentThread();
		c.commit();
	}

	public void rollback() {
		c.activateOnCurrentThread();
		c.rollback();
	}

	/** Returns total number of rows in table from cache if found */
	public long getRowCount(String table) {
		// Find in cache?
		Long count = tableCountCache.get(table);
		if (count != null) {
			//System.out.println("Row count cache hit on table "+table);
			return count;
		}
		//System.out.println("Counting rows of "+table);
		try {
			c.activateOnCurrentThread();
			long cnt = c.countClass(table);
			tableCountCache.put(table, cnt);
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

	/** Execute a query and return a QueryResult object */
    public synchronized QueryResult query(String expression) {
    	if (DEBUG) System.out.println("DatabaseConnection.DEBUG(query)="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
        c.activateOnCurrentThread();
    	List<ODocument> result = c.query(new OSQLSynchQuery<>(expression));
    	return new QueryResult(result);
    }

    /** Get the first document found by this query */
    public synchronized ODocument queryDocument(String expression) {
    	if (DEBUG) System.out.println("DatabaseConnection.DEBUG(queryDocument)="+expression+";");
    	if (expression == null) { return null; }
       	lastAccess = System.currentTimeMillis();
       	c.activateOnCurrentThread();
    	List<ODocument> result = c.query(new OSQLSynchQuery<>(expression));
    	if (result != null && result.size() > 0) {
    		return result.get(0);
    	} else {
    		return null;
    	}
    }

    /** Execute an update statement */
    public synchronized Object update(String expression) {
        if (DEBUG) System.out.println("DatabaseConnection.DEBUG(update)="+expression+";");
       	lastAccess = System.currentTimeMillis();
       	c.activateOnCurrentThread();
        return c.command(new OCommandSQL(expression)).execute();  // run the query return the resulting object
    }

    /** Get a document by its RID */
    public synchronized ODocument get(String rid) {
       	lastAccess = System.currentTimeMillis();
       	c.activateOnCurrentThread();
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

    /** Flush the local cache */
    public void flush() {
        c.activateOnCurrentThread();
        c.getLocalCache().invalidate();
    }

    public Collection<OProperty> getColumns(String table) {
        return getColumns(table, null);
    }

    /**
     * Returns column names and column details including type, and linked class
     * overrides column order based on value in columnList in columns table and add inherited columns as well
     * returns an empty list if table not found
     */
    public Collection<OProperty> getColumns(String table, String columnOverride) {
        ArrayList<OProperty> result = new ArrayList<>();

        // Original list of columns
        OSchema schema = getSchema();
        OClass tableClass = schema.getClass(table);
        if (tableClass == null) {
            System.out.println("Server: getColumns(tableClass not found for "+table+")");
            return result;
        }
        result.addAll(tableClass.properties());

        // List of columns to override natural (apparently random) order
        QueryResult columnList = null;
        if (columnOverride == null) {
            columnList = query("SELECT columnList FROM "+Setup.TABLE_COLUMNS+" WHERE name='"+table+"'");
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
        ArrayList<OProperty> newList = new ArrayList<>();
        for (String name : columnNames) {
            name = name.trim();
            if (!name.startsWith("-") && !name.equals("")) {
                if (name.startsWith("button")) {
                    OProperty bd = new ButtonProperty();
                    bd.setName(name.replace("(","_").replace(":","_").replace(")",""));
                    newList.add(bd);
                } else {
                    boolean found = false;
                    for (OProperty p : result) {
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
                        System.out.println("Could not find column "+name+" in the columns for table "+table+" even though this column was explicitly mentioned in the columns table - huh!");
                    }
                }
            }
        }
        if (addDynamicColumns) {
            for (OProperty col : result) {
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

    /**
     * Used to create a column for a button in the UI. These buttons can be invoked via columnOverride
     */
    class ButtonProperty implements OProperty {
            String name;
            @Override public OProperty setType(OType t) { return this; }
            @Override public boolean isMandatory() { return false; }
            @Override public int compareTo(OProperty o) { return 0; }
            @Override public String getName() { return name; }
            @Override public String getFullName() { return null; }
            @Override public OProperty setName(String iName) {
                    name = iName;
                    return this;
            }
            @Override public void set(ATTRIBUTES attribute, Object iValue) { }
            @Override public OType getType() { return OType.TRANSIENT; }
            @Override public OClass getLinkedClass() { return null; }
            @Override public OProperty setLinkedClass(OClass oClass) { return null; }
            @Override public OType getLinkedType() { return null; }
            @Override public OProperty setLinkedType(OType type) { return null; }
            @Override public boolean isNotNull() { return false; }
            @Override public OProperty setNotNull(boolean iNotNull) { return null; }
            @Override public OCollate getCollate() { return null; }
            @Override public OProperty setCollate(String iCollateName) { return null; }
            @Override public OProperty setCollate(OCollate collate) { return null; }
            @Override public OProperty setMandatory(boolean mandatory) { return null; }
            @Override public boolean isReadonly() { return false; }
            @Override public OProperty setReadonly(boolean iReadonly) { return null; }
            @Override public String getMin() { return null; }
            @Override public OProperty setMin(String min) { return null; }
            @Override public String getMax() { return null; }
            @Override public OProperty setMax(String max) { return null; }
            @Override public OIndex<?> createIndex(INDEX_TYPE iType) { return null; }
            @Override public OIndex<?> createIndex(String iType) {	return null;	}
            @Override @Deprecated public OProperty dropIndexes() {	return null;	}
            @Override @Deprecated public Set<OIndex<?>> getIndexes() {		return null;	}
            @Override @Deprecated public OIndex<?> getIndex() {	return null;	}
            @Override public Collection<OIndex<?>> getAllIndexes() {	return null;		}
            @Override @Deprecated public boolean isIndexed() {	return false;	}
            @Override public String getRegexp() {		return null;	}
            @Override public OProperty setRegexp(String regexp) {			return null;	}
            @Override public String getCustom(String iName) {	return null;			}
            @Override public OProperty setCustom(String iName, String iValue) {	return null;	}
            @Override public void removeCustom(String iName) {	}
            @Override public void clearCustom() {		}
            @Override public Set<String> getCustomKeys() {			return null;		}
            @Override public OClass getOwnerClass() {	return null;			}
            @Override  public Object get(ATTRIBUTES iAttribute) {	return null;		}
            @Override public Integer getId() { return null; }
            @Override public String getDefaultValue() { return null;  }
            @Override public OProperty setDefaultValue(String arg0) { return null;  };
            @Override public OIndex<?> createIndex(String iType, ODocument metadata) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
            @Override public OIndex<?> createIndex(INDEX_TYPE iType, ODocument metadata) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
            @Override public String getDescription() { return "Button";  }
            @Override public OProperty setDescription(String arg0) { return this; }

	}
}
