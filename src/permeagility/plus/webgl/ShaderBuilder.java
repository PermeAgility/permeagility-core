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
package permeagility.plus.webgl;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import com.arcadedb.database.Document;

import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Table;

/**
 * Shader Builder - edits and previews pages using webgl and twgl.js [optional]
 * @author glenn
 */
public class ShaderBuilder extends Table {

    public final String TABLE_NAME = "shader";
    
    @Override public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();
        String preview = parms.get("PREVIEW");

        // If there was an update do it
        String update = processSubmit(con, parms, TABLE_NAME, errors);
        if (update != null) { return update; }
        
        // If preview, return the assembled result page (without editors)
        if (preview != null) {
            Document viewDoc = con.get(preview);          
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve shader details using " + preview));
            } else {
                StringBuilder usesScripts = new StringBuilder();
                Map<String,Object> uses = viewDoc.getMap("usesShader");
                if (uses != null && uses.size() > 0) {
                    for (String refName : uses.keySet()) {
                        if (DEBUG) System.out.println("Including shader: "+refName);
                        if (viewDoc.getString("vertexScript") != null) usesScripts.append("<script id=\""+refName.substring(1,refName.length()-1)+"-vs\" type=\"x-shader/x-vertex\">" + ((Document)uses.get(refName)).getString("vertexScript") + "</script>\n");
                        if (viewDoc.getString("fragmentScript") != null) usesScripts.append("<script id=\""+refName.substring(1,refName.length()-1)+"-fs\" type=\"x-shader/x-fragment\">" + ((Document)uses.get(refName)).getString("fragmentScript") + "</script>\n");
                    }
                }
                String vertexScript = (viewDoc.getString("vertexScript") != null ? "<script id=\"vs\" type=\"x-shader/x-vertex\">" + viewDoc.getString("vertexScript") + "</script>\n" : "");
                String fragmentScript = (viewDoc.getString("fragmentScript") != null ? "<script id=\"fs\" type=\"x-shader/x-fragment\">" + viewDoc.getString("fragmentScript") + "</script>\n" : "");
                String script = (viewDoc.getString("testScript") != null ? "<script>" + SHADER_CONTROLS_SCRIPT + "\n" + viewDoc.getString("testScript") + "</script>" : "");
                return head(con, "Shader Builder", getScript("twgl-full.min.js") + getScript("chroma.min.js") + getScript("audiostreamsource.min.js")) 
                        + body("<canvas id=\"c\" style=\"width: 100vw; height: calc(100vh - 30px);\"></canvas>") 
                        + usesScripts.toString() + vertexScript + fragmentScript + script;
            }
        }

        // If nothing else happened, show the list of Scripts owned by the user
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "Shader Builder");
                sb.append(getTable(con, parms, TABLE_NAME, null, null, 0, "button_VIEW_View,name,description,-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving Script table: " + e.getMessage());
            }
        }
        
        // Return the default page
        return head(con, "Shader Builder", "" )
                + body(standardLayout(con, parms,
                    ((Security.getTablePriv(con, TABLE_NAME) & Security.PRIV_CREATE) > 0
                    ? popupForm("CREATE_NEW_ROW", null, Message.get(locale, "CREATE_ROW"), null, "NAME",
                            paragraph("banner", Message.get(locale, "CREATE_ROW"))
                            + hidden("TABLENAME", TABLE_NAME)
                            + super.getTableRowFields(con, TABLE_NAME, parms, "name,description,usesShader,-")
                            + submitButton(locale, "CREATE_ROW"))
                    : "")
                    + errors.toString()
                    + sb.toString()
         ));
    }

    @Override public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms);
    }

    /** Returns the default row editor page with Script editors and preview in a split panel  */
    @Override public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms) {
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
        String vertexEditor = "";
        String testScriptEditor = "";
        String fragmentEditor = "";
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");

        String init = initialValues.getString("vertexScript");
        if (init == null) init = "/* Vertex Shader */\n";
        vertexEditor = getCodeEditorControl(formName, PARM_PREFIX + "vertexScript", init, "text/x-csrc", null);

        init = initialValues.getString("fragmentScript");
        if (init == null) init = "/* Fragment Shader "+new Date()+" by "+con.getUser()+"*/\n";
        fragmentEditor = getCodeEditorControl(formName, PARM_PREFIX + "fragmentScript", init, "text/x-csrc", null);                    

        init = initialValues.getString("testScript");
        if (init == null) init = "/* Shader Test "+new Date()+" by "+con.getUser()+"*/\n";
        testScriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "testScript", init, "application/javascript", null);                    


        String resultView = 
            (readOnly ? "" :
                button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN"))
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + popupBox("UPDATE_NAME", null, Message.get(con.getLocale(), "DETAILS"), null, "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", TABLE_NAME)
                        + super.getTableRowFields(con, TABLE_NAME, parms, "name,description,usesShader,-")
                )
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            )
            + popupForm("UPDATE_MORE", null, Message.get(con.getLocale(), "MORE"), null, "NAME",
                    paragraph("banner", Message.get(con.getLocale(), "MORE"))
                    + hidden("TABLENAME", TABLE_NAME)
                    + (readOnly ? "" : deleteButton(con.getLocale())+"<br>")
                    + submitButton(con.getLocale(), "COPY")
            )
            +"<br>"+frame("previewFrame","permeagility.plus.webgl.ShaderBuilder?PREVIEW="+edit_id);

        return div("leftHand","split split-horizontal",div("vertexEditor","split content",vertexEditor)+div("testScriptEditor",testScriptEditor))
               +div("rightHand","split split-horizontal",div("fragmentEditor","split content",fragmentEditor)+div("resultView",resultView))
               +script("Split(['#leftHand', '#rightHand'], { gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                        + "Split(['#vertexEditor', '#testScriptEditor'], { direction: 'vertical', sizes: [50, 50], minSize: [10,10], gutterSize: 8, cursor: 'row-resize' });\n"
                        + "Split(['#fragmentEditor', '#resultView'], { direction: 'vertical', sizes: [50, 50], minSize: [10,10], gutterSize: 8, cursor: 'row-resize' });\n"
                       +(readOnly ? "" :
                        "d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "d3.select('#UpdateButton').on('click', function() { \n"
                       + "   "+PARM_PREFIX+"vertexScriptEditor.save();\n"  
                       + "   "+PARM_PREFIX+"fragmentScriptEditor.save();\n"
                       + "   "+PARM_PREFIX+"testScriptEditor.save();\n"
                       + "   var formData = new FormData();\n"
                       + "   formData.append('SUBMIT','UPDATE');\n"
                            + addFormData(formName,"vertexScript")
                            + addFormData(formName,"fragmentScript")
                            + addFormData(formName,"testScript")
                            + addFormData("name")
                            + addFormData("description")
                            + addFormData("usesShader")
                       + "   d3.xhr('').post(formData, function(error,data) {   \n"                        
                       + "      d3.select('#previewFrame').attr('src','permeagility.plus.webgl.ShaderBuilder?PREVIEW="+edit_id+"');\n"  // Refresh preview
                       + "      d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"  // Update name (if changed)
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
 
    public static String SHADER_CONTROLS_SCRIPT = 
        "// Add Shader controls - call SHADER_CONTROLS(myUpdateFunction) in your update function\n" +
        "var SHADER_STOP = false;\n" +
        "var SHADER_STEP = false;\n" +
        "var SHADER_SLOW = false;\n" +
        "var SHADER_DEFAULT_INTERVAL = 250;\n" +
        "var SHADER_SLOW_INTERVAL;\n" +
        "var SHADER_LOOP_FUNCTION;  // Set when shader loop is called\n" +
        "const SHADER_CONTROLS = (fun) => {\n" +
        "  if (SHADER_SLOW_INTERVAL == undefined) {\n" +
        "    var butdiv = document.createElement(\"div\");\n" +
        "    butdiv.setAttribute(\"style\",\"position: absolute; top: 0;\");\n" +
        "    function createButton(name, code) {\n" +
        "      var but = document.createElement(\"button\");\n" +
        "      but.setAttribute(\"onClick\",code);\n" +
        "      but.appendChild(document.createTextNode(name));\n" +
        "      butdiv.appendChild(but);\n" +
        "    }\n" +
        "    createButton(\"Stop\",\"SHADER_STOP = true;\");\n" +
        "    createButton(\"Go\",\"SHADER_STOP = false;  SHADER_SLOW = false;  SHADER_LOOP_FUNCTION();\");\n" +
        "    createButton(\"Step\",\"SHADER_SLOW = false; SHADER_STOP = false; SHADER_STEP = true; SHADER_LOOP_FUNCTION();\");\n" +
        "    createButton(\"Slow\",\"SHADER_SLOW = true; SHADER_SLOW_INTERVAL.value = \"+SHADER_DEFAULT_INTERVAL+\"; SHADER_STOP = false; SHADER_LOOP_FUNCTION();\");\n" +
        "    SHADER_SLOW_INTERVAL = document.createElement(\"input\");\n" +
        "    SHADER_SLOW_INTERVAL.setAttribute(\"type\",\"range\");\n" +
        "    SHADER_SLOW_INTERVAL.setAttribute(\"min\",0);\n" +
        "    SHADER_SLOW_INTERVAL.setAttribute(\"max\",2000);\n" +
        "    SHADER_SLOW_INTERVAL.setAttribute(\"value\",SHADER_DEFAULT_INTERVAL);\n" +
        "    butdiv.appendChild(SHADER_SLOW_INTERVAL);\n" +
        "    document.body.appendChild(butdiv);\n" +
        "  }\n" +
        "  SHADER_LOOP_FUNCTION = fun;\n" +
        "  if (!SHADER_STOP)  \n" +
        "    if (SHADER_SLOW) {\n" +
        "      window.setTimeout(function() { requestAnimationFrame(fun); } , SHADER_SLOW_INTERVAL.value);\n" +
        "    } else {" +
        "      if (SHADER_STEP) {" +
        "        SHADER_STOP = true;\n" +
        "        SHADER_STEP = false;\n" +
        "      }\n" +
        "      requestAnimationFrame(fun);\n" +
        "   }" +
        "};";
 
    
    public static String getAsComponent(DatabaseConnection con, String id) {
        Document viewDoc = con.get(id);          
        if (viewDoc == null) {
            System.out.println(paragraph("Could not retrieve shader details using " + id));
        } else {
            StringBuilder usesScripts = new StringBuilder();
            Map<String,Object> uses = viewDoc.getMap("usesShader");
            if (uses != null && uses.size() > 0) {
                for (String refName : uses.keySet()) {
                    if (DEBUG) System.out.println("Including shader: "+refName);
                    if (viewDoc.getString("vertexScript") != null) usesScripts.append("<script id=\""+refName.substring(1,refName.length()-1)+"-vs\" type=\"x-shader/x-vertex\">" + ((Document)uses.get(refName)).getString("vertexScript") + "</script>\n");
                    if (viewDoc.getString("fragmentScript") != null) usesScripts.append("<script id=\""+refName.substring(1,refName.length()-1)+"-fs\" type=\"x-shader/x-fragment\">" + ((Document)uses.get(refName)).getString("fragmentScript") + "</script>\n");
                }
            }
            String vertexScript = (viewDoc.getString("vertexScript") != null ? "<script id=\"vs\" type=\"x-shader/x-vertex\">" + viewDoc.getString("vertexScript") + "</script>\n" : "");
            String fragmentScript = (viewDoc.getString("fragmentScript") != null ? "<script id=\"fs\" type=\"x-shader/x-fragment\">" + viewDoc.getString("fragmentScript") + "</script>\n" : "");
            String script = (viewDoc.getString("testScript") != null ? "<script>" + SHADER_CONTROLS_SCRIPT + "\n" + viewDoc.getString("testScript") + "</script>" : "");
            return getScript("twgl-full.min.js") + getScript("chroma.min.js") + getScript("audiostreamsource.min.js")
                    + "<canvas id=\"c\" style=\"width: 100vw; height: calc(100vh - 30px);\"></canvas>" 
                    + usesScripts.toString() + vertexScript + fragmentScript + script;
        }
        return "N/A no-doc";
    }

}
