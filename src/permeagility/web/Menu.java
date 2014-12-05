/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.util.DatabaseConnection;
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
		
		StringBuffer menu = new StringBuffer(512);
		if (DEBUG) System.out.println("Menu: Getting menu for "+con.getUser());

		String query = // Sort order is just not working here - aaargh!
				"select menu.name as menuname, name, description, classname, menu.description as menudesc, menu.sortOrder * 10000 + sortOrder as sortOrder "
				+ "from key where active=true and menu is not null "
				+ "and roles contains (@rid in (select roles from OUser where name='"+con.getUser()+"') ) "
				+ "order by sortOrder";

		if (DEBUG) System.out.println("Menu(query):"+ query);

		String previousGroup = null;
		String serviceGroup = null;
		String serviceName = null;
		String serviceDesc = null;
		String serviceClass = null;

		QueryResult rs = null;
		DatabaseConnection dbcon = null;
		try {
			menu.append("<P>");

			// Only the server and admin need to connect to the user table
			dbcon = Server.getDatabase().getConnection();
			if (DEBUG) System.out.println("Menu: Connected as (server) "+dbcon.getUser());
			try {
				rs = dbcon.query(query);
				for (ODocument row : rs.get()) {
					serviceGroup = row.field("menuname");
					serviceName = row.field("name");
					serviceDesc = row.field("description");
					serviceClass = row.field("classname");
	
	                if (previousGroup == null || !serviceGroup.equals(previousGroup)) {
	                    if (previousGroup != null) {
	                        menu.append("<BR>");
	                    }
	                    menu.append(paragraph("menuheader",Message.get(con.getLocale(), serviceGroup)));
	                }
	                previousGroup = serviceGroup;
	                
	                if (serviceClass.toLowerCase().startsWith("http")) {
	                    menu.append(linkNewWindow(serviceClass,Message.get(con.getLocale(), serviceName),Message.get(con.getLocale(), serviceDesc)));
	                    menu.append("<BR>");
	                } else {
	                    try {
							if (serviceName.equals("*")) { // If name=* getMenu() will be called
							    Class<?> xclass = Class.forName( serviceClass );
								Object serviceObject = xclass.newInstance();	
								try {
									Method method = xclass.getMethod("getMenu",new Class[] {DatabaseConnection.class, java.util.HashMap.class});
									String menuHTML = (String)method.invoke(serviceObject,new Object[] { con, parms });
									menu.append(menuHTML);
								} catch (Exception e) {
									System.out.println("Menu: Unable to get menu items from "+serviceClass+": "+e.getLocalizedMessage());
								}
							} else {
								menu.append(link(serviceClass,Message.get(con.getLocale(), serviceName),Message.get(con.getLocale(), serviceDesc)));	
		                        menu.append("<BR>");
							}
						} catch( Throwable t ) {
			                System.out.println("Menu: "+ serviceClass + " " + t.toString() );
			                t.printStackTrace();
						}		
	                }
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			QueryResult qr = dbcon.query("SELECT FROM menu WHERE active=TRUE ORDER BY sortOrder");
			for (ODocument m : qr.get()) {
				StringBuffer itemMenu = new StringBuffer();
                List<ODocument> items = m.field("items");
                if (items != null) {
	                for (ODocument i : items) {
	                	Set<ODocument> readRoles = i.field("_allowRead");
	                	Object userRoles[] = Server.getUserRoles(con);
	                	if (readRoles != null && userRoles != null) {
	                		for (ODocument r : readRoles) {
	                			for (Object u : userRoles) {
	                				if (r != null && u != null) {
	                					if (r.equals(u)) {
	                                    	if (i.field("classname") == null || ((String)i.field("classname")).equals("")) {
	                                            itemMenu.append("<BR>");                	                	
	                                    	} else {
	                                    		itemMenu.append(link((String)i.field("classname"),Message.get(con.getLocale(), (String)i.field("name")),Message.get(con.getLocale(), (String)i.field("description"))));	
	                                    		itemMenu.append("<BR>");  
	                                    	}
	                                    	break;
	                					}
	                				}
	                			}
	                		}
	                	}
	                }
	                if (itemMenu.length() > 0) {
		                menu.append(paragraph("menuheader",Message.get(con.getLocale(), (String)m.field("name"))));
		                menu.append(itemMenu);
	                }
                }
			}
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
	
	// Override link to use framemenu class
	public static String link(String ref, String name, String desc) { 
		return "<A CLASS=\"framemenu\" HREF=\""+ref+"\" TITLE=\""+desc+"\">"+xxSmall(name)+"</A>";
	}
	public static String linkNewWindow(String ref, String name, String desc) { 
		return "<A CLASS=\"framemenu\" HREF=\""+ref+"\" TARGET=\"_blank\" TITLE=\""+desc+"\">"+xxSmall(name)+"</A>";
	}

}


