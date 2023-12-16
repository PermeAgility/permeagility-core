/*
 * Copyright 2016 PermeAgility Incorporated.
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

import java.util.HashMap;
import com.arcadedb.database.Document;

import permeagility.util.DatabaseConnection;

/**
 * Run a JavaScript to generate a page, parms and con passed in and an HTML string is expected out
 * @author glenn
 */
public class Scriptlet extends Table {

    String serviceStyleOverride = "#service { top: 0px !important; left: 0px !important; right: 0px !important; bottom: 0px !important; }\n";

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
        HTMX_MODE = true;
    	StringBuilder sb = new StringBuilder();
        String styleScript = "";
        String htmlScript = "";

        // Put rest stuff here
        String httpMethod = parms.get("HTTP_METHOD");
        String restOfURL = parms.get("REST_OF_URL");  // if rest attributes exist then parse table/id
        if (restOfURL != null && !restOfURL.isEmpty()) {
            String[] restParts = restOfURL.split("/");  // 0=table, 1=rid
            String table = restParts[0];

            String rid = null;
            if (restParts.length > 1) rid = restParts[1];
            // if table only or *, GET returns all rows in table (with possible filter conditions encoded eg. */dept.name/eq/sales)
            if (httpMethod.equals("GET") && (rid == null || rid.equals("*"))  ) {
                return getTableWithControls(con, parms, table);
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
                    StringBuilder ie = new StringBuilder();
                    if (rid != null && rid.equals("columns")) {
                        addColumn(con, parms, table, ie);
                        return ie.toString() + getTableWithControls(con, parms, table);

                    }
                    // PUT to insert and return the new row 
                    if (insertRow(con, table, parms, ie)) {
                        return getTableRowForm(con, table, parms);
                    } else {
                        System.out.println("Insert errors: "+ie.toString());
                        return ie.toString() + getTableRowForm(con, table, parms);
                    }
                } else if (httpMethod.equals("PATCH")) {
                // PATCH to update and return the updated row
                    StringBuilder ie = new StringBuilder();
                    if (!rid.equals("-")) parms.put("EDIT_ID", rid);  // updateRow needs this
                    if (updateRow(con, table, parms, ie)) {
                        return getTableWithControls(con, parms, table);
                    } else {
                        System.out.println("Insert errors: "+ie.toString());
                        return ie.toString() + getTableRowForm(con, table, parms);
                    }
                } else if (httpMethod.equals("DELETE")) {
                // DELETE to remove the row
                    StringBuilder ie = new StringBuilder();
                    if (!rid.equals("-")) parms.put("EDIT_ID", rid);  // updateRow needs this
                    if(deleteRow(con, table, parms, ie)) {
                        return getTableWithControls(con, parms, table);
                    } else {
                        System.out.println("Delete errors: "+ie.toString());
                        return ie.toString() + getTableRowForm(con, table, parms);
                    }
                }
            }
        }
        Document menuItem = con.get(parms.get("ID"));
        if (menuItem != null && (htmlScript = menuItem.getString("pageScript")) != null) {
            try {
                parms.put("SERVICE",menuItem.getString("name"));
                styleScript = "\n"+menuItem.getString("pageStyle");

                // Will likely allow some templating here but for now, just dump it
                sb.append(htmlScript);

            } catch (Exception e) {
                System.out.println("Error in scriptlet: " + htmlScript + " exception: "+ e.getClass().getName()+" message=" + e.getMessage());
                sb.append("Error in scriptlet: " + parms.get("ID") + " message=" + e.getMessage());
                e.printStackTrace();
            } finally {
            }
        } else {
            //System.err.println("MenuItem or pageScript is null - showing all tables");
            parms.put("HTMX",this.getClass().getName());
            return new Schema().getPage(con,parms);
        }
     //   styleScript += "\n<script src='https://unpkg.com/hyperscript.org@0.9.12'></script>";
     //   styleScript += "\n<script src='https://unpkg.com/htmx.org@1.9.9'></script>";
        return headMinimum(con, parms.get("SERVICE"),styleScript)+bodyMinimum("",sb.toString());
    }

    String getTableWithControls(DatabaseConnection con, HashMap<String,String> parms, String table) {
        return linkHTMX(this.getClass().getName(), "&lt;" + Message.get(con.getLocale(), "ALL_TABLES"))
                + "&nbsp;&nbsp;&nbsp;"
                 + ((Security.getTablePriv(con, table) & PRIV_CREATE) > 0 
                    ? popupFormHTMX("CREATE_NEW_ROW", this.getClass().getName()+"/"+table, "put", Message.get(con.getLocale(), "NEW_ROW"), "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "CREATE_ROW")+" "+makeCamelCasePretty(table))
                        + getTableRowFields(con, table, parms)
                        + submitButton(con.getLocale(), "CREATE_ROW")) 
                    : "")
            + "&nbsp;&nbsp;&nbsp;"
             + (Security.isDBA(con)
                ? newColumnPopup(con, table)
                //+ "&nbsp;&nbsp;&nbsp;"
                //+ popupForm("RIGHTSOPTIONS", null, Message.get(locale, "TABLE_RIGHTS_OPTIONS"), null, "XXX", rightsOptionsForm(con, table, parms, ""))
                //+ "&nbsp;&nbsp;&nbsp;"
                //+ popupForm("ADVANCEDOPTIONS", null, Message.get(locale, "ADVANCED_TABLE_OPTIONS"), null, "NEWCOLUMNNAME", advancedOptionsForm(con, table, parms, ""))
                : "") // isDBA switch
        + br()
        + getTable(con, table);
    }

}
