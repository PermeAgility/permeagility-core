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

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.ArrayList;

public class QueryResult {
	
	public static boolean DEBUG = false;
	    
	List<ODocument> result;
	Date time = new Date();

    public QueryResult(List<ODocument> _result) {
    	result = _result;
    }
	    
    public List<ODocument> get() {
    	return result;
    }
    
    public List<String> getIds() {
        List<String> list = new ArrayList<>();
        for (ODocument d : get()) {
            list.add(d.getIdentity().toString());
        }
        return list;
    }
    
    public Date getTime() {
    	return time;
    }
    
    public ODocument get(int i) {
    	return result.get(i);
    }
    
	public String[] getColumns() {
		if (result.size()>0) {
			return result.get(0).fieldNames();
		}
		return null;
	}
	
	public OType getType(String colName) {
		if (result.size()>0) {
			return result.get(0).fieldType(colName);
		} else {
			System.out.println("No rows to get types from");
		}
		return null;
	}
	
	public int size() {
		return result.size();
	}
	
    public String getColumnName(int i) {
    	return getColumns()[i];
    }
    
    public OType getColumnType(int i) {
    	return getType(getColumnName(i));
    }

    public OType getColumnType(String colName) {
    	return getType(colName);
    }

    // Should be deprecated to getObject?
	public Object getValue(int row, String column) {
		try {
			return result.get(row).field(column);
		} catch (Exception e) {
			System.out.println("No column "+column+":"+e.getMessage());
			return null;
		}
	}

	public Object getObject(int row, String column) {
		return result.get(row).field(column);
	}

	public int findFirstRow(String column, String value) {
		// simple table lookup
		if (DEBUG) System.out.print("FindFirstRow where "+column+" = "+value+"...");
		for (int i=0;i<size();i++) {
			String o = getStringValue(i,column);
			if (o == null && column.equals("rid")) {
				ODocument d = get(i);
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
	
	public ORID getIdentity(int row) {
		return result.get(row).getIdentity();
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
	public void append(QueryResult other) {
		for (ODocument o : other.result) {
			result.add(o);
		}
	}

	/** Add another document to this queryResult - like a Union */
	public void append(ODocument doc) {
		result.add(doc);
	}
	
}


