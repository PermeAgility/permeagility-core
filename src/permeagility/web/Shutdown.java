/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;

public class Shutdown extends Weblet {

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		String service = Message.get(con.getLocale(),"SHUTDOWN_SERVER");
		parms.put("SERVICE",service);
		StringBuffer errors = new StringBuffer();
		
		if (parms.get("SUBMIT") != null && parms.get("SUBMIT").equals(Message.get(con.getLocale(),"CANCEL"))) {
			return head("Redirect")
			+ bodyOnLoad("Redirecting...", "window.location.href='/';");
    	}
    
		if (parms.get("SUBMIT") == null || !parms.get("SUBMIT").equals(Message.get(con.getLocale(),"CONFIRM_SHUTDOWN"))) {
	    	return head(service)+
		    standardLayout(con, parms,
		    	errors
	    		+paragraph("banner",Message.get(con.getLocale(), "CONFIRM_SHUTDOWN"))
	    		+form("SHUTDOWN_FORM",
	    			hidden("SHUTDOWN",parms.get("SHUTDOWN"))
	    			+paragraph(Message.get(con.getLocale(),"SHUTDOWN_CONFIRM_MESSAGE"))
	    			+submitButton(Message.get(con.getLocale(),"CONFIRM_SHUTDOWN"))
	    			+"&nbsp;&nbsp;&nbsp;&nbsp;"
	    			+submitButton(Message.get(con.getLocale(),"CANCEL"))
		        )
		    );
		} else {
				System.out.println("Database and Server shutdown initiated by user "+con.getUser());
				Server.restore_lockout = true;
				try {
					System.out.println("Shutting down the database");
					System.exit(0);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return head("Redirect")
				+ bodyOnLoad("Redirecting...", "window.location.href='/';");
		}
    }

}


