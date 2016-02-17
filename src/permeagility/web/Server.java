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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import permeagility.util.Browser;
import permeagility.util.ConstantOverride;
import permeagility.util.Database;
import permeagility.util.DatabaseConnection;
import permeagility.util.Dumper;
import permeagility.util.PlusClassLoader;
import permeagility.util.Setup;

/** This is the PermeAgility web server - it handles security, database connections and some useful caches for privileges and such
  * all web requests go through the run() function for each thread/socket
  *  
  *  Parameters: [port(1999)] [db(plocal:db)] [selftest] 
  */
public class Server implements Runnable {

	private static int HTTP_PORT = 1999;  // First parameter
	private static String DB_NAME = "plocal:db";  // Second parameter
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
	public static int SOCKET_TIMEOUT = 180000;  // Three minute timeout
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
	public static int SERVER_POOL_SIZE = 5;
	public static boolean LOGOUT_KILLS_USER = false; // Set this to true to kill all sessions for a user when a user logs out (more secure)
	public static String LOCKOUT_MESSAGE = "<p>The system is unavailable because a system restore is being performed. Please try again later.</p><a href='/'>Try it now</a>";
	
	private static Database database;  // Server database connection (internal)
	private static Database dbNone = null;  // Used for login and account request

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

	Socket socket;
        
	private static ExecutorService executor = Executors.newCachedThreadPool();

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

		if (System.console()==null) {
			System.out.println("Console is null - logging System.out and System.err to file");
			File logDir = new File("log");
			if (!logDir.exists()) {
				System.out.println("Log directory does not exist - will be created");
			}
			if (!logDir.isDirectory()) {
				boolean success = logDir.mkdir();
				if (success) {
					System.out.println("Log directory created");
				} else {
					// Use file called log in the home directory to force console output
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
		ServerSocket ss = null;
		try {
			System.out.println("Opening HTTP_PORT "  + HTTP_PORT);
			ss = new ServerSocket(HTTP_PORT);  // Do this before init in case already bound
			if (initializeServer()) {   
				// Add shutdown hook
				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						closeAllConnections(); 
					} 
				});
				if (SELF_TEST) {
					System.out.println("self test - exiting...");
				} else {
					System.out.println("Accepting connections on HTTP_PORT "  + ss.getLocalPort());
					viewPage("");  // Fire up the browser if Win or OS X
					
					// This is the main web server loop
					while (true) {
						//Server s = new Server(ss.accept());
						//s.start();
                                            executor.execute(new Server(ss.accept()));
					}
				}
			} else {
				System.out.println("Failed to initialize server");
			}
			//ss.close();
		} catch (BindException b) {
			System.err.println("***\n*** Exit condition: \n***"+b.getMessage());
			viewPage("");  // Fire up the browser - server is probably already up
			exit(-2);
		} catch (Exception e) {
			System.err.println("***\n*** Exit condition: \n***"+e.getClass().getName()+":"+e.getMessage());
			exit(-1);
		} finally {
                    executor.shutdown();
                    if (ss != null) {
                        try {  ss.close();  } catch (Exception e) { e.printStackTrace();  }
                    } 
                    System.out.println("Server Stopped");
                    exit(0);
		}
	}
		
	public static void exit(int returnCode) {
		System.out.println("Server exit with status "+returnCode);
                
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
		Locale requestLocale = null;
		
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

			if (method.equals("GET") || method.equals("POST") || method.equals("PUT") || method.equals("DELETE")) {
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
						requestLocale = new Locale(language);
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
				if ( ( method.equals("POST")  || method.equals("PUT") ) && boundaryValue != null ) {
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
					Database userdb = null;
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
									sessionsDB.remove(u).close();
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
					
					// Pull log text files from the log directory (only for Admin)
					if (userdb != null && file.startsWith("/log/") && userdb.getUser().equals("admin")) {
						if (DEBUG) System.out.println("Looking for log "+file);
						File logfile = new File(file.substring(1));
						if (logfile.exists()) {
                                                        returnFile(file, logfile, keep_alive, os);
							break;
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
                                                    break;
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
							break;							
						} else {
							System.out.println("Thumbnail(Server) - no data found in thumbnail "+tid);
							break;
						}
					}

					// Validate that class is allowed to be used
					if (userdb != null) {
						if (DEBUG) System.out.println("Authorizing user "+userdb.getUser()+" for class "+className);
						if (Security.authorized(userdb.getUser(),className)) {
							if (DEBUG) System.out.println("User "+userdb.getUser()+" is allowed to use "+className);								
						} else {
                                                    if (userdb.getUser().equals("admin") && className.equals("permeagility.web.Query")) { 
                                                        // Allow admin to use Query tool if something needs to be fixed with security or the database
                                                    } else {
							System.out.println("User "+userdb.getUser()+" is attempting to use "+className+" without authorization");
							parms.put("SECURITY_VIOLATION","You are not authorized to access "+className);
							className = HOME_CLASS;							
                                                    }
						}
					}
					// Instantiate the requested class and use it
					Class<?> classOf = Class.forName( className, true, plusClassLoader );
                                        Object classInstance = classOf.newInstance();
				    
				    if (classInstance instanceof Weblet) {
				    	parms.put("REQUESTED_CLASS_NAME", className);
				    	parms.put("COOKIE_VALUE", cookieValue);
				    	Weblet weblet = (Weblet)classInstance;
				    	if (DEBUG) System.out.println("LOADING HTML PAGE="+className+" PARAMETER="+parms.toString());
						DatabaseConnection con = null;
						try {
							if (userdb != null) { 
								con = userdb.getConnection();
								if (con == null) {
									theData = "<BODY><P>Server is busy, please try again</P></BODY>".getBytes();
									System.out.println("!"+userdb.getUser());
								} else {
									theData = weblet.doPage(con, parms);
								}
							}
						} catch (Exception e) {
							System.out.println("Exception running weblet: ");
							e.printStackTrace();
							ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
							e.printStackTrace(new PrintWriter(dataStream));
							theData = dataStream.toByteArray();
							userdb.closeConnection(con);  // Assume the connection has gone bad
							con = null;
						}
						if (userdb != null && con != null) {
							userdb.freeConnection(con);							
						}
				    } else if (classInstance instanceof Download) {
			    		Download downloadlet = (Download)classOf.newInstance();
						DatabaseConnection con = null;
						if (userdb != null) { 
							con = userdb.getConnection();
							if (con != null) {
								if (DEBUG) System.out.println("DOWNLOAD PAGE="+className+" PARAMETER="+parms.toString());
								theData = downloadlet.doPage(con, parms);
								userdb.freeConnection(con);
							}
						}
						// Do after to allow content-disposition to be dynamic if necessary
				    	content_type = downloadlet.getContentType();
				    	content_disposition = downloadlet.getContentDisposition();
				    } else {
				    	System.out.println(file+" is not a proper class");
				    }
                                    if (DEBUG) System.out.println("---------------------" + className+" generated in "+(System.currentTimeMillis()-startTime)+" ms -------------------------");
                                    if (parms.containsKey("RESPONSE_REDIRECT")) {
                                        os.write(getRedirectHeader(parms).getBytes());
                                    } else {
                                        if (theData != null) {
                                            os.write(getHeader(content_type, theData.length, newCookieValue, content_disposition, keep_alive).getBytes());
                                            os.write(theData);
                                        }
                                    }
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
			} else {  // method does not equal "GET" or "POST" or "PUT" or "DELETE"
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
		StringBuilder sb = new StringBuilder();
		int c = is.read();
			do {
				if (c != 0x0A && c != 0x0D) { sb.append((char)c); }
				if (is.available()>0) { c = is.read(); }
				if (sb.length() > 1024) { break; }
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
	
        /* Return a file response and flush - file is streamed in 1024 byte chunks */
        private void returnFile(String filename, File thefile, boolean keep_alive, OutputStream os) throws Exception {
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

        /** Return Redirect header */
	public String getRedirectHeader(HashMap<String,String> parms) {
		String responseHeader = "HTTP/1.1 303 See other\r\n"
			+"Location: " + parms.get("RESPONSE_REDIRECT") + "\r\n"
       			+"Content-length: 0\r\n"   // Because Firefox is too stupid to understand the redirect without this
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
			(ct.startsWith("image") || ct.endsWith("pdf") ? "Content-disposition: inline; filename=\"" : "Content-disposition: attachment; filename=\"" ) + fn +  "\"\r\n"+
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
	protected static DatabaseConnection getServerConnection() {
		return database.getConnection();
	}
	
	protected static void freeServerConnection(DatabaseConnection dbc) {
		if (dbc != null) database.freeConnection(dbc);
	}
	
	protected static String getServerUser() {
		return database.getUser();
	}
	
	protected static String getClientVersion() {
		return database.getClientVersion();
	}
	
	/** Get the mime content type based on the filename */
	public String getContentType(String name) {
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

	public static String addTransientImage(String type, byte[] data) {
		String name = "IMG_"+(int)(Math.random()*1000000)+".jpg";
		transientImages.put(name, data);
		transientImageDates.put(name, new Date());
		transientImageTypes.put(name, type);
		return name;
	}
	
	/**  Call this when you update a table that the server or caches may be interested in   */
	public static void tableUpdated(String table) {
		if (table.equalsIgnoreCase("metadata:schema")) {
			DatabaseConnection con = getServerConnection();
			if (DEBUG) System.out.println("Server: schema updated - reloading");
			//clearColumnsCache("ALL");
			con.getSchema().reload();
			freeServerConnection(con);
		} else if (table.equals("constant")) {
			if (DEBUG) System.out.println("Server: tableUpdated("+table+") - constants applied");
			DatabaseConnection con = getServerConnection();
			ConstantOverride.apply(con);
			freeServerConnection(con);
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
			DatabaseConnection con = getServerConnection();
			Message.initialize(con);
			freeServerConnection(con);
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
	
	public static List<String> getPickValues(String table, String column) {
		if (pickValues.size() == 0) {
			updatePickValues();  // There must be a few by default - empty means not loaded yet (Note: restart server if delete)
		}
		return pickValues.get(table+"."+column);
	}

	public static void updatePickValues() {
		DatabaseConnection con = null;
		try {
			con = getServerConnection();
			for (ODocument values : con.getDb().browseClass(Setup.TABLE_PICKVALUES)) {
				String v[] = values.field("values").toString().split(",");
				ArrayList<String> list = new ArrayList<String>();
				for (String s : v) {
					list.add(s);
				}
				pickValues.put(values.field("name"),list);
			}
		} catch (Exception e) {
			System.err.println("Error getting pickValues: "+e.getMessage());
		} finally {
			if (con != null) freeServerConnection(con);
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
		try {
			String p = getLocalSetting(DB_NAME+HTTP_PORT, null);
			//System.out.println("Localsetting for password is "+p);
			if (DB_NAME.startsWith("plocal")) {  // Install database hook for Audit Trail and other triggers
				System.out.println("Installing database hook for Audit Trail");
				databaseHook = new DatabaseHook();
			}
//                        database = new Database(DB_NAME, "admin", "admin");
			database = new Database(DB_NAME, "server", (p == null ? "server" : p));
			if (!database.isConnected()) {    
                            System.out.println("Panic: Cannot login with server user, maybe this is first time so will try admin/admin");
                            database = new Database(DB_NAME, "admin", "admin");
			}
			if (!database.isConnected()) {
				System.out.println("Unable to acquire initial connection for "+DB_NAME);
				if (DB_NAME.startsWith("plocal")) {
					System.out.println("Creating new database Using saved password key="+DB_NAME+HTTP_PORT+". pass="+p);
					String restore = getLocalSetting("restore", null);
					database.createLocal((restore == null ? DEFAULT_DBFILE : restore),p);  // New database will default to password given in local setting
                                        database = new Database(DB_NAME, "server", (p == null ? "server" : p));  // createLocal will create the server role/user
                                        database.fillPool();
					if (database.getPooledCount() > 0 && restore != null) {
						setLocalSetting("restore",null);  // Clear restore only if successful
					}
				} else {
					System.out.println("***\n*** Exit condition: couldn't connect to remote as server, please add server OUser with admin role - Exiting.\n***");
					exit(-1);					
				}
			}
			if (database.isConnected()) {
				DatabaseConnection con = getServerConnection();
				
				if (!Setup.checkInstallation(con)) {
					System.out.println("---\n--- Warning condition: checkInstallation failed - check install messages in context\n---");
				}

				database.setPoolSize(SERVER_POOL_SIZE);
				System.out.println("Connected to database name="+DB_NAME+" version="+database.getClientVersion());
				
				// Initialize security
				Security.refreshSecurity();
				if (Security.keyRoleCount() < 1) {
					System.out.println("***\n*** Exit condition: No key roles found for security - no functions to enable\n***");
					exit(-1);
				}
				
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
				
				Message.initialize(con);
				
				freeServerConnection(con);
				
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
		if (dbNone == null)	{  
			Database d = null;
			try {
				d = new Database(DB_NAME,"guest","guest");
				d.setPoolSize(GUEST_POOL_SIZE);
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
	public static Date getSecurityRefreshTime() {   return Security.securityRefreshTime;  }
	public static String getCodeSource() { return codeSource; }
	public static String getDBName() { return DB_NAME; }
	public static int getHTTPPort() { return HTTP_PORT; }
	
}
