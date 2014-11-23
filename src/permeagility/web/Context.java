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

    public boolean DEBUG = true;

	public Context() {
		super();
	}

	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	
		parms.put("SERVICE","PermeAgility Server Context");
		
		String ref = parms.get("TABLE_NAME");
		if (ref != null) {
		    getCache().refresh(ref);
		    if (DEBUG) System.out.println("CONTEXT: Cache refresh="+ref);
		}

		String cc = parms.get("CLEAR_COLUMNS");
		if (cc != null && !cc.equals("")) {
			Server.clearColumnsCache(cc);
		    if (DEBUG) System.out.println("CONTEXT: Column Cache refresh="+cc);
		}
	
		String cm = parms.get("CLEAR_MENUS");
		if (cm != null && cm.equals("ALL")) {
			Menu.clearCache();
		    if (DEBUG) System.out.println("CONTEXT: Menu Cache refresh");
		}
	
		String refSec = parms.get("REFRESH_SECURITY_MODEL");
		if (refSec != null) {
		    if (DEBUG) System.out.println("Context: refresh security");
			Server.refreshSecurity();
		}

		String checkInst = parms.get("CHECK_INSTALLATION");
		if (checkInst != null && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: Check Installation");
			DatabaseSetup.checkInstallation(con);
		}

		return 	head("Context")+
		    body(standardLayout(con, parms,getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		
		StringBuffer cacheList = new StringBuffer(256);
		Object[] keys = getCache().keySet().toArray();
		for (Object key : keys) {
			QueryResult qr = getCache().get(key);
			if (qr != null) {
				cacheList.append(row("data",column(30,link("permeagility.web.Context?TABLE_NAME="+key,(String)key))+column(10,""+(qr.size()))+column(20,qr.getTime().toString())));
			}
		}
	
		StringBuffer localeList = new StringBuffer(256);
		for (Locale l : Message.getLocales()) {
		    localeList.append(row("data",column(30,l.getLanguage()+" "+l.getCountry()+" "+l.getVariant())+column(30,l.getDisplayLanguage()+" "+l.getDisplayCountry()+" "+l.getDisplayVariant())));
		}
		
/*		StringBuffer securityReport = new StringBuffer();
//		OSecurity sec = con.getDb().getMetadata().getSecurity();
		QueryResult r = con.query("SELECT FROM ORole");
		for (ODocument d : r.get()) {
			String n = d.field("name");
			securityReport.append("<BR>Role="+n+" rules=");
			Map<String,Object> m = d.field("rules");
			for (String k : m.keySet()) {
				securityReport.append(k+"="+m.get(k)+" ");
			}
		}
		securityReport.append("<BR>");
		QueryResult r2 = con.query("SELECT FROM OUser");
		for (ODocument d : r2.get()) {
			String n = d.field("name");
			securityReport.append("<BR>User="+n+" roles=");
			OMVRBTreeRIDSet m = d.field("roles");
			for (Object k : m.toArray()) {
				if (k instanceof ODocument) {
					ODocument nd = (ODocument)k;
					securityReport.append(nd.field("name")+" ");
				}
			}
		}
*/		
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

		String installMessages = DatabaseSetup.getMessages();
		
		return
		    paragraph("Server has been running since "+Server.getServerInitTime())
			+(con.getUser().equals("admin") ? form(button("CHECK_INSTALLATION","CHECKINSTALLATION","Check Installation")) : "")
		    +(installMessages != null && !installMessages.equals("") ? paragraph("banner","Installation messages")+paragraph(installMessages) : "")
		    +paragraph("banner","Sessions")
			+sessionReport.toString()
		    +paragraph("banner","Cached Lists")
		    +table("data",
			  row(tableHead("Query")+tableHead("Size")+tableHead("Time of execution"))
			  +cacheList.toString()
			  )
			+form(button("REFRESH_CACHE_ALL","REFRESHCACHE","Clear all cached lists")+hidden("TABLE_NAME","ALL"))
			+form(button("REFRESH_COLUMNS_ALL","REFRESHCOLUMNS","Clear all cached table columns")+hidden("CLEAR_COLUMNS","ALL"))
			+form(button("REFRESH_MENUS_ALL","REFRESHMENUS","Clear all cached menus")+hidden("CLEAR_MENUS","ALL"))
		    +paragraph("banner","Active Locales")
		    +table("data",
			  row(tableHead("Name")+tableHead("Description"))+
			  localeList.toString()
			  )
			+paragraph("banner","Security")
			+paragraph("Last updated "+Server.getSecurityRefreshTime())
			+form(button("REFRESH_SECURITY_MODEL","REFRESHSEC","Refresh security"));
//			securityReport.toString();
    }

}
