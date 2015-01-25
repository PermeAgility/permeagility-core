package permeagility.plus;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.record.impl.ODocument;

public abstract class PlusSetup extends Weblet {

    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
    	return head("plus module")+body(paragraph("This is a plus model and must be setup through the context"));
    }

    /** Implement this to return the plus module name */
	public abstract String getName();
	
	/** Implement this to return the module description */
	public abstract String getInfo();
	
	/** Implement this to return the version (should be ascending alpha numeric) */
	public abstract String getVersion();

	/** Implement this with what the plug in needs to be installed */
	public abstract boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors);

	/** Implement this with what the plug in needs to be removed */
	public abstract boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors);

	/** Implement this with what the plug in needs when upgraded */
	public abstract boolean upgrade(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors);
	
	/** Implement this to return true if the plus module is installed */
	public abstract boolean isInstalled();

	/** Default install form (TABLEGROUP,MENU,ROLES) override to add fields (be sure to include default fields if needed) */
	public String getAddForm(DatabaseConnection con) {
		return "TableGroup "+createListFromTable("TABLEGROUP", "Plus", con, "tableGroup")
				+" or "+input("NEW_TABLEGROUP","")
				+"<br>Add to menu"+createListFromTable("MENU", "", con, "menu")
				+"<br>Roles: "+linkSetControl(con, "ROLES", "OIdentity", getCache().getResult(con,getQueryForTable(con, "OIdentity")), con.getLocale(), null);
	}
	
	/** Default remove form (REMOVE_TABLES,REMOVE_MENU) override to add fields (be sure to include default fields if needed) */
	public String getRemoveForm(DatabaseConnection con) {
		return "Remove tables "+checkbox("REMOVE_TABLES",true)
				+"<br>Remove from menu"+checkbox("REMOVE_MENU",true);
	}

	/** Default upgrade form - no fields (override to add your own as appropriate) */
	public String getUpgradeForm(DatabaseConnection con) {
		return "";
	}

	/** Pick the tablegroup to use from the parms either TABLEGROUP=rid or NEW_TABLEGROUP=name */
	public String pickTableGroup(DatabaseConnection con, HashMap<String, String> parms) {
		String newTableGroup = parms.get("NEW_TABLEGROUP");
		if (newTableGroup == null || newTableGroup.equals("")) {
			String tableGroup = parms.get("TABLEGROUP");
			if (tableGroup == null || tableGroup.equals("")) {
				return null;
			} else {
				try {
					ODocument tg = con.get(tableGroup);
					if (tg != null) {
						newTableGroup = tg.field("name");
					}
				} catch (Exception e) {
					return null;
				}
			}
		}
		return newTableGroup;
	}

	/** Get the INSTALLED_VERSION constant for the plus module given */
	public String getInstalledVersion(DatabaseConnection con, String classname) {
		ODocument d = con.queryDocument("SELECT FROM "+Setup.TABLE_CONSTANT+" WHERE classname='"+classname+"' AND field='INSTALLED_VERSION'");
		if (d != null) {
			return d.field("value");
		}
		return null;
	}
	
	/** Set the INSTALLED and INSTALLED_VERSION constant  */
	public static void setPlusInstalled(DatabaseConnection con, String classname, String info, String version) {
		Setup.checkCreateConstant(con,classname,info,"INSTALLED","true");
		Setup.checkCreateConstant(con,classname,info,"INSTALLED_VERSION",version);
	}

	/** Remove the INSTALLED and INSTALLED_VERSION constant  */
	public static void setPlusUninstalled(DatabaseConnection con, String classname) {
		con.update("DELETE FROM "+Setup.TABLE_CONSTANT+" WHERE classname='"+classname+"'");		
	}
	
	/** Update the INSTALLED_VERSION constant  */
	public static void setPlusVersion(DatabaseConnection con, String classname, String info, String version) {
		Setup.checkCreateConstant(con,classname,info,"INSTALLED_VERSION",version);	
	}
	
}
