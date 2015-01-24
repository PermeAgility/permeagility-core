/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.List;
import java.util.Locale;

import permeagility.plus.PlusSetup;
import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.PlusClassLoader;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

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
		if (cc != null && Server.isDBA(con)) {
		    if (DEBUG) System.out.println("Context: Column Cache refresh="+cc);
			Server.clearColumnsCache("ALL");
		}
	
		String cm = parms.get("CLEAR_MENUS");
		if (cm != null && Server.isDBA(con)) {
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
			Setup.checkInstallation(con);
		}
    	
		// Prepare cached query list
		StringBuilder cacheList = new StringBuilder();
		Object[] keys = getCache().keySet().toArray();
		for (Object key : keys) {
			QueryResult qr = getCache().get(key);
			if (qr != null) {
				cacheList.append(row("data",column(30,link("permeagility.web.Context?TABLE_NAME="+key,(String)key))+column(10,""+(qr.size()))+column(20,qr.getTime().toString())));
			}
		}

		// Prepare plus modules list and install/un-install plus modules as well
		String submit = parms.get("SUBMIT");
		String module = parms.get("MODULE");
		StringBuilder plusList = new StringBuilder();
		List<String> modules = PlusClassLoader.getModules();
		for (String m : modules) {
				String setupClassName = "permeagility.plus."+m.substring(5)+".PlusSetup";
				try {
					Class<?> classOf = Class.forName( setupClassName, true, PlusClassLoader.get() );
				    Object classInstance = classOf.newInstance();
				    if (classInstance instanceof PlusSetup) {
						StringBuilder errors = new StringBuilder();
				    	PlusSetup plusSetup = (PlusSetup)classInstance;
				    	boolean installed = plusSetup.isInstalled();
				    	if (submit != null && module != null && module.equals(m)) {
				    		if (installed) {
				    			if (submit.equals("Remove")) {
				    				System.out.println("Removing "+m);
				    				installed = !plusSetup.remove(con, parms, errors);
				    			} else if (submit.equals("Upgrade")) { 
				    				System.out.println("Upgrading "+m);				    				
				    				installed = plusSetup.upgrade(con, parms, errors);				    				
				    			}
				    		} else if (submit != null && submit.equals("Install")){
				    			System.out.println("Installing "+m);
				    			installed = plusSetup.install(con, parms, errors);
				    		}
				    	}
				    	String inVersion = plusSetup.getInstalledVersion(con, plusSetup.getClass().getName());
				    	String plusVersion = plusSetup.getVersion();
				    	String act = (installed ? (plusVersion.compareTo(inVersion)>0 ? "Upgrade" : "Remove") : "Install");
						plusList.append(row("data",
								column(m)
								+column(inVersion)
								+column(plusVersion)
								+column(popupForm("INSTALL-"+m,null,act,null,null
										,paragraph("banner",act+" "+m)
										+hidden("MODULE",m)
										+(installed ? (act.equals("Remove") ? plusSetup.getRemoveForm(con) : plusSetup.getUpgradeForm(con)) : plusSetup.getAddForm(con))
										+br()+center(submitButton(act))
								))
								+column(plusSetup.getInfo())
								+(errors.length() > 0 ? row(columnSpan(3,errors.toString())) : "")
						));
				    }
				} catch (Exception e) {
					e.printStackTrace();
				}
		}

		// Prepare session report
		StringBuilder sessionReport = new StringBuilder();
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
		String installMessages = Setup.getMessages();
		
		// Return content
		return
		    paragraph(Message.get(locale, "SERVER_ON_PORT")+"&nbsp;"+Server.HTTP_PORT
		    		+br()+Message.get(locale, "SERVER_RUNNING")+"&nbsp;"+Server.getServerInitTime()
		    		+br()+Message.get(locale, "SERVER_JAR")+"&nbsp;"+Server.getCodeSource()
		    		+br()+Message.get(locale, "SERVER_CONNECT")+"&nbsp;"+Server.DB_NAME
		    		+br()+Message.get(locale, "SERVER_USER")+"&nbsp;"+Server.getDatabase().getUser()
		    		+br()+Message.get(locale, "SERVER_VERSION")+"&nbsp;"+Server.getDatabase().getClientVersion())
		    +paragraph("banner",Message.get(locale, "SERVER_CACHE"))
			+form(submitButton("CLEAR_COLUMNS",Message.get(locale, "CACHE_CLEAR_COLUMNS"))
					+ "&nbsp;" + Message.get(locale, "CACHE_COUNT",""+Server.columnsCacheSize()))
			+br()
			+form(submitButton("CLEAR_MENUS",Message.get(locale, "CACHE_CLEAR_MENUS"))
					+ "&nbsp;" + Message.get(locale, "CACHE_COUNT",""+Menu.cacheSize()))
		    +table("data",
			  row(columnHeader(Message.get(locale, "CACHED_QUERY"))
					  +columnHeader(Message.get(locale, "CACHE_SIZE"))
					  +columnHeader(Message.get(locale, "CACHE_LASTREFRESH")))
			  +cacheList.toString())
			+form(submitButton("REFRESH_CACHE_ALL",Message.get(locale, "CACHE_CLEAR_LISTS"))
					+hidden("TABLE_NAME","ALL") + "&nbsp;"
					+Message.get(locale, "CACHE_COUNT",""+getCache().size()))
			+paragraph("banner",Message.get(locale, "PLUS_MODULES"))
		    +table("data",
			  row(columnHeader(Message.get(locale, "PLUS_NAME"))
					  +columnHeader(Message.get(locale, "PLUS_DB_VERSION"))
					  +columnHeader(Message.get(locale, "PLUS_VERSION"))
					  +columnHeader(Message.get(locale, "PLUS_SETUP"))
					  +columnHeader(Message.get(locale, "PLUS_DESCRIPTION")))
			  +plusList.toString())
			+paragraph("banner",Message.get(locale, "SERVER_SECURITY"))
			+sessionReport.toString()
			+form(submitButton("REFRESH_SECURITY_MODEL",Message.get(locale, "REFRESH_SECURITY"))
					+"&nbsp;"+Message.get(locale, "SECURITY_UPDATED")+"&nbsp;"+Server.getSecurityRefreshTime())
		    +paragraph("banner",Message.get(locale,"SERVER_SETUP"))
			+form(submitButton("CHECK_INSTALLATION",Message.get(locale,"CHECK_INSTALLATION")))
			+(installMessages != null && !installMessages.equals("") ? installMessages : "");
    }

}
