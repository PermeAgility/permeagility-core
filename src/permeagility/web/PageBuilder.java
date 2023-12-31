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
public class PageBuilder extends Table {

    public final String TABLE_NAME = "menuItem";
    public final String APP_NAME = "Page Builder";

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
        String update = processREST(con, parms); // Do table stuff
        return update != null ? update : getTableWithControls(con, parms, TABLE_NAME);  // If REST did nothing - default result
    }

    // The default view when no record specified - the list page
    @Override public String getTableWithControls(DatabaseConnection con, HashMap<String,String> parms, String table) {
        Locale locale = con.getLocale();
        return head(con, APP_NAME)
                + bodyMinimum(
                    ((Security.getTablePriv(con, TABLE_NAME) & Security.PRIV_CREATE) > 0
                    ? popupFormHTMX("CREATE_NEW_ROW", this.getClass().getName()+"/"+TABLE_NAME, "put", parms.get("HX-TARGET"), Message.get(locale, "CREATE_ROW"), "NAME",
                            paragraph("banner", Message.get(locale, "CREATE_ROW"))
                            + hidden("TABLENAME", TABLE_NAME)
                            + super.getTableRowFields(con, TABLE_NAME, null, "name,description,useStyleFrom,-", null)
                            + submitButton(locale, "CREATE_ROW")
                            + POPUP_FORM_CLOSER)
                    : "")
                    + getTable(con, parms, TABLE_NAME
                        , "SELECT FROM " + TABLE_NAME+" WHERE name != '' AND (classname is null OR classname = '')"
                        , null, 0, "name,description,useStyleFrom,-")
                    + serviceHeaderUpdateDiv(parms, APP_NAME)
                );
    }
     
    @Override public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms);
    }

    /** Returns the Style and Script editor along with a schema and preview in a split pane 
     *  This is the main page/app
     */
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
        if (initialValues != null) init = initialValues.getString("pageScript");
        if (init == null) init = "<!-- "+new Date()+"\n     by "+con.getUser()+"\n     body contents below -->\n";
        scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "pageScript", init, "htmlmixed", null);

        String resultView =
            (readOnly ? "" :
                button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN"))
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + popupFormHTMX("UPDATE_NAME", "", "", parms.get("HX-TARGET"), Message.get(con.getLocale(), "DETAILS"), "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", TABLE_NAME)
                        + super.getTableRowFields(con, TABLE_NAME, parms, "name,description,useStyleFrom,-")
                        + POPUP_FORM_CLOSER
                )
                +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            )
            + popupFormHTMX("UPDATE_MORE", this.getClass().getName()+"/"+TABLE_NAME+"/"+edit_id, "PATCH", parms.get("HX-TARGET"),Message.get(con.getLocale(), "MORE"),  "NAME",
                    paragraph("banner", Message.get(con.getLocale(), "MORE"))
                    //+ hidden("TABLENAME", TABLE_NAME)
                    + (readOnly ? "" : deleteButton(con.getLocale(),TABLE_NAME, edit_id, parms.get("HX-TARGET")) 
                                      + "<br>" 
                                      + submitButton(con.getLocale(), "COPY")
                                      + POPUP_FORM_CLOSER)
            )
            +"<br>"
            +frame("previewFrame","previewFrame","permeagility.web.Home?ID="+edit_id);

            return div("leftHand","split split-horizontal",div("styleEditor","split split-vertical",styleEditor)+div("scriptEditor","split split-vertical",scriptEditor))
                  +div("rightHand","split split-horizontal",div("resultView",resultView))
                  +script("Split(['#leftHand', '#rightHand'], { direction: 'horizontal', gutterSize: 8, minSize: [5,5], cursor: 'col-resize' });\n"
                           + "Split(['#styleEditor', '#scriptEditor'], { direction: 'vertical', sizes: [50, 50], minSize: [5,5], gutterSize: 8, cursor: 'row-resize' });\n"
                          +(readOnly ? "" :
                        "d3.select('#headerservice').text('"+APP_NAME+": ' + document.getElementById('"+PARM_PREFIX+"name').value);\n"  // set the header service info
                       + "d3.select('#UpdateButton').on('click', function() { \n"  // On click
                       + "   "+PARM_PREFIX+"pageStyleEditor.save();\n"              // get the data
                       + "   "+PARM_PREFIX+"pageScriptEditor.save();\n"
                       + "   var formData = new FormData();\n"
                       + "   formData.append('SUBMIT','UPDATE');\n"                // put into form data
                            + addFormData(formName,"pageStyle")
                            + addFormData(formName,"pageScript")
                            + addFormData("name")
                            + addFormData("description")
                            + addFormData("useStyleFrom")
                            //+ addFormData("_allowRead")                           // send it to be processed
                            // Todo: should convert this to htmx and target errors to a place where they could be seen
                        + "   fetch('/"+this.getClass().getName()+"/"+TABLE_NAME+"/"+edit_id+"', { method: \"PATCH\", body: formData } ).then(data => {   \n"                        
                       + "      d3.select('#previewFrame').attr('src','permeagility.web.Home?ID="+edit_id+"');\n"  // refresh the preview
                        + "      d3.select('#headerservice').text('"+APP_NAME+": ' + document.getElementById('"+PARM_PREFIX+"name').value);\n"  // update the header
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
