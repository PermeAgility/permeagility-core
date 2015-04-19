package permeagility.plus.json;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

public class PlusSetup extends permeagility.plus.PlusSetup {

	public static boolean INSTALLED = false;  // Set via constant to complete installation
	public static String INSTALLED_VERSION = "0";  // Set via constant to complete installation
		
	public static String MENU_CLASS = "permeagility.plus.json.ImportJSON";
	
	public String getName() { return "Import JSON"; }
	public String getInfo() { return "(built-in) Import JSON data into a table"; }
	public String getVersion() { return "0.1.0"; }
	
	public boolean isInstalled() { return INSTALLED; }
	
	@Override   // Override as there are no tables for this plugin
	public String getAddForm(DatabaseConnection con) {
		return "Add to menu"+createListFromTable("MENU", "", con, "menu")
				+"<br>Roles: "+linkSetControl(con, "ROLES", "OIdentity", getCache().getResult(con,getQueryForTable(con, "OIdentity")), con.getLocale(), null);
	}
	
	@Override   // Override as there are no tables for this plugin
	public String getRemoveForm(DatabaseConnection con) {
		return "Remove from menu"+checkbox("REMOVE_MENU",true);
	}

	public boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		if (isNullOrBlank(parms.get("MENU")) || isNullOrBlank(parms.get("ROLES"))) {
			errors.append(paragraph("error","Please specify a menu and the roles to access"));
			return false;
		}

		// No tables
		
		Setup.createMenuItem(con,getName(),getInfo(),MENU_CLASS,parms.get("MENU"),parms.get("ROLES"));	

		setPlusInstalled(con, this.getClass().getName(), getInfo(), getVersion());
		INSTALLED = true;
		return true;
	}
	
	public boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		if (parms.get("REMOVE_MENU") != null) {
			Setup.removeMenuItem(con, MENU_CLASS, errors);
		}
		// No tables

		setPlusUninstalled(con, this.getClass().getName());
		INSTALLED = false;
		return true;
	}
	
	public boolean upgrade(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		// Perform upgrade actions
		
				
		setPlusVersion(con,this.getClass().getName(),getInfo(),getVersion());
		return true;
	}

}
