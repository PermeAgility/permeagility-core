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
package permeagility.web;

import com.orientechnologies.orient.core.exception.OSecurityAccessException;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class Table extends Weblet {

	public static boolean DEBUG = true;
	public static int MAX_STRING_DISPLAY = 100;
	public static int TEXT_AREA_THRESHOLD = 40;  // When showing a column as a cell, only show this many characters
	public static int TEXT_AREA_WIDTH = 80;      // When the data is larger than this size, the input will be a text area
	public static long ROW_COUNT_LIMIT = 500;    // All text areas will be this width
	public static long DOT_INTERVAL = 5;         // Interval for dot when numerous pages - probably should derive this to be more dynamic
	public static long PAGE_WINDOW = 3;          // Always show this many pages around the current page when there are many dots
	public static String PARM_PREFIX = "PARM_";  // Use this prefix in front of all column names as form field names (parameter names) 
	public static boolean SHOW_ALL_RELATED_TABLES = true;   // Will show that relationships exist even if no access to the table

	public static String TABLE_REF_LIST = "SELECT name as rid, name FROM (select expand(classes) from metadata:schema) ORDER BY name";
	public static String USERS_LIST = "SELECT name as rid, name FROM OUser ORDER BY name";
	public static String ROLES_LIST = "SELECT name as rid, name FROM ORole ORDER BY name";

	public static final int PRIV_CREATE = 1;
	public static final int PRIV_READ = 2;
	public static final int PRIV_UPDATE = 4;
	public static final int PRIV_DELETE = 8;
	public static final int PRIV_ALL = 15;

	// Need to store the data type names by locale and don't want to generate it every time so this is a cache
	static ConcurrentHashMap<Locale,ArrayList<String>> dataTypeNames = new ConcurrentHashMap<>();
	static ConcurrentHashMap<Locale,ArrayList<String>> dataTypeValues = new ConcurrentHashMap<>();
	
	private static void setUpDataTypes(Locale locale) {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            names.add(Message.get(locale, "DATATYPE_TEXT"));
            names.add(Message.get(locale, "DATATYPE_FLOAT"));
            names.add(Message.get(locale, "DATATYPE_INT"));
            names.add(Message.get(locale, "DATATYPE_DECIMAL"));  
            names.add(Message.get(locale, "DATATYPE_DATE"));  // Calendar
            names.add(Message.get(locale, "DATATYPE_DATETIME")); // Calendar and time
            names.add(Message.get(locale, "DATATYPE_BOOLEAN"));   // Checkbox
            names.add(Message.get(locale, "DATATYPE_LINK"));    // PickList
            names.add(Message.get(locale, "DATATYPE_LINKSET"));  // Link Set control
            names.add(Message.get(locale, "DATATYPE_LINKLIST")); // Link List control
            names.add(Message.get(locale, "DATATYPE_LINKMAP"));  // Link map control
            names.add(Message.get(locale, "DATATYPE_BLOB"));  // Image (with thumbnail)
            values.add("DATATYPE_TEXT");
            values.add("DATATYPE_FLOAT");
            values.add("DATATYPE_INT");
            values.add("DATATYPE_DECIMAL");  
            values.add("DATATYPE_DATE");  // Calendar
            values.add("DATATYPE_DATETIME"); // Calendar and time
            values.add("DATATYPE_BOOLEAN");   // Checkbox
            values.add("DATATYPE_LINK");    // PickList
            values.add("DATATYPE_LINKSET");  // Link Set control
            values.add("DATATYPE_LINKLIST"); // Link List control
            values.add("DATATYPE_LINKMAP");  // Link map control
            values.add("DATATYPE_BLOB"); // Image (with thumbnail)
            dataTypeNames.put(locale,names);
            dataTypeValues.put(locale,values);
        }

	public String getPage(DatabaseConnection con, java.util.HashMap<String, String> parms) {
		Locale locale = con.getLocale();
		
		String submit = parms.get("SUBMIT");
		String table = parms.get("TABLENAME");
		String pagest = parms.get("PAGE");
		String sourceTable = parms.get("SOURCETABLENAME");
		if (isNullOrBlank(table)) {
                    if (parms.containsKey("EDIT_ID")) {
                        ODocument d = con.get(parms.get("EDIT_ID"));
                        if (d != null) {
                            table = d.getClassName();
                        } else {
                            return redirect(locale, "permeagility.web.Schema");                            
                        }
                    } else {
			return redirect(locale, "permeagility.web.Schema");
                    }
		}

		String prettyTable = Message.get(locale,"TABLE_"+table);
		if (table != null && ("TABLE_"+table).equals(prettyTable)) {  // No translation
			prettyTable = makeCamelCasePretty(table);
		}
		String title = Message.get(locale, "TABLE_EDITOR", table != null ? prettyTable : "None");
		parms.put("SERVICE", title);

		long page = 1;
		if (pagest != null) {
			try {
				page = Integer.parseInt(pagest);
			} catch (Exception e) {
				System.out.println("Unable to interpret page number "+pagest+" as a number");
			}
		}

		StringBuilder errors = new StringBuilder();

		if (parms.get("ADVANCED_OPTIONS") != null
				|| (submit != null && submit.equals("ADVANCED_OPTIONS"))) {
			return advancedOptions(con, table, parms);
		}

		if (parms.get("RIGHTS_OPTIONS") != null
				|| (submit != null && submit.equals("RIGHTS_OPTIONS"))) {
			return rightsOptions(con, table, parms);
		}
		if (submit != null && (parms.containsKey("UPDATE_ID") || submit.equals("CREATE_ROW"))) {
			if (DEBUG) System.out.println("update_id="+parms.get("UPDATE_ID"));
			if (submit.equals("COPY")) {
					parms.put("EDIT_ID", parms.get("UPDATE_ID"));
					return head(title,getScripts(con))
							+ body(standardLayout(con, parms, 
								errors.toString()
								+form("NEWROW","#",
										paragraph("banner",Message.get(locale, "COPY")+"&nbsp;"+prettyTable)
										+getTableRowFields(con, table, parms)
										+center(submitButton(locale,"CREATE_ROW")+submitButton(locale,"CANCEL"))
								)
							));
			} else if (submit.equals("CREATE_ROW")) {
				if (DEBUG) System.out.println("************ Inserting row");
				if (!insertRow(con,table,parms,errors)) {
					return head(title,getScripts(con))
							+ body(standardLayout(con, parms, 
								errors.toString()
								+form("NEWROW","#",
										paragraph("banner",Message.get(locale, "CREATE")+"&nbsp;"+prettyTable)
										+getTableRowFields(con, table, parms)
										+center(submitButton(locale, "CREATE_ROW")
										+submitButton(locale, "CANCEL"))
								)
							));					
				}
			}else if (submit.equals("DELETE")) {
				if (DEBUG) System.out.println("************ Deleting row");
					if (deleteRow(con, table, parms, errors)) {
						parms.remove("EDIT_ID");
						parms.remove("UPDATE_ID");
						submit = null;
					} else {
						return head(title, getScripts(con))
								+ body(standardLayout(con, parms, errors.toString() + getTableRowForm(con, table, parms) ));
					}
			} else if (submit.equals("UPDATE")) {
				if (DEBUG) System.out.println("In updating row");
				if (updateRow(con, table, parms, errors)) {
					parms.remove("EDIT_ID");
					parms.remove("UPDATE_ID");
				} else {
					return head(title, getScripts(con))
							+ body(standardLayout(con, parms, errors.toString() + getTableRowForm(con, table, parms) ));
				}
			}
			if (sourceTable != null && !sourceTable.equals("")) {  // Go to the source record if it is defined
				if (DEBUG) System.out.println("Table (Cancel) popping sourceTableName="+parms.get("SOURCETABLENAME")+" id="+parms.get("SOURCEEDIT_ID"));
				int lastComma = sourceTable.lastIndexOf(',');
				String sourceId = parms.get("SOURCEEDIT_ID");
				int lastCommaId = sourceId.lastIndexOf(',');
				String oldTable = (lastComma > 0 && lastComma < sourceTable.length() ? sourceTable.substring(lastComma+1) : sourceTable);
				String oldId = (lastCommaId > 0 && lastCommaId < sourceId.length() ? sourceId.substring(lastCommaId+1) : sourceId);
				String newSourceTable = sourceTable.substring(0,(lastComma > 0 ? lastComma : sourceTable.length()));
				String newSourceId = sourceId.substring(0,(lastCommaId > 0 ? lastCommaId : sourceId.length()));
				if (oldId.equals(parms.get("UPDATE_ID"))) { // popping onto itself - pop one more
					if (DEBUG) System.out.println("Prevent popping onto itself, skipping");
					lastComma = newSourceTable.lastIndexOf(',');
					lastCommaId = newSourceId.lastIndexOf(',');
					oldTable = (lastComma > 0 && lastComma < newSourceTable.length() ? newSourceTable.substring(lastComma+1) : newSourceTable);
					oldId = (lastCommaId > 0 && lastCommaId < newSourceId.length() ? newSourceId.substring(lastCommaId+1) : newSourceId);					
					newSourceTable = newSourceTable.substring(0,(lastComma > 0 ? lastComma : newSourceTable.length()));
					newSourceId = newSourceId.substring(0,(lastCommaId > 0 ? lastCommaId : newSourceId.length()));
					if (DEBUG) System.out.println("Popped to: "+oldTable+" "+oldId);
					if (oldId.equals(newSourceId)) {
						if (DEBUG) System.out.println("Removing old source information - at last record");
						newSourceTable = "";
						newSourceId = "";
					}
				}
				return redirect(locale, this, "TABLENAME=" + oldTable + "&EDIT_ID=" + oldId
						+(!oldTable.equals(newSourceTable) && !oldId.equals(newSourceId) ? "&SOURCETABLENAME=" + newSourceTable + "&SOURCEEDIT_ID=" + newSourceId : ""));
			} else {
			return redirect(locale, this, "TABLENAME=" + table);
			}
		}

		if (submit != null && submit.equals("NEW_COLUMN")) {
			String cn = parms.get("NEWCOLUMNNAME");
			String dt = parms.get("NEWDATATYPE");
			String tr = parms.get("NEWTABLEREF");
			
			OType type = null;
			if (dt == null || dt.isEmpty()) {
                        } else if (dt.equals("DATATYPE_FLOAT")) {
				type = OType.DOUBLE;
				tr = null;
			} else if (dt.equals("DATATYPE_INT")) {
				type = OType.LONG;
				tr = null;
			} else if (dt.equals("DATATYPE_BOOLEAN")) {
				type = OType.BOOLEAN;
				tr = null;
			} else if (dt.equals("DATATYPE_TEXT")) {
				type = OType.STRING;
				tr = null;
			} else if (dt.equals("DATATYPE_DATETIME")) {
				type = OType.DATETIME;
				tr = null;
			} else if (dt.equals("DATATYPE_DATE")) {
				type = OType.DATE;
				tr = null;
			} else if (dt.equals("DATATYPE_BLOB")) {
				type = OType.CUSTOM;
				tr = null;
			} else if (dt.equals("DATATYPE_DECIMAL")) {
				type = OType.DECIMAL;
				tr = null;
			} else if (dt.equals("DATATYPE_LINK")) {
				type = OType.LINK;
				if (isNullOrBlank(tr)) {
					errors.append(paragraph("error", Message.get(locale, "LINK_TYPES_NEED_LINK_TABLE")));
				}
			} else if (dt.equals("DATATYPE_LINKLIST")) {
				type = OType.LINKLIST;
				if (isNullOrBlank(tr)) {
					errors.append(paragraph("error", Message.get(locale, "LINK_TYPES_NEED_LINK_TABLE")));
				}
			} else if (dt.equals("DATATYPE_LINKSET")) {
				type = OType.LINKSET;
				if (isNullOrBlank(tr)) {
					errors.append(paragraph("error", Message.get(locale, "LINK_TYPES_NEED_LINK_TABLE")));
				}
			} else if (dt.equals("DATATYPE_LINKMAP")) {
				type = OType.LINKMAP;
				if (isNullOrBlank(tr)) {
					errors.append(paragraph("error", Message.get(locale, "LINK_TYPES_NEED_LINK_TABLE")));
				}
			}
			if (type == null || isNullOrBlank(cn) || isNullOrBlank(dt)) {
				errors.append(paragraph("error", Message.get(locale, "COLUMN_NAME_AND_TYPE_REQUIRED")));
			} else {
				try {
					OClass c = con.getSchema().getClass(table);
					if (c == null) {
						errors.append(paragraph("error", Message.get(locale, "CANNOT_CREATE_COLUMN") + " Cannot find class to create column in table: " + table));							
					} else {
						String camel = makePrettyCamelCase(cn);						
						if (tr != null) {
							Setup.checkCreateColumn(con, c, camel, type, con.getSchema().getClass(tr), errors);
						} else {
							Setup.checkCreateColumn(con, c, camel, type, errors);
						}
						errors.append(paragraph("success", Message.get(locale, "NEW_COLUMN_CREATED")+":&nbsp;"+camel));
						Server.tableUpdated("metadata:schema");
					}
				} catch (Exception e) {
					e.printStackTrace();
					errors.append(paragraph("error", Message.get(locale, "CANNOT_CREATE_COLUMN") + e.getMessage()));
				}
			} 
		}

		if (parms.containsKey("EDIT_ID") && (submit == null || !submit.equals("CREATE_ROW"))) {
			return head(title, getScripts(con))
					+ body(standardLayout(con, parms, getTableRowForm(con, table, parms)));
		}

		parms.remove("EDIT_ID"); // Need to avoid confusing getTableRowForm
		
		// Make the result
		return head(title, getScripts(con))
                    + body(standardLayout(con, parms,  
                        link(this.getClass().getName(),"&lt;"+Message.get(locale,"ALL_TABLES"))
                        +"&nbsp;&nbsp;&nbsp;"
                        +((Security.getTablePriv(con, table) & PRIV_CREATE) > 0 ? popupForm("CREATE_NEW_ROW",null,Message.get(locale,"NEW_ROW"),null,"NAME",
                                        paragraph("banner",Message.get(locale, "CREATE_ROW"))
                                        +getTableRowFields(con, table, parms)
                                        +center(submitButton(locale, "CREATE_ROW"))) : "")
                        +"&nbsp;&nbsp;&nbsp;"
                        +(Security.isDBA(con) ?
                                popupForm("NEWCOLUMN", null, Message.get(locale, "ADD_COLUMN"),null,"NEWCOLUMNNAME", newColumnForm(con))
                                +"&nbsp;&nbsp;&nbsp;"
                                +popupForm("RIGHTSOPTIONS", null, Message.get(locale, "TABLE_RIGHTS_OPTIONS"),null,"XXX", rightsOptionsForm(con,table,parms,""))
                                +"&nbsp;&nbsp;&nbsp;"
                                +popupForm("ADVANCEDOPTIONS", null, Message.get(locale, "ADVANCED_TABLE_OPTIONS"),null,"NEWCOLUMNNAME", advancedOptionsForm(con,table,parms,""))
                                : "") // isDBA switch
                        +"&nbsp;&nbsp;&nbsp;"
                        +link("permeagility.web.Visuility?TYPE=table&ID="+table,"<i>V</i>")    
                        + br() 
                        + errors.toString()
                        + br()
                        + ((Security.getTablePriv(con, table) & PRIV_READ) > 0 ? getTable(con, table, page) : paragraph(Message.get(locale,"NO_PERMISSION_TO_VIEW")))
                    ));
	}

	// Clear all the dataTypes from the list if a message or locale has changed - called by Server
	public static void clearDataTypes() {
		dataTypeNames.clear();
		dataTypeValues.clear();
	}
	
	public String getScripts(DatabaseConnection con) {
		return getDateControlScript(con.getLocale())+getColorControlScript()+getCodeEditorScript()+getScript("d3.js");
	}
	
	public boolean insertRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
            ODocument newDoc = con.create(table);
            for (OProperty column : con.getColumns(table)) {
                Integer type = column.getType().getId();
                String name = column.getName();
                String value = parms.get(PARM_PREFIX+name);
                if (DEBUG) System.out.println("InsertRow(JavaAPI): column "+name+" is a "+type+" and its value is "+value);
                if (!isNullOrBlank(value)) {				
                    if (type == 0) {  // Boolean
                        newDoc.field(name,(value.equals("on") ? true : false));
                    } else if (type == 1 || type == 2 || type == 3 || type == 17) {   // Number (int,long, etc...)
                        try {
                            long longValue = Long.parseLong(value);
                            newDoc.field(name,longValue);
                        } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_NUMBER_VALUE",value)));
                        }						
                    } else if (type == 4 || type == 5 || type == 21) {  // Float - Double - Decimal
                        try {
                            double dubValue = Double.parseDouble(value);
                            newDoc.field(name,dubValue);
                        } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_NUMBER_VALUE", value)));
                        }					
                    } else if (type == 6) {  // Datetime
                        try {
                            newDoc.field(name,value,column.getType());
                        } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_DATE_VALUE", value)));                            
                        }
                    } else if (type == 7) {  // String
                        newDoc.field(name,value);
                    } else if (type == 20) {  // Binary/image (8 = binary, 20 = custom)
                        updateBlob(newDoc, table, name, parms, errors);					
                    } else if (type >= 9 && type <= 12) {  // Embedded types
                        newDoc.field(name,value);
                    } else if (type == 13) {  // Link
                        ODocument linkDoc = null;
                        if (value != null) { 
                            linkDoc = con.get(value);
                            if (linkDoc == null) {
                                System.out.println("INSERT WARNING: attempted to insert with a link field with a document that couldn't be found - will not update this field with questionable data");
                            }
                        };
                        if (value == null || linkDoc != null) {
                            newDoc.field(name,linkDoc);
                        }
                    } else if (type == 14) { // LinkList
                        String[] newValues = {};
                        if (value != null && !value.trim().equals("")) {
                            newValues = value.split(",");
                        }
                        // Add new list
                        List<ODocument> list = new ArrayList<ODocument>();
                        boolean okToUpdate = true;
                        for (String nv : newValues) {
                            ODocument doc = con.get(nv);
                            if (doc != null) {
                                list.add(doc);
                            } else {
                                okToUpdate = false;
                                System.out.println("INSERT WARNING: attempted to add a non-existent document to a list - list will remain unchanged");
                            }
                        }
                        if (okToUpdate) {
                            newDoc.field(name,list);
                        }
                    } else if (type == 15) { // Linkset
                        String[] newValues = {};
                        if (value != null) {  newValues = value.split(","); }
                        // Add new list
                        Set<ODocument> list = new HashSet<ODocument>();
                        boolean okToUpdate = true;
                        for (String nv : newValues) {
                            ODocument doc = con.get(nv);
                            if (doc != null) {
                                list.add(doc);
                            } else {
                                okToUpdate = false;
                                System.out.println("INSERT WARNING: attempted to add a non-existent document to a list - list will remain unchanged");
                            }
                        }
                        if (okToUpdate) {
                            newDoc.field(name,list);
                        }
                    } else if (type == 16) { // LinkMap
                        Map<String,ODocument> map = new HashMap<String,ODocument>();
                        if (DEBUG) System.out.println("Updating LinkMap "+(map == null ? "" : map));
                        String[] newValues = {};
                        if (value != null && !value.trim().equals("")) {
                            newValues = splitCSV(value);
                        }
                        boolean okToUpdate = true;
                        // Add all from new list
                        for (String nv : newValues) {
                            String[] v = nv.split(":",2);
                            try {
                                if (v.length != 2) {
                                    okToUpdate = false;
                                    System.out.println("INSERT WARNING: Cannot parse map value "+nv+" it should be <name>:<cluster>:<record> - field will remain unchanged");
                                }
                                ODocument doc = con.get(v[1]);
                                if (doc != null) {
                                    map.put(v[0], doc);
                                } else {
                                    okToUpdate = false;
                                    System.out.println("INSERT WARNING: attempted to add a non-existent document to a map - map will remain unchanged");																		
                                }
                            } catch (ArrayIndexOutOfBoundsException e) {
                                System.out.println("Could not understand map field:"+nv);
                            }									
                        }
                        if (okToUpdate) {
                            newDoc.field(name,map,OType.LINKMAP);
                        }
                    } else if (type == 19) {  // Date
                        newDoc.field(name,value,column.getType());
                    } else {
                        errors.append(paragraph("error", Message.get(con.getLocale(),"UNKNOWN_FIELD_TYPE",""+type,name)));
                    }
                }
            }
            if (newDoc.isDirty()) {
                try {
                    newDoc.save();
                    errors.append(paragraph("success", Message.get(con.getLocale(), "NEW_ROW_CREATED",(newDoc.isDirty() ? "false" : "true"))));
                    Server.tableUpdated(table);
                    DatabaseConnection.rowCountChanged(table);
                    return true;
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    errors.append(paragraph("error", Message.get(con.getLocale(), "CANNOT_CREATE_ROW")+e.getMessage())+(DEBUG ? "<br>"+xxSmall(sw.toString()) : "")); 
                    if (DEBUG || e instanceof OSecurityAccessException) System.err.println(sw.toString());  // Security messages must go to log
                    return false;
                }
            } else {
                errors.append(paragraph("warning", Message.get(con.getLocale(),"NOTHING_TO_UPDATE")));
                return false;
            }
	}


	public boolean updateBlob(ODocument doc, String table, String blobName, HashMap<String, String> parms, StringBuilder errors) {
            if (doc != null) {
                String blob_temp_file = parms.get(PARM_PREFIX+blobName);
                String blob_file_name = parms.get(PARM_PREFIX+blobName+"_FILENAME");
                String blob_type = parms.get(PARM_PREFIX+blobName+"_TYPE");
                if (blob_temp_file != null && !blob_temp_file.trim().equals("")) {
                    if (DEBUG) System.out.println("Writing blob "+blob_file_name+" type:"+blob_type+" file:"+blob_temp_file);
                    ORecordBytes record = new ORecordBytes();
                    try {
                        record.fromInputStream(new FileInputStream(blob_temp_file));
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    record.save();
                    doc.field(blobName,record);
                    Thumbnail.createThumbnail(table, doc, blobName);
                }
            } else {
                System.out.println("Table.updateBlobs() - document is null");
            }
            return true;
	}

	public boolean updateRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
            if (DEBUG) System.out.println("In updateRow (Java API) of table "+table);
            ODocument updateRow = con.get(parms.get("UPDATE_ID"));
            if (updateRow != null) {
                Collection<OProperty> columns = con.getColumns(table);
                for (OProperty column : columns) {
                    String columnName = column.getName();
                    Integer type = column.getType().getId();
                    String newValue = parms.get(PARM_PREFIX+columnName);
                    if (newValue == null && !parms.containsKey(PARM_PREFIX+columnName)) {
                        continue;  // Don't update if not specified in parameters
                    }
                    if (DEBUG) System.out.println("updating "+columnName+" of type "+type+" with value "+newValue);
                    if (newValue != null) {
                        if (newValue.equals("null")) {
                                newValue = null;
                        }
                    }
                    if (type == 0) { // Boolean
                        Boolean oldval = updateRow.field(columnName);
                        boolean oldbool = false;
                        boolean newbool = false;
                        if (oldval != null) {
                            oldbool = oldval.booleanValue();
                        }
                        if (newValue != null) {
                            if (newValue.equalsIgnoreCase("on") || newValue.equalsIgnoreCase("true")
                                            || newValue.equalsIgnoreCase("yes")) {
                                    newbool = true;
                            }
                        }
                        if (oldbool != newbool) {
                            updateRow.field(columnName,newbool);
                        }
                    } else if (type == 1 || type == 2 || type == 3) { // Whole number
                        Number originalValue = updateRow.field(columnName);
                        Number newVal = null;
                        if (newValue != null) {
                            try {
                                newVal = Long.parseLong(newValue);
                            } catch (Exception e) {
                                errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_NUMBER_VALUE",newValue)+" " + e.getMessage()));
                            }
                        }
                        if ((newValue != null && originalValue != null && newVal != null && !newVal.equals(originalValue)) 
                                || (newValue == null && originalValue != null)
                                || (originalValue == null && newValue != null)) {
                            updateRow.field(columnName,newVal);
                        }
                    } else if (type == 4 || type == 5) { // float, double
                        Double originalValue = updateRow.field(columnName);
                        Double newVal = null;
                        if (newValue != null) {
                            try {
                                newVal = Double.parseDouble(newValue);
                            } catch (Exception e) {
                                errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_READ_NUMBER_VALUE",newValue)+" " + e.getMessage()));
                            }
                        }
                        if ((newValue != null && originalValue != null && newVal != null && !newVal.equals(originalValue)) 
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                                updateRow.field(columnName,newVal);
                        }
                    } else if (type == 21) { // decimal
                        BigDecimal originalValue = updateRow.field(columnName);
                        BigDecimal newVal = null;
                        if (newValue != null) {
                            try {
                                newVal = new BigDecimal(newValue);
                            } catch (Exception e) {
                                errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_READ_NUMBER_VALUE",newValue)+" "+e.getMessage()));
                            }
                        }
                        if ((newValue != null && originalValue != null && newVal != null && !newVal.equals(originalValue)) 
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                                updateRow.field(columnName,newVal);
                        }
                    } else if (type == 6) {  // Datetime
                        Date originalValue = updateRow.field(columnName);
                        Date newDate = parseDatetime(con.getLocale(),newValue);
                        if (newValue != null && newDate == null) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_DATE_VALUE",newValue)));							
                        } else {
                            if (DEBUG) System.out.println("Updating Datetime "+(originalValue == null ? "" : originalValue.toString())+" to "+newDate);
                            if ((newValue != null && originalValue != null && !newDate.equals(originalValue)) 
                                || (newValue == null && originalValue != null)
                                || (originalValue == null && newValue != null)) {
                                    updateRow.field(columnName,newDate);
                            }	
                        }
                    } else if (type == 7) { // String
                        String originalValue = updateRow.field(columnName);
                        if ((newValue != null && originalValue != null && !newValue.equals(originalValue)) 
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                                if (columnName.toUpperCase().endsWith("PASSWORD") && (newValue == null || newValue.equals(""))) {
                                    System.out.println("Not updating null password requested by user "+con.getUser());
                                } else {
                                    updateRow.field(columnName,newValue);
                                }
                        }
                    } else if (type == 20) {  // Blob
                        String blob_name = parms.get(PARM_PREFIX+columnName); 
                        if (blob_name != null && !blob_name.trim().equals("")) {
                            if (DEBUG) System.out.println("Updating BLOB");
                            updateBlob(updateRow,table,columnName,parms,errors);
                        }
                    } else if (type == 9 || type==10 || type==11 || type==12) { // Embedded types - treat like a string (without quotes) - user beware
                        Object originalValue = updateRow.field(columnName);
                        if ((newValue != null && originalValue != null && !newValue.equals(originalValue)) // They will always detect a change because the string is formatted differently
                            || (newValue == null && originalValue != null)  // This could be considered a bug
                            || (originalValue == null && newValue != null)) {
                            if (DEBUG) System.out.println("Embedded value changed");
                                updateRow.field(columnName,newValue);
                        }

                    } else if (type == 13) { // Link
                        ODocument o = updateRow.field(columnName);
                        if (DEBUG) System.out.println("Updating Link "+(o == null ? "" : o.getIdentity().toString()));
                        String originalValue = (o == null ? null : o.getIdentity().toString().substring(1));
                        if ((newValue != null && originalValue != null && !newValue.equals(originalValue)) 
                                  || (newValue == null && originalValue != null)
                                  || (originalValue == null && newValue != null)) {
                                ODocument linkDoc = null;
                                if (newValue != null) { 
                                        linkDoc = con.get(newValue);
                                        if (linkDoc == null) {
                                                System.out.println("UPDATE WARNING: attempted to update a link field with a document that couldn't be found - will not update this field with questionable data");
                                        }
                                };
                                if (newValue == null || linkDoc != null) {
                                        updateRow.field(columnName,linkDoc);
                                }
                        }
                    } else if (type == 14) { // LinkList (Ordered and can contain duplicates)
                        List<ODocument> o = updateRow.field(columnName);
                        if (DEBUG) System.out.println("Updating LinkList "+(o == null ? "" : o));
                        String[] newValues = {};
                        if (newValue != null && !newValue.trim().equals("")) {
                            newValues = newValue.split(",");
                        }
                        // Remove all from original list as this list is ordered
                        if (o == null) { 
                            o = new ArrayList<ODocument>(); 
                        } else {
                            o.clear();
                        }
                        // Add new list
                        boolean okToUpdate = true;
                        for (String nv : newValues) {
                            ODocument doc = con.get(nv);
                            if (doc != null) {
                                o.add(doc);
                            } else {
                                okToUpdate = false;
                                System.out.println("UPDATE WARNING: attempted to add a non-existent document to a list - list will remain unchanged");
                            }
                        }
                        if (okToUpdate) {
                            updateRow.field(columnName,o);
                        }
                    } else if (type == 15) { // Linkset
                        Set<ODocument> o = updateRow.field(columnName);
                        if (DEBUG) System.out.println("Updating LinkSet "+(o == null ? "" : o));
                        String[] newValues = {};
                        if (newValue != null && !newValue.trim().equals("")) {
                            newValues = newValue.split(",");
                        }
                        boolean okToUpdate = true;
                        // Remove any from original list where not in new list
                        if (o == null) {
                            o = new HashSet<ODocument>();
                        } else {
                            Object[] oldList = o.toArray();  // Make a copy of list to avoid concurrent modification exception
                            for (Object d : oldList) {
                                boolean found = false;
                                if (d != null) {
                                    String id = ((ODocument)d).getIdentity().toString().substring(1);
                                    for (String nv : newValues) {
                                        if (id.equals(nv)) { found = true; }
                                    }
                                    if (!found) {
                                        o.remove(d);
                                    }
                                }
                            }
                        }
                        // Add any from new list where not in original list
                        for (String nv : newValues) {
                            boolean found = false;
                            if (o != null) {
                                for (ODocument d : o) {
                                    if (d != null && d.getIdentity().toString().substring(1).equals(nv)) { found = true; }
                                }
                            }
                            if (!found) {
                                ODocument doc = con.get(nv);
                                if (doc != null) {
                                    o.add(doc);
                                } else {
                                    okToUpdate = false;
                                    System.out.println("UPDATE WARNING: attempted to add a non-existent document to a set - set will remain unchanged");									
                                }
                            }
                        }
                        if (okToUpdate && o != null) {
                            updateRow.field(columnName,o);
                        }
                    } else if (type == 16) { // LinkMap
                        Map<String,ODocument> o = updateRow.field(columnName);
                        if (DEBUG) System.out.println("Updating LinkMap "+(o == null ? "" : o));
                        String[] newValues = {};
                        if (newValue != null && !newValue.trim().equals("")) {
                            newValues = splitCSV(newValue);
                        }
                        // Remove all from original map as this list field is ordered
                        if (o == null) {
                            o = new HashMap<String,ODocument>();
                        } else {
                            o.clear();
                        }
                        boolean okToUpdate = true;
                        // Add all from new list
                        for (String nv : newValues) {
                            String[] v = nv.split(":",2);
                            try {
                                if (v.length != 2) {
                                    okToUpdate = false;
                                    System.out.println("UPDATE WARNING: Cannot parse map value "+nv+" it should be <name>:<cluster>:<record> - field will remain unchanged");
                                }
                                ODocument doc = con.get(v[1]);
                                if (doc != null) {
                                    o.put(v[0], doc);
                                } else {
                                    okToUpdate = false;
                                    System.out.println("UPDATE WARNING: attempted to add a non-existent document to a map - map will remain unchanged");																		
                                }
                            } catch (ArrayIndexOutOfBoundsException e) {
                                System.out.println("Could not understand map field:"+nv);
                            }									
                        }
                        if (okToUpdate) {
                            updateRow.field(columnName,o,OType.LINKSET);
                        }
                    } else if (type == 19) {  // Date
                        Date originalValue = updateRow.field(columnName);
                        Date newDate = parseDate(con.getLocale(),newValue);
                        if (newValue != null && newDate == null) {
                            errors.append(paragraph("error", Message.get(con.getLocale(),"INVALID_DATE_VALUE",newValue)));							
                        } else {
                            if (DEBUG) System.out.println("Updating Date/Datetime "+(originalValue == null ? "" : originalValue.toString())+" to "+newDate);
                            if ((newValue != null && originalValue != null && !newDate.equals(originalValue)) 
                                || (newValue == null && originalValue != null)
                                || (originalValue == null && newValue != null)) {
                              updateRow.field(columnName,newDate,column.getType());
                            }	
                        }
                    }
                }
                if (updateRow.isDirty()) {
                    try {
                        updateRow.save();
                        errors.append(paragraph("success", Message.get(con.getLocale(), "ROW_UPDATED",(updateRow.isDirty() ? "false" : "true"))));
                        Server.tableUpdated(table);
                        return true;
                    } catch (Exception e) {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        errors.append(paragraph("error", Message.get(con.getLocale(), "CANNOT_UPDATE")+e.getMessage())+(DEBUG ? "<br>"+xxSmall(sw.toString()) : "")); 
                        if (DEBUG || e instanceof OSecurityAccessException) System.err.println(sw.toString());  // Security messages will always go to log
                        return false;
                    }
                } else {
                    errors.append(paragraph("warning", Message.get(con.getLocale(),"NOTHING_TO_UPDATE")));
                    return false;
                }
            } else {
                if (DEBUG) System.out.println("Error in permeagility.web.Table:updateRow: Could not find row "+parms.get("UPDATE_ID"));
                errors.append(paragraph("error",Message.get(con.getLocale(),"NOTHING_TO_UPDATE")+" "+parms.get("UPDATE_ID")+" not found"));
                return false;
            }
	}

        
	public boolean deleteRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
            ODocument doc = con.get(parms.get("UPDATE_ID"));
            if (doc != null) {
                try {
                    doc.delete();
                    Server.tableUpdated(table);
                    DatabaseConnection.rowCountChanged(table);
                    errors.append(paragraph("success", Message.get(con.getLocale(), "ROW_DELETED") ));
                    return true;
                } catch (Exception e) {
                    errors.append(paragraph("error", Message.get(con.getLocale(), "ROW_CANNOT_BE_DELETED")  + e.getMessage()));
                    return false;
                }
            } else {
		errors.append(paragraph("error",Message.get(con.getLocale(), "ROW_CANNOT_BE_DELETED") +" record not found"));
                return false;
            }
        }



	public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
		String edit_id = parms.get("EDIT_ID");
		
		if (table == null) return paragraph("error","null passed to table row form");
		
		// Cannot view abstract class directly - redirect to the actual record's class
		OClass tclass = con.getSchema().getClass(table);
		if (tclass == null) return paragraph("error","cannot find class "+table);

		if (DEBUG) System.out.println("Table.getTableRowForm: table="+table+" class.isAbstract="+tclass.isAbstract());
		if (tclass.isAbstract()) {  // If table name is abstract, get the table name from the document itself
                    ODocument d = con.get(edit_id);
                    if (d != null) {
                        OClass c = d.getSchemaClass();
                        table = c.getName();
                        parms.put("TABLENAME", table);
                    }
		}

		String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
		return (con.getUser().equals("guest") ? "" : link(this.getClass().getName()+"?TABLENAME="+table,Message.get(con.getLocale(), "ALL_ROWS_IN_TABLE",makeCamelCasePretty(table))))
                        + "&nbsp;&nbsp;" + link("permeagility.web.Visuility?TYPE=row&ID="+edit_id,"<i>V</i>")    
			+ getLinkTrail(con, parms.get("SOURCETABLENAME"),parms.get("SOURCEEDIT_ID")) 
			+ paragraph("banner", (edit_id == null ? Message.get(con.getLocale(), "CREATE_ROW") 
					: Message.get(con.getLocale(), "UPDATE") + "&nbsp;" + makeCamelCasePretty(table)))
			+ form(formName, 
                            getTableRowFields(con, table, parms)
                            + center((edit_id == null 
                              ? ((Security.getTablePriv(con, table) & PRIV_CREATE) > 0 ? submitButton(con.getLocale(), "CREATE_ROW") : "")
                              : ((Security.getTablePriv(con, table) & PRIV_UPDATE) > 0 ? submitButton(con.getLocale(), "UPDATE") : "") 
                                    + "&nbsp;&nbsp;"
                                    + submitButton(con.getLocale(), "CANCEL")))
                            +paragraph("delete",
                              (edit_id != null && (Security.getTablePriv(con, table) & PRIV_CREATE) > 0 ? submitButton(con.getLocale(), "COPY") : "") + "&nbsp;&nbsp;"
                            + (edit_id != null && (Security.getTablePriv(con, table) & PRIV_DELETE) > 0 ? deleteButton(con.getLocale()) : ""))
			)
			+ getTableRowRelated(con,table,parms);
	}

	private String getLinkTrail(DatabaseConnection con, String tables, String ids) {
            if (tables == null || tables.equals("")) return "";
            if (ids == null || ids.equals("")) return "";
            String tabs[] = tables.split(",");
            String tabIds[] = ids.split(",");
            StringBuilder ret = new StringBuilder();
            for (int i=0; i<tabs.length; i++) {
                    String t = tabs[i];
                    String id = tabIds[i];
                    ret.append("<br>&nbsp;&nbsp;&nbsp;"+link(this.getClass().getName()+"?TABLENAME="+t+"&EDIT_ID="+id, makeCamelCasePretty(t)+" ("+getDescriptionFromTable(con, t, id)+")"));
            }
            return ret.toString();
	}

	public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms) {
		return getTableRowFields(con, table, parms, null);
	}
	
	/** Returns the fields for a table - can be for insert of a new row or update of an existing (as specified by the EDIT_ID in parms) */
	public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride) {
            String edit_id = (parms != null ? parms.get("EDIT_ID") : null);
            ODocument initialValues = null;
            if (edit_id != null) {
                QueryResult initrows = con.query("SELECT * FROM #" + edit_id);
                if (initrows != null && initrows.size() == 1) {
                    initialValues = initrows.get(0);
                } else {
                    if (DEBUG) System.out.println("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing rows="+ initrows.size());
                    return paragraph("error", Message.get(con.getLocale(), "ONLY_ONE_ROW_CAN_BE_EDITED"));
                }
            } else {
                if (DEBUG) System.out.println("getTableRowFields: No EDIT_ID specified");
            }
            StringBuilder fields = new StringBuilder();
            StringBuilder hidden = new StringBuilder();
            if (edit_id != null) {
                hidden.append(hidden("UPDATE_ID", edit_id));
            }
            String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
            Collection<OProperty> columns = con.getColumns(table, columnOverride);
            if (columns != null) {
                for (OProperty column : columns) {
                    String name = column.getName();
                    if (parms.get("FORCE_"+name) != null) {
                            hidden.append(hidden(PARM_PREFIX+name,parms.get("FORCE_"+name)));
                            continue;
                    }
                    // Added to support request approval using parms to prime the initial values
//			if (initialValues.field(name) == null && parms.get(name) != null) {
//				initialValues.field(name, parms.get(name));
//			}
                    fields.append(getColumnAsField(table, column, initialValues, con, formName, edit_id, parms));
                }
                return hidden.toString()+center(table("data", fields.toString()));
            } else {
                return null;
            }
	}

	/**
	 * @param column - column information (name, type, etc...)
	 * @param initialValues - document
	 * @param con - connection/context
	 * @param formName - form to make field part of
	 * @param edit_id - record id of the value to be edited (the identity of initialValues would be misleading on a new record) 
	 * @return  a table row for a given column in the document
	 */
	private String getColumnAsField(String table, OProperty column, ODocument initialValues, DatabaseConnection con, String formName, String edit_id, HashMap<String,String> parms) {
            Integer type = column.getType().getId();
            String name = column.getName();
            String prettyName = makeCamelCasePretty(name);
            String trName = Message.get(con.getLocale(),"COLUMN_"+name);
            if (!trName.equals("COLUMN_"+name)) {
                prettyName = trName;
            }
            String label = column("label",prettyName);

            if (DEBUG) System.out.println("Table.getColumnAsField() " + name + " is a " + type);

            Object initialValue;
            try {
                initialValue = initialValues.field(name);
            } catch (Exception e) {
                initialValue = null;
            }
            if (DEBUG) System.out.println(name+" InitialValue="+(type != 20 ? initialValue : "binary"));
            if (initialValue == null && edit_id != null) {
                initialValue = parms.get(PARM_PREFIX+name);  // Need to load parms with values
            }

            if (type == 0) {
                return row(label + column(checkbox(PARM_PREFIX+name, (initialValue == null ? false : new Boolean(initialValue.toString())))));

            // Number
            } else if (type == 1 || type == 2 || type == 3 || type == 4 || type == 5 || type == 17 || type == 21) {  
                List<String> pickValues = Server.getPickValues(table, name);
                if (pickValues != null) {
                    return row(label + column(createList(con.getLocale(), PARM_PREFIX+name, initialValue != null ? initialValue.toString() : null, pickValues, null, false, null, true)));
                }
                return row(label + column(input("number", PARM_PREFIX+name, initialValue)));
            // Datetime
            } else if (type == 6) { 
                return row(label + column(
                    getDateTimeControl(formName, PARM_PREFIX+name, 
                        (initialValue != null && initialValue instanceof Date 
                                ? formatDatetime(con.getLocale(),(Date)initialValue) 
                                : "")
                    )));
            // Date
            } else if (type == 19) { 
                return row(label + column(getDateControl(formName, PARM_PREFIX+name, 
                (initialValue != null && initialValue instanceof Date 
                        ? formatDate(con.getLocale(),(Date)initialValue,DATE_FORMAT) 
                        : "")
                )));
            // Password (String)
            } else if (type == 7 && name.toUpperCase().endsWith("PASSWORD")) {
                return row(label + column(password(PARM_PREFIX+name, null, 15)));
            // Colour (String)
            } else if (type == 7 && (name.toUpperCase().endsWith("COLOR") || name.toUpperCase().endsWith("COLOUR"))) {
                if (DEBUG) System.out.println("Doing color field "+initialValues);
                return row(label + column(getColorControl(formName,PARM_PREFIX+name,(String)initialValue)));
            // SQL (String)
            } else if (type == 7 && name.toUpperCase().endsWith("SQL")) {
                    if (DEBUG) System.out.println("Doing SQL Editor field "+name);
                    return row(label + column(getCodeEditorControl(formName,PARM_PREFIX+name,(String)initialValue,"text/x-sql")));
            // JSON (String)
            } else if (type == 7 && name.toUpperCase().endsWith("JSON")) {
                    if (DEBUG) System.out.println("Doing JSON Editor field "+name);
                    return row(label + column(getCodeEditorControl(formName,PARM_PREFIX+name,(String)initialValue,"application/json")));
            // Script-R (String)
            } else if (type == 7 && name.toUpperCase().endsWith("RSCRIPT")) {
                    if (DEBUG) System.out.println("Doing R Code Editor field "+name);
                    return row(label + column(getCodeEditorControl(formName,PARM_PREFIX+name,(String)initialValue,"text/x-rsrc")));
            // Script-Javascript (String)
            } else if (type == 7 && (name.toUpperCase().endsWith("SCRIPT") || name.toUpperCase().endsWith("CODE"))) {
                    if (DEBUG) System.out.println("Doing Javascript Code Editor field "+name);
                    return row(label + column(getCodeEditorControl(formName,PARM_PREFIX+name,(String)initialValue,"text/javascript")));
            // Style-CSS (String)
            } else if (type == 7 && (name.toUpperCase().endsWith("STYLE"))) {
                    if (DEBUG) System.out.println("Doing CSS Code Editor field "+initialValues);
                    return row(label + column(getCodeEditorControl(formName,PARM_PREFIX+name,(String)initialValue,"css")));
            // String
            } else if (type == 7) {  
                List<String> pickValues = Server.getPickValues(table, name);
                if (pickValues != null) {
                    return row(label + column(createList(con.getLocale(), PARM_PREFIX+name, initialValue != null ? initialValue.toString() : null, pickValues, null, false, null, true)));
                }
                if (initialValue != null && ((String) initialValue).length() > TEXT_AREA_THRESHOLD || name.equals("description")) {
                    int linecount = (initialValue != null ? countLines((String) initialValue) : 0);
                    return row(label + column(textArea(PARM_PREFIX+name, initialValue, (linecount > 2 ? linecount + 3 : 3), TEXT_AREA_WIDTH)));
                } else {
                    int length = 20;
                    if (initialValue != null && initialValue.toString().length() > 20) {
                        length = initialValue.toString().length() + 5;
                    }
                    return row(label + column(input("text", PARM_PREFIX+name, initialValue, length)));
                }
            // Binary
            } else if (type == 20) { // 8 = binary, 20 = custom 
                StringBuilder desc = new StringBuilder();
                if (edit_id != null) {
                    String nail = null;
                    String blobid = Thumbnail.getThumbnailId(table, edit_id, name, desc);
                    if (blobid != null) {
                        nail = Thumbnail.getThumbnailLink(con.getLocale(),blobid, desc.toString());
                    } else {
                        nail = "<div title=\""+Message.get(con.getLocale(), "THUMBNAIL_NOT_FOUND",name, edit_id+"\">"+Message.get(con.getLocale(),"OPTION_NONE")+"</div>");					
                    } 
                    return row(label + column(nail+fileInput(PARM_PREFIX+name)));
                } else {
                    return row(label + column(fileInput(PARM_PREFIX+name)));
                }
            // Embedded
            } else if (type == 9) {  
                String val = (initialValue == null ? "" : initialValue.toString());
                return row(label + column(textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));
            // Embedded list
            } else if (type == 10) {  
                String val = (initialValue == null ? "" : initialValue.toString());
                // convert val to JSON format for editing directly
                if (initialValue != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[\n");
                    if (initialValue instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> l = (List<Object>)initialValue;
                        String comma = " ";
                        for (Object o : l.toArray()) {
                            if (o instanceof String) {
                                sb.append(comma+"\""+o+"\"\n");
                            } else {
                                sb.append(comma+o+"\n");
                            }
                            comma = ",";
                        }
                        sb.append("]");
                        val = sb.toString();
                    }
                }
                return row(label + column(50, textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));
            // Embedded set
            } else if (type == 11) {  
                String val = (initialValue == null ? "" : initialValue.toString());
                // convert val to JSON format for editing directly
                if (initialValue != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[\n");
                    @SuppressWarnings("unchecked")
                    Set<Object> l = (Set<Object>)initialValue;
                    String comma = " ";
                    for (Object o : l.toArray()) {
                        if (o instanceof String) {
                            sb.append(comma+"\""+o+"\"\n");
                        } else {
                            sb.append(comma+o+"\n");
                        }
                        comma = ",";
                    }
                    sb.append("]");
                    val = sb.toString();
                }
                return row(label + column(textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));
            // Embedded map
            } else if (type == 12) {  
                String val = (initialValue == null ? "" : initialValue.toString());
                // convert val to JSON format for editing directly
                if (initialValue != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\n");
                    @SuppressWarnings("unchecked")
                    Map<String,Object> m = (Map<String,Object>)initialValue;
                    String comma = " ";
                    for (String key : m.keySet()) {
                        Object o = m.get(key);
                        sb.append(comma+"\""+key+"\":"+(o instanceof String ? "\""+o+"\"" : o)+"\n");
                        comma = ",";
                    }
                    sb.append("}");
                    val = sb.toString();
                }
                return row(label + column(textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));
            // Single link
            } else if (type == 13 && column.getLinkedClass() != null) {
                String v = null;
                if (initialValue != null && initialValue instanceof ODocument) {
                    v = ((ODocument)initialValue).getIdentity().toString().substring(1);
                }
                return row(label + column(createListFromTable(PARM_PREFIX+name, (v == null ? "" : v), con, column.getLinkedClass().getName(), null, true, null, true)
                    +(initialValues == null || initialValue == null 
                            ? "" 
                            : linkNewWindow(this.getClass().getName()+"?TABLENAME="+column.getLinkedClass().getName()+"&EDIT_ID="+v,Message.get(con.getLocale(), "GOTO_ROW")))
                ));
            // Link list
            } else if (type == 14) {
                List<ODocument> l = null;
                try { l = initialValues.field(name); } catch (NullPointerException e) { } // It will do this if it doesn't exist
                String linkedClass = column.getLinkedClass().getName();
                return row(label + columnNoWrap(linkListControl(con, PARM_PREFIX+name, linkedClass, getCache().getResult(con,getQueryForTable(con, linkedClass)), con.getLocale(), l)));
            // Link set
            } else if (type == 15) {
                Set<ODocument> l = null;
                try {  l = initialValues.field(name);  } catch (NullPointerException e) { }  // It will do this if it doesn't exist
                //System.out.println("linkset size="+l.size());			
                String linkedClass = column.getLinkedClass().getName();
                return row(label + columnNoWrap(linkSetControl(con, PARM_PREFIX+name, linkedClass, getCache().getResult(con,getQueryForTable(con, linkedClass)), con.getLocale(), l)));
            // Link map
            } else if (type == 16) {
                Map<String,ODocument> l = null;
                try {  l = initialValues.field(name);  } catch (NullPointerException e) { }  // It will do this if it doesn't exist
                if (l != null && DEBUG) System.out.println("linkmap size="+l.size());			
                String linkedClass = column.getLinkedClass().getName();
                return row(label + columnNoWrap(linkMapControl(con,PARM_PREFIX+name, linkedClass, getCache().getResult(con,getQueryForTable(con, linkedClass)), con.getLocale(), l)));
            } else {
                System.out.println("Table.GetColumnAsField: Unrecognized type: "+type);
                return row(label + column(input("other", PARM_PREFIX+name, initialValue)));
            }
	}

	public String getTableRowRelated(DatabaseConnection con, String table, HashMap<String, String> parms) {
            StringBuilder sb = new StringBuilder();
            Stack<String> tables = new Stack<>();
            Stack<String> columns = new Stack<>();
            Stack<OType> types = new Stack<>();

            String edit_id = parms.get("EDIT_ID");

            for (OClass c : con.getSchema().getClasses()) {
                for (OProperty p : c.properties()) {
                    if (p.getLinkedClass() != null && p.getLinkedClass().getName().equals(table)) {
                        if (SHOW_ALL_RELATED_TABLES || (Security.getTablePriv(con, c.getName()) & PRIV_READ) > 0) {
                            tables.push(c.getName());
                            columns.push(p.getName());
                            types.push(p.getType());
                        }
                    }
                }
            }

            // Add to or start the bread crumb so we can find our way back from a submit
            String sourceTable = parms.get("SOURCETABLENAME");
            String sourceId = parms.get("SOURCEEDIT_ID");
            String newSourceTable = null;
            String newSourceId = null;
            if (sourceTable != null && !sourceTable.equals("") && sourceId != null && parms.get("SUBMIT") == null) {
                String ids[] = sourceId.split(",");
                String lastId = ids[ids.length-1];
                if (!edit_id.equals(lastId)) {
                    newSourceTable = sourceTable+","+table;
                    newSourceId = sourceId+","+edit_id;
                } 
            } else {
                newSourceTable = table;
                newSourceId = edit_id;
            }			
            parms.put("SOURCEEDIT_ID", (newSourceId != null ? newSourceId : edit_id));
            parms.put("SOURCETABLENAME", (newSourceTable != null ? newSourceTable : table));

            if (DEBUG) System.out.println("getTableRowRelated "+tables.size());
	    while (!tables.isEmpty()) {
                String relTable = tables.pop();
                String fkColumn = columns.pop();
                OType fkType = types.pop();

                int priv = Security.getTablePriv(con, table);
                //System.out.println("Privilege on table "+table+" for user "+con.getUser()+" = "+priv);

                HashMap<String,String> fkParms = new HashMap<>();
                fkParms.put("FORCE_"+fkColumn, edit_id);

                String hiddenFields = hidden("TABLENAME", relTable)
                    + (newSourceId != null ? hidden("SOURCEEDIT_ID", newSourceId) : "")
                    + (newSourceTable != null ? hidden("SOURCETABLENAME", newSourceTable) : "");
                sb.append(
                    paragraph("banner",(table.equals(fkColumn) ?  makeCamelCasePretty(relTable) : makeCamelCasePretty(relTable) + " (" + makeCamelCasePretty(fkColumn) + ")"))
                    + ((priv & PRIV_CREATE) > 0 ? popupForm("CREATE_NEW_ROW_"+relTable,"permeagility.web.Table",Message.get(con.getLocale(),"NEW_ROW"),null,"NAME",
                        paragraph("banner",Message.get(con.getLocale(), "CREATE_ROW"))
                        +hiddenFields
                        +getTableRowFields(con, relTable, fkParms)  // send fkcolumn data in parms
                        +center(submitButton(con.getLocale(), "CREATE_ROW"))) : "")
                    + "&nbsp;&nbsp;&nbsp;"
                    + (Security.isDBA(con) 
                      ? popupForm("NEWCOLUMN_"+relTable, null, Message.get(con.getLocale(), "ADD_COLUMN"),null,"NEWCOLUMNNAME",
                            hiddenFields
                            +newColumnForm(con))
                       + "&nbsp;&nbsp;&nbsp;"
                       + popupForm("RIGHTSOPTIONS_"+relTable, null, Message.get(con.getLocale(), "TABLE_RIGHTS_OPTIONS"),null,"XXX",
                       hiddenFields
                       + rightsOptionsForm(con,relTable,null,"")) 
                     : "")
                    + br() 
                    + ((Security.getTablePriv(con, relTable) & PRIV_READ) > 0 ?
                        (fkType == OType.LINKLIST || fkType == OType.LINKSET || fkType == OType.LINKMAP || fkType == OType.LINKBAG 
                            ? (fkType == OType.LINKMAP ? getTableWhere(con,parms,relTable,fkColumn,"containsvalue",edit_id,-1) 
                                                    :getTableWhere(con,parms,relTable,fkColumn,"contains",edit_id,-1))
                            : getTableWhere(con,parms,relTable,fkColumn,edit_id,-1)  // Need to pass parms so can add source to URL
                        )
                    : Message.get(con.getLocale(),"NO_ACCESS_TO_TABLE"))
		);
	    }
	    return sb.toString();
	}

	public String newColumnForm(DatabaseConnection con) {
            Locale l = con.getLocale();
            String show = "ng-show=\"NEWDATATYPE == 'DATATYPE_LINK' || NEWDATATYPE == 'DATATYPE_LINKLIST' || NEWDATATYPE == 'DATATYPE_LINKSET' || NEWDATATYPE == 'DATATYPE_LINKMAP' \"";
            return
                paragraph("banner",Message.get(l, "NEW_COLUMN"))
                + getDatatypeList(l,"NEWDATATYPE","DATATYPE_TEXT", "ng-model=\"NEWDATATYPE\" ng-init=\"NEWDATATYPE='DATATYPE_TEXT'\"")
                + createListFromCache("NEWTABLEREF", null, con, TABLE_REF_LIST, show, false, null, true)
                + br()
                + input("NEWCOLUMNNAME", "")
                + br()
                + center(submitButton(l, "NEW_COLUMN"));
	}
	
        public static String getDatatypeList(Locale l, String name, String selected, String options) {
            if (dataTypeNames.get(l) == null) { setUpDataTypes(l); }
            return selectList(l, name, selected, dataTypeNames.get(l), dataTypeValues.get(l), options, false, null, true);
        }
        
	/** Get the EDIT_ID from the parms, get the document and populate the parms with the document's field data */
	public HashMap<String, String> getTableRowParameters(DatabaseConnection con, String schema, String table, HashMap<String, String> parms) {
            if (DEBUG) System.out.println("getTableRowParameters: Getting row and injecting into parameters");
            String edit_id = parms.get("EDIT_ID");
            if (edit_id != null) {
                QueryResult rows = con.query("SELECT FROM #" + edit_id);
                if (rows != null && rows.size() == 1) {
                    String[] keys = rows.getColumns();
                    for (int i=0; i<keys.length; i++) {
                        String value = rows.getStringValue(0,keys[i]);
                        parms.put(keys[i], value);
                        if (DEBUG) System.out.println("Injected "+keys[i]+"="+value);
                    }
                } else {
                    if (DEBUG) System.out.println("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing rows="+ rows.size());
                }
            } else {
                System.out.println("getTableRowParameters: EDIT_ID not specified");
            }
            return parms;
	}

	public String getTable(DatabaseConnection con, String table) {
            String query = "SELECT FROM " + table;
            return getTable(con, null,table,query,null,0);
	}

	public String getTable(DatabaseConnection con, String table, long page) {
            String query = "SELECT FROM " + table;
            return getTable(con, null,table,query,null,page);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String columnValue) {
            String query = "SELECT FROM " + table + " WHERE "+column+" = #"+columnValue;
            return getTable(con, null,table,query,column, 0);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String operator, String columnValue) {
            String query = "SELECT FROM " + table + " WHERE "+column+" "+operator+" #"+columnValue;
            return getTable(con, null,table,query,column, 0);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String columnValue, long page) {
            String query = "SELECT FROM " + table + " WHERE "+column+"= #"+columnValue;
            return getTable(con, null,table,query,column,page);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String operator, String columnValue, long page) {
            String query = "SELECT FROM " + table + " WHERE "+column+" "+operator+" #"+columnValue;
            return getTable(con, null,table,query,column,page);
	}

	public String getTable(DatabaseConnection con, String table, String query, String hideColumn, long page) {
            return getTable(con, null, table, query, hideColumn, page, null);
	}

	public String getTable(DatabaseConnection con, HashMap<String,String> parms, String table) {
            String query = "SELECT FROM " + table;
            return getTable(con,table,query,null,0);
	}

	public String getTable(DatabaseConnection con, HashMap<String,String> parms, String table, long page) {
            String query = "SELECT FROM " + table;
            return getTable(con,table,query,null,page);
	}

	public String getTableWhere(DatabaseConnection con, HashMap<String,String> parms, String table, String column, String columnValue) {
            String query = "SELECT FROM " + table + " WHERE "+column+" = #"+columnValue;
            return getTable(con, parms,table,query,column, 0);
	}

	public String getTableWhere(DatabaseConnection con, HashMap<String,String> parms, String table, String column, String operator, String columnValue) {
            String query = "SELECT FROM " + table + " WHERE "+column+" "+operator+" #"+columnValue;
            return getTable(con, parms,table,query,column, 0);
	}

	public String getTableWhere(DatabaseConnection con, HashMap<String,String> parms, String table, String column, String columnValue, long page) {
            String query = "SELECT FROM " + table + " WHERE "+column+"= #"+columnValue;
            return getTable(con, parms,table,query,column,page);
	}

	public String getTableWhere(DatabaseConnection con, HashMap<String,String> parms, String table, String column, String operator, String columnValue, long page) {
            String query = "SELECT FROM " + table + " WHERE "+column+" "+operator+" #"+columnValue;
            return getTable(con, parms,table,query,column,page);
	}

	public String getTable(DatabaseConnection con, HashMap<String,String> parms, String table, String query, String hideColumn, long page) {
            return getTable(con, parms, table, query, hideColumn, page, null);
	}

	/** Get a row-clickable table - See example usages  Note: page=-1 will show all records, use where clause to limit data */
	public String getTable(DatabaseConnection con, HashMap<String,String> parms, String table, String query, String hideColumn, long page, String columnOverride) {
            try {
                StringBuilder sb = new StringBuilder();
                int rowCount = 0;
                long totalRows = con.getRowCount(table);
                // Handle Paging
                if (page > -1) {
                    if (totalRows > ROW_COUNT_LIMIT) {
                        sb.append(Message.get(con.getLocale(), "PAGE_NAV")+"&nbsp;");
                        long pageCount = totalRows/ROW_COUNT_LIMIT+1;
                        for (long p=1; p<=pageCount; p++) {
                            if (Math.abs(page - p) < PAGE_WINDOW || pageCount - p < PAGE_WINDOW || p < PAGE_WINDOW) {
                                if (p == page) {
                                    sb.append(bold(color("red", ""+p))+"&nbsp;");
                                } else {
                                    sb.append(linkWithTip("permeagility.web.Table?TABLENAME="+table+"&PAGE="+p,""+p,"Page "+p)+"&nbsp;");
                                }
                            } else {
                                if (p % DOT_INTERVAL == 0) {
                                    sb.append(linkWithTip("permeagility.web.Table?TABLENAME="+table+"&PAGE="+p,".","Page "+p));							
                                }
                            }
                        }
                    }
                    String skip = "";
                    if (page > 0) {
                        skip = " SKIP "+((page - 1) * ROW_COUNT_LIMIT);
                    }
                    query += skip + " LIMIT "+ROW_COUNT_LIMIT;
                }

                String sourceTable = (parms != null ? parms.get("SOURCETABLENAME") : null);
                String sourceId = (parms != null ? parms.get("SOURCEEDIT_ID") : null);
                if (sourceTable != null) { sourceTable = "&SOURCETABLENAME="+sourceTable; } else { sourceTable = ""; }
                if (sourceId != null) { sourceId = "&SOURCEEDIT_ID="+sourceId; } else { sourceId = ""; }

                if (DEBUG) System.out.println("permeagility.web.Table:query="+query);
                QueryResult rs = con.query(query);

                // Get the table's columns
                Collection<OProperty> columns = con.getColumns(table, columnOverride);
                if (columns == null) {
                    System.out.println("Get columns for table:"+table+" returned null");
                } else {
                    sb.append(getRowHeader(con, table, columns, hideColumn));
                    for (ODocument row : rs.get()) {
                        sb.append(rowOnClick("clickable", getRow(columns, row, con, hideColumn), 
                            this.getClass().getName()  // Supports descendants using this function
                            + "?EDIT_ID=" + row.getIdentity().toString().substring(1) 
                            + "&TABLENAME=" + table 
                            + sourceTable 
                            + sourceId 
                        ));
                        rowCount++;
                        if (page > -1 && rowCount >= ROW_COUNT_LIMIT) break;
                    }
                    String rowCountInfo = paragraph(Message.get(con.getLocale(), "ROWS_OF", ""+rowCount, ""+totalRows) + "&nbsp;"+(page > -1 ? Message.get(con.getLocale(), "PAGE_NAV")+"&nbsp;"+page : ""));
                    sb.append(tableFooter(row(columnSpan(columns.size(), rowCountInfo ))));
                }
                return table("sortable", sb.toString());
            } catch (Exception e) {
                Throwable cause = e.getCause();
                System.out.println("permeagility.web.Table: Error: " + e.getMessage() + (cause == null ? "" : ": " + cause.getMessage()));
                e.printStackTrace();
                return "Error: " + e.getMessage() + (cause == null ? "" : "<BR>" + cause.getMessage());
            }
	}

	public String getRowHeader(DatabaseConnection con, String table, Collection<OProperty> columns, String hideColumn) throws SQLException {
            StringBuilder sb = new StringBuilder();
            for (OProperty column : columns) {
                String columnName = column.getName();
                String colNameI18N;
                String trName = Message.get(con.getLocale(),"COLUMN_"+columnName);
                if (!trName.equals("COLUMN_"+columnName)) {
                    colNameI18N = trName;
                } else {
                    colNameI18N = makeCamelCasePretty(columnName);
                }
                if (!columnName.toUpperCase().endsWith("PASSWORD")  // && !columnName.startsWith("_")
                    && (hideColumn == null || !columnName.equals(hideColumn)) ) {
                    if (columnName.startsWith("button_") && columnName.length() > 8 && columnName.indexOf('_',7) > 7) {
                        int cp = columnName.indexOf('_',7);
                        //String n = columnName.substring(7,cp);  // Not used
                        String l = columnName.substring(cp+1);
                        sb.append(columnHeaderNoSort(center(l)));
                    } else {
                        sb.append(columnHeader(center(colNameI18N)));
                    }
                }
            }
            return row(sb.toString());
	}

	public String getRow(Collection<OProperty> columns, ODocument d, DatabaseConnection con, String hideColumn) throws SQLException {
            StringBuilder sb = new StringBuilder();
//		if (DEBUG) System.out.println("Table.getRow colCount="+columns.size());
            for (OProperty column : columns) {
                String fieldName = column.getName();
                if (!fieldName.toUpperCase().endsWith("PASSWORD")   // && !fieldName.startsWith("_")
                   && 	(hideColumn == null || !fieldName.equals(hideColumn)) ) {
                    if (fieldName.startsWith("button_") && fieldName.length() > 8 && fieldName.indexOf('_',7) > 7) {
                        int cp = fieldName.indexOf('_',7);
                        String n = fieldName.substring(7,cp);  
                        String l = fieldName.substring(cp+1);
//					sb.append(column("<div style=\"z-index: 1000;\">"+form(hidden(n,d.getIdentity().toString().substring(1))+submitButton(l))+"</div>"));
//					sb.append(column(form(hidden(n,d.getIdentity().toString().substring(1))+submitButton(l))));
                        sb.append(column(form(button(n,d.getIdentity().toString().substring(1),l))));
                    } else {
                        sb.append(getColumnAsCell(column, d, con));					
                    }
                }
            }
            return sb.toString();
	}

	private String getColumnAsCell(OProperty column, ODocument d, DatabaseConnection con) {
            StringBuilder sb = new StringBuilder();
            String columnName = column.getName();
            Integer columnType = column.getType().getId(); 
//		if (DEBUG) System.out.println("permeagility.web.Table:Column name = "+columnName+" type = "+(columnType==null ? "null" : columnType));
            if (columnType == 0) {
                    sb.append(column(checkboxDisabled(columnName, (d.field(columnName) == null ? false : (Boolean)d.field(columnName)))));
            } else if (columnType == 1 || columnType == 2 || columnType == 3) {   // OrientDB int, short, long type
                    sb.append(column("number", ""+(d.field(columnName) == null ? "" : formatNumber(con.getLocale(),(Number)d.field(columnName),INT_FORMAT))));
            } else if (columnType == 4 || columnType == 5) {   // OrientDB float, double
                    sb.append(column("number", ""+(d.field(columnName) == null ? "" : formatNumber(con.getLocale(),(Number)d.field(columnName),FLOAT_FORMAT))));
            } else if (columnType == 6) {  // OrientDB Datetime
                    sb.append(column(""+(d.field(columnName) == null ? "" : formatDatetime(con.getLocale(),(Date)d.field(columnName)))));
            } else if (columnType == 7) {  // String
                    if (columnName.toUpperCase().endsWith("COLOR") 
                            || columnName.toUpperCase().endsWith("COLOUR")) {
                            sb.append(columnColor(5, (String)d.field(columnName)));				
                    } else {
                            String stringvalue = d.field(columnName);
                            if (stringvalue != null && stringvalue.length() > MAX_STRING_DISPLAY) {
                                    stringvalue = stringvalue.substring(0, MAX_STRING_DISPLAY) + "...";
                            }
                            if (stringvalue != null) {
                                    stringvalue = stringvalue.replace("<","&lt;"); // These can mess up the display
                                    stringvalue = stringvalue.replace(">","&gt;");
                            }
                            sb.append(column(stringvalue));
                    }
            } else if (columnType == 20) {  // Binary (Using CUSTOM OType)
                    StringBuilder desc = new StringBuilder();
                    String blobid = Thumbnail.getThumbnailId(d.getClassName(), d.getIdentity().toString().substring(1), columnName, desc);
                    if (blobid != null) {
                            sb.append(column(Thumbnail.getThumbnailLink(con.getLocale(),blobid, desc.toString())));
                    } else {
                            sb.append(column("<div title=\""+Message.get(con.getLocale(), "THUMBNAIL_NOT_FOUND",columnName, d.getIdentity().toString()+"\">"+Message.get(con.getLocale(),"OPTION_NONE")+"</div>")));					
                    } 

            } else if (columnType >= 9 && columnType <= 12) {  // Embedded
                    String stringvalue = ""+d.field(columnName);
                    if (stringvalue != null && stringvalue.length() > MAX_STRING_DISPLAY) {
                            stringvalue = stringvalue.substring(0, MAX_STRING_DISPLAY) + "...";
                    }
                    sb.append(column(stringvalue));
            } else if (columnType == 13) {  // Link
                    String desc = "";
                    try {
                        ODocument l = d.field(columnName);
                        if (l != null) {
                                desc = getDescriptionFromDocument(con, l);
                                if (desc == null) {
                                        desc = (String)l.field("name");
                                }
                        }
                    } catch (Exception e) {
                        System.out.println("A null ODocument link was found in "+columnName+" of "+d.getIdentity().toString());
                        desc = "!";
                    }
                    sb.append(column(desc == null ? "null" : desc));
            } else if (columnType == 14) {  // LinkList
                    List<ODocument> l = d.field(columnName);
                    StringBuilder ll = new StringBuilder();
                    if (l != null) {
                            if (DEBUG) System.out.println("linkList size="+l.size()+(l.size()>0 ? " type="+l.get(0).getClass().getName() : ""));
                            for (ODocument o : l) {
                                    if (o != null) {
                                            ll.append(getDescriptionFromDocument(con, o)+br());
                                    }
                            }
                    }
                    sb.append(column(ll.toString()));
            } else if (columnType == 15) {  // LinkSet
                    Set<ODocument> l = d.field(columnName);
                    StringBuilder ll = new StringBuilder();
                    if (l != null) {
                            for (Object o : l) {
                                    if (o != null) {
                                            if (o instanceof ORecordId) {
                                                    o = con.getDb().getRecord((ORecordId)o);
                                            }
                                            if (o != null) {
                                                    ll.append(getDescriptionFromDocument(con, (ODocument)o)+br());
                                            }
                                    }
                            }
                    }
                    sb.append(column(ll.toString()));
            } else if (columnType == 16) {    // LinkMap
                    Map<String,ODocument> l = d.field(columnName);
                    StringBuilder ll = new StringBuilder();
                    if (l != null) {
                            for (String k : l.keySet()) {
                                    ODocument o = l.get(k);
                                    if (o != null) {
                                            ll.append(k+":"+getDescriptionFromDocument(con, o)+br());
                                    }
                            }
                    }
                    sb.append(column(ll.toString()));
            } else if (columnType == 17) {  // Byte
                    sb.append(column(""+d.field(columnName)));
            } else if (columnType == 18) {  // Transient
                    sb.append(column("transient"));
            } else if (columnType == 19) {  // OrientDB Date
                    sb.append(column(""+(d.field(columnName) == null ? "" : formatDate(con.getLocale(),(Date)d.field(columnName)))));
            } else if (columnType == 21) {   // OrientDB Decimal
                    Number num = (Number)d.field(columnName);
                    String formatted = (num == null ? "" : formatNumber(con.getLocale(),num,FLOAT_FORMAT));
                    sb.append(column("number", formatted));
            } else if (columnType == 22) {   // LinkBag
                    sb.append(column("LinkBag"));
            } else if (columnType == 23) {   // Any
                    Object value = d.field(columnName);
                    sb.append(column(value == null ? "null" : value.toString() ));
            } else {
                    if (DEBUG) System.out.println("Table: unrecognized type "+columnType);
                    sb.append(column("??"+columnType+"??"));
            }
            return sb.toString();
	}

	public String advancedOptions(DatabaseConnection con, String table, HashMap<String, String> parms) {
            Locale locale = con.getLocale();
            StringBuilder errors = new StringBuilder();
            String submit = parms.get("SUBMIT");
            if (submit != null) {
                if (submit.equals("RENAME_TABLE_BUTTON")) {
                        if (isNullOrBlank(parms.get("RENAME_TABLE"))) {
                                errors.append(paragraph("error", "Please specify a new name"));
                        } else {
                                try {
                                        String newtable = parms.get("RENAME_TABLE");
                                        newtable = makePrettyCamelCase(newtable);
                                        Server.tableUpdated(table);
                                        con.update("ALTER CLASS "+table+" NAME "+newtable);
                                        con.update("UPDATE columns SET name='"+newtable+"' WHERE name='"+table+"'");
                                        table = newtable;
                                        Server.tableUpdated("metadata:schema");
                                        return redirect(locale, this, "TABLENAME=" + table);
                                } catch (Exception e) {
                                        errors.append(paragraph("error", e.getMessage()));
                                }
                        }
                } else if (submit.equals("RENAME_COLUMN_BUTTON")) {
                        if (isNullOrBlank(parms.get("RENAME_COLUMN"))) {
                                errors.append(paragraph("error", Message.get(locale, "SPECIFY_COLUMN_NAME")));
                        } else {
                                try {
                                        String oldcolumn = parms.get("COLUMN_TO_RENAME");
                                        String newcolumn = parms.get("RENAME_COLUMN");
                                        newcolumn = makePrettyCamelCase(newcolumn);
                                        con.update("ALTER PROPERTY " + table + "."+ oldcolumn +" NAME " + newcolumn);
                                        ODocument d = con.queryDocument("SELECT FROM columns WHERE name='"+table+"'");
                                        if (d != null) {
                                                String cl = d.field("columnList");
                                                if (cl != null) {
                                                        d.field("columnList",cl.replace(oldcolumn, newcolumn));
                                                        d.save();
                                                }								
                                        }
                                        Server.tableUpdated("metadata:schema");
                                        return redirect(locale, this, "TABLENAME=" + table);
                                } catch (Exception e) {
                                        errors.append(paragraph("error", e.getMessage()));
                                }
                        }
                } else if (submit.equals("DROP_COLUMN_BUTTON")) {
                        if (isNullOrBlank(parms.get("COLUMN_TO_DROP"))) {
                                errors.append(paragraph("error", Message.get(locale, "SPECIFY_COLUMN_NAME")));
                        } else {
                                String colToDrop = parms.get("COLUMN_TO_DROP");
                                try {
                                        OClass c = con.getSchema().getClass(table);
                                        c.dropProperty(colToDrop);
                                        Object ret = con.update("UPDATE "+table+" REMOVE "+colToDrop);  // Otherwise, column actually remains in the data
                                        errors.append(paragraph("success", "Data for column removed:"+ret));						
                                        Setup.removeColumnFromColumns(con, table, colToDrop);
                                        Server.tableUpdated("metadata:schema");
                                        return redirect(locale, this, "TABLENAME=" + table);
                                } catch (Exception e) {
                                        errors.append(paragraph("error", e.getMessage()));
                                }
                        }
                } else if (submit.equals("DROP_TABLE_BUTTON")) {
                        try {
                                con.update("DROP class " + table);
                                ODocument d = con.queryDocument("SELECT FROM columns WHERE name='"+table+"'");
                                if (d != null) {
                                        d.delete();
                                        d.save();
                                }
                                DatabaseConnection.rowCountChanged(table);
                                return redirect(locale, this);
                        } catch (Exception e) {
                                errors.append(paragraph("error", e.getMessage()));
                        }
                }
            }
            String title = table + " " + Message.get(locale, "ADVANCED_OPTIONS");
            parms.put("SERVICE", title);
            return head(title,getScripts(con))
                + body(standardLayout(con, parms,
                        advancedOptionsForm(con, table, parms,errors.toString())
                        + br()
                        + link("permeagility.web.Table?TABLENAME=" + table, Message.get(locale, "BACK_TO_TABLE"))
                ));
	}

	public String advancedOptionsForm(DatabaseConnection con, String table, HashMap<String, String> parms,String errors) {
            Locale locale = con.getLocale();
            return hidden("ADVANCED_OPTIONS", "YES")
                + paragraph("banner",Message.get(locale, "ADVANCED_OPTIONS"))
                + errors
                + paragraph(Message.get(locale, "RENAME_TABLE_TO") + input("RENAME_TABLE", (parms != null ? parms.get("RENAME_TABLE") : ""))
                        + submitButton(locale, "RENAME_TABLE_BUTTON"))
                + paragraph(Message.get(locale, "RENAME_COLUMN") + " " 
                        + createListFromCache("COLUMN_TO_RENAME", (parms != null ? parms.get("COLUMN_TO_RENAME") : ""), con,
                                        "SELECT name as rid, name FROM (SELECT expand(properties) FROM (select expand(classes) from metadata:schema) where name = '" + table + "') ORDER BY name") 
                                + Message.get(locale, "CHANGE_NAME_TO")
                        + input("RENAME_COLUMN", (parms != null ? parms.get("RENAME_COLUMN") : ""))
                        + submitButton(locale, "RENAME_COLUMN_BUTTON"))
                + paragraph(Message.get(locale, "DROP_COLUMN") + " " 
                        + createListFromCache("COLUMN_TO_DROP", (parms != null ? parms.get("COLUMN_TO_DROP") : ""), con,
                                        "SELECT name as rid, name FROM (SELECT expand(properties) FROM (select expand(classes) from metadata:schema) where name = '" + table + "') ORDER BY name") 
                        + confirmButton(locale,"DROP_COLUMN_BUTTON", "DROP_COLUMN"))
                + paragraph(Message.get(locale, "DROP_TABLE") + " " + table + "   "
                        + confirmButton(locale, "DROP_TABLE_BUTTON", "DROP_TABLE_CONFIRM"));
	}

	public String rightsOptions(DatabaseConnection con, String table, HashMap<String, String> parms) {
            Locale locale = con.getLocale();
            StringBuilder errors = new StringBuilder();
            String submit = (parms != null ? parms.get("SUBMIT") : null);
            String right = (parms != null ? parms.get("RIGHT") : null);
            String role = (parms != null ? parms.get("ROLESELECT") : null);
            if (submit != null && submit.equals("GRANT_RIGHT")) {
                if (DEBUG) System.out.println("Granting right");
                String grantQuery = "GRANT "+right
                                +" ON database.class."+table
                                +" TO "+role;
                System.out.println("Executing GRANT: "+grantQuery);
                try {
                    con.update(grantQuery);
                    Server.tableUpdated("ORole");  // Privs are stored in ORole
                    Security.tablePrivUpdated(table);
                } catch (Exception e) {
                    errors.append(e.getLocalizedMessage());
                    e.printStackTrace();
                }
                return redirect(locale, this, "TABLENAME=" + table);
            }
            if (submit != null && submit.equals("REVOKE_RIGHT")) {
                if (DEBUG) System.out.println("Revoking right ");
                String revokeQuery = "REVOKE "+right
                                +" ON database.class."+table
                                +" FROM "+role;
                System.out.println("Executing REVOKE: "+revokeQuery);
                try {
                    con.update(revokeQuery);
                    Server.tableUpdated("ORole");  // Stored in ORole
                    Security.tablePrivUpdated(table);
                } catch (Exception e) {
                    errors.append(e.getLocalizedMessage());
                    e.printStackTrace();
                }
                return redirect(locale, this, "TABLENAME=" + table);
            }

            String title = table + " " + Message.get(locale, "ADVANCED_OPTIONS");
            parms.put("SERVICE", title);
            return head(title,getScripts(con))
                + body(standardLayout(con, parms,
                        rightsOptionsForm(con, table, parms,errors.toString())
                        + br()
                        + link("permeagility.web.Table?TABLENAME=" + table, Message.get(locale, "BACK_TO_TABLE"))
                ));
	}

	public String rightsOptionsForm(DatabaseConnection con, String table, HashMap<String, String> parms,String errors) {
            StringBuilder currentRights = new StringBuilder();
            List<String> rightsNames = new ArrayList<>();
            HashMap<String,Number> privs = Security.getTablePrivs(table);

            for (String role : privs.keySet()) {
                Number b = privs.get(role);
                if (b != null) {
                    StringBuilder sb = new StringBuilder();
                    if (b.intValue() == 0) {
                        //sb.append(Message.get(con.getLocale(), "PRIV_NONE"));
                    } else if (b.intValue() == PRIV_ALL) {
                        sb.append(Message.get(con.getLocale(), "PRIV_ALL"));
                    } else {
                        if (DEBUG) System.out.println("role="+role+" table="+table+" create="+(b.intValue() & PRIV_CREATE)+" read="+(b.intValue() & PRIV_READ)+" update="+(b.intValue() & PRIV_UPDATE)+" delete="+(b.intValue() & PRIV_DELETE));
                        if ((b.intValue() & PRIV_CREATE) > 0) {
                            sb.append(Message.get(con.getLocale(), "PRIV_CREATE"));
                        }
                        if ((b.intValue() & PRIV_READ) > 0) {
                            if (sb.length() > 0) { sb.append(", "); }
                            sb.append(Message.get(con.getLocale(), "PRIV_READ"));
                        }
                        if ((b.intValue() & PRIV_UPDATE) > 0) {
                            if (sb.length() > 0) { sb.append(", "); }
                            sb.append(Message.get(con.getLocale(), "PRIV_UPDATE"));
                        }
                        if ((b.intValue() & PRIV_DELETE) > 0) {
                            if (sb.length() > 0) { sb.append(", "); }
                            sb.append(Message.get(con.getLocale(), "PRIV_DELETE"));
                        }
                    }
                    if (sb.length()>0) {
                        currentRights.append(Message.get(con.getLocale(), "ROLE_CAN_PRIV", role,sb.toString())+br());
                    }
                }
            }

            rightsNames.add("NONE");
            rightsNames.add("CREATE");
            rightsNames.add("READ");
            rightsNames.add("UPDATE");
            rightsNames.add("DELETE");
            rightsNames.add("ALL");

            return hidden("RIGHTS_OPTIONS", "YES") + errors
                + paragraph("banner",Message.get(con.getLocale(), "EXISTING_RIGHTS"))
                + currentRights.toString()
                + paragraph("banner",Message.get(con.getLocale(), "ADD_OR_REMOVE_RIGHT"))
                + createListFromCache("ROLESELECT", null, con, ROLES_LIST, null, false, null, true)
                + createList(con.getLocale(),"RIGHT", null, rightsNames, null, false, null, true)
                + submitButton(con.getLocale(), "GRANT_RIGHT")
                + submitButton(con.getLocale(), "REVOKE_RIGHT");
	}

    public static String password(String name, Object value, int size) {
    	return "<INPUT TYPE=\"PASSWORD\" NAME=\""+name+"\" VALUE=\""+(value==null ? "" : value)+"\" SIZE=\""+size+"\">";
    }

    public static String getTypeName(Integer i) {
        OType type = OType.getById(i.byteValue());
        if (type == OType.DOUBLE) {
                return "DATATYPE_FLOAT";
        } else if (type == OType.LONG) {
                return "DATATYPE_INT";
        } else if (type == OType.BOOLEAN) {
                return "DATATYPE_BOOLEAN";
        } else if (type == OType.STRING) {
                return "DATATYPE_TEXT";
        } else if (type == OType.DATETIME) {
                return "DATATYPE_DATETIME";
        } else if (type == OType.DATE) {
                return "DATATYPE_DATE";
        } else if (type == OType.CUSTOM) {
                return "DATATYPE_BLOB";
        } else if (type == OType.DECIMAL) {
                return "DATATYPE_DECIMAL";
        } else if (type == OType.LINK) {
                return "DATATYPE_LINK";
        } else if (type == OType.LINKLIST) {
                return "DATATYPE_LINKLIST";
        } else if (type == OType.LINKMAP) {
                return "DATATYPE_LINKMAP";
        } else if (type == OType.LINKSET) {
                return "DATATYPE_LINKSET";
        } else if (type == OType.LINKBAG) {
                return "LinkBag (not supported)";
        } else {
                return (type.name());
        }
    }

    public static OType getOTypeFromName(String name) {
        if (name == null || name.isEmpty()) {
            return OType.STRING;
        } else if (name.equals("DATATYPE_FLOAT")) {
            return OType.DOUBLE;
        } else if (name.equals("DATATYPE_INT")) {
            return OType.LONG;
        } else if (name.equals("DATATYPE_BOOLEAN")) {
            return OType.BOOLEAN;
        } else if (name.equals("DATATYPE_TEXT")) {
            return OType.STRING;
        } else if (name.equals("DATATYPE_DATETIME")) {
            return OType.DATETIME;
        } else if (name.equals("DATATYPE_DATE")) {
            return OType.DATE;
        } else if (name.equals("DATATYPE_BLOB")) {
            return OType.CUSTOM;
        } else if (name.equals("DATATYPE_DECIMAL")) {
            return OType.DECIMAL;
        } else if (name.equals("DATATYPE_LINK")) {
            return OType.LINK;
        } else if (name.equals("DATATYPE_LINKLIST")) {
            return OType.LINKLIST;
        } else if (name.equals("DATATYPE_LINKSET")) {
            return OType.LINKSET;
        } else if (name.equals("DATATYPE_LINKMAP")) {
            return OType.LINKMAP;
        } else {
            return OType.STRING;
        }
    }

    public static DateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    public static DateFormat sqlDatetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    
}