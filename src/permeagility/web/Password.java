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

import permeagility.util.DatabaseConnection;

public class Password extends Weblet {
   
    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		return head(Message.get(con.getLocale(),"CHANGE_PASSWORD_FOR",con.getUser()))+
		    standardLayout(con, parms, body(getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	    StringBuilder errors = new StringBuilder();
		
		if(parms.get("SUBMIT") != null && parms.get("CHANGE") != null && (parms.get("CHANGE")).equals("CHANGE")) {
			String currentPass= (String)parms.get("CURRENTPASS");
			String newPass= (String)parms.get("NEWPASS");
			String confirmPass=(String)parms.get("CONFIRMPASS");
			if(newPass != null && newPass.equals(confirmPass)) {
				if (Security.changePassword(con, currentPass, newPass)) {
				    errors.append(paragraph("success",Message.get(con.getLocale(),"PASSWORD_CHANGE_SUCCESS",con.getUser())));
				} else {
				    errors.append(paragraph("error",Message.get(con.getLocale(),"PASSWORD_CHANGE_FAILED",con.getUser())));					
				}
			} else {
			    errors.append(paragraph("error",Message.get(con.getLocale(),"PASSWORD_MISMATCH",con.getUser())));
			}
		}
		
		return errors
                    +form("PASSCHANGE",hidden("CHANGE","CHANGE") + table("CHGPASS",
                        row(columnSpan(2,paragraph("banner",Message.get(con.getLocale(),"CHANGE_PASSWORD_FOR",con.getUser()))))+
                        row(column("label",Message.get(con.getLocale(),"CURRENT_PASSWORD"))+column(password("CURRENTPASS","")))+
                        row(column("label",Message.get(con.getLocale(),"NEW_PASSWORD"))+column(password("NEWPASS","")))+
                        row(column("label",Message.get(con.getLocale(),"CONFIRM_PASSWORD"))+column(password("CONFIRMPASS","")))+
                        row(column("")+column(submitButton(con.getLocale(),"SUBMIT_BUTTON")))
                    ));
    }
    
    public static String password(String name, Object value) {
    	return "<INPUT TYPE=\"PASSWORD\" NAME=\""+name+"\" VALUE=\""+(value==null ? "" : value)+"\">";
    }

}
