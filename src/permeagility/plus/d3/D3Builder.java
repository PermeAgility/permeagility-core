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

import com.orientechnologies.orient.core.metadata.schema.OProperty;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Table;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Collection;
import java.util.Locale;
import permeagility.util.QueryResult;
import static permeagility.web.Table.DEBUG;
import static permeagility.web.Weblet.hidden;
import static permeagility.web.Weblet.paragraph;

public class D3Builder extends Table {

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        String submit = parms.get("SUBMIT");
        String preview = parms.get("PREVIEW");
        String view = parms.get("VIEW");
        String tableName = PlusSetup.TABLE;
        String additionalScript = "";
        String additionalStyle = "";
        String serviceStyleOverride = "#service { top: 0px !important; left: 0px !important; }";

        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) { return update; }
        
        if (preview != null) {
            ODocument viewDoc = con.get(preview);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + preview));
            } else {
                if (parms.get("DATA") != null) {
                    String sampleData = viewDoc.field("sampleData");
                    return sampleData;
                }
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.field("style") != null ? "<style>" + viewDoc.field("style")+ serviceStyleOverride + "</style>" : "");
                additionalScript = (viewDoc.field("pluginScript") != null ? "<script>" + viewDoc.field("pluginScript") + "</script>" : "");
                String script = (viewDoc.field("script") != null ? "<script>" + viewDoc.field("script") + "</script>" : "");
                script = script.replace("$$this$$", "/permeagility.plus.d3.Data?VIEW="+preview);
                //sb.append(paragraph("" + viewDoc.field("description")));
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(additionalScript + script);
                return head("D3 Builder", getScripts(con) + additionalStyle) + body(errors.toString()+div("service",sb.toString()));
            }
        }
        
        if (view != null) {
            System.out.println("Building D3 view " + view);
            ODocument viewDoc = con.get(view);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + view));
            } else {
                parms.put("SERVICE", viewDoc.field("name"));
                if (parms.get("DATA") != null) {
                    String sampleData = viewDoc.field("sampleData");
                    return sampleData;
                }
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.field("style") != null ? "<style>\n" + viewDoc.field("style") + "</style>\n" : "");
                additionalScript = (viewDoc.field("pluginScript") != null ? "<script>\n" + viewDoc.field("pluginScript") + "</script>\n" : "");
                String script = (viewDoc.field("script") != null ? "<script>\n" + viewDoc.field("script")+"\nd3.select('#service').attr('top','0px');" + "</script>\n" : "");
                script = script.replace("$$this$$", "/permeagility.plus.d3.Data?VIEW="+view);
                sb.append(paragraph("" + viewDoc.field("description")));
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(additionalScript + script);
            }
        }
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "D3 Builder");
                sb.append(getTable(con, parms, PlusSetup.TABLE, "SELECT FROM " + PlusSetup.TABLE, null, 0, "button_VIEW_View,name,description,-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving D3 Script table: " + e.getMessage());
            }
        }
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
            QueryResult initrows = con.query("SELECT FROM #" + edit_id);
            if (initrows != null && initrows.size() == 1) {
                initialValues = initrows.get(0);
            } else {
                if (DEBUG) {
                    System.out.println("Error in permeagility.web.Table:getTableRowForm: Only one row may be returned by ID for editing rows=" + initrows.size());
                }
                return paragraph("error", Message.get(con.getLocale(), "ONLY_ONE_ROW_CAN_BE_EDITED"));
            }
        } else {
            if (DEBUG) {
                System.out.println("getTableRowFields: No EDIT_ID specified");
            }
        }
        String styleEditor = "";
        String scriptEditor = "";
        String dataEditor = "";
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
        Collection<OProperty> columns = con.getColumns(table, columnOverride);
        if (columns != null) {
            for (OProperty column : columns) {
                String name = column.getName();
                    if (column.getName().equals("style")) {
                        String init = initialValues.field(name);
                        if (init == null) init = "/* CSS Styles */\n";
                        styleEditor = getCodeEditorControl(formName, PARM_PREFIX + name, init, "css");
                    }
                    if (column.getName().equals("script")) {
                        String init = initialValues.field(name);
                        if (init == null) init = "// D3 Script\n";
                        scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + name, init, "application/json");                    
                    }
                    if (column.getName().equals("dataScript")) {
                        String init = initialValues.field(name);
                        if (init == null) init = "{\"removethisnote\":\"Data returned by d3.json('$$this$$', function(data) {  });\"}\n";
                       dataEditor = getCodeEditorControl(formName, PARM_PREFIX + name, init, "application/json");                    
                    }
            }
            String resultView = button("UpdateButton", "UPDATEBUTTON","UPDATE","Save/Run")
                    +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                    + popupBox("UPDATE_NAME", null, Message.get(con.getLocale(), "DETAILS"), null, "NAME",
                            paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                            + hidden("TABLENAME", PlusSetup.TABLE)
                            + super.getTableRowFields(con, PlusSetup.TABLE, parms, "name,description,-")
                    )
                    +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                    + popupForm("UPDATE_MORE", null, Message.get(con.getLocale(), "MORE"), null, "NAME",
                            paragraph("banner", Message.get(con.getLocale(), "MORE"))
                            + hidden("TABLENAME", PlusSetup.TABLE)
                            + deleteButton(con.getLocale())+"<br>"
                            + submitButton(con.getLocale(), "COPY")
                    )
                    +"<br><iframe id='previewFrame' width='100%' height='100%'></iframe>\n";

            return getSplitScript()
                   +div("leftHand","split split-horizontal",div("styleEditor","split content",styleEditor)+div("scriptEditor",scriptEditor))
                   +div("rightHand","split split-horizontal",div("dataEditor","split content",dataEditor)+div("resultView",resultView))
                   +script("Split(['#leftHand', '#rightHand'], { gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                            + "Split(['#styleEditor', '#scriptEditor'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                            + "Split(['#dataEditor', '#resultView'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                           + "d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                           + "d3.select('#previewFrame').attr('src','/permeagility.plus.d3.D3Builder?PREVIEW="+edit_id+"');\n"
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
                           //+ "formData.append('EDIT_ID','"+edit_id+"');\n"  // its already there
                           + "   d3.xhr('').post(formData, function(error,data) {   \n"                        
                           + "      d3.select('#previewFrame').attr('src','/permeagility.plus.d3.D3Builder?PREVIEW="+edit_id+"');\n"
                           + "      d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                           + "   });\n"
                           + "});\n"
                    );
        } else {
            return null;
        }
    }

    public String addFormData(String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+PARM_PREFIX+name+"').value);\n";
    }
}
