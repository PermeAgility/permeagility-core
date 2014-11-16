/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import permeagility.util.DatabaseConnection;

public class ChangePassword extends Weblet {
   
    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		return head(Message.get(con.getLocale(),"CHANGE_PASSWORD_FOR",con.getUser()))+
		    standardLayout(con, parms, body(getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	    StringBuffer errors = new StringBuffer();
		
		if(parms.get("SUBMIT") != null && parms.get("CHANGE") != null && (parms.get("CHANGE")).equals("CHANGE")) {
			String currentPass= (String)parms.get("CURRENTPASS");
			String newPass= (String)parms.get("NEWPASS");
			String confirmPass=(String)parms.get("CONFIRMPASS");
			if(newPass != null && newPass.equals(confirmPass)) {
				if (Server.changePassword(con, currentPass, newPass)) {
				    errors.append(paragraph("success",Message.get(con.getLocale(),"PASSWORD_CHANGE_SUCCESS",con.getUser())));
				} else {
				    errors.append(paragraph("error",Message.get(con.getLocale(),"PASSWORD_CHANGE_FAILED",con.getUser())));					
				}
			} else {
			    errors.append(paragraph("error",Message.get(con.getLocale(),"PASSWORD_MISMATCH",con.getUser())));
			}
		}
		
		return errors
			+form("PASSCHANGE",hidden("CHANGE","CHANGE")+
			     table("CHGPASS",
				   row(columnSpan(2,paragraph("banner",Message.get(con.getLocale(),"CHANGE_PASSWORD_FOR",con.getUser()))))+
				   row(columnRight(10,Message.get(con.getLocale(),"CURRENT_PASSWORD"))+
				       column(10,password("CURRENTPASS","")))+
				   row(columnRight(10,Message.get(con.getLocale(),"NEW_PASSWORD"))+
				       column(10,password("NEWPASS","")))+
				   row(columnRight(10,Message.get(con.getLocale(),"CONFIRM_PASSWORD"))+
				       column(10,password("CONFIRMPASS","")))+
				   row(columnRight(10,submitButton("SUBMIT",Message.get(con.getLocale(),"SUBMIT")))+
				       column(10,""))
				  )
			);

    }
    
    public static String password(String name, Object value) {
    	return "<INPUT TYPE=\"PASSWORD\" NAME=\""+name+"\" VALUE=\""+(value==null ? "" : value)+"\">";
    }

}
