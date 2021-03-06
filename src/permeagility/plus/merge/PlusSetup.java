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
package permeagility.plus.merge;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;

public class PlusSetup extends permeagility.plus.PlusSetup {

	public static boolean INSTALLED = false;  // Set via constant to complete installation
	public static String INSTALLED_VERSION = "0";  // Set via constant to complete installation
	
	// Override these to change the names of the tables that will be created and used by this module
	public static String MERGE_TABLE = "mergePath";   // Table name to hold merge source/dest
	public static String ATTR_TABLE = "mergePathColumn";   // Defines property mapping for a merge
	
	public static String MENU_CLASS = "permeagility.plus.merge.Merge";
	
	@Override public String getName() { return "Merge"; }
	@Override public String getInfo() { return "Move, link, or update data from one table to another"; }
	
	@Override public boolean isInstalled() { return INSTALLED; }
	
	@Override public boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		OSchema oschema = con.getSchema();
		String newTableGroup = pickTableGroup(con, parms);
		
		if (isNullOrBlank(newTableGroup) || isNullOrBlank(parms.get("MENU")) || isNullOrBlank(parms.get("ROLES"))) {
			errors.append(paragraph("error","Please specify a table group, menu and the roles to access"));
			return false;
		}
		
		OClass table = Setup.checkCreateTable(con, oschema, MERGE_TABLE, errors, newTableGroup);
		Setup.checkCreateColumn(con, table, "name", OType.STRING, errors);
		Setup.checkCreateColumn(con, table, "fromTable", OType.STRING, errors);
		Setup.checkCreateColumn(con, table, "fromKey", OType.STRING, errors);
		Setup.checkCreateColumn(con, table, "toTable", OType.STRING, errors);
		Setup.checkCreateColumn(con, table, "toKey", OType.STRING, errors);
		Setup.checkCreateColumn(con, table, "created", OType.DATETIME, errors);
		Setup.checkCreateColumn(con, table, "executed", OType.DATETIME, errors);
		
		OClass logTable = Setup.checkCreateTable(con, oschema, ATTR_TABLE, errors, newTableGroup);
		Setup.checkCreateColumn(con, logTable, "path", OType.LINK, table, errors);
		Setup.checkCreateColumn(con, logTable, "fromColumn", OType.STRING, errors);
		Setup.checkCreateColumn(con, logTable, "toColumn", OType.STRING, errors);
		Setup.checkCreateColumn(con, logTable, "linkProperty", OType.STRING, errors);

		Setup.createMenuItem(con,getName(),getInfo(),MENU_CLASS,parms.get("MENU"),parms.get("ROLES"));	
		
		setPlusInstalled(con, this.getClass().getName(), getInfo(), getVersion());
		INSTALLED = true;
		return true;
	}
	
	@Override public boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		
		if (parms.get("REMOVE_MENU") != null) {
			Setup.removeMenuItem(con, MENU_CLASS, errors);
		}
		
		String remTab = parms.get("REMOVE_TABLES");
		if (remTab != null && remTab.equals("on")) {
			Setup.dropTable(con, MERGE_TABLE);
			errors.append(paragraph("success","Table dropped: "+MERGE_TABLE));
			Setup.dropTable(con, ATTR_TABLE);
			errors.append(paragraph("success","Table dropped: "+ATTR_TABLE));
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
