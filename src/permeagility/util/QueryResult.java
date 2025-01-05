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
import java.util.List;
import java.util.Set;

import com.arcadedb.database.Document;
import com.arcadedb.database.RID;
import com.arcadedb.query.sql.executor.ResultSet;
import java.util.ArrayList;

public class QueryResult {
	
	public static boolean DEBUG = false;
	    
	ResultSet result;
	Date time = new Date();
	List<Document> documents;
	String[] columns = null;
	String type;

    public QueryResult(ResultSet _result) {
    	result = _result;
		documents = result.toDocuments();
		if (documents.size() > 0) {
			Document first = documents.get(0);
			columns = first.getPropertyNames().toArray(new String[0]);
			type = first.getTypeName();
		}
    }
	    
    public List<Document> get() {
    	return documents;
    }
    
    public List<String> getIds() {
        List<String> list = new ArrayList<>();
        for (Document d : get()) {
            list.add(d.getIdentity().toString());
        }
        return list;
    }
    
    public Date getTime() {	return time; }
	public String getType() { return type;	}

    public Document get(int i) { return documents.get(i); }
    
	public String[] getColumns() {
		if (documents.size() > 0) {
			return columns;
		}
		return null;
	}
		
	public int size() { return documents.size(); }
	
    public String getColumnName(int i) { 
		if (columns != null && columns.length > i) {
			return columns[i]; 
		} else {
			System.out.println("Trying to get column name for "+i+" not found or null");
			return null;
		}
    }
  
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

	public int findFirstRow(String column, String value) {
		// simple table lookup
		if (DEBUG) System.out.print("FindFirstRow where "+column+" = "+value+"...");
		for (int i=0;i<size();i++) {
			String o = getStringValue(i,column);
			if (o == null && column.equals("rid")) {
				Document d = get(i);
				o = d.getIdentity().toString();
			}
			if (o == null && value == null) return i;
			if (o != null && value != null && o.equals(value)) {
				if (DEBUG) System.out.println("row found");
				return i;
			}
		}
		if (DEBUG) System.out.println("NOT found");
		return -1;
	}
	
	public RID getIdentity(int row) {
		return documents.get(row).getIdentity();
	}
	
	public String getStringValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null) {
			return o.toString();
		}
		return null;
	}
	
	public Object[] getLinkSetValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null && o instanceof Set set) {
			@SuppressWarnings("rawtypes")
			Object oset[] = set.toArray();
			return oset;
		}
		return null;
	}
	
	public Number getNumberValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null && o instanceof Number n) {
			return n;
		}
		return null;
	}

	public java.util.Date getDateValue(int row, String column) {
		Object o = getValue(row, column);
		if (DEBUG) System.out.println("DT Class = "+o.getClass().getName());
		if (o != null && o instanceof java.util.Date d) {
			return d;
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
	public void append(QueryResult other) {
		for (Document o : other.documents) {
			documents.add(o);
		}
	}

	/** Add another document to this queryResult - like a Union */
	public void append(Document doc) {
		documents.add(doc);
	}
	
	public void dump() {
		System.out.println("Dumping contents of result of type "+type);
		if (documents.size() > 0) {
			Set<String> pnames = documents.get(0).getPropertyNames();
			for (String header : pnames) {
				System.out.print(header+"\t");
			}
			System.out.println("");
			for (Document row : documents) {
				for (String pname : pnames) {
					System.out.print(""+row.get(pname)+"\t");
				}
			}
			System.out.println("");
		} else {
			System.out.println("QueryResult.dump - No rows");
		}
	}

}


