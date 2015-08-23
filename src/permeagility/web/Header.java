/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import permeagility.util.DatabaseConnection;

public class Header extends Weblet {
	
	public static String LOGO_FILE = "Logo-blk.svg";
	public static boolean PAD_LOGIN = true;  // Add 30 spaces before login to ensure form fits (needed if on right of screen)
	
	public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		return 
			head("Header")+
			body(getHTML(con, parms));
	}
	
	public String getHTML(DatabaseConnection con, HashMap<String,String> parms) {
		Locale locale = con.getLocale();
		String serviceName = parms.get("SERVICE");		
		return
			logoLink(Server.HOME_CLASS,image("headerlogo",LOGO_FILE),Message.get(locale,"HEADER_LOGO_DESC"))
            +div("headeruser",getHeaderUser(con,parms))
			+div("headertitle",Message.get(locale,"HEADER_TITLE"))
			+div("headerservice",(serviceName == null ? "" : serviceName))
			+div("headertime",Message.get(locale,"DATE_LABEL")+"&nbsp;"+formatDate(locale,new Date(),Message.get(locale,"DATE_FORMAT")));
	}

	public String getHeaderUser(DatabaseConnection con, HashMap<String,String> parms) {
		String pad = new String(new char[15]).replace("\0", "&nbsp;");
		if (con.getUser().equals("guest")) {
			return 
				Message.get(con.getLocale(), "YOU_ARE_NOT_LOGGED_IN")+"&nbsp;"
				+popupForm("login", null, Message.get(con.getLocale(),"LOGIN_BUTTON_TEXT"), null , "USERNAME",
				  table("data",
					row(column("label",Message.get(con.getLocale(),"USER_LABEL"))+column(input("USERNAME","")))
					+row(column("label",Message.get(con.getLocale(),"PASSWORD_LABEL"))+column(password(null)))
					+row(column("")+column(submitButton(con.getLocale(),"LOGIN_BUTTON_TEXT")))
					+row(columnSpan(2,link("permeagility.web.UserRequest",Message.get(con.getLocale(), "REQUEST_LOGIN"))))
				))
				+ (parms.get("USERNAME") == null ? "" : color("red",bold(Message.get(con.getLocale(), "INVALID_USER_OR_PASSWORD"))))
				+(PAD_LOGIN ? pad : "");
		} else {
			return Message.get(con.getLocale(),"WELCOME_USER",con.getUser())
					+"&nbsp;&nbsp;"+link(Server.LOGIN_CLASS,Message.get(con.getLocale(), "LOGOUT"));
		}
	}
	
	public static String logoLink(String ref, String name, String desc) { 
		return "<A CLASS=\"headerlogo\" HREF=\""+ref+"\" TITLE=\""+desc+"\">"+name+"</A>";
	}
				
}
