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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import com.arcadedb.Constants;
import com.arcadedb.database.Document;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.streams.BufferedChannelInputStream;
import org.xnio.streams.ChannelOutputStream;
import permeagility.plus.json.JSONObject;
import permeagility.util.BitInputStream;
import permeagility.util.BitOutputStream;

import permeagility.util.ConstantOverride;
import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.Dumper;
import permeagility.util.PlusClassLoader;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

/** This is the PermeAgility web server - it handles security, database connections and some useful caches
  * all web requests go through the service() function for each thread/socket
  *
  *  Parameters: [port(1999)] [db] [selftest]
  */
public class Server {

	private static int HTTP_PORT = 1999;  // First parameter
	private static String DB_NAME = "db";  // Second parameter
	private static boolean SELF_TEST = false; // Will exit after initialization
	private static Date serverInitTime = new Date();
	private static String DEFAULT_DBFILE = "starterdb.json.gz";  //  Used at initial start
	private static String codeSource;  // The file this class is in, for version reporting
	static boolean restore_lockout = false;
	static String restore_file = null;  // Backup file to restore when initializing
	static long resource_jar_last_modified = 0l;
	static String resource_jar_last_modified__string = "";
	private static SimpleDateFormat gmt_date_format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

	/* Overrideable constants */
	public static boolean DEBUG = false;
    public static boolean LOG_REQUESTS = true;
    public static int WEBSOCKET_QUEUE_CHUNK = 100;   // Number of message to spit out from queue before a pause
    public static int WEBSOCKET_PAUSE_MS = 50;
	public static boolean ALLOW_KEEP_ALIVE = true;
	public static boolean KEEP_ALIVE = false;  // if true Keep sockets alive by default, don't wait for browser to ask
	public static String LOGIN_CLASS = "permeagility.web.Login";
	public static String REQUEST_CLASS = "permeagility.web.UserRequest";
	public static String HOME_CLASS = "permeagility.web.Home";
	public static String FAV_ICON = "/images/pa_icon_ani.gif";
	public static boolean WWW_IN_JAR = true; // If true Use Class loader for www files/images (include images in Jar)
	public static int SESSION_TIMEOUT = 3600000; // One hour (in ms) - override with -1 to have no timeout
	public static int GUEST_POOL_SIZE = 10;
	public static double GUEST_POOL_GROWTH_STEP = 5;  // When growing the pool, increase by this size
	public static double GUEST_POOL_GROWTH_FACTOR = 0.75; // When active connections reaches this portion of the MAX, increase the pool
	public static boolean ALLOW_GUEST_POOL_GROWTH = true;   // Will increase size by if active guest connections > 75% of pool
	public static int SERVER_POOL_SIZE = 5;
	public static boolean LOGOUT_KILLS_USER = false; // Set this to true to kill all sessions for a user when a user logs out (more secure)
	public static String LOCKOUT_MESSAGE = "<p>The system is unavailable because a system restore is being performed. Please try again later.</p><a href='/'>Try it now</a>";

	protected static Database database;  // Server database connection (internal)
	//private static Database dbNone = null;  // Used for guest access, login and account request

	private static String SETTINGS_FILE = "init.pa";
	private static Properties localSettings = new Properties();

	static ConcurrentHashMap<String,String> sessions = new ConcurrentHashMap<>(); // Cookie (random+user) -> username
	static ConcurrentHashMap<String,Locale> sessionsLocale = new ConcurrentHashMap<>(); // Cookie (random+user) -> locale
	static ConcurrentHashMap<String,Database> sessionsDB = new ConcurrentHashMap<>();  // username -> Database pool

	private static ConcurrentHashMap<String,byte[]> transientImages = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String,Date> transientImageDates = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String,String> transientImageTypes = new ConcurrentHashMap<>();

	private static ConcurrentHashMap<String,List<String>> pickValues = new ConcurrentHashMap<>();

	private static DatabaseHook databaseHook = null;
	private static ClassLoader plusClassLoader;

        // Websocket op codes
        public static final int WSOC_CONTINUOUS = 0, WSOC_TEXT = 1, WSOC_BINARY = 2, WSOC_PING = 9, WSOC_PONG = 10, WSOC_CLOSING = 8;


     //   protected static ArrayList<WebsocketSender.EventStreamListener> eventStreamListeners = new ArrayList<>();
     //   protected static ArrayList<EventStreamFilter> eventStreamFilters = new ArrayList<>();

	private static ExecutorService executor = Executors.newCachedThreadPool();

	public static void main(String[] args) {

		try { codeSource = new java.io.File(Server.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
		} catch (Exception e) { e.printStackTrace(); }

		try {
			HTTP_PORT = Integer.parseInt(args[0]);
			if (HTTP_PORT < 0 || HTTP_PORT > 65535) HTTP_PORT = 1999;
		}
		catch (Exception e) {
			HTTP_PORT = 1999;
		}
		if (args.length > 1) {
			DB_NAME = args[1];
		}
		System.out.println("Using port "+HTTP_PORT+" on database "+DB_NAME);

		if (args.length > 2 && args[2].equals("selftest")) {
			SELF_TEST = true;
			System.out.println("selftest is specified: will exit after initialization");
		}

                // Setup logging to a file or console via System.out or System.err
		if (System.console()==null) {
			System.out.println("Console is null - logging System.out and System.err to file");
			File logDir = new File("log");
			if (!logDir.isDirectory()) {
				boolean success = logDir.mkdir();
				if (success) {
					System.out.println("Log directory created");
				} else {
					// Use plain file called log in the home directory to force console output
					System.out.println("Log directory could not be created - cannot store logs");
				}
			}
			if (logDir.isDirectory()) {
				SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
				String logFilename = "pa_"+dateTimeFormat.format(new Date())+".log";
				try {
					PrintStream logPS = new PrintStream(new File(logDir,logFilename));
					System.setOut(logPS);
					System.setErr(logPS);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

                // Create an XNIO accept listener to spawn service loop/threads.
                final ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = (final AcceptingChannel<StreamConnection> channel) -> {
                    try {
                        final StreamConnection accepted = channel.accept();
                        if (accepted != null) {
                            executor.execute(() -> {
                                try {
                                    // Call the PermeAgility service and loop if returns true for keep alive
                                    while(service(new BufferedChannelInputStream(accepted.getSourceChannel(),1024), new ChannelOutputStream(accepted.getSinkChannel()))) {}
                                    accepted.close();
                                    channel.resumeAccepts();
                                 } catch (Exception e) {
                                    System.out.println("Exception in execute/run: "+e);
                                    IoUtils.safeClose(channel);
                                }
                            });
                        }
                    } catch (IOException ignored) {
                        System.out.println("Exception in acceptListener: "+ignored.getMessage());
                    }
                };
                // Start the server and initialize
                AcceptingChannel<? extends StreamConnection> server = null;

		try {
			// Start the XNNIO server to make sure we get the port first
            final XnioWorker worker = Xnio.getInstance().createWorker(OptionMap.EMPTY);
            server = worker.createStreamConnectionServer(new InetSocketAddress(HTTP_PORT), acceptListener, OptionMap.EMPTY);

			if (initializeServer()) {
                System.out.println("Server initialization completed successfully");
                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        System.out.println("ShutdownHook called - shutting down executors");
                        executor.shutdown();
                        closeAllConnections();
                    }
                });
                if (SELF_TEST) {
                    System.out.println("self test - exiting...");
                    server.close();
                    System.exit(0);
                } else {
                    server.resumeAccepts();
                    System.out.println("Accepting connections on port "  + HTTP_PORT + " localAddress="+ server.getLocalAddress());
                }
			} else {
                System.out.println("Failed to initialize server");
			}
		} catch (BindException b) {
            System.err.println("***\n*** Exit condition: BindException\n***"+b.getMessage());
            exit(-2);
		} catch (Exception e) {
            System.err.println("***\n*** Exit condition: Exception\n***"+e.getClass().getName()+":"+e.getMessage());
            e.printStackTrace();
            exit(-1);
		}
        // Stuff just runs now - all new connections will call service
	}

	public final static void exit(int returnCode) {
            System.out.println("Server exit with status "+returnCode);
            System.exit(returnCode);
	}

    // Requests get serviced here, including websockets
    public final static boolean service(InputStream is, OutputStream os) {

		String method;  // GET and POST are treated the same
		String content_type;
		String content_disposition = null; // For downloads
		String version = "";
		String cookieValue = null;
		String newCookieValue = null;
		Locale requestLocale = null;

		boolean keep_alive = KEEP_ALIVE;  // Get the dynamic default
                boolean websocket = false;
                String websocket_version = "";
                String websocket_key = "";
                String websocket_protocol = "";
                String websocket_extensions = "";
                WebsocketSender websocket_sender = null;
                BitInputStream bitsIn = null;

        	Database userdb = null;

            long startTime = 0;
            try {

                String get = readLine(is);
                while (get == null || get.isEmpty()) {
                    get = readLine(is);
                    System.out.println("****** Request:blank");
                }
                if (LOG_REQUESTS) System.out.print("REQUEST="+get+" ");
                StringTokenizer st = new StringTokenizer(get);
                if (!st.hasMoreTokens()) {
                        System.out.println("****** Request is null - returning no results");
                        return false;
                }
                if (st.hasMoreTokens()) {
                        method = st.nextToken();
                } else {
                        System.out.println("****** Request missing tokens - returning no results");
                        return false;
                }
                String file;
                if (st.hasMoreTokens()) {
                        file = st.nextToken();
                } else {
                        if (get.getBytes().length <= 3) {  // EF BF BF (Not sure whether this means keep alive or not so FU!
                                //if (ALLOW_KEEP_ALIVE) keep_alive = true;    //System.out.println("EFBFBF? FU");
                                return false;
                        } else {
                                System.out.println("Invalid file request "+" request="+Dumper.hexDump(get.getBytes()));
                                return false;
                        }
                }
                startTime = System.currentTimeMillis();
                if (method.equals("GET") || method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE")) {
                        content_type = getContentType(file);
                        if (st.hasMoreTokens()) {
                                version = st.nextToken();
                        }
                        // loop through the rest of the input lines
                        String boundaryValue = null;
                        get = readLine(is);
                        while (is.available()>0 && !get.trim().equals("")) {
                                if (DEBUG) System.out.println("REQUESTHEADER="+get);
                                if (get.startsWith("Cookie:")) {
                                        StringTokenizer cookiet = new StringTokenizer(get.substring(7)," =;");
                                        while (cookiet.hasMoreTokens() && !cookiet.nextToken().equalsIgnoreCase("value")) {}
                                        if (cookiet.hasMoreTokens()) {
                                                cookieValue = cookiet.nextToken();
                                                if (DEBUG) System.out.println("GOT COOKIE "+cookieValue);
                                        }
                                } else if (get.startsWith("Accept-Language:")) {
                                        String language = get.substring(16).trim().substring(0,2);
                                        if (DEBUG) System.out.println("Requested language="+language);
                                        requestLocale = new Locale.Builder().setLanguage(language).build();
                                } else if (ALLOW_KEEP_ALIVE && get.equalsIgnoreCase("Connection: keep-alive")) {
                                        keep_alive = true;
                                } else if (get.equalsIgnoreCase("Upgrade: websocket")) {
                                        keep_alive = true;
                                        websocket = true;
                                } else if (get.startsWith("Sec-WebSocket-Version:")) {
                                        websocket_version = get.substring(23).trim();
                                } else if (get.startsWith("Sec-WebSocket-Key:")) {
                                        websocket_key = get.substring(19).trim();
                                } else if (get.startsWith("Sec-WebSocket-Protocol:")) {
                                        websocket_protocol = get.substring(23).trim();
                                } else if (get.startsWith("Sec-WebSocket-Extensions:")) {
                                        websocket_extensions = get.substring(26);
                                } else if (get.startsWith("Content-Type: multipart")) {
                                        int bi = get.indexOf("boundary=");
                                        if (bi > 0) {
                                                boundaryValue = get.substring(bi+9);
                                        }
                                        if (DEBUG) System.out.println("Boundary="+boundaryValue);
                                }
                                get = readLine(is);
                        }

                        if (( method.equals("POST")  || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE") ) && boundaryValue == null ) {
                                if (DEBUG) System.out.println("Reading post stuff");
                                StringBuilder formstuff = new StringBuilder();
                                char firstchar = (char)is.read();
                                while (is.available() > 0) {
                                        formstuff.append((char)is.read());
                                }
                                file += "&" + firstchar + formstuff.toString();
                                if (DEBUG) System.out.println("POST Vars="+firstchar+formstuff);
                        }

                        // Prepare parameters
                        String className;
                        StringTokenizer st2 = new StringTokenizer(file.substring(1),"?&",false);
                        if (st2.hasMoreTokens()) {
                                className = st2.nextToken();
                        } else {
                                if (DEBUG) System.out.println("Could not determine target class, going home");
                                className = HOME_CLASS;
                        }

                        // Get URL encoded parameters and put in HashMap
                        HashMap<String,String> parms = new HashMap<>();
                        parms.put("HTTP_METHOD",method);
                        while (st2.hasMoreTokens()) {
                                String string2 = st2.nextToken("&");
                                if (string2.startsWith("?")) {
                                        string2 = string2.substring(1);
                                }
                                StringTokenizer st3 = new StringTokenizer(string2);
                                while (st3.hasMoreTokens()) {
                                        String parmName = st3.nextToken("=");
                                        if (st3.hasMoreTokens()) {
                                                String parmValue = URLDecoder.decode(st3.nextToken("&").substring(1).trim(),"UTF-8");
                                                if (parms.get(parmName) != null) {
                                                        parmValue = parms.get(parmName)+","+parmValue;
                                                }
                                                parms.put(parmName,parmValue);
                                        }
                                }
                        }

                        // Get multipart data and put into parms - files into temp files
                        if ( ( method.equals("POST")  || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE") ) && boundaryValue != null ) {
                                if (DEBUG) System.out.println("Reading multipart stuff");
                                @SuppressWarnings("unused")
                                int firstchar = is.read();
                                byte[] cbuf = new byte[boundaryValue.length()];
                                is.read(cbuf);
                                while (is.available() > 0) {
                                        readMultipartData(is, boundaryValue, parms);
                                }
                        }

                        // Some browsers cough badly if we don't read everything. This is just in case
                        if (is.available() > 0) {
                            if (DEBUG) System.out.println("Server: Found extra data: ");
                            while (is.available() > 0) {
                                    int ec = is.read();
                                    if (DEBUG) System.out.print(" "+Integer.toHexString(ec));
                            }
                            System.out.println(".");
                        }
                        // Prepare the output
                        if (restore_lockout) {
                                os.write(getLogHeader("text/html", LOCKOUT_MESSAGE.getBytes().length, keep_alive).getBytes());
                                os.write(LOCKOUT_MESSAGE.getBytes());
                                return false;
                        }

                        try {
                                if (DEBUG) System.out.println("DATA="+file);
                                byte[] theData = new byte[0];

                                // The little icon for browser tabs
                                if (file.startsWith("/favicon.ico")) {
                                        file = FAV_ICON;
                                }

                                // Internal images and JavaScript - anyone can have them
                                if (file.startsWith("/images/")
                                || file.startsWith("/js/")) {
                                        if (DEBUG) System.out.println("Looking for resource "+file);
                                        if (file.contains("?")) {
                                                file = file.substring(0,file.indexOf('?'));
                                                if (DEBUG) System.out.println("Removed parameter file= "+file);
                                        }
                                        // TODO: Should see if If-modified is specified and send back Not-modified in header
                                        InputStream iis = null;
                                        long filesize = 0;
                                        if (WWW_IN_JAR) {  // Get files from jar
                                                //file = "/www"+file;
                                                URL imageurl = Thread.currentThread().getContextClassLoader().getResource(file.substring(1));
                                                if (imageurl != null) {
                                                        URLConnection urlcon = imageurl.openConnection();
                                                        urlcon.setConnectTimeout(100);
                                                        urlcon.setReadTimeout(100);
                                                        filesize = urlcon.getContentLengthLong();
                                                        iis = imageurl.openStream();
                                                }
                                        } else {  // Look in file system
                                                file = "www"+file;
                                                File imagefile = new File(file);
                                                filesize = imagefile.length();
                                                if (imagefile != null) {
                                                        iis = new FileInputStream(imagefile);
                                                }
                                        }
                                        if (iis != null) {
                                                if (DEBUG) System.out.println("Returning "+filesize+" bytes for "+file);
                                                content_type = getContentType(file);
                                                os.write(getImageHeader(content_type, (int)filesize, keep_alive).getBytes());
                                                int b;  byte[] buf = new byte[1024];
                                                while ((b = iis.read(buf)) != -1) {
                                                    os.write(buf, 0, b);
                                                }
                                                iis.close();
                                                os.flush();
                                                return keep_alive;
                                        } else {
                                                System.out.println("Could not retrieve image/file "+file);
                                                os.write(("HTTP/1.1 404 File Not Found\r\n").getBytes());
                                                os.write(("Date: " + new java.util.Date() + "\r\n").getBytes());
                                                os.write(("Server: PermeAgility 1.0\r\n").getBytes());
                                                os.write(("Content-type: text/html" + "\r\n\r\n").getBytes());
                                                os.flush();
                                                 return keep_alive;
                                        }
                                }

                                // Authenticate the user
                                if (cookieValue != null) {
                                        if (DEBUG) System.out.println("Server: looking for cookie |"+cookieValue+"| there are "+sessions.size()+" sessions");
                                        String u = sessions.get(cookieValue);
                                        if (u != null) {
                                                userdb = sessionsDB.get(u);
                                                if (DEBUG) System.out.println("Server: session lookup found "+userdb);
                                        }
                                }

                                // Logout if logged in and login class requested
                                if (userdb != null && className.equals(LOGIN_CLASS)) {  // If you ask to login and you are already connected, then you have asked to log out
                                        System.out.println("User "+userdb.getUser()+" logging out");
                                        sessions.remove(cookieValue);
                                                // Should remove other cookies for this user (They asked to log out - their user ID will be removed from memory)
                                                if (LOGOUT_KILLS_USER) {
                                                        String u = userdb.getUser();
                                                        if (!u.equals("guest")) {  // Except for guest - leave them alone - there may be lots of them
                                                                ArrayList<String> cookiesToRemove = new ArrayList<String>();
                                                                for (String c : sessions.keySet()) {
                                                                        String s = sessions.get(c);
                                                                        if (s != null && s.equals(u)) {
                                                                                cookiesToRemove.add(c);
                                                                        }
                                                                }
                                                                for (String c : cookiesToRemove) {
                                                                        System.out.println("Alternate session found ("+c+") and will be killed");
                                                                        sessions.remove(c);
                                                                        sessionsLocale.remove(c);
                                                                }
                                                                //sessionsDB.remove(u).close();
                                                        }
                                                }
                                                userdb = null;
                                                className = HOME_CLASS;
                                }

                                // If username specified in parms and we do not have a connection we must be logging in
                                String parmUserName = parms.get("USERNAME");
                                if (userdb == null || parmUserName != null) {
                                        if (parmUserName != null) {  // Trying to log in
                                                try {
                                                        userdb = sessionsDB.get(parmUserName);
                                                        if (userdb == null || !userdb.isPassword(parms.get("PASSWORD"))) {
                                                                if (userdb != null) {
                                                                        System.out.println("session with wrong password found - removing it");
                                                                        if (cookieValue != null) sessions.remove(cookieValue);
                                                                        sessionsDB.remove(parmUserName);
                                                                }
                                                                userdb = new Database(DB_NAME,parmUserName,parms.get("PASSWORD"));
                                                                if (!userdb.isConnected()) {
                                                                        System.out.println("Database login failed for user "+parmUserName);
                                                                        userdb = null;
                                                                }
                                                        } else {
                                                                if (DEBUG) System.out.println("Reusing connection");
                                                        }
                                                } catch (Exception e) {
                                                        System.out.println("Error logging in "+e);
                                                }
                                        }

                                        // Set a cookie value
                                        if (userdb != null && parmUserName != null) {
                                                if (DEBUG) System.out.println("Calculating new cookie for: "+parmUserName);
                                                newCookieValue = (Math.random() * 100000000) + parmUserName;
                                                sessions.put(newCookieValue, parmUserName);
                                                if (!sessionsDB.containsKey(parmUserName)) {
                                                        sessionsDB.put(parmUserName, userdb);
                                                }
                                                if (DEBUG) System.out.println("User "+userdb.getUser()+" logged in");
                                        }

                                        // Get database connection (guest) for non users
                                        if (userdb == null) {
                                                try {
                                                        if (DEBUG) System.out.println("Using guest connection");
                                                        userdb = getNonUserDatabase();
                                                        newCookieValue = (Math.random() * 100000000) + "guest";
                                                        sessions.put(newCookieValue, "guest");
                                                        if (!sessionsDB.containsKey("guest")) {
                                                                sessionsDB.put("guest", userdb);
                                                        }
                                                        if (requestLocale != null && parms.get("LOCALE") == null) { // Since new connect, use the requested language
                                                                parms.put("LOCALE",requestLocale.getLanguage());
                                                        }
                                                } catch (Exception e) {
                                                        System.out.println("Error logging in with guest "+e);
                                                }
                                        }
                                }

                                // Set locale if specified
                                if (userdb != null) {
                                        if (parms.containsKey("LOCALE")) {
                                                if (DEBUG) System.out.println("Setting locale to "+parms.get("LOCALE"));
                                                Locale l = Message.getLocale(parms.get("LOCALE"));
                                                if (l != null) {
                                                        userdb.setLocale(l);
                                                        sessionsLocale.put((cookieValue != null ? cookieValue : newCookieValue), l);
                                                        Menu.clearMenu(userdb.getUser());  // clear Menu cache for this user
                                                }
                                        } else {
                                                if (cookieValue != null) {
                                                        Locale l = sessionsLocale.get(cookieValue);
                                                        if (l != null && l != userdb.getLocale()) {
                                                                userdb.setLocale(l);
                                                                Menu.clearMenu(userdb.getUser());  // clear Menu cache for this user
                                                        }
                                                }
                                        }
                                }

                                // Handle a websocket upgrade now (once) - after a successful authentication and getting a userdb :-)
                                if (websocket) {  // userDb will be the users database connection or a guest one
                                    os.write("HTTP/1.1 101 Switching Protocols\r\n".getBytes());
                                    os.write("Upgrade: websocket\r\n".getBytes());
                                    os.write("Connection: Upgrade\r\n".getBytes());
                                    String key = websocket_key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                                    MessageDigest crypt = MessageDigest.getInstance("SHA-1");
                                    crypt.reset();
                                    crypt.update(key.getBytes("UTF-8"));
                                    byte[] dig = crypt.digest();
                                    String accept = Base64.getEncoder().encodeToString(dig);
                                    if (DEBUG) System.out.println("Sec-WebSocket-Accepting: "+accept);
                                    os.write(("Sec-WebSocket-Protocol: "+websocket_protocol+"\r\n").getBytes());
                                    os.write(("Sec-WebSocket-Accept: "+accept+"\r\n").getBytes());
                                    os.write("\r\n".getBytes());
                                    os.flush();

                                    bitsIn = new BitInputStream(is);  // Upgrade the input stream to be bitly
                                    websocket_sender = new WebsocketSender(os,userdb);  // The sender will upgrade the output stream
                                    executor.execute(websocket_sender);

                                    // Websocket loop
                                    while (keep_alive) {
                                        int fin = bitsIn.readBits(1);
                                        int rsv = bitsIn.readBits(3);
                                        int opCode = bitsIn.readBits(4);
                                        int mask = bitsIn.readBits(1);
                                        long paylen = bitsIn.readBits(7);
                                        if (paylen == 126) {
                                            paylen = bitsIn.readBits(16);
                                        } else if (paylen == 127) {
                                            paylen = bitsIn.readBits(64);
                                        }
                                        int[] masks = new int[4];
                                        masks[0] = (mask == 1 ? bitsIn.readBits(8): 0);
                                        masks[1] = (mask == 1 ? bitsIn.readBits(8): 0);
                                        masks[2] = (mask == 1 ? bitsIn.readBits(8): 0);
                                        masks[3] = (mask == 1 ? bitsIn.readBits(8): 0);
                                        if (paylen > -1) {
                                            if (DEBUG) System.out.print("WebSocket header: fin="+fin+" rsv="+rsv+" op="+opCode+" mask="+mask+" paylen="+paylen);

                                            StringBuilder msg = null;
                                            byte[] bytes = null;

                                            if (opCode == WSOC_TEXT) {
                                                // Read as text string
                                                msg = new StringBuilder((int)paylen);
                                                int b;
                                                for (int bCount = 0; bCount < paylen; bCount++) {
                                                    b = bitsIn.readBits(8);
                                                    msg.append(mask == 1 ? (char)(b ^ masks[bCount % 4]) : (char)b);
                                                    //System.out.println("WebSocket: byte "+b+" as char="+(char)(b ^ masks[bCount % 4]));
                                                }
                                            } else {
                                                // Read as binary or likely nothing (0 length)
                                                ByteArrayOutputStream ba = new ByteArrayOutputStream((int)paylen);
                                                int b;
                                                for (int bCount = 0; bCount < paylen; bCount++) {
                                                    b = bitsIn.readBits(8);
                                                    ba.write(mask == 1 ? (char)(b ^ masks[bCount % 4]) : (char)b);
                                                }
                                                bytes = ba.toByteArray();

                                            }
                                            bitsIn.close();
                                            // Process the request based on operation code
                                            switch (opCode) {
                                                case WSOC_CLOSING:
                                                    websocket_sender.closed();
                                                    return false;
                                                case WSOC_PING:
                                                    websocket_sender.pinged();
                                                    break;
                                                case WSOC_PONG:  // Ignore a pong
                                                    break;
                                                case WSOC_BINARY:
                                                    System.out.println("Websocket received (and ignored) binary message: "+Dumper.hexDump(bytes));
                                                    break;
                                                case WSOC_TEXT:  // Process text message - assuming JSON (and last in the switch)
                                                    JSONObject jmsg;
                                                    try {
                                                        jmsg = new JSONObject(msg.toString());
                                                    } catch (Exception e) {
                                                        System.out.println("Invalid JSON Websocket request (ignored): "+msg.toString());
                                                        break;
                                                    }
                                                    if (DEBUG) System.out.println("Websocket request JSONObject="+jmsg);
                                                    try {
                                                        websocket_sender.processRequest(jmsg, userdb);
                                                    } catch (Exception e) {
                                                        System.out.println("Error processing Websocket request: "+msg.toString()+"\n"+e.getClass().getName()+"="+e.getMessage());
                                                        e.printStackTrace();
                                                    }
                                                    break;
                                            }
                                        } else {
                                            System.out.println("bad socket: paylen="+paylen);
                                            websocket_sender.closed();
                                            return false;
                                        }
                                    }
                                }

                                // Pull log text files from the log directory (only for Admin)
                                if (userdb != null && file.startsWith("/log/") && userdb.getUser().equals("admin")) {
                                        if (DEBUG) System.out.println("Looking for log "+file);
                                        File logfile = new File(file.substring(1));
                                        if (logfile.exists()) {
                                                returnFile(file, logfile, keep_alive, os);
                                                return keep_alive;
                                        } else {
                                                System.out.println("Could not retrieve log file "+file);
                                        }
                                }

                                // Pull backup files from the backup directory (only for Admin)  - Need to make this handle big files
                                if (userdb != null && file.startsWith("/backup/") && userdb.getUser().equals("admin")) {
                                        if (DEBUG) System.out.println("Looking for backup "+file);
                                        File backfile = new File(file.substring(1));
                                        if (backfile.exists()) {
                                            returnFile(file, backfile, keep_alive, os);
                                            return keep_alive;
                                        } else {
                                                System.out.println("Could not retrieve backup file "+file);
                                        }
                                }

                                // Thumbnails
                                if (userdb != null && file.startsWith("/thumbnail")) {
                                        String tsize = parms.get("SIZE");
                                        String tid = parms.get("ID");
                                        if (DEBUG) System.out.println("Retrieving thumbnail "+tid);
                                        StringBuilder ttypeb = new StringBuilder();
                                        StringBuilder tfileb = new StringBuilder();
                                        theData = Thumbnail.getThumbnail(tid, tsize, ttypeb, tfileb);
                                        String ttype = ttypeb.toString();
                                        String tfile = tfileb.toString();
                                        if (theData != null) {
                                                os.write(getThumbnailImageHeader(ttype, tfile, theData.length, keep_alive).getBytes());
                                                os.write(theData);
                                                os.flush();
                                                if (DEBUG) System.out.println("Thumbnail(Server) - sent "+ttype+" size="+theData.length);
                                                return keep_alive;
                                        } else {
                                                System.out.println("Thumbnail(Server) - no data found in thumbnail "+tid);
                                                return false;
                                        }
                                }

                                // Validate that class is allowed to be used
                                if (userdb != null) {
                                        if (DEBUG) System.out.println("Authorizing user "+userdb.getUser()+" for class "+className);
                                   //     if (Security.authorized(userdb.getUser(),className)) {
                                   //             if (DEBUG) System.out.println("User "+userdb.getUser()+" is allowed to use "+className);
                                   //     } else {
                                   //         if (userdb.getUser().equals("admin") && className.equals("permeagility.web.Query")) {
                                   //             // Allow admin to use Query tool if something needs to be fixed with security or the database
                                   //         } else {
                                   //             System.out.println("User "+userdb.getUser()+" is attempting to use "+className+" without authorization");
                                   //             parms.put("SECURITY_VIOLATION","You are not authorized to access "+className);
                                   //             className = HOME_CLASS;
                                   //         }
                                   //     }
                                }
                                if (className.contains("/")) {
                                    int slashLoc = className.indexOf("/");
                                    if (slashLoc + 1 < className.length()) parms.put("REST_OF_URL",className.substring(slashLoc+1));
                                    className = className.substring(0, slashLoc);
                                }
                                if (!className.startsWith("permeagility.plus.") // allow plus packages
                                    && !className.startsWith("permeagility.web.")) {  // could allow additional packages to be added in future
                                    className = "permeagility.web."+className;
                                }

                                Class<?> classOf = Class.forName( className, true, plusClassLoader );
                                Object classInstance = classOf.getDeclaredConstructor().newInstance();

                             // Instantiate the requested class and use it
                            if (classInstance instanceof Weblet) {
                                parms.put("REQUESTED_CLASS_NAME", className);
                                parms.put("COOKIE_VALUE", cookieValue);
                                Weblet weblet = (Weblet)classInstance;
                                if (DEBUG) System.out.println("LOADING HTML PAGE="+className+" PARAMETER="+parms.toString());
                                        DatabaseConnection con = null;
                                        try {
                                                if (userdb != null) {
                                                        try { 
                                                            con = userdb.getConnection();
                                                        } catch (Exception e) {
                                                                theData = "<BODY><P>Server is busy, please try again</P></BODY>".getBytes();
                                                                System.out.println("!"+userdb.getUser());
                                                        }
                                                        if (con == null) {
                                                            try { 
                                                                con = userdb.getConnection();
                                                            } catch (Exception e) {
                                                                    theData = "<BODY><P>Server is still busy, please try again</P></BODY>".getBytes();
                                                                    System.out.println("!"+userdb.getUser());
                                                            }
                                                        }
                                                        if (con != null) {
                                                            con.begin();
                                                            theData = weblet.doPage(con, parms);
                                                            con.commit();
                                                        }
                                                }
                                        } catch (Exception e) {
                                                System.out.println("Exception running weblet: ");
                                                e.printStackTrace();
                                                if (con != null) con.rollback();
                                                ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
                                                e.printStackTrace(new PrintWriter(dataStream));
                                                theData = dataStream.toByteArray();
                                        } finally {
                                            if (con != null && con.isConnected()) con.close();  
                                            con = null;
                                        }
                                        
                            } else if (classInstance instanceof Download) {
                                Download downloadlet = (Download)classOf.getDeclaredConstructor().newInstance();
                                        DatabaseConnection con = null;
                                        try {
                                            if (userdb != null) {
                                                    con = userdb.getConnection();
                                                    if (con != null) {
                                                            if (DEBUG) System.out.println("DOWNLOAD PAGE="+className+" PARAMETER="+parms.toString());
                                                            con.begin();
                                                            theData = downloadlet.doPage(con, parms);
                                                            con.commit();
                                                    }
                                            }
                                        } catch (Exception e) {
                                                System.out.println("Exception running weblet: ");
                                                e.printStackTrace();
                                                if (con != null) con.rollback();   
                                        } finally {
                                            if (con.isConnected()) con.close(); 
                                            con = null;
                                        }
                                        // Do after to allow content-disposition to be dynamic if necessary
                                content_type = downloadlet.getContentType();
                                content_disposition = downloadlet.getContentDisposition();
                            } else {
                                System.out.println(file+" is not a proper class");
                            }
                            if (LOG_REQUESTS) System.out.println(" --- "+" "+(System.currentTimeMillis()-startTime)+"ms ---");
                            if (parms.containsKey("RESPONSE_REDIRECT")) {
                                os.write(getRedirectHeader(parms).getBytes());
                            } else {
                                if (theData != null) {
                                    os.write(getHeader(content_type, theData.length, newCookieValue, content_disposition, keep_alive).getBytes());
                                    os.flush();
                                    os.write(theData);
                                }
                            }
                            os.flush();
                        } catch (ClosedChannelException se) {  // Connection broken
                                return false;
                        } catch (IOException ioe) {  // Connection broken
                                System.out.println("IOException:"+ioe);
                                return false;
                        } catch (Exception e) {  // Everything else
                                System.out.println("Exception:"+e);
                                e.printStackTrace();
                                if (version.startsWith("HTTP/")) {  // send a MIME header
                                        os.write(("HTTP/1.1 404 File Not Found\r\n").getBytes());
                                        os.write(("Date: " + new java.util.Date() + "\r\n").getBytes());
                                        os.write(("Server: PermeAgility 1.0\r\n").getBytes());
                                        os.write(("Content-type: text/html" + "\r\n\r\n").getBytes());
                                }
                                os.write(("<HTML><HEAD><TITLE>File Not Found</TITLE></HEAD>").getBytes());
                                os.write(("<BODY><H1>HTTP Error 404: File Not Found</H1></BODY></HTML>").getBytes());
                                os.flush();
                                return false;
                        }
                } else {  // method does not equal "GET" or "POST" or "PUT" or "DELETE"
                    System.out.println("Invalid request method: "+method);
                    if (version.startsWith("HTTP/")) {  // send a MIME header
                        os.write(("HTTP/1.1 501 Not Implemented\r\n").getBytes());
                        os.write(("Date: " + new java.util.Date() + "\r\n").getBytes());
                        os.write(("Server: PermeAgility 1.0\r\n").getBytes());
                        os.write(("Content-type: text/html" + "\r\n\r\n").getBytes());
                    }
                    os.write("<HTML><HEAD><TITLE>Not Implemented</TITLE></HEAD>".getBytes());
                    os.write("<BODY><H1>HTTP Error 501: Not Implemented</H1></BODY></HTML>".getBytes());
                    os.flush();
                    return false;
                }
             } catch (Exception e) {
                    if (DEBUG) System.out.println("Service exception:"+e);
                    return false;
            }

            return keep_alive;
	}

	/** Read a line from the input stream into a string buffer */
	private final static String readLine(InputStream is) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c = is.read();
            do {
                if (c != 0x0A && c != 0x0D) { sb.append((char)c); }
                if (is.available() == 0) { return sb.toString(); }
                c = is.read();
                if (sb.length() > 1024) { break; }
            } while (c != 0x0A  && c != -1);
            return sb.toString();
	}

	/** Read the multipart request and put into parms (files come this way and sometimes form fields)
	 * If a file is uploaded, it will be put in a temp file and the file name will be in the parms */
	private final static void readMultipartData(InputStream is, String boundaryValue, HashMap<String,String> parms) {
		// Read a part until the boundary value is reached.
		try {
			if (is.available() == 0) { return; }
			readLine(is);
			if (is.available() == 0) { return; }
			String content_name = null;
			String content_disposition = readLine(is);
			String content_type = null;
			String file_name = null;
			if (DEBUG) System.out.println("Server.ReadMultipartData: disp="+content_disposition);
			int fni = content_disposition.indexOf("filename=");
			if (fni > 0) {
                            int fne = content_disposition.indexOf('"',fni+10);
                            if (fni < fne) {
                                file_name = content_disposition.substring(fni+10,fne);
                                if (!file_name.equals("")) {
                                    String ctline = readLine(is);
                                    if (ctline.length() > 14) {
                                            content_type = ctline.substring(14);
                                    } else {
                                            content_type = "Unknown";
                                    }
                                    if (DEBUG) System.out.println("Server.ReadMultipartData: type="+content_type);
                                }
                            }
			}
			int in = content_disposition.indexOf("name=");
			int ine = content_disposition.indexOf('"',in+6);
			content_name = content_disposition.substring(in+6,ine);

			char[] boundary = boundaryValue.toCharArray();
			char[] bcompare = new char[boundaryValue.length()];
			int boundaryLength = boundaryValue.length();

			ByteArrayOutputStream content = new ByteArrayOutputStream();

			readLine(is); // Get to the content (past the /r/l)

			if (DEBUG) System.out.println("Server.ReadMultipartData: content: available="+is.available()+" boundary="+boundaryValue);
			int cc = 0;
			int c;
			char last = ' ', nlast;  // Next last needed to avoid grabbing some of the boundary
			do {
				nlast = last;
				last = bcompare[0];
				c = is.read();
				System.arraycopy(bcompare, 1, bcompare, 0, boundaryLength-1);
				bcompare[boundaryLength-1] = (char)c;
				if (Arrays.equals(boundary,bcompare)) {
					break;
				}
				if (cc > boundaryLength) {
					content.write(nlast);
				}
				cc++;
			} while (c != -1);

			if (DEBUG) System.out.println("Server.ReadMultipartData: content head="+Dumper.hexDump(content.toByteArray(),0,Math.min(content.size(), 64)));
			if (DEBUG && content.size()>65) System.out.println("Server.ReadMultipartData: content tail="+Dumper.hexDump(content.toByteArray(),content.size()-64,64));
			if (file_name != null && !file_name.equals("")) {
				//System.out.println("FileContent="+content.toString().substring(0,content.size()-boundaryLength-3));
				File temp = File.createTempFile("pa_upload_", ".tmp");
				if (DEBUG) System.out.println("Server.ReadMultipartData: writing binary to "+temp.getCanonicalPath());
				FileOutputStream fo = new FileOutputStream(temp);
				fo.write(content_type.getBytes());
				fo.write(0x00);
				fo.write(file_name.getBytes());
				fo.write(0x00);
				content.writeTo(fo);
				fo.close();
				String scontent = temp.getAbsolutePath();
				parms.put(content_name, scontent);
				parms.put(content_name+"_FILENAME", file_name);
				parms.put(content_name+"_TYPE", content_type);
			} else if (content_name != null) {
				String scontent = content.toString().trim();  // Remove trailing /r/l
				String newValue = parms.get(content_name);
				if (newValue == null || newValue.equals("")) {
					newValue = scontent;
				} else {
					newValue = newValue + "," + scontent;
				}
				parms.put(content_name, newValue);
				if (DEBUG) System.out.println("Server.ReadMultipartData: String Content:"+content_name+"="+newValue);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

        /* Return a file response and flush - file is streamed in 1024 byte chunks */
        private final static void returnFile(String filename, File thefile, boolean keep_alive, OutputStream os) throws Exception {
            os.write(getHeader(getContentType(filename), (int) thefile.length(), null, null, keep_alive).getBytes());
            InputStream iis = new FileInputStream(thefile);
            int b;   byte[] buf = new byte[1024];
            while ((b = iis.read(buf)) != -1) {
                os.write(buf, 0, b);
            }
            iis.close();
            os.flush();
        }

	/** Get HTML header adding a cookie and content disposition
	 * @param keep_alive */
	public final static String getHeader(String ct, int size, String newCookieValue, String content_disposition, boolean keep_alive) {
		String responseHeader = "HTTP/1.1 200 OK\r\n"
			+"Date: " + new java.util.Date() + "\r\n"
			+"Server: PermeAgility 1.0\r\n"
			+(keep_alive ? "Connection: keep-alive\n" : "")
			+(newCookieValue != null ? "Set-Cookie: name=PermeAgilitySession"+HTTP_PORT+"; SameSite=Lax; value="+newCookieValue+";\r\n" : "")
			+"Content-length: " + size + "\r\n"
			+"Content-type: " + ct + "\r\n"
			+(content_disposition != null ? "Content-disposition: " + content_disposition + "\r\n" : "")
			+"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

        /** Return Redirect header */
	public final static String getRedirectHeader(HashMap<String,String> parms) {
		String responseHeader = "HTTP/1.1 303 See other\r\n"
			+"Location: " + parms.get("RESPONSE_REDIRECT") + "\r\n"
       			+"Content-length: 0\r\n"   // Because Firefox is too stupid to understand the redirect without this
                        +"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

	/* Get header for images */
	public final static String getImageHeader(String ct, int size, boolean keep_alive) {
		String responseHeader = "HTTP/1.1 200 OK\r\n"+
			"Date: " + new java.util.Date() + "\r\n"+
			"Server: PermeAgility 1.0\r\n"+
			(keep_alive ? "Connection: keep-alive\n" : "")+
			"Cache-Control: max-age=2592000\r\n"+
			"Expires: "+gmt_date_format.format((new Date((new Date()).getTime()+86400000)))+"\r\n"+
			"Last-Modified: "+resource_jar_last_modified__string+"\r\n"+
			"Content-length: " + size + "\r\n"+
			"Content-type: " + ct + "\r\n"+
			"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

	public static int DB_IMAGE_EXPIRE_TIME = 10000;
	public final static String getThumbnailImageHeader(String ct, String fn, int size, boolean keep_alive) {
		String responseHeader = "HTTP/1.1 200 OK\r\n"+
			"Date: " + new java.util.Date() + "\r\n"+
			"Server: PermeAgility 1.0\r\n"+
			(keep_alive ? "Connection: keep-alive\n" : "")+
			"Cache-Control: max-age="+DB_IMAGE_EXPIRE_TIME+"\r\n"+
			"Expires: "+gmt_date_format.format((new Date((new Date()).getTime()+DB_IMAGE_EXPIRE_TIME)))+"\r\n"+
			//"Cache-Control: no-cache\r\n"+
			"Content-length: " + size + "\r\n"+
			"Content-type: " + ct + "\r\n"+
			(ct.startsWith("image") || ct.endsWith("pdf") ? "Content-disposition: inline; filename=\"" : "Content-disposition: attachment; filename=\"" ) + fn +  "\"\r\n"+
			"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

	/** Get the header for log files */
	public final static String getLogHeader(String ct, int size, boolean keep_alive) {
		String responseHeader = "HTTP/1.1 200 OK\r\n"+
			"Date: " + new java.util.Date() + "\r\n"+
			"Server: PermeAgility 1.0\r\n"+
			(keep_alive ? "Connection: keep-alive\n" : "")+
			"Content-length: " + size + "\r\n"+
			"Content-type: " + ct + "\r\n"+
			"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

	protected final static String getServerUser() {
		return database.getUser();
	}

	protected final static String getClientVersion() {
		return database.getClientVersion();
	}

	/** Get the mime content type based on the filename */
	public final static String getContentType(String name) {
		if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
		else if (name.endsWith(".txt") || name.endsWith(".log")) return "text/plain";
		else if (name.endsWith(".json") ) return "application/json";
		else if (name.endsWith(".gz") ) return "application/gzip";
		else if (name.endsWith(".js") ) return "text/javascript";
		else if (name.endsWith(".css")) return "text/css";
		else if (name.endsWith(".pdf")) return "application/pdf";
		else if (name.endsWith(".gif") ) return "image/gif";
		else if (name.endsWith(".png") ) return "image/png";
		else if (name.endsWith(".svg") ) return "image/svg+xml";
		else if (name.endsWith(".class") ) return "application/octet-stream";
		else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
		else return "text/html";
	}

	public final static String addTransientImage(String type, byte[] data) {
		String name = "IMG_"+(int)(Math.random()*1000000)+".jpg";
		transientImages.put(name, data);
		transientImageDates.put(name, new Date());
		transientImageTypes.put(name, type);
		return name;
	}

	/**  Call this when you update a table that the server or caches may be interested in   */
	public final static void tableUpdated(DatabaseConnection con, String table) {
		if (table.equalsIgnoreCase("metadata:schema")) {
			//DatabaseConnection con = getServerConnection();
			if (DEBUG) System.out.println("Server: schema updated - reloading - not implemented");
			//clearColumnsCache("ALL");
			//con.getSchema().reload();
		} else if (table.equals("constant")) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - constants applied");
			ConstantOverride.apply(con);
		} else if (table.equals("columns") ) {
			//if (DEBUG) System.out.println("Server: tableUpdated("+table+") - columns cache cleared");
			//Server.clearColumnsCache("ALL");  // Don't know which table or which row in columns table so clear all
		} else if (table.equals("pickList") ) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - query caches cleared");
			Weblet.queryCache.clear();
		} else if (table.equals("pickValues") ) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - pickValues cleared");
			updatePickValues();
		} else if (table.equals("locale") || table.equals("message")) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - messages refreshed and menus cleared");
			Message.initialize(con);
			Menu.clearCache();
			Table.clearDataTypes();
		}
		if (table.equals("user")
		  || table.equals("menu")
		  || table.equals("menuItem")
		  || table.equals("ORole")
		  || table.equals("OUser")) {
			Security.refreshSecurity();  // Will display its own messages
			Menu.clearCache();
		}
		if (DEBUG) System.out.println("Server: tableUpdated("+table+") - query cache updated");
		Weblet.queryCache.refreshContains(table);
	}

	public final static List<String> getPickValues(String table, String column) {
		if (pickValues.isEmpty()) {
		//	updatePickValues();  // There must be a few by default - empty means not loaded yet (Note: restart server if delete)
		}
		return pickValues.get(table+"."+column);
	}

	public final static void updatePickValues() {
		DatabaseConnection con = null;
		try {
			con = database.getConnection();
            con.begin();
			for (Document values : con.query("SELECT FROM "+Setup.TABLE_PICKVALUES).get()) {
				String v[] = values.getString("values").toString().split(",");
				ArrayList<String> list = new ArrayList<String>();
				for (String s : v) {
					list.add(s);
				}
				pickValues.put(values.getString("name"),list);
			}
            con.commit();
		} catch (Exception e) {
            con.rollback();
			System.err.println("Error getting pickValues: "+e.getMessage());
		} finally {
            if (con.isConnected()) con.close();
		}
	}

	public final static void setLocalSetting(String key, String value) {
		if (value == null) {
			localSettings.remove(key);
		} else {
			localSettings.put(key,value);
		}
		try {
			localSettings.store(new FileWriter(SETTINGS_FILE), "Initialization Parameters for PermeAgility server");
		} catch (IOException e) {
			System.out.println("Cannot store init file");
		}
	}

	private final static String getLocalSetting(String key, String def) {
		try {
			localSettings.load(new FileReader(SETTINGS_FILE));
		} catch (IOException fnf) {
			System.out.println("Cannot open init file - assuming defaults");
		}
		return localSettings.getProperty(key,def);
	}

	static final boolean initializeServer() {
		System.out.println("Initializing "+Constants.PRODUCT+" Version "+Constants.getVersion());
		DatabaseConnection con = null;
		try {
             String p = getLocalSetting(DB_NAME+HTTP_PORT, null);
            try {
    			database = new Database(DB_NAME, "server", (p == null ? "server" : p));
            } catch (Exception e) {
                e.printStackTrace();
            }
			if (database.isConnected()) {
				System.out.println("Connected to database name="+DB_NAME+" version="+database.getClientVersion());
				con = database.getConnection();
                try {
                    if (!Setup.checkInstallation(con)) {
                        System.out.println("---\n--- Warning condition: checkInstallation failed - check install messages in context\n---");
                    }
                } catch (Exception e) {
                }

                con.begin();

			//	database.setPoolSize(SERVER_POOL_SIZE);

				// Initialize security
		//		Security.refreshSecurity();
		//		if (Security.keyRoleCount() < 1) {
		//			System.out.println("***\n*** Exit condition: No key roles found for security - no functions to enable\n***");
		//			exit(-1);
		//		}

				// Initialize PlusClassLoader
				plusClassLoader = PlusClassLoader.get();
				if (plusClassLoader == null) {
					System.out.println("***\n*** Exit condition: Could not initialize the PlusClassLoader\n***");
					exit(-1);
				}
				// Set the class loader for the currentThread (and all the Children so that plus's will work)
				Thread.currentThread().setContextClassLoader(plusClassLoader);

				// Do this after plus modules loaded so we can set their constants
				if (!ConstantOverride.apply(con)) {
					System.out.println("***\n*** Exit condition: Could not apply constant overrides\n***");
					exit(-1);
				}

                con.commit();

					if (restore_lockout) restore_lockout = false;
			} else {
                System.out.println("Database not connected. Wha?");
            }
		} catch (Exception e) {
			e.printStackTrace();
            if (con != null) con.rollback();
			return false;
		} finally {
            if (con != null) con.close();
        }
        System.out.println("init complete");
		return true;
	}

	/** Get the guest connection */
	public final static Database getNonUserDatabase() {
        return database;
//		if (dbNone == null)	{
//			Database d = null;
//			try {
//				d = new Database(DB_NAME,"guest","guest");
//				d.setPoolSize(GUEST_POOL_SIZE);
//				dbNone = d;
//			} catch (Exception e) {
//				System.out.println("Cannot connect guest database");
//			}
//		}
//		return dbNone;
	}

	protected final static void closeAllConnections() {
		System.out.print("dropping all connections...");
	//	for (Database d : sessionsDB.values()) {
	//		d.close();
	//	}
		sessions.clear();
	//	sessionsDB.clear();
		if (database != null) {
			database.close();
			database = null;
		}
	//	System.gc();
		System.out.println("done");
	}

    public final static Date getServerInitTime() {   return serverInitTime;  }
    public final static Date getSecurityRefreshTime() {   return Security.securityRefreshTime;  }
    public final static String getCodeSource() { return codeSource; }
    public final static String getDBName() { return DB_NAME; }
    public final static int getHTTPPort() { return HTTP_PORT; }

 //   public final static void addEventStreamFilter(EventStreamFilter esf) {
 //       eventStreamFilters.add(esf);
 //   }

    /** Because Websocket is two-way asynchronous, this second thread will be opened to send messages
    * while the original request thread will continue to process the inputs and call processRequest(msg,userdb)
    * when a text request comes in
    *
    * Note: the request is executed from the input thread and puts messages onto the websocketMessageQueue
    */
    private static class WebsocketSender implements Runnable {

        BitOutputStream bitsOut;
        Database userdb;
        ConcurrentLinkedDeque<String> webSocketMessageQueue = new ConcurrentLinkedDeque<>();
        HashMap<String,Object> openLiveQueries = new HashMap<>();

        public WebsocketSender(OutputStream os, Database db) {
            bitsOut = new BitOutputStream(os);
            userdb = db;
        }

        boolean websocket_alive = true;
        boolean websocket_pinged = false;
        public void pinged() { websocket_pinged = true; }

        public void closed() {
            websocket_alive = false;
            System.out.println("Live query unsubscribing");
            DatabaseConnection con = userdb.getConnection();
            try {
                for (String subject : openLiveQueries.keySet()) {
                    try {  // We want to close all even if some of them fail
                        con.begin();
                        Object ures = con.update("LIVE UNSUBSCRIBE "+openLiveQueries.get(subject));
                        con.commit();
                        System.out.println("Unsubscribe (during close) "+subject+" result: "+ures);
                    } catch (Exception e) {
                        e.printStackTrace();
                        webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                    }                }
                openLiveQueries.clear();
            } catch (Exception e) {
                con.rollback();
                e.printStackTrace();
                webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
            } finally {
                con.close();
            }
        }


        @Override public void run() {

            while(websocket_alive) {
                try {
                    Thread.sleep(Math.max(50, WEBSOCKET_PAUSE_MS));  // at least 50 ms sleep
                    //Thread.sleep(100);  // at least 50 ms sleep

                    // Send any requested pongs
                    if (websocket_pinged) {
                        sendWebsocketMessage(bitsOut, WSOC_PONG, null);
                        websocket_pinged = false;
                    }
                    // Send the messages in the queue
                    int pendingOut = Math.min(webSocketMessageQueue.size(),WEBSOCKET_QUEUE_CHUNK);
                    if (pendingOut == 1) {
                        sendWebsocketMessage(bitsOut, WSOC_TEXT, webSocketMessageQueue.poll().getBytes(StandardCharsets.UTF_8));
                    } else if (pendingOut > 1) {
                        //if (DEBUG) System.out.println("Found "+pendingOut+" of "+webSocketMessageQueue.size()+" messages to send");
                        StringBuilder sb = new StringBuilder();
                        sb.append("[ ");
                        for (int i=0;i<pendingOut && websocket_alive;i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(webSocketMessageQueue.poll());
                        }
                        sb.append(" ]");
                        if (DEBUG) System.out.println("Sending array of "+pendingOut+" messages");
                        sendWebsocketMessage(bitsOut, WSOC_TEXT, sb.toString().getBytes(StandardCharsets.UTF_8));
                    }
                } catch (InterruptedException ie) {
                    System.out.println("Interrupted the websocket out thread - so?"+ie);
                } catch (Exception e) {
                    System.out.println("Exception in the websocket out thread - so I will close socket - goodbye"+e);
                    websocket_alive = false;
                }
            }
            closed();
        }

        private void sendWebsocketMessage(BitOutputStream bitsOut, int operation, byte[] bytes) {
            bitsOut.writeBits(1,1);  // Fin = 1 single messages support only at this time
            bitsOut.writeBits(3,0);  // RSV
            bitsOut.writeBits(4, operation);  // Operation
            bitsOut.writeBits(1, 0);  // Mask
            if (bytes == null) {
                bitsOut.writeBits(7, 0); // length=0
            } else if (bytes.length < 126) {
                bitsOut.writeBits(7, bytes.length);  // Write the length
            } else if (bytes.length < 65535) {
                bitsOut.writeBits(7, 126);   // put 126, then the length as a 16 bit
                bitsOut.writeBits(16, bytes.length);
            } else {
                bitsOut.writeBits(7, 127);  // put 127, then the length as a 64 bit (crazy, I know)
                bitsOut.writeBits(64, bytes.length);
            }
            // Write out the message
            if (bytes != null) {
                for (byte b : bytes) {
                    bitsOut.writeBits(8, b);
                }
            }
            bitsOut.flush();  // and whoosh, down the drain
        }


        private void processRequest(JSONObject jo, Database userdb) {
            if (DEBUG) System.out.println(" JSONObject="+jo);
            String type = jo.getString("type");
            if (type == null) {
                System.out.println("WebSocket: type is null - sorry, no comprende");

            } else if (type.equalsIgnoreCase("ping")) {
                    webSocketMessageQueue.add("{ \"type\": \"pong\" }");

            } else if (type.equalsIgnoreCase("event") && jo.has("subject")) {
                String subject = jo.getString("subject");
                if (jo.has("data")) {
                    System.out.println("Sending event " + subject + " to all subscribers");
          //          for (EventStreamListener esl : eventStreamListeners) {
          //              boolean filterEvent = false;
          //              if (eventStreamFilters.size() > 0) {
          //                  System.out.println("Filtering event " + subject );
          //                  for (EventStreamFilter esf : eventStreamFilters) {
          //                     filterEvent = esf.filterEvent(subject, jo.getJSONObject("data"), esl.getUser());
          //                  }
          //              }
          //              if (!filterEvent) {
          //                 //esl.onEvent(subject, jo.getJSONObject("data").toString());
          //              }
          //          }
                }
                //webSocketMessageQueue.add("{ \"type\": \"event\", \"subject\": \""+jo.getString("subject")+"\" }");

            } else if (type.equalsIgnoreCase("query") && jo.has("subject")) {
                DatabaseConnection con = userdb.getConnection();
                try {
                    con.begin();
                    QueryResult result = con.query(jo.getString("subject"));
                    if (result.size() == 0) {
                        webSocketMessageQueue.add("{ \"type\": \"event\", \"subject\": \""+jo.getString("subject")+"\" }");
                    } else {
                        long st = System.currentTimeMillis();
                        for (Document d : result.get()) {
                            webSocketMessageQueue.add("{ \"type\": \"data\", \"data\": "+d.toJSON()+" }");
                        }
                        webSocketMessageQueue.add("{ \"type\": \"eod\"}");    // end of data
                        System.out.println("Queued up "+result.size()+" rows in (ms)"+(System.currentTimeMillis() - st));
                    }
                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                } finally {
                    con.close();
                }

            } else if (type.equalsIgnoreCase("update") && jo.has("subject")) {
                DatabaseConnection con = userdb.getConnection();
                try {
                    String table = jo.getString("subject");
                    JSONObject data = jo.getJSONObject("data");
                    StringBuilder us = new StringBuilder();
                    int cc = 0;
                    Iterator<String> i = data.keys();
                    String rid = null;
                    while (i.hasNext()) {
                        String k = i.next();
                        if (k.equals("@rid")) {
                            rid = data.getString(k);
                        } else {
                            if (cc > 0) {
                                us.append(", ");
                            }
                            Object o = data.get(k);
                            if (o.getClass().getSimpleName().equals("String")) {
                                us.append(k+" = \""+o+"\"");
                                cc++;
                            } else {
                                us.append(k+" = "+o.toString());
                                cc++;
                            }
                        }
                    }
                    String u = "UPDATE "+rid+" SET "+us.toString();
                    if (DEBUG) System.out.println("UPDATE="+u);
                    con.begin();
                    Object result = con.update(u);
                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                } finally {
                    con.close();
                }

            } else if (type.equalsIgnoreCase("upsert") && jo.has("subject")) {
                DatabaseConnection con = userdb.getConnection();
                try {
                    String table = jo.getString("subject");
                    int whereAt = table.toUpperCase().indexOf("WHERE");
                    JSONObject data = jo.getJSONObject("data");
                    StringBuilder us = new StringBuilder();
                    int cc = 0;
                    Iterator<String> i = data.keys();
                    String rid = null;
                    while (i.hasNext()) {
                        String k = i.next();
                        if (k.equals("@rid")) {
                            rid = data.getString(k);
                        } else {
                            if (cc > 0) {
                                us.append(", ");
                            }
                            Object o = data.get(k);
                            if (o.getClass().getSimpleName().equals("String")) {
                                us.append(k+" = \""+o+"\"");
                                cc++;
                            } else {
                                us.append(k+" = "+o.toString());
                                cc++;
                            }
                        }
                    }
                    String u = "UPDATE "+(rid != null ? rid + " SET "+us.toString() : table.substring(0,whereAt))+" SET "+us.toString()+" UPSERT "+table.substring(whereAt);
                    if (DEBUG) System.out.println("UPDATE="+u);
                    con.begin();
                    Object result = con.update(u);
                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                } finally {
                    con.close();
                }

            } else if (type.equalsIgnoreCase("subscribe") && jo.has("subject") ) {
                String subject = jo.getString("subject");
                if (subject.equals("event")) {
                    //eventStreamListeners.add(new EventStreamListener().setUser(userdb.getUser()));
                } else {
                    if (DEBUG) System.out.println("Live query subscribing... userdb="+userdb);
                    if (userdb != null) {
                        // Subscribe to changes
                        if (DEBUG) System.out.println("subscribed to: "+subject);
                        DatabaseConnection con = userdb.getConnection();
                        try {
                            List<Document> liveQueryResult = null; //con.getDb().query(new OLiveQuery<>("LIVE SELECT FROM "+subject, new LiveQueryListener()));
                            if (liveQueryResult != null && liveQueryResult.size() == 1) {
                                System.out.println("LiveQueryHandle="+liveQueryResult.get(0).getString("token"));
                                openLiveQueries.put(subject, liveQueryResult.get(0).getString("token"));
                            } else {
                                System.out.println("Live query failed "+liveQueryResult);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                        }
                        if (DEBUG) System.out.println("Live query subscribed");
                        webSocketMessageQueue.add("{ \"type\": \"event\", \"subject\": \"subscribe\", \"subject\": \""+subject+"\" }");
                        // Now send the data
        //                con = userdb.getConnection();
                      try {
                          con.begin();
                          QueryResult result = con.query("SELECT FROM "+subject);
                          if (result.size() == 0) {
                              webSocketMessageQueue.add("{ \"type\": \"event\", \"subject\": \""+subject+"\" }");
                          } else {
                              for (Document d : result.get()) {
                                  webSocketMessageQueue.add("{ \"type\": \"data\", \"subject\": \""+subject+"\", \"data\": "+d.toJSON()+" }");
                              }
                              webSocketMessageQueue.add("{ \"type\": \"eod\", \"subject\": \""+subject+"\"}");    // end of data
                          }
                          con.commit();
                      } catch (Exception e) {
                          con.rollback();
                          webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                      } finally {
                          con.close();
                      }
                    }
                }

            } else if (type.equalsIgnoreCase("unsubscribe") && jo.has("subject") ) {
                if (DEBUG) System.out.println("Live query unsubscribing... userdb="+userdb);
                String subject = jo.getString("subject");
                DatabaseConnection con = userdb.getConnection();
                try {
                    con.begin();
                    Object ures = con.update("LIVE UNSUBSCRIBE "+openLiveQueries.get(subject));
                    System.out.println("Unsubscribe "+subject+" result: "+ures);
                    openLiveQueries.remove(subject);
                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    e.printStackTrace();
                    webSocketMessageQueue.add("{ \"type\": \"error\", \"message\": \""+e.getMessage()+"\" }");
                } finally {
                    con.close();
                }
            } else {
                System.out.println("Unsupported operation type="+type);
            }
        }

   /*      class LiveQueryListener implements OLiveResultListener {

            @Override public void onLiveResult(int iLiveToken, ORecordOperation iOp) throws OException {
                String op = iOp.toString();
                switch (iOp.type) {
                    case 1: op = "update"; break;
                    case 2: op = "delete"; break;
                    case 3: op = "add"; break;
                }
                if (DEBUG) System.out.println("Live query: "+iLiveToken+" operation: "+op+" content: "+iOp.record);
                webSocketMessageQueue.add("{ \"type\": \"live-query\", \"operation\": \""+op+"\", \"data\": "+iOp.getRecord().toJSON()+" }");
            }

            @Override public void onError(int iLiveToken) {
                if (DEBUG) System.out.println("Live query terminated due to error");
                webSocketMessageQueue.add("{ \"type\": \"live-query\", \"operation\": \""+"error"+"\" }");
            }

            @Override public void onUnsubscribe(int iLiveToken) {
                if (DEBUG) System.out.println("Live query terminated with unsubscribe");
                webSocketMessageQueue.add("{ \"type\": \"live-query\", \"operation\": \""+"unsubscribe"+"\" }");
            }
        }

        class EventStreamListener {
            String user = null;

            public String getUser() { return user; }
            public EventStreamListener setUser(String u) { user = u; return this; }

            public void onEvent(String subject, String data) throws OException {
                if (DEBUG) System.out.println("Event: "+data);
                webSocketMessageQueue.add("{ \"type\": \"event\", \"subject\": \""+subject+"\", \"data\": "+data+" }");
            }

            public void onUnsubscribe(int iLiveToken) {
                if (DEBUG) System.out.println("Event stream terminated with unsubscribe");
                webSocketMessageQueue.add("{ \"type\": \"event\", \"subject\": \""+"unsubscribe"+"\" }");
            }
        }
*/

    }

    public interface EventStreamFilter {
        public boolean filterEvent(String subject, JSONObject data, String user);
    }
}
