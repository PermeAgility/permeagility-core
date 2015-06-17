package permeagility.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.web.Server;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.ORule.ResourceGeneric;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class Security {

	private static Database database;
	public static Date securityRefreshTime = new Date();
	private static ConcurrentHashMap<String,Object[]> userRoles = new ConcurrentHashMap<String,Object[]>();
	private static ConcurrentHashMap<String,HashMap<String,Number>> userRules = new ConcurrentHashMap<String,HashMap<String,Number>>();
	private static ConcurrentHashMap<String,Object[]> keyRoles = new ConcurrentHashMap<String,Object[]>();
	//	private static ConcurrentHashMap<String,QueryResult> columnsCache = new ConcurrentHashMap<String,QueryResult>();
	private static ConcurrentHashMap<String,HashMap<String,Number>> tablePrivsCache = new ConcurrentHashMap<String,HashMap<String,Number>>();

	public Security() {
		// TODO Auto-generated constructor stub
	}

	// Must be called before anything else
	public static void setDatabase(Database db) {
		database = db;
	}
	
	public static boolean authorized(String user, String className) {
		Object[] uRoles = userRoles.get(user);
		Object[] kRoles = keyRoles.get(className);
		return isRoleMatch(uRoles,kRoles);
	}
	
	public static int keyRoleCount() { 
		return keyRoles.size();
	}
	
	/** Refresh the cached security model in the server */
	public static void refreshSecurity() {
		securityRefreshTime = new Date();
		DatabaseConnection con = database.getConnection();
		int entryCount = 0;
		try {
			QueryResult qr = new QueryResult(con.getSecurity().getAllUsers());
			if (qr != null) {
				for (int i=0;i<qr.size();i++) {
					String n = qr.getStringValue(i, "name");
					Object[] roles = qr.getLinkSetValue(i,"roles");
					if (roles != null) {
						if (Server.DEBUG) System.out.println("Adding security key "+n+" - "+roles.length);
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
						if (Server.DEBUG) System.out.println("Adding security key "+n);
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
					Security.getRoleRules(new ORole(r),rules);				
				}
				if (Server.DEBUG) System.out.println(user+" rules="+rules);
				// Collapse the rules into a single HashMap 
				HashMap<String,Number> newRules = new HashMap<String,Number>();
				for (Set<ORule> m : rules) {
					for (ORule rule : m) {
						ResourceGeneric rg = rule.getResourceGeneric();
						if (rg != null) {
							if (Server.DEBUG) System.out.println("ResourceGeneric="+rg.getName()+" priv="+rule.getAccess());
							newRules.put(rg.getName(), rule.getAccess());
						}
						Map<String,Byte> spec = rule.getSpecificResources();
						for (String res : spec.keySet()) {
							String resource = res;
							Number newPriv = spec.get(res);
							if (Server.DEBUG) System.out.println("Resource="+resource+" newPriv="+newPriv+" generic="+rule.getResourceGeneric());
							newRules.put(resource, newPriv);
						}
					}
				}
				if (Server.DEBUG) System.out.println(user+" newRules="+newRules);
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
					//if (DEBUG) System.out.println("Server.isRoleAuthorized: Match on user role "+ur.toString()+" Authorized!");
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
			StringBuilder rb = new StringBuilder();
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

	public static HashMap<String,Number> getTablePrivs(String table) {
		HashMap<String,Number> cmap = Security.tablePrivsCache.get(table);
		if (cmap != null) {
			return cmap;
		}
		
		if (Server.DEBUG) System.out.println("Retrieving privs for table "+table);
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
						if (Server.DEBUG) System.out.println("getTablePrivs: specific "+rule.toString()+": "+rule.getAccess());
						map.put(roleName, rule.getAccess());
					} else if (rule.getResourceGeneric() == ResourceGeneric.CLASS) {
						if (Server.DEBUG) System.out.println("getTablePrivs: all classes: "+rule.getAccess());
						if (rule.getAccess() != null) {
							map.put(roleName, rule.getAccess());
						}
					} else if (rule.getResourceGeneric() == ResourceGeneric.BYPASS_RESTRICTED) {
						if (Server.DEBUG) System.out.println("getTablePrivs: all classes: "+rule.getAccess());
						if (rule.getAccess() != null) {
							map.put(roleName, rule.getAccess());
						}
					}
				}
			}
		}		
		Security.tablePrivsCache.put(table,map);
		return map;
	}

	public static void tablePrivUpdated(String table) {
		Security.tablePrivsCache.remove(table);
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
			    	Server.setLocalSetting(Server.getDBName() + Server.getHTTPPort(), newPassword);
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

}
