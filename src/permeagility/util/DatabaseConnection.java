/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
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
import com.orientechnologies.orient.core.metadata.schema.OProperty.ATTRIBUTES;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
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
			c.getLocalCache().invalidate();
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
		//c.getLocalCache().clear();  // Remove existing objects from the cache as they will not know of SQL update
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

	public Collection<OProperty> getColumns(String table) {
		return getColumns(table, null);
	}
 	
	/**
	 * Returns column names and column details including type, and linked class
	 * overrides column order based on value in columnList in columns table and add inherited columns as well
	 * returns an empty list if table not found
	 */
	public Collection<OProperty> getColumns(String table, String columnOverride) {
		ArrayList<OProperty> result = new ArrayList<OProperty>();
		
		// Original list of columns
		OSchema schema = getSchema();
		OClass tableClass = schema.getClass(table);
		if (tableClass == null) {
			System.out.println("Server: getColumns(tableClass not found for "+table+")");
			return result;
		}
		result.addAll(tableClass.properties());

		// Get details about the table so we can add superClass attributes 
		OClass superClass = tableClass.getSuperClass();
		if (superClass != null) {
			Collection<OProperty> superProperties = superClass.properties();
			for (OProperty sc : superProperties) {
				if (!result.contains(sc)) {
					result.add(sc);  // Only add if not already there (latest version brings the superclass columns)
				}
			}
		}
		
		// List of columns to override natural (random) order
		QueryResult columnList = null;
		if (columnOverride == null) {
			columnList = query("SELECT columnList FROM "+Setup.TABLE_COLUMNS+" WHERE name='"+table+"'");
		}
		boolean addDynamicColumns = true;
		if (columnOverride != null || (columnList != null && columnList.size()>0)) {
			String list = (columnOverride == null ? columnList.getStringValue(0, "columnList") : columnOverride);
			String columnNames[] = list.split(",");
			if (columnNames.length > 0 ) {
				ArrayList<OProperty> newList = new ArrayList<OProperty>();
				for (String name : columnNames) {
					name = name.trim();
					if (name.equals("-")) {
						if (Server.DEBUG) System.out.println("ColumnOverride="+columnOverride+" no dynamic columns");
						addDynamicColumns = false;
					}
					if (!name.startsWith("-")) {
						if (name.trim().startsWith("button")) {
							OProperty bd = new OProperty() {
								String name;
								public OProperty setType(OType t) { return this; }
								public boolean isMandatory() { return false; }
								@Override
								public int compareTo(OProperty o) { return 0; }
								@Override
								public String getName() { return name; }
								@Override
								public String getFullName() { return null; }
								@Override
								public OProperty setName(String iName) {
									name = iName;
									return this;
								}
								@Override
								public void set(ATTRIBUTES attribute, Object iValue) { }
								@Override
								public OType getType() { return OType.TRANSIENT; }
								@Override
								public OClass getLinkedClass() { return null; }
								@Override
								public OProperty setLinkedClass(OClass oClass) { return null; }
								@Override
								public OType getLinkedType() { return null; }
								@Override
								public OProperty setLinkedType(OType type) { return null; }
								@Override
								public boolean isNotNull() { return false; }
								@Override
								public OProperty setNotNull(boolean iNotNull) { return null; }
								@Override
								public OCollate getCollate() { return null; }
								@Override
								public OProperty setCollate(String iCollateName) { return null; }
								@Override
								public OProperty setCollate(OCollate collate) { return null; }
								@Override
								public OProperty setMandatory(boolean mandatory) { return null; }
								@Override
								public boolean isReadonly() { return false; }
								@Override
								public OProperty setReadonly(boolean iReadonly) { return null; }
								@Override
								public String getMin() { return null; }
								@Override
								public OProperty setMin(String min) { return null; }
								@Override
								public String getMax() { return null; }
								@Override
								public OProperty setMax(String max) { return null; }
								@Override
								public OIndex<?> createIndex(INDEX_TYPE iType) { return null; }
								@Override
								public OIndex<?> createIndex(String iType) {
									return null;
								}
								@Override
								public OProperty dropIndexes() {
									return null;
								}
								@Override
								public Set<OIndex<?>> getIndexes() {
									return null;
								}
								@Override
								public OIndex<?> getIndex() {
									return null;
								}
								@Override
								public Collection<OIndex<?>> getAllIndexes() {
									return null;
								}
								@Override
								public boolean isIndexed() {
									return false;
								}
								@Override
								public String getRegexp() {
									return null;
								}
								@Override
								public OProperty setRegexp(String regexp) {
									return null;
								}
								@Override
								public String getCustom(String iName) {
									return null;
								}
								@Override
								public OProperty setCustom(String iName,
										String iValue) {
									return null;
								}
								@Override
								public void removeCustom(String iName) {
								}
								@Override
								public void clearCustom() {
								}
								@Override
								public Set<String> getCustomKeys() {
									return null;
								}
								@Override
								public OClass getOwnerClass() {
									return null;
								}
								@Override
								public Object get(ATTRIBUTES iAttribute) {
									return null;
								}
								@Override
								public Integer getId() {
									return null;
								};
							};
							//OProperty bd = tableClass.createProperty(name.replace("(","_").replace(":","_").replace(")",""), OType.TRANSIENT);
							bd.setName(name.replace("(","_").replace(":","_").replace(")",""));
							newList.add(bd);
						} else {
							boolean found = false;
							for (OProperty p : result) {
								if (p.getName().equals(name)) {
									newList.add(p);
									found = true;
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
							if (cnt.equals(name) 
							  || (cnt.startsWith("-") && cnt.substring(1).equals(name))) {
								found = true;
							}
						}
						if (!found) {
							newList.add(col);
						}
					}
				}
				if (newList.size() > 0 ) {
					return newList;
				}
			}
			return result;
		}
		return result;  // will be empty but not null
	}

}
