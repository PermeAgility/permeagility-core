package permeagility.plus;

import java.util.HashMap;

import com.orientechnologies.orient.core.record.impl.ODocument;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;
import permeagility.web.Weblet;

public abstract class PlusSetup extends Weblet {

    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
    	return head("plus module")+body(paragraph("This is a plus model and must be setup through the context"));
    }

	public abstract String getName();
	public abstract String getInfo();
	public abstract String getVersion();

	public abstract boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors);
	public abstract boolean remove(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors);
	public abstract boolean upgrade(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors);
	
	public abstract boolean isInstalled();

	// Default install form
	public String getAddForm(DatabaseConnection con) {
		return "TableGroup "+createListFromTable("TABLEGROUP", "Plus", con, "tableGroup")
				+" or "+input("NEW_TABLEGROUP","")
				+"<br>Add to menu"+createListFromTable("MENU", "", con, "menu")
				+"<br>Roles: "+linkSetControl(con, "ROLES", "OIdentity", getCache().getResult(con,getQueryForTable(con, "OIdentity")), con.getLocale(), null);
	}
	
	// Default remove form
	public String getRemoveForm(DatabaseConnection con) {
		return "Remove tables "+checkbox("REMOVE_TABLES",true)
				+"<br>Remove from menu"+checkbox("REMOVE_MENU",true);
	}

	// Default upgrade form
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

	public String getInstalledVersion(DatabaseConnection con, String classname) {
		ODocument d = con.queryDocument("SELECT FROM "+Setup.TABLE_CONSTANT+" WHERE classname='"+classname+"' AND field='INSTALLED_VERSION'");
		if (d != null) {
			return d.field("value");
		}
		return null;
	}
	
}
