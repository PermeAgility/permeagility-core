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

import java.util.Locale;

import com.arcadedb.database.Document;

import permeagility.util.DatabaseConnection;

public class Login extends Weblet {

	public static String DEFAULT_HOME = "home-dark";  // Only needed if people try to go home with no name specified

	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		return getHTML(con, parms);
	}
	
	public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	
		String destinationClass = parms.get("DESTINATIONCLASS");
		String destinationMode = parms.get("DESTINATIONMODE");

		if (destinationMode == null || destinationMode.isBlank()) destinationMode = DEFAULT_HOME;

		if (destinationClass == null) {
			destinationClass = Server.HOME_CLASS+"?NAME="+destinationMode;
		}
		if (!con.getUser().equalsIgnoreCase("guest") ) { // We are logged in, redirect to main page
			return redirect(parms, destinationClass);
		}

		StringBuilder modeSelect = new StringBuilder();		// build a select list of home pages to choose from (usually light/dark)
		modeSelect.append("<select name='DESTINATIONMODE'>\n");
		for (Document item : con.queryTable("menuItem","type = 'HOME'").get()) {
			modeSelect.append("<option value='"+item.getString("name")+"' title='"+item.getString("description")+"'>"+item.getString("name")+"</option>");
		}
		modeSelect.append("</select>");

		String error = null;
		if (parms.get("USERNAME") != null || parms.get("PASSWORD") != null) {
			error = "Error in user name or password";
		}
		if (parms.get("SECURITY_VIOLATION") != null) {
			error = parms.get("SECURITY_VIOLATION");
		}

		Locale locale = Message.getDefaultLocale();
		if (parms.get("LOCALE") != null) {
			Locale newLocale = Message.getLocale(parms.get("LOCALE"));
			if (newLocale != null) {
				locale = newLocale;
			}
		}
		return
			head(con, Message.get(locale, "LOGIN_TITLE"), LOGIN_PAGE_STYLE)+
			bodyOnLoad(
				link("/"+Server.HOME_CLASS, image("headerlogo", Header.LOGO_FILE))+br()
				+form("LOGIN","",
				    hidden("LOCALE",locale.toString())
				    +(error != null ? paragraph("error",error) : "")
					+table("data",
						row("header",columnSpan(2,center(Message.get(locale, "LOGIN_TITLE")+br())))+
						row("data",
							columnRight(40,Message.get(locale,"USER_LABEL")) 
							+column(60,inputRequired(0,"USERNAME",null))
						)+
						row("data",
							columnRight(40,Message.get(locale,"PASSWORD_LABEL")) 
							+column(60,passwordRequired())
						) +
						row("data",
							columnRight(40,Message.get(locale,"LIGHTDARK_LABEL")) 
							+column(60,modeSelect.toString())
						) +
						row(
							column(40,hidden("DESTINATIONCLASS",destinationClass))
							+column(60,submitButton(locale,"LOGIN_BUTTON_TEXT"))
						)
					)
				)
				+br()
				+Message.getLocaleSelector(locale, parms)	
				+br()
				+ link("permeagility.web.UserRequest",Message.get(locale, "REQUEST_LOGIN"))+br() 
				+paragraph("&copy; <A HREF=http://www.permeagility.com>PermeAgility Incorporated</A>")+"\n"
			,"self.focus(); document.LOGIN.USERNAME.focus();");
	} 
	
	public static String form(String n, String action, String s) { 
		return "<FORM AUTOCOMPLETE=\"off\" NAME=\""+n+"\" ACTION=\""+action
		     + "\" METHOD=\"POST\" ENCTYPE=\"application/x-www-form-urlencoded\">"+s+"</FORM>\n"; 
	}

	public static  String LOGIN_PAGE_STYLE = """
<style type='text/css'>
body { background-color: #222; color: white; font-family: sans-serif; }
img.headerlogo { width: 400px; }
a { color: #999; }
</style>
""";

}
