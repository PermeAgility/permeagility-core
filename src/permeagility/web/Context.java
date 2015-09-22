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

import java.util.List;
import java.util.Locale;

import permeagility.plus.PlusSetup;
import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.PlusClassLoader;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class Context extends Weblet {

    public static String TEST_MODULE = null;
    public static boolean DEBUG = true;  // Normally its nice to see these messages in the log

    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
      return 	head("Context")+
                body(standardLayout(con, parms, getHTML(con, parms)));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {

        Locale locale = con.getLocale();
        parms.put("SERVICE",Message.get(locale, "SERVER_CONTEXT"));

        // Do stuff if asked
        String ref = parms.get("TABLENAME");
        String submit = parms.get("SUBMIT");
        if (ref != null && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Cache refresh="+ref);
            getCache().refresh(ref);
        }

        if (submit != null && submit.equals("CACHE_CLEAR_MENUS") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Menu Cache refresh");
            Menu.clearCache();
        }

        if (submit != null && submit.equals("REFRESH_SECURITY") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: refresh security");
            Security.refreshSecurity();
        }

        String checkInst = parms.get("CHECK_INSTALLATION");
        if (submit != null && submit.equals("CHECK_INSTALLATION") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Check Installation");
            Setup.checkInstallation(con);
        }

        // Prepare cached query list
        StringBuilder cacheList = new StringBuilder();
        Object[] keys = getCache().keySet().toArray();
        for (Object key : keys) {
            QueryResult qr = getCache().get(key);
            if (qr != null) {
                cacheList.append(row("data",column(30,link("permeagility.web.Context?TABLENAME="+key,(String)key))+column(10,""+(qr.size()))+column(20,qr.getTime().toString())));
            }
        }

        // Prepare plus modules list and install/un-install plus modules as well
        String module = parms.get("MODULE");
        StringBuilder plusList = new StringBuilder();
        List<String> modules = PlusClassLoader.getModules();
        if (TEST_MODULE != null) {
                modules.add(TEST_MODULE);
        }
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
                            if (submit.equals("PLUS_REMOVE")) {
                                System.out.println("Removing "+m);
                                installed = !plusSetup.remove(con, parms, errors);
                            } else if (submit.equals("PLUS_UPGRADE")) { 
                                System.out.println("Upgrading "+m);				    				
                                installed = plusSetup.upgrade(con, parms, errors);				    				
                            }
                        } else if (submit.equals("PLUS_INSTALL")){
                            System.out.println("Installing "+m);
                            installed = plusSetup.install(con, parms, errors);
                        }
                    }
                    String inVersion = plusSetup.getInstalledVersion(con, plusSetup.getClass().getName());
                    String plusVersion = plusSetup.getVersion();
                    if (inVersion == null) {
                        installed = false;
                    }
                    String act = (installed ? (plusVersion.compareTo(inVersion)>0 ? "PLUS_UPGRADE" : "PLUS_REMOVE") : "PLUS_INSTALL");
                    plusList.append(row("data",
                        column(m)
                        +column(inVersion)
                        +column(plusVersion)
                        +column(popupForm("INSTALL-"+m,null,Message.get(locale,act),null,null
                            ,paragraph("banner",act+" "+m)
                            +hidden("MODULE",m)
                            +(installed ? (act.equals("PLUS_REMOVE") ? plusSetup.getRemoveForm(con) : plusSetup.getUpgradeForm(con)) : plusSetup.getAddForm(con))
                            +br()+center(submitButton(locale, act))
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
            paragraph(Message.get(locale, "SERVER_ON_PORT")+"&nbsp;"+Server.getHTTPPort()
                +br()+Message.get(locale, "SERVER_RUNNING")+"&nbsp;"+Server.getServerInitTime()
                +br()+Message.get(locale, "SERVER_JAR")+"&nbsp;"+Server.getCodeSource()
                +br()+Message.get(locale, "SERVER_CONNECT")+"&nbsp;"+Server.getDBName()
                +" "+Message.get(locale, "SERVER_USER")+"&nbsp;"+Server.getServerUser()
                +br()+Message.get(locale, "SERVER_VERSION")+"&nbsp;"+Server.getClientVersion())
            +paragraph("banner",Message.get(locale, "SERVER_CACHE"))
                +form(submitButton(locale, "CACHE_CLEAR_MENUS")
                    + "&nbsp;" + Message.get(locale, "CACHE_COUNT",""+Menu.cacheSize()))
            +table("data",
                  row(columnHeader(Message.get(locale, "CACHED_QUERY"))
                    +columnHeader(Message.get(locale, "CACHE_SIZE"))
                    +columnHeader(Message.get(locale, "CACHE_LASTREFRESH")))
                  +cacheList.toString())
                +form(submitButton(locale, "CACHE_CLEAR_LISTS")
                    +hidden("TABLENAME","ALL") + "&nbsp;"
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
                +form(submitButton(locale, "REFRESH_SECURITY")
                        +"&nbsp;"+Message.get(locale, "SECURITY_UPDATED")+"&nbsp;"+Server.getSecurityRefreshTime())
            +paragraph("banner",Message.get(locale,"SERVER_SETUP"))
                +form(submitButton(locale,"CHECK_INSTALLATION"))
                +(installMessages != null && !installMessages.equals("") ? installMessages : "");
    }

}
