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
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.arcadedb.database.Document;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.serializer.json.JSONObject;

public class QueryResultCache {
	
	public static boolean DEBUG = false;
	    
	ResultSet result;
	Date time = new Date();
	List<JSONObject> documents = new ArrayList<JSONObject>();
    String type = null;
    String[] columns = null;

    public QueryResultCache(ResultSet _result) {
    	result = _result;
        if (DEBUG) System.out.println("QueryResultCache: caching result");
		List<Document> docs = result.toDocuments();
        for (Document doc : docs) {
            JSONObject jdoc = doc.toJSON();
            jdoc.put("rid",doc.getIdentity().toString());
            documents.add(jdoc);
            if (columns == null) columns = doc.getPropertyNames().toArray(new String[0]);
            if (type == null) type = doc.getTypeName();
        }
        if (DEBUG) dump();
    }
	    
    public List<JSONObject> get() { return documents; }
    public Date getTime() { return time; }
    public String getType() { return type; }
    public JSONObject get(int i) { return documents.get(i); }
	public String[] getColumns() { return documents.size() > 0 ? columns : null; }
	public int size() { return documents.size(); }
    public String getColumnName(int i) { return columns[i]; }
    
    // Should be deprecated to getObject?
	public Object getValue(int row, String column) {
		try {
			return documents.get(row).get(column);
		} catch (Exception e) {
			System.out.println("No column "+column+":"+e.getMessage());
			return null;
		}
	}

	public Object getObject(int row, String column) {
		return documents.get(row).get(column);
	}
	
//	public RID getIdentity(int row) {
//		return documents.get(row).getIdentity();
//	}
	
	public String getStringValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null) {
			return o.toString();
		}
		return null;
	}
	
	public Object[] getLinkSetValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null && o instanceof Set) {
			@SuppressWarnings("rawtypes")
			Set set = (Set)o;
			Object oset[] = set.toArray();
			return oset;
		}
		return null;
	}
	
	public Number getNumberValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null && o instanceof Number) {
			return (Number)o;
		}
		return null;
	}

	public java.util.Date getDateValue(int row, String column) {
		Object o = getValue(row, column);
		if (DEBUG) System.out.println("DT Class = "+o.getClass().getName());
		if (o != null && o instanceof java.util.Date) {
			return (java.util.Date)o;
		}
		return null;
	}

	public boolean hasChanged(int row, String column) {
		if (row < 1) {
			return true;
		} else {
			return changed(getValue(row, column), getValue(row - 1, column));
		}
	}

	public boolean willChange(int row, String column) {
		if (row > (size() - 2)) {
			return true;
		} else {
			return changed(getValue(row, column), getValue(row + 1, column));
		}
	}

	public boolean changed(Object o1, Object o2) {
		if (o1 == null && o2 == null) return false;
		if (o1 == null && o2 != null) return true;
		if (o1 != null && o2 == null) return true;
		return !o1.equals(o2);
	}

	/** Add another queryResult to this one - like a Union */
	public void append(QueryResultCache other) {
		for (JSONObject o : other.documents) {
			documents.add(o);
		}
	}

	/** Add another document to this queryResult - like a Union */
	public void append(JSONObject doc) {
		documents.add(doc);
	}
	
    public int findFirstRow(String column, String value) {
		// simple table lookup
		if (DEBUG) System.out.print("FindFirstRow where "+column+" = "+value+"...");
		for (int i=0;i<size();i++) {
			String o = getStringValue(i,column);
			if (o == null && value == null) return i;
			if (o != null && value != null && o.equals(value)) {
				if (DEBUG) System.out.println("row found");
				return i;
			}
		}
		if (DEBUG) System.out.println("NOT found");
		return -1;
	}
	

	  public void dump() {
		System.out.println("Dump of "+getType()+" that will be cached");
    	if (documents.size() > 0) {
            System.out.print("rid\t");
            for (String header : columns) {
                System.out.print(header+"\t");
            }
            System.out.println("");
            for (JSONObject row : documents) {
                System.out.print(""+row.get("rid")+"\t");
                for (String pname : columns) {
                    if (row.has(pname)) {
                        System.out.print(""+row.get(pname)+"\t");
                    } else {
                        System.out.print("null\t");
                    }
                }
                System.out.println("");
            }
    	} else {
            System.out.println("QueryResult.dump - No rows");
    	}
    }

}


