/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import permeagility.util.DatabaseConnection;

public class UserRequest extends Table {

    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	return head(Message.get(con.getLocale(),"REQUEST_ACCOUNT"))+
	    body(getHTML(con,parms));
    }
        
    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
    	StringBuffer errors = new StringBuffer();
    	if (parms.get("SUBMIT") != null) {
    		if (insertRow(con, "userRequest", parms, errors)) {
    			return paragraph("success","Your request was inserted - you will receive an email to confirm your account")
    					+link("/","Back to home page");
    		}
    	}
    	return errors+getTableRowForm(con, "userRequest", parms);
    }
        
}
