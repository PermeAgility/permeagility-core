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
import java.util.Date;
import java.util.HashMap;

import org.jsoup.Jsoup;

import com.arcadedb.database.Document;
import com.arcadedb.database.RID;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

public class Home extends Weblet {
	
    public static String DEFAULT_HOME = "welcome";

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
        String serviceName = "Home page: none";
        String id = parms.get("ID");
        String name = parms.get("NAME");
        Document menuItem = null;
        if (id != null) menuItem = con.get(id);
        if (id == null && name == null) {
            name = DEFAULT_HOME;
        }
        if (menuItem == null && name != null) {
            menuItem = con.queryDocument("SELECT FROM menuItem WHERE name='"+name+"'");
        }
        if (menuItem == null) {
            // If no script (probably new) return blank, otherwise, show an error
            return head(con, "Error")
              + body(paragraph("error","No Page to generate: "+menuItem+" maybe the ID or NAME was not specified, try adding ?ID=rid or ?NAME=name"));
        }
        serviceName = menuItem.getString("name");

        StringBuilder styleScript = new StringBuilder();
        adoptStyleFrom(con, styleScript, menuItem);
        String pageStyle = menuItem.getString("pageStyle");
        if (pageStyle != null) styleScript.append(pageStyle);

        String htmlScript = menuItem.getString("pageScript");

        // templating 
        if (htmlScript != null) {
            org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(htmlScript);
            org.jsoup.select.Elements elements = htmlDoc.children().select("PermeAgility");
            if (elements.size() > 0) {  // If PermeAgility elements inside
                for (org.jsoup.nodes.Element ele : elements) {
                    ele.html(runTemplate(con, ele));
                }
                htmlScript = htmlDoc.select("body").html(); 
                // then remove the permeagility tag, no trace we were here
                htmlScript = htmlScript.replace("<permeagility>","");
                htmlScript = htmlScript.replace("</permeagility>","");
            }
        } else {
            htmlScript = "";
        }
        // returning
        return head(con, serviceName, styleScript.toString())
                + body(htmlScript);
    }

    public void adoptStyleFrom(DatabaseConnection con, StringBuilder styleScript, Document menuItem) {
        Object useStyleDoc = menuItem.get("useStyleFrom");
        if (useStyleDoc != null) {
            Document useStyle = null;
            if (useStyleDoc instanceof RID) useStyle = con.get((RID)useStyleDoc);
            if (useStyleDoc instanceof Document) useStyle = (Document)useStyleDoc;
            if (useStyle != null) {
                adoptStyleFrom(con, styleScript, useStyle);  // Adopt ancestors first
                styleScript.append(useStyle.getString("pageStyle")+"\n");
            }
        }
    }

    private String runTemplate(DatabaseConnection con, org.jsoup.nodes.Element ele) {
            String table = ele.attr("table");
            String where = ele.attr("where");
            String order = ele.attr("order");
            String template = ele.html();
            if (where != null) where = scanForTokens(con, where);  // Replace any global tokens used in the where clause
            if (where != null && !where.isEmpty()) where = " WHERE " + where;
            if (order != null && !order.isEmpty()) order = " ORDER BY " + order;
            if (table == null || table.isEmpty()) {
                return paragraph("error","PermeAgility: table attribute not found for template")+template;
            }
            StringBuilder result = new StringBuilder();
            try {
                QueryResult qr = con.query("SELECT FROM " + table + where + order);
                for (Document d : qr.get()) {
                    StringBuilder resultline = new StringBuilder();
                    String[] templines = template.split("\\$\\{");
                    for (String templine : templines) {
                        int bi = templine.indexOf("}");
                        if (bi > 0) {
                            String prop = templine.substring(0,bi);
                            String subprop = null;
                            String repval = "";
                            if (prop.startsWith(table+".")) {
                                prop = prop.substring(table.length()+1);
                                if (prop.equalsIgnoreCase("rid")) {
                                    repval = d.getIdentity().toString().substring(1);
                                } else {
                                    if (prop.contains(".")) {
                                        subprop = prop.substring(prop.indexOf(".")+1);
                                        prop = prop.substring(0,prop.indexOf("."));
                                    }
                                    if (d.has(prop)) {
                                        Object po = d.get(prop);
                                        if (subprop != null && po instanceof RID) {
                                            Document sd = con.get((RID)po);
                                            if (sd.has(subprop)) {
                                                repval = sd.getString(subprop);
                                            } else {
                                                repval = "!{"+table+"."+prop+"."+subprop+"}";
                                            }
                                        } else {
                                            repval = d.getString(prop);
                                        }
                                    } else {
                                        repval = "!{"+table+"."+prop+"}";
                                    }
                                }
                                resultline.append(repval+templine.substring(bi+1));
                            } else {
                                repval = replaceToken(con, prop);
                                resultline.append(repval+templine.substring(bi+1));
                            }
                        } else {
                            resultline.append(templine);
                        }   
                    }
                    result.append(resultline.toString());
                }
                ele.removeAttr("table");
                ele.removeAttr("where");
                ele.removeAttr("order");
                return result.toString();
            } catch (Exception e) {
                return paragraph("error","Error running query: "+e.getClass().getName()+"\n"+e.getMessage());
            }
    }

    String scanForTokens(DatabaseConnection con, String toScan) {
        StringBuilder resultline = new StringBuilder();
        String[] templines = toScan.split("\\$\\{");
        for (String templine : templines) {
            int bi = templine.indexOf("}");
            if (bi > 0) {
                String prop = templine.substring(0,bi);
                String repval = replaceToken(con, prop);
                resultline.append(repval+templine.substring(bi+1));
            } else {
                resultline.append(templine);
            }   
        }
        return resultline.toString();
    }
    String replaceToken(DatabaseConnection con, String token) {
        if (token.equals("locale")) {
            return con.getLocale().toString();
        } else if (token.equals("user")) {
            return con.getUser();
        } else if (token.equals("dbname")) {
            return Server.getDBName();
        } else if (token.equals("timestamp")) {
            return (new Date()).toString();
        }
        System.out.println("!! Error on token replacement for token: "+token);
        return "!{"+token+"}";
    }
}
