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

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.arcadedb.database.Document;
import com.arcadedb.database.RID;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

public class Security {

    public static boolean DEBUG = false;
    public static Date securityRefreshTime = new Date();
    
    public static final int PRIV_CREATE = 1;
    public static final int PRIV_READ = 2;
    public static final int PRIV_UPDATE = 4;
    public static final int PRIV_DELETE = 8;
    public static final int PRIV_ALL = 15;
    public static final int PRIV_SUPER = 16;

    private static final ConcurrentHashMap<String,List<RID>> userRoles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,HashMap<String,Integer>> userRules = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,List<RID>> keyRoles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,HashMap<String,Integer>> tablePrivsCache = new ConcurrentHashMap<>();
    public static boolean CACHE_TABLE_PRIVS = true;
    
    public static boolean authorized(String user, String className) {
        if (DEBUG) System.out.println("Authorizing "+user+" to "+className);
        List<RID> uRoles = userRoles.get(user);
        List<RID> kRoles = keyRoles.get(className);
        return isRoleMatch(uRoles,kRoles);  // unauthorized if any list null
    }

    public static int keyRoleCount() { 
        return keyRoles.size();
    }

    /** Refresh the cached security model in the server */
    public static void refreshSecurity(DatabaseConnection con) {

        System.out.println("Security: refreshing using "+con.getUser()+" user");
        int entryCount = 0;
        try {
            List<Document> users = con.query("SELECT FROM user").get();
            if (users != null) {
                for (Document user : users) {
                    String n = user.getString("name");
                    List<RID> roles = user.getList("roles");
                    if (roles != null) {
                        if (DEBUG) System.out.println("Adding security keyuser "+n+" - "+roles.size());
                        entryCount++;
                        List<RID> roleSet = new ArrayList<>();
                        for (RID r : roles) {
                            if (r != null) {
                                roleSet.add(r);
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
                for (Document menuItem : qr.get()) {
                    String n = menuItem.getString("classname");
                    List<RID> roles = menuItem.getList("_allowRead");
                    if (n != null && roles != null) {
                        if (DEBUG) System.out.print("Adding security keyrole "+n+" - ");
                        entryCount++;
                        List<RID> roleSet = keyRoles.get(n);
                        if (roleSet == null) {
                            roleSet = new ArrayList<>();
                            keyRoles.put(n, roleSet);
                        }
                        for (RID r : roles) {
                            if (r != null) {
                                if (DEBUG) System.out.print(r+" ");
                                roleSet.add(r);
                            }
                        }
                    }
                    if (DEBUG) System.out.println("done.");
                }
                System.out.println("Security.refreshSecurity() - loaded "+entryCount+" menuItems");
            }

            for (String user : userRoles.keySet()) {
               // User object rules are compiled into a simple HashMap<String,Byte>
                List<RID> roles = userRoles.get(user);  
                ArrayList<List<Document>> rules = new ArrayList<>();  // To hold the rules
                for (RID role : roles) {
                    getRoleRules(con, role, rules);				
                }
                if (DEBUG) System.out.println(user+" rules="+rules);
                // Collapse the rules into a single HashMap 
                HashMap<String,Integer> newRules = new HashMap<>();
                for (List<Document> m : rules) {
                    for (Document rule : m) {
                       if (DEBUG) System.out.println("Security.refreshSecurity found rule "+rule);
                       Integer privN = newRules.get(rule.getString("resource"));
                       int priv = 0;
                       if (privN != null) priv = privN.intValue();
                       String access = rule.getString("access");
                       if (access.equals("ALL")) {
                            priv |= PRIV_ALL;
                       } else if (access.equals("CREATE")) {
                            priv |= PRIV_CREATE;
                       } else if (access.equals("READ")) {
                            priv |= PRIV_READ;
                       } else if (access.equals("UPDATE")) {
                            priv |= PRIV_UPDATE;
                       } else if (access.equals("DELETE")) {
                            priv |= PRIV_DELETE;
                       } else if (access.equals("READONLY")) { // makes sense in SUPER mode
                            priv |= PRIV_CREATE | PRIV_UPDATE | PRIV_DELETE;
                            if (DEBUG) System.out.println("Readonly priv is "+priv);
                       }
                       newRules.put(rule.getString("resource"),priv);
                    }
                }
                if (DEBUG) System.out.println(user+" newRules="+newRules);
                userRules.put(user, newRules);
            }
        } catch (Exception e) {
            System.out.println("Error retrieving security model into cache - "+e.getMessage());
            e.printStackTrace();
        }
        securityRefreshTime = new Date();
    }

    /** Returns true is one of the first set of roles is a match for a role in the second set - please pass in arrays of ORoles */
    public static boolean isRoleMatch(List<RID> uRoles, List<RID> kRoles) {
        boolean authorized = false;
        if (uRoles == null) {
            System.out.println("Server.isRoleAuthorized: uRoles is null");
            return authorized;
        }
        if (kRoles == null) {
            System.out.println("Server.isRoleAuthorized: kRoles is null");
            return authorized;
        }
        if (DEBUG) System.out.println("Server.isRoleAuthorized: uRoles="+uRoles.size()+" kRoles="+kRoles.size());
        OUT: for (RID ur: uRoles) {
            for (RID kr: kRoles) {
                if (DEBUG) System.out.println("Server.isRoleAuthorized: comparing "+ur+" with "+kr);
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
    public static int getRoleRules(DatabaseConnection con, RID role, ArrayList<List<Document>> rules) {
        Document roleDoc = con.get(role);
        if (roleDoc.get("inheritedRole") != null) {
            if (DEBUG) System.out.println("found inherited role for "+roleDoc.getString("name"));
            getRoleRules(con, (RID)roleDoc.get("inheritedRole"), rules);
        } 
        List<Document> privs = con.query("SELECT FROM privilege WHERE identity="+role.toString()).get();
        List<Document> ru = new ArrayList<>();
        for (Document p : privs) {
            if (DEBUG) System.out.println("Security.getRoleRules added " + p.toString());
            ru.add(p);
        }
        rules.add(ru);
        return rules.size();
    }

    /** Get the connected user's roles in an object array  */ 
    public static List<RID> getUserRoles(DatabaseConnection con) {
        return userRoles.get(con.getUser());  
    }

    /** Get the connected user's roles as a list that can be used in SQL. Example: #1:1,#1:2,#1:3 */
    public static String getUserRolesList(DatabaseConnection con) {
        List<RID> roles = userRoles.get(con.getUser());
        if (roles != null) {
            StringBuilder rb = new StringBuilder();
            String comma = "";
            for(RID r : roles) {
                rb.append(comma);
                rb.append(r.toString());
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
        boolean superuser = false;
        int priv = 0;
        String user = con.getUser();
        List<RID> roles = userRoles.get(user);
        for (RID r : roles) {
            Document role = con.get(r);
            if (role != null) {
                if (role.getString("mode").equals("SUPER")) {
                    if (DEBUG) System.out.println("We have a SUPER user called "+user);
                    superuser = true;
                    priv = Security.PRIV_ALL;
                }
            }
        }
        HashMap<String,Integer> newRules = userRules.get(user);
        if (DEBUG) {
            for (String n : newRules.keySet()) {
                System.out.println("Security.getTablePriv: rule="+n+" access="+newRules.get(n));
            }
        }
        // Find the most specific privilege for the table from the user's rules
        Integer o = newRules.get(table);
        if (o != null) {
            if (DEBUG) System.out.println("Security.getTablePriv: Found database.class."+table+"="+o);
            if (superuser) {
                priv ^= o.intValue();  // XOR the priv for SUPER user
            } else {
                priv |= o.intValue();  // OR the priv for NORMAL user
            }
        }
        return priv;
    }

    public static HashMap<String,Integer> getTablePrivs(DatabaseConnection con, String table) {
        if (CACHE_TABLE_PRIVS) {
            HashMap<String,Integer> cmap = Security.tablePrivsCache.get(table);
            if (cmap != null) {
                return cmap;
            }
        }
        if (DEBUG) System.out.println("Retrieving privs for table "+table);
        HashMap<String,Integer> map = new HashMap<>();
        for (Document role : con.query("SELECT FROM role").get()) {
            String roleName = role.getString("name");
            String roleMode = role.getString("mode");
            ArrayList<List<Document>> rules = new ArrayList<>();
            getRoleRules(con, role.getIdentity(), rules);
            for (List<Document> rs : rules) {
                int priv = 0;
                for (Document rule : rs) {
                    if (DEBUG) System.out.println("getTablePrivs: rule="+roleName+" "+rule.getString("resource")+": "+rule.getString("access"));
                    if (rule.getString("resource").equals(table)) {
                        Integer privN = map.get(rule.getString(table));
                        if (privN != null) priv = privN.intValue();
                        String access = rule.getString("access");
                        if (access.equals("ALL")) {
                             priv |= PRIV_ALL;
                        } else if (access.equals("CREATE")) {
                             priv |= PRIV_CREATE;
                        } else if (access.equals("READ")) {
                             priv |= PRIV_READ;
                        } else if (access.equals("UPDATE")) {
                             priv |= PRIV_UPDATE;
                        } else if (access.equals("DELETE")) {
                             priv |= PRIV_DELETE;
                        } else if (access.equals("READONLY")) { // makes sense in SUPER mode
                             priv |= PRIV_CREATE | PRIV_UPDATE | PRIV_DELETE;
                             if (DEBUG) System.out.println("Readonly priv is "+priv);
                        }
                        if (roleMode.equals("SUPER")) priv |= PRIV_SUPER;
                        if (DEBUG) System.out.println("getTablePrivs: specific "+roleName+" "+rule.toString()+": "+priv);
                    }
                }
                map.put(roleName, priv);
            }
        }		
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
        DatabaseConnection c = Server.database.getConnection();
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
        }
    }

    /* See if document is view-only based on the restricted fields, 
    Note: this does not check table privileges
    */
    public static boolean isReadOnlyDocument(DatabaseConnection con, Document doc) {
        if (Security.isDBA(con)) return false;  // admin, dba not read only
        List<RID> roles = Security.getUserRoles(con);
        if (doc.has("_allow")) {
            List<RID> allowed = doc.getList("_allow");
            for (RID r : allowed) {
                if (roles.contains(r)) return false; // role on the _allow
            }            
        }
        if (doc.has("_allowUpdate")) {
            List<RID> allowed = doc.getList("_allowUpdate");
            for (RID r : allowed) {
                if (roles.contains(r)) return false; // role on the _allowUpdate
            }            
            return true;
        }
        return false;
    }

    private static byte[] salt = HexFormat.ofDelimiter(":").parseHex("0c:70:66:ee:29:2c:dd:39:6b:a3:ed:df:a3:18:0a:8f");
    private static MessageDigest messageDigest = null;
    
    public static String digest(String string) {

        // Todo: use salt from file, generate if not found
        //SecureRandom random = new SecureRandom();
        //byte[] salt = new byte[16];
        //random.nextBytes(salt);

        if (messageDigest == null) {
            try {
                if (DEBUG) System.out.println("Salt:"+HexFormat.ofDelimiter(":").formatHex(salt));
                messageDigest = MessageDigest.getInstance("SHA-512");
                messageDigest.update(salt);
                System.out.println("Digest alg="+messageDigest.getAlgorithm()+" length="+messageDigest.getDigestLength()
                    +" prov="+messageDigest.getProvider().getName()+" provinfo="+messageDigest.getProvider().getInfo());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (messageDigest != null) {
            byte[] hashedPassword = messageDigest.digest(string.getBytes());
            if (DEBUG) System.out.println("Salted password="+HexFormat.ofDelimiter(":").formatHex(hashedPassword));
            return HexFormat.ofDelimiter(":").formatHex(hashedPassword);
        } else {
            System.out.println("Security.digest panic: no messageDigest available");
            return string;
        }
    }

}
