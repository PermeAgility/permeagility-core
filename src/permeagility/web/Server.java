/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
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
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.util.Browser;
import permeagility.util.ConstantOverride;
import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.Dumper;
import permeagility.util.PlusClassLoader;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.OrientShutdownHook;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.ORule.ResourceGeneric;
import com.orientechnologies.orient.core.record.impl.ODocument;

/** This is the PermeAgility web server - it handles security, database connections and some useful caches for privileges and such
  * all web requests go through the run() function for each thread/socket
  *  
  *  Parameters: [port(1999)] [db(plocal:db)] [selftest] 
  */
public class Server extends Thread {

	static int HTTP_PORT = 1999;  // First parameter
	static String DB_NAME = "plocal:db";  // Second parameter
	private static Date serverInitTime = new Date();
	private static String DEFAULT_DBFILE = "starterdb.json.gz";  //  Used at initial start

	/* Overrideable constants */
	public static boolean DEBUG = true;
	public static int SOCKET_TIMEOUT = 60000;  // One minute timeout
	public static boolean ALLOW_KEEP_ALIVE = true;
	public static boolean KEEP_ALIVE = true;  // Keep sockets alive by default, don't wait for browser to ask
	public static int KEEP_ALIVE_PAUSE_MS = 0;  // If keep_alive, pause after sending response
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
	public static int SERVER_POOL_SIZE = 10;
	public static boolean LOGOUT_KILLS_USER = false; // Set this to true to kill all sessions for a user when a user logs out (more secure)
	public static String LOCKOUT_MESSAGE = "<p>The system is unavailable because a system restore is being performed. Please try again later.</p><a href='/'>Try it now</a>";
	
	static Database database;  // Server database connection
	static Database dbNone = null;  // Used for login and account request

	static boolean restore_lockout = false;
	static String restore_file = null;  // Backup file to restore when initializing

	static long resource_jar_last_modified = 0l;
	static String resource_jar_last_modified__string = "";
	
	static Message messages = null;
	
	static ConcurrentHashMap<String,String> sessions = new ConcurrentHashMap<String,String>(); // Cookie (user+random) -> username
	static ConcurrentHashMap<String,Database> sessionsDB = new ConcurrentHashMap<String,Database>();  // username -> Database pool
	
	private static Date securityRefreshTime = new Date();
	private static ConcurrentHashMap<String,Object[]> userRoles = new ConcurrentHashMap<String,Object[]>();
	private static ConcurrentHashMap<String,HashMap<String,Number>> userRules = new ConcurrentHashMap<String,HashMap<String,Number>>();
	private static ConcurrentHashMap<String,Object[]> keyRoles = new ConcurrentHashMap<String,Object[]>();
	private static ConcurrentHashMap<String,QueryResult> columnsCache = new ConcurrentHashMap<String,QueryResult>();
	private static ConcurrentHashMap<String,HashMap<String,Number>> tablePrivsCache = new ConcurrentHashMap<String,HashMap<String,Number>>();

	private static SimpleDateFormat gmt_date_format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

	private static String SETTINGS_FILE = "init.pa";
	private static Properties localSettings = new Properties();

	private static ConcurrentHashMap<String,byte[]> transientImages = new ConcurrentHashMap<String,byte[]>();
	private static ConcurrentHashMap<String,Date> transientImageDates = new ConcurrentHashMap<String,Date>();
	private static ConcurrentHashMap<String,String> transientImageTypes = new ConcurrentHashMap<String,String>();

	private static boolean SELF_TEST = false; // Will exit after initialization

	private static ClassLoader plusClassLoader;

	Socket socket;
	
	public Server() {
		socket = null;
	}  

	public Server(Socket s) {
		socket = s;
		try {
			socket.setSoTimeout(SOCKET_TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}  

	public static void main(String[] args) {		
		ServerSocket ss;
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

		if (System.console()==null) {
			System.out.println("Console is null - logging System.out and System.err to file");
			File logDir = new File("log");
			if (logDir == null || !logDir.exists()) {
				System.out.println("Log directory does not exist - will be created");
			}
			if (logDir != null && !logDir.isDirectory()) {
				boolean success = logDir.mkdir();
				if (success) {
					System.out.println("Log directory created");
				} else {
					// Use file called log in the home directory to force console output
					System.out.println("Log directory could not be created - cannot store logs");					
				}
			}
			if (logDir != null && logDir.isDirectory()) {
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
		
		try {
			System.out.println("Opening HTTP_PORT "  + HTTP_PORT);
			ss = new ServerSocket(HTTP_PORT);

			if (initializeServer()) {   

				// Add shutdown hook
				//Runtime.getRuntime().addShutdownHook(new Thread() {
				//	public void run() { exit(0); } 
				//});
				
				if (SELF_TEST) {
					System.out.println("self test - exiting...");
				} else {
					System.out.println("Accepting connections on HTTP_PORT "  + ss.getLocalPort());
					viewPage("");  // Fire up the browser if Win or OS X
					
					// This is the main web server loop
					while (true) {
						Server s = new Server(ss.accept());
						s.start();
					}
				}
			}
		} catch (BindException b) {
			System.err.println("***\n*** Exit condition: \n***"+b.getMessage());
			viewPage("");  // Fire up the browser - server is probably already up
			exit(-2);
		} catch (Exception e) {
			System.err.println("***\n*** Exit condition: \n***"+e.getClass().getName()+":"+e.getMessage());
			exit(-1);
		} finally {
			System.out.println("Server Stopped");
			exit(0);
		}
	}
		
	public static void exit(int returnCode) {
		System.out.println("Server exit with status "+returnCode);
		closeAllConnections();
		System.exit(returnCode);
	}
	
	/** Each and every request goes through here in its own thread */
	public void run() {
		String method;  // GET and POST are treated the same
		String content_type;
		String content_disposition = null; // For downloads
		String version = "";
		String cookieValue = null;
		String newCookieValue = null;
		boolean keep_alive = KEEP_ALIVE;  // Get the dynamic default

		OutputStream os = null;
		InputStream is = null;
		try {
			os = socket.getOutputStream();			
			is = socket.getInputStream();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return;
		}
		
		do {
		try {
			String get = readLine(is);
			if (DEBUG) System.out.println("REQUEST="+get);
			StringTokenizer st = new StringTokenizer(get);
			if (get == null || !st.hasMoreTokens()) {
				System.out.println("****** Request is null - returning no results");
				keep_alive = false;
				break;
			}
			if (st.hasMoreTokens()) {
				method = st.nextToken();
			} else {
				keep_alive = false;
				break;
			}
			String file;
			if (st.hasMoreTokens()) {
				file = st.nextToken();
			} else {
				if (get.getBytes().length == 3) {  // EF BF BF (Not sure whether this means keep alive or not so FU!
					//if (ALLOW_KEEP_ALIVE) keep_alive = true;
					//System.out.println("EFBFBF? FU");
					keep_alive = false;
					break;
				} else {
					System.out.println("Invalid file request from "+socket.getRemoteSocketAddress()+" request="+Dumper.hexDump(get.getBytes()));
					keep_alive = false;
					break;
				}
			}

			if (method.equals("GET") || method.equals("POST")) {
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
					} else if (ALLOW_KEEP_ALIVE && get.equalsIgnoreCase("Connection: keep-alive")) {
						keep_alive = true;
					} else if (get.startsWith("Content-Type: multipart")) {
						int bi = get.indexOf("boundary=");
						if (bi > 0) {
							boundaryValue = get.substring(bi+9);
						}
						if (DEBUG) System.out.println("Boundary="+boundaryValue);
					}
					get = readLine(is);
				}
				
				if (method.equals("POST") && boundaryValue == null ) {
					if (DEBUG) System.out.println("Reading post stuff");
					StringBuffer formstuff = new StringBuffer();
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
				
				// Get URL encoded parameters
				HashMap<String,String> parms = new HashMap<String,String>();
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
				if (method.equals("POST") && boundaryValue != null ) {
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
				while (is.available() > 0) {
					int ec = is.read();
					if (DEBUG) System.out.println("Server: Extra="+Integer.toHexString(ec));
				}
								
				// Prepare the output
				if (restore_lockout) {
					os.write(getLogHeader("text/html", LOCKOUT_MESSAGE.getBytes().length, keep_alive).getBytes());
					os.write(LOCKOUT_MESSAGE.getBytes());
					socket.close();	
					return;
				}
				
				try {
					long startTime = System.currentTimeMillis();
					Database db = null;
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
						if (WWW_IN_JAR) {  // Get files from jar
							//file = "/www"+file;
							URL imageurl = Thread.currentThread().getContextClassLoader().getResource(file.substring(1));
							if (imageurl != null) {
								URLConnection urlcon = imageurl.openConnection();
								urlcon.setConnectTimeout(100);
								urlcon.setReadTimeout(100);
								iis = imageurl.openStream();
							}
						} else {  // Look in file system
							file = "www"+file;
							File imageurl = new File(file);
							if (imageurl != null) {
								iis = new FileInputStream(imageurl);
							}
						}
						if (iis != null) {
							ByteArrayOutputStream databuf = new ByteArrayOutputStream(); 
							int b = iis.read();
							while (b != -1) {
								databuf.write(b);
								b = iis.read();
							}
							if (DEBUG) System.out.println("Returning "+databuf.size()+" bytes for "+file);
							iis.close();
							os.write(getImageHeader(content_type, databuf.size(), keep_alive).getBytes());
							databuf.writeTo(os);
							os.flush();
							break;
						} else {
							System.out.println("Could not retrieve image/file "+file);
							os.write(("HTTP/1.1 404 File Not Found\r\n").getBytes());
							os.write(("Date: " + new java.util.Date() + "\r\n").getBytes());
							os.write(("Server: PermeAgility 1.0\r\n").getBytes());
							os.write(("Content-type: text/html" + "\r\n\r\n").getBytes());
							os.flush();
							break;
						}
					}

					// Authenticate the user
					if (cookieValue != null) {
						if (DEBUG) System.out.println("Server: looking for cookie |"+cookieValue+"| there are "+sessions.size()+" sessions");
						String u = sessions.get(cookieValue);
						if (u != null) {
							db = sessionsDB.get(u);
							if (DEBUG) System.out.println("Server: session lookup found "+db);
						}
					}

					// Logout if logged in and login class requested
					if (db != null && className.equals(LOGIN_CLASS)) {  // If you ask to login and you are already connected, then you have asked to log out
						System.out.println("User "+db.getUser()+" logging out");
						sessions.remove(cookieValue);  
							// Should remove other cookies for this user (They asked to log out - their user ID will be removed from memory)
							if (LOGOUT_KILLS_USER) {
								String u = db.getUser();
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
									}
									sessionsDB.remove(u).close();
								}
							}
							db = null;
					}
					
					// If username specified in parms and we do not have a connection we must be logging in
					String parmUserName = parms.get("USERNAME"); 
					if (db == null || parmUserName != null) {
						if (parmUserName != null) {  // Trying to log in
							try {
								db = sessionsDB.get(parmUserName);
								if (db == null || !db.isPassword(parms.get("PASSWORD"))) { 	
									if (db != null) {
										System.out.println("session with wrong password found - removing it");
										if (cookieValue != null) sessions.remove(cookieValue);
										sessionsDB.remove(parmUserName);
									}
									db = new Database(DB_NAME,parmUserName,parms.get("PASSWORD"));
									if (!db.isConnected()) {
										System.out.println("Database login failed for user "+parmUserName);
										db = null;
									}
								} else {
									if (DEBUG) System.out.println("Reusing connection");
								}
							} catch (Exception e) {
								System.out.println("Error logging in "+e);
							}
						}

						if (db != null && parmUserName != null) {
							if (DEBUG) System.out.println("Calculating new cookie for: "+parmUserName);
							newCookieValue = parmUserName + (Math.random() * 100000000);
							sessions.put(newCookieValue, parmUserName);
							if (!sessionsDB.containsKey(parmUserName)) {
								sessionsDB.put(parmUserName, db);
							}
							if (DEBUG) System.out.println("User "+db.getUser()+" logged in");
						}		

						if (db == null) {
							try {
								if (DEBUG) System.out.println("Using guest connection");
								db = getNonUserDatabase();
							} catch (Exception e) {
								System.out.println("Error logging in with guest "+e);
							}									
						}
					}
										
					// Set locale if specified (TODO: need to be by sessions)
					if (parms.containsKey("LOCALE") && db != null) {
						Locale l = Message.getLocale(parms.get("LOCALE"));
						db.setLocale(l);
				  		Menu.clearMenu(db.getUser());  // Menu cache for this user needs to be cleared
					}

					// Pull log text files from the log directory (only for Admin)
					if (db != null && file.startsWith("/log/") && db.getUser().equals("admin")) {
						if (DEBUG) System.out.println("Looking for log "+file);
						File logfile = new File(file.substring(1));
						if (logfile != null && logfile.exists()) {
							InputStream iis = new FileInputStream(logfile);
							theData = new byte[iis.available()];
							int bytesread = iis.read(theData);
							if (DEBUG) System.out.println("Returning "+theData.length+" bytes: "+bytesread+" bytes read");
							// need to check the number of bytes read here
							iis.close();
							os.write(getLogHeader(content_type, theData.length, keep_alive).getBytes());
							os.write(theData);
							os.flush();
							break;
						} else {
							System.out.println("Could not retrieve log file "+file);
						}
					}

					// Thumbnails should be for users only
					if (db != null && file.startsWith("/thumbnail")) {
						String tsize = parms.get("SIZE");
						String tid = parms.get("ID");
						if (DEBUG) System.out.println("Retrieving thumbnail "+tid);
						StringBuffer ttypeb = new StringBuffer();
						StringBuffer tfileb = new StringBuffer();
						theData = Thumbnail.getThumbnail(tid, tsize, ttypeb, tfileb);
						String ttype = ttypeb.toString();
						String tfile = tfileb.toString();
						if (theData != null) {
							os.write(getThumbnailImageHeader(ttype, tfile, theData.length, keep_alive).getBytes());
							os.write(theData);
							os.flush();
							if (DEBUG) System.out.println("Thumbnail(Server) - sent "+ttype+" size="+theData.length);
							break;							
						} else {
							System.out.println("Thumbnail(Server) - no data found in thumbnail "+tid);
							break;
						}
					}

					// Validate that class is allowed to be used
					if (db != null) {
						if (DEBUG) System.out.println("Authorizing user "+db.getUser()+" for class "+className);
						Object[] uRoles = userRoles.get(db.getUser());
						Object[] kRoles = keyRoles.get(className);
						boolean authorized = isRoleMatch(uRoles,kRoles);
						if (authorized) {
							if (DEBUG) System.out.println("User "+db.getUser()+" is allowed to use "+className);								
						} else {
							System.out.println("User "+db.getUser()+" is attempting to use "+className+" without authorization");
							parms.put("SECURITY_VIOLATION","You are not authorized to access "+className);
							className = HOME_CLASS;							
						}
					}
					// Instantiate the requested class and use it
					Class<?> classOf = Class.forName( className, true, plusClassLoader );
				    Object classInstance = classOf.newInstance();
				    
				    if (classInstance instanceof Weblet) {
				    	parms.put("REQUESTED_CLASS_NAME", className);
				    	Weblet weblet = (Weblet)classInstance;
				    	if (DEBUG) System.out.println("LOADING HTML PAGE="+className+" PARAMETER="+parms.toString());
						DatabaseConnection con = null;
						try {
							if (db != null) { 
								con = db.getConnection();
								if (con == null) {
									theData = "<BODY><P>Server is busy, please try again</P></BODY>".getBytes();
									System.out.println("!"+db.getUser());
								} else {
									if (parms.containsKey("LOCALE") && db != null) {
										if (DEBUG) System.out.println("Setting locale to "+parms.get("LOCALE"));
										Locale l = Message.getLocale(parms.get("LOCALE"));
										db.setLocale(l); // have to maintain this by cookie
									}
									theData = weblet.doPage(con, parms);
								}
							}
						} catch (Exception e) {
							System.out.println("Exception running weblet: ");
							e.printStackTrace();
							db.closeConnection(con);
							con = null;
						}
						if (db != null && con != null) {
							db.freeConnection(con);							
						}
				    } else if (classInstance instanceof Download) {
			    		Download downloadlet = (Download)classOf.newInstance();
						DatabaseConnection con = null;
						if (db != null) { 
							con = db.getConnection();
						}
						if (DEBUG) System.out.println("DOWNLOAD PAGE="+className+" PARAMETER="+parms.toString());
						theData = downloadlet.doPage(con, parms);
						if (db != null) {
							db.freeConnection(con);
						}
						// Do after to allow content-disposition to be dynamic if necessary
				    	content_type = downloadlet.getContentType();
				    	content_disposition = downloadlet.getContentDisposition();
					} else {
				    	System.out.println(file+" is not a proper class");
				    }
					if (DEBUG) System.out.println("---------------------" + className+" generated in "+(System.currentTimeMillis()-startTime)+" ms -------------------------");
					os.write(getHeader(content_type, theData.length, newCookieValue, content_disposition, keep_alive).getBytes());
					os.write(theData);
					os.flush();
				} catch (SocketException se) {  // Connection broken
					System.out.println("Exception:"+se);
					is.close();
					os.close();
					keep_alive = false;
				} catch (Exception e) {  // can't find the file
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
					keep_alive = false;
				}
			} else {  // method does not equal "GET" or "POST"
				if (version.startsWith("HTTP/")) {  // send a MIME header
					os.write(("HTTP/1.1 501 Not Implemented\r\n").getBytes());
					os.write(("Date: " + new java.util.Date() + "\r\n").getBytes());
					os.write(("Server: PermeAgility 1.0\r\n").getBytes());
					os.write(("Content-type: text/html" + "\r\n\r\n").getBytes()); 
				}       
				os.write("<HTML><HEAD><TITLE>Not Implemented</TITLE></HEAD>".getBytes());
				os.write("<BODY><H1>HTTP Error 501: Not Implemented</H1></BODY></HTML>".getBytes());
				os.flush();
				keep_alive = false;
			}
		} catch (SocketTimeoutException ste) {
//			System.out.println("Socket Timeout");
			keep_alive = false;
		} catch (Exception e) {
			System.out.println("Server exception:"+e);
			keep_alive = false;
		}
		// Rest after sending response
		if (keep_alive && KEEP_ALIVE_PAUSE_MS > 0) { try { Thread.sleep(KEEP_ALIVE_PAUSE_MS); } catch (Exception e) { System.out.println("Keep-Alive pause interrupted"); } }
		} while (keep_alive);

		try {
			socket.close();
			if (DEBUG && ALLOW_KEEP_ALIVE) System.out.println("Socket closed");  // Not interesting unless keep alive is enabled
		} catch (IOException ioe) {
			System.out.println("Error closing socket");
		}
	}   

	/** Read a line from the input stream into a string buffer */
	private String readLine(InputStream is) throws IOException {
		StringBuffer sb = new StringBuffer();
		int c = is.read();
			do {
				if (c != 0x0A && c != 0x0D) sb.append((char)c);
				if (is.available()>0) {
					c = is.read();
				}
				if (sb.length() > 1024) {
					break;
				}
			} while (c != 0x0A  && c != -1);
		return sb.toString();
	}
	
	/** Read the multipart request and put into parms (files come this way and sometimes form fields)
	 * If a file is uploaded, it will be put in a temp file and the file name will be in the parms */
	private void readMultipartData(InputStream is, String boundaryValue, HashMap<String,String> parms) {
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
				if (is.available() == 0) { 
					System.out.println("unavail");
					return; 
				}
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
			if (is.available() == 0) {
				System.out.println("---- Nothing available ----");
				return; 
			}
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
	
	/** Get HTML header adding a cookie and content disposition 
	 * @param keep_alive */
	public String getHeader(String ct, int size, String newCookieValue, String content_disposition, boolean keep_alive) {
		String responseHeader = "HTTP/1.1 200 OK\r\n"
			+"Date: " + new java.util.Date() + "\r\n"
			+"Server: PermeAgility 1.0\r\n"
			+(keep_alive ? "Connection: keep-alive\n" : "")
			+(newCookieValue != null ? "Set-Cookie: name=PermeAgilitySession"+HTTP_PORT+" value="+newCookieValue+"\r\n" : "")
			+"Content-length: " + size + "\r\n"
			+"Content-type: " + ct + "\r\n"
			+(content_disposition != null ? "Content-disposition: " + content_disposition + "\r\n" : "")
			+"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

	/* Get header for images */
	public String getImageHeader(String ct, int size, boolean keep_alive) {
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
	public String getThumbnailImageHeader(String ct, String fn, int size, boolean keep_alive) {
		String responseHeader = "HTTP/1.1 200 OK\r\n"+
			"Date: " + new java.util.Date() + "\r\n"+
			"Server: PermeAgility 1.0\r\n"+
			(keep_alive ? "Connection: keep-alive\n" : "")+
			"Cache-Control: max-age="+DB_IMAGE_EXPIRE_TIME+"\r\n"+
			"Expires: "+gmt_date_format.format((new Date((new Date()).getTime()+DB_IMAGE_EXPIRE_TIME)))+"\r\n"+
			//"Cache-Control: no-cache\r\n"+
			"Content-length: " + size + "\r\n"+
			"Content-type: " + ct + "\r\n"+
			(ct.startsWith("image") ? "Content-disposition: inline; filename=" : "Content-disposition: attachment; filename=" ) + fn + "\r\n"+
			"\r\n";
		if (DEBUG) System.out.println("RESPONSEHEADER="+responseHeader);
		return responseHeader;
	}

	/** Get the header for log files */
	public String getLogHeader(String ct, int size, boolean keep_alive) {
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

	/** Get the server's database connection 
	 *  (this is protected as only classes within this package should use this) */
	protected static Database getDatabase() {
		return database;
	}
	
	/** Get the mime content type based on the filename */
	public String getContentType(String name) {
		if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
		else if (name.endsWith(".txt") || name.endsWith(".log")) return "text/plain";
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

	/** Refresh the cached security model in the server */
	public static void refreshSecurity() {
		securityRefreshTime = new Date();
		DatabaseConnection con = database.getConnection();
		int entryCount = 0;
		try {
			QueryResult qr = new QueryResult(con.getSecurity().getAllUsers());
//			QueryResult qr = con.query("SELECT name, roles from OUser");
			if (qr != null) {
				for (int i=0;i<qr.size();i++) {
					String n = qr.getStringValue(i, "name");
					Object[] roles = qr.getLinkSetValue(i,"roles");
					if (roles != null) {
						if (DEBUG) System.out.println("Adding security key "+n+" - "+roles.length);
						entryCount++;
						userRoles.put(n, roles);
					}
				}
				System.out.println("Server.refreshSecurity() - loaded "+entryCount+" users");
			}
			entryCount = 0;
			qr = con.query("SELECT classname, _allowRead from menuItem");
			if (qr != null) {
				for (int i=0;i<qr.size();i++) {
					String n = qr.getStringValue(i, "classname");
					Object[] roles = qr.getLinkSetValue(i,"_allowRead");
					if (n != null && roles != null) {
						if (DEBUG) System.out.println("Adding security key "+n);
						entryCount++;
						keyRoles.put(n, roles);
					}
				}
				System.out.println("Server.refreshSecurity() - loaded "+entryCount+" menuItems");
			}
			
			for (String user : userRoles.keySet()) {
				// User object rules are compiled into a simple HashMap<String,Byte>
				Object[] roles = userRoles.get(user);  // I believe these are ODocuments or ORecordIds
				ArrayList<Set<ORule>> rules = new ArrayList<Set<ORule>>();  // To hold the rules
				for (Object role : roles) {
					ODocument r;
					if (role instanceof ORecordId) {
						r = con.getDb().getRecord((ORecordId)role);
					} else {
						r = (ODocument)role;
					}
					getRoleRules(new ORole(r),rules);				
				}
				if (DEBUG) System.out.println(user+" rules="+rules);
				// Collapse the rules into a single HashMap 
				HashMap<String,Number> newRules = new HashMap<String,Number>();
				for (Set<ORule> m : rules) {
					for (ORule rule : m) {
						ResourceGeneric rg = rule.getResourceGeneric();
						if (rg != null) {
							if (DEBUG) System.out.println("ResourceGeneric="+rg.getName()+" priv="+rule.getAccess());
							newRules.put(rg.getName(), rule.getAccess());
						}
						Map<String,Byte> spec = rule.getSpecificResources();
						for (String res : spec.keySet()) {
							String resource = res;
							Number newPriv = spec.get(res);
							if (DEBUG) System.out.println("Resource="+resource+" newPriv="+newPriv+" generic="+rule.getResourceGeneric());
							newRules.put(resource, newPriv);
						}
					}
				}
				if (DEBUG) System.out.println(user+" newRules="+newRules);
				userRules.put(user, newRules);
			}
		} catch (Exception e) {
			System.out.println("Error retrieving security model into cache - "+e.getMessage());
			e.printStackTrace();
		}
		database.freeConnection(con);
	}

	/** Returns true is one of the first set of roles is a match for a role in the second set - please pass in arrays of ORoles */
	public static boolean isRoleMatch(Object uRoles[], Object kRoles[]) {
		boolean authorized = false;

		if (uRoles == null || kRoles == null) {
			System.out.println("Server.isRoleAuthorized: kRoles/uRoles is null");
			return authorized;
		}

		OUT: for (Object ur: uRoles) {
			for (Object kr: kRoles) {
				if (ur != null && kr != null && ur.equals(kr)) {
					if (DEBUG) System.out.println("Server.isRoleAuthorized: Match on user role "+ur.toString()+" Authorized!");
					authorized = true;
					break OUT;
				}
			}
		}
		return authorized;
	}
	
	/** Recursive function to return the rules for a role that has been given (includes inherited rules) */
	public static int getRoleRules(ORole role, ArrayList<Set<ORule>> rules) {
		if (role.getParentRole() != null) {
			getRoleRules(role.getParentRole(), rules);
		} 
		Set<ORule> ru = (Set<ORule>)role.getRuleSet();
		rules.add(ru);
		return rules.size();
	}
	
	/** Get the connected user's roles in an object array (be warned, they could be ODocuments or ORecordIds) */ 
	public static Object[] getUserRoles(DatabaseConnection con) {
		return userRoles.get(con.getUser());  // I believe these are ODocuments or ORecordIds
	}

	/** Get the connected user's roles as a list that can be used in SQL. Example: #1:1,#1:2,#1:3 */
	public static String getUserRolesList(DatabaseConnection con) {
		Object[] roles = userRoles.get(con.getUser());
		if (roles != null) {
			StringBuffer rb = new StringBuffer();
			for(Object r : roles) {
				if (r != roles[0]) { rb.append(", "); }
				if (r instanceof ORecordId) {  // Sometimes, these are returned
					rb.append(((ORecordId)r).getIdentity());
				} else {
					rb.append(((ODocument)r).getIdentity());
				}
			}
			return rb.toString();  // These are ODocuments or ORecordIds
		} else {
			System.out.println("This is odd, "+con.getUser()+" has no roles?");
			return "";
		}
	}

	public static boolean isDBA(DatabaseConnection con) {
		String user = con.getUser();
		if (user.equals("admin") || user.equals("server") || user.equals("dba")) {
			return true;
		}
		return false;
	}
	
	/** Get the privileges that the connected user has to the given table */
	public static int getTablePriv(DatabaseConnection con, String table) {
		int priv = 0;
		String user = con.getUser();
		HashMap<String,Number> newRules = userRules.get(user);
		
		// if starts with database, it is a specific privilege
		if (table.startsWith("database.")) {
			Number r = newRules.get(table);
			if (r != null) {
				return r.intValue();
			}
		}
		
		// Find the most specific privilege for the table from the user's rules
		Number o;
		o = newRules.get(ResourceGeneric.BYPASS_RESTRICTED.getName()); 
		if (o != null) {
			//if (DEBUG) System.out.println("Found "+ResourceGeneric.BYPASS_RESTRICTED.getName()+"="+o);
			priv = o.intValue();
		}
		o = newRules.get(ResourceGeneric.CLASS.getName());
		if (o != null) {
			//if (DEBUG) System.out.println("Found "+ResourceGeneric.CLASS.getName()+"="+o);
			priv = o.intValue();
		}
		o = newRules.get(table.toLowerCase());
		if (o != null) {
			//if (DEBUG) System.out.println("Found database.class."+table.toLowerCase()+"="+o);
			priv = o.intValue();
		}
		return priv;
	}

	protected static HashMap<String,Number> getTablePrivs(String table) {
		HashMap<String,Number> cmap = tablePrivsCache.get(table);
		if (cmap != null) {
			return cmap;
		}
		
		if (DEBUG) System.out.println("Retrieving privs for table "+table);
		HashMap<String,Number> map = new HashMap<String,Number>();
		DatabaseConnection con = database.getConnection();		
		QueryResult roles = Weblet.getCache().getResult(con, "select from ORole");
		database.freeConnection(con);		
		for (ODocument role : roles.get()) {
			String roleName = role.field("name");
			ArrayList<Set<ORule>> rules = new ArrayList<Set<ORule>>();
			getRoleRules(new ORole(role),rules);
			for (Set<ORule> rs : rules) {
				for (ORule rule : rs) {
					if (rule.containsSpecificResource(table.toLowerCase())) {
						if (DEBUG) System.out.println("getTablePrivs: specific "+rule.toString()+": "+rule.getAccess());
						map.put(roleName, rule.getAccess());
					} else if (rule.getResourceGeneric() == ResourceGeneric.CLASS) {
						if (DEBUG) System.out.println("getTablePrivs: all classes: "+rule.getAccess());
						if (rule.getAccess() != null) {
							map.put(roleName, rule.getAccess());
						}
					} else if (rule.getResourceGeneric() == ResourceGeneric.BYPASS_RESTRICTED) {
						if (DEBUG) System.out.println("getTablePrivs: all classes: "+rule.getAccess());
						if (rule.getAccess() != null) {
							map.put(roleName, rule.getAccess());
						}
					}
				}
			}
		}		
		tablePrivsCache.put(table,map);
		return map;
	}
	
	public static void tablePrivUpdated(String table) {
		tablePrivsCache.remove(table);
	}
	
	public static String addTransientImage(String type, byte[] data) {
		String name = "IMG_"+(int)(Math.random()*1000000)+".jpg";
		transientImages.put(name, data);
		transientImageDates.put(name, new Date());
		transientImageTypes.put(name, type);
		return name;
	}
	
	/**  Call this when you update a table that the server or caches may be interested in   */
	public static void tableUpdated(String table) {
		if (table.equals("constant")) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - constants applied");
			DatabaseConnection con = database.getConnection();
			ConstantOverride.apply(con);
			database.freeConnection(con);
		}
		if (table.equals("columns")) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - columns cache cleared");
			Server.clearColumnsCache("ALL");  // Don't know which table or which row in columns table so clear all
		}
		if (table.equals("locale") || table.equals("message")) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - messages refreshed and menus cleared");
			DatabaseConnection con = database.getConnection();
			Message.initialize(con);
			database.freeConnection(con);
			Menu.clearCache();
			Table.clearDataTypes();
		}
		if (table.equals("user")
		  || table.equals("menu")
		  || table.equals("menuItem")
		  || table.equals("ORole")
		  || table.equals("OUser")) {
			refreshSecurity();  // Will display its own messages
			Menu.clearCache();
		}
		if (DEBUG) System.out.println("Server: tableUpdated("+table+") - query cache updated");
		Weblet.queryCache.refreshContains(table);
	}

	/** Return table information - includes superClass, abstract, */
	public static QueryResult getTableInfo(String table) {
		DatabaseConnection con = database.getConnection();
		QueryResult result =  con.query("select from (select expand(classes) from metadata:schema) where name = '"+table+"'");
		database.freeConnection(con);
		return result;
	}
	
	public static int columnsCacheSize() {
		return columnsCache.size();
	}
	
	public static void clearColumnsCache(String table) {
		if (table.equals("ALL")) {
			columnsCache.clear();
			if (DEBUG) System.out.println("Server.clearColumnsCache() Columns cleared for all tables");
		} else {
			columnsCache.remove(table);
			if (DEBUG) System.out.println("Server.clearColumnsCache() Columns cleared for table "+table);
		}
	}
	
	/**
	 * Returns column names and column details including type, and linked class
	 * overrides column order based on value in columnList in columns table and add inherited columns as well
	 * (There must be a cleaner way to code this but hey, it works and it is fairly simple)
	 */
	public static QueryResult getColumns(String table) {
		return getColumns(table, null);
	}

	public static QueryResult getColumns(String table, String columnOverride) {
		
		if (columnOverride == null) {
			QueryResult ccolumns = columnsCache.get(table);
	 		if (ccolumns != null) {
				return ccolumns;
			}
		}
		DatabaseConnection con = database.getConnection();
		// Original list of columns
		if (con == null) {
			System.out.println("Server.getColumns() cannot get a connection");
		} else {
			QueryResult result =  con.query("select from (select expand(properties) from (select expand(classes) from metadata:schema) where name = '" + table + "') order by name");
	
			// Get details about the table so we can add superClass attributes 
			QueryResult tableInfo = Server.getTableInfo(table);
			String superClass = tableInfo.getStringValue(0, "superClass");
			if (superClass != null) {
				QueryResult superColumns = Server.getColumns(superClass);
				for (ODocument sc : superColumns.get()) {
					if (result.findFirstRow("name", (String)sc.field("name")) == -1) {
						result.append(sc);  // Only add if not already there (latest version brings the superclass columns)
					}
				}
			}
			
			// List of columns to override natural alphabetical order
			QueryResult columnList = null;
			if (columnOverride == null) {
				columnList = con.query("SELECT columnList FROM "+Setup.TABLE_COLUMNS+" WHERE name='"+table+"'");
			}
			database.freeConnection(con);
			boolean addDynamicColumns = true;
			if (columnOverride != null || (columnList != null && columnList.size()>0)) {
				String list = (columnOverride == null ? columnList.getStringValue(0, "columnList") : columnOverride);
				String columnNames[] = list.split(",");
				if (columnNames.length > 0 ) {
					ArrayList<ODocument> newList = new ArrayList<ODocument>();
					for (String name : columnNames) {
						if (name.trim().equals("-")) {
							if (DEBUG) System.out.println("ColumnOverride="+columnOverride+" no dynamic columns");
							addDynamicColumns = false;
						}
						if (!name.trim().startsWith("-")) {
							int i = result.findFirstRow("name", name.trim());
							if (i>-1) {
								newList.add(result.get(i));
							} else {
								if (name.trim().startsWith("button(")) {
									ODocument bd = new ODocument();
									bd.field("name",name.trim());
									newList.add(bd);
								} else {
									System.out.println("Could not find column "+name+" in the columns for table "+table+" even though this column was explicitly mentioned in the columns table - huh!");
								}
							}
						}
					}
					if (addDynamicColumns) {
						for (ODocument col : result.get()) {
							String name = col.field("name");
							boolean found = false;
							for (String cn : columnNames) {
								String cnt = cn.trim();
								if (cnt.equals(name) 
								  || (cnt.startsWith("-") && cnt.substring(1).equals(name))) {
									found = true;
								}
							}
							if (!found) {
								newList.add(col);
							}
						}
					}
					if (newList.size() > 0 ) {
						QueryResult newResult = new QueryResult(newList);
						if (columnOverride == null) {
							columnsCache.put(table, newResult);
						}
						return newResult;
					}
				}
			}
			if (columnOverride == null) {
				columnsCache.put(table, result);							
			}
			return result;
		}
		return null;
	}

	public static boolean changePassword(DatabaseConnection con, String oldPassword, String newPassword) {
		if (!con.isPassword(oldPassword)) {
			System.out.println("Attempt to change password with incorrect old password");
			return false;
		}
		DatabaseConnection c = database.getConnection();
		try {
			if (c != null) {
				Object rc = c.update("UPDATE OUser SET password='"+newPassword+"' WHERE name='"+con.getUser()+"'");
				System.out.println(con.getUser()+" password changed successfully "+rc);
				con.setPassword(newPassword);
			    if (con.getUser().equalsIgnoreCase("server")) {
			    	//System.out.println("Setting localsetting for server password using key="+Server.DB_NAME + Server.HTTP_PORT+" and pass="+newPassword);
			    	Server.setLocalSetting(Server.DB_NAME + Server.HTTP_PORT, newPassword);
			    }
				return true;
			} else {
				System.out.println("Server.changePassword() - could not get a server connection");
				return false;
			}
		} catch (Exception e) {
			System.out.println("Cannot change password for user "+con.getUser());
			e.printStackTrace();
			return false;
		} finally {
			if (c != null) database.freeConnection(c);
		}
	}

	public static void viewPage(String url) {
		Browser.displayURL("http://localhost:"+HTTP_PORT+"/"+url);
	}

	public static void setLocalSetting(String key, String value) {
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
	
	private static String getLocalSetting(String key, String def) {
		try {
			localSettings.load(new FileReader(SETTINGS_FILE));
		} catch (IOException fnf) { 
			System.out.println("Cannot open init file - assuming defaults");
		}
		return localSettings.getProperty(key,def);	
	}
	
	static boolean initializeServer() {
		System.out.println("Initializing server using OrientDB Version "+OConstants.getVersion()+" Build number "+OConstants.getBuildNumber());
//		OGlobalConfiguration.CACHE_LOCAL_ENABLED.setValue(false);  // To ensure concurrency across threads (pre 2.0-M3)
		try {
			String p = getLocalSetting(DB_NAME+HTTP_PORT, "");
			//System.out.println("Localsetting for password is "+p);
			database = new Database(DB_NAME, "server", (p == null || p.equals("") ? "server" : p));
			if (!database.isConnected()) {    
				System.out.println("Panic: Cannot login with server user, maybe this is first time so will try admin/admin");
				database = new Database(DB_NAME, "admin", "admin");
			}
			if (!database.isConnected()) {
				System.out.println("Unable to acquire initial connection for "+DB_NAME);
				if (DB_NAME.startsWith("plocal")) {
					System.out.println("Creating new database Using saved password key="+DB_NAME+HTTP_PORT+". pass="+p);
					String restore = getLocalSetting("restore", null);
					database.createLocal((restore == null ? DEFAULT_DBFILE : restore));  // New database will default to password given in local setting
					database.fillPool();
					if (restore != null) {
						setLocalSetting("restore",null);
					}
				} else {
					System.out.println("***\n*** Exit condition: couldn't connect to remote as server, please add server OUser with admin role - Exiting.\n***");
					exit(-1);					
				}
			}
			if (database.isConnected()) {
				DatabaseConnection con = database.getConnection();
				if (!Setup.checkInstallation(con)) {
					System.out.println("---\n--- Warning condition: checkInstallation failed - check install messages in context\n---");
				}
//				database.freeConnection(con);
//				database.close();  // Close all the connections after installation check
				database.setPoolSize(SERVER_POOL_SIZE);
//				con = database.getConnection();
				System.out.println("Connected to database name="+DB_NAME+" version="+database.getClientVersion());
				if (!ConstantOverride.apply(con)) {
					System.out.println("***\n*** Exit condition: Could not apply constant overrides\n***");
					exit(-1);
				}
				plusClassLoader = PlusClassLoader.get();
				if (plusClassLoader == null) {
					System.out.println("***\n*** Exit condition: Could not initialize the PlusClassLoader\n***");
					exit(-1);
				}
				// Set the class loader for the currentThread (and all the Children so that plus's will work)
				currentThread().setContextClassLoader(plusClassLoader);
				
				refreshSecurity();
				if (keyRoles.size() < 1) {
					System.out.println("***\n*** Exit condition: No key roles found for security - no functions to enable\n***");
					exit(-1);
				}
				
				messages = new Message(con);
				Thumbnail.setDatabase(database);
				database.freeConnection(con);
				
				if (restore_lockout) restore_lockout = false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/** Get the guest connection */
	public Database getNonUserDatabase() {
		if (dbNone == null)	{  // Just once, thank you
			Database d = null;
			try {
				d = new Database(DB_NAME,"guest","guest");
				d.setPoolSize(GUEST_POOL_SIZE);
//				d = new Database(DB_NAME,"admin","admin");  // Just incase you change guest password and need to login
				dbNone = d;
			} catch (Exception e) {
				System.out.println("Cannot connect guest database");
			}
		}
		return dbNone;
	}
	
	protected static void closeAllConnections() {
		System.out.print("dropping all connections...");
		for (Database d : sessionsDB.values()) {
			d.close();
		}
		sessions.clear();
		sessionsDB.clear();
		if (database != null) {
			database.close();
			database = null;
		}
		System.gc();
		System.out.println("done");
	}
	
	public static Date getServerInitTime() {   return serverInitTime;  }
	public static Date getSecurityRefreshTime() {   return securityRefreshTime;  }
}
