/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.DatabaseSetup;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.record.impl.ODocument;

public class Settings extends Weblet {
	
	public static boolean DEBUG = false;

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		String service = Message.get(con.getLocale(),"SERVER_SETTINGS");
		parms.put("SERVICE",service);
		StringBuffer errors = new StringBuffer();
		
		// Get current style
		QueryResult currentStyleResult = con.query("SELECT FROM "+DatabaseSetup.TABLE_CONSTANT+" WHERE classname='permeagility.web.Context' AND field='DEFAULT_STYLE'");
		ODocument currentStyle = null;
		String currentStyleName = null;
		if (currentStyleResult != null && currentStyleResult.size()>0) {
			currentStyle = currentStyleResult.get(0);
			currentStyleName = currentStyle.field("value");
			if (DEBUG) System.out.println("Settings: CurrentStyle="+currentStyle.field("value"));
		} else {
			System.out.println("Settings: Could not determine current style setting");
		}

		// Get current table row limit
		QueryResult currentRowCountResult = con.query("SELECT value FROM "+DatabaseSetup.TABLE_CONSTANT+" WHERE classname='permeagility.web.Table' AND field='ROW_COUNT_LIMIT'");
		String currentRowCount = "";
		if (currentRowCountResult != null && currentRowCountResult.size()>0) {
			currentRowCount = currentRowCountResult.getStringValue(0, "value");
		}

		String submit = (String)parms.get("SUBMIT");
		if (submit != null) {
			
			// Update style if changed
			String setStyle = parms.get("SET_STYLE");
			if (setStyle != null && !setStyle.equals(currentStyle.getIdentity().toString())) {
				try {
					ODocument style = con.get(setStyle);
					currentStyle.field("value", style.field("name"));
					currentStyleName = style.field("name");
					currentStyle.save();
					Server.tableUpdated("constant");
					errors.append(paragraph("success",Message.get(con.getLocale(),"SYSTEM_STYLE_UPDATED")));
				} catch (Exception e) {
					errors.append(paragraph("error","Error setting style: "+e.getLocalizedMessage()));
					e.printStackTrace();
				}
			}

			// Update rowcount if changed
			String setRowCount = parms.get("SET_ROWCOUNT");
			if (setRowCount != null && !setRowCount.equals(currentRowCount)) {
				int newRowCount = -1;
				try {
					newRowCount = Integer.parseInt(setRowCount);
				} catch (Exception e) {
					errors.append(Message.get(con.getLocale(),"ERROR_PARSING_NUMBER",setRowCount));
				}
				if (newRowCount > 0) {
				try {
					con.update("UPDATE "+DatabaseSetup.TABLE_CONSTANT+" SET value = '"+newRowCount+"' WHERE classname='permeagility.web.Table' AND field='ROW_COUNT_LIMIT'");
					currentRowCount = setRowCount;
					Server.tableUpdated("constant");
					errors.append(paragraph("success",Message.get(con.getLocale(),"ROW_COUNT_LIMIT_UPDATED",setRowCount)));
				} catch (Exception e) {
					errors.append(paragraph("error","Error setting table rowcount limit: "+e.getLocalizedMessage()));
				}
				}
			}
		
		}
		
		// Need to retrieve the style to get its RID to set the default for the pick list
		QueryResult selectedStyle = con.query("SELECT from "+DatabaseSetup.TABLE_STYLE+" WHERE name='"+currentStyleName+"'");
		String selectedStyleID = (selectedStyle == null || selectedStyle.size() == 0 ? "" : selectedStyle.get(0).getIdentity().toString().substring(1));

		return head(service)+
	    standardLayout(con, parms,errors
	    	+form(
	    		table("layout",
	    			row(columnRight(Message.get(con.getLocale(), "SET_STYLE"))+column(createListFromTable("SET_STYLE", selectedStyleID, con, "style", null, false, null, true)))
	    			+row(columnRight(Message.get(con.getLocale(), "SET_ROWCOUNT"))+column(input("SET_ROWCOUNT", currentRowCount)))
	    			+row(columnRight("")+column(submitButton(Message.get(con.getLocale(), "SUBMIT_BUTTON"))))
	        	)
	    	)
    	);
    }

}


