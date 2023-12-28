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
package permeagility.plus.r;

import java.util.HashMap;

import com.arcadedb.database.Document;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

import permeagility.web.Message;
import permeagility.web.Server;
import permeagility.web.Table;

public class PlusSetup extends permeagility.plus.PlusSetup {

	public static boolean INSTALLED = false;  // Set via constant to complete installation
	public static String INSTALLED_VERSION = "0";  // Set via constant to complete installation
	
	// Override these to change the names of the tables that will be created and used by this importer
	public static String TABLE = "RScript";   // Local table name to hold connection specs
	
	public static String MENU_CLASS = "permeagility.plus.r.RBuilder";
	public static String DATA_CLASS = "permeagility.plus.r.Data";
	
	@Override public String getName() { return "R Builder"; }
	@Override public String getInfo() { return "Calculate, twist and plot data using R (requires R install on server)"; }
	
	@Override public boolean isInstalled() { return INSTALLED; }
	
	@Override public boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		Schema oschema = con.getSchema();
		String newTableGroup = pickTableGroup(con, parms);
                String roles = parms.get("ROLES");
				
		if (isNullOrBlank(newTableGroup) || isNullOrBlank(parms.get("MENU")) || isNullOrBlank(roles)) {
			errors.append(paragraph("error",Message.get(con.getLocale(), "PLUS_PARMS_INVALID")));
			return false;
		}
                Document loc = con.queryDocument("SELECT FROM locale WHERE name='en'");

		DocumentType table = Setup.checkCreateTable(con, oschema, TABLE, errors, newTableGroup);
                Setup.checkTableSuperclass(oschema, table, "ORestricted", errors);
		Setup.checkCreateColumn(con,table, "name", Type.STRING, errors);
		Setup.checkCreateColumn(con,table, "description", Type.STRING, errors);
		Setup.checkCreateColumn(con,table, "RScript", Type.STRING, errors);
		Setup.checkCreateColumn(con,table, "status", Type.STRING, errors);
		Setup.checkCreateColumn(con,table, "textResult", Type.STRING, errors);
	//	Setup.checkCreateColumn(con,table, "PDFResult", Type.CUSTOM, errors);
		
		Setup.createMenuItem(con,getName(),getInfo(),MENU_CLASS,parms.get("MENU"),roles);	
		Setup.createMenuItem(con,getName(),getInfo(),DATA_CLASS,null,roles);	
		
                // Add table privs for each role
                String privRoles[] = roles.split(",");
                for (String role : privRoles) {
                    String roleName = con.get(role).getString("name");
                    if (roleName != null) {
          //              Setup.checkCreatePrivilege(con, roleName, ORule.ResourceGeneric.CLASS, TABLE, Table.PRIV_ALL, errors);
          //              Setup.checkCreatePrivilege(con, roleName, ORule.ResourceGeneric.CLUSTER, TABLE, Table.PRIV_ALL, errors);
                    }
                }
                Server.tableUpdated(con, "message");
		setPlusInstalled(con, this.getClass().getName(), getInfo(), getVersion());
		INSTALLED = true;
		return true;
	}
	
	@Override public boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {

		if (parms.get("REMOVE_MENU") != null) {
			Setup.removeMenuItem(con, MENU_CLASS, errors);
			Setup.removeMenuItem(con, DATA_CLASS, errors);
		}
		
		String remTab = parms.get("REMOVE_TABLES");
		if (remTab != null && remTab.equals("on")) {
			Setup.dropTable(con, TABLE, errors);  // Need to drop all privs too?
		}

		setPlusUninstalled(con, this.getClass().getName());
		INSTALLED = false;
		return true;
	}
	
	@Override public boolean upgrade(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		// Perform upgrade actions
				
		setPlusVersion(con,this.getClass().getName(),getInfo(),getVersion());
		return true;
	}

}
