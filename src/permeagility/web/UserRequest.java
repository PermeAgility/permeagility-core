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
import permeagility.util.Setup;

public class UserRequest extends Table {

     public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	return head(Message.get(con.getLocale(),"REQUEST_ACCOUNT"),getDateControlScript(con.getLocale())+getColorControlScript())+
	    body(getHTML(con,parms));
    }
        
    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
    	StringBuilder errors = new StringBuilder();
    	if (parms.get("SUBMIT") != null) {
            if (insertRow(con, Setup.TABLE_USERREQUEST, parms, errors)) {
                return paragraph("success",Message.get(con.getLocale(), "USERREQUEST_INSERTED"))+link("/",Message.get(con.getLocale(), "HEADER_LOGO_DESC"));
            }
    	}
    	return errors+getTableRowForm(con, Setup.TABLE_USERREQUEST, parms);
    }
                
}
