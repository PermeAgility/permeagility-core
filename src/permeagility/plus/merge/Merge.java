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
package permeagility.plus.merge;

import java.util.HashMap;

import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Table;

import permeagility.util.Setup;

public class Merge extends Table {

    // Override this with a constant to true after installation to avoid installation check
    public static boolean INSTALLED = false;  // Will check for existence of config tables and create - can turn off in constant

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();

 //       String submit = parms.get("SUBMIT");
 //       String connect = parms.get("CONNECT");
        String fromTable = parms.get("fromTable");
        String toTable = parms.get("toTable");
        String fromKey = parms.get("fromKey");
        String toKey = parms.get("toKey");
 //       String editId = parms.get("EDIT_ID");
 //       String updateId = parms.get("UPDATE_ID");
        String run = parms.get("RUN");
        String tableName = parms.get("TABLENAME");

        // Process update of work tables
        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) { return update; }

/*        if (updateId != null && submit != null) {
            System.out.println("update_id=" + updateId);
            if (submit.equals("DELETE")) {
                if (deleteRow(con, tableName, parms, errors)) {
                    submit = null;
                } else {
                    return head(con, "Could not delete")
                            + body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
                }
            } else if (submit.equals("UPDATE")) {
                System.out.println("In updating row");
                if (updateRow(con, tableName, parms, errors)) {
                    submit = null;
                } else {
                    return head(con, "Could not update", getDateControlScript(con.getLocale()) + getColorControlScript())
                            + body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
                }
            }
            // Cancel is assumed
            editId = null;
            updateId = null;
            connect = parms.get(PARM_PREFIX + "path");
        }
*/
        // Create a merge directly - set the created date
/*        if (submit != null && submit.equals("CREATE_ROW")) {
            parms.put(PARM_PREFIX + "created", formatDate(con.getLocale(), new java.util.Date()));
            boolean inserted = insertRow(con, tableName, parms, errors);
            if (!inserted) {
                errors.append(paragraph("error", "Could not insert"));
            }
        }
        */
/*
        // Show edit form if row selected for edit
        if (editId != null && submit == null && connect == null) {
            toTable = tableName;
            return head(con, "Edit", getDateControlScript(con.getLocale()) + getColorControlScript())
                    + body(standardLayout(con, parms, getTableRowForm(con, toTable, parms)));
        }
*/
        if (run != null) {
            // Run a merge path 
            int insertCount = 0;
            int updateCount = 0;
            System.out.println("Running merge path " + run);
            //editId = null;
            Document mDoc = con.get(run);
            if (mDoc != null) {
                fromTable = mDoc.getString("fromTable");
                toTable = mDoc.getString("toTable");
                fromKey = mDoc.getString("fromKey");
                toKey = mDoc.getString("toKey");
            }
            Schema oschema = con.getSchema();
            DocumentType fromClass = oschema.getType(fromTable);
            System.out.println("Merge from " + fromTable + " to " + toTable);
            DocumentType toClass = Setup.checkCreateTable(oschema, tableName, errors);
            QueryResult fromResult = null;
            QueryResult toResult = null;
            try {
                fromResult = con.query("SELECT FROM " + fromTable + " ORDER BY " + fromKey);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                sb.append(paragraph("error", "FromTable: " + cause == null ? e.getMessage() : cause.getMessage()));
            }
            try {
                toResult = con.query("SELECT FROM " + toTable + " ORDER BY " + toKey);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                sb.append(paragraph("error", "ToTable: " + cause == null ? e.getMessage() : cause.getMessage()));
            }
            if (fromResult != null && toResult != null) {
                sb.append(paragraph("success", "Merging from count " + fromResult.size() + " to count " + toResult.size()));
                QueryResult columnMap = null;
                try {
                    columnMap = con.query("SELECT FROM " + PlusSetup.ATTR_TABLE + " WHERE path=#" + run);
                } catch (Exception e) {
                    Throwable cause = e.getCause();
                    sb.append(paragraph("error", "AttributeTable: " + cause == null ? e.getMessage() : cause.getMessage()));
                }
                if (columnMap != null && columnMap.size() > 0) {
                    int fromIndex = 0;
                    int toIndex = 0;
                    String lastFromKey = null;
                    while (fromIndex < fromResult.size()) {
                        Document fromDoc = fromResult.get(fromIndex);
                        MutableDocument toDoc = null;
                        if (toIndex < toResult.size()) {
                            toDoc = (MutableDocument)toResult.get(toIndex);
                        }
                        Object fromId = fromDoc.get(fromKey);
                        Object toId = (toDoc != null ? toDoc.get(toKey) : null);
                        if (fromId == null) {
                            sb.append(paragraph("error", "Cannot merge a null key"));
                            fromIndex++;
                        } else {
                            String fromK = fromId.toString();
                            if (toId != null) {
                                String toK = toId.toString();
                                int comp = fromK.compareTo(toK);
                                if (comp < 0) {
                                    //sb.append(paragraph("Would insert "+fromKey+"="+fromK));
                                    if (fromK.equals(lastFromKey)) {
                                        sb.append(paragraph("Ignored duplicate from key " + fromKey + "=" + fromK));
                                    } else {
                                        insertCount += insertDocument(con, fromDoc, columnMap, toTable);
                                    }
                                    fromIndex++;
                                } else if (comp == 0) {
                                    //sb.append(paragraph("Would merge "+fromKey+"="+fromK));
                                    updateCount += mergeDocument(con, fromDoc, toDoc, columnMap);
                                    fromIndex++;
                                    toIndex++;
                                } else {
                                    toIndex++;
                                }
                            } else {
                                //sb.append(paragraph("To is empty: Would insert "+fromKey+"="+fromK));
                                if (fromK.equals(lastFromKey)) {
                                    sb.append(paragraph("Ignored duplicate from key " + fromKey + "=" + fromK));
                                } else {
                                    insertCount += insertDocument(con, fromDoc, columnMap, toTable);
                                }
                                fromIndex++;
                                toIndex++;
                            }
                            lastFromKey = fromK;
                        }
                    }
                    sb.append(paragraph("success", "Merged " + columnMap.size() + " columns in " + fromResult.size() + " rows "));
                    if (insertCount > 0 || updateCount > 0) {
                        sb.append(paragraph("success", "Merge: " + insertCount + " rows inserted " + updateCount + " data elements merged"));
                    } else {
                        sb.append(paragraph("warning", "Zero inserts or updates"));
                    }
                } else {
                    sb.append(paragraph("error", "No columns mapped from table " + fromTable + " to table " + toTable + " in merge path " + mDoc.getString("name")));
                }
            }
        }
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "Merge: Setup/Select merge path");
                sb.append(paragraph("banner", "Merge paths"));
                sb.append(getTable(con, parms, PlusSetup.MERGE_TABLE, "SELECT FROM " + PlusSetup.MERGE_TABLE, null, 0, "button_RUN_Run, name, fromTable, toTable, created, executed"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving import patterns: " + e.getMessage());
            }
        }
        return head(con, "Merge")
                + body(standardLayout(con, parms,
                                errors.toString()
                                + ((Security.getTablePriv(con, PlusSetup.MERGE_TABLE) & Security.PRIV_CREATE) > 0
                                        ? popupForm("CREATE_NEW_ROW", this.getClass().getName(), "New merge path", null, "name",
                                                paragraph("banner", Message.get(con.getLocale(), "CREATE_ROW"))
                                                + hidden("TABLENAME", PlusSetup.MERGE_TABLE)
                                                + getTableRowFields(con, PlusSetup.MERGE_TABLE, parms, "name,fromTable,toTable,fromKey,toKey")
                                                + submitButton(con.getLocale(), "CREATE_ROW"))
                                        : "")
                                + sb.toString()
                        ));
    }

    public int insertDocument(DatabaseConnection con, Document doc, QueryResult columnMap, String toTable) {
        DocumentType tableClass = con.getSchema().getType(toTable);
        MutableDocument newdoc = con.create(toTable);
        if (newdoc == null || tableClass == null) {
            System.out.println("Could not create new document of type " + toTable);
            return 0;
        }
        return 1 + mergeDocument(con, doc, newdoc, columnMap);
    }

    public int mergeDocument(DatabaseConnection con, Document fromDoc, MutableDocument toDoc, QueryResult columnMap) {
        if (fromDoc == null || toDoc == null || columnMap == null) {
            System.out.println("Merge: What the?");
            return 0;
        }
        StringBuilder errors = new StringBuilder();
        int mergeCount = 0;
        DocumentType tableClass = toDoc.getType();
        for (Document cm : columnMap.get()) {
            try {
                String fromCol = cm.getString("fromColumn");
                String toCol = cm.getString("toColumn");
                String linkProp = cm.getString("linkProperty");
                Property toProp = tableClass.getProperty(toCol);
                if (fromCol == null || fromCol.equals("")) {
                    System.out.println("fromColumn is null");
                } else if (toCol == null || toCol.equals("")) {
                    System.out.println("toColumn is null");
                } else {
                    if (toProp == null) {
                        System.out.println("toColumn property can not be found in the target class");
                        toProp = Setup.checkCreateColumn(con, tableClass, toCol, fromDoc.getType().getProperty(fromCol).getType(), errors);                    
                    }
                    String linkedClass = toProp.getOfType();
                    Object data = fromDoc.get(fromCol);
                    Object toData = toDoc.get(toCol);
                    if (data != null) {
                        if (linkedClass != null) {
                            if (linkProp != null && toProp.getType() == Type.LINK) {
                                String q = "SELECT FROM " + linkedClass + " WHERE " + linkProp + " = " + (data instanceof String ? "'" + data + "'" : data.toString());
                                //System.out.println("query="+q);
                                QueryResult refs = con.query(q);
                                if (refs != null && refs.size() > 0) {
                                    Document linkDoc = refs.get(0);
                                    if (toData == null || !((Document) toData).getIdentity().equals(linkDoc.getIdentity())) {
                                        toDoc.set(toCol, linkDoc);
                                        mergeCount++;
                                    }
                                } else {
                                    System.out.println("Could not find document for link to " + linkedClass + " where " + linkProp + "=" + data);
                                }
                            } else {
                                System.out.println("Linked class found but linkProperty not defined or link is multiple type");
                            }
                        } else if (toData == null || !data.toString().equals(toData.toString())) {
                            if (toProp.getType() == Type.BOOLEAN) {
                                String first = data.toString().substring(0, 1).toUpperCase();
                                if (first.equals("T") || first.equals("Y")) {
                                    toDoc.set(toCol, true);
                                } else {
                                    toDoc.set(toCol, false);
                                }
                            } else {
                                toDoc.set(toCol, data);
                            }
                            mergeCount++;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mergeCount > 0) {
            toDoc.save();
        }
        return mergeCount;
    }

}
