/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.DatabaseSetup;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.record.impl.ODocument;

public class Menu extends Weblet {
	
	public static boolean DEBUG = false;
	
	private static ConcurrentHashMap<String,String> menuCache = new ConcurrentHashMap<String,String>();

	public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		return head("Menu")+
			body("framemenu",getHTML(con, parms));
	}

	public String getHTML(DatabaseConnection con, HashMap<String,String> parms) {
		
		// Return value from cache if it is there
		String cmenu = menuCache.get(con.getUser());
		if (cmenu != null) {
			return cmenu;
		}
		
		StringBuffer menu = new StringBuffer();
		if (DEBUG) System.out.println("Menu: Getting menu for "+con.getUser());

		DatabaseConnection dbcon = null;
		try {
			menu.append("<P>");

			// Only the server and admin need to see the menu table
			dbcon = Server.getDatabase().getConnection();
			if (DEBUG) System.out.println("Menu: Connected as (server) "+dbcon.getUser());

			// Assemble menu based on the users roles and the menuItem's _allowRead
			QueryResult qr = dbcon.query("SELECT FROM "+DatabaseSetup.TABLE_MENU+" WHERE active=TRUE ORDER BY sortOrder");
			for (ODocument m : qr.get()) {
				StringBuffer itemMenu = new StringBuffer();
                List<ODocument> items = m.field("items");
                if (items != null) {
	                for (ODocument i : items) {
	                	Set<ODocument> readRoles = i.field("_allowRead");
	                	if (Server.isRoleMatch(Server.getUserRoles(con),readRoles.toArray())) {
                        	if (i.field("classname") == null || ((String)i.field("classname")).equals("")) {
                                itemMenu.append("<BR>");                	                	
                        	} else {
                        		itemMenu.append(link((String)i.field("classname"),Message.get(con.getLocale(), (String)i.field("name")),Message.get(con.getLocale(), (String)i.field("description"))));	
                        		itemMenu.append("<BR>");  
                        	}
	                	}
	                }
	                if (itemMenu.length() > 0) {
		                menu.append(paragraph("menuheader",Message.get(con.getLocale(), (String)m.field("name"))));
		                menu.append(itemMenu);
	                }
                }
			}
			// Add the locale selector
			menu.append("<BR><BR><BR><BR>"+xxSmall(Message.getLocaleSelector(con.getLocale(),parms)));
		} catch( Exception e ) {
			System.out.println("Error in Menu: "+e);
			e.printStackTrace();
		} finally {
			if (dbcon != null) {
				Server.getDatabase().freeConnection(dbcon);
			}
		}
		menu.append("</P>\n");
		if (DEBUG) System.out.println("Menu: Adding menu for "+con.getUser()+" to menuCache");
		menuCache.put(con.getUser(), menu.toString());
		return menu.toString();
	}		

	public static void clearMenu(String user) {
		menuCache.remove(user);
	}
	
	public static void clearCache() {
		menuCache.clear();
	}
	
	public static int cacheSize() {
		return menuCache.size();
	}
	
	// Override links to use framemenu class
	public static String link(String ref, String name, String desc) { 
		return "<A CLASS=\"framemenu\" HREF=\""+ref+"\" TITLE=\""+desc+"\">"+xxSmall(name)+"</A>";
	}
	public static String linkNewWindow(String ref, String name, String desc) { 
		return "<A CLASS=\"framemenu\" HREF=\""+ref+"\" TARGET=\"_blank\" TITLE=\""+desc+"\">"+xxSmall(name)+"</A>";
	}

}