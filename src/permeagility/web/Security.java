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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.ORule.ResourceGeneric;
import com.orientechnologies.orient.core.metadata.security.OSecurity;
import com.orientechnologies.orient.core.metadata.security.OUser;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.HashSet;
import java.util.List;
import permeagility.util.Database;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

public class Security {

    public static boolean DEBUG = false;
    public static Date securityRefreshTime = new Date();
    
    private static final ConcurrentHashMap<String,Set<String>> userRoles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,HashMap<String,Number>> userRules = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,Set<String>> keyRoles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,HashMap<String,Number>> tablePrivsCache = new ConcurrentHashMap<>();
    public static boolean CACHE_TABLE_PRIVS = false;
    
    public static boolean authorized(String user, String className) {
        Set<String> uRoles = userRoles.get(user);
        Set<String> kRoles = keyRoles.get(className);
        return isRoleMatch(uRoles,kRoles);
    }

    public static int keyRoleCount() { 
        return keyRoles.size();
    }

    /** Refresh the cached security model in the server */
    public static void refreshSecurity() {

        // If security has changed, close all existing connections for all users but admin
        for (String usr : Server.sessionsDB.keySet()) {
            if (usr != null && !usr.equals("admin")) {
                Database udb = Server.sessionsDB.get(usr);
                udb.close();
            }
        }
  
        DatabaseConnection con = Server.getServerConnection();
        System.out.println("Security: refreshing using "+con.getUser()+" user");
        int entryCount = 0;
        try {
            OSecurity osec = con.getSecurity();
            List<ODocument> users = osec.getAllUsers();
            if (users != null) {
                for (ODocument user : users) {
                    String n = user.field("name");
                    OUser u = osec.getUser(n);
                    Set<ORole> roles = u.getRoles();
                    if (roles != null) {
                        if (DEBUG) System.out.println("Adding security keyuser "+n+" - "+roles.size());
                        entryCount++;
                        Set<String> roleSet = new HashSet<>();
                        for (ORole r : roles) {
                            if (r != null) {
                                roleSet.add(r.getDocument().getIdentity().toString());
                            }
                        }
                        userRoles.put(n, roleSet);
                    }
                }
                System.out.println("Security.refreshSecurity() - loaded "+entryCount+" users");
            }
            entryCount = 0;
            QueryResult qr = con.query("SELECT FROM menuItem");
            if (qr != null) {
                for (ODocument menuItem : qr.get()) {
                        String n = menuItem.field("classname");
                        Set<ODocument> roles = menuItem.field("_allowRead");
                        if (n != null && roles != null) {
                                if (DEBUG) System.out.println("Adding security keyrole "+n);
                                entryCount++;
                                Set<String> roleSet = new HashSet<>();
                                for (ODocument r : roles) {
                                    if (r != null) {
                                        roleSet.add(r.getIdentity().toString());
                                    }
                                }
                                keyRoles.put(n, roleSet);
                        }
                }
                System.out.println("Security.refreshSecurity() - loaded "+entryCount+" menuItems");
            }

            for (String user : userRoles.keySet()) {
                // User object rules are compiled into a simple HashMap<String,Byte>
                Set<String> roles = userRoles.get(user);  
                ArrayList<Set<ORule>> rules = new ArrayList<>();  // To hold the rules
                for (String role : roles) {
                    getRoleRules(osec.getRole((con.get(role)).getIdentity()),rules);				
                }
                if (DEBUG) System.out.println(user+" rules="+rules);
                // Collapse the rules into a single HashMap 
                HashMap<String,Number> newRules = new HashMap<>();
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
        } finally {
            Server.freeServerConnection(con);
        }
        securityRefreshTime = new Date();
    }

    /** Returns true is one of the first set of roles is a match for a role in the second set - please pass in arrays of ORoles */
    public static boolean isRoleMatch(Set<String> uRoles, Set<String> kRoles) {
        boolean authorized = false;
        if (uRoles == null || kRoles == null) {
            System.out.println("Server.isRoleAuthorized: kRoles/uRoles is null");
            return authorized;
        }
        OUT: for (String ur: uRoles) {
            for (String kr: kRoles) {
                if (ur != null && kr != null && ur.equals(kr)) {
                    if (DEBUG) System.out.println("Server.isRoleAuthorized: Match on user role "+ur+" Authorized!");
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
    public static Set<String> getUserRoles(DatabaseConnection con) {
        return userRoles.get(con.getUser());  
    }

    /** Get the connected user's roles as a list that can be used in SQL. Example: #1:1,#1:2,#1:3 */
    public static String getUserRolesList(DatabaseConnection con) {
        Set<String> roles = userRoles.get(con.getUser());
        if (roles != null) {
            StringBuilder rb = new StringBuilder();
            String comma = "";
            for(String r : roles) {
                rb.append(comma);
                rb.append(r);
                comma = ",";
            }
            if (DEBUG) System.out.println("Security.getUserRolesList for user "+con.getUser()+" = "+rb.toString());
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
        if (DEBUG) {
            for (String n : newRules.keySet()) {
                System.out.println("Security.getTablePriv: rule="+n+" access="+newRules.get(n));
            }
        }
        
        // Find the most specific privilege for the table from the user's rules
        Number o;
        o = newRules.get(ResourceGeneric.BYPASS_RESTRICTED.getName()); 
        if (o != null) {
            if (DEBUG) System.out.println("Security.getTablePriv: Found "+ResourceGeneric.BYPASS_RESTRICTED.getName()+"="+o);
            priv |= o.intValue();
        }
        o = newRules.get(ResourceGeneric.CLASS.getName());
        if (o != null) {
            if (DEBUG) System.out.println("Security.getTablePriv: Found "+ResourceGeneric.CLASS.getName()+"="+o);
            priv |= o.intValue();
        }
        o = newRules.get(table.toLowerCase());
        if (o != null) {
            if (DEBUG) System.out.println("Security.getTablePriv: Found database.class."+table.toLowerCase()+"="+o);
            priv |= o.intValue();
        }
        return priv;
    }

    public static HashMap<String,Number> getTablePrivs(String table) {
        if (CACHE_TABLE_PRIVS) {
            HashMap<String,Number> cmap = Security.tablePrivsCache.get(table);
            if (cmap != null) {
                return cmap;
            }
        }
        if (DEBUG) System.out.println("Retrieving privs for table "+table);
        HashMap<String,Number> map = new HashMap<>();
        DatabaseConnection con = Server.getServerConnection();	
        OSecurity osec = con.getSecurity();
        for (ODocument role : osec.getAllRoles()) {
            String roleName = role.field("name");
            ArrayList<Set<ORule>> rules = new ArrayList<>();
            getRoleRules(osec.getRole(role.getIdentity()),rules);
            for (Set<ORule> rs : rules) {
                for (ORule rule : rs) {
                    if (DEBUG) System.out.println("getTablePrivs: rule="+roleName+" "+rule.getSpecificResources()+": "+rule.getAccess());
                    if (rule.containsSpecificResource(table.toLowerCase())) {
                        Byte access = rule.getSpecificResources().get(table.toLowerCase());
                        if (DEBUG) System.out.println("getTablePrivs: specific "+roleName+" "+rule.toString()+": "+access);
                        map.put(roleName, access);
                    } else if (rule.getResourceGeneric() == ResourceGeneric.CLASS) {
                        if (DEBUG) System.out.println("getTablePrivs: all classes: "+roleName+" "+rule.getAccess());
                        if (rule.getAccess() != null) {
                                map.put(roleName, rule.getAccess());
                        }
                    } else if (rule.getResourceGeneric() == ResourceGeneric.BYPASS_RESTRICTED) {
                        if (DEBUG) System.out.println("getTablePrivs: all classes: "+roleName+" "+rule.getAccess());
                        if (rule.getAccess() != null) {
                                map.put(roleName, rule.getAccess());
                        }
                    }
                }
            }
        }		
        Server.freeServerConnection(con);		
        if (CACHE_TABLE_PRIVS) {
            Security.tablePrivsCache.put(table,map);
        }
        return map;
    }

    public static void tablePrivUpdated(String table) {
        if (!CACHE_TABLE_PRIVS) return;
        if (table == null) {
            Security.tablePrivsCache.clear();
        } else {
            Security.tablePrivsCache.remove(table);
        }
    }

    public static boolean changePassword(DatabaseConnection con, String oldPassword, String newPassword) {
        if (!con.isPassword(oldPassword)) {
            System.out.println("Attempt to change password with incorrect old password");
            return false;
        }
        DatabaseConnection c = Server.getServerConnection();
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
            if (c != null) Server.freeServerConnection(c);
        }
    }

    /* See if document is view-only based on the ORestricted fields, 
    Note: this does not check table privileges
    */
    public static boolean isReadOnlyDocument(DatabaseConnection con, ODocument doc) {
        if (Security.isDBA(con)) return false;  // admin, dba not read only
        Set<String> roles = Security.getUserRoles(con);
        if (doc.containsField("_allow")) {
            Set<ODocument> allowed = doc.field("_allow");
            for (ODocument r : allowed) {
                String aname = r.field("name");
                if (r.getClassName().equals("ORole")) {
                    if (roles.contains(aname)) return false; // role on the _allow
                } else {
                    if (con.getUser().equals(aname)) return false;  // on the _allow list
                }
            }            
        }
        if (doc.containsField("_allowUpdate")) {
            Set<ODocument> allowed = doc.field("_allowUpdate");
            for (ODocument r : allowed) {
                String aname = r.field("name");
                if (r.getClassName().equals("ORole")) {
                    if (roles.contains(aname)) return false; // role on the _allowUpdate
                } else {
                    if (con.getUser().equals(aname)) return false;  // on the _allowUpdate
                }
            }            
            return true;
        }
        return false;
    }

}
