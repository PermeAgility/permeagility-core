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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import com.arcadedb.database.Document;
import com.arcadedb.database.EmbeddedDocument;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.RID;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Type;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class Table extends Weblet {

    public static boolean DEBUG = false;
    public static int MAX_STRING_DISPLAY = 100;
    public static boolean ALWAYS_TEXT_AREA = true;  // Use text areas for all string fields
    public static int TEXT_AREA_THRESHOLD = 40;  // When the data is larger than this size, the input will be a text area
    public static int TEXT_AREA_WIDTH = 50;      // When showing a column as a cell, only show this many characters
    public static long ROW_COUNT_LIMIT = 200;    // Limit to number of rows to retrieve before paging
    public static long DOT_INTERVAL = 5;         // Interval for dot when numerous pages - probably should derive this to be more dynamic
    public static long DOT_LIMIT = 100;         // Limit to number of dot-links to show, browser slows with too many
    public static long PAGE_WINDOW = 3;          // Always show this many pages around the current page when there are many dots
    public static String PARM_PREFIX = "PARM_";  // Use this prefix in front of all column names as form field names (parameter names) 
    public static boolean SHOW_ALL_RELATED_TABLES = true;   // Will show that relationships exist even if no access to the table

    public String getPage(DatabaseConnection con, java.util.HashMap<String, String> parms) {      
        String restResult = processREST(con, parms);
        return restResult != null ? restResult 
            : new Schema().getPage(con,parms); // Return default of schema page if no result
    }

 /*        // Todo: Check for translation, if no translation, make pretty
        String prettyTable = Message.get(locale, "TABLE_" + table);
        if (table != null && ("TABLE_" + table).equals(prettyTable)) {  prettyTable = makeCamelCasePretty(table); }
        String title = Message.get(locale, "TABLE_EDITOR", table != null ? prettyTable : "None");
  */

    public String processREST(DatabaseConnection con, HashMap<String, String> parms) {
        StringBuilder errors = new StringBuilder();        
        String restOfURL = parms.get("REST_OF_URL");  // if rest attributes exist then parse table/id
        if (restOfURL != null && !restOfURL.isEmpty()) {
            String[] restParts = restOfURL.split("/");  // 0=table, 1=rid
            String rid = null;
            String table = null;
            table = restParts[0];

            if (restParts.length > 1) rid = restParts[1];
            // if table only or *, GET returns all rows in table (with possible filter conditions encoded eg. */dept.name/eq/sales)
            String httpMethod = parms.get("HTTP_METHOD");
            if (httpMethod.equals("GET") && (rid == null || rid.equals("*"))  ) {
                if (con.getSchema().existsType(table)) {
                    return getTableWithControls(con, parms, table);
                } else {
                    return paragraph("error","Table: Error "+table+" table not found");
                }
            }
            if (restParts.length > 2) {
                System.out.println("Further REST parts not implemented yet and will be ignored");
            }
            if (httpMethod.equals("GET")) {
                // if table and row specified, GET returns a single row as a form (use - for new row form)
                if (!rid.equals("-")) parms.put("EDIT_ID", rid);  // leave edit id out for new row
                return getTableRowForm(con, table, parms);
            } else {
                if (httpMethod.equals("PUT")) {
                    // PUT to 'columns' will add a column provided the proper details are given as parameters 
                    if (rid != null && rid.equals("columns")) {
                        addColumn(con, parms, table, errors);
                        return errors.toString() + getTableWithControls(con, parms, table);
                    }
                    // PUT to insert and return the new row 
                    if (insertRow(con, table, parms, errors)) {
                        return getTableRowForm(con, table, parms);
                    } else {
                        System.out.println("Insert errors: "+errors.toString());
                        return errors.toString() + getTableRowForm(con, table, parms);
                    }
                } else if (httpMethod.equals("PATCH")) {
                    // PATCH to update and return the updated row (Possibly Copy button was pressed in this form)
                    if (!rid.equals("-")) parms.put("EDIT_ID", rid);  // updateRow needs this
                    if (parms.get("SUBMIT") != null && parms.get("SUBMIT").equals("COPY")) {
                        if (copyRow(con, table, parms, errors)) {
                            return errors.toString() + getTableRowForm(con, table, parms);
                        }
                    }
                    if (updateRow(con, table, parms, errors)) {
                        return errors.toString() + getTableWithControls(con, parms, table);
                    } else {
                        System.out.println("Update errors: "+errors.toString());
                        return errors.toString() + getTableRowForm(con, table, parms);
                    }
                } else if (httpMethod.equals("DELETE")) {
                    // DELETE to remove the row
                    if (!rid.equals("-")) parms.put("EDIT_ID", rid);  // deleteRow needs this
                    if(deleteRow(con, table, parms, errors)) {
                        return errors.toString() + getTableWithControls(con, parms, table);
                    } else {
                        System.out.println("Delete errors: "+errors.toString());
                        return errors.toString() + getTableRowForm(con, table, parms);
                    }
                } else if (httpMethod.equals("POST")) {
                    String opFlag = parms.get("ADVANCED_OPTIONS");
                    String ropFlag = parms.get("RIGHTS_OPTIONS");
                    if (opFlag != null && opFlag.equals("YES")) {
                        String advOp = advancedOptions(con, table, parms, errors);
                        if (advOp != null && !advOp.isEmpty()) {
                            return advOp;
                        }
                    } else if (ropFlag != null && ropFlag.equals("YES")) {
                        String rOp = rightsOptions(con, table, parms);
                        if (rOp != null && !rOp.isEmpty()) {
                            return rOp;
                        }
                    } else {
                        return null;
                    }
                }
            }
        }
        return null;  // No REST parameters specified
    }

    public String processSubmit(DatabaseConnection con, HashMap<String, String> parms, String table, StringBuilder errors) {

        System.out.println("Table.processSubmit called ----------------------------- *********************");
        Locale locale = con.getLocale();
        String submit = parms.get("SUBMIT");
        String editId = parms.get("EDIT_ID");

        if (DEBUG) System.out.println("In Table.processSubmit() submit="+submit);

        // Show edit form if row selected for edit
        if (editId != null && submit == null) {
            return head(con, "Edit", "")
                    + body(getTableRowForm(con, table, parms));
        }

        // If no submit, we are done here
        if (submit == null || submit.isEmpty()) {
            return null;
        }

        // Process cancel action
        if (submit.equals("CANCEL")) {
            return redirectUsingSource(parms, "TABLENAME=" + table);
        }
        // Process create action
        if (submit.equals("CREATE_ROW")) {
            if (insertRow(con, table, parms, errors)) {
                redirectUsingSource(parms, "TABLENAME=" + table + "&EDIT_ID="+parms.get("EDIT_ID"));
            } else {
                errors.append(paragraph("error", "Could not insert"));
                return head(con, "Insert", "")
                        + body(
                            errors.toString()
                            + form("NEWROW", "#",
                                    paragraph("banner", Message.get(locale, "CREATE") + "&nbsp;" + makeCamelCasePretty(table))
                                    + getTableRowFields(con, table, parms)
                                    + center(submitButton(locale, "CREATE_ROW")
                                            + submitButton(locale, "CANCEL"))
                            )
                    );
            }
        }
        // Process actions on an existing record
        if (editId != null) {
            if (submit.equals("COPY")) {
                if (copyRow(con, table, parms, errors)) {
                    //return redirect(parms, this, "TABLENAME=" + table + "&EDIT_ID="+parms.get("EDIT_ID"));
                    return redirectHTMX(parms, parms.get("EDIT_ID"));  // Copy will put new record in edit id
                } else {
                    errors.append(paragraph("error", "Could not copy"));
                    return head(con, "Insert", "")
                        + body(
                            errors.toString()
                            + form("NEWROW", "#",
                                    paragraph("banner", Message.get(locale, "COPY") + "&nbsp;" + makeCamelCasePretty(table))
                                    + getTableRowFields(con, table, parms)
                                    + center(submitButton(locale, "CREATE_ROW") + submitButton(locale, "CANCEL"))
                        ));
                }
            } else if (submit.equals("DELETE")) {
                if (deleteRow(con, table, parms, errors)) {
                   return null;
                } else {
                    return head(con, "Could not delete", "")
                            + bodyMinimum(errors.toString()+getTableRowForm(con, table, parms));
                }
            } else if (submit.equals("UPDATE")) {
                if (DEBUG) {
                    System.out.println("In updating row");
                }
                if (updateRow(con, table, parms, errors)) {
                    return null;
//                    return redirectUsingSource(parms, "TABLENAME=" + table);
                } else {
                    return head(con, "Could not update", "")
                            + bodyMinimum(errors.toString()+getTableRowForm(con, table, parms));
                }
            }
            System.out.println("Table.processSubmit: Unrecognized submit value: "+submit);
        }
        return null;  // Nothing happened here
    }

    /* After a POST operation (create, update, delete) redirect to the source table/record if it is in the parms
      because the source table/id could be a list, pop it
    */ 
    public String redirectUsingSource(HashMap<String, String> parms, String defaultPath) {
            String sourceTable = parms.get("SOURCETABLENAME");
            String sourceId = parms.get("SOURCEEDIT_ID");
            String editId = parms.get("EDIT_ID");
            if (sourceTable != null && !sourceTable.isEmpty()  && sourceId != null && !sourceId.isEmpty()) { 
                if (DEBUG) {
                    System.out.println("Table (Cancel) popping sourceTableName=" + parms.get("SOURCETABLENAME") + " id=" + parms.get("SOURCEEDIT_ID"));
                }
                int lastComma = sourceTable.lastIndexOf(',');
                int lastCommaId = sourceId.lastIndexOf(',');
                String oldTable = (lastComma > 0 && lastComma < sourceTable.length() ? sourceTable.substring(lastComma + 1) : sourceTable);
                String oldId = (lastCommaId > 0 && lastCommaId < sourceId.length() ? sourceId.substring(lastCommaId + 1) : sourceId);
                String newSourceTable = sourceTable.substring(0, (lastComma > 0 ? lastComma : sourceTable.length()));
                String newSourceId = sourceId.substring(0, (lastCommaId > 0 ? lastCommaId : sourceId.length()));
                if (oldId.equals(editId)) { // popping onto itself - pop one more
                    if (DEBUG) {
                        System.out.println("Prevent popping onto itself, skipping");
                    }
                    lastComma = newSourceTable.lastIndexOf(',');
                    lastCommaId = newSourceId.lastIndexOf(',');
                    oldTable = (lastComma > 0 && lastComma < newSourceTable.length() ? newSourceTable.substring(lastComma + 1) : newSourceTable);
                    oldId = (lastCommaId > 0 && lastCommaId < newSourceId.length() ? newSourceId.substring(lastCommaId + 1) : newSourceId);
                    newSourceTable = newSourceTable.substring(0, (lastComma > 0 ? lastComma : newSourceTable.length()));
                    newSourceId = newSourceId.substring(0, (lastCommaId > 0 ? lastCommaId : newSourceId.length()));
                    if (DEBUG) {
                        System.out.println("Popped to: " + oldTable + " " + oldId);
                    }
                    if (oldId.equals(newSourceId)) {
                        if (DEBUG) {
                            System.out.println("Removing old source information - at last record");
                        }
                        newSourceTable = "";
                        newSourceId = "";
                    }
                }
                return redirect(parms, this, "TABLENAME=" + oldTable + "&EDIT_ID=" + oldId
                        + (!oldTable.equals(newSourceTable) && !oldId.equals(newSourceId) ? "&SOURCETABLENAME=" + newSourceTable + "&SOURCEEDIT_ID=" + newSourceId : ""));
            } else {
                return redirect(parms, this, defaultPath);
            }

    }

    public boolean addColumn(DatabaseConnection con, HashMap<String,String> parms, String table, StringBuilder errors) {
        Locale locale = con.getLocale();
        String cn = parms.get("NEWCOLUMNNAME");
        String dt = parms.get("NEWDATATYPE");
        String tr = parms.get("NEWTABLEREF");
        Type type = null;
        if (dt == null || dt.isEmpty()) {
        } else {
            type = getTypeFromName(dt);
        }
        if (type == Type.LINK || type == Type.LIST || type == Type.MAP) {
            if (isNullOrBlank(tr)) {
                errors.append(paragraph("error", Message.get(locale, "LINK_TYPES_NEED_LINK_TABLE")));
            }
        } else {
            tr = null;
        }
        if (type == null || isNullOrBlank(cn) || isNullOrBlank(dt)) {
            errors.append(paragraph("error", Message.get(locale, "COLUMN_NAME_AND_TYPE_REQUIRED")));
        } else {
            try {
                    DocumentType c = con.getSchema().getType(table);
                    if (c == null) {
                        errors.append(paragraph("error", Message.get(locale, "CANNOT_CREATE_COLUMN") + " Cannot find class to create column in table: " + table));
                    } else {
                        String camel = makePrettyCamelCase(cn);
                        if (tr != null) {
                            Setup.checkCreateColumn(con, c, camel, type, con.getSchema().getType(tr), errors);
                        } else {
                            Setup.checkCreateColumn(con, c, camel, type, errors);
                        }
                        errors.append(serviceNotificationDiv(paragraph("success", Message.get(con.getLocale(), "NEW_COLUMN_CREATED") + ":&nbsp;" + camel)));
                        Server.tableUpdated(con, "metadata:schema");
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.append(paragraph("error", Message.get(locale, "CANNOT_CREATE_COLUMN") + e.getMessage()));
                }
            }
            return false;
    }

    public boolean copyRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
        Document oldDoc = con.get(parms.get("EDIT_ID"));
        if (oldDoc != null) {
            MutableDocument newDoc = con.create(oldDoc.getTypeName());
            String copyName = null;
            Map<String,Object> fieldMap = oldDoc.toMap();
            fieldMap.remove("@rid");  // Otherwise will try to overwrite
            fieldMap.remove("_allow"); 
            fieldMap.remove("_allowRead");
            fieldMap.remove("_allowUpdate");
            fieldMap.remove("_allowDelete");
            newDoc.fromMap(fieldMap);
            if (newDoc.has("name")) {
                copyName = newDoc.getString("name");
                newDoc.set("name", 
                    newDoc.getString("name")
                    +Message.get(con.getLocale(),"COPY_SUFFIX")
                );
            }
            if (newDoc.has("description")) {
                if (copyName == null) copyName = newDoc.getString("description");
                newDoc.set("description", 
                    Message.get(con.getLocale(),"COPY_PREFIX",new Date().toString())
                    +"\n"+newDoc.getString("description")
                );
            }
            if (copyName == null) copyName = newDoc.getIdentity().toString();
            newDoc.save();
            parms.put("EDIT_ID", newDoc.getIdentity().toString().substring(1));  // In case we want to go straight to the new record's editor
            errors.append(serviceNotificationDiv(paragraph("success", Message.get(con.getLocale(), "ROW_COPIED", copyName))));
            return true;
        }
        return false;
    }

    public boolean insertRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
        MutableDocument newDoc = con.create(table);
        ArrayList<String> thumbsToUpdate = new ArrayList<String>();

        for (Property column : con.getColumns(table)) {
            Type type = column.getType();
            String name = column.getName();
            String value = parms.get(PARM_PREFIX + name);
            if (value != null && value.equals("null")) {
                value = null;
            }
            if (DEBUG) {
                System.out.println("InsertRow(JavaAPI): column " + name + " is a " + type + " and its value is " + value);
            }
            if (!isNullOrBlank(value)) {
                if (type == Type.BOOLEAN) {  // Boolean
                    newDoc.set(name, (value.equals("on") ? true : false));
                } else if (type == Type.BYTE || type == Type.SHORT || type == Type.INTEGER || type == Type.LONG) {   // Number (int,long, etc...)
                    try {
                        long longValue = Long.parseLong(value);
                        newDoc.set(name, longValue);
                    } catch (Exception e) {
                        errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_NUMBER_VALUE", value+": "+e.getMessage())));
                    }
                } else if (type == Type.FLOAT || type == Type.DOUBLE || type == Type.DECIMAL) {  // Float - Double - Decimal
                    try {
                        double dubValue = Double.parseDouble(value);
                        newDoc.set(name, dubValue);
                    } catch (Exception e) {
                        errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_NUMBER_VALUE", value+": "+e.getMessage())));
                    }
                } else if (type == Type.DATE) {  // Date
                    try {
                        Date date = parseDate(con.getLocale(),value);
                        newDoc.set(name, date);
                    } catch (Exception e) {
                        errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_DATE_VALUE", value+": "+e.getMessage())));
                    }
                } else if (type == Type.DATETIME) {  // Datetime
                    try {
                        value = value.replace('T', ' ');
                        if (value.length() == 16) value += ":00";
                        LocalDateTime ldt = parseLocalDatetime(con.getLocale(), value);
                        newDoc.set(name, ldt);
                    } catch (Exception e) {
                        errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_DATETIME_VALUE", value+": "+e.getMessage())));
                    }
                } else if (type == Type.STRING) {  // String
                    newDoc.set(name, value);
                } else if (type == Type.BINARY) {  // Binary/image 
                    updateBlob(con, newDoc, table, name, parms, errors);
                    thumbsToUpdate.add(name);
                } else if (type == Type.LINK) {  // Link
                    Document linkDoc = null;
                    if (value != null) {
                        linkDoc = con.get(value);
                        if (linkDoc == null) {
                            System.out.println("INSERT WARNING: attempted to insert with a link field with a document that couldn't be found - will not update this field with questionable data");
                        }
                    };
                    if (value == null || linkDoc != null) {
                        newDoc.set(name, linkDoc.getIdentity());
                    }
                } else if (type == Type.LIST) { // LinkList
                    String[] newValues = {};
                    if (value.startsWith(",")) value = value.substring(1);
                    if (value != null && !value.trim().equals("")) {
                        newValues = value.split(",");
                    }
                    // Add new list
                    List<RID> list = new ArrayList<>();
                    boolean okToUpdate = true;
                    for (String nv : newValues) {
                        Document doc = con.get(nv);
                        if (doc != null) {
                            list.add(doc.getIdentity());
                        } else {
                            okToUpdate = false;
                            System.out.println("INSERT WARNING: attempted to add a non-existent document to a list - list will remain unchanged");
                        }
                    }
                    if (okToUpdate) {
                        newDoc.set(name, list);
                    }
                } else if (type == Type.MAP) { // Map
                    String newMap = parms.get(PARM_PREFIX + name + "_map");
                    if (DEBUG) System.out.println("Inserting LinkMap");
                    String[] newValues = {};
                    if (value != null && !value.trim().equals("")) {
                        if (value.startsWith(",")) value = value.substring(1);
                        newValues = splitCSV(value);
                    }
                    String[] newMaps = {};
                    if (newMap != null && !newMap.trim().equals("")) {
                        newMaps = splitCSV(newMap);
                    }
                    // Create a new map
                    Map<String, RID> map = new HashMap<>();
                    boolean okToUpdate = true;
                    // Add all from new list
                    int mapIndex = 0;
                    for (String nv : newValues) {
                        try {
                            Document doc = con.get(nv);
                            if (doc != null) {
                                map.put(newMaps[mapIndex], doc.getIdentity());
                            } else {
                                okToUpdate = false;
                                System.out.println("UPDATE WARNING: attempted to add a non-existent document to a map - map will remain unchanged");
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            System.out.println("Could not understand map field:" + nv);
                        }
                        mapIndex++;
                    }
                    if (okToUpdate) {
                        newDoc.set(name, map);
                    }
                } else {
                    errors.append(paragraph("error", Message.get(con.getLocale(), "UNKNOWN_FIELD_TYPE", "" + type, name)));
                }
            }
        }
        if (newDoc.isDirty()) {
            try {
                MutableDocument createdDoc = newDoc.save();
                parms.put("EDIT_ID", createdDoc.getIdentity().toString().substring(1));  // In case we want to go straight to the new record's editor
                errors.append(paragraph("success", Message.get(con.getLocale(), "NEW_ROW_CREATED", (newDoc.isDirty() ? "false" : "true"))));
                for (String thumbCol : thumbsToUpdate) {
                    Thumbnail.createThumbnail(con, table, createdDoc, thumbCol);
                }
                Server.tableUpdated(con, table);
                DatabaseConnection.rowCountChanged(table);
                return true;
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                errors.append(paragraph("error", Message.get(con.getLocale(), "CANNOT_CREATE_ROW") + e.getMessage()) + (DEBUG ? "<br>" + xxSmall(sw.toString()) : ""));
     //           if (DEBUG || e instanceof OSecurityAccessException) {
     //               System.err.println(sw.toString());  // Security messages must go to log
     //           }
                return false;
            }
        } else {
            errors.append(paragraph("warning", Message.get(con.getLocale(), "NOTHING_TO_UPDATE")));
            return false;
        }
    }

    public boolean updateBlob(DatabaseConnection con, MutableDocument doc, String table, String blobName, HashMap<String, String> parms, StringBuilder errors) {
        if (doc != null) {
            String blob_temp_file = parms.get(PARM_PREFIX + blobName);
            String blob_file_name = parms.get(PARM_PREFIX + blobName + "_FILENAME");
            String blob_type = parms.get(PARM_PREFIX + blobName + "_TYPE");
            if (blob_temp_file != null && !blob_temp_file.trim().equals("")) {
                if (DEBUG) System.out.println("Writing blob " + blob_file_name + " type:" + blob_type + " file:" + blob_temp_file);
                try {
                    FileInputStream is = new FileInputStream(blob_temp_file);
                    byte[] byteArray = is.readAllBytes();
                    is.close();
                    if (byteArray.length > 0) {
                        doc.set(blobName, byteArray);
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        } else {
            System.out.println("Table.updateBlobs() - document is null");
        }
        return true;
    }

    public boolean updateRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
        String edit_id = parms.get("EDIT_ID");
        if (DEBUG) System.out.println("In updateRow (Java API) of table " + table + " for Document "+edit_id);
        if (edit_id == null || edit_id.isEmpty()) {
            errors.append(paragraph("error","In updateRow (Java API) of table " + table + " for Document "+edit_id+" edit_id is null"));
        }
        Document toUpdate = con.get(edit_id);
        MutableDocument updateRow = toUpdate.modify();
        if (updateRow != null) {
            Collection<Property> columns = con.getColumns(table);
            for (Property column : columns) {
                String columnName = column.getName();
                Type type = column.getType();
                String newValue = parms.get(PARM_PREFIX + columnName);
                if (DEBUG) System.out.println("updating " + columnName + " of type " + type + " with value " + newValue);
                if (newValue == null) {
                    continue;  // Don't update field if not specified in parameters
                }                
                if (newValue.equals("null")) {
                    newValue = null;
                }
                if (type == Type.BOOLEAN) { // Boolean
                    Boolean oldval = updateRow.getBoolean(columnName);
                    boolean newbool = false;
                    if (newValue != null) {  // boolean could have commas because of the hidden input to force the value into the form
                        if (newValue.contains("on")) {
                            newbool = true;
                        }
                        if (oldval == null || oldval != newbool) {
                            updateRow.set(columnName, newbool);
                        }
                    }
                } else if (type == Type.SHORT || type == Type.INTEGER || type == Type.LONG) { // Whole number
                    Number originalValue = updateRow.getLong(columnName);
                    Number newVal = null;
                    if (newValue != null) {
                        try {
                            newVal = Long.parseLong(newValue);
                        } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_NUMBER_VALUE", newValue) + " " + e.getMessage()));
                        }
                    }
                    if ((newValue != null && originalValue != null && newVal != null && !newVal.equals(originalValue))
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                        updateRow.set(columnName, newVal);
                    }
                } else if (type == Type.FLOAT || type == Type.DOUBLE) { // float, double
                    Double originalValue = updateRow.getDouble(columnName);
                    Double newVal = null;
                    if (newValue != null) {
                        try {
                            newVal = Double.parseDouble(newValue);
                        } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_READ_NUMBER_VALUE", newValue) + " " + e.getMessage()));
                        }
                    }
                    if ((newValue != null && originalValue != null && newVal != null && !newVal.equals(originalValue))
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                        updateRow.set(columnName, newVal);
                    }
                } else if (type == Type.DECIMAL) { // decimal
                    BigDecimal originalValue = updateRow.getDecimal(columnName);
                    BigDecimal newVal = null;
                    if (newValue != null) {
                        try {
                            newVal = new BigDecimal(newValue);
                        } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_READ_NUMBER_VALUE", newValue) + " " + e.getMessage()));
                        }
                    }
                    if ((newValue != null && originalValue != null && newVal != null && !newVal.equals(originalValue))
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                        updateRow.set(columnName, newVal);
                    }
                } else if (type == Type.DATE) {  // Date
                    Date originalValue = updateRow.getDate(columnName);
                    try {
                        Date newDate = parseDate(con.getLocale(), newValue);
                        if (DEBUG) System.out.println("Updating Date " + (originalValue == null ? "" : originalValue.toString()) + " to " + newDate);
                        if ((newValue != null && originalValue != null && !newDate.equals(originalValue))
                                || (newValue == null && originalValue != null)
                                || (originalValue == null && newValue != null)) {
                            updateRow.set(columnName, newDate);
                        }
                    } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_DATE_VALUE", newValue+": "+e.getMessage())));
                    }
                } else if (type == Type.DATETIME) {  // Datetime
                    LocalDateTime originalValue = updateRow.getLocalDateTime(columnName);
                    try {
                        newValue = newValue.replace('T',' ');
                        if (newValue.length() == 16) newValue += ":00";
                        LocalDateTime newDate = parseLocalDatetime(con.getLocale(), newValue);
                        if (DEBUG) System.out.println("Updating Datetime " + (originalValue == null ? "" : originalValue.toString()) + " to " + newDate);
                        if ((newValue != null && originalValue != null && !newDate.equals(originalValue))
                                || (newValue == null && originalValue != null)
                                || (originalValue == null && newValue != null)) {
                            updateRow.set(columnName, newDate);
                        }
                    } catch (Exception e) {
                            errors.append(paragraph("error", Message.get(con.getLocale(), "INVALID_DATETIME_VALUE", newValue + " Format:"+ DATE_FORMAT+" "+TIME_FORMAT + " Message: "+e.getMessage())));
                    }
                } else if (type == Type.STRING) { // String
                    String originalValue = updateRow.getString(columnName);
                    if ((newValue != null && originalValue != null && !newValue.equals(originalValue))
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                        if (columnName.toUpperCase().endsWith("PASSWORD") && (newValue == null || newValue.equals(""))) {
                            System.out.println("Not updating null password requested by user " + con.getUser());
                        } else {
                            updateRow.set(columnName, newValue);
                        }
                    }
                } else if (type == Type.BINARY) {  // Blob
                    String blob_name = parms.get(PARM_PREFIX + columnName);
                    if (blob_name != null && !blob_name.trim().equals("")) {
                        if (DEBUG) {
                            System.out.println("Updating BLOB");
                        }
                        updateBlob(con, updateRow, table, columnName, parms, errors);
                        Thumbnail.createThumbnail(con, table, updateRow, columnName);

                    }
                } else if (type == Type.EMBEDDED) { // Embedded types - treat like a string (without quotes) - user beware
                    Object originalValue = updateRow.get(columnName);
                    if ((newValue != null && originalValue != null && !newValue.equals(originalValue)) // They will always detect a change because the string is formatted differently
                            || (newValue == null && originalValue != null) // This could be considered a bug
                            || (originalValue == null && newValue != null)) {
                        if (DEBUG) {
                            System.out.println("Embedded value changed");
                        }
                        updateRow.set(columnName, newValue);
                    }

                 } else if (type == Type.LINK) { // Link
                    RID rid = (RID)updateRow.get(columnName);
                    Document o = rid==null ? null : con.get(rid);
                    if (DEBUG) System.out.println("Updating Link " + (o == null ? "" : o.getIdentity().toString()));
                    String originalValue = (o == null ? null : o.getIdentity().toString().substring(1));
                    if ((newValue != null && originalValue != null && !newValue.equals(originalValue))
                            || (newValue == null && originalValue != null)
                            || (originalValue == null && newValue != null)) {
                        Document linkDoc = null;
                        if (newValue != null) {
                            linkDoc = con.get(newValue);
                            if (linkDoc == null) {
                                System.out.println("UPDATE WARNING: attempted to update a link field with a document that couldn't be found - will not update this field with questionable data");
                            }
                        };
                        if (newValue == null || linkDoc != null) {
                            updateRow.set(columnName, linkDoc);
                        }
                    }
                } else if (type == Type.LIST) { // LinkList (Ordered and can contain duplicates)
                    List<RID> o = updateRow.getList(columnName);
                    if (DEBUG) System.out.println("Updating LinkList " + (o == null ? "" : o));
                    String[] newValues = {};
                    if (newValue.startsWith(",")) newValue = newValue.substring(1);
                    if (newValue != null && !newValue.isBlank()) {
                        newValues = newValue.split(",");
                    }
                    // Remove all from original list as this list is ordered
                    if (o == null) {
                        o = new ArrayList<>();
                    } else {
                        o.clear();
                    }
                    // Add new list
                    boolean okToUpdate = true;
                    for (String nv : newValues) {
                        Document doc = con.get(nv);
                        if (doc != null) {
                            o.add(doc.getIdentity());
                        } else {
                            okToUpdate = false;
                            System.out.println("UPDATE WARNING: attempted to add a non-existent document to a list - list will remain unchanged");
                        }
                    }
                    if (okToUpdate) {
                        updateRow.set(columnName, o);
                    }
                } else if (type == Type.MAP) { // LinkMap
                    Map<String, Object> o = updateRow.getMap(columnName);
                    String newMap = parms.get(PARM_PREFIX + columnName + "_map");
                    if (DEBUG) System.out.println("Updating LinkMap " + (o == null ? "" : o));
                    String[] newValues = {};
                    if (newValue != null && !newValue.trim().equals("")) {
                        if (newValue.startsWith(",")) newValue = newValue.substring(1);
                        newValues = splitCSV(newValue);
                    }
                    String[] newMaps = {};
                    if (newMap != null && !newMap.trim().equals("")) {
                        newMaps = splitCSV(newMap);
                    }
                    // Remove all from original map as this list field is ordered
                    if (o == null) {
                        o = new HashMap<String,Object>();
                    } else {
                        o.clear();
                    }
                    boolean okToUpdate = true;
                    // Add all from new list
                    int mapIndex = 0;
                    for (String nv : newValues) {
                        try {
                            Document doc = con.get(nv);
                            if (doc != null) {
                                o.put(newMaps[mapIndex], doc);
                            } else {
                                okToUpdate = false;
                                System.out.println("UPDATE WARNING: attempted to add a non-existent document to a map - map will remain unchanged");
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            System.out.println("Could not understand map field:" + nv);
                        }
                        mapIndex++;
                    }
                    if (okToUpdate) {
                        updateRow.set(columnName, o);
                    }
                }
            }
            if (updateRow.isDirty()) {
                try {
                    updateRow.save();
                    errors.append(serviceNotificationDiv(paragraph("success", Message.get(con.getLocale(), "ROW_UPDATED", (updateRow.isDirty() ? "false" : "true")))));
                    Server.tableUpdated(con, table);
                    return true;
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    errors.append(paragraph("error", Message.get(con.getLocale(), "CANNOT_UPDATE") + e.getMessage()) + (DEBUG ? "<br>" + xxSmall(sw.toString()) : ""));
                     return false;
                }
            } else {
                errors.append(paragraph("warning", Message.get(con.getLocale(), "NOTHING_TO_UPDATE")));
                return false;
            }
        } else {
            if (DEBUG) {
                System.out.println("Error in permeagility.web.Table:updateRow: Could not find row " + parms.get("EDIT_ID"));
            }
            errors.append(paragraph("error", Message.get(con.getLocale(), "NOTHING_TO_UPDATE") + " " + parms.get("EDIT_ID") + " not found"));
            return false;
        }
    }

    public boolean deleteRow(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
        String edit_id = parms.get("EDIT_ID");
        if (edit_id == null || edit_id.isEmpty()) {
            errors.append(paragraph("error", Message.get(con.getLocale(), "ROW_CANNOT_BE_DELETED") + " record not found"));
            System.err.println("Table.deleteRow: Really?? Trying to delete a null or blank edit_id, not gonna happen");
            return false;
        }
        Document doc = con.get(edit_id);
        if (doc != null) {
            try {
                String delName = doc.has("name") ? doc.getString("name") : doc.getIdentity().toString();                
                doc.delete();
                Server.tableUpdated(con, table);
                DatabaseConnection.rowCountChanged(table);
                Thumbnail.deleteThumbnail(con, table, edit_id );
                parms.remove("EDIT_ID");
                errors.append(serviceNotificationDiv(paragraph("success", Message.get(con.getLocale(), "ROW_DELETED", delName))));
                return true;
            } catch (Exception e) {
                errors.append(paragraph("error", Message.get(con.getLocale(), "ROW_CANNOT_BE_DELETED") + e.getMessage()));
                return false;
            }
        } else {
            errors.append(paragraph("error", Message.get(con.getLocale(), "ROW_CANNOT_BE_DELETED") + " record not found"));
            return false;
        }
    }

    public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        String edit_id = parms.get("EDIT_ID");

        if (table == null) {
            return paragraph("error", "null table passed to table row form");
        }

        // Cannot view abstract class directly - redirect to the actual record's class
        DocumentType tclass = con.getSchema().getType(table);
        if (tclass == null) {
            return paragraph("error", "cannot find class " + table);
        }

        String allRowsLink = "";
        if (!con.getUser().equals("guest")) {
            allRowsLink = linkHTMX("/"+this.getClass().getName() + "/" + table, "&lt;"+Message.get(con.getLocale(), "ALL_ROWS_IN_TABLE", makeCamelCasePretty(table)), parms.get("HX-TARGET"));
        }

        // getTableRowFields can return a list of hyperscript command to run when a form is submitted (for CodeEditor mostly)
        ArrayList<String> submitCodeLines = new ArrayList<String>();
        String rowForm = getTableRowFields(con, table, parms, submitCodeLines);
        StringBuilder submitCode = new StringBuilder("_=\"on click "); 
        int lineIndex = 0;
        for (String codeLine : submitCodeLines) {
            if (lineIndex > 0) submitCode.append(" then ");
            submitCode.append(codeLine);
            lineIndex++;
        }
        if (DEBUG) {
            submitCode.append(" then log 'button:clicked'\"");
        } else {
            submitCode.append("\"");
        }

        boolean readOnly = false;  //edit_id == null ? false : Security.isReadOnlyDocument(con, con.get(edit_id));
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
        String formContent = rowForm
                        + center((edit_id == null
                            ? ((Security.getTablePriv(con, table) & Security.PRIV_CREATE) > 0 
                                    ? submitButton(con.getLocale(), "CREATE_ROW", submitCodeLines.size()>0 ? submitCode.toString() : "") 
                                    : "")
                            : ((Security.getTablePriv(con, table) & Security.PRIV_UPDATE) > 0 && !readOnly 
                                    ? submitButton(con.getLocale(), "UPDATE", submitCodeLines.size()>0 ? submitCode.toString() : "") 
                                    : "")
                            + "&nbsp;&nbsp;"
                            + cancelButton(con.getLocale(), table, parms.get("HX-TARGET"))))
                        + paragraph("delete",
                                (edit_id != null && (Security.getTablePriv(con, table) & Security.PRIV_CREATE) > 0 ? submitButton(con.getLocale(), "COPY") : "") + "&nbsp;&nbsp;"
                                + (edit_id != null && (Security.getTablePriv(con, table) & Security.PRIV_DELETE) > 0 && !readOnly ? deleteButton(con.getLocale(),table,edit_id, parms.get("HX-TARGET")) : ""));
        if (edit_id == null) {  // PUT
            formContent = formHTMX(formName, "/"+this.getClass().getName()+"/"+table, "put", parms.get("HX-TARGET"), formContent);
        } else {                // PATCH
            formContent = formHTMX(formName, "/"+this.getClass().getName()+"/"+table+"/"+edit_id, "patch", parms.get("HX-TARGET"), formContent);
        }
       String docDesc = edit_id == null ? "New" : getDescriptionFromDocument(con, con.get(edit_id));
        String title = Message.get(con.getLocale(), "EDIT_ROW", makeCamelCasePretty(table), docDesc);
        return allRowsLink
                + getLinkTrail(con, parms.get("SOURCETABLENAME"), parms.get("SOURCEEDIT_ID"), parms.get("HX-TARGET"))
                + paragraph("banner", (edit_id == null 
                        ? Message.get(con.getLocale(), "CREATE_ROW")+" "+makeCamelCasePretty(table)
                        : Message.get(con.getLocale(), "UPDATE") + "&nbsp;" + makeCamelCasePretty(table)))
                + formContent
                + getTableRowRelated(con, table, parms)
                + serviceHeaderUpdateDiv(parms, title);
    }

    private String getLinkTrail(DatabaseConnection con, String tables, String ids, String target) {
        if (tables == null || tables.equals("")) {
            return "";
        }
        if (ids == null || ids.equals("")) {
            return "";
        }
        String tabs[] = tables.split(",");
        String tabIds[] = ids.split(",");
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < tabs.length; i++) {
            String t = tabs[i];
            String id = tabIds[i];
            ret.append("<br>&nbsp;&nbsp;&nbsp;" + linkHTMX("/"+this.getClass().getName() + "/" + t + "/" + id, makeCamelCasePretty(t) + " (" + getDescriptionFromTable(con, t, id) + ")", target));
        }
        return ret.toString();
    }

 //   public String getTableRowFields(DatabaseConnection con, String table) {
 //       return getTableRowFields(con, table, null, null, null);
 //   }

    public String getTableRowFieldsNew(DatabaseConnection con, String table, HashMap<String, String> parms) {
        // must make copy of parms and remove EDIT_ID, still want parms for in progress data values and FORCE_ columns
        HashMap<String,String> newParms = parms;
        if (parms.containsKey("EDIT_ID")) {
            newParms = new HashMap<String,String>();
            newParms.putAll(parms);
            newParms.remove("EDIT_ID");
        }
        return getTableRowFields(con, table, newParms, null, null);
    }
    
    // Used
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms, null, null);
    }
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, ArrayList<String> submitCodeLines) {
        return getTableRowFields(con, table, parms, null, submitCodeLines);
    }
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride) {
        return getTableRowFields(con, table, parms, columnOverride, null);
    }
    /**
     * Returns the fields for a table - can be for insert of a new row or update of an existing (as specified by the EDIT_ID in parms)
     */
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride, ArrayList<String> submitCodeLines) {
        String edit_id = (parms != null ? parms.get("EDIT_ID") : null);
        Document initialValues = edit_id == null ? null : con.get(edit_id);

        StringBuilder fields = new StringBuilder();
        StringBuilder hidden = new StringBuilder();
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
        Collection<Property> columns = con.getColumns(table, columnOverride);
        if (columns != null) {
            for (Property column : columns) {
                String name = column.getName();
                //if (column.getLinkedClass() != null && (Security.getTablePriv(con, column.getLinkedClass().getName()) & PRIV_READ) == 0) {
                //        continue;
                //}
                if (name.startsWith("button_") && name.length() > 8 && name.indexOf('_', 7) > 7) {
                     if (initialValues != null) {
                        int cp = name.indexOf('_', 7);
                        String n = name.substring(7, cp);
                        String l = name.substring(cp + 1);
                        fields.append(column("")+column(button(n, edit_id, l)));
                     }
                } else {
                    if (parms != null && parms.get("FORCE_" + name) != null) {
                        if (DEBUG) System.out.println("Adding hidden parameter via FORCE_"+name+" with value "+parms.get("FORCE_" + name));
                        hidden.append(hidden(PARM_PREFIX + name, parms.get("FORCE_" + name)));
                        continue;
                    }
                    fields.append(getColumnAsField(table, column, initialValues, con, formName, edit_id, parms, submitCodeLines));
                }
            }
            return hidden.toString() + center(table("data", fields.toString()));
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
     * @return a table row for a given column in the document
     */
    public String getColumnAsField(String table, Property column, Document initialValues
                                , DatabaseConnection con, String formName, String edit_id
                                , HashMap<String, String> parms, ArrayList<String> submitCodeLines) {
        Type type = column.getType();
        String name = column.getName();
        String prettyName = makeCamelCasePretty(name);
        String trName = Message.get(con.getLocale(), "COLUMN_" + table + "." + name);
        if (!trName.equals("COLUMN_" + table + "." + name)) {
            prettyName = trName;
        }
        String label = column("label", prettyName);
        if (DEBUG) System.out.println("Table.getColumnAsField() " + name + " is a " + type + " of "+ column.getOfType());

        Object initialValue;
        try {
            initialValue = initialValues.get(name);
        } catch (Exception e) {
            initialValue = null;
        }
        if (DEBUG) {
            System.out.println(name + " InitialValue=" + (type != Type.BINARY ? initialValue : "binary"));
        }
        if (initialValue == null && edit_id != null && parms != null) {
            initialValue = parms.get(PARM_PREFIX + name);  // Need to load parms with values
        }

        if (type == Type.BOOLEAN) {
            return row(label + column(checkbox(PARM_PREFIX + name, (initialValue == null ? false : Boolean.valueOf(initialValue.toString())))));

        // Number
        } else if (type == Type.DECIMAL || type == Type.INTEGER || type == Type.LONG 
                || type == Type.FLOAT || type == Type.DOUBLE 
                || type == Type.SHORT || type == Type.BYTE) {
            List<String> pickValues = Server.getPickValues(con, table, name);
            if (pickValues != null) {
                return row(label + column(createList(con.getLocale(), PARM_PREFIX + name, initialValue != null ? initialValue.toString() : null, pickValues, null, false, null, true)));
            }
            return row(label + column(input("number", PARM_PREFIX + name, initialValue)));
        // Date
        } else if (type == Type.DATE) {
            if (DEBUG && initialValue != null) System.out.println("Date field is of type: "+initialValue.getClass().getName());
            return row(label + column("<input type=\"date\" name=\"" + PARM_PREFIX + name + "\" value=\""
                        +(initialValue != null ? formatDate(con.getLocale(), initialValues.getDate(name)) : "")+"\" />"
                    ));
          // Datetime
        } else if (type == Type.DATETIME) {
            if (DEBUG && initialValue != null) System.out.println("Datetime field is of type: "+initialValue.getClass().getName());
            return row(label + column("<input type=\"datetime-local\" step=1 name=\"" + PARM_PREFIX + name + "\" value=\""
                            +(initialValue != null ? formatDatetime(con.getLocale(), initialValues.getLocalDateTime(name)) : "")+"\" />"
                    ));
        // Password (String)
        } else if (type == Type.STRING && name.toUpperCase().endsWith("PASSWORD")) {
            return row(label + column(password(PARM_PREFIX + name, null, 15)));
        // Colour (String)
        } else if (type == Type.STRING && (name.toUpperCase().endsWith("COLOR") || name.toUpperCase().endsWith("COLOUR"))) {
            if (DEBUG) System.out.println("Doing color field " + initialValues);
            return row(label + column("<input type=\"color\" name=\"" + PARM_PREFIX + name + "\" value=\""
                        +(initialValue != null ? initialValues.getString(name) : "")+"\" />"
                    ));
            // SQL (String)
        } else if (type == Type.STRING && name.toUpperCase().endsWith("SQL")) {
            if (DEBUG) System.out.println("Doing SQL Editor field " + name);
            return row(label + column(getCodeEditorControl(formName, PARM_PREFIX + name, (String) initialValue, "text/x-sql", submitCodeLines)));
            // JSON (String)
        } else if (type == Type.STRING && name.toUpperCase().endsWith("JSON")) {
            if (DEBUG) System.out.println("Doing JSON Editor field " + name);
            return row(label + column(getCodeEditorControl(formName, PARM_PREFIX + name, (String) initialValue, "application/json", submitCodeLines)));
            // Script-R (String)
        } else if (type == Type.STRING && name.toUpperCase().endsWith("RSCRIPT")) {
            if (DEBUG) System.out.println("Doing R Code Editor field " + name);
            return row(label + column(getCodeEditorControl(formName, PARM_PREFIX + name, (String) initialValue, "text/x-rsrc", submitCodeLines)));
            // Script-HTML (String)
        } else if (type == Type.STRING && name.toUpperCase().endsWith("SCRIPT") || name.toUpperCase().endsWith("HTML")) {
            if (DEBUG) System.out.println("Doing HTML Code Editor field " + name);
            return row(label + column(getCodeEditorControl(formName, PARM_PREFIX + name, (String) initialValue, "htmlmixed", submitCodeLines)));
            // Script-Javascript (String)
        } else if (type == Type.STRING && (name.toUpperCase().endsWith("JAVASCRIPT") || name.toUpperCase().endsWith("CODE"))) {
            if (DEBUG) System.out.println("Doing JavaScript Code Editor field " + name);
            return row(label + column(getCodeEditorControl(formName, PARM_PREFIX + name, (String) initialValue, "text/javascript", submitCodeLines)));
            // Style-CSS (String)
        } else if (type == Type.STRING && (name.toUpperCase().endsWith("STYLE"))) {
            if (DEBUG) System.out.println("Doing CSS Code Editor field " + initialValues);
            return row(label + column(getCodeEditorControl(formName, PARM_PREFIX + name, (String) initialValue, "css", submitCodeLines)));
            // String
        } else if (type == Type.STRING) {
            List<String> pickValues = Server.getPickValues(con, table, name);
            if (pickValues != null) {
                return row(label + column(createList(con.getLocale(), PARM_PREFIX + name, initialValue != null ? initialValue.toString() : null, pickValues, null, false, null, true)));
            }
            if (ALWAYS_TEXT_AREA || initialValue != null && ((String) initialValue).length() > TEXT_AREA_THRESHOLD || name.equals("description")) {
                int length = initialValue != null ? ((String) initialValue).length() : 20;
                int linecount = (initialValue != null ? countLines((String) initialValue, TEXT_AREA_WIDTH) : 1);
                int width = length > TEXT_AREA_THRESHOLD ? TEXT_AREA_WIDTH : TEXT_AREA_THRESHOLD;
                return row(label + column(textArea(PARM_PREFIX + name, initialValue, (linecount > 1 || length > TEXT_AREA_THRESHOLD ? linecount + 2 : linecount), width)));
            } else {
                int length = 20;
                if (initialValue != null && initialValue.toString().length() > 20) {
                    length = initialValue.toString().length() + 5;
                }
                return row(label + column(input("text", PARM_PREFIX + name, initialValue, length)));
            }
            // Binary
        } else if (type == Type.BINARY) { // 8 = binary, 20 = custom 
            StringBuilder desc = new StringBuilder();
            if (edit_id != null) {
                String nail = null;
                String blobid = Thumbnail.getThumbnailId(con, table, edit_id, name, desc);
                if (blobid != null) {
                    nail = Thumbnail.getThumbnailLink(con.getLocale(), blobid, desc.toString());
                } else {
                    nail = "<div title=\"" + Message.get(con.getLocale(), "THUMBNAIL_NOT_FOUND", name, edit_id + "\">" + Message.get(con.getLocale(), "OPTION_NONE") + "</div>");
                }
                return row(label + column(nail + fileInput(PARM_PREFIX + name)));
            } else {
                return row(label + column(fileInput(PARM_PREFIX + name)));
            }
        // Embedded
        } else if (type == Type.EMBEDDED) {
            EmbeddedDocument ed = initialValues == null ? null : initialValues.getEmbedded(name);
            String val = ed == null ? "" : ed.toJSON().toString();
            return row(label + column(textArea(PARM_PREFIX + name, val, 5, TEXT_AREA_WIDTH)));
        // Single link
        } else if (type == Type.LINK) {
            String ofType = column.getOfType();
            if (ofType == null) System.out.println("Table.getColumnAsField LINK type is null for "+name);
            if (DEBUG) System.out.println("Table.getColumnAsField found LINK type");
            String v = null;
            if (initialValue != null) {
                if (initialValue instanceof RID) {
                    v = initialValue.toString();
                } else if (initialValue instanceof Document) {
                    v = ((Document)initialValue).getIdentity().toString();
                } else {
                    System.out.println("Table.getColumnAsField Found "+initialValue.getClass().getName()+" for a link - ignoring it");
                }
                if (v != null && v.startsWith("#")) v = v.substring(1);
            }
            String gotoLink = "";
            if (initialValues != null || initialValue != null) {
                gotoLink = linkHTMX("/"+this.getClass().getName() + "/" + ofType + "/" + v, Message.get(con.getLocale(), "GOTO_ROW"), parms.get("HX-TARGET"));
            }
            if (ofType != null) {
                return row(label 
                        + column(createListFromCache(PARM_PREFIX + name, (v == null ? "" : v), con, getQueryForTable(con,ofType,name), null, true, null, true)
                        + gotoLink          
                        ));
            } else {
                return paragraph("error","table.getColumnAsField: Could not determine linked type for "+name);
            }
        // Link list
        } else if (type == Type.LIST) {
            List<RID> list = null;
            try {
                list = initialValues == null ? null : initialValues.getList(name);
            } catch (NullPointerException e) {
                e.printStackTrace();
            } // It will do this if it doesn't exist
            String linkedType = column.getOfType();
            if (linkedType == null && name.startsWith("_allow")) {
                    if (DEBUG) System.out.println("Assuming a link type of identity for "+name);
                    linkedType = "identity";
            }
            if (linkedType == null && list != null && list.size() > 0) {
                System.out.println("table.getColumnAsField: Will deduce LINK OfType from contents");
                Document d = con.get(list.get(0));
                if (d != null) {
                    linkedType = d.getTypeName();
                    System.out.println("table.getColumnAsField: Deduced that LINK is OfType "+linkedType);
                }
            }
            if (linkedType != null) {
                return row(label + columnNoWrap(linkListControl(con, PARM_PREFIX + name, linkedType, getCache().getResult(con, getQueryForTable(con, linkedType, name)), con.getLocale(), list)));
            } else {
                return paragraph("error","table.getColumnAsField: Could not determine linked LIST type for "+name);
            }
           // Link map
        } else if (type == Type.MAP) { 
            Map<String, Object> l = null;
            try {
                l = initialValues.getMap(name);
            } catch (NullPointerException e) {
            }  // It will do this if it doesn't exist
            if (l != null && DEBUG) System.out.println("linkmap size=" + l.size());
            
            String linkedType = column.getOfType();
            if (linkedType != null) {
                return row(label + columnNoWrap(linkMapControl(con, PARM_PREFIX + name, linkedType, getCache().getResult(con, getQueryForTable(con, linkedType, name)), con.getLocale(), l)));
            } else {
                return paragraph("error","table.getColumnAsField: Could not determine linked MAP type for "+name);
            }
        } else {
            System.out.println("Table.GetColumnAsField: Unrecognized type: " + type);
            return row(label + column(input("other", PARM_PREFIX + name, initialValue)));
        }
    }

    public String getTableRowRelated(DatabaseConnection con, String table, HashMap<String, String> parms) {
        Stack<String> tables = new Stack<>();
        Stack<String> columns = new Stack<>();
        Stack<Type> types = new Stack<>();

        String edit_id = parms.get("EDIT_ID");

        for (DocumentType c : con.getSchema().getTypes()) {
            for (Property p : c.getProperties()) {
                if (p.getOfType() != null && p.getOfType().equals(table)) {
                    if (SHOW_ALL_RELATED_TABLES || (Security.getTablePriv(con, c.getName()) & Security.PRIV_READ) > 0) {
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
            String lastId = ids[ids.length - 1];
            if (!edit_id.equals(lastId)) {
                newSourceTable = sourceTable + "," + table;
                newSourceId = sourceId + "," + edit_id;
            }
        } else {
            newSourceTable = table;
            newSourceId = edit_id;
        }
        parms.put("SOURCEEDIT_ID", (newSourceId != null ? newSourceId : edit_id));
        parms.put("SOURCETABLENAME", (newSourceTable != null ? newSourceTable : table));

        if (DEBUG) System.out.println("getTableRowRelated " + tables.size());

        ArrayList<String> tabNames = new ArrayList<String>();
        ArrayList<String> tabTargets = new ArrayList<String>();
        
        while (!tables.isEmpty()) {
            String relTable = tables.pop();
            String fkColumn = columns.pop();
            Type fkType = types.pop();
            if (DEBUG) System.out.println("Table.getTableRowRelated: fkType="+fkType+" fkColumn="+fkColumn);
            int priv = Security.getTablePriv(con, table);
            //System.out.println("Privilege on table "+table+" for user "+con.getUser()+" = "+priv);

            tabNames.add(makeCamelCasePretty(relTable));
            tabTargets.add("/Table/"+relTable+"/*/where/"+fkColumn+"/eq/"+edit_id+"?FORCE_"+fkColumn+"="+edit_id);
        }
        return getTabPanel("reltables", tabNames, tabTargets);
    }

    public String newColumnPopup(DatabaseConnection con, String table, String target) {
        return popupFormHTMX("NEWCOLUMN_"+table, this.getClass().getName()+"/"+table+"/columns", 
             "put", target, Message.get(con.getLocale(), "ADD_COLUMN"), "NEWCOLUMNNAME", newColumnForm(con));
    }

    public String newColumnForm(DatabaseConnection con) {
        Locale l = con.getLocale();  
        String typeSelAttr = """
            _="on load hide #NEWTABLEREF end 
               on change if #NEWDATATYPE.value is 'DATATYPE_LINK' or #NEWDATATYPE.value is 'DATATYPE_LIST' or #NEWDATATYPE.value is 'DATATYPE_MAP'
                  show #NEWTABLEREF
                  else hide #NEWTABLEREF"
            """;   // This script will show the table picklist only if datatype is link, list or map
        String tableSelAttr = null;
        return paragraph("banner", Message.get(l, "NEW_COLUMN"))
                + getDatatypeList(l, "NEWDATATYPE", "DATATYPE_TEXT", typeSelAttr)
                + createListFromDocumentTypes("NEWTABLEREF", null, con, tableSelAttr, false, null, true) + br()
                + inputWithPlaceholder("NEWCOLUMNNAME", "Column name") + br()
                + center(submitButton(l, "NEW_COLUMN")+POPUP_FORM_CLOSER);
    }

    public String createListFromDocumentTypes(String name, String initial, DatabaseConnection con, String attributes, boolean allowNull, String classname, boolean enabled) {
        Collection<? extends DocumentType> types = con.getSchema().getTypes();
        ArrayList<String> names = new ArrayList<String>();
        for (DocumentType t : types) {
           names.add(t.getName());
        }
        return createList(con.getLocale(), name, initial, names, attributes, allowNull, classname, enabled);
    }

    public static String getDatatypeList(Locale l, String name, String selected, String options) {
        if (dataTypeNames.get(l) == null) {
            setUpDataTypes(l);
        }
        return selectList(l, name, selected, dataTypeNames.get(l), dataTypeValues.get(l), options, false, null, true);
    }

    /**
     * Get the EDIT_ID from the parms, get the document and populate the parms with the document's field data
     */
    public HashMap<String, String> getTableRowParameters(DatabaseConnection con, String schema, String table, HashMap<String, String> parms) {
        if (DEBUG) {
            System.out.println("getTableRowParameters: Getting row and injecting into parameters");
        }
        String edit_id = parms.get("EDIT_ID");
        if (edit_id != null) {
            QueryResult rows = con.query("SELECT FROM #" + edit_id);
            if (rows != null && rows.size() == 1) {
                String[] keys = rows.getColumns();
                for (int i = 0; i < keys.length; i++) {
                    String value = rows.getStringValue(0, keys[i]);
                    parms.put(keys[i], value);
                    if (DEBUG) {
                        System.out.println("Injected " + keys[i] + "=" + value);
                    }
                }
            } else {
                if (DEBUG) {
                    System.out.println("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing rows=" + rows.size());
                }
            }
        } else {
            System.out.println("getTableRowParameters: EDIT_ID not specified");
        }
        return parms;
    }

    String getTableWithControls(DatabaseConnection con, HashMap<String,String> parms, String table) {
        String pagest = parms.get("PAGE");
        long page = 0;
        if (pagest != null) {
            try { page = Integer.parseInt(pagest); } catch (Exception e) { }  // If it isn't a number, ignore it
        }
        String body = linkHTMX(this.getClass().getName(), "&lt;" + Message.get(con.getLocale(), "ALL_TABLES"), parms.get("HX-TARGET"))
                + "&nbsp;&nbsp;&nbsp;"
                 + ((Security.getTablePriv(con, table) & Security.PRIV_CREATE) > 0 
                    ? popupFormHTMX("CREATE_NEW_ROW", this.getClass().getName()+"/"+table, "put", parms.get("HX-TARGET"), Message.get(con.getLocale(), "NEW_ROW"), "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "CREATE_ROW")+" "+makeCamelCasePretty(table))
                        + getTableRowFieldsNew(con, table, parms)
                        + submitButton(con.getLocale(), "CREATE_ROW")
                        + POPUP_FORM_CLOSER) 
                    : "")
                + "&nbsp;&nbsp;&nbsp;"
                + (Security.isDBA(con)
                    ? newColumnPopup(con, table, parms.get("HX-TARGET"))
                    + "&nbsp;&nbsp;&nbsp;"
//                    + popupFormHTMX("RIGHTSOPTIONS", this.getClass().getName()+"/"+table, "post", parms.get("HX-TARGET")
                    + popupFormHTMX("RIGHTSOPTIONS", this.getClass().getName()+"/"+table, "post", "RIGHTSOPTIONS"
                                , Message.get(con.getLocale(), "TABLE_RIGHTS_OPTIONS"), "XXX"
                                , rightsOptionsForm(con, table, parms, ""))
                    + "&nbsp;&nbsp;&nbsp;"
                    + popupFormHTMX("ADVANCEDOPTIONS", this.getClass().getName()+"/"+table, "post", parms.get("HX-TARGET")
                                 , Message.get(con.getLocale(), "ADVANCED_TABLE_OPTIONS"), "XXX"
                                 , advancedOptionsForm(con, table, parms, "") + POPUP_FORM_CLOSER)
                    : "") // isDBA switch
                + br()
                + getTable(con, table, parms, page);

        String title = Message.get(con.getLocale(),"VIEW_TABLE",makeCamelCasePretty(table));
        return body + serviceHeaderUpdateDiv(parms, title);
    }

    public String getTable(DatabaseConnection con, String table, HashMap<String,String> parms, long page) {
        // if rest attributes exist then parse WHERE clause
        String where = "";
        String hideColumn = null;
        String restOfURL = parms.get("REST_OF_URL");  
        if (restOfURL != null && !restOfURL.isEmpty()) {
            String[] restParts = restOfURL.split("/"); 
            String prefix = " WHERE ";
            if (restParts.length > 2 && restParts[0].equals(table) && restParts[1].equals("*") 
                 && restParts[2].equalsIgnoreCase("WHERE")) {
                    if (DEBUG) System.out.println("We have a where clause coming");
                    int restIndex = 2; // after 0table/1splat/2where/3column/4operator/5value/6and/7col/8eq/9val
                    while (restIndex + 3 < restParts.length) {  // if there are three more parts to read
                        String column = restParts[restIndex+1];
                        if (parms.get("FORCE_"+column) != null) hideColumn = column; // If column is forced (as a subtable, hide it)
                        String operator = restParts[restIndex+2];
                        if (operator.equalsIgnoreCase("eq")) operator = "=";
                        String value = restParts[restIndex+3];
                        if (!value.startsWith("'") && value.contains(":")) {
                            value = "#"+value;  // no quotes with colon must be RID
                        }
                        where += prefix+column+" "+operator+" "+value;
                        
                        restIndex += 3;
                        if (restIndex + 1 < restParts.length) {  // Are we going to continue?
                            if (restParts[restIndex+1].equalsIgnoreCase("AND")
                              || restParts[restIndex+1].equalsIgnoreCase("OR")) {
                                prefix = " "+restParts[restIndex+1]+" ";
                                restIndex++;
                            }
                        }
                    }
            }
        }
        String query = "SELECT FROM " + table + where;
        if (!where.isBlank() && DEBUG) System.out.println("Table.getTable with REST where query="+query);

        return getTable(con, parms, table, query, hideColumn, page);
    }


    public String getTable(DatabaseConnection con, HashMap<String, String> parms, String table, String query, String hideColumn, long page) {
        return getTable(con, parms, table, query, hideColumn, page, null);
    }

    /**
     * Get a row-clickable table - See example usages Note: page=-1 will show all records, use where clause to limit data
     */
    public String getTable(DatabaseConnection con, HashMap<String, String> parms, String table, String query, String hideColumn, long page, String columnOverride) {
        try {
            StringBuilder pageNav = new StringBuilder();
            StringBuilder sb = new StringBuilder();
            int rowCount = 0;
            
            long totalRows = con.getRowCount(table);
            // Handle Paging
            if (page > -1) {
                if (totalRows > ROW_COUNT_LIMIT) {
                    pageNav.append(Message.get(con.getLocale(), "PAGE_NAV") + "&nbsp;");
                    long pageCount = totalRows / ROW_COUNT_LIMIT + 1;
                    long dotInterval = DOT_INTERVAL;
                    if (pageCount/dotInterval > DOT_LIMIT) dotInterval = pageCount/DOT_LIMIT;
                    for (long p = 1; p <= pageCount; p++) {
                        if (Math.abs(page - p) < PAGE_WINDOW || pageCount - p < PAGE_WINDOW || p < PAGE_WINDOW) {
                            if (p == page || (page == 0 && p == 1)) {
                                pageNav.append(bold(color("red", "" + p)) + "&nbsp;");  // the page you on is not a link (TODO: use a style here)
                            } else {
                                pageNav.append(linkWithTipHTMX(this.getClass().getName()+"/" + table + "&PAGE=" + p, "" + p, "Page " + p, parms.get("HX-TARGET")) + "&nbsp;");
                            }
                        } else {
                            if (p % dotInterval == 0) {
                                pageNav.append(linkWithTipHTMX(this.getClass().getName()+"/" + table + "&PAGE=" + p, ".", "Page " + p, parms.get("HX-TARGET")));
                            }
                        }
                    }
                }
                String skip = "";
                if (page > 0) skip = " SKIP " + ((page - 1) * ROW_COUNT_LIMIT);
                query += skip + " LIMIT " + ROW_COUNT_LIMIT;
            }
            QueryResult rs = con.query(query);

            String sourceTable = (parms != null ? parms.get("SOURCETABLENAME") : null);
            String sourceId = (parms != null ? parms.get("SOURCEEDIT_ID") : null);
            if (sourceTable != null) {
                sourceTable = "&SOURCETABLENAME=" + sourceTable;
            } else {
                sourceTable = "";
            }
            if (sourceId != null) {
                sourceId = "&SOURCEEDIT_ID=" + sourceId;
            } else {
                sourceId = "";
            }

            if (DEBUG) {
                System.out.println("permeagility.web.Table:query=" + query);
            }

            // Get the table's columns
            Collection<Property> columns = con.getColumns(table, columnOverride);
            if (columns == null) {
                System.out.println("Get columns for table:" + table + " returned null");
            } else {
                sb.append(getRowHeader(con, table, columns, hideColumn));
                for (Document row : rs.get()) {
                    sb.append(rowOnClickHTMX("clickable", getRow(columns, row, con, hideColumn),
                        "/"+this.getClass().getName() // Supports descendants using this function
                        + "/" + table + "/" + row.getIdentity().toString().substring(1)
                        + (!sourceTable.isEmpty() ? "?" + sourceTable + sourceId : "")
                        , null  // title/tooltip would be nice at some point
                        , (parms != null ? parms.get("HX-TARGET") : DEFAULT_TARGET)
                    ));                         
                    rowCount++;
                    if (page > -1 && rowCount >= ROW_COUNT_LIMIT) {
                        if (page == 0) page = 1;  // reached limit with no page specified, must be page 1
                        break;
                    }
                }
                String rowCountInfo;
                if (page <= 1 && rowCount < ROW_COUNT_LIMIT) {  // We came in without a page no, and found less than the limit, no need to show totals or page no
                    rowCountInfo = paragraph(rowCount+" rows");
                } else {
                    rowCountInfo = paragraph(Message.get(con.getLocale(), "ROWS_OF", "" + rowCount, "" + totalRows) 
                                         + "&nbsp;" + (page > -1 ? Message.get(con.getLocale(), "PAGE_NAV") + "&nbsp;" + page : ""));
                }
                sb.append(tableFooter(row(columnSpan(columns.size(), rowCountInfo))));
            }
            return pageNav+tableHTMX("sortable_"+table, (rowCount > 0 ? "sortable" : ""), sb.toString());  // sort table breaks if you have no rows in the tbody

        } catch (Exception e) {
            Throwable cause = e.getCause();
            System.out.println("permeagility.web.Table: Error: " + e.getMessage() + (cause == null ? "" : ": " + cause.getMessage()));
            e.printStackTrace();
            return paragraph("error","Error: " + e.getMessage() + (cause == null ? "" : "<BR>" + cause.getMessage()));
        }
    }

    public String getRowHeader(DatabaseConnection con, String table, Collection<Property> columns, String hideColumn) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (Property column : columns) {
            String columnName = column.getName();
            String colNameI18N;
            String trName = Message.get(con.getLocale(), "COLUMN_" + columnName);
            if (!trName.equals("COLUMN_" + columnName)) {
                colNameI18N = trName;
            } else {
                colNameI18N = makeCamelCasePretty(columnName);
            }
            if (column.getOfType() != null && (Security.getTablePriv(con, column.getOfType()) & Security.PRIV_READ) == 0) {
                continue;
            }
            if (!columnName.toUpperCase().endsWith("PASSWORD") // && !columnName.startsWith("_")
                    && (hideColumn == null || !columnName.equals(hideColumn))) {
                if (columnName.startsWith("button_") && columnName.length() > 8 && columnName.indexOf('_', 7) > 7) {
                    int cp = columnName.indexOf('_', 7);
                    //String n = columnName.substring(7,cp);  // Not used
                    String l = columnName.substring(cp + 1);
                    sb.append(columnHeaderNoSort(l));
                } else {
                    sb.append(columnHeader(colNameI18N));
                }
            }
        }
        return tableHeader(row(sb.toString()));
    }

    public String getRow(Collection<Property> columns, Document d, DatabaseConnection con, String hideColumn) throws SQLException {
        StringBuilder sb = new StringBuilder();
//		if (DEBUG) System.out.println("Table.getRow colCount="+columns.size());
        for (Property column : columns) {
            String fieldName = column.getName();
            if (column.getOfType() != null && (Security.getTablePriv(con, column.getOfType()) & Security.PRIV_READ) == 0) {
                continue;
            }
            if (!fieldName.toUpperCase().endsWith("PASSWORD") // && !fieldName.startsWith("_")
                    && (hideColumn == null || !fieldName.equals(hideColumn))) {
                if (fieldName.startsWith("button_") && fieldName.length() > 8 && fieldName.indexOf('_', 7) > 7) {
                    int cp = fieldName.indexOf('_', 7);
                    String n = fieldName.substring(7, cp);
                    String l = fieldName.substring(cp + 1);
                    sb.append(column(form(button(n, d.getIdentity().toString().substring(1), l))));
                } else {
                    sb.append(getColumnAsCell(column, d, con));
                }
            }
        }
        return sb.toString();
    }

    public String getColumnAsCell(Property column, Document d, DatabaseConnection con) {
        StringBuilder sb = new StringBuilder();
        String columnName = column.getName();
        Type columnType = column.getType();
		//if (DEBUG) System.out.println("permeagility.web.Table.getColumnAsCell name = "+columnName+" type = "+(columnType==null ? "null" : columnType));
        if (columnType == Type.BOOLEAN) {
            sb.append(column(checkboxDisabled(columnName, (d.getBoolean(columnName) == null ? false : d.getBoolean(columnName)))));
        } else if (columnType == Type.INTEGER || columnType == Type.SHORT || columnType == Type.LONG) {   // int, short, long type
            sb.append(column("number", "" + (d.getLong(columnName) == null ? "" : formatNumber(con.getLocale(), (Number) d.getLong(columnName), INT_FORMAT))));
        } else if (columnType == Type.FLOAT || columnType == Type.DOUBLE) {   // float, double
            sb.append(column("number", "" + (d.getDouble(columnName) == null ? "" : formatNumber(con.getLocale(), (Number) d.getDouble(columnName), FLOAT_FORMAT))));
        } else if (columnType == Type.DATETIME) {  // Datetime
            sb.append(column("" + (d.getLocalDateTime(columnName) == null ? "" : formatDatetime(con.getLocale(), d.getLocalDateTime(columnName)))));
        } else if (columnType == Type.STRING) {  // String
            if (columnName.toUpperCase().endsWith("COLOR")
                    || columnName.toUpperCase().endsWith("COLOUR")) {
                sb.append(columnColor(5, d.getString(columnName)));
            } else {
                String stringvalue = d.getString(columnName);
                if (stringvalue != null && stringvalue.length() > MAX_STRING_DISPLAY) {
                    stringvalue = stringvalue.substring(0, MAX_STRING_DISPLAY) + "...";
                }
                if (stringvalue != null) {
                    stringvalue = stringvalue.replace("<", "&lt;"); // These can mess up the display
                    stringvalue = stringvalue.replace(">", "&gt;");
                }
                sb.append(column(stringvalue));
            }
        } else if (columnType == Type.BINARY) {  // Binary 
            StringBuilder desc = new StringBuilder();
            String blobid = Thumbnail.getThumbnailId(con, d.getTypeName(), d.getIdentity().toString().substring(1), columnName, desc);
            if (blobid != null) {
                sb.append(column(Thumbnail.getThumbnailAsCell(con.getLocale(), blobid, desc.toString())));
            } else {
                sb.append(column("<div title=\"" + Message.get(con.getLocale(), "THUMBNAIL_NOT_FOUND", columnName, d.getIdentity().toString() + "\">" + Message.get(con.getLocale(), "OPTION_NONE") + "</div>")));
            }

        } else if (columnType == Type.EMBEDDED) {  // Embedded
            String stringvalue = "??";
            try {
                EmbeddedDocument value = d.getEmbedded(columnName);
                stringvalue = value == null ? "" : value.toJSON().toString();
                if (stringvalue != null && stringvalue.length() > MAX_STRING_DISPLAY) {
                    stringvalue = stringvalue.substring(0, MAX_STRING_DISPLAY) + "...";
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Table.getColumnAsCell: Error in showing embedded object "+columnName+" from table "+d.getTypeName());
            }
            sb.append(column(stringvalue));
        } else if (columnType == Type.LINK) {  // Link
            String desc = "";
            try {
                Object o = d.get(columnName);
                Document l = null;
                if (o != null) {
                    if (o instanceof RID) {
                        l = con.get((RID)o);
                    } else if (o instanceof Document) {
                        l = (Document)o;
                    } else {
                        System.out.println("Table.getColumnAsCell: LINK Found instance of "+o.getClass().getName());
                    }
                }
                if (l != null) {
                    desc = getDescriptionFromDocument(con, l);
                    if (desc == null) {
                        desc = l.getString("name");
                    }
                }
            } catch (Exception e) {
                System.out.println("A null Document link was found in column " + columnName + " of " + d.getIdentity().toString());
                e.printStackTrace();
                desc = "!";
            }
            sb.append(column(desc == null ? "null" : desc));
        } else if (columnType == Type.LIST) {  // LinkList
            List<RID> l = d.getList(columnName);
            StringBuilder ll = new StringBuilder();
            if (l != null) {
                if (DEBUG) {
                    System.out.println("linkList size=" + l.size() + (l.size() > 0 ? " type=" + column.getOfType() : ""));
                }
                for (RID rid : l) {
                    Document o = con.get(rid);
                    if (o != null) {
                        ll.append(getDescriptionFromDocument(con, o) + br());
                    }
                }
            }
            sb.append(column(ll.toString()));
          } else if (columnType == Type.MAP) {    // LinkMap
            Map<String, Object> l = d.getMap(columnName);
            StringBuilder ll = new StringBuilder();
            if (l != null) {
                for (String k : l.keySet()) {
                    RID rid = (RID)l.get(k);
                    Document o = con.get(rid);
                    if (o != null) {
                        ll.append(k + ":" + getDescriptionFromDocument(con, o) + br());
                    }
                }
            }
            sb.append(column(ll.toString()));
        } else if (columnType == Type.BYTE) {  // Byte
            sb.append(column("" + d.getByte(columnName)));
        } else if (columnType == Type.DATE) {  // Date
            sb.append(column("" + (d.getDate(columnName) == null ? "" : formatDate(con.getLocale(), d.getDate(columnName)))));
        } else if (columnType == Type.DECIMAL) {   // Decimal
            Number num = (Number) d.getDecimal(columnName);
            String formatted = (num == null ? "" : formatNumber(con.getLocale(), num, FLOAT_FORMAT));
            sb.append(column("number", formatted));
        } else {
            if (DEBUG) {
                System.out.println("Table: unrecognized type " + columnType);
            }
            sb.append(column("??" + columnType + "??"));
        }
        return sb.toString();
    }

    public String advancedOptions(DatabaseConnection con, String table, HashMap<String, String> parms, StringBuilder errors) {
        Locale locale = con.getLocale();
        String submit = parms.get("SUBMIT");
        if (submit != null) {
            if (submit.equals("RENAME_TABLE_BUTTON")) {
                if (isNullOrBlank(parms.get("RENAME_TABLE"))) {
                    errors.append(paragraph("error", "Please specify a new name"));
                } else {
                    try {
                        String newtable = parms.get("RENAME_TABLE");
                        newtable = makePrettyCamelCase(newtable);
                        Server.tableUpdated(con, table);
                        con.update("ALTER TYPE " + table + " NAME " + newtable);
                        con.update("UPDATE columns SET name='" + newtable + "' WHERE name='" + table + "'");
                        table = newtable;
                        Server.tableUpdated(con, "metadata:schema");
                        return getTableWithControls(con, parms, table);
//                        return redirect(parms, this, "TABLENAME=" + table);
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
                        con.update("ALTER PROPERTY " + table + "." + oldcolumn + " NAME " + newcolumn);
                        MutableDocument d = con.queryDocument("SELECT FROM columns WHERE name='" + table + "'").modify();
                        if (d != null) {
                            String cl = d.getString("columnList");
                            if (cl != null) {
                                d.set("columnList", cl.replace(oldcolumn, newcolumn));
                                d.save();
                            }
                        }
                        Server.tableUpdated(con, "metadata:schema");
                        return redirect(parms, this, "TABLENAME=" + table);
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
                        Object ret = con.update("UPDATE " + table + " REMOVE " + colToDrop);  // Otherwise, column actually remains in the data
                        DocumentType c = con.getSchema().getType(table);
                        c.dropProperty(colToDrop);
                        errors.append(paragraph("success", "Data for column removed:" + ret));
                        Setup.removeColumnFromColumns(con, table, colToDrop);
                        Server.tableUpdated(con, "metadata:schema");
                        return redirect(parms, this, "TABLENAME=" + table);
                    } catch (Exception e) {
                        errors.append(paragraph("error", e.getMessage()));
                    }
                }
            } else if (submit.equals("DROP_TABLE_BUTTON")) {
                try {
                    if (Setup.dropTable(con, table, errors)) {
                        return errors + new Schema().getPage(con, parms);
                    } else {
                        return null;
                    }
                } catch (Exception e) {
                    errors.append(paragraph("error", e.getMessage()));
                }
            }
        }
        String title = table + " " + Message.get(locale, "ADVANCED_OPTIONS");
        parms.put("SERVICE", title);
        return head(con, title)
                + body(         //advancedOptionsForm(con, table, parms, errors.toString())
                                //+ br()
                                 linkHTMX(this.getClass().getName()+"/" + table, Message.get(locale, "BACK_TO_TABLE"), parms.get("HX-TARGET"))
                        );
    }

    public String advancedOptionsForm(DatabaseConnection con, String table, HashMap<String, String> parms, String errors) {
        Locale locale = con.getLocale();
        Collection<Property> properties = con.getSchema().getType(table).getProperties();
        return hidden("ADVANCED_OPTIONS", "YES")
                + paragraph("banner", Message.get(locale, "ADVANCED_OPTIONS"))
                + errors
                + paragraph(Message.get(locale, "RENAME_TABLE_TO") + input("RENAME_TABLE", (parms != null ? parms.get("RENAME_TABLE") : ""))
                    + submitButton(locale, "RENAME_TABLE_BUTTON"))
                + paragraph(Message.get(locale, "RENAME_COLUMN") + " "
                    + createColumnList(locale, "COLUMN_TO_RENAME", null, null, true, null, true, properties )
                    + Message.get(locale, "CHANGE_NAME_TO")
                    + input("RENAME_COLUMN", (parms != null ? parms.get("RENAME_COLUMN") : ""))
                    + submitButton(locale, "RENAME_COLUMN_BUTTON"))
                + paragraph(Message.get(locale, "DROP_COLUMN") + " "
                    + createColumnList(locale, "COLUMN_TO_DROP", null, null, true, null, true, properties )
                    + confirmButton(locale, "DROP_COLUMN_BUTTON", "DROP_COLUMN"))
                + paragraph(Message.get(locale, "DROP_TABLE") + " " + table + "   "
                    + confirmButton(locale, "DROP_TABLE_BUTTON", "DROP_TABLE_CONFIRM"));
    }

    public String rightsOptions(DatabaseConnection con, String table, HashMap<String, String> parms) {
        Locale locale = con.getLocale();
        StringBuilder errors = new StringBuilder();
        String submit = (parms != null ? parms.get("SUBMIT") : null);
        String right = (parms != null ? parms.get("RIGHT") : null);
        String role = (parms != null ? parms.get("ROLESELECT") : null);
        // need to validate the right value
        if (submit != null && submit.equals("GRANT_RIGHT")
            && right != null && role != null) {
            if (DEBUG) System.out.println("Granting right (via INSERT to priv table)");
            try {
                MutableDocument privilege = con.create("privilege");
                privilege.set("access", right);
                privilege.set("resource", table);
                privilege.set("identity", "#"+role);
                privilege.save();
                Server.tableUpdated(con, "privilege"); 
                Security.tablePrivUpdated(table);
            } catch (Exception e) {
                errors.append(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }
        if (submit != null && submit.equals("REVOKE_RIGHT")
               && right != null && role != null) {
            if (DEBUG)  System.out.println("Revoking right (via DELETE from priv table)");
            
            String revokeQuery;
            if (right.equals("ALL")) {
                revokeQuery = "DELETE FROM privilege WHERE resource='"+table+"' AND identity=#"+role;
            } else {
                revokeQuery = "DELETE FROM privilege WHERE access='"+right+"' AND resource='"+table+"' AND identity=#"+role;
            }
            System.out.println("Executing REVOKE: " + revokeQuery);
            try {
                con.update(revokeQuery);
                //Server.tableUpdated(con, "privilege");
                Security.tablePrivUpdated(table);
            } catch (Exception e) {
                errors.append(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }
        return rightsOptionsForm(con, table, parms, errors.toString());
                
    }

    public String rightsOptionsForm(DatabaseConnection con, String table, HashMap<String, String> parms, String errors) {
        StringBuilder currentRights = new StringBuilder();
        List<String> rightsNames = new ArrayList<>();
        HashMap<String, Integer> privs = Security.getTablePrivs(con, table);

        for (String role : privs.keySet()) {
            Integer b = privs.get(role);
            if (b != null) {
                StringBuilder sb = new StringBuilder();
                if (b.intValue() == 0) {
                    //sb.append(Message.get(con.getLocale(), "PRIV_NONE"));
                } else if (b.intValue() == Security.PRIV_ALL) {
                    sb.append(Message.get(con.getLocale(), "PRIV_ALL"));
                } else {
                    if (DEBUG) {
                        System.out.println("role=" + role + " table=" + table 
                        + " create=" + (b.intValue() & Security.PRIV_CREATE) 
                        + " read=" + (b.intValue() & Security.PRIV_READ) 
                        + " update=" + (b.intValue() & Security.PRIV_UPDATE) 
                        + " delete=" + (b.intValue() & Security.PRIV_DELETE));
                    }
                    if ((b.intValue() & Security.PRIV_CREATE) > 0) {
                        sb.append(Message.get(con.getLocale(), "PRIV_CREATE"));
                    }
                    if ((b.intValue() & Security.PRIV_READ) > 0) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(Message.get(con.getLocale(), "PRIV_READ"));
                    }
                    if ((b.intValue() & Security.PRIV_UPDATE) > 0) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(Message.get(con.getLocale(), "PRIV_UPDATE"));
                    }
                    if ((b.intValue() & Security.PRIV_DELETE) > 0) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(Message.get(con.getLocale(), "PRIV_DELETE"));
                    }
                }
                if (sb.length() > 0) {
                    currentRights.append(Message.get(con.getLocale(), "ROLE_CAN_PRIV", role, sb.toString()) + br());
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
                + paragraph("banner", Message.get(con.getLocale(), "EXISTING_RIGHTS"))
                + currentRights.toString()
                + paragraph("banner", Message.get(con.getLocale(), "ADD_OR_REMOVE_RIGHT"))
                + createListFromCache("ROLESELECT", null, con, getQueryForTable(con, "identity"), null, false, null, true)
                + createList(con.getLocale(), "RIGHT", null, rightsNames, null, false, null, true)
                + submitButton(con.getLocale(), "GRANT_RIGHT")
                + submitButton(con.getLocale(), "REVOKE_RIGHT")
                + POPUP_FORM_CLOSER;
    }

    public static String password(String name, Object value, int size) {
        return "<INPUT TYPE=\"PASSWORD\" NAME=\"" + name + "\" VALUE=\"" + (value == null ? "" : value) + "\" SIZE=\"" + size + "\">";
    }

    public static String getTypeName(Integer i) {
        Type type = Type.getById(i.byteValue());
        if (type == Type.DOUBLE) {
            return "DATATYPE_FLOAT";
        } else if (type == Type.LONG) {
            return "DATATYPE_INT";
        } else if (type == Type.BOOLEAN) {
            return "DATATYPE_BOOLEAN";
        } else if (type == Type.STRING) {
            return "DATATYPE_TEXT";
        } else if (type == Type.DATETIME) {
            return "DATATYPE_DATETIME";
        } else if (type == Type.DATE) {
            return "DATATYPE_DATE";
        } else if (type == Type.BINARY) {
            return "DATATYPE_BLOB";
        } else if (type == Type.DECIMAL) {
            return "DATATYPE_DECIMAL";
        } else if (type == Type.LINK) {
            return "DATATYPE_LINK";
        } else if (type == Type.LIST) {
            return "DATATYPE_LIST";
        } else if (type == Type.MAP) {
            return "DATATYPE_MAP";
        } else {
            return (type.name());
        }
    }

    public static Type getTypeFromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        } else if (name.equals("DATATYPE_FLOAT")) {
            return Type.DOUBLE;
        } else if (name.equals("DATATYPE_INT")) {
            return Type.LONG;
        } else if (name.equals("DATATYPE_BOOLEAN")) {
            return Type.BOOLEAN;
        } else if (name.equals("DATATYPE_TEXT")) {
            return Type.STRING;
        } else if (name.equals("DATATYPE_DATETIME")) {
            return Type.DATETIME;
        } else if (name.equals("DATATYPE_DATE")) {
            return Type.DATE;
        } else if (name.equals("DATATYPE_BLOB")) {
            return Type.BINARY;
        } else if (name.equals("DATATYPE_DECIMAL")) {
            return Type.DECIMAL;
        } else if (name.equals("DATATYPE_LINK")) {
            return Type.LINK;
        } else if (name.equals("DATATYPE_LIST")) {
            return Type.LIST;
        } else if (name.equals("DATATYPE_MAP")) {
            return Type.MAP;
        } else {
            return Type.STRING;
        }
    }

    public static DateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    public static DateFormat sqlDatetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Need to store the data type names by locale and don't want to generate it every time so this is a cache
    static ConcurrentHashMap<Locale, ArrayList<String>> dataTypeNames = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Locale, ArrayList<String>> dataTypeValues = new ConcurrentHashMap<>();

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
        names.add(Message.get(locale, "DATATYPE_LIST")); // Link List control
        names.add(Message.get(locale, "DATATYPE_MAP"));  // Link map control
        names.add(Message.get(locale, "DATATYPE_BLOB"));  // Image (with thumbnail)
        values.add("DATATYPE_TEXT");
        values.add("DATATYPE_FLOAT");
        values.add("DATATYPE_INT");
        values.add("DATATYPE_DECIMAL");
        values.add("DATATYPE_DATE");  // Calendar
        values.add("DATATYPE_DATETIME"); // Calendar and time
        values.add("DATATYPE_BOOLEAN");   // Checkbox
        values.add("DATATYPE_LINK");    // PickList
        values.add("DATATYPE_LIST"); // Link List control
        values.add("DATATYPE_MAP");  // Link map control
        values.add("DATATYPE_BLOB"); // Image (with thumbnail)
        dataTypeNames.put(locale, names);
        dataTypeValues.put(locale, values);
    }

    // Clear all the dataTypes from the list if a message or locale has changed - called by Server
    public static void clearDataTypes() {
        dataTypeNames.clear();
        dataTypeValues.clear();
    }

}
