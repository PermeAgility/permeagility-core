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
package permeagility.plus.d3;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Table;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class D3Builder extends Table {

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        String preview = parms.get("PREVIEW");
        String view = parms.get("VIEW");
        String tableName = PlusSetup.TABLE;
        String additionalScript = "";
        String additionalStyle = "";
        String serviceStyleOverride = "#service { top: 0px !important; left: 0px !important; }";

        // If there was an update do it
        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) { return update; }
        
        if (preview != null) {
            ODocument viewDoc = con.get(preview);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + preview));
            } else {
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.field("style") != null ? "<style>" + viewDoc.field("style")+ serviceStyleOverride + "</style>" : "");
                additionalScript = script(getPlugins(viewDoc));
                String script = (viewDoc.field("script") != null ? "<script>" + viewDoc.field("script") + "</script>" : "");
                script = script.replace("$$this$$", "/permeagility.plus.d3.Data?VIEW="+preview);
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(additionalScript + script);
                return head("D3 Builder", getScripts(con) + additionalStyle) + body(errors.toString()+div("service",sb.toString()));
            }
        }
        
        // Standard D3 Viewer
        if (view != null) {
            if (DEBUG) System.out.println("Building D3 view " + view);
            ODocument viewDoc = con.get(view);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + view));
            } else {
                parms.put("SERVICE", viewDoc.field("name"));
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.field("style") != null ? "<style>\n" + viewDoc.field("style") + "</style>\n" : "");
                additionalScript = script(getPlugins(viewDoc));
                String script = (viewDoc.field("script") != null ? "<script>\n" + viewDoc.field("script")+"\nd3.select('#service').attr('top','0px');" + "</script>\n" : "");
                script = script.replace("$$this$$", "/permeagility.plus.d3.Data?VIEW="+view);
                sb.append(paragraph("" + viewDoc.field("description")));
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(additionalScript + script);
            }
        }
        
        // If nothing else happened, show the list of R Scripts owned by the user
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "D3 Builder");
                sb.append(getTable(con, parms, PlusSetup.TABLE, "SELECT FROM " + PlusSetup.TABLE+" WHERE _allow contains(name='"+con.getUser()+"')", null, 0, "button_VIEW_View,name,description,-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving D3 Script table: " + e.getMessage());
            }
        }
        
        // Return the default result
        return head("D3 Builder", getScripts(con) + additionalStyle)
                + body(standardLayout(con, parms,
                    ((Security.getTablePriv(con, PlusSetup.TABLE) & PRIV_CREATE) > 0 && view == null
                    ? popupForm("CREATE_NEW_ROW", null, Message.get(locale, "CREATE_ROW"), null, "NAME",
                            paragraph("banner", Message.get(locale, "CREATE_ROW"))
                            + hidden("TABLENAME", PlusSetup.TABLE)
                            + super.getTableRowFields(con, PlusSetup.TABLE, parms, "name,description,-")
                            + submitButton(locale, "CREATE_ROW"))
                    : "")
                    + errors.toString()
                    + sb.toString()
         ));
    }

    @Override
    public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms, null);
    }

    /**
     * Returns the fields for a table - can be for insert of a new row or update of an existing (as specified by the EDIT_ID in parms)
     */
    @Override
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride) {
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
        String dataEditor = "";
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");

        String init = initialValues.field("style");
        if (init == null) init = "/* CSS Styles */\n";
        styleEditor = getCodeEditorControl(formName, PARM_PREFIX + "style", init, "css");

        init = initialValues.field("script");
        if (init == null) init = "// D3 Script "+new Date()+"\n";
        scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "script", init, "application/json");                    

        init = initialValues.field("dataScript");
        if (init == null) init = "{\"removethisnote\":\"Data returned by d3.json('$$this$$', function(data) {  });\"}\n";
        dataEditor = getCodeEditorControl(formName, PARM_PREFIX + "dataScript", init, "application/json");                    

        String resultView = 
            (readOnly ? "" :
                button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN"))
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + popupBox("UPDATE_NAME", null, Message.get(con.getLocale(), "DETAILS"), null, "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", PlusSetup.TABLE)
                        + super.getTableRowFields(con, PlusSetup.TABLE, parms, "name,description,plugins,-")
                )
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            )
            + popupForm("UPDATE_MORE", null, Message.get(con.getLocale(), "MORE"), null, "NAME",
                    paragraph("banner", Message.get(con.getLocale(), "MORE"))
                    + hidden("TABLENAME", PlusSetup.TABLE)
                    + (readOnly ? "" : deleteButton(con.getLocale())+"<br>")
                    + submitButton(con.getLocale(), "COPY")
            )
            +"<br>"+frame("previewFrame","/permeagility.plus.d3.D3Builder?PREVIEW="+edit_id);  //<iframe id='previewFrame' width='100%' height='100%'></iframe>\n";

        return getSplitScript()
               +div("leftHand","split split-horizontal",div("styleEditor","split content",styleEditor)+div("scriptEditor",scriptEditor))
               +div("rightHand","split split-horizontal",div("dataEditor","split content",dataEditor)+div("resultView",resultView))
               +script("Split(['#leftHand', '#rightHand'], { gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                        + "Split(['#styleEditor', '#scriptEditor'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                        + "Split(['#dataEditor', '#resultView'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                       +(readOnly ? "" :
                        "d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "d3.select('#UpdateButton').on('click', function() { \n"
                       + "   "+PARM_PREFIX+"styleEditor.save();\n"
                       + "   "+PARM_PREFIX+"scriptEditor.save();\n"
                       + "   "+PARM_PREFIX+"dataScriptEditor.save();\n"
                       + "   var formData = new FormData();\n"
                       + "   formData.append('SUBMIT','UPDATE');\n"
                            + addFormData("style")
                            + addFormData("script")
                            + addFormData("dataScript")
                            + addFormData("name")
                            + addFormData("description")
                            + addFormData("plugins")
                       + "   d3.xhr('').post(formData, function(error,data) {   \n"                        
                       + "      d3.select('#previewFrame').attr('src','/permeagility.plus.d3.D3Builder?PREVIEW="+edit_id+"');\n"
                       + "      d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "   });\n"
                       + "});\n")
                );
    }

    public String addFormData(String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+PARM_PREFIX+name+"').value);\n";
    }
    
    public String getPlugins(ODocument viewDoc) {
        StringBuilder result = new StringBuilder();
        List<ODocument> plugins = viewDoc.field("plugins");
        if (plugins != null) {
            for (ODocument p : plugins) {
                if (p.containsField("script")) result.append("\n"+p.field("script")+"\n");
            }
        }
        return result.toString();
    }
    
}
