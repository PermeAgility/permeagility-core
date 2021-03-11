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

import java.util.HashMap;

import permeagility.util.DatabaseConnection;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Date;
import java.util.Locale;

/**
 * JavaScript Page Builder - edits and previews pages
 * @author glenn
 */
public class PageBuilder extends Table {

    public final String TABLE_NAME = "menuItem";

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        //String view = parms.get("VIEW");
        String additionalStyle = "";

        // If there was an update do it
        String update = processSubmit(con, parms, TABLE_NAME, errors);
        if (update != null) { return update; }

        // If nothing else happened, show the list of Scripts owned by the user
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "Page Builder");
                sb.append(getTable(con, parms, TABLE_NAME, "SELECT FROM " + TABLE_NAME+" WHERE name != '' AND (classname is null OR classname = '') AND _allow contains(name='"+con.getUser()+"')", null, 0, "name,description,-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving Script table: " + e.getMessage());
            }
        }

        return head("Page Builder", getScripts(con) + additionalStyle)
                + body(standardLayout(con, parms,
                    ((Security.getTablePriv(con, TABLE_NAME) & PRIV_CREATE) > 0
                    ? popupForm("CREATE_NEW_ROW", null, Message.get(locale, "CREATE_ROW"), null, "NAME",
                            paragraph("banner", Message.get(locale, "CREATE_ROW"))
                            + hidden("TABLENAME", TABLE_NAME)
                            + super.getTableRowFields(con, TABLE_NAME, parms, "name,description,-")
                            + submitButton(locale, "CREATE_ROW"))
                    : "")
                    + errors.toString()
                    + sb.toString()
         ));
    }

    @Override public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms, null);
    }

    /** Returns the Style and Script editor along with a schema and preview in a split pane  */
    @Override public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride) {
        String edit_id = (parms != null ? parms.get("EDIT_ID") : null);
        ODocument initialValues = null;
        if (edit_id != null) {
            initialValues = con.get(edit_id);
            if (initialValues == null) {
                return paragraph("error", Message.get(con.getLocale(), "ERROR_IN_QUERY",edit_id));
            }
        }
        boolean readOnly = false;  // Assume a new doc
        if (initialValues != null) {
            readOnly = Security.isReadOnlyDocument(con, initialValues);
        }
        String styleEditor = "";
        String scriptEditor = "";
        String browser = "";
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");

        String init = initialValues.field("pageStyle");
        if (init == null) init = "/* CSS Styles */\n";
        styleEditor = getCodeEditorControl(formName, PARM_PREFIX + "pageStyle", init, "css");

        init = initialValues.field("pageScript");
        if (init == null) init = "// (con,parms) return page - written "+new Date()+" by "+con.getUser()+"\n";
        scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "pageScript", init, "application/json");

        browser = frame("browserFrame","permeagility.web.Schema");

        String resultView =
            (readOnly ? "" :
                button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN"))
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + popupBox("UPDATE_NAME", null, Message.get(con.getLocale(), "DETAILS"), null, "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", TABLE_NAME)
                        + super.getTableRowFields(con, TABLE_NAME, parms, "name,description,_allowRead,-")
                )
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            )
            + popupForm("UPDATE_MORE", null, Message.get(con.getLocale(), "MORE"), null, "NAME",
                    paragraph("banner", Message.get(con.getLocale(), "MORE"))
                    + hidden("TABLENAME", TABLE_NAME)
                    + (readOnly ? "" : deleteButton(con.getLocale())+"<br>")
                    + submitButton(con.getLocale(), "COPY")
            )
            +"<br>"+frame("previewFrame","permeagility.web.Scriptlet?LAYOUT=none&ID="+edit_id);

        return getSplitScript()
               +div("leftHand","split split-horizontal",div("styleEditor","split content",styleEditor)+div("scriptEditor",scriptEditor))
               +div("rightHand","split split-horizontal",div("dataEditor","split content",browser)+div("resultView",resultView))
               +script("Split(['#leftHand', '#rightHand'], { gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                        + "Split(['#styleEditor', '#scriptEditor'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                        + "Split(['#dataEditor', '#resultView'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                       +(readOnly ? "" :
                        "d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "d3.select('#UpdateButton').on('click', function() { \n"
                       + "   "+PARM_PREFIX+"pageStyleEditor.save();\n"
                       + "   "+PARM_PREFIX+"pageScriptEditor.save();\n"
                       + "   var formData = new FormData();\n"
                       + "   formData.append('SUBMIT','UPDATE');\n"
                            + addFormData(formName,"pageStyle")
                            + addFormData(formName,"pageScript")
                            + addFormData("name")
                            + addFormData("description")
                            + addFormData("_allowRead")
                       + "   d3.xhr('').post(formData, function(error,data) {   \n"
                       + "      d3.select('#previewFrame').attr('src','permeagility.web.Scriptlet?LAYOUT=none&ID="+edit_id+"');\n"
                       + "      d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "   });\n"
                       + "});\n")
                );
    }

    public String addFormData(String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+PARM_PREFIX+name+"').value);\n";
    }

    public String addFormData(String formName, String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+formName+PARM_PREFIX+name+"').value);\n";
    }


}
