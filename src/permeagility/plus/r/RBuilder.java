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
package permeagility.plus.r;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Table;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Locale;

public class RBuilder extends Table {

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        String submit = parms.get("SUBMIT");
        String view = parms.get("VIEW");
        String tableName = PlusSetup.TABLE;
        String editId = parms.get("EDIT_ID");
        String updateId = parms.get("UPDATE_ID");
        String additionalScript = "";
        String additionalStyle = "";

        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) {
            return update;
        }
        
        if (view != null) {
            System.out.println("Build view " + view);
            ODocument viewDoc = con.get(view);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve view details using " + view));
            } else {
                if (parms.get("DATA") != null) {
                    String sampleData = viewDoc.field("sampleData");
                    return sampleData;
                }
                parms.put("SERVICE", "Viewing:" + viewDoc.field("name"));
                sb.append("<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>");
                additionalStyle = (viewDoc.field("style") != null ? "<style>\n" + viewDoc.field("style") + "</style>\n" : "");
                additionalScript = (viewDoc.field("pluginURL") != null ? "<script type=\"text/javascript\" src=\"" + viewDoc.field("pluginFiles") + "\"></script>\n" : "");
                String script = (viewDoc.field("script") != null ? "<script>\n" + viewDoc.field("script") + "</script>\n" : "");

                sb.append(paragraph("" + viewDoc.field("description")));
                sb.append(form("svgform", hidden("format", "") + hidden("data", "")));
                sb.append(additionalScript + script);
            }
        }
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "View: Setup view");
                sb.append(paragraph("banner", "Select View"));
                sb.append(getTable(con, parms, PlusSetup.TABLE, "SELECT FROM " + PlusSetup.TABLE, null, 0, "name, description, button(VIEW:View),-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving import patterns: " + e.getMessage());
            }
        }
        return head("D3 Builder", getScripts(con) + additionalStyle)
                + body(standardLayout(con, parms,
                    ((Security.getTablePriv(con, PlusSetup.TABLE) & PRIV_CREATE) > 0 && view == null
                    ? popupForm("CREATE_NEW_ROW", null, Message.get(con.getLocale(), "CREATE_ROW"), null, "NAME",
                            paragraph("banner", Message.get(con.getLocale(), "CREATE_ROW"))
                            + hidden("TABLENAME", PlusSetup.TABLE)
                            + getTableRowFields(con, PlusSetup.TABLE, parms)
                            + submitButton(con.getLocale(), "CREATE_ROW"))
                    : "")
                    + errors.toString()
                    + sb.toString()
         ));
    }

}
