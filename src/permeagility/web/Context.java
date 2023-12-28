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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

      // For testing apply update
//    static Date lastChecked = new Date();
//    static String latestVersion = "permeagility-0.6.1.jar";
//    static String latestVersionDate = new Date().toString();
//    static int downloadPercent = 100; // For testing
//    static String downloadURL = "Test";

    public String getPage(DatabaseConnection con, java.util.HashMap<String, String> parms) {
        return head(con, "Context")
                + bodyMinimum(getHTML(con, parms));
    }

    public String getHTML(DatabaseConnection con, java.util.HashMap<String, String> parms) {

        Locale locale = con.getLocale();
        parms.put("SERVICE", Message.get(locale, "SERVER_CONTEXT"));

        // Do stuff if asked
        String ref = parms.get("TABLENAME");
        String submit = parms.get("SUBMIT");
        if (ref != null && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Cache refresh=" + ref);
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

        if (submit != null && submit.equals("CHECK_INSTALLATION") && Security.isDBA(con)) {
            if (DEBUG) System.out.println("Context: Check Installation");
            Setup.checkInstallation(con);
        }

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
                                    +column(exists ? Message.get(locale, "DOWNLOADING_COMPLETE") : form(hidden("DOWNLOAD_PLUS_URL",downloadPlusURL)+submitButton(locale,"DOWNLOAD_PLUS_FILE")))
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
                    +form(submitButton(locale,"CANCEL"));
            } else {
                return paragraph("warning","NO_PLUS_MODULES")
                    +form(submitButton(locale,"CANCEL"));
            }
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

        // Prepare cached query list
        StringBuilder cacheList = new StringBuilder();
        Object[] keys = getCache().keySet().toArray();
        for (Object key : keys) {
            QueryResultCache qr = getCache().get(key);
            if (qr != null) {
                cacheList.append(row("data", column(30, link("permeagility.web.Context?TABLENAME=" + key, (String) key)) + column(10, "" + (qr.size())) + column(20, qr.getTime().toString())));
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
            //System.out.println("Loading plus module: "+m);
            String setupClassName = "permeagility.plus." + (m.indexOf("-",5) > 0 ? m.substring(5,m.indexOf("-",5)) : m.substring(5)) + ".PlusSetup";
            try {
                Class<?> classOf = Class.forName(setupClassName, true, PlusClassLoader.get());
                Object classInstance = classOf.getDeclaredConstructor().newInstance();
                if (classInstance instanceof PlusSetup) {
                    StringBuilder errors = new StringBuilder();
                    PlusSetup plusSetup = (PlusSetup) classInstance;
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
                            + column(popupForm("INSTALL-" + m, null, Message.get(locale, act), null, null, paragraph("banner", Message.get(locale, act) + " " + m)
                                            + hidden("MODULE", m)
                                            + (installed ? (act.equals("PLUS_REMOVE") ? plusSetup.getRemoveForm(con) : plusSetup.getUpgradeForm(con)) : plusSetup.getAddForm(con))
                                            + br() + center(submitButton(locale, act))
                                    ))
                            + column(plusSetup.getInfo())
                            + (errors.length() > 0 ? row(columnSpan(3, errors.toString())) : "")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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

        // Get install messages
        String installMessages = Setup.getMessages();

        // Return content page
        return  paragraph("banner", Message.get(locale, "SERVER_SETUP"))
                +paragraph(Message.get(locale, "SERVER_ON_PORT") + "&nbsp;" + bold(""+Server.getHTTPPort())
                + " " + Message.get(locale, "SERVER_RUNNING") + "&nbsp;" + Server.getServerInitTime())
                +paragraph( Message.get(locale, "SERVER_CONNECT") + "&nbsp;" + bold(Server.getDBName())
                + " " + Message.get(locale, "SERVER_USER") + "&nbsp;" + bold(Server.getServerUser())
                + " " + Message.get(locale, "SERVER_VERSION") + "&nbsp;" + bold(Server.getClientVersion()))
                +paragraph( Message.get(locale, "SERVER_JAR") + "&nbsp;" + bold(Server.getCodeSource()) 
                +(updateApplied.length() > 0 ? updateApplied.toString()
                   : (Server.getCodeSource().equals("classes") ? "" : form(submitButton(locale, "CHECK_FOR_UPDATE")))
                    + (latestVersion != null ? br() + "Latest="+latestVersion+"("+latestVersionDate+")        checked:"+lastChecked.toString() : "")
                    + (latestVersion != null && !latestVersion.equals(Server.getCodeSource()) && !downloading ? form(submitButton(locale, "DOWNLOAD_UPDATE")) : "")
                    + (downloading ? downloadPercent < 100 ? br()+Message.get(locale, "DOWNLOADING_UPDATE")+" "+downloadPercent+"%" : Message.get(locale, "DOWNLOADING_COMPLETE") : "")
                    + (downloadURL != null && !downloadedPlus && downloadPercent == 100 ? form(submitButton(locale, "APPLY_UPDATE")) : "")))
                + (logs.length() > 0 ? table("sortable",
                                row(columnHeader(Message.get(locale, "LOG_FILENAME"))
                                        + columnHeader(Message.get(locale, "LOG_SIZE"))
                                        + columnHeader(Message.get(locale, "LOG_DATE")))
                                + logs.toString())
                        : bold(Message.get(locale, "LOGGING_TO_CONSOLE")))
                + paragraph("banner", Message.get(locale, "SERVER_CACHE"))
                + form(submitButton(locale, "CACHE_CLEAR_MENUS")
                        + "&nbsp;" + Message.get(locale, "CACHE_COUNT", "" + Menu.cacheSize()))
                + table("data",
                        row(columnHeader(Message.get(locale, "CACHED_QUERY"))
                                + columnHeader(Message.get(locale, "CACHE_SIZE"))
                                + columnHeader(Message.get(locale, "CACHE_LASTREFRESH")))
                        + cacheList.toString())
                + form(submitButton(locale, "CACHE_CLEAR_LISTS")
                        + hidden("TABLENAME", "ALL") + "&nbsp;"
                        + Message.get(locale, "CACHE_COUNT", "" + getCache().size()))
                + paragraph("banner", Message.get(locale, "PLUS_MODULES"))
                + (downloading ? "" : form(submitButton(locale, "DOWNLOAD_PLUS")))
                + (downloadedPlus ? paragraph("warning",Message.get(locale,"RESTART_REQUIRED")) : "")
                + table("data",
                        row(columnHeader(Message.get(locale, "PLUS_NAME"))
                                + columnHeader(Message.get(locale, "PLUS_DB_VERSION"))
                                + columnHeader(Message.get(locale, "PLUS_VERSION"))
                                + columnHeader(Message.get(locale, "PLUS_SETUP"))
                                + columnHeader(Message.get(locale, "PLUS_DESCRIPTION")))
                        + plusList.toString())
                + paragraph("banner", Message.get(locale, "SERVER_SECURITY"))
                + sessionReport.toString()
                + form(submitButton(locale, "REFRESH_SECURITY")
                        + "&nbsp;" + Message.get(locale, "SECURITY_UPDATED") + "&nbsp;" + Server.getSecurityRefreshTime())
                + paragraph("banner", Message.get(locale, "SERVER_SETUP"))
                + form(submitButton(locale, "CHECK_INSTALLATION"))
                + (installMessages != null && !installMessages.equals("") ? installMessages : "");
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
}
