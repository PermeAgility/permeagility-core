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
		    body(standardLayout(con, parms,getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {

		parms.put("SERVICE","PermeAgility Server Context");
		
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
	
		// Prepare locale list
		StringBuffer localeList = new StringBuffer();
		for (Locale l : Message.getLocales()) {
		    localeList.append(row("data",column(30,l.getLanguage()+" "+l.getCountry()+" "+l.getVariant())+column(30,l.getDisplayLanguage()+" "+l.getDisplayCountry()+" "+l.getDisplayVariant())));
		}
		
		// Prepare session report
		StringBuffer sessionReport = new StringBuffer();
		sessionReport.append("<p>Sessions:<br>");
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
		    paragraph("HTTP server on port "+Server.HTTP_PORT+" has been running since "+Server.getServerInitTime()+" connected to "+Server.DB_NAME+" using "+Server.getDatabase().getUser())
		    +paragraph("Database info="+Server.getDatabase().getName(con)+"<br>clientversion="+Server.getDatabase().getClientVersion()+"<br>serverversion="+Server.getDatabase().getDatabaseVersion(con))
		    +paragraph("banner","Cached Lists")
			+form(button("REFRESH_COLUMNS_ALL","REFRESHCOLUMNS","Clear table columns cache")+hidden("CLEAR_COLUMNS","ALL") + " cached="+Server.columnsCacheSize())
			+br()
			+form(button("REFRESH_MENUS_ALL","REFRESHMENUS","Clear menu cache")+hidden("CLEAR_MENUS","ALL") + " cached="+Menu.cacheSize())
		    +table("data",
			  row(tableHead("Cached query")+tableHead("Size")+tableHead("Time of execution"))
			  +cacheList.toString())
			+form(button("REFRESH_CACHE_ALL","REFRESHCACHE","Clear all query cache")+hidden("TABLE_NAME","ALL") + " cached="+getCache().size())
			+paragraph("banner","Security")
			+sessionReport.toString()
			+form(button("REFRESH_SECURITY_MODEL","REFRESHSEC","Refresh security")+" lastUpdated="+Server.getSecurityRefreshTime())
		    +paragraph("banner","Active Locales")
		    +table("data",
			  row(tableHead("Name")+tableHead("Description"))+
			  localeList.toString()
			  )
		    +paragraph("banner","Setup")
			+form(button("CHECK_INSTALLATION","CHECKINSTALLATION","Check Installation"))
			+(installMessages != null && !installMessages.equals("") ? installMessages : "");
    }

}
