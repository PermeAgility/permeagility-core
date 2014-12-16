/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.Locale;

import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.DatabaseSetup;
import permeagility.util.QueryResult;

public class Context extends Weblet {

    public boolean DEBUG = true;  // Normally its nice to see these messages in the log

	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		return 	head("Context")+
		    body(standardLayout(con, parms, getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {

		Locale locale = con.getLocale();
		parms.put("SERVICE",Message.get(locale, "SERVER_CONTEXT"));
		
		// Do stuff if asked
		String ref = parms.get("TABLE_NAME");
		if (ref != null && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: Cache refresh="+ref);
		    getCache().refresh(ref);
		}

		String cc = parms.get("CLEAR_COLUMNS");
		if (cc != null && !cc.equals("") && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: Column Cache refresh="+cc);
			Server.clearColumnsCache(cc);
		}
	
		String cm = parms.get("CLEAR_MENUS");
		if (cm != null && cm.equals("ALL") && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: Menu Cache refresh");
			Menu.clearCache();
		}
	
		String refSec = parms.get("REFRESH_SECURITY_MODEL");
		if (refSec != null && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: refresh security");
			Server.refreshSecurity();
		}

		String checkInst = parms.get("CHECK_INSTALLATION");
		if (checkInst != null && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: Check Installation");
			DatabaseSetup.checkInstallation(con);
		}
    	
		// Prepare cached query list
		StringBuffer cacheList = new StringBuffer();
		Object[] keys = getCache().keySet().toArray();
		for (Object key : keys) {
			QueryResult qr = getCache().get(key);
			if (qr != null) {
				cacheList.append(row("data",column(30,link("permeagility.web.Context?TABLE_NAME="+key,(String)key))+column(10,""+(qr.size()))+column(20,qr.getTime().toString())));
			}
		}
	
		// Prepare session report
		StringBuffer sessionReport = new StringBuffer();
		sessionReport.append("<p>"+Message.get(locale,"SERVER_SESSIONS")+"<br>");
		for (String usr : Server.sessionsDB.keySet()) {
			if (usr != null) {
				int scnt = 0;
				for (String cookie : Server.sessions.keySet()) {
					String cu = Server.sessions.get(cookie);
					if (cu != null) {
						if (cu.equals(usr)) scnt++;
					}
				}
				Database udb = Server.sessionsDB.get(usr);
				if (udb != null) {
					sessionReport.append(usr+" sessions="+scnt+" active="+udb.getActiveCount()+" pooled="+udb.getPooledCount()+"<BR>");
				}
			}
		}
		sessionReport.append("</p>");

		// Get install messages
		String installMessages = DatabaseSetup.getMessages();
		
		// Return content
		return
		    paragraph(Message.get(locale, "SERVER_ON_PORT")+"&nbsp;"+Server.HTTP_PORT
		    		+br()+Message.get(locale, "SERVER_RUNNING")+" "+Server.getServerInitTime()
		    		+br()+Message.get(locale, "SERVER_CONNECT")+" "+Server.DB_NAME
		    		+br()+Message.get(locale, "SERVER_USER")+" "+Server.getDatabase().getUser()
		    		+br()+Message.get(locale, "SERVER_VERSION")+Server.getDatabase().getClientVersion())
		    +paragraph("banner",Message.get(locale, "SERVER_CACHE"))
			+form(button("REFRESH_COLUMNS_ALL","REFRESHCOLUMNS",Message.get(locale, "CACHE_CLEAR_COLUMNS"))
					+hidden("CLEAR_COLUMNS","ALL") + "&nbsp;"
					+Message.get(locale, "CACHE_COUNT",""+Server.columnsCacheSize()))
			+br()
			+form(button("REFRESH_MENUS_ALL","REFRESHMENUS",Message.get(locale, "CACHE_CLEAR_MENUS"))
					+hidden("CLEAR_MENUS","ALL") + "&nbsp;"
					+Message.get(locale, "CACHE_COUNT",""+Menu.cacheSize()))
		    +table("data",
			  row(tableHead(Message.get(locale, "CACHED_QUERY"))
					  +tableHead(Message.get(locale, "CACHE_SIZE"))
					  +tableHead(Message.get(locale, "CACHE_LASTREFRESH")))
			  +cacheList.toString())
			+form(button("REFRESH_CACHE_ALL","REFRESHCACHE",Message.get(locale, "CACHE_CLEAR_LISTS"))
					+hidden("TABLE_NAME","ALL") + "&nbsp;"
					+Message.get(locale, "CACHE_COUNT",""+getCache().size()))
			+paragraph("banner",Message.get(locale, "SERVER_SECURITY"))
			+sessionReport.toString()
			+form(button("REFRESH_SECURITY_MODEL","REFRESHSEC",Message.get(locale, "REFRESH_SECURITY"))
					+"&nbsp;"+Message.get(locale, "SECURITY_UPDATED")+"&nbsp;"+Server.getSecurityRefreshTime())
		    +paragraph("banner",Message.get(locale,"SERVER_SETUP"))
			+form(button("CHECK_INSTALLATION","CHECKINSTALLATION",Message.get(locale,"CHECK_INSTALLATION")))
			+(installMessages != null && !installMessages.equals("") ? installMessages : "");
    }

}
