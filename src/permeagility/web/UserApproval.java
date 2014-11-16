/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.StringTokenizer;
import java.util.Vector;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;


public class UserApproval extends Table {
	
    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
    	String title = Message.get(con.getLocale(), "ACCOUNT_APPROVAL", parms.get("SCHEMANAME"), parms.get("TABLENAME"));
		parms.put("SERVICE", title);

		StringBuffer messages = new StringBuffer();
		String editId = parms.get("EDIT_ID");
    	if (editId != null) {
    		if (parms.get("ROLES") == null) {
			    QueryResult roles = con.query("SELECT ID,NAME,DESCRIPTION FROM CONFIGURATION.ROLE_DEFINE");
			    Vector<String> names = new Vector<String>(roles.size());
			    Vector<String> values = new Vector<String>(roles.size());
			    Vector<String> tooltips = new Vector<String>(roles.size());
			    for(int i=0;i< roles.size();i++) {
			    	names.add(roles.getStringValue(i, "NAME"));
			    	values.add(roles.getStringValue(i, "ID"));
			    	tooltips.add(roles.getStringValue(i, "DESCRIPTION"));
			    }
			    getTableRowParameters(con,"CONFIGURATION","USER_REQUEST",parms);
			    // We will create a new record in another table so we need to remove the edit id
			    parms.remove("EDIT_ID"); // so that getTableRowFields will think this is a new record
			    return head("Approve Account", getDateControlScript())
					+ body(standardLayout(con, parms, 
						messages
						+formStart("APPROVE","permeagility.web.UserApproval")
						+paragraph("banner",Message.get(con.getLocale(),"CREATE_USER"))
						+hidden("EDIT_ID",editId)
						+hidden("LAST_MOD_TIME",parms.get("LAST_MOD_TIME"))
						+hidden("LAST_MOD_USER",parms.get("LAST_MOD_USER"))
						+getTableRowFields(con,"USER_DEFINE",parms)
						+paragraph("banner",Message.get(con.getLocale(),"ATTACH_ROLES"))
						+tableStart(0)
					    	+row(columnTopRight(50,Message.get(con.getLocale(),"ROLES"))
					    	+column(50,multiCheckboxList("ROLES",names,values,tooltips,con.getLocale())))
					    	+row(columnRight(50,submitButton("SUBMIT",Message.get(con.getLocale(),"APPROVE_ACCOUNT")))
					    	+column(50,resetButton("RESET",Message.get(con.getLocale(),"CLEAR"))))
					    +tableEnd()
					    +formEnd()
					 ));    			
    		} else {
    			// Approve the user and assign its roles
    			String submit = parms.get("SUBMIT");
    			if (submit != null && submit.equals(Message.get(con.getLocale(),"APPROVE_ACCOUNT"))) {
        			System.out.println("Approving the user");
        			parms.put("UPDATE_ID", parms.get("EDIT_ID")); // Copy EDIT_ID to UPDATE_ID to delete USER_REQUEST
        			deleteRow(con,"user",parms,messages);
        			insertRow(con,"user",parms,messages);
    				System.out.println("Attaching Roles = "+parms.get("ROLES"));
    				StringTokenizer st = new StringTokenizer(parms.get("ROLES"),",");
    				int roleCount = st.countTokens();
    				for (int i=0;i<roleCount;i++) {
    					String roleId = st.nextToken();
    					try {
    						con.update("INSERT INTO CONFIGURATION.USER_ROLE_REFER (USER_ID, ROLE_ID, LAST_MOD_TIME, LAST_MOD_USER) VALUES ("
    							+"(SELECT ID FROM CONFIGURATION.USER_DEFINE WHERE NAME="+wrapWithQuotes(parms.get("NAME"))+")"
    							+","+roleId+",CURRENT_TIMESTAMP(), CURRENT_USER() )");
    					} catch (Exception e) {
    						messages.append(paragraph("error","Unable to attach role "+roleId+" - "+e.getLocalizedMessage()));
    					}
    				}
    			}
    		}
    	}
    	return head("Account Approval", getDateControlScript())
		+ body(standardLayout(con, parms, 
				messages+getTable(con, "userRequest")));
    }
}
