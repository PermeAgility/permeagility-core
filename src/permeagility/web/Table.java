/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public class Table extends Weblet {

	public static boolean DEBUG = true;
	public static int MAX_STRING_DISPLAY = 100;
	public static int TEXT_AREA_THRESHOLD = 40;
	public static int TEXT_AREA_WIDTH = 80;
	public static long ROW_COUNT_LIMIT = 500;
	public static long DOT_INTERVAL = 5;  // Probably should derive this to be more dynamic
	public static long PAGE_WINDOW = 3;

	static List<String> dataTypeNames = new ArrayList<String>();
	public static String DATATYPE_FLOAT = "Floating point number (double)";
	public static String DATATYPE_INT = "Whole number (integer)";
	public static String DATATYPE_TEXT = "Text (any length)";
	public static String DATATYPE_BOOLEAN = "Boolean (true/false)";
	public static String DATATYPE_DATETIME = "Date and time";
	public static String DATATYPE_DATE = "Date";
	public static String DATATYPE_DECIMAL = "Decimal (Currency)";
	public static String DATATYPE_BLOB = "Binary (image/file)";
	public static String DATATYPE_LINK = "Link (single reference)";
	public static String DATATYPE_LINKLIST = "Link list (ordered)";
	public static String DATATYPE_LINKSET = "Link set (unordered)";
	public static String DATATYPE_LINKMAP = "Link map (with names, ordered)";

	public static String PARM_PREFIX = "PARM_";
	
	public static boolean SHOW_ALL_RELATED_TABLES = true;

	public static final int PRIV_CREATE = 1;
	public static final int PRIV_READ = 2;
	public static final int PRIV_UPDATE = 4;
	public static final int PRIV_DELETE = 8;
	public static final int PRIV_ALL = 15;
	
	public static String TABLE_REF_LIST = "SELECT name as rid, name FROM (select expand(classes) from metadata:schema) ORDER BY name";
	public static String USERS_LIST = "SELECT name as rid, name FROM OUser ORDER BY name";
	public static String ROLES_LIST = "SELECT name as rid, name FROM ORole ORDER BY name";

	public String getPage(DatabaseConnection con, java.util.HashMap<String, String> parms) {
		String title = Message.get(con.getLocale(), "TABLE_EDITOR", parms.get("TABLENAME") != null ? makeCamelCasePretty( parms.get("TABLENAME")) : "None");
		parms.put("SERVICE", title);
		String submit = parms.get("SUBMIT");
		String table = parms.get("TABLENAME");
		String pagest = parms.get("PAGE");
		long page = 1;
		if (pagest != null) {
			try {
				page = Integer.parseInt(pagest);
			} catch (Exception e) {
				System.out.println("Unable to interpret page number "+pagest+" as a number");
			}
		}
		String sourceTable = parms.get("SOURCETABLENAME");
		if (isNullOrBlank(table)) {
			return head("Redirect") + bodyOnLoad("No table specified, Redirecting...", "window.location.href='permeagility.web.Schema';");
		}

		StringBuffer errors = new StringBuffer();

		if (parms.get("ADVANCED_OPTIONS") != null
				|| (submit != null && submit.equals(Message.get(con.getLocale(), "ADVANCED_OPTIONS")))) {
			return advancedOptions(con, table, parms);
		}

		if (parms.get("RIGHTS_OPTIONS") != null
				|| (submit != null && submit.equals(Message.get(con.getLocale(), "RIGHTS_OPTIONS")))) {
			return rightsOptions(con, table, parms);
		}
		if (submit != null
				&& (submit.equals(Message.get(con.getLocale(), "CANCEL")) 
				|| submit.equals(Message.get(con.getLocale(), "NEW_COLUMN")))) {
			parms.remove("EDIT_ID");
			parms.remove("UPDATE_ID");
		}
		if (parms.containsKey("UPDATE_ID")) {
			if (DEBUG) System.out.println("update_id="+parms.get("UPDATE_ID"));
			if (submit != null && submit.equals(Message.get(con.getLocale(), "DELETE"))) {
				if (deleteRow(con, table, parms, errors)) {
					parms.remove("EDIT_ID");
					parms.remove("UPDATE_ID");
					submit = null;
				} else {
					return head(title)
							+ body(standardLayout(con, parms, getTableRowForm(con, table, parms) + errors.toString()));
				}
			} else if (submit != null && submit.equals(Message.get(con.getLocale(), "UPDATE"))) {
				if (DEBUG) System.out.println("In updating row");
				if (updateRow(con, table, parms, errors)) {
					parms.remove("EDIT_ID");
					parms.remove("UPDATE_ID");
				} else {
					return head(title, getDateControlScript()+getColorControlScript()+getPrettyPhotoScript()+getAngularControlScript())
							+ body(standardLayout(con, parms, getTableRowForm(con, table, parms) + errors.toString()));
				}
			} else {
				// Cancel is assumed
				parms.remove("EDIT_ID");
				parms.remove("UPDATE_ID");
			}
		}

		if (submit != null) {
			if (submit.equals(Message.get(con.getLocale(), "NEW_COLUMN"))) {
				String cn = parms.get("NEWCOLUMNNAME");
				String dt = parms.get("NEWDATATYPE");
				String tr = parms.get("NEWTABLEREF");
				
				OType type = null;
				if (dt.equals(DATATYPE_FLOAT)) {
					type = OType.DOUBLE;
					tr = null;
				} else if (dt.equals(DATATYPE_INT)) {
					type = OType.LONG;
					tr = null;
				} else if (dt.equals(DATATYPE_BOOLEAN)) {
					type = OType.BOOLEAN;
					tr = null;
				} else if (dt.equals(DATATYPE_TEXT)) {
					type = OType.STRING;
					tr = null;
				} else if (dt.equals(DATATYPE_DATETIME)) {
					type = OType.DATETIME;
					tr = null;
				} else if (dt.equals(DATATYPE_DATE)) {
					type = OType.DATE;
					tr = null;
				} else if (dt.equals(DATATYPE_BLOB)) {
					type = OType.CUSTOM;
					tr = null;
				} else if (dt.equals(DATATYPE_DECIMAL)) {
					type = OType.DECIMAL;
					tr = null;
				} else if (dt.equals(DATATYPE_LINK)) {
					type = OType.LINK;
					if (isNullOrBlank(tr)) {
						errors.append(paragraph("error", "Link table must be specified for reference column types"));
					}
				} else if (dt.equals(DATATYPE_LINKLIST)) {
					type = OType.LINKLIST;
					if (isNullOrBlank(tr)) {
						errors.append(paragraph("error", "Link table must be specified for reference column types"));
					}
				} else if (dt.equals(DATATYPE_LINKSET)) {
					type = OType.LINKSET;
					if (isNullOrBlank(tr)) {
						errors.append(paragraph("error", "Link table must be specified for reference column types"));
					}
				} else if (dt.equals(DATATYPE_LINKMAP)) {
					type = OType.LINKMAP;
					if (isNullOrBlank(tr)) {
						errors.append(paragraph("error", "Link table must be specified for reference column types"));
					}
				}
				if (type == null || isNullOrBlank(cn) || isNullOrBlank(dt)) {
					errors.append(paragraph("error", "Column name and data type must be specified"));
				} else {
					try {
						OClass c = con.getSchema().getClass(table);
						if (c == null) {
							errors.append(paragraph("error", "Cannot find class to create column in table: " + table));							
						} else {
							if (tr != null) {
								c.createProperty(cn, type, con.getSchema().getClass(tr));
							} else {
								c.createProperty(cn, type);
							}
							errors.append(paragraph("success", "New column created"));
							Server.tableUpdated("metadata:schema");
							Server.clearColumnsCache(table);
						}
					} catch (Exception e) {
						e.printStackTrace();
						errors.append(paragraph("error", "Cannot create column: " + e.getMessage()));
					}

				}
			} else if (submit.equals(Message.get(con.getLocale(), "CREATE_ROW"))) {
				if (DEBUG) System.out.println("************ Inserting row");
				insertRow(con,table,parms,errors);
			} else {
				System.out.println("Did not understand submit="+submit);
			}
		}

		if (sourceTable != null && parms.containsKey("SOURCEEDIT_ID")) {
			table = sourceTable;
			submit = null;
			parms.put("EDIT_ID", parms.get("SOURCEEDIT_ID"));
		}

		if (parms.containsKey("EDIT_ID") && (submit == null || !submit.equals(Message.get(con.getLocale(), "CREATE_ROW")))) {
			return head(title, getDateControlScript()+getColorControlScript()+getPrettyPhotoScript()+getAngularControlScript())
					+ body(standardLayout(con, parms, getTableRowForm(con, table, parms)));
		}

		if (dataTypeNames.size() == 0) {  // Do this once only
			dataTypeNames.add(DATATYPE_TEXT);
			dataTypeNames.add(DATATYPE_FLOAT);
			dataTypeNames.add(DATATYPE_INT);
			dataTypeNames.add(DATATYPE_DECIMAL);  
			dataTypeNames.add(DATATYPE_DATE);  // Calendar
			dataTypeNames.add(DATATYPE_DATETIME); // Calendar and time
			dataTypeNames.add(DATATYPE_BOOLEAN);   // Checkbox
			dataTypeNames.add(DATATYPE_LINK);    // PickList
			dataTypeNames.add(DATATYPE_LINKSET);  // Link Set control
			dataTypeNames.add(DATATYPE_LINKLIST); // Link List control
			dataTypeNames.add(DATATYPE_LINKMAP);  // Link map control
			dataTypeNames.add(DATATYPE_BLOB);  // Image (with thumbnail)
		}
		
		parms.remove("EDIT_ID"); // Need to avoid confusing getTableRowForm
		
		// Make the result
		return head(title, getDateControlScript()+getSortTableScript()+getColorControlScript()+getPrettyPhotoScript()+getAngularControlScript())
				+ body(standardLayout(con, parms,  
					link(this.getClass().getName(),"&lt;"+Message.get(con.getLocale(),"ALL_TABLES"))
					+"&nbsp;&nbsp;&nbsp;"
					+((Server.getTablePriv(con, table) & PRIV_CREATE) > 0 ? popupForm("CREATE_NEW_ROW",null,Message.get(con.getLocale(),"NEW_ROW"),null,"NAME",
							paragraph("banner",Message.get(con.getLocale(), "CREATE_ROW"))
							+getTableRowFields(con, table, parms)
							+submitButton(Message.get(con.getLocale(), "CREATE_ROW"))) : "")
					+"&nbsp;&nbsp;&nbsp;"
					+(Server.isDBA(con) ?
						popupForm("NEWCOLUMN", null, Message.get(con.getLocale(), "ADD_COLUMN"),null,"NEWCOLUMNNAME", newColumnForm(con))
						+"&nbsp;&nbsp;&nbsp;"
						+popupForm("RIGHTSOPTIONS", null, Message.get(con.getLocale(), "TABLE_RIGHTS_OPTIONS"),null,"XXX", rightsOptionsForm(con,table,parms,""))
						+"&nbsp;&nbsp;&nbsp;"
						+popupForm("ADVANCEDOPTIONS", null, Message.get(con.getLocale(), "ADVANCED_TABLE_OPTIONS"),null,"NEWCOLUMNNAME", advancedOptionsForm(con,table,parms,""))
						: "") // isDBA switch
					+ br() 
					+ errors.toString()
					+ br()
					+ ((Server.getTablePriv(con, table) & PRIV_READ) > 0 ? getTable(con, table, page) : paragraph(Message.get(con.getLocale(),"NO_PERMISSION_TO_VIEW")))
				));
	}

	public boolean insertRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuffer errors) {
		StringBuffer ins = new StringBuffer("INSERT INTO "+table+" SET ");
		int colCount = 0;
		ArrayList<String> blobList = new ArrayList<String>();
		
		for (ODocument column : Server.getColumns(table).get()) {
			Integer type = column.field("type");
			String name = column.field("name");
			String value = parms.get(PARM_PREFIX+name);
			if (DEBUG) System.out.println("InsertRow: column "+name+" is a "+type+" and its value is "+value);
			if (value != null && !value.trim().equals("")) {
				if (colCount > 0) {
					ins.append(", ");
				}
				colCount++;
	
				if (type == 0) {
					if (value == null) value = "off";
					if (!isNullOrBlank(value)) {
						ins.append(name+" = "+(value.equals("on") ? "true" : "false"));
					}
					
				} else if (type == 1 || type == 2 || type == 3 || type == 17) {
					if (value == null || value.equals("") || value.equals("null")) {
						ins.append(name+" = null");					
					} else {
						try {
							int intValue = Integer.parseInt(value);
							ins.append(name+" = "+intValue);
						} catch (Exception e) {
							errors.append(paragraph("error", "Unable to determine whole number value from " + value));
							value = null;
						}						
					}
					
				} else if (type == 4 || type == 5 || type == 21) {  // Float - Double - Decimal
					if (value == null || value.equals("") || value.equals("null")) {
						ins.append(name+" = null");
					} else {
						try {
							double dubValue = Double.parseDouble(value);
							ins.append(name+" = "+dubValue);
						} catch (Exception e) {
							errors.append(paragraph("error", "Unable to determine number value from " + value));
							value = null;
						}
					}
					
				} else if (type == 6) {  // Datetime
					ins.append(name+" = "+(value == null || value.equals("") ? "null" : "'"+value+"'"));
					
				} else if (type == 7) {  // String 
					ins.append(name+" = '"+value+"'");
	
				} else if (type == 20) {  // Binary/image (8 = binary, 20 = custom)
					if (DEBUG) System.out.println("Adding "+name+" to blobList");
					blobList.add(name);
					
				} else if (type >= 9 && type <= 12) {  // Embedded types
					ins.append(name+" = "+value+"");
	
				} else if (type == 13) {
					ins.append(name+" = "+(value == null || value.equals("null") ? "" : "#")+value);
					
				} else if (type == 14) { // LinkList
					if (value != null && !value.trim().equals("")) {
						String[] newValues = {};
						if (value != null) {  newValues = value.split(","); }
						StringBuffer vs = new StringBuffer();
						for (String nv : newValues) {
							if (vs.length() > 0) vs.append(", ");
							vs.append("#"+nv);
						}
						ins.append(name+" = ["+vs.toString()+"]");
					}				
				} else if (type == 15) { // Linkset
					if (value != null && !value.trim().equals("")) {
						String[] newValues = {};
						if (value != null) {  newValues = value.split(","); }
						StringBuffer vs = new StringBuffer();
						for (String nv : newValues) {
							if (vs.length() > 0) vs.append(", ");
							vs.append("#"+nv);
						}
						ins.append(name+" = ["+vs.toString()+"]");
					}				
				} else if (type == 16) { // LinkMap
					if (value != null && !value.trim().equals("")) {
						String[] newValues = {};
						if (value != null) {  newValues = splitCSV(value); }
						StringBuffer vs = new StringBuffer();
						for (String nv : newValues) {
							if (vs.length() > 0) vs.append(", ");
							String[] v = nv.split(":",2);
							vs.append(v[0]+":#"+v[1]);
						}
						ins.append(name+" = {"+vs.toString()+"}");
					}				
				} else if (type == 19) {  // Date
					ins.append(name+" = '"+value+"'");
					
				} else {
					errors.append(paragraph("error", "Not able to recognize field type " + type + " for column " + name));
				}
			}
		}
		if (ins.toString().endsWith("SET ")) {
			errors.append(paragraph("error",Message.get(con.getLocale(),"NOTHING_TO_INSERT")));
			return false;
		} else {
			try {
				System.out.println("insert sql = "+ins.toString());
				Object rc = con.update(ins.toString());  // Do the update
				if (rc != null && rc instanceof ODocument && blobList.size() > 0) {
					updateBlobs((ODocument)rc, table, blobList, parms, errors);
				}
				errors.append(paragraph("success", Message.get(con.getLocale(),"NEW_ROW_CREATED")+rc));
				Server.tableUpdated(table);
				DatabaseConnection.rowCountChanged(table);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				errors.append(paragraph("error", Message.get(con.getLocale(),"CANNOT_CREATE_ROW") + e.getMessage()));
				return false;
			}
		}
	}
	
	public boolean updateBlobs(ODocument doc, String table, ArrayList<String> blobList, HashMap<String, String> parms, StringBuffer errors) {
		if (doc != null) {
			for (String blob_name : blobList) {
				String blob_temp_file = parms.get(PARM_PREFIX+blob_name);
				String blob_file_name = parms.get(PARM_PREFIX+blob_name+"_FILENAME");
				String blob_type = parms.get(PARM_PREFIX+blob_name+"_TYPE");
				if (blob_temp_file != null && !blob_temp_file.trim().equals("")) {
					if (DEBUG) System.out.println("Writing blob "+blob_file_name+" type:"+blob_type+" file:"+blob_temp_file);
					ORecordBytes record = new ORecordBytes();
					try {
						record.fromInputStream(new FileInputStream(blob_temp_file));
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
					record.save();
					doc.field(blob_name,record);
					Thumbnail.createThumbnail(table, doc, blob_name);
				}
			}			
			doc.save();
		} else {
			System.out.println("Table.updateBlobs() - document is null");
		}
		return true;
	}
	
	public boolean updateRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuffer errors) {
		if (DEBUG) System.out.println("In updateRow of table "+table);
		StringBuffer setString = new StringBuffer();
		ArrayList<String> extras  = new ArrayList<String>();
		String update_id = parms.get("UPDATE_ID");
		ArrayList<String> blobs = new ArrayList<String>();
		if (update_id != null) {
			QueryResult initrows = con.query("SELECT FROM #" + update_id);
			if (initrows != null && initrows.size() == 1) {
				ODocument updateRow = initrows.get(0);
				QueryResult columns = Server.getColumns(table);
				for (ODocument column : columns.get()) {
					
					String columnName = column.field("name");
					Integer type = column.field("type");
					String newValue = parms.get(PARM_PREFIX+columnName);
					if (DEBUG) System.out.println("updating "+columnName+" of type "+type+" with value "+newValue);
					if (newValue != null) {
						if (newValue.equals("") || newValue.equals("null")) {
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
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + "=" + newbool);
						}
					} else if (type == 1 || type == 2 || type == 3) { // Whole number
						Long originalValue = updateRow.field(columnName);
						Long newVal = null;
						try {
							newVal = Long.parseLong(newValue);
						} catch (Exception e) {
							//errors.append(paragraph("error", "Value could not be updated: " + e.getMessage()));
						}
						if ((newValue != null && originalValue != null && !newVal.equals(originalValue)) 
								  || (newValue == null && originalValue != null)
								  || (originalValue == null && newValue != null)) {
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + "=" + (newVal == null ? "null" : newValue));
						}
					} else if (type == 4 || type == 5) { // float, double
						Double originalValue = updateRow.field(columnName);
						Double newVal = null;
						try {
							newVal = Double.parseDouble(newValue);
						} catch (Exception e) {
							errors.append(paragraph("error", "Value could not be updated: " + e.getMessage()));
						}
						if ((newValue != null && originalValue != null && !newVal.equals(originalValue)) 
								  || (newValue == null && originalValue != null)
								  || (originalValue == null && newValue != null)) {
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + "=" + (newVal == null ? "null" : newValue));
						}
					} else if (type == 21) { // decimal
						BigDecimal originalValue = updateRow.field(columnName);
						BigDecimal newVal = null;
						try {
							newVal = new BigDecimal(newValue);
						} catch (Exception e) {
							errors.append(paragraph("error", "Value could not be updated: " + e.getMessage()));
						}
						if ((newValue != null && originalValue != null && !newVal.equals(originalValue)) 
								  || (newValue == null && originalValue != null)
								  || (originalValue == null && newValue != null)) {
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + "=" + (newVal == null ? "null" : newValue));
						}
					} else if (type == 6) {  // Datetime
						Date originalValue = updateRow.field(columnName);
						Date newDate = parseDate(con.getLocale(),newValue);
						if (newValue != null && newDate == null) {
							errors.append(paragraph("error", "Invalid datetime value "+newValue+". "+columnName+" could not be updated"));							
						} else {
							
							if (DEBUG) System.out.println("Updating Datetime "+(originalValue == null ? "" : originalValue.toString())+" to "+newDate);
							if ((newValue != null && originalValue != null && !newDate.equals(originalValue)) 
								  || (newValue == null && originalValue != null)
								  || (originalValue == null && newValue != null)) {
								if (setString.length() > 0) {
									setString.append(", ");
								}
								setString.append(columnName + " = "+(newValue == null ? "null" : wrapWithQuotes(sqlDatetimeFormat.format(newDate))));
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
								if (setString.length() > 0) {
									setString.append(", ");
								}
								setString.append(columnName + "=" + wrapWithQuotes(newValue));
							}
						}
						
					} else if (type == 20) {
						String blob_name = parms.get(PARM_PREFIX+columnName); 
						if (blob_name != null && !blob_name.trim().equals("")) {
							if (DEBUG) System.out.println("Updating BLOB");
							blobs.add(columnName);
						}
						
					} else if (type == 9 || type==10 || type==11 || type==12) { // Embedded types - treat like a string (without quotes) - user beware
						Object originalValue = updateRow.field(columnName);
						if ((newValue != null && originalValue != null && !newValue.equals(originalValue)) // They will always detect a change because the string is formatted differently
								  || (newValue == null && originalValue != null)  // This could be considered a bug
								  || (originalValue == null && newValue != null)) {
							if (DEBUG) System.out.println("Embedded value changed");
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + "=" + newValue);
						}
						
					} else if (type == 13) { // Link
						ODocument o = updateRow.field(columnName);
						if (DEBUG) System.out.println("Updating Link "+(o == null ? "" : o.getIdentity().toString()));
						String originalValue = (o == null ? null : o.getIdentity().toString().substring(1));
						if ((newValue != null && originalValue != null && !newValue.equals(originalValue)) 
							  || (newValue == null && originalValue != null)
							  || (originalValue == null && newValue != null)) {
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + " = "+(newValue == null ? "null" : "#"+newValue));
						}

					} else if (type == 14) { // LinkList (Ordered and can contain duplicates)
						List<ODocument> o = updateRow.field(columnName);
						if (DEBUG) System.out.println("Updating Linkset "+(o == null ? "" : o));
						String[] newValues = {};
						if (newValue != null) {
							newValues = newValue.split(",");
						}
						// Remove all from original list
						if (o != null) {
							for (ODocument d : o) {
								if (d != null) {
									extras.add("REMOVE "+columnName+" = "+d.getIdentity().toString());
								}
							}
						}
						// Add new list
						for (String nv : newValues) {
							extras.add("ADD "+columnName+" = #"+nv);
						}

					} else if (type == 15) { // Linkset
						Set<ODocument> o = updateRow.field(columnName);
						if (DEBUG) System.out.println("Updating Linkset "+(o == null ? "" : o));
						String[] newValues = {};
						if (newValue != null) {
							newValues = newValue.split(",");
						}
						// Remove any from original list where not in new list
						if (o != null) {
							for (ODocument d : o) {
								boolean found = false;
								if (d != null) {
									String id = d.getIdentity().toString().substring(1);
									for (String nv : newValues) {
										if (id.equals(nv)) { found = true; }
									}
									if (!found) { extras.add("REMOVE "+columnName+" = "+d.getIdentity().toString()); }
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
							if (!found) { extras.add("ADD "+columnName+" = #"+nv); }
						}
					} else if (type == 16) { // LinkMap
						Map<String,ODocument> o = updateRow.field(columnName);
						if (DEBUG) System.out.println("Updating Linkset "+(o == null ? "" : o));
						String[] newValues = {};
						if (newValue != null) {
							newValues = splitCSV(newValue);
						}
						// Remove all from original list
						if (o != null) {
							for (String k : o.keySet()) {
								extras.add("REMOVE "+columnName+" = '"+k+"'");
							}
						}
						// Add all from new list
						for (String nv : newValues) {
							String[] v = nv.split(":",2);
							try {
								extras.add("PUT "+columnName+" = "+v[0]+",#"+v[1]);
							} catch (ArrayIndexOutOfBoundsException e) {
								System.out.println("Could not understand map field:"+nv);
							}									
						}
					} else if (type == 19) {  // Date
						Date originalValue = updateRow.field(columnName);
						Date newDate = parseDate(con.getLocale(),newValue);
						if (newValue != null && newDate == null) {
							errors.append(paragraph("error", "Invalid date value "+newValue+". "+columnName+" could not be updated"));							
						} else {
							if (DEBUG) System.out.println("Updating Date/Datetime "+(originalValue == null ? "" : originalValue.toString())+" to "+newDate);
							if ((newValue != null && originalValue != null && !newDate.equals(originalValue)) 
								  || (newValue == null && originalValue != null)
								  || (originalValue == null && newValue != null)) {
								if (setString.length() > 0) {
									setString.append(", ");
								}
								setString.append(columnName + " = "+(newValue == null ? "null" : wrapWithQuotes(sqlDateFormat.format(newDate))));
							}	
						}
					} else if (type == 21) { // Decimal
						BigDecimal originalValue = updateRow.field(columnName);
						BigDecimal newVal = null;
						try {
							newVal = new BigDecimal(newValue);
						} catch (Exception e) {
							errors.append(paragraph("error", "Decimal value could not be updated: " + e.getMessage()));
						}
						if ((newValue != null && originalValue != null && !newVal.equals(originalValue)) 
								  || (newValue == null && originalValue != null)
								  || (originalValue == null && newValue != null)) {
							if (setString.length() > 0) {
								setString.append(", ");
							}
							setString.append(columnName + "=" + (newVal == null ? "null" : newValue));
						}
					}
				}
				if ((setString.length() > 0 || extras.size() > 0 || blobs.size() > 0) && errors.length() == 0) {
					try {
						Object rc = 0;
						if (setString.length() > 0) {
							String updateStatement = "UPDATE #" + update_id + " SET " + setString.toString();
							if (DEBUG) System.out.println("table update: "+updateStatement);
							rc = con.update(updateStatement);
						}
						for (String extraUpdate : extras) {
							if (DEBUG) System.out.println("table extraUpdate: "+extraUpdate);
							rc = con.update("UPDATE #" + update_id +" "+ extraUpdate);							
						}
						if (blobs.size() > 0) {
							updateBlobs(con.get(update_id), table, blobs, parms, errors);
						}
						errors.append(paragraph("success", Message.get(con.getLocale(), "ROW_UPDATED",(rc==null?"null":"true"))));
						Server.tableUpdated(table);
						getCache().refreshContains(table);
						return true;
					} catch (Exception e) {
						errors.append(paragraph("error", "Row could not be updated: " + e.getMessage()));
						e.printStackTrace();
						return false;
					}
				} else {
					errors.append(paragraph("warning", Message.get(con.getLocale(),"NOTHING_TO_UPDATE")));
					return false;
				}
			} else {
				if (DEBUG) System.out.println("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing");
				errors.append("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing");
				return false;
			}
		}
		return false;
	}

	public boolean deleteRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuffer errors) {
		String update_id = parms.get("UPDATE_ID");
		if (update_id != null) {
			QueryResult initrows = con.query("SELECT FROM #" + update_id);
			if (initrows != null && initrows.size() == 1) {
				for (String columnName : initrows.getColumns()) {
					if (initrows.changed(initrows.getStringValue(0, columnName), parms.get(PARM_PREFIX+columnName))) {
						if (columnName.equals("LAST_MOD_TIME")) {
							errors.append(paragraph("error", "Row was changed by " + initrows.getStringValue(0, "LAST_MOD_USER")
									+ " at " + initrows.getStringValue(0, "LAST_MOD_TIME")));
							return false;
						}
					}
				}
				try {
					String deleteStatement = "DELETE FROM #" + update_id;
					if (DEBUG) System.out.println(deleteStatement);
					Object rc = con.update(deleteStatement);
					Thumbnail.deleteThumbnail(table, update_id);
					if (DEBUG) System.out.println("After delete. RowCount=" + rc);
					if (rc != null) {
						errors.append(paragraph("success", "Row deleted"));
						Server.tableUpdated(table);
						DatabaseConnection.rowCountChanged(table);
						getCache().refreshContains(table);
						return true;
					} else {
						errors.append(paragraph("error",
								"Row was NOT deleted - it was possibly updated by someone else.  RowCount=" + rc));
						return false;
					}
				} catch (Exception e) {
					errors.append(paragraph("error", "Row could not be deleted: " + e.getMessage()));
					return false;
				}
			} else {
				if (DEBUG) System.out.println("Error in permeagility.web.Table:deleteRow: Only one row may be returned by ID for deleting");
				errors.append("Error in permeagility.web.Table:deleteRow: Only one row may be returned by ID for deleting");
				return false;
			}
		}
		errors.append("Error in permeagility.web.Table:deleteRow: update_id is null?");
		return false;
	}

	public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
		String edit_id = parms.get("EDIT_ID");
		//String update_id = parms.get("UPDATE_ID");
		String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
		return paragraph("banner", (edit_id == null ? Message.get(con.getLocale(), "CREATE_ROW") 
				: Message.get(con.getLocale(), "UPDATE") + "&nbsp;"
			+ makeCamelCasePretty(table)))
			+ (con.getUser().equals("guest") ? "" : link(this.getClass().getName()+"?TABLENAME="+table,Message.get(con.getLocale(), "ALL_ROWS_IN_TABLE",makeCamelCasePretty(table))))
			+ "<FORM ACTION=\"#\" NAME=\""+formName+"\" METHOD=POST ENCTYPE=\"multipart/form-data\">" 
			+ getTableRowFields(con, table, parms)
			+ center((edit_id == null 
					? ((Server.getTablePriv(con, table) & PRIV_CREATE) > 0 ? submitButton(Message.get(con.getLocale(), "CREATE_ROW")) : "")
					: ((Server.getTablePriv(con, table) & PRIV_UPDATE) > 0 ? submitButton(Message.get(con.getLocale(), "UPDATE")) : "") 
						+ "&nbsp;&nbsp;"
						+ submitButton(Message.get(con.getLocale(), "CANCEL"))))
			+ (edit_id != null && (Server.getTablePriv(con, table) & PRIV_DELETE) > 0 ? paragraph("delete", deleteButton(con.getLocale())) : "")
			+ "</FORM>"
			+ getTableRowRelated(con,table,parms);
	}

	public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms) {
		return getTableRowFields(con, table, parms, null);
	}
	
	public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride) {
		String edit_id = (parms != null ? parms.get("EDIT_ID") : null);
		ODocument initialValues = null;
		if (edit_id != null) {
			QueryResult initrows = con.query("SELECT * FROM #" + edit_id);
			if (initrows != null && initrows.size() == 1) {
				initialValues = initrows.get(0);
			} else {
				if (DEBUG) System.out.println("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing rows="
									+ initrows.size());
				return paragraph("error",
						"Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing");
			}
		} else {
			if (DEBUG) System.out.println("getTableRowFields: No EDIT_ID specified");
		}
		StringBuffer fields = new StringBuffer();
		if (edit_id != null) {
			fields.append(hidden("UPDATE_ID", edit_id));
		}
		//fields.append(hidden("TABLENAME",table));
		String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");

		QueryResult columns = Server.getColumns(table, columnOverride);
		if (columns != null) {
			for (ODocument column : columns.get()) {
				String name = (String) column.field("name");
				if (parms.get("FORCE_"+name) != null) {
					fields.append(hidden(PARM_PREFIX+name,parms.get("FORCE_"+name)));
					continue;
				}
				// Added to support request approval using parms to prime the initial values
	//			if (initialValues.field(name) == null && parms.get(name) != null) {
	//				initialValues.field(name, parms.get(name));
	//			}
				fields.append(getColumnAsField(column, initialValues, con, formName, edit_id, parms));
	
			}
			return table("data", fields.toString());
		} else {
			return null;
		}
	}

	/**
	 * Returns a table row for a given column in the document
	 * @param column - column information (name, type, etc...)
	 * @param initialValues - document
	 * @param con - connection/context
	 * @param formName - form to make field part of
	 * @param edit_id - record id of the value to be edited (the identity of initialValues would be misleading on a new record) 
	 * @return
	 */
	private String getColumnAsField(ODocument column, ODocument initialValues, DatabaseConnection con, String formName, String edit_id, HashMap<String, String> parms) {
		Integer type = column.field("type");
		String name = (String) column.field("name");
		String prettyName = makeCamelCasePretty(name);
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
			return row(columnTopRight(50, small(prettyName))
					+ column(50, checkbox(PARM_PREFIX+name, (initialValue == null ? false : new Boolean(initialValue.toString())))));

		// Number
		} else if (type == 1 || type == 2 || type == 3 || type == 4 || type == 5 || type == 17 || type == 21) {  
			return row(columnTopRight(50, small(prettyName)) + column(50, input("number", PARM_PREFIX+name, initialValue)));

		// Datetime
		} else if (type == 6) { 
			return row(columnTopRight(50, small(prettyName))
					+ column(50, getDateTimeControl(formName, PARM_PREFIX+name, (initialValue != null && initialValue instanceof Date ? formatDate(con.getLocale(),(Date)initialValue,Message.get(con.getLocale(), "DATE_FORMAT")+" "+Message.get(con.getLocale(), "TIME_FORMAT")) : ""))));

		// Date
		} else if (type == 19) { 
			return row(columnTopRight(50, small(prettyName))
					+ column(50, getDateControl(formName, PARM_PREFIX+name, (initialValue != null && initialValue instanceof Date ? formatDate(con.getLocale(),(Date)initialValue,Message.get(con.getLocale(), "DATE_FORMAT")) : ""))));

		// Password (String)
		} else if (type == 7 && name.toUpperCase().endsWith("PASSWORD")) {
			return row(columnTopRight(50, small(prettyName)) + column(50, password(PARM_PREFIX+name, null, 15)));

		// Colour (String)
		} else if (type == 7 && (name.toUpperCase().endsWith("COLOR") || name.toUpperCase().endsWith("COLOUR"))) {
			if (DEBUG) System.out.println("Doing color field "+initialValues);
			return row(columnTopRight(50, small(prettyName)) + column(50, getColorControl(formName,PARM_PREFIX+name,(String)initialValue)));

		// String
		} else if (type == 7) {  
			if (initialValue != null && ((String) initialValue).length() > TEXT_AREA_THRESHOLD) {
				int linecount = countLines((String) initialValue);
				return row(columnTopRight(50, small(prettyName))
						+ column(50, textArea(PARM_PREFIX+name, initialValue, (linecount > 2 ? linecount + 5 : 5), TEXT_AREA_WIDTH)));
			} else {
				int length = 20;
				if (initialValue != null && initialValue.toString().length() > 20) {
					length = initialValue.toString().length() + 5;
				}
				return row(columnTopRight(50, small(prettyName)) + column(50, input("text", PARM_PREFIX+name, initialValue, length)));
			}

		// Binary
		} else if (type == 20) { // 8 = binary, 20 = custom 
			StringBuffer desc = new StringBuffer();
			if (edit_id != null) {
				String nail = null;
				String blobid = Thumbnail.getThumbnailId(initialValues.getClassName(), edit_id, name, desc);
				if (blobid != null) {
					nail = Thumbnail.getThumbnailLink(blobid, desc.toString());
				} else {
					nail = xSmall("Thumbnail not found for column "+name+" with rid="+edit_id);					
				} 
				return row(columnTopRight(50, small(prettyName+nail) + column(50, "<INPUT TYPE=FILE NAME=\""+PARM_PREFIX+name+"\" VALUE=\"None\">")));
			} else {
				return row(columnTopRight(50, small(prettyName))
					+ column(50, "<INPUT TYPE=FILE NAME=\""+PARM_PREFIX+name+"\" VALUE=\"None\">"));
			}

		// Embedded
		} else if (type == 9) {  
			String val = (initialValue == null ? "" : initialValue.toString());
			return row(columnTopRight(50, small(prettyName))
					+ column(50, textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));

		// Embedded list
		} else if (type == 10) {  
			String val = (initialValue == null ? "" : initialValue.toString());
			// convert val to JSON format for editing directly
			if (initialValue != null) {
				StringBuffer sb = new StringBuffer();
				sb.append("[\n");
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
			return row(columnTopRight(50, small(prettyName))
					+ column(50, textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));
			
		// Embedded set
		} else if (type == 11) {  
			String val = (initialValue == null ? "" : initialValue.toString());
			// convert val to JSON format for editing directly
			if (initialValue != null) {
				StringBuffer sb = new StringBuffer();
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
			return row(columnTopRight(50, small(prettyName))
					+ column(50, textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));

		// Embedded map
		} else if (type == 12) {  
			String val = (initialValue == null ? "" : initialValue.toString());
			// convert val to JSON format for editing directly
			if (initialValue != null) {
				StringBuffer sb = new StringBuffer();
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
			return row(columnTopRight(50, small(prettyName))
					+ column(50, textArea(PARM_PREFIX+name, val, 5, TEXT_AREA_WIDTH)));

		// Single link
		} else if (type == 13 && column.field("linkedClass") != null) {
			String v = null;
			if (initialValue != null && initialValue instanceof ODocument) {
				v = ((ODocument)initialValue).getIdentity().toString().substring(1);
			}
			return row(columnTopRight(50, small(prettyName))
					+ column(50, createListFromTable(PARM_PREFIX+name, (v == null ? "" : v), con, (String)column.field("linkedClass"), null, true, null, true)
						+(initialValues == null || initialValue == null ? "" : 
							link(this.getClass().getName()
								+"?TABLENAME="+column.field("linkedClass")
								+"&EDIT_ID="+v,Message.get(con.getLocale(), "GOTO_ROW")))));
				
		// Link list
		} else if (type == 14) {
			List<ODocument> l = null;
			try { l = initialValues.field(name); } catch (NullPointerException e) { } // It will do this if it doesn't exist
			return row(columnTopRight(50, small(prettyName))
					+ columnNoWrap(50, xSmall(linkListControl(con, PARM_PREFIX+name, (String)column.field("linkedClass"), getCache().getResult(con,getQueryForTable(con, (String)column.field("linkedClass"))), con.getLocale(), l))));
//				+ column(50, xSmall(ll.toString()+createListFromTable(name, "", con, (String)column.field("linkedClass"), null, true, null, true))));

		// Link set
		} else if (type == 15) {
			Set<ODocument> l = null;
			try {  l = initialValues.field(name);  } catch (NullPointerException e) { }  // It will do this if it doesn't exist
			//System.out.println("linkset size="+l.size());			
			return row(columnTopRight(50, small(prettyName))
					+ columnNoWrap(50, xSmall(linkSetControl(PARM_PREFIX+name, (String)column.field("linkedClass"), con.query(getQueryForTable(con, (String)column.field("linkedClass"))), con.getLocale(), l))));

		// Link map
		} else if (type == 16) {
			Map<String,ODocument> l = null;
			try {  l = initialValues.field(name);  } catch (NullPointerException e) { }  // It will do this if it doesn't exist
			if (l != null && DEBUG) System.out.println("linkmap size="+l.size());			
			return row(columnTopRight(50, small(prettyName))
					+ columnNoWrap(50, xSmall(linkMapControl(con,PARM_PREFIX+name, (String)column.field("linkedClass"), con.query(getQueryForTable(con, (String)column.field("linkedClass"))), con.getLocale(), l))));
			
		} else {
			System.out.println("Table.GetColumnAsField: Unrecognized type: "+type);
			return row(columnTopRight(50, small(prettyName)) + column(50, input("other", PARM_PREFIX+name, initialValue)));
		}
	}


	public String getTableRowRelated(DatabaseConnection con, String table, HashMap<String, String> parms) {
		StringBuffer sb = new StringBuffer();
		Stack<String> tables = new Stack<String>();
		Stack<String> columns = new Stack<String>();
		Stack<OType> types = new Stack<OType>();
		if (DEBUG) System.out.println("getTableRowRelated start..");
		
		String edit_id = parms.get("EDIT_ID");
		for (OClass c : con.getSchema().getClasses()) {
			for (OProperty p : c.properties()) {
				if (p.getLinkedClass() != null && p.getLinkedClass().getName().equals(table)) {
					if (SHOW_ALL_RELATED_TABLES || (Server.getTablePriv(con, c.getName()) & PRIV_READ) > 0) {
						tables.push(c.getName());
						columns.push(p.getName());
						types.push(p.getType());
					}
				}
			}
		}
		if (DEBUG) System.out.println("getTableRowRelated "+tables.size());
	    while (!tables.isEmpty()) {
			String relTable = tables.pop();
			String fkColumn = columns.pop();
			OType fkType = types.pop();
			
			int priv = Server.getTablePriv(con, table);
			//System.out.println("Privilege on table "+table+" for user "+con.getUser()+" = "+priv);
			
			HashMap<String,String> fkParms = new HashMap<String,String>();
			fkParms.put("FORCE_"+fkColumn, edit_id);
			String hiddenFields = hidden("TABLENAME", relTable)
								+ hidden("SOURCEEDIT_ID",edit_id)
								+ hidden("SOURCETABLENAME", table);
			sb.append(
				paragraph("banner",(table.equals(fkColumn) ?  makeCamelCasePretty(relTable) : makeCamelCasePretty(relTable) + " (" + makeCamelCasePretty(fkColumn) + ")"))
				+ ((priv & PRIV_CREATE) > 0 ? popupForm("CREATE_NEW_ROW_"+relTable,"permeagility.web.Table",Message.get(con.getLocale(),"NEW_ROW"),null,"NAME",
						paragraph("banner",Message.get(con.getLocale(), "CREATE_ROW"))
						+hiddenFields
						+getTableRowFields(con, relTable, fkParms)  // send fkcolumn data in parms
						+submitButton(Message.get(con.getLocale(), "CREATE_ROW"))) : "")
				+ "&nbsp;&nbsp;&nbsp;"
				+ (Server.isDBA(con) 
				  ? popupForm("NEWCOLUMN_"+relTable, null, Message.get(con.getLocale(), "ADD_COLUMN"),null,"NEWCOLUMNNAME",
						hiddenFields
						+newColumnForm(con))
				   + "&nbsp;&nbsp;&nbsp;"
				   + popupForm("RIGHTSOPTIONS_"+relTable, null, Message.get(con.getLocale(), "TABLE_RIGHTS_OPTIONS"),null,"XXX",
				   hiddenFields
				   + rightsOptionsForm(con,relTable,null,"")) 
				 : "")
				+ br() 
				+ ((Server.getTablePriv(con, relTable) & PRIV_READ) > 0 ?
						(fkType == OType.LINKLIST || fkType == OType.LINKSET || fkType == OType.LINKMAP || fkType == OType.LINKBAG 
							? (fkType == OType.LINKMAP ? getTableWhere(con,relTable,fkColumn,"containsvalue",edit_id) 
														:getTableWhere(con,relTable,fkColumn,"contains",edit_id))
							: getTableWhere(con,relTable,fkColumn,edit_id)
						)
				: Message.get(con.getLocale(),"NO_ACCESS_TO_TABLE"))
			);
	}
	return sb.toString();
	}

	public String newColumnForm(DatabaseConnection con) {
		String show = "ng-show=\"NEWDATATYPE == '"+DATATYPE_LINK+"' || NEWDATATYPE == '"+DATATYPE_LINKLIST+"' || NEWDATATYPE == '"+DATATYPE_LINKSET+"' || NEWDATATYPE == '"+DATATYPE_LINKMAP+"' \"";
		return
			paragraph("banner",Message.get(con.getLocale(), "NEW_COLUMN"))
			+ createList(con.getLocale(),"NEWDATATYPE", DATATYPE_INT, dataTypeNames, "ng-model=\"NEWDATATYPE\"", false, null, true)
			+ createListFromCache("NEWTABLEREF", null, con, TABLE_REF_LIST, show, false, null, true)
			+ br()
			+ input("NEWCOLUMNNAME", "")
			+ br()
			+ submitButton(Message.get(con.getLocale(), "NEW_COLUMN"));
	}
	
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

	private int countLines(String string) {
		return java.util.regex.Pattern.compile("[\\n]+").split(string.trim()).length;
	}

	public String getTable(DatabaseConnection con, String table) {
		String query = "SELECT FROM " + table;
		return getTable(con,table,query,null,0);
	}

	public String getTable(DatabaseConnection con, String table, long page) {
		String query = "SELECT FROM " + table;
		return getTable(con,table,query,null,page);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String columnValue) {
		String query = "SELECT FROM " + table + " WHERE "+column+" = #"+columnValue;
		return getTable(con,table,query,column, 0);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String operator, String columnValue) {
		String query = "SELECT FROM " + table + " WHERE "+column+" "+operator+" #"+columnValue;
		return getTable(con,table,query,column, 0);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String columnValue, long page) {
		String query = "SELECT FROM " + table + " WHERE "+column+"= #"+columnValue;
		return getTable(con,table,query,column,page);
	}

	public String getTableWhere(DatabaseConnection con, String table, String column, String operator, String columnValue, long page) {
		String query = "SELECT FROM " + table + " WHERE "+column+" "+operator+" #"+columnValue;
		return getTable(con,table,query,column,page);
	}

	public String getTable(DatabaseConnection con, String table, String query, String hideColumn, long page) {
		return getTable(con, table, query, hideColumn, page, null);
	}

	public String getTable(DatabaseConnection con, String table, String query, String hideColumn, long page, String columnOverride) {
		try {
			StringBuffer sb = new StringBuffer();
			int rowCount = 0;
			
			// Handle Paging
			long totalRows = con.getRowCount(table);
			if (totalRows > ROW_COUNT_LIMIT) {
				sb.append("Page&nbsp;");
				long pageCount = totalRows/ROW_COUNT_LIMIT+1;
				for (long p=1; p<=pageCount; p++) {
					if (Math.abs(page - p) < PAGE_WINDOW || pageCount - p < PAGE_WINDOW || p < PAGE_WINDOW) {
						if (p == page) {
							sb.append(fontSize(3, bold(color("red", ""+p)))+"&nbsp;");
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
			if (DEBUG) System.out.println("permeagility.web.Table:query="+query);
			QueryResult rs = con.query(query + skip + " LIMIT "+ROW_COUNT_LIMIT);
			
			// Get the table's columns
			QueryResult columns = Server.getColumns(table, columnOverride);
			if (columns == null) {
				System.out.println("Get columns for table:"+table+" returned null");
			} else {
				sb.append(getRowHeader(con, table, columns, hideColumn));
				for (ODocument row : rs.get()) {
					//if (DEBUG) System.out.println("Print row...");
					sb.append(rowOnClick("clickable", getRow(columns, row, con, hideColumn), "window.location.href='" + this.getClass().getName()
							+ "?EDIT_ID=" + row.getIdentity().toString().substring(1) + "&TABLENAME=" + table + "';"));
					rowCount++;
					if (rowCount >= ROW_COUNT_LIMIT) break;
				}
				sb.append(tableFoot(columnSpan(columns.size(), paragraph("RowCount=" + rowCount + " of " + totalRows + " rows, page="+page) )));
			}
			return table("sortable", sb.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return "Error: " + e.getMessage() + "\n" + e.toString();
		}
	}

	public String getRowHeader(DatabaseConnection con, String table, QueryResult columns, String hideColumn) throws SQLException {
		StringBuffer sb = new StringBuffer();
		for (ODocument column : columns.get()) {
			String columnName = column.field("name");
			String messageKey = table + '.' + columnName;
			String colNameI18N = Message.get(con.getLocale(), messageKey);
			if (colNameI18N.equals(messageKey)) {
				colNameI18N = makeCamelCasePretty(columnName);
			}
			if (column.field("type") == OType.TRANSIENT) {
				sb.append(tableHead("sorttable_nosort", center(xSmall(bold(columnName)))));				
			} else if (!columnName.toUpperCase().endsWith("PASSWORD") && !columnName.startsWith("_")
					&& (hideColumn == null || !columnName.equals(hideColumn)) ) {
				if (columnName.startsWith("button(") && columnName.length() > 8 && columnName.indexOf(':',7) > 7) {
					int cp = columnName.indexOf(':',7);
					String n = columnName.substring(7,cp);
					String l = columnName.substring(cp+1,columnName.length()-1);
					sb.append(tableHead(center(xSmall(bold(l)))));
				} else {
					sb.append(tableHead(center(xSmall(bold(colNameI18N)))));
				}
			}
		}
		return row(sb.toString());
	}

	public String getRow(QueryResult columns, ODocument d, DatabaseConnection con, String hideColumn) throws SQLException {
		StringBuffer sb = new StringBuffer();
//		if (DEBUG) System.out.println("Table.getRow colCount="+columns.size());
		for (ODocument column : columns.get()) {
			String fieldName = column.field("name");
			if ((hideColumn == null || !fieldName.equals(hideColumn)) 
					&& !fieldName.toUpperCase().endsWith("PASSWORD") && !fieldName.startsWith("_")) {
				if (fieldName.startsWith("button(") && fieldName.length() > 8 && fieldName.indexOf(':',7) > 7) {
					int cp = fieldName.indexOf(':',7);
					String n = fieldName.substring(7,cp);
					String l = fieldName.substring(cp+1,fieldName.length()-1);
					sb.append(column(form(button(n,d.getIdentity().toString().substring(1),l))));
				} else {
					sb.append(getColumnAsCell(column, d, con));					
				}
			}
		}
		return sb.toString();
	}

	private String getColumnAsCell(ODocument column, ODocument d, DatabaseConnection con) {
		StringBuffer sb = new StringBuffer();
		String columnName = column.field("name");
		Integer columnType = column.field("type"); 
//		if (DEBUG) System.out.println("permeagility.web.Table:Column name = "+columnName+" type = "+(columnType==null ? "null" : columnType));
		if (columnType == 0) {
			sb.append(column(checkboxDisabled(columnName, (d.field(columnName) == null ? false : (Boolean)d.field(columnName)))));
		} else if (columnType == 1 || columnType == 2 || columnType == 3) {   // OrientDB int, short, long type
			sb.append(column("number", xSmall(""+formatNumber(con.getLocale(),(Number)d.field(columnName),INT_FORMAT))));
		} else if (columnType == 4 || columnType == 5) {   // OrientDB float, double
			sb.append(column("number", xSmall(""+formatNumber(con.getLocale(),(Number)d.field(columnName),FLOAT_FORMAT))));
		} else if (columnType == 6) {  // OrientDB Datetime
			sb.append(column(xSmall(""+(d.field(columnName) == null ? "" : formatDate(con.getLocale(),(Date)d.field(columnName),Message.get(con.getLocale(), "DATE_FORMAT")+' '+Message.get(con.getLocale(), "TIME_FORMAT"))))));
		} else if (columnType == 7) {  // String
			if (columnName.toUpperCase().endsWith("COLOR") 
				|| columnName.toUpperCase().endsWith("COLOUR")) {
				sb.append(columnColor(5, (String)d.field(columnName)));				
			} else {
				String stringvalue = d.field(columnName);
				if (stringvalue != null && stringvalue.length() > MAX_STRING_DISPLAY) {
					stringvalue = stringvalue.substring(0, MAX_STRING_DISPLAY) + "...";
				}
				sb.append(column(xSmall(stringvalue)));
			}
		} else if (columnType == 20) {  // Binary
			StringBuffer desc = new StringBuffer();
			String blobid = Thumbnail.getThumbnailId(d.getClassName(), d.getIdentity().toString().substring(1), columnName, desc);
			if (blobid != null) {
				sb.append(column(Thumbnail.getThumbnailLink(blobid, desc.toString())));
			} else {
				sb.append(column(xSmall("Thumbnail not found for column "+columnName+" with rid="+d.getIdentity().toString())));					
			} 

		} else if (columnType >= 9 && columnType <= 12) {  // Embedded
			String stringvalue = ""+d.field(columnName);
			if (stringvalue != null && stringvalue.length() > MAX_STRING_DISPLAY) {
				stringvalue = stringvalue.substring(0, MAX_STRING_DISPLAY) + "...";
			}
			sb.append(column(xSmall(stringvalue)));
		} else if (columnType == 13) {  // Link
			ODocument l = d.field(columnName);
			String desc = "";
			if (l != null) {
				desc = getDescriptionFromDocument(con, l);
				if (desc == null) {
					desc = (String)l.field("name");
				}
			}
			sb.append(column(xSmall(desc == null ? "null" : desc)));
		} else if (columnType == 14) {  // LinkList
			List<ODocument> l = d.field(columnName);
			StringBuffer ll = new StringBuffer();
			if (l != null) {
				if (DEBUG) System.out.println("linkList size="+l.size()+(l.size()>0 ? " type="+l.get(0).getClass().getName() : ""));
				for (ODocument o : l) {
					ll.append(getDescriptionFromDocument(con, o)+br());
				}
			}
			sb.append(column(xSmall(ll.toString())));
		} else if (columnType == 15) {  // LinkSet
			Set<ODocument> l = d.field(columnName);
			StringBuffer ll = new StringBuffer();
			if (l != null) {
				for (ODocument o : l) {
					ll.append(getDescriptionFromDocument(con, o)+br());
				}
			}
			sb.append(column(xSmall(ll.toString())));
		} else if (columnType == 16) {    // LinkMap
			Map<String,ODocument> l = d.field(columnName);
			StringBuffer ll = new StringBuffer();
			if (l != null) {
				for (String k : l.keySet()) {
					ODocument o = l.get(k);
					if (o != null) {
						ll.append(k+":"+getDescriptionFromDocument(con, o)+br());
					}
				}
			}
			sb.append(column(xSmall(ll.toString())));
		} else if (columnType == 17) {  // Byte
			sb.append(column(xSmall(""+d.field(columnName))));
		} else if (columnType == 18) {  // Transient
			sb.append(column(xSmall("transient")));
		} else if (columnType == 19) {  // OrientDB Date
			sb.append(column(xSmall(""+(d.field(columnName) == null ? "" : formatDate(con.getLocale(),(Date)d.field(columnName),Message.get(con.getLocale(), "DATE_FORMAT"))))));
		} else if (columnType == 20) {  // Custom
			sb.append(column(xSmall("custom")));
		} else if (columnType == 21) {   // OrientDB Decimal
			sb.append(column("number", xSmall(""+formatNumber(con.getLocale(),(Number)d.field(columnName),FLOAT_FORMAT))));
		} else if (columnType == 22) {   // LinkBag
			sb.append(column(xSmall("LinkBag")));
		} else if (columnType == 23) {   // Any
			Object value = d.field(columnName);
			sb.append(column(xSmall(value == null ? "null" : value.toString() )));
//		} else if (columnType == 5555) {  // ?? BLOB??
		} else {
			if (DEBUG) System.out.println("Table: unrecognized type "+columnType);
			sb.append(column(xSmall("??"+columnType+"??")));
		}
		return sb.toString();
	}

	public String advancedOptions(DatabaseConnection con, String table, HashMap<String, String> parms) {
		StringBuffer errors = new StringBuffer();
		String submit = parms.get("SUBMIT");
		if (submit != null) {
			if (submit.equals(Message.get(con.getLocale(), "RENAME_TABLE_BUTTON"))) {
				if (isNullOrBlank(parms.get("RENAME_TABLE"))) {
					errors.append(paragraph("error", "Please specify a new name"));
				} else {
					try {
						String newtable = parms.get("RENAME_TABLE");
						con.update("ALTER CLASS "+table+" NAME "+newtable);
						table = newtable;
						Server.tableUpdated("metadata:schema");
						Server.clearColumnsCache(table);
						return head("Redirect")
								+ bodyOnLoad("Redirecting...", "window.location.href='permeagility.web.Table?TABLENAME=" + table + "';");
					} catch (Exception e) {
						errors.append(paragraph("error", e.getMessage()));
					}
				}
			} else if (submit.equals(Message.get(con.getLocale(), "RENAME_COLUMN_BUTTON"))) {
				if (isNullOrBlank(parms.get("RENAME_COLUMN"))) {
					errors.append(paragraph("error", "Please specify a new name for the column"));
				} else {
					try {
						String oldcolumn = parms.get("COLUMN_TO_RENAME");
						String newcolumn = parms.get("RENAME_COLUMN");
						con.update("ALTER PROPERTY " + table + "."+ oldcolumn +" NAME " + newcolumn);
						Server.tableUpdated("metadata:schema");
						Server.clearColumnsCache(table);
						return head("Redirect")
								+ bodyOnLoad("Redirecting...", "window.location.href='permeagility.web.Table?TABLENAME=" + table + "';");
					} catch (Exception e) {
						errors.append(paragraph("error", e.getMessage()));
					}
				}
			} else if (submit.equals(Message.get(con.getLocale(), "DROP_COLUMN_BUTTON"))) {
				if (isNullOrBlank(parms.get("COLUMN_TO_DROP"))) {
					errors.append(paragraph("error", "Please specify a column to drop"));
				} else {
					try {
						OClass c = con.getSchema().getClass(table);
						c.dropProperty(parms.get("COLUMN_TO_DROP"));
						con.getSchema().save();
						Server.tableUpdated("metadata:schema");
						Server.clearColumnsCache(table);
						return head("Redirect")
								+ bodyOnLoad("Redirecting...", "window.location.href='permeagility.web.Table?TABLENAME=" + table + "';");
					} catch (Exception e) {
						errors.append(paragraph("error", e.getMessage()));
					}
				}
			} else if (submit.equals(Message.get(con.getLocale(), "DROP_TABLE_BUTTON"))) {
				try {
					con.update("DROP class " + table);
					Server.clearColumnsCache(table);
					DatabaseConnection.rowCountChanged(table);
					return head("Redirect") + bodyOnLoad("Redirecting...", "window.location.href='permeagility.web.Schema';");
				} catch (Exception e) {
					errors.append(paragraph("error", e.getMessage()));
				}
			}
		}

		String title = table + " " + Message.get(con.getLocale(), "ADVANCED_OPTIONS");
		parms.put("SERVICE", title);
		return head(title)
				+ body(standardLayout(con, parms,
					advancedOptionsForm(con, table, parms,errors.toString())
					+ br()
					+ link("permeagility.web.Table?TABLENAME=" + table, "Back to table manager")
				));
	}

	public String advancedOptionsForm(DatabaseConnection con, String table, HashMap<String, String> parms,String errors) {
		return form(hidden("ADVANCED_OPTIONS", "YES")
				+ paragraph("banner",Message.get(con.getLocale(), "ADVANCED_OPTIONS"))
				+ errors
				+ paragraph("Rename table to " + input("RENAME_TABLE", (parms != null ? parms.get("RENAME_TABLE") : ""))
					+ submitButton(Message.get(con.getLocale(), "RENAME_TABLE_BUTTON")))
				+ paragraph("Rename column "
					+ createListFromCache("COLUMN_TO_RENAME", (parms != null ? parms.get("COLUMN_TO_RENAME") : ""), con,
							"SELECT name as rid, name FROM (SELECT expand(properties) FROM (select expand(classes) from metadata:schema) where name = '" + table + "') ORDER BY name") 
						+ " to "
					+ input("RENAME_COLUMN", (parms != null ? parms.get("RENAME_COLUMN") : ""))
					+ submitButton(Message.get(con.getLocale(), "RENAME_COLUMN_BUTTON")))
				+ paragraph("Drop column "
					+ createListFromCache("COLUMN_TO_DROP", (parms != null ? parms.get("COLUMN_TO_DROP") : ""), con,
							"SELECT name as rid, name FROM (SELECT expand(properties) FROM (select expand(classes) from metadata:schema) where name = '" + table + "') ORDER BY name") 
					+ confirmButton(Message.get(con.getLocale(), "DROP_COLUMN_BUTTON"), "Drop column?"))
				+ paragraph("Drop Table " + table + "   "
					+ confirmButton(Message.get(con.getLocale(), "DROP_TABLE_BUTTON"),
							"Table will be dropped/deleted - this can not be undone. Proceed?"))
		);
	}

	public String rightsOptions(DatabaseConnection con, String table, HashMap<String, String> parms) {
		StringBuffer errors = new StringBuffer();
		String submit = (parms != null ? parms.get("SUBMIT") : null);
		String right = (parms != null ? parms.get("RIGHT") : null);
		String role = (parms != null ? parms.get("ROLESELECT") : null);
		if (submit != null && submit.equals(Message.get(con.getLocale(), "GRANT_RIGHT"))) {
			if (DEBUG) System.out.println("Granting right");
			String grantQuery = "GRANT "+right
								+" ON database.class."+table
								+" TO "+role;
			System.out.println("Executing GRANT: "+grantQuery);
			try {
				con.update(grantQuery);
				Server.tableUpdated("ORole");  // Privs are stored in ORole
				Server.tablePrivUpdated(table);
			} catch (Exception e) {
				errors.append(e.getLocalizedMessage());
				e.printStackTrace();
			}
			return head("Redirect") + bodyOnLoad("Redirecting...", "window.location.href='permeagility.web.Table?TABLENAME="+table+"';");
		}
		if (submit != null && submit.equals(Message.get(con.getLocale(), "REVOKE_RIGHT"))) {
			if (DEBUG) System.out.println("Revoking right ");
			String revokeQuery = "REVOKE "+right
								+" ON database.class."+table
								+" FROM "+role;
			System.out.println("Executing REVOKE: "+revokeQuery);
			try {
				con.update(revokeQuery);
				Server.tableUpdated("ORole");  // Stored in ORole
				Server.tablePrivUpdated(table);
			} catch (Exception e) {
				errors.append(e.getLocalizedMessage());
				e.printStackTrace();
			}
			return head("Redirect") + bodyOnLoad("Redirecting...", "window.location.href='permeagility.web.Table?TABLENAME="+table+"';");
		}

		String title = table + " " + Message.get(con.getLocale(), "ADVANCED_OPTIONS");
		parms.put("SERVICE", title);
		return head(title)
				+ body(standardLayout(con, parms,
					rightsOptionsForm(con, table, parms,errors.toString())
					+ br()
					+ link("permeagility.web.Table?TABLENAME=" + table, "Back to table manager")
				));
	}

	public String rightsOptionsForm(DatabaseConnection con, String table, HashMap<String, String> parms,String errors) {
		StringBuffer currentRights = new StringBuffer();
		List<String> rightsNames = new ArrayList<String>();
		HashMap<String,Byte> privs = Server.getTablePrivs(table);

		for (String role : privs.keySet()) {
			Byte b = privs.get(role);
			StringBuffer sb = new StringBuffer();
			if (b.intValue() == 0) {
				sb.append(Message.get(con.getLocale(), "PRIV_NONE"));
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
			currentRights.append(Message.get(con.getLocale(), "ROLE_CAN_PRIV", role,sb.toString())+br());
		}

		rightsNames.add("NONE");
		rightsNames.add("CREATE");
		rightsNames.add("READ");
		rightsNames.add("UPDATE");
		rightsNames.add("DELETE");
		rightsNames.add("ALL");

		return form(hidden("RIGHTS_OPTIONS", "YES") + errors
				+ paragraph("banner",Message.get(con.getLocale(), "EXISTING_RIGHTS"))
				+ currentRights.toString()
				+ paragraph("banner",Message.get(con.getLocale(), "ADD_OR_REMOVE_RIGHT"))
				+ createListFromCache("ROLESELECT", null, con, ROLES_LIST, null, false, null, true)
				+ createList(con.getLocale(),"RIGHT", null, rightsNames, null, false, null, true)
				+submitButton(Message.get(con.getLocale(), "GRANT_RIGHT"))
				+submitButton(Message.get(con.getLocale(), "REVOKE_RIGHT"))
		);
	}

	public static String password(String name, Object value, int size) {
    	return "<INPUT TYPE=\"PASSWORD\" NAME=\""+name+"\" VALUE=\""+(value==null ? "" : value)+"\" SIZE=\""+size+"\">";
    }

	public static String getTypeName(Integer i) {
		OType type = OType.getById(i.byteValue());
		if (type == OType.DOUBLE) {
			return DATATYPE_FLOAT;
		} else if (type == OType.LONG) {
			return DATATYPE_INT;
		} else if (type == OType.BOOLEAN) {
			return DATATYPE_BOOLEAN;
		} else if (type == OType.STRING) {
			return DATATYPE_TEXT;
		} else if (type == OType.DATETIME) {
			return DATATYPE_DATETIME;
		} else if (type == OType.DATE) {
			return DATATYPE_DATE;
		} else if (type == OType.CUSTOM) {
			return DATATYPE_BLOB;
		} else if (type == OType.DECIMAL) {
			return DATATYPE_DECIMAL;
		} else if (type == OType.LINK) {
			return DATATYPE_LINK;
		} else if (type == OType.LINKLIST) {
			return DATATYPE_LINKLIST;
		} else if (type == OType.LINKMAP) {
			return DATATYPE_LINKMAP;
		} else if (type == OType.LINKSET) {
			return DATATYPE_LINKSET;
		} else if (type == OType.LINKBAG) {
			return "LinkBag (not supported)";
		} else {
			return (type.name());
		}
	}
	
}