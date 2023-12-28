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

import java.util.ArrayList;
import java.util.Collection;
import com.arcadedb.database.Document;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Type;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class Schema extends Weblet {

    public static boolean DEBUG = false;
    public static int NUMBER_OF_COLUMNS = 4;
    public static boolean ADD_NAME_TO_NEW_TABLE = true;   // Will always add a name field to a new table (if you don't like it, delete it or change this constant)

    @Override
    public String getPage(DatabaseConnection con, java.util.HashMap<String, String> parms) {
        parms.put("SERVICE", Message.get(con.getLocale(), "SCHEMA_EDITOR"));
        StringBuilder errors = new StringBuilder();
        String submit = parms.get("SUBMIT");
        if (submit != null) {
            if (submit.equals("NEW_TABLE")) {
                String tn = parms.get("NEWTABLENAME");
                if (tn == null || tn.equals("")) {
                    errors.append(paragraph("error", "Table name must be specified"));
                } else {
                    try {
                        String camel = makePrettyCamelCase(tn);
                        DocumentType newclass = con.getSchema().getOrCreateDocumentType(camel);
                        if (newclass != null) {
                            errors.append(serviceNotificationDiv(paragraph("success", Message.get(con.getLocale(), "NEW_TABLE_CREATED", camel, makeCamelCasePretty(camel)))));
                            if (ADD_NAME_TO_NEW_TABLE) {
                                Setup.checkCreateColumn(con, newclass, "name", Type.STRING, errors);
                                //newclass.createProperty("name", OType.STRING).setNotNull(false).setMandatory(false);
                            }
                            Server.tableUpdated(con, "metadata:schema");
                        } else {
                            errors.append(paragraph("warning", "New table returned null class"));
                        }
                    } catch (Exception e) {
                        errors.append(paragraph("error", "Cannot create table: " + e.getMessage()));
                    }
                }
            }
        }

        // Prepare response
        int cellCount = 0;
        StringBuilder rows = new StringBuilder();
        StringBuilder columns = new StringBuilder();
        QueryResult schemas = con.query("SELECT FROM " + Setup.TABLE_TABLEGROUP /*  + " WHERE _allowRead in [" + Security.getUserRolesList(con) + "]"*/ +" ORDER BY name");
        //QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
        Collection<? extends DocumentType> tables = con.getSchema().getTypes();
        if (DEBUG) System.out.println("Retrieved "+tables.size()+" DocumentTypes");
        ArrayList<String> tablesInGroups = new ArrayList<>();
        for (Document schema : schemas.get()) {
            String groupName = schema.getString("name");
            StringBuilder tablelist = new StringBuilder();
            String tablesf = schema.getString("tables");
            String table[] = {};
            if (tablesf != null) {
                table = tablesf.split(",");
            }
            String prettyGroup = Message.get(con.getLocale(), "TABLEGROUP_" + groupName);
            if (prettyGroup != null && ("TABLEGROUP_" + groupName).equals(prettyGroup)) {
                prettyGroup = makeCamelCasePretty(groupName);
            }

            tablelist.append(paragraph("banner", prettyGroup));
            boolean groupHasTable = false;
            for (String tableName : table) {
                tableName = tableName.trim();
                boolean show = true;
                if (tableName.startsWith("-")) {
                    show = false;
                    tableName = tableName.substring(1);
                }
                String pretty = Message.get(con.getLocale(), "TABLE_" + tableName);
                if (pretty != null && ("TABLE_" + tableName).equals(pretty)) {
                    pretty = makeCamelCasePretty(tableName);
                }
                tablesInGroups.add(tableName);
                if (show) {
                    int privs = Security.getTablePriv(con, tableName);
                    //System.out.println("Table privs for table "+tableName+" for user "+con.getUser()+" privs="+privs);
                    if (privs > 0) {
                        tableName = tableName.trim();
                        if (con.getSchema().existsType(tableName)) {
                            tablelist.append(linkHTMX("/Table/" + tableName, pretty, parms.get("HX-TARGET")) + br());
                            groupHasTable = true;
                        } else {
                            if (!tableName.isEmpty()) {
                                if (DEBUG) System.out.println("permeagility.web.Schema: Table " + tableName + " not found - will not be shown");
                            }
                        }
                    }
                }
            }
            if (groupHasTable && !groupName.startsWith("-")) {
                columns.append(column("layout", tablelist.toString()));
                cellCount++;
                if (cellCount == NUMBER_OF_COLUMNS) {
                    rows.append(row(columns.toString()));
                    columns = new StringBuilder();
                    cellCount = 0;
                }
            }
        }

        // Add new/ungrouped - for DBA's only
        if (Security.isDBA(con)) {
            StringBuilder tablelist = new StringBuilder();
            tablelist.append(paragraph("banner", Message.get(con.getLocale(), "TABLE_NONGROUPED")));
            for (DocumentType row : tables) {
                String tablename = row.getName();
                if (!tablesInGroups.contains(tablename)) {
                    if (Security.getTablePriv(con, tablename) > 0) {
                        String pretty = Message.get(con.getLocale(), "TABLE_" + tablename);
                        if (pretty != null && ("TABLE_" + tablename).equals(pretty)) {
                            pretty = makeCamelCasePretty(tablename);
                        }
                        tablelist.append(linkHTMX("/Table"+"/" + tablename, pretty, parms.get("HX-TARGET")) + br());
                    }
                }
            }
            columns.append(column("layout", tablelist.toString()));
        }

        // Make sure the last row is added
        if (columns.length() > 0) {
            rows.append(row(columns.toString()));
        }

        // Return result
        return errors.toString()
                + table("layout", rows.toString()) + br()
                + (Security.isDBA(con)
                        ? popupFormHTMX("NEWTABLE_Ungrouped", "/Schema", "PUT", parms.get("HX-TARGET"), Message.get(con.getLocale(), "NEW_TABLE"), "NEWTABLENAME",
                                input("NEWTABLENAME", "") + "&nbsp;&nbsp;"
                                + submitButton(con.getLocale(), "NEW_TABLE")
                        )
                        : "")
                    + serviceHeaderUpdateDiv(parms, Message.get(con.getLocale(),"SCHEMA_EDITOR", con.getDb().getName()));
    }

    public static String getTableSelector(DatabaseConnection con) {
        return getTableSelector(con, "selTable");
    }

    public static String getTableSelector(DatabaseConnection con, String model) {
        StringBuilder tableInit = new StringBuilder(); // JSON list of tables and groups

        // Add tables in groups (similar code to Schema - should be combined in one place - need one more use - and this should be it)
        QueryResult schemas = con.query("SELECT from tableGroup");
        //QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
        Collection<? extends DocumentType> tables = con.getSchema().getTypes();
        ArrayList<String> tablesInGroups = new ArrayList<>();
        for (Document schema : schemas.get()) {
            StringBuilder tablelist = new StringBuilder();
            String tablesf = schema.getString("tables");
            String table[] = {};
            if (tablesf != null) {
                table = tablesf.split(",");
            }
            String groupName = schema.getString("name");
            tablelist.append(paragraph("banner", groupName));
            //boolean groupHasTable = false;
            for (String tableName : table) {
                tableName = tableName.trim();
                boolean show = true;
                if (tableName.startsWith("-")) {
                    show = false;
                    tableName = tableName.substring(1);
                }
                tablesInGroups.add(tableName);
                if (show) {
                    int privs = Security.getTablePriv(con, tableName);
                    //System.out.println("Table privs for table "+tableName+" for user "+con.getUser()+" privs="+privs);
                    if (privs > 0) {
                        tableName = tableName.trim();
                        if (tableInit.length() > 0) {
                            tableInit.append(", ");
                        }
                        tableInit.append("{ group:'" + groupName + "', table:'" + tableName + "'}");
                        //		groupHasTable = true;
                    }
                }
            }
        }

        // Add the non grouped (new) tables
        StringBuilder tablelist = new StringBuilder();
        tablelist.append(paragraph("banner", Message.get(con.getLocale(), "TABLE_NONGROUPED")));
        for (DocumentType row : tables) {
            String tablename = row.getName();
            if (!tablesInGroups.contains(tablename)) {
                if (Security.getTablePriv(con, tablename) > 0) {
                    if (tableInit.length() > 0) {
                        tableInit.append(", ");
                    }
                    tableInit.append("{ group:'New', table:'" + tablename + "'}");
                }
            }
        }

        return table(
                        row(column("")
                                + column(
                                        "<select ng-model=\"selGroup\"\n"
                                        + "  ng-options=\"v.group for v in tables | unique:'group'\" >\n"
                                        + "  <option value=\"\">Table Group</option>\n"
                                        + "</select>\n")
                                + column("")
                                + column("<select id=\"tableSelector\" ng-model=\""+model+"\" \n"
                                        + "  ng-options=\"v.table for v in tables | filter:{group:selGroup.group} | orderBy:'table'\">\n"
                                        + "  <option value=\"\">Table</option>\n"
                                        + "</select>\n")
                        )
                );
    }

}
