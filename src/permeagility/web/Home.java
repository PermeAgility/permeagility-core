/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;
import permeagility.util.QueryResult;

public class Home extends Weblet {
	
    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		String service = Message.get(con.getLocale(),Message.get(con.getLocale(), "HOME_PAGE_TITLE"));
		parms.put("SERVICE",service);
		return head(service)+
		    body (standardLayout(con, parms,getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, HashMap<String,String> parms) {
    	StringBuffer sb = new StringBuffer();
    	try {
	    	QueryResult qr = con.query("SELECT dateline, name, description FROM "+Setup.TABLE_NEWS
	    							+" WHERE (archive IS NULL or archive=false) and (locale IS NULL or locale.name='"+con.getLocale().getLanguage()+"') "
	    							+"AND _allowRead in ["+Server.getUserRolesList(con)+"] ORDER BY dateline desc "
	    							);
	    	for (int i=0; i<qr.size(); i++) {
	    		sb.append(paragraph("headline",qr.getStringValue(i, "name")));
	    		sb.append(paragraph("dateline",formatDate(con.getLocale(), qr.getDateValue(i, "dateline"), "MMMM dd yyyy")));
	    		sb.append(paragraph("article",qr.getStringValue(i, "description")));
	    	}
    	} catch (Exception e) {  
    		e.printStackTrace();
    		sb.append("Error retrieving articles: "+e.getMessage());
    	}
    	return sb.toString();
    }

}
