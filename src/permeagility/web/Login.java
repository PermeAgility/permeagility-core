/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.Locale;

import permeagility.util.DatabaseConnection;

public class Login extends Weblet {

	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		return getHTML(parms);
	}
	
	public String getHTML(java.util.HashMap<String,String> parms) {
	
		String error = null;
		if (parms.get("USERNAME") != null || parms.get("PASSWORD") != null) {
			error = "Error in user name or password";
		}
		if (parms.get("SECURITY_VIOLATION") != null) {
			error = parms.get("SECURITY_VIOLATION");
		}

		String destinationClass = parms.get("DESTINATIONCLASS");
		if (destinationClass == null) {
			destinationClass = Server.HOME_CLASS;
		}
		
		Locale locale = Message.getDefaultLocale();
		if (parms.get("LOCALE") != null) {
			Locale newLocale = Message.getLocale(parms.get("LOCALE"));
			if (newLocale != null) {
				locale = newLocale;
			}
		}
		return
			head(Message.get(locale, "LOGIN_TITLE"))+
			bodyOnLoad(
				link(Server.HOME_CLASS,image(Header.LOGO_FILE))+br()
				+form("LOGIN",destinationClass,
				    hidden("LOCALE",locale.toString())
				    +(error != null ? paragraph("error",error) : "")
					+table("data",
						row("header",columnSpan(2,center(Message.get(locale, "LOGIN_TITLE")+br())))+
						row("data",
							columnRight(40,Message.get(locale,"USER_LABEL")) 
							+column(60,input(0,"USERNAME",null))
						)+
						row("data",
							columnRight(40,Message.get(locale,"PASSWORD_LABEL")) 
							+column(60,password(null))
						) +
						row(
							column(40,null)
							+column(60,submitButton(locale,"LOGIN_BUTTON_TEXT"))
						)
					)
				)
				+br()
				+Message.getLocaleSelector(locale, parms)	
				+br()
				+ link("permeagility.web.UserRequest",Message.get(locale, "REQUEST_LOGIN"))+br() 
				+paragraph("&copy;2015 <A HREF=http://www.permeagility.com>PermeAgility Incorporated</A>")+"\n"
			,"self.focus(); document.LOGIN.USERNAME.focus();");
	} 
	
	public static String form(String n, String action, String s) { return "<FORM AUTOCOMPLETE=\"off\" NAME=\""+n+"\" ACTION=\""+action+"\" METHOD=\"POST\" ENCTYPE=\"application/x-www-form-urlencoded\">"+s+"</FORM>\n"; }
	
}
