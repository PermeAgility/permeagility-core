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
import java.util.Date;
import java.util.Locale;
import com.arcadedb.database.Document;

/**
 * HTML Page Builder - edits and previews pages
 * @author glenn
 */
public class ActionBuilder extends Table {

    public final String TABLE_NAME = "action";
    public final String APP_NAME = "Action Builder";

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
        String update = processREST(con, parms); // Do table stuff
        return update != null ? update : getTableWithControls(con, parms, TABLE_NAME);  // If REST did nothing - default result
    }

      @Override public String getTableWithControls(DatabaseConnection con, HashMap<String,String> parms, String table) {
        Locale locale = con.getLocale();
        return head(con, APP_NAME)
                + bodyMinimum(
                    ((Security.getTablePriv(con, TABLE_NAME) & Security.PRIV_CREATE) > 0
                    ? popupFormHTMX("CREATE_NEW_ROW", this.getClass().getName()+"/"+TABLE_NAME, "put", parms.get("HX-TARGET"), Message.get(locale, "CREATE_ROW"), "NAME",
                            paragraph("banner", Message.get(locale, "CREATE_ROW"))
                            + hidden("TABLENAME", TABLE_NAME)
                            + super.getTableRowFields(con, TABLE_NAME, null, "name,type,table,description,-", null)
                            + submitButton(locale, "CREATE_ROW")
                          )
                    : "")
                    + getTable(con, parms, TABLE_NAME
                        , "(classname is null OR classname = '')"
                        , null, 0, "name,table,type,description,-")
                    + serviceHeaderUpdateDiv(parms, APP_NAME)
                );
    }
     
    @Override public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms);
    }

    /** Returns the Style and Script editor along with a schema and preview in a split pane */
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
    //        readOnly = Security.isReadOnlyDocument(con, initialValues);
        }
        String styleEditor = "";
        String scriptEditor = "";
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");

        String init = null;
        if (initialValues != null) init = initialValues.getString("pageStyle");
        if (init == null) init = "<style type='text/css'>\n/* CSS Styles in here */\n\n</style>\n";
        styleEditor = getCodeEditorControl(formName, PARM_PREFIX + "pageStyle", init, "css", null);

        init = null;
        if (initialValues != null) init = initialValues.getString("actionScript");
        if (init == null) init = "/* "+new Date()+"\n     by "+con.getUser()+"\n     code contents below */\n";
        scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "actionScript", init, "groovy", null);

        String saveButton = button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN")
            , "_=\"on click js \n"                    
            + "   "+PARM_PREFIX+"pageStyleEditor.save();\n"              // get the data
            + "   "+PARM_PREFIX+"actionScriptEditor.save();\n"
            + "   var formData = new FormData();\n"
            + "   formData.append('SUBMIT','UPDATE');\n"                // put into form data
            // + addFormData(formName,"pageStyle")
             + addFormData(formName,"actionScript")
             + addFormData("name")
             + addFormData("description")
             + addFormData("type")
             + addFormData("table")
       //      + addFormData("_allowRead")                           
       //      + addFormData("_allow")                           // send it to be processed
           // then send path request via fetch
           + "   fetch('/"+ this.getClass().getName()+"/"+TABLE_NAME+"/"+edit_id +"', { method: 'PATCH', body: formData } ).then(data => {   \n"                        
           + "      document.getElementById('previewFrame').src='permeagility.web.Home?ID="+edit_id+"';\n"
           + "      document.getElementById('headerservice').innerHTML = '"+APP_NAME+": ' + document.getElementById('"+PARM_PREFIX+"name').value;\n"
           + "   });\n"
           + "end\"\n"
        );

        String resultView =
            (readOnly ? "" :
                saveButton
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + popupFormHTMX("UPDATE_NAME", "", "", parms.get("HX-TARGET"), Message.get(con.getLocale(), "DETAILS"), "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", TABLE_NAME)
                        + super.getTableRowFields(con, TABLE_NAME, parms, 
                        "name,type,table,description,useStyleFrom,_allowRead,_allow,-")
                )
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            )
            + popupFormHTMX("UPDATE_MORE", this.getClass().getName()+"/"+TABLE_NAME+"/"+edit_id, "PATCH", parms.get("HX-TARGET"),Message.get(con.getLocale(), "MORE"),  "NAME",
                    paragraph("banner", Message.get(con.getLocale(), "MORE"))
                    + hidden("TABLENAME", TABLE_NAME)
                    + (readOnly ? "" : deleteButton(con.getLocale(),TABLE_NAME, edit_id, parms.get("HX-TARGET")) 
                                      + "<br>" 
                                      + submitButton(con.getLocale(), "COPY")
                                      )
            )
            +"<br>"
            +frame("previewFrame","previewFrame","permeagility.web.Home?ID="+edit_id);

            return div("leftHand","split split-horizontal",
                div("styleEditor","split split-vertical",styleEditor)
                +div("scriptEditor","split split-vertical",scriptEditor)
            )
            +div("rightHand","split split-horizontal noscroll",div("resultView",resultView))
            +script("Split(['#leftHand', '#rightHand'], { direction: 'horizontal', gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                    + "Split(['#styleEditor', '#scriptEditor'], { direction: 'vertical', sizes: [50, 50], minSize: [5,5], gutterSize: 8, cursor: 'row-resize' });\n"
                    + "document.getElementById('headerservice').innerHTML = '"+APP_NAME+": ' + document.getElementById('"+PARM_PREFIX+"name').value;\n"
            );
    }

    public String addFormData(String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+PARM_PREFIX+name+"').value);\n";
    }
    public String addFormData(String formName, String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+formName+PARM_PREFIX+name+"').value);\n";
    }

}
