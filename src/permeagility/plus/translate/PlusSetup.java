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
package permeagility.plus.translate;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

public class PlusSetup extends permeagility.plus.PlusSetup {

	public static boolean INSTALLED = false;  // Set via constant to complete installation
	public static String INSTALLED_VERSION = "0";  // Set via constant to complete installation
		
	public static String MENU_CLASS = "permeagility.plus.translate.Translate";
	
	@Override public String getName() { return "Translate"; }
	@Override public String getInfo() { return "Translate messages, table and column names, and news articles using mymemory.translated.net"; }
	
	@Override public boolean isInstalled() { return INSTALLED; }
	
	@Override   // Override as there are no tables for this plugin
	public String getAddForm(DatabaseConnection con) {
		return "Add to menu"+createListFromTable("MENU_"+getPackage(), "", con, "menu")
			+"<br>Roles: "
			+linkListControl(con, "ROLES_"+getPackage(), "identity", getCache().getResult(con,getQueryForTable(con, "identity")), con.getLocale(), null);
	}
	
	@Override   // Override as there are no tables for this plugin
	public String getRemoveForm(DatabaseConnection con) {
		return "Remove from menu"+checkbox("REMOVE_MENU_"+getPackage(),true);
	}

	@Override public boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		if (isNullOrBlank(parms.get("MENU_"+getPackage())) || isNullOrBlank(parms.get("ROLES_"+getPackage()))) {
			errors.append(paragraph("error","Please specify a menu and the roles to access"));
			return false;
		}

		// No tables
		
		Setup.createMenuItem(con,getName(),getInfo(),MENU_CLASS,parms.get("MENU_"+getPackage()),parms.get("ROLES_"+getPackage()));	

		setPlusInstalled(con, this.getClass().getName(), getInfo(), getVersion());
		INSTALLED = true;
		return true;
	}
	
	@Override public boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		if (parms.get("REMOVE_MENU_"+getPackage()) != null) {
			Setup.removeMenuItem(con, MENU_CLASS, errors);
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
