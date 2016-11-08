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

import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;


public class Menu extends Weblet {

    public static boolean DEBUG = false;
    public static boolean HORIZONTAL_LAYOUT = false;

    private static ConcurrentHashMap<String, String> menuCache = new ConcurrentHashMap<>();
    private static Locale guestLocale = Locale.getDefault();  // Saves the locale of the guest menu cache

    @Override  // This is generally not used because the getHTML is generally called from standardLayout()
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
        return head("Menu") + body("menu", getHTML(con, parms));
    }

    public String getHTML(DatabaseConnection con, HashMap<String, String> parms) {

        Locale locale = con.getLocale();
        String selector = Message.getLocaleSelector(locale, parms);
        String localeSelector = (selector.length() > 0
                ? (HORIZONTAL_LAYOUT  // this gets a popup form instead of a list
                        ? " <div style=\"float: right;\">" + popupForm("LANGUAGE", null, Message.get(locale, "LANGUAGE"), null, null,
                                paragraph("banner", Message.get(locale, "SELECT_LANGUAGE"))
                                + "<p align=\"left\">" + selector + "</p>\n")
                        + "</div>\n"
                        : "<BR><BR><BR><BR>" + selector)
                : "");

        // Return value from cache if it is there
        String cmenu = menuCache.get(con.getUser());
        if (cmenu != null && cmenu.length() > 0) {
            if (con.getUser().equals("guest") && locale == guestLocale) {  // Guest could have many locales, only the last one is cached
                return cmenu + localeSelector;
            }
        }

        if (DEBUG) System.out.println("Menu: Getting menu for " + con.getUser());
        StringBuilder menu = new StringBuilder();
        
        try { // Assemble menu based on the items the user can see
            QueryResult qr = con.query("SELECT FROM " + Setup.TABLE_MENU + " WHERE active=TRUE ORDER BY sortOrder");
            for (ODocument m : qr.get()) {
                StringBuilder itemMenu = new StringBuilder();
                List<ODocument> items = m.field("items");
                if (items != null) {
                    for (ODocument i : items) {
                        if (i != null) {
                            if (DEBUG) System.out.println("MenuItem=" + i.field("name")+" class="+i.field("classname")+" active="+i.field("active"));
                            String menuName = (String) i.field("name");
                            String menuDesc = (String) i.field("description");
                            String classname = (String) i.field("classname");
                            if (i.field("active")) {
                                String pretty = Message.get(locale, "MENUITEM_" + menuName);
                                if (menuName != null && ("MENUITEM_" + menuName).equals(pretty)) {  // No translation
                                    pretty = menuName;
                                } else if (menuName == null) {
                                    pretty = "";
                                }
                                String prettyDesc = Message.get(locale, "MENUITEMDESC_" + menuName);
                                if (menuDesc != null && ("MENUITEMDESC_" + menuName).equals(prettyDesc)) {  // No translation
                                    prettyDesc = menuDesc;
                                } else if (menuDesc == null) {
                                    prettyDesc = "";
                                }
                                if (DEBUG) System.out.println("MenuItem2=" + pretty+" desc="+prettyDesc+" class="+classname);
                                if (classname == null || Security.authorized(con.getUser(),classname)) {
                                    if (i.field("classname") == null || ((String) i.field("classname")).equals("")) {
                                        itemMenu.append((HORIZONTAL_LAYOUT ? "&nbsp;" : "<br>") + "\n");
                                    } else {
                                        if (i.field("pageScript") != null) {
                                            //System.out.println("Building a pageScript menu item now");
                                            itemMenu.append(link((String) i.field("classname")+"?ID="+i.getIdentity().toString().substring(1), pretty, prettyDesc));
                                        } else {
                                            //System.out.println("Building a pageScript menu item now");
                                            itemMenu.append(link((String) i.field("classname"), pretty, prettyDesc));
                                        }
                                        itemMenu.append((HORIZONTAL_LAYOUT ? "&nbsp;" : "<br>") + "\n");
                                    }                                    
                                    if (DEBUG) System.out.println("MenuItem3.added?=" + pretty+" desc="+prettyDesc+" class="+classname);
                                } else {
                                    if (DEBUG) System.out.println("MenuItem="+pretty+" not authorized");
                                }
                            } else {
                                if (DEBUG) System.out.println("Menu item " + menuName + " is inactive");
                            }
                        } else {
                            // No access to item so it doesn't get added // System.err.println("Menu item is null - was deleted?");
                        }
                    }
                    if (itemMenu.length() > 0) {
                        String menuHeader = (String) m.field("name");
                        String pretty = Message.get(locale, "MENU_" + menuHeader);
                        if (menuHeader != null && ("MENU_" + menuHeader).equals(pretty)) {  // No translation
                            pretty = menuHeader;
                        } else if (menuHeader == null) {
                            pretty = "";
                        }
                        menu.append((HORIZONTAL_LAYOUT ? "&nbsp;&nbsp;&nbsp;" : paragraph("menuheader", pretty)));
                        menu.append(itemMenu);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error in Menu: " + e);
            e.printStackTrace();
        }
        if (DEBUG) {
            System.out.println("Menu: Adding menu for " + con.getUser() + " to menuCache");
        }
        String newMenu = menu.toString();
        menuCache.put(con.getUser(), newMenu);
        if (con.getUser().equals("guest")) {
            guestLocale = locale;
        }
        return newMenu + localeSelector;
    }

    // Override links to use menuitem class
    public static String link(String ref, String name, String desc) {
        return "<a class=\"menuitem\" href=\"" + ref + "\" title=\"" + desc + "\">" + name + "</a>\n";
    }

    public static String linkNewWindow(String ref, String name, String desc) {
        return "<a class=\"menuitem\" href=\"" + ref + "\" target=\"_blank\" title=\"" + desc + "\">" + name + "</a>\n";
    }

    public static void clearMenu(String user) {
        menuCache.remove(user);
    }

    public static void clearCache() {
        menuCache.clear();
    }

    public static int cacheSize() {
        return menuCache.size();
    }

}
