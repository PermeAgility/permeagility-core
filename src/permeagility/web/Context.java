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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.arcadedb.integration.exporter.Exporter;

import permeagility.plus.PlusSetup;
import permeagility.plus.json.JSONArray;
import permeagility.plus.json.JSONObject;
import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.PlusClassLoader;
import permeagility.util.QueryResultCache;
import permeagility.util.Setup;

public class Context extends Weblet {

    public static String TEST_MODULE = null;
    public static boolean DEBUG = true;  // Normally its nice to see these messages in the log
    
    static String currentVersion = null;
    static boolean downloading = false;
    static Date lastChecked = null;
    static String latestVersion = null;
    static String latestVersionDate = null;
    static int downloadPercent = 0;
    static String downloadURL = null;
    static boolean downloadedPlus = false;

    static String TAB_CONTENT = "syscontext"; // Contents of current tab: where to target responses

      // For testing apply update
//    static Date lastChecked = new Date();
//    static String latestVersion = "permeagility-0.6.1.jar";
//    static String latestVersionDate = new Date().toString();
//    static int downloadPercent = 100; // For testing
//    static String downloadURL = "Test";

    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        String restOfURL = parms.get("REST_OF_URL");  
        if (restOfURL != null && !restOfURL.isEmpty()) {
            String[] restParts = restOfURL.split("/");  
            String tab = null;
            tab = restParts[0];
            if (tab != null && !tab.isBlank()) {
                return getTab(con, parms, tab);
            }
        }
        return getTabs()
            +serviceHeaderUpdateDiv(parms, "Server configuration");
    }

    public String getTabs() {
        ArrayList<String> tabNames = new ArrayList<String>();
        ArrayList<String> tabTargets = new ArrayList<String>();
        tabNames.add("Server");
        tabTargets.add("/Context/server");
        tabNames.add("Cache");
        tabTargets.add("/Context/cache");
        tabNames.add("Plus");
        tabTargets.add("/Context/plus");
        tabNames.add("Security");
        tabTargets.add("/Context/security");
        tabNames.add("Backup/Restore");
        tabTargets.add("/Context/backup");
        tabNames.add("Logs");
        tabTargets.add("/Context/logs");
        tabNames.add("Shutdown");
        tabTargets.add("/Context/shutdown");
        return getTabPanel(TAB_CONTENT, tabNames, tabTargets);
    }

    public String getTab(DatabaseConnection con, HashMap<String, String> parms, String tab) {
        return switch(tab) {
            case "server": yield getServerTab(con, parms);
            case "cache": yield getCacheTab(con, parms);
            case "plus": yield getPlusTab(con, parms);
            case "security": yield getSecurityTab(con, parms);
            case "backup": yield getBackupTab(con, parms);
            case "logs": yield getLogsTab(con, parms);
            case "shutdown": yield getShutdownTab(con, parms);
            default:
                System.out.println("Context: tab "+tab+" not recognized");
                yield paragraph("error","Context tab "+tab+" is under construction");
            };
    }

    String getServerTab(DatabaseConnection con, HashMap<String, String> parms) {
        Locale locale = con.getLocale();
        String submit = parms.get("SUBMIT");

        if (submit != null && submit.equals("CHECK_INSTALLATION") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Check Installation");
            Setup.checkInstallation(con);
        }

        if (submit != null && submit.equals("CHECK_FOR_UPDATE") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Checking for updated version");
            try {
                StringBuilder jsonString = new StringBuilder();
                URL latestURL = new URI("https://api.github.com/repos/permeagility/permeagility-core/releases/latest").toURL();
                URLConnection yc = latestURL.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    jsonString.append(inputLine);
                }
                in.close();
                JSONObject jsonObject = new JSONObject(jsonString.toString());
                String latestName = jsonObject.getString("name");
                String latestBody = jsonObject.getString("body");
                String latestPublishedDate = jsonObject.getString("published_at");
                JSONArray latestAssets = jsonObject.getJSONArray("assets");
                JSONObject latestAsset = latestAssets.getJSONObject(0);
                String latestFileName = latestAsset.getString("name");
                if (DEBUG) System.out.println("Latest: name="+latestName+" body="+latestBody+" fileName="+latestFileName);
                
                currentVersion = Server.getCodeSource();
                downloadURL = latestAsset.getString("browser_download_url");
                latestVersion = latestFileName;
                lastChecked = new Date();
                latestVersionDate = latestPublishedDate;
                if (DEBUG) System.out.println("Latest: name="+latestName+" body="+latestBody+" fileName="+latestFileName+" url="+downloadURL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

         if (submit != null && submit.equals("DOWNLOAD_UPDATE") && Security.isDBA(con) && !downloading && downloadURL != null && latestVersion != null && latestVersion.endsWith(".jar")) {
            if (DEBUG) System.out.println("Context: Downloading latest version");
            downloading = true;
            new Thread() {
                @Override public void run() {
                    try {
                        URL latestURL = new URI(downloadURL).toURL();
                        URLConnection yc = latestURL.openConnection();
                        int dlLength = yc.getContentLength();
                        InputStream input = yc.getInputStream();
                        OutputStream output = new FileOutputStream(new File(latestVersion));
                        int n = -1;
                        int nTotal = 0;
                        byte[] buffer = new byte[4096];
                        while (( n = input.read(buffer)) != -1) {
                            output.write(buffer, 0, n);
                            nTotal += n;
                            downloadPercent = (int)((float)nTotal/(float)dlLength*100.0);
                        }
                        output.close();
                        input.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        downloading = false;                        
                    }                   
                }
            }.start();
        }
   
        StringBuilder updateApplied = new StringBuilder();
        if (submit != null && submit.equals("APPLY_UPDATE") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Applying update as link modification");
            String os = System.getProperty("os.name");
            if (os != null && os.toLowerCase().startsWith("window")) {
                System.out.println("Cannot do this in Windows, sorry");
            }
            try {
                String[] cmd = {"ln -sf "+latestVersion+" permeagility.jar"};
                Process proc = Runtime.getRuntime().exec(cmd);
                int procStat = proc.waitFor();
                if (procStat == 0) {
                    updateApplied.append(paragraph("success","The symbolic link has been updated - restart the server using the shutdown (with restart)"));
                } else {
                    updateApplied.append(paragraph("error","The update of the symbolic link returned "+procStat+". do you run permeagility through a symbolic link?"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String act = "/Context/server";
        // Get install messages
        String installMessages = Setup.getMessages();
        return  paragraph("banner", Message.get(locale, "SERVER_SETUP"))
                +paragraph(Message.get(locale, "SERVER_ON_PORT") + "&nbsp;" + bold(""+Server.getHTTPPort())
                + " " + Message.get(locale, "SERVER_RUNNING") + "&nbsp;" + Server.getServerInitTime())
                +paragraph( Message.get(locale, "SERVER_CONNECT") + "&nbsp;" + bold(Server.getDBName())
                + " " + Message.get(locale, "SERVER_USER") + "&nbsp;" + bold(Server.getServerUser())
                + " " + Message.get(locale, "SERVER_VERSION") + "&nbsp;" + bold(Server.getClientVersion()))
                +paragraph( Message.get(locale, "SERVER_JAR") + "&nbsp;" + bold(Server.getCodeSource()) 
                +(updateApplied.length() > 0 ? updateApplied.toString()
                   : (Server.getCodeSource().equals("classes") ? "" : formHTMX("checkinst",act,"post",TAB_CONTENT,submitButton(locale, "CHECK_FOR_UPDATE")))
                    + (latestVersion != null ? br() + "Latest="+latestVersion+"("+latestVersionDate+")        checked:"+lastChecked.toString() : "")
                    + (latestVersion != null && !latestVersion.equals(Server.getCodeSource()) && !downloading ? formHTMX("dlupdate", act, "post", TAB_CONTENT, submitButton(locale, "DOWNLOAD_UPDATE")) : "")
                    + (downloading ? downloadPercent < 100 ? br()+Message.get(locale, "DOWNLOADING_UPDATE")+" "+downloadPercent+"%" : Message.get(locale, "DOWNLOADING_COMPLETE") : "")
                    + (downloadURL != null && !downloadedPlus && downloadPercent == 100 ? formHTMX("doupdate", act, "post", TAB_CONTENT, submitButton(locale, "APPLY_UPDATE")) : "")))
                + paragraph("banner", Message.get(locale, "SERVER_SETUP"))
                + formHTMX("checkinst",act, "post", TAB_CONTENT, submitButton(locale, "CHECK_INSTALLATION"))
                + (installMessages != null && !installMessages.equals("") ? installMessages : "");
    }

    String getCacheTab(DatabaseConnection con, HashMap<String, String> parms) {
        Locale locale = con.getLocale();
        String submit = parms.get("SUBMIT");
        String ref = parms.get("TABLENAME");
        if (ref != null && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Cache refresh=" + ref);
            getCache().refresh(ref);
        }

        if (submit != null && submit.equals("CACHE_CLEAR_MENUS") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Menu Cache refresh");
            Menu.clearCache();
        }

       // Prepare cached query list
        StringBuilder cacheList = new StringBuilder();
        Object[] keys = getCache().keySet().toArray();
        for (Object key : keys) {
            QueryResultCache qr = getCache().get(key);
            if (qr != null) {
                cacheList.append(row("data", column(30, linkHTMX("permeagility.web.Context/cache?TABLENAME=" + key, (String) key, TAB_CONTENT)) + column(10, "" + (qr.size())) + column(20, qr.getTime().toString())));
            }
        }
       return paragraph("banner", Message.get(locale, "SERVER_CACHE"))
                + formHTMX("clearmenus","/Context/cache", "post", TAB_CONTENT, submitButton(locale, "CACHE_CLEAR_MENUS")
                        + "&nbsp;" + Message.get(locale, "CACHE_COUNT", "" + Menu.cacheSize()))
                + table("data",
                        row(columnHeader(Message.get(locale, "CACHED_QUERY"))
                                + columnHeader(Message.get(locale, "CACHE_SIZE"))
                                + columnHeader(Message.get(locale, "CACHE_LASTREFRESH")))
                        + cacheList.toString())
                + formHTMX("clearlists","/Context/cache", "post", TAB_CONTENT, submitButton(locale, "CACHE_CLEAR_LISTS")
                        + hidden("TABLENAME", "ALL") + "&nbsp;"
                        + Message.get(locale, "CACHE_COUNT", "" + getCache().size()));
    }

    String getPlusTab(DatabaseConnection con, HashMap<String, String> parms) {
        Locale locale = con.getLocale();
        String submit = parms.get("SUBMIT");

        if (!downloading && submit != null && submit.equals("DOWNLOAD_PLUS_FILE") && Security.isDBA(con) && parms.get("DOWNLOAD_PLUS_URL") != null) {
            if (DEBUG) System.out.println("Context: Downloading plus");
            downloading = true;
            String plusFileURL = parms.get("DOWNLOAD_PLUS_URL");
            new Thread() {
                @Override public void run() {
                    try {
                        String fileName = "plus" + plusFileURL.substring(plusFileURL.lastIndexOf(File.separator));
                        if (DEBUG) System.out.println("Downloading "+plusFileURL+" to "+fileName);
                        URL latestURL = new URI(plusFileURL).toURL();
                        URLConnection yc = latestURL.openConnection();
                        int dlLength = yc.getContentLength();
                        InputStream input = yc.getInputStream();
                        OutputStream output = new FileOutputStream(new File(fileName));
                        int n = -1;
                        int nTotal = 0;
                        byte[] buffer = new byte[4096];
                        while (( n = input.read(buffer)) != -1) {
                            output.write(buffer, 0, n);
                            nTotal += n;
                            downloadPercent = (int)((float)nTotal/(float)dlLength*100.0);
                        }
                        output.close();
                        input.close();
                        downloadedPlus = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        downloading = false;                        
                    }                   
                }
            }.start();
            
        }
        
        if (submit != null && submit.equals("DOWNLOAD_PLUS") && Security.isDBA(con)) {
            if (DEBUG)  System.out.println("Context: Checking for plus modules");
            StringBuilder plusList = new StringBuilder();        
            List<String> modules = PlusClassLoader.getModules();
        
            JSONArray jsonArray = getURLArray("https://api.github.com/orgs/permeagility/repos");
            for (int i=0; i<jsonArray.length(); i++) {                
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String plusName = jsonObject.getString("name");
                if (plusName.startsWith("permeagility-plus-")) {
                    String plusDesc = jsonObject.getString("description");
                    if (DEBUG) System.out.println("GitHub Plus found: "+plusName);
                    JSONObject jsonPlus = getURLObject("https://api.github.com/repos/permeagility/"+plusName+"/releases/latest");
                    if (jsonPlus != null) {
                        boolean exists = false;
                        for (String m : modules) {  if (plusName.endsWith(m)) { exists = true; break; } }
                        String latestName = jsonPlus.getString("name");
                        String latestBody = jsonPlus.getString("body");
                        if (!latestBody.isEmpty()) latestBody = latestBody.replace("\n","<br>");
                        String latestPublishedDate = jsonPlus.getString("published_at");
                        if (jsonPlus.has("assets")) {
                            JSONArray latestAssets = jsonPlus.getJSONArray("assets");
                            JSONObject latestAsset = latestAssets.getJSONObject(0);
                            String latestFileName = latestAsset.getString("name");
                            long latestFileSize = latestAsset.getLong("size");
                            if (DEBUG) System.out.println("Latest: name="+latestName+" body="+latestBody+" fileName="+latestFileName);
                            String downloadPlusURL = latestAsset.getString("browser_download_url");
                            if (plusName.startsWith("permeagility-")) {
                                plusName = plusName.substring(13);
                            }
                            plusList.append(row(
                                    column(plusName)
                                    +column(plusDesc+"<br>"+xSmall(latestPublishedDate+"<br>"+latestBody))
                                    +column(latestName)
                                            +column(latestFileSize/1024+"k")
                                    +column(exists ? Message.get(locale, "DOWNLOADING_COMPLETE") 
                                        : formHTMX("dlplus","/Context/plus", "post", TAB_CONTENT, 
                                            hidden("DOWNLOAD_PLUS_URL",downloadPlusURL)+submitButton(locale,"DOWNLOAD_PLUS_FILE")))
                            ));
                        } else {
                            System.out.println("No assets");
                        }
                    } else {
                        System.out.println("No releases");
                    }
                }
            }
            if (plusList.length() > 0) {
                parms.put("SERVICE", Message.get(locale, "DOWNLOAD_PLUS"));
                return table("sortable",
                    row(columnHeader(Message.get(locale, "PLUS_NAME"))
                        + columnHeader(Message.get(locale, "PLUS_DESCRIPTION"))
                        + columnHeader(Message.get(locale, "PLUS_VERSION"))
                        + columnHeader(Message.get(locale, "PLUS_SIZE"))
                        + columnHeader(Message.get(locale, "DOWNLOAD_PLUS_FILE")))
                    +plusList.toString())
                    +formHTMX("dlcan", "/Context/plus", "post", TAB_CONTENT, submitButton(locale,"CANCEL"));
            } else {
                return paragraph("warning","NO_PLUS_MODULES")
                    +formHTMX("noplus","/Context/plus", "post", TAB_CONTENT, submitButton(locale,"CANCEL"));
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
            String plusName = (m.indexOf("-",5) > 0 ? m.substring(5,m.indexOf("-",5)) : m.substring(5));
            //System.out.println("Loading plus module: "+m+" using name: "+plusName);
            String setupClassName = "permeagility.plus." + plusName + ".PlusSetup";
            try {
                Class<?> classOf = Class.forName(setupClassName, true, PlusClassLoader.get());
                Object classInstance = classOf.getDeclaredConstructor().newInstance();
                if (classInstance instanceof PlusSetup) {
                    StringBuilder errors = new StringBuilder();
                    PlusSetup plusSetup = (PlusSetup) classInstance;
                    plusSetup.setPackage(plusName);
                    boolean installed = plusSetup.isInstalled();
                    if (submit != null && module != null && module.equals(m)) {
                        if (installed) {
                            if (submit.equals("PLUS_REMOVE")) {
                                if (DEBUG) System.out.println("Removing " + m);
                                installed = !plusSetup.remove(con, parms, errors);
                            } else if (submit.equals("PLUS_UPGRADE")) {
                                if (DEBUG) System.out.println("Upgrading " + m);
                                installed = plusSetup.upgrade(con, parms, errors);
                            }
                        } else if (submit.equals("PLUS_INSTALL")) {
                            if (DEBUG) System.out.println("Installing " + m);
                            installed = plusSetup.install(con, parms, errors);
                        }
                    }
                    String inVersion = plusSetup.getInstalledVersion(con, plusSetup.getClass().getName());
                    String plusVersion = plusSetup.getVersion();
                    if (inVersion == null && !plusSetup.isInstalled()) {
                        installed = false;
                    }
                    String act = (installed ? (plusVersion != null && plusVersion.compareTo(inVersion) > 0 ? "PLUS_UPGRADE" : "PLUS_REMOVE") : "PLUS_INSTALL");
                    plusList.append(row("data",
                            column(m)
                            + column(inVersion)
                            + column(plusVersion==null ? Message.get(locale,"PLUS_EMBEDDED") : plusVersion)
                            + column(popupFormHTMX("INSTALL-" + m, "/Context/plus", "post", TAB_CONTENT, Message.get(locale, act), null, 
                                            paragraph("banner", Message.get(locale, act) + " " + m)
                                            + hidden("MODULE", m)
                                            + (installed ? (act.equals("PLUS_REMOVE") ? plusSetup.getRemoveForm(con) : plusSetup.getUpgradeForm(con)) : plusSetup.getAddForm(con))
                                            + br() + center(submitButton(locale, act) + POPUP_FORM_CLOSER)
                                    ))
                            + column(plusSetup.getInfo())
                            + (errors.length() > 0 ? row(columnSpan(3, errors.toString())) : "")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return paragraph("banner", Message.get(locale, "PLUS_MODULES"))
                + (downloading ? "" : form(submitButton(locale, "DOWNLOAD_PLUS")))
                + (downloadedPlus ? paragraph("warning",Message.get(locale,"RESTART_REQUIRED")) : "")
                + table("data",
                        row(columnHeader(Message.get(locale, "PLUS_NAME"))
                                + columnHeader(Message.get(locale, "PLUS_DB_VERSION"))
                                + columnHeader(Message.get(locale, "PLUS_VERSION"))
                                + columnHeader(Message.get(locale, "PLUS_SETUP"))
                                + columnHeader(Message.get(locale, "PLUS_DESCRIPTION")))
                        + plusList.toString());

    }

    String getSecurityTab(DatabaseConnection con, HashMap<String, String> parms) {
        Locale locale = con.getLocale();
        String submit = parms.get("SUBMIT");

        if (submit != null && submit.equals("REFRESH_SECURITY") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: refresh security");
            Security.refreshSecurity(con);
        }

       // Prepare session report
        StringBuilder sessionReport = new StringBuilder();
        sessionReport.append("<p>" + Message.get(locale, "SERVER_SESSIONS") + "<br>");
        for (String usr : Server.sessionsDB.keySet()) {
            if (usr != null) {
                int scnt = 0;
                for (String cookie : Server.sessions.keySet()) {
                    String cu = Server.sessions.get(cookie);
                    if (cu != null) {
                        if (cu.equals(usr)) {
                            scnt++;
                        }
                    }
                }
                Database udb = Server.sessionsDB.get(usr);
                if (udb != null) {
                    sessionReport.append(usr + " sessions=" + scnt + "<BR>");
                }
            }
        }
        sessionReport.append("</p>");

        return paragraph("banner", Message.get(locale, "SERVER_SECURITY"))
                + sessionReport.toString()
                + formHTMX("refreshsec","/Context/security","post", TAB_CONTENT, submitButton(locale, "REFRESH_SECURITY")
                        + "&nbsp;" + Message.get(locale, "SECURITY_UPDATED") + "&nbsp;" + Server.getSecurityRefreshTime());

    }

    String getBackupTab(DatabaseConnection con, HashMap<String, String> parms) {
        Locale locale = con.getLocale();
        StringBuilder errors = new StringBuilder();

        if (!Security.isDBA(con)) {
        return paragraph("error",Message.get(locale, "RESTORE_ACCESS"));
        }

        File backupDir = new File("backup");
        if (backupDir == null || !backupDir.exists()) {
                errors.append(paragraph("error",Message.get(locale, "BACKUP_DIRECTORY_CREATED")));
                backupDir.mkdir();
        } else {
                if (!backupDir.isDirectory()) {
                        boolean success = backupDir.mkdir();
                        if (success) {
                                errors.append(paragraph("warning",Message.get(locale, "BACKUP_DIRECTORY_CREATED")));
                        } else {
                                errors.append(paragraph("error","Backup directory could not be created - cannot perform backups"));					
                        }
                }
        }
        String submit = parms.get("SUBMIT");
        StringBuilder exportLog = new StringBuilder();

        if (submit != null && submit.equals("BACKUP_NOW")) {
                String backupName = parms.get("BACKUP_FILENAME");
                System.out.println("Creating backup of database to file "+backupName);
                if (backupName.equals("")) {
                        errors.append(paragraph("error",Message.get(locale,"BACKUP_FILENAME_NEEDED")));
                } else {
                        try {
                                Exporter exp = new Exporter(con.getDb(), "backup/"+backupName+".json.gz");
                                exp.setFormat("jsonl");
                                Map<String, Object> results = exp.exportDatabase();
                                for (String k : results.keySet()) {
                                    errors.append(paragraph(k + ": " + results.get(k)));
                                }
                                errors.append(paragraph("success",Message.get(locale,"BACKUP_SUCCESS")+backupName));
                        } catch (Exception e1) {
                                e1.printStackTrace();
                                errors.append(paragraph("error",Message.get(locale,"BACKUP_FAIL")+e1.getLocalizedMessage()));
                        }
                }
        }

        if (submit != null && submit.equals("RESTORE_NOW")) {
            if (Server.getDBName().startsWith("remote:")) {
                return paragraph("error",Message.get(locale, "RESTORE_PLOCAL"));
            }

            if (parms.get("CONFIRM") != null && parms.get("CONFIRM").equals("YES") && parms.get("RESTORE") != null) {
                System.out.println("Restoring the database from file "+parms.get("RESTORE"));
                Server.restore_lockout = true;
                Server.restore_file = "backup/"+parms.get("RESTORE");

                Thread restore_thread = new Thread() {
                    public void run() {

                        // Wait 5 seconds for requests to clear
                        try {
                                System.out.println("Waiting 5 seconds for requests to clear...");
                                sleep(5000);
                        } catch (InterruptedException e) {
                                e.printStackTrace();
                        }

                        System.out.println("Shutting down the database connections");
                        Server.closeAllConnections();

                        // Wait 5 seconds for requests to clear
                        try {
                            System.out.println("Waiting 5 seconds for connections to clear...");
                            sleep(5000);
                        } catch (InterruptedException e) {
                                e.printStackTrace();
                        }

                        System.gc();

                        String dbDirectory = Server.getDBName().split(":")[1];

                        // Delete the database files in the db_saved directory if they exist from a previous restore
                        File dbSaved = new File(dbDirectory+"_saved");
                        if (dbSaved.isDirectory()) {
                            boolean deletesuccess = true;
                            for (File c : dbSaved.listFiles()) {
                                try {
                                    if (!deleteFile(c)) {
                                        deletesuccess = false;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    deletesuccess = false;
                                }
                            }
                            if (deletesuccess) {
                                dbSaved.delete();
                            } else {
                                System.out.println("Error deleting saved database before restore - exiting");
                                Server.exit(-4);
                            }
                        }

                        // Rename the database directory
                        File dbdata = new File(dbDirectory);
                        if (dbdata.isDirectory()) {
                            try {
                                dbdata.renameTo(new File(dbDirectory+"_saved"));
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Error renaming old database - exiting");
                                Server.exit(-5);
                            }
                        } else {
                            System.out.println("The data directory "+dbDirectory+" is not a directory - aborting restore - sorry");
                            Server.exit(-6);
                        }

                        // Because the stuff below doesn't work we have to restart and use the settings file to pass the backup file name
                        System.out.println("Setting restore localSetting for restore on startup");
                        Server.setLocalSetting("restore", Server.restore_file);

                        System.out.println("Exit with restart (1)");
                        Server.exit(1);

                        // This doesn't work because the server keeps remembering the database even though the files are gone (well, directory renamed)
/*						Server.initializeServer(Server.restore_file);
                        System.out.println("We are back up now - I hope");
*/
                        }
                    };

                    restore_thread.start();
                    return redirect(parms, "/");
            } else {
                return errors
                    +paragraph("banner",Message.get(locale, "CONFIRM_RESTORE",parms.get("RESTORE")))
                    +formHTMX("backrest","/Context/backup","post",TAB_CONTENT,
                        hidden("CONFIRM","YES")
                        +hidden("RESTORE",parms.get("RESTORE"))
                        +paragraph(Message.get(locale, "RESTORE_CONFIRM"))
                        +submitButton(locale,"RESTORE_NOW")
                        +submitButton(locale,"CANCEL")
                    );
            }
        }

        StringBuilder restorePoints = new StringBuilder();
        File[] backupFiles = backupDir.listFiles();
        if (backupFiles != null) {
            for (int i=0; i<backupFiles.length; i++) {
                if (backupFiles[i].getName().endsWith(".gz") || backupFiles[i].getName().endsWith(".json")) {
                    String fileSizeString;
                    DecimalFormat sizeFormat = new DecimalFormat("#0.0");
                    long fileSize = backupFiles[i].length();
                    if (fileSize/1024 < 1024) {
                        fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0)+"KB";
                    } else if (fileSize/1024/1024 < 1024) {
                        fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0/1024.0)+"MB";		
                    } else {
                        fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0/1024.0/1024.0)+"GB";							
                    }
                    restorePoints.append(row(
                        column(link("/backup/"+backupFiles[i].getName(),backupFiles[i].getName()))
                        +column(fileSizeString)
                        +column(""+(new Date(backupFiles[i].lastModified())))
                        +column(formHTMX("restore","/Context/backup", "post", TAB_CONTENT, hidden("RESTORE",backupFiles[i].getName())+submitButton(locale, "RESTORE_NOW") ) )
                    ));
                }
            }
        }
        String backupFilename = "Backup_"+formatDate(locale,new Date(),"yyyy-MM-dd_HH-mm");
    	return errors
    		+paragraph("banner", Message.get(locale, "BACKUP_THE_DATABASE"))
    		+formHTMX("backnow", this.getClass().getName()+"/backup", "post", TAB_CONTENT, table("layout",
                    row(column("label",Message.get(locale, "BACKUP_FILENAME"))+column(input("BACKUP_FILENAME",backupFilename,40)))
                    +row(column("")+column(submitButton(locale,"BACKUP_NOW")))
	        ))
	    	+paragraph("banner", Message.get(locale, "RESTORE_THE_DATABASE"))
	    	+form(table("sortable", 
                    row(columnHeader(Message.get(locale, "BACKUP_FILENAME"))
                        +columnHeader(Message.get(locale, "BACKUP_SIZE"))
                        +columnHeader(Message.get(locale, "BACKUP_DATE"))
                        +columnHeader(Message.get(locale, "RESTORE_NOW")))
                +restorePoints.toString()
	    	))
	    	+(exportLog != null ? exportLog.toString() : "");
    }

    String getLogsTab(DatabaseConnection con, HashMap<String, String> parms) {
        Locale locale = con.getLocale();

        // Prepare log file list
        File logDir = new File("log");
        StringBuilder logs = new StringBuilder();
        if (logDir.isDirectory()) {
            File[] logFiles = logDir.listFiles();
            if (logFiles != null) {
                for (int i = 0; i < logFiles.length; i++) {
                    if (logFiles[i].getName().endsWith(".log")) {
                        String fileSizeString;
                        DecimalFormat sizeFormat = new DecimalFormat("#0.0");
                        long fileSize = logFiles[i].length();
                        if (fileSize / 1024 < 1024) {
                            fileSizeString = "" + sizeFormat.format((double) fileSize / 1024.0) + "KB";
                        } else if (fileSize / 1024 / 1024 < 1024) {
                            fileSizeString = "" + sizeFormat.format((double) fileSize / 1024.0 / 1024.0) + "MB";
                        } else {
                            fileSizeString = "" + sizeFormat.format((double) fileSize / 1024.0 / 1024.0 / 1024.0) + "GB";
                        }
                        logs.append(row(
                                column(linkNewWindow("/log/" + logFiles[i].getName(), logFiles[i].getName()))
                                + column(fileSizeString)
                                + column("" + (new Date(logFiles[i].lastModified())))
                        ));
                    }
                }
            }
        }

        return (logs.length() > 0 ? table("sortable",
                                row(columnHeader(Message.get(locale, "LOG_FILENAME"))
                                        + columnHeader(Message.get(locale, "LOG_SIZE"))
                                        + columnHeader(Message.get(locale, "LOG_DATE")))
                                + logs.toString())
                        : bold(Message.get(locale, "LOGGING_TO_CONSOLE")));
    }

    String getShutdownTab(DatabaseConnection con, HashMap<String, String> parms) {
        if (parms.get("SUBMIT") == null || !parms.get("SUBMIT").equals("CONFIRM_SHUTDOWN")) {
            return  paragraph("banner", Message.get(con.getLocale(), "CONFIRM_SHUTDOWN"))
                    + formHTMX("shutdown",this.getClass().getName()+"/shutdown", "post", TAB_CONTENT,
                            hidden("SHUTDOWN", parms.get("SHUTDOWN"))
                            + paragraph(Message.get(con.getLocale(), "SHUTDOWN_CONFIRM_MESSAGE"))
                            + paragraph(checkbox("WITH_RESTART", true) + "&nbsp;" + Message.get(con.getLocale(), "SHUTDOWN_RESTART"))
                            + submitButton(con.getLocale(), "CONFIRM_SHUTDOWN")
                            + "&nbsp;&nbsp;&nbsp;&nbsp;"
                            + submitButton(con.getLocale(), "CANCEL")
                    );
        } else {
            System.out.println("Database and Server shutdown initiated by user " + con.getUser());
            Server.restore_lockout = true;
            int exitCode = parms.get("WITH_RESTART") != null ? 1 : 0;
            (new Thread() {
                public void run() {
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    System.exit(exitCode);
                }
            }).start();
            return paragraph("error","Server is shutting down");
        }
    }

    public static JSONArray getURLArray(String url) {
        String a = getURL(url);
        return a == null ? null : new JSONArray(a);        
    }

    public static JSONObject getURLObject(String url) {
        String o = getURL(url);
        return o == null ? null : new JSONObject(o);
    }
            
    public static String getURL(String url) {
         try {
            StringBuilder jsonString = new StringBuilder();
             URL latestURL = new URI(url).toURL();
             URLConnection yc = latestURL.openConnection();
             BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
             String inputLine;
             while ((inputLine = in.readLine()) != null) {
                 jsonString.append(inputLine);
             }
             in.close();
             return jsonString.toString();
        } catch (Exception e) {
            System.out.println("Error retrieving URL: "+url+" error is "+e.getClass().getName());
        }
        return null;       
    }        

    private boolean deleteFile(File f) throws IOException {
    	if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteFile(c);
            }
            System.out.println("Deleting directory "+f);
            return f.delete();
    	} else {
            System.out.println("Deleting file "+f);
            return f.delete();
    	}
    }

}
