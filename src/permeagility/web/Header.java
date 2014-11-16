/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.Calendar;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;

public class Header extends Weblet {
	
	public static String LOGO_FILE = "Logo.svg";
	
	public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		return 
			head("Header")+
			body(getHTML(con, parms));
	}
	
	public String getHTML(DatabaseConnection con, HashMap<String,String> parms) {
		String sn = parms.get("SERVICE");		
		return
			table("layout",
				row(
					column(10,
							link(Server.HOME_CLASS,image("headerlogo",LOGO_FILE),Message.get(con.getLocale(),"HEADER_LOGO_DESC"))
					)+
					columnNoWrap(70,
						span("headertitle",Message.get(con.getLocale(),"HEADER_TITLE"))+
						br()+
						span("headerservice",(sn == null ? "" : sn))
					)+
					columnNoWrap(20,
                            span("headeruser",getHeaderUser(con,parms))+
							br()+
							span("headertime",Message.get(con.getLocale(),"DATE_LABEL")
									+"&nbsp;"
								    +formatDate(con.getLocale(),Calendar.getInstance().getTime().getTime()/1000,Message.get(con.getLocale(),"DATE_FORMAT")))
					)
				)
			);
	}

	String getHeaderUser(DatabaseConnection con, HashMap<String,String> parms) {
		if (con.getUser().equals("guest")) {
			return popupForm("login", null, Message.get(con.getLocale(),"LOGIN_BUTTON_TEXT"),null , "USERNAME",
			  table("data",
				row(columnRight(50,Message.get(con.getLocale(),"USER_LABEL"))+column(50,input("USERNAME","")))
				+row(columnRight(50,Message.get(con.getLocale(),"PASSWORD_LABEL"))+column(50,password(null)))
				+row(columnRight(50,"")+column(50,submitButton(Message.get(con.getLocale(),"LOGIN_BUTTON_TEXT"))))
				+row(columnSpan(2,link("permeagility.web.UserRequest",Message.get(con.getLocale(), "REQUEST_LOGIN"))))
				))
				+ (parms.get("USERNAME") == null ? "" : Message.get(con.getLocale(), "INVALID_USER_OR_PASSWORD"));
		} else {
			return Message.get(con.getLocale(),"WELCOME_USER",con.getUser())
					+"&nbsp;&nbsp;"+link(Server.LOGIN_CLASS,Message.get(con.getLocale(), "LOGOUT"));
		}
	}
	
	public static String link(String ref, String name, String desc) { 
		return "<A CLASS=\"headerlogo\" HREF=\""+ref+"\" TITLE=\""+desc+"\">"+xxSmall(name)+"</A>";
	}
				
}
