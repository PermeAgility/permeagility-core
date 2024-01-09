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

import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.arcadedb.database.Document;

public class D3Builder extends Table {

 //   public static String D3_SCRIPT_REF = "<script type='text/javascript' src='/js/d3.min.js'></script>\n";

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        String preview = parms.get("PREVIEW");
        String view = parms.get("VIEW");
        String additionalScript = "";
        String additionalStyle = "";
        
        // Preview view inside D3 Builder
        if (preview != null) {
            Document viewDoc = con.get(preview);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + preview));
            } else {
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.getString("style") != null ? viewDoc.getString("style") : "");
                additionalScript = script(getPlugins(viewDoc));
                String script = (viewDoc.getString("script") != null ? "<script>\n" + viewDoc.getString("script") + "\n</script>\n" : "");
                script = script.replace("$$this$$", "permeagility.plus.d3.Data?VIEW="+preview);
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(chartDiv("chart"));
                sb.append(additionalScript + "\n" + script);
                return head(con, viewDoc.getString("name"), additionalStyle) + body(errors.toString()+sb.toString());
            }
        }

        // D3 Viewer as a standalone component
        if (view != null) {
            if (DEBUG) System.out.println("Building D3 view " + view);
            Document viewDoc = con.get(view);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + view));
            } else {
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.getString("style") != null ? viewDoc.getString("style") : "");
                additionalScript = script(getPlugins(viewDoc));
                String script = (viewDoc.getString("script") != null ? "<script>\n" + viewDoc.getString("script")+"\n</script>\n" : "");
                script = script.replace("$$this$$", "permeagility.plus.d3.Data?VIEW="+view);
                sb.append(paragraph("" + viewDoc.getString("description")));
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(chartDiv("chart"));
                sb.append(additionalScript + "\n" + script);
                return head(con, viewDoc.getString("name"), additionalStyle) + body(errors.toString()+sb.toString());
            }
        }

        String update = processREST(con, parms); // Do table stuff
        return update != null ? update : getTableWithControls(con, parms, PlusSetup.TABLE);  // If REST did nothing - default result
    }

    // The default view when no record specified - the list page
    @Override public String getTableWithControls(DatabaseConnection con, HashMap<String,String> parms, String table) {
    Locale locale = con.getLocale();
    return bodyMinimum(
                ((Security.getTablePriv(con, PlusSetup.TABLE) & Security.PRIV_CREATE) > 0
                ? popupFormHTMX("CREATE_NEW_ROW", this.getClass().getName()+"/"+PlusSetup.TABLE, "put", parms.get("HX-TARGET"), Message.get(locale, "CREATE_ROW"), "NAME",
                        paragraph("banner", Message.get(locale, "CREATE_ROW"))
                        + hidden("TABLENAME", PlusSetup.TABLE)
                        + super.getTableRowFields(con, PlusSetup.TABLE, null, "name,description,-", null)
                        + submitButton(locale, "CREATE_ROW")
                        + POPUP_FORM_CLOSER)
                : "")
                + getTable(con, parms, PlusSetup.TABLE
                    , "(classname is null OR classname = '')"
                    , null, 0, "name,description,-")
                + serviceHeaderUpdateDiv(parms, "D3 Builder")
            );
    }

    @Override
    public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms);
    }

    /**
     * Returns the fields for a table - can be for insert of a new row or update of an existing (as specified by the EDIT_ID in parms)
     */
    @Override
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms) {
        String edit_id = (parms != null ? parms.get("EDIT_ID") : null);
        Document initialValues = null;
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

        String init = initialValues.getString("style");
        if (init == null) init = "<script type='text/javascript' src='/js/d3_v3/d3.min.js'></script>\n" + //
                "<style type='text/css'>\n/* CSS Styles */\n</style>\n\n";
        if (!init.startsWith("<script")) {  // add script tags to support old data
            init = "<script type='text/javascript' src='/js/d3_v3/d3.min.js'></script>\n"
                + "<style type='text/css'>\n"+init+"\n</style>\n\n";
        }
        if (!init.endsWith("\n\n")) init += "\n\n";  // last line can be lost without this
        styleEditor = getCodeEditorControl(formName, PARM_PREFIX + "style", init, "css", null);

        init = initialValues.getString("script");
        if (init == null) init = "// D3 Script "+new Date()+"\n\n";
        if (!init.endsWith("\n\n")) init += "\n\n";  // last line can be lost without this
        scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "script", init, "application/json", null);                    

        init = initialValues.getString("dataScript");
        if (init == null) init = "{\"removethisnote\":\"Data returned by d3.json('$$this$$', function(data) {  });\"}\n\n";
        if (!init.endsWith("\n\n")) init += "\n\n";  // last line can be lost without this
        dataEditor = getCodeEditorControl(formName, PARM_PREFIX + "dataScript", init, "application/json", null);                    

        String saveButton = button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN")
                    , "_=\"on click js \n"                    
                       + "   "+PARM_PREFIX+"styleEditor.save();\n"  // save editor data
                       + "   "+PARM_PREFIX+"scriptEditor.save();\n"
                       + "   "+PARM_PREFIX+"dataScriptEditor.save();\n"
                       + "   var formData = new FormData();\n"    // assemble the formdata
                       + "   formData.append('SUBMIT','UPDATE');\n"
                       + addFormData(formName,"style")
                       + addFormData(formName,"script")
                       + addFormData(formName,"dataScript")
                       + addFormData("name")
                       + addFormData("description")
                       + addFormData("plugins")    // then send path request via fetch
                       + "   fetch('/"+ this.getClass().getName()+"/"+PlusSetup.TABLE+"/"+edit_id +"', { method: 'PATCH', body: formData } ).then(data => {   \n"                        
                       + "      document.getElementById('previewFrame').src='permeagility.plus.d3.D3Builder?PREVIEW="+edit_id+"';\n"
                       + "      document.getElementById('headerservice').innerHTML = document.getElementById('"+PARM_PREFIX+"name').value;\n"
                       + "   });\n"
                       + "end\"\n"
                    );
        //System.out.println("SaveButton="+saveButton);  // for debugging help

        String resultView = 
            (readOnly ? "" : 
                  saveButton
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + popupFormHTMX("UPDATE_NAME", "", "", parms.get("HX-TARGET"), Message.get(con.getLocale(), "DETAILS"), "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", PlusSetup.TABLE)
                        + super.getTableRowFields(con, PlusSetup.TABLE, parms, "name,description,plugins,-")
                        + POPUP_FORM_CLOSER
                )
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            )
            + popupFormHTMX("UPDATE_MORE", this.getClass().getName()+"/"+PlusSetup.TABLE+"/"+edit_id, "patch", parms.get("HX-TARGET"), Message.get(con.getLocale(), "MORE"), "NAME",
                    paragraph("banner", Message.get(con.getLocale(), "MORE"))
                    + hidden("TABLENAME", PlusSetup.TABLE)
                    + (readOnly ? "" : deleteButton(con.getLocale())+"<br>")
                    + submitButton(con.getLocale(), "COPY")
                    + POPUP_FORM_CLOSER
            )
            +"<br>"
            +frame("previewFrame","permeagility.plus.d3.D3Builder?PREVIEW="+edit_id); 

        return div("leftHand","split split-horizontal",div("styleEditor","split content",styleEditor)+div("scriptEditor",scriptEditor))
               +div("rightHand","split split-horizontal",div("dataEditor","split content",dataEditor)+div("resultView",resultView))
               +script("Split(['#leftHand', '#rightHand'], { gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                        + "Split(['#styleEditor', '#scriptEditor'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                        + "Split(['#dataEditor', '#resultView'], { direction: 'vertical', sizes: [20, 95], minSize: [5,90], gutterSize: 8, cursor: 'row-resize' });\n"
                        +"document.getElementById('headerservice').innerHTML = document.getElementById('"+PARM_PREFIX+"name').value;\n"
                );              
    }

    public String addFormData(String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+PARM_PREFIX+name+"').value);\n";
    }
    
    public String addFormData(String formName, String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+formName+PARM_PREFIX+name+"').value);\n";
    }

    public static String getPlugins(Document viewDoc) {
        StringBuilder result = new StringBuilder();
        List<Document> plugins = viewDoc.getList("plugins");
        if (plugins != null) {
            for (Document p : plugins) {
                if (p.has("script")) result.append("\n"+p.getString("script")+"\n");
            }
        }
        return result.toString();
    }

    public static String getAsComponent(DatabaseConnection con, String id) {
        Document viewDoc = con.get(id);
        if (viewDoc != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
           // String additionalStyle = (viewDoc.getString("style") != null ? "<style>" + viewDoc.getString("style")+ "</style>" : "");
            String additionalScript = script(getPlugins(viewDoc));
            String script = (viewDoc.getString("script") != null ? "<script>" + viewDoc.getString("script") + "</script>" : "");
            //script = script.replace("$$this$$", "permeagility.plus.d3.Data?VIEW="+preview);
            sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
            sb.append(chartDiv("chart"));
            sb.append(additionalScript + "\n" + script);
            return  div("component",sb.toString());
        }
        return "N/A no-doc";
    }
}
