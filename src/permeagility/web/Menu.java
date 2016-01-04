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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.HashSet;

public class Menu extends Weblet {
	
	public static boolean DEBUG = false;
	public static boolean HORIZONTAL_LAYOUT = false;
	
	private static ConcurrentHashMap<String,String> menuCache = new ConcurrentHashMap<String,String>();
	private static Locale guestLocale = Locale.getDefault();  // Saves the locale of the guest menu cache

	public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		return head("Menu")+body("menu",getHTML(con, parms));
	}

	public String getHTML(DatabaseConnection con, HashMap<String,String> parms) {
		
		Locale locale = con.getLocale();
		String selector = Message.getLocaleSelector(locale,parms);
		String localeSelector = (selector.length() > 0
				? (HORIZONTAL_LAYOUT 
					?" <div style=\"float: right;\">"+popupForm("LANGUAGE",null,Message.get(locale,"LANGUAGE"),null,null,
						paragraph("banner",Message.get(locale, "SELECT_LANGUAGE"))
						+"<p align=\"left\">"+selector+"</p>\n")
						+"</div>\n"
					: "<BR><BR><BR><BR>"+Message.getLocaleSelector(locale,parms))
				: "");
		
		// Return value from cache if it is there
		String cmenu = menuCache.get(con.getUser());
		if (cmenu != null && cmenu.length()>0) {
			if (con.getUser().equals("guest") && locale == guestLocale) {  // Guest could have many locales
				return cmenu + localeSelector;
			}
		}
		
		if (DEBUG) System.out.println("Menu: Getting menu for "+con.getUser());
		StringBuilder menu = new StringBuilder();
		//DatabaseConnection dbcon = null;
		try {
			// Only the server and admin need to see the menu table
			//dbcon = Server.getServerConnection();
			//if (DEBUG) System.out.println("Menu: Connected as (server) "+dbcon.getUser());

			// Assemble menu based on the users roles and the menuItem's _allowRead
			//QueryResult qr = dbcon.query("SELECT FROM "+Setup.TABLE_MENU+" WHERE active=TRUE ORDER BY sortOrder");
			QueryResult qr = con.query("SELECT FROM "+Setup.TABLE_MENU+" WHERE active=TRUE ORDER BY sortOrder");
			for (ODocument m : qr.get()) {
                            StringBuilder itemMenu = new StringBuilder();
                            List<ODocument> items = m.field("items");
                            if (items != null) {
                                    for (ODocument i : items) {
                                        if (DEBUG) System.out.println("MenuItem="+i);
                                            if (i != null) {
                                                    String menuName = (String)i.field("name");
                                                    String menuDesc = (String)i.field("description");
                                                    Boolean itemActive = i.field("active");
                                                    if (itemActive != null && itemActive==true) {
                                                            String pretty = Message.get(locale,"MENUITEM_"+menuName);
                                                            if (menuName != null && ("MENUITEM_"+menuName).equals(pretty)) {  // No translation
                                                                    pretty = menuName;
                                                            } else if (menuName == null) {
                                                                    pretty = "";
                                                            }
                                                            String prettyDesc = Message.get(locale,"MENUITEMDESC_"+menuName);
                                                            if (menuDesc != null && ("MENUITEMDESC_"+menuName).equals(prettyDesc)) {  // No translation
                                                                    prettyDesc = menuDesc;
                                                            } else if (menuDesc == null) {
                                                                    prettyDesc = "";
                                                            }
                                                            Set<ODocument> readRoles = i.field("_allowRead");
                                                            Set<String> readRoleSet = new HashSet<>();
                                                            for (ODocument rr : readRoles) {
                                                                readRoleSet.add(rr.getIdentity().toString());
                                                            }
                                                            if (readRoles != null && Security.isRoleMatch(Security.getUserRoles(con),readRoleSet)) {
  
                                                            if (i.field("classname") == null || ((String)i.field("classname")).equals("")) {
                                                                itemMenu.append((HORIZONTAL_LAYOUT ? "&nbsp;" : "<br>") +"\n");                	                	
                                                            } else {
                                                                itemMenu.append(link((String)i.field("classname"), pretty, prettyDesc));	
                                                                itemMenu.append((HORIZONTAL_LAYOUT ? "&nbsp;" : "<br>") +"\n");  
                                                            }
                                                        }
                                                    } else {
                                                        if (DEBUG) System.out.println("Menu item "+menuName+" is inactive");
                                                    } 
                                            } else {
                                                // No access to item
                                                //    System.err.println("Menu item is null - was deleted?");
                                            }
                                    }
                                    if (itemMenu.length() > 0) {
                                            String menuHeader = (String)m.field("name");
                                            String pretty = Message.get(locale,"MENU_"+menuHeader);
                                            if (menuHeader != null && ("MENU_"+menuHeader).equals(pretty)) {  // No translation
                                                    pretty = menuHeader;
                                            } else if (menuHeader == null) {
                                                    pretty = "";
                                            }
                                            menu.append((HORIZONTAL_LAYOUT ? "&nbsp;&nbsp;&nbsp;" : paragraph("menuheader",pretty)));
                                            menu.append(itemMenu);
                                    }
                            }
			}
		} catch( Exception e ) {
			System.out.println("Error in Menu: "+e);
			e.printStackTrace();
//		} finally {
//			if (dbcon != null) {
//				Server.freeServerConnection(dbcon);
//			}
		}
		if (DEBUG) System.out.println("Menu: Adding menu for "+con.getUser()+" to menuCache");
		String newMenu = menu.toString();
		menuCache.put(con.getUser(), newMenu);
		if (con.getUser().equals("guest")) {
			guestLocale = locale;
		}
		return newMenu + localeSelector;
	}		

	// Override links to use menuitem class
	public static String link(String ref, String name, String desc) { 
		return "<a class=\"menuitem\" href=\""+ref+"\" title=\""+desc+"\">"+name+"</a>\n";
	}
	public static String linkNewWindow(String ref, String name, String desc) { 
		return "<a class=\"menuitem\" href=\""+ref+"\" target=\"_blank\" title=\""+desc+"\">"+name+"</a>\n";
	}

	public static void clearMenu(String user) {  menuCache.remove(user);  }
	public static void clearCache() {  menuCache.clear(); }
	public static int cacheSize() {	return menuCache.size();  }	
	
}