/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import permeagility.util.DatabaseConnection;

public class Visuility extends Weblet {
	
	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		parms.put("SERVICE", Message.get(con.getLocale(), "VISUILITY"));
		return 	
			head(Message.get(con.getLocale(), "VISUILITY"),getScript("d3.js"))+
			body(standardLayout(con, parms,  
				Schema.getTableSelector(con)
				+"<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>"
				+getScript("visuility.js")
			));
	}

}


