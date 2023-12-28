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

import java.util.Collection;

import com.arcadedb.database.Document;
import com.arcadedb.schema.Property;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

public class Profile extends Table {
   
    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		return head(con, Message.get(con.getLocale(),"UPDATE_PROFILE",con.getUser()))
             + bodyMinimum(getHTML(con, parms));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
        StringBuilder errors = new StringBuilder();
        String submit = parms.get("SUBMIT");
        if (submit != null && submit.equals("UPDATE_PASSWORD")) {
            String currentPass = parms.get("CURRENTPASS");
            String newPass = parms.get("NEWPASS");
            String confirmPass = parms.get("CONFIRMPASS");
            if(newPass != null && newPass.equals(confirmPass)) {
                if (Security.changePassword(con, currentPass, newPass)) {
                    errors.append(paragraph("success",Message.get(con.getLocale(),"PASSWORD_CHANGE_SUCCESS",con.getUser())));
                    return errors+link("/",Message.get(con.getLocale(), "HEADER_LOGO_DESC"));
                } else {
                    errors.append(paragraph("error",Message.get(con.getLocale(),"PASSWORD_CHANGE_FAILED",con.getUser())));					
                }
            } else {
                errors.append(paragraph("error",Message.get(con.getLocale(),"PASSWORD_MISMATCH",con.getUser())));
            }
        }

        if (submit != null && submit.equals("UPDATE")) {
            if (updateRow(con, Setup.TABLE_USERPROFILE, parms, errors)) {
                errors.append(paragraph("success",Message.get(con.getLocale(),"PROFILE_UPDATED",con.getUser())));
                return errors+link("/",Message.get(con.getLocale(), "HEADER_LOGO_DESC"));
            }
        }

        // Get the user profile id for this user - if none (admin) then no fields will be shown
        Document up = con.queryDocument("SELECT FROM "+Setup.TABLE_USERPROFILE+" WHERE name='"+con.getUser()+"'");
        if (up != null) {
           parms.put("EDIT_ID", up.getIdentity().toString().substring(1));
        }       
        
        // Build a column list for the profile without the name and password
        StringBuilder profileColumns = new StringBuilder();
        profileColumns.append("-");  // Keep name/password from being added again
        Collection<Property> profColumns = con.getColumns(Setup.TABLE_USERPROFILE);
        for (Property p : profColumns) {
            String pcName = p.getName();
            if (!pcName.equals("name") && !pcName.equals("password")) {
                profileColumns.append(",");
                profileColumns.append(pcName);
            }
        }

        return errors
            +paragraph("banner",Message.get(con.getLocale(),"UPDATE_PROFILE",con.getUser()))
            +(parms.get("EDIT_ID") != null 
               ? form("PROFILECHANGE",hidden("EDIT_ID",parms.get("EDIT_ID"))
                       +getTableRowFields(con, Setup.TABLE_USERPROFILE, parms, profileColumns.toString())
                       +center(submitButton(con.getLocale(),"UPDATE"))
               )
               : "")
            +paragraph("banner",Message.get(con.getLocale(),"UPDATE_PASSWORD",con.getUser()))
            +center(form("PASSCHANGE",table("CHGPASS",
                row(column("label",Message.get(con.getLocale(),"CURRENT_PASSWORD"))+column(password("CURRENTPASS","")))+
                row(column("label",Message.get(con.getLocale(),"NEW_PASSWORD"))+column(password("NEWPASS","")))+
                row(column("label",Message.get(con.getLocale(),"CONFIRM_PASSWORD"))+column(password("CONFIRMPASS","")))+
                row(column("")+column(submitButton(con.getLocale(),"UPDATE_PASSWORD")))
            )));
    }
    
    public static String password(String name, Object value) {
    	return "<INPUT TYPE=\"PASSWORD\" NAME=\""+name+"\" VALUE=\""+(value==null ? "" : value)+"\">";
    }

}
