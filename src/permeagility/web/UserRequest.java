/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

public class UserRequest extends Table {

    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	return head(Message.get(con.getLocale(),"REQUEST_ACCOUNT"))+
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
