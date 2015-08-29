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

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class Home extends Weblet {
	
    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		String service = Message.get(con.getLocale(),Message.get(con.getLocale(), "HOME_PAGE_TITLE"));
		parms.put("SERVICE",service);
		return head(service)+
		    body (standardLayout(con, parms,getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, HashMap<String,String> parms) {
    	StringBuilder sb = new StringBuilder();
    	try {
	    	QueryResult qr = con.query("SELECT dateline, name, description FROM "+Setup.TABLE_NEWS
	    							+" WHERE (archive IS NULL or archive=false) and (locale IS NULL or locale.name='"+con.getLocale().getLanguage()+"') "
	    							+"AND _allowRead in ["+Security.getUserRolesList(con)+"] ORDER BY dateline desc "
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
