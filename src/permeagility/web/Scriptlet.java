/*
 * Copyright 2016 PermeAgility Incorporated.
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
import java.util.HashSet;
import java.util.Set;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import permeagility.util.DatabaseConnection;
import static permeagility.web.Security.DEBUG;

/**
 * Run a JavaScript to generate a page, parms and con passed in and an HTML string is expected out
 * @author glenn
 */
public class Scriptlet extends Weblet {

    String serviceStyleOverride = "#service { top: 0px !important; left: 0px !important; right: 0px !important; bottom: 0px !important; }\n";

    // Each thread will have an engine of its own
    private ThreadLocal<ScriptEngine> engineHolder = new ThreadLocal<ScriptEngine>() {
        @Override protected ScriptEngine initialValue() {
            return new ScriptEngineManager().getEngineByName("nashorn");
        }
    };
    
    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
    	StringBuilder sb = new StringBuilder();
        String styleScript = "";
        String logicScript;
        String layoutFlag = parms.get("LAYOUT");  // Default to standard menu/header layout use "none" for no layout (as a component)
        ODocument menuItem = con.get(parms.get("ID"));
        if (menuItem != null && (logicScript = menuItem.field("pageScript")) != null) {
            Set<ODocument> roles = menuItem.field("_allowRead");
            boolean authorized = false;
            if (roles != null) {
                Set<String> uRoles = Security.getUserRoles(con);
                //System.out.println("uRoles="+uRoles);
                for (ODocument r : roles) {
                    if (r != null) {
                        if (uRoles.contains(r.getIdentity().toString())) {
                            authorized = true;
                            //System.out.println("This scriptlet is authorized via the menuItem");
                        }
                    }
                }
            }
            if (authorized) {
                try {
                    styleScript = menuItem.field("pageStyle");
                    engineHolder.get().eval("function getPage(con, parms) {" + logicScript + "\n }");
                    Invocable invocable = (Invocable) engineHolder.get();
                    Object result = invocable.invokeFunction("getPage", con, parms);
                    StringBuilder requireScripts = new StringBuilder();
                    if (parms.get("REQUIRESCRIPT") != null) {
                        String[] req = parms.get("REQUIRESCRIPT").split(",");
                        for (String r : req) {
                            requireScripts.append(getScript(r)); 
                        }
                    }
                    return layoutFlag != null && layoutFlag.equalsIgnoreCase("NONE")   // just the styles and content please
                             ? head(parms.get("SERVICE"), requireScripts + style(serviceStyleOverride + (styleScript.isEmpty() ? "" : styleScript)))
                                + body(div("service",result != null ? result.toString() : "N/A"))
                            : head(parms.get("SERVICE"), requireScripts + (styleScript.isEmpty() ? "" : style(styleScript)))  
                                + body(standardLayout(con, parms, result != null ? result.toString() : "N/A"));   // full page with menu and header
                } catch (Exception e) {
                    System.out.println("Error in scriptlet: " + logicScript + " exception: "+ e.getClass().getName()+" message=" + e.getMessage());
                    sb.append("Error in scriptlet: " + parms.get("ID") + " message=" + e.getMessage());
                } finally {
                }                          
            } else {
                sb.append(paragraph("error","This scriptlet is not authorized for you to run it. allowRead="+roles));
            }
        }
        // null script and Errors land here
        return head(parms.get("SERVICE"))+body(layoutFlag != null && layoutFlag.equalsIgnoreCase("NONE") ? div("service",sb.toString()) : standardLayout(con, parms, sb.toString()));
    }
}
