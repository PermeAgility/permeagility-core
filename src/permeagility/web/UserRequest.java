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
import java.util.Locale;
import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

public class UserRequest extends Table {
    
    // If not null, user request will automatically create user with the specified role
    public static String ACCEPT_TO_ROLE = null;

     public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
	return head(Message.get(con.getLocale(),"REQUEST_LOGIN"),getDateControlScript(con.getLocale())+getColorControlScript())+
	    body(getHTML(con,parms));
    }
        
    public String getHTML(DatabaseConnection con, java.util.HashMap<String,String> parms) {
    	StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();
    	if (parms.get("SUBMIT") != null) {
            DatabaseConnection serverCon = Server.getServerConnection();
            if (serverCon != null) {
                try {
                    String name = parms.get(PARM_PREFIX+"name");
                    String pass = parms.get(PARM_PREFIX+"password");
                    if (name == null || name.isEmpty() || pass == null || pass.isEmpty()) {
                        errors.append(paragraph("error", Message.get(locale,"USERREQUEST_NEED_NAMEPASS")));
                    } else if (serverCon.queryDocument("SELECT FROM userRequest WHERE name='"+name+"'") != null
                            || serverCon.queryDocument("SELECT FROM OUser WHERE name='"+name+"'") != null) {
                        errors.append(paragraph("error", Message.get(locale,"USERREQUEST_EXISTS")));
                    } else {
                        if (insertRow(con, Setup.TABLE_USERREQUEST, parms, errors)) {
                            if (ACCEPT_TO_ROLE != null && !ACCEPT_TO_ROLE.isEmpty()) {
                                System.out.println("Automatically creating the user "+name+" with ACCEPT_TO_ROLE="+ACCEPT_TO_ROLE);
                                ODocument roleDoc = serverCon.queryDocument("SELECT FROM ORole WHERE name='"+ACCEPT_TO_ROLE+"'");
                                if (roleDoc == null) {
                                    System.out.println("ERROR in UserRequest: ORole "+ACCEPT_TO_ROLE+" not found");
                                    errors.append("Could not find role "+ACCEPT_TO_ROLE);
                                } else {
                                    //System.out.println("Role "+ACCEPT_TO_ROLE+" found, adding user "+name);
                                    // name, password should already be in the request so leave it alone
                                    parms.put(PARM_PREFIX+"status", "ACTIVE");
                                    parms.put(PARM_PREFIX+"roles", roleDoc.getIdentity().toString().substring(1));
                                    if (insertRow(serverCon, "OUser", parms, errors)) {
                                        System.out.println("New user "+parms.get(PARM_PREFIX+"name")+" created");
                                        return paragraph("success",Message.get(locale, "USERREQUEST_CREATED"))+link("/",Message.get(locale, "HEADER_LOGO_DESC"));
                                    }
                                }
                            }
                        } else {
                            return paragraph("success",Message.get(locale, "USERREQUEST_INSERTED"))+link("/",Message.get(locale, "HEADER_LOGO_DESC"));
                        }
                    }
                } catch (Exception e) {
                    errors.append(paragraph("error",Message.get(locale,"USERREQUEST_ERROR")+e.getMessage()));
                    e.printStackTrace();
                } finally {
                    Server.freeServerConnection(serverCon);
                }
            }
    	}
    	return paragraph("banner",Message.get(locale, "REQUEST_LOGIN"))
               +errors
               +form(
                    getTableRowFields(con, Setup.TABLE_USERREQUEST, parms)
                   +center(submitButton(locale, "CREATE_ROW"))
               );
    }
                
}
