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
package permeagility.plus.csv;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

public class PlusSetup extends permeagility.plus.PlusSetup {

	public static boolean INSTALLED = false;  // Set via constant to complete installation
	public static String INSTALLED_VERSION = "0";  // Set via constant to complete installation
		
	public static String MENU_CLASS_IMP = "permeagility.plus.csv.ImportCSV";
	public static String MENU_CLASS_EXP = "permeagility.plus.csv.ExportCSV";
	public static String MENU_CLASS_DATA = "permeagility.plus.csv.Download";
	
	@Override public String getName() { return "plus CSV"; }
        @Override public String getInfo() { return "Import/Export CSV data"; }
	
	@Override public boolean isInstalled() { return INSTALLED; }
	
	@Override   // Override as there are no tables for this plugin
	public String getAddForm(DatabaseConnection con) {
		return "Add to menu"+createListFromTable("MENU", "", con, "menu")
				+"<br>Roles: "+linkSetControl(con, "ROLES", "OIdentity", getCache().getResult(con,getQueryForTable(con, "OIdentity")), con.getLocale(), null);
	}
	
	@Override   // Override as there are no tables for this plugin
	public String getRemoveForm(DatabaseConnection con) {
		return "Remove from menu"+checkbox("REMOVE_MENU",true);
	}

	@Override public boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		if (isNullOrBlank(parms.get("MENU")) || isNullOrBlank(parms.get("ROLES"))) {
			errors.append(paragraph("error","Please specify a menu and the roles to access"));
			return false;
		}

		// No tables
		
                // Menu items
		Setup.createMenuItem(con,"ImportCSV","Import CSV into a table",MENU_CLASS_IMP,parms.get("MENU"),parms.get("ROLES"));	
		Setup.createMenuItem(con,"ExportCSV","Export CSV from table or SQL",MENU_CLASS_EXP,parms.get("MENU"),parms.get("ROLES"));	
		Setup.createMenuItem(con,"CSV Download","Download CSV data",MENU_CLASS_DATA,null,parms.get("ROLES"));	// Not on the menu

		setPlusInstalled(con, this.getClass().getName(), getInfo(), getVersion());
		INSTALLED = true;
		return true;
	}
	
	@Override public boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		if (parms.get("REMOVE_MENU") != null) {
			Setup.removeMenuItem(con, MENU_CLASS_IMP, errors);
			Setup.removeMenuItem(con, MENU_CLASS_EXP, errors);
			Setup.removeMenuItem(con, MENU_CLASS_DATA, errors);
		}
		// No tables

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
