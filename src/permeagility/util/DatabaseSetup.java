package permeagility.util;

import java.util.ArrayList;
import java.util.Date;

import permeagility.web.Message;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class DatabaseSetup {

	static StringBuffer installMessages = new StringBuffer();
	
	public static String TABLE_THUMBNAIL = "thumbnail";
	public static String TABLE_CONSTANT = "constant";
	public static String TABLE_LOCALE = "locale";
	public static String TABLE_MESSAGE = "message";
	public static String TABLE_STYLE = "style";
	public static String TABLE_PICKLIST = "pickList";
	public static String TABLE_TABLEGROUP = "tableGroup";
	public static String TABLE_COLUMNS = "columns";
	public static String TABLE_MENU = "menu";
	public static String TABLE_KEY = "key";
	public static String TABLE_NEWS = "article";
	public static String TABLE_USERREQUEST = "userRequest";
	
	/**
	 *  Verify the installation of the permeagility-core table structures
	 *  returns true if all tables and data are sufficient for framework startup
	 */
	public static boolean checkInstallation(DatabaseConnection con) {
		try {
			OSchema oschema = con.getSchema();
			
			System.out.print("DatabaseSetup.checkInstallation ");

			ArrayList<ODocument> allRoles = new ArrayList<ODocument>();			
			ArrayList<ODocument> adminRoles = new ArrayList<ODocument>();			
			ArrayList<ODocument> guestRoles = new ArrayList<ODocument>();			
			QueryResult qr = con.query("SELECT FROM ORole");
			for (ODocument role : qr.get()) {
				allRoles.add(role);
				if (role.field("name").equals("admin")) adminRoles.add(role);
				if (role.field("name").equals("guest")) guestRoles.add(role);
			}
						
			ODocument adminUser = con.queryDocument("SELECT FROM OUser WHERE name='admin'");
			ODocument guestUser = con.queryDocument("SELECT FROM OUser WHERE name='guest'");

			if (guestRoles.size() == 0) {
				ODocument guestRole = (ODocument)con.update("insert into ORole set name = 'guest', mode = 0"
						+", rules = {\"database.schema\":2, \"database\":2, \"database.command\":3"
						+", \"database.class.userrequest\":1, \"database.cluster.userrequest\":1"
						+", \"database.class.article\":2, \"database.cluster.article\":2"
						+", \"database.cluster.*\":2"
						+ ", \"database.class.style\":2,\"database.cluster.style\":2 }");
				guestRoles.add(guestRole);
				allRoles.add(guestRole);
				installMessages.append(Weblet.paragraph("success","CheckInstallation: Created guest role"));
			}
			if (guestUser == null) {
				guestUser = (ODocument)con.update("insert into OUser set name = 'guest', password = 'guest', status = 'ACTIVE', roles = (select from ORole where name = 'guest')");
				installMessages.append(Weblet.paragraph("success","CheckInstallation: Created guest user"));
			}
			
			System.out.print(TABLE_THUMBNAIL+" ");
			OClass thumbnailTable = Database.checkCreateClass(oschema, TABLE_THUMBNAIL, installMessages);
			Database.checkCreateProperty(thumbnailTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(thumbnailTable, "table", OType.STRING, installMessages);
			Database.checkCreateProperty(thumbnailTable, "column", OType.STRING, installMessages);
			Database.checkCreateProperty(thumbnailTable, "id", OType.STRING, installMessages);
			Database.checkCreateProperty(thumbnailTable, "type", OType.STRING, installMessages);
			Database.checkCreateProperty(thumbnailTable, "size", OType.INTEGER, installMessages);
			Database.checkCreateProperty(thumbnailTable, "width", OType.INTEGER, installMessages);
			Database.checkCreateProperty(thumbnailTable, "height", OType.INTEGER, installMessages);
			Database.checkCreateProperty(thumbnailTable, "small", OType.CUSTOM, installMessages);
			Database.checkCreateProperty(thumbnailTable, "medium", OType.CUSTOM, installMessages);

			System.out.print(TABLE_CONSTANT+" ");
			OClass constantTable = Database.checkCreateClass(oschema, TABLE_CONSTANT, installMessages);
			Database.checkCreateProperty(constantTable, "classname", OType.STRING, installMessages);
			Database.checkCreateProperty(constantTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(constantTable, "field", OType.STRING, installMessages);
			Database.checkCreateProperty(constantTable, "value", OType.STRING, installMessages);
			
			if (constantTable.count() == 0) {
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Server").field("description","Server debug flag").field("field","DEBUG").field("value","false").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Table debug flag").field("field","DEBUG").field("value","false").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Table page count").field("field","ROW_COUNT_LIMIT").field("value","200").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Context").field("description","Style sheet").field("field","DEFAULT_STYLE").field("value","default").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Show related tables even if no privilege").field("field","SHOW_ALL_RELATED_TABLES").field("value","true").save();				
			}

			System.out.print(TABLE_LOCALE+" ");
			OClass localeTable = Database.checkCreateClass(oschema, TABLE_LOCALE, installMessages);
			Database.checkCreateProperty(localeTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(localeTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(localeTable, "active", OType.BOOLEAN, installMessages);
			
			ODocument loc;  // Locale
			if (localeTable.count() == 0) {
				loc = con.create(TABLE_LOCALE);
				loc.field("name","en");
				loc.field("description","English");
				loc.field("active",true);
				loc.save();
				installMessages.append(Weblet.paragraph("success","CheckInstallation: Created en locale"));
			} else {
				loc = con.queryDocument("SELECT FROM locale WHERE name='en'");
			}
			
			System.out.print(TABLE_MESSAGE+" ");
			OClass messageTable = Database.checkCreateClass(oschema, TABLE_MESSAGE, installMessages);
			Database.checkCreateProperty(messageTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(messageTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(messageTable, "locale", OType.LINK, localeTable, installMessages);

			Message.initialize(con);
			
			int mCount = 0;
			mCount += checkCreateMessage(con, loc, "HEADER_TITLE", "The dynamic adaptive data management platform");
			mCount += checkCreateMessage(con, loc, "DATE_FORMAT", "yyyy-MM-dd");
			mCount += checkCreateMessage(con, loc, "TIME_FORMAT", "hh:mm:ss");
			mCount += checkCreateMessage(con, loc, "WELCOME_USER", "Welcome {0}");
			mCount += checkCreateMessage(con, loc, "DATE_LABEL", "Date:");
			mCount += checkCreateMessage(con, loc, "TABLE_EDITOR", "{0} editor");
			mCount += checkCreateMessage(con, loc, "LOGIN_TITLE", "Login");
			mCount += checkCreateMessage(con, loc, "HOME_PAGE_TITLE", "Home page");
			mCount += checkCreateMessage(con, loc, "LOGIN_BUTTON_TEXT", "Login");
			mCount += checkCreateMessage(con, loc, "USER_LABEL", "User");
			mCount += checkCreateMessage(con, loc, "PASSWORD_LABEL", "Password");
			mCount += checkCreateMessage(con, loc, "REQUEST_LOGIN", "Request login");
			mCount += checkCreateMessage(con, loc, "INVALID_USER_OR_PASSWORD", "Invalid user/password");
			mCount += checkCreateMessage(con, loc, "LOGOUT", "Logout");
			mCount += checkCreateMessage(con, loc, "TABLE_NONGROUPED", "Ungrouped");
			mCount += checkCreateMessage(con, loc, "ALL_ROWS_IN_TABLE", "All rows");
			mCount += checkCreateMessage(con, loc, "NEW_ROW", "+Row");
			mCount += checkCreateMessage(con, loc, "ADD_COLUMN", "+Column");
			mCount += checkCreateMessage(con, loc, "TABLE_RIGHTS_OPTIONS", "Rights");
			mCount += checkCreateMessage(con, loc, "ADVANCED_TABLE_OPTIONS", "Advanced");
			mCount += checkCreateMessage(con, loc, "ALL_TABLES", "All tables");
			mCount += checkCreateMessage(con, loc, "SCHEMA_EDITOR", "Tables");
			mCount += checkCreateMessage(con, loc, "CREATE_ROW", "Create");
			mCount += checkCreateMessage(con, loc, "NEW_COLUMN", "Add column");
			mCount += checkCreateMessage(con, loc, "ROLE_CAN_PRIV", "{0} can {1}");
			mCount += checkCreateMessage(con, loc, "EXISTING_RIGHTS", "Existing rights");
			mCount += checkCreateMessage(con, loc, "ADD_OR_REMOVE_RIGHT", "Add or remove access");
			mCount += checkCreateMessage(con, loc, "GRANT_RIGHT", "Grant");
			mCount += checkCreateMessage(con, loc, "REVOKE_RIGHT", "Revoke");
			mCount += checkCreateMessage(con, loc, "ADVANCED_OPTIONS", "Advanced table options");
			mCount += checkCreateMessage(con, loc, "RENAME_TABLE_BUTTON", "Rename table");
			mCount += checkCreateMessage(con, loc, "RENAME_COLUMN_BUTTON", "Rename column");
			mCount += checkCreateMessage(con, loc, "DROP_COLUMN_BUTTON", "Drop column");
			mCount += checkCreateMessage(con, loc, "DROP_TABLE_BUTTON", "Drop table");
			mCount += checkCreateMessage(con, loc, "NEW_TABLE", "New table");
			mCount += checkCreateMessage(con, loc, "PRIV_CREATE", "Create");
			mCount += checkCreateMessage(con, loc, "PRIV_READ", "Read");
			mCount += checkCreateMessage(con, loc, "PRIV_UPDATE", "Update");
			mCount += checkCreateMessage(con, loc, "PRIV_DELETE", "Delete");
			mCount += checkCreateMessage(con, loc, "PRIV_ALL", "do anything");
			mCount += checkCreateMessage(con, loc, "SHUTDOWN_CONFIRM_MESSAGE", "This will shutdown the server, the server will need to be restarted at the host to continue");
			mCount += checkCreateMessage(con, loc, "CONFIRM_SHUTDOWN", "Shut down");
			mCount += checkCreateMessage(con, loc, "SQL_WEBLET", "Query");
			
			if (mCount > 0) {
				installMessages.append(Weblet.paragraph("success","CheckInstallation: Created "+mCount+" messages"));
			}
			
			System.out.print(TABLE_NEWS+" ");
			OClass newsTable = Database.checkCreateClass(oschema, TABLE_NEWS, installMessages);
			Database.checkClassSuperclass(oschema, newsTable, "ORestricted", installMessages);
			Database.checkCreateProperty(newsTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(newsTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(newsTable, "dateline", OType.DATETIME, installMessages);
			Database.checkCreateProperty(newsTable, "locale", OType.LINK, localeTable, installMessages);
			Database.checkCreateProperty(newsTable, "archive", OType.BOOLEAN, installMessages);			

			if (newsTable.count() == 0) {
				ODocument n1 = con.create(TABLE_NEWS);
				n1.field("name","Welcome to PermeAgility");
				n1.field("description","The core template for big applications");
				n1.field("dateline",new Date());
				n1.field("locale",loc);
				n1.field("archive",false);
				n1.field("_allowRead", guestRoles.toArray());
				n1.save();

				ODocument n2 = con.create(TABLE_NEWS);
				n2.field("name","Welcome admin or dba");
				n2.field("description","With great power comes great responsibility. Tread carefully.");
				n2.field("dateline",new Date());
				n2.field("locale",loc);
				n2.field("archive",false);
				n2.field("_allowRead", adminRoles.toArray());
				n2.save();				
			}
			
			System.out.print(TABLE_STYLE+" ");
			OClass styleTable = Database.checkCreateClass(oschema, TABLE_STYLE, installMessages);
			Database.checkCreateProperty(styleTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(styleTable, "description", OType.STRING, installMessages);
			
			if (styleTable.count() == 0) {
				ODocument style = con.create(TABLE_STYLE); 
				style.field("name", "default");
				style.field("description", DEFAULT_STYLESHEET);
				style.save();
			}
			
			System.out.print(TABLE_PICKLIST+" ");
			OClass pickListTable = Database.checkCreateClass(oschema, TABLE_PICKLIST, installMessages);
			Database.checkCreateProperty(pickListTable, "tablename", OType.STRING, installMessages);
			Database.checkCreateProperty(pickListTable, "query", OType.STRING, installMessages);
			Database.checkCreateProperty(pickListTable, "description", OType.STRING, installMessages);

			if (pickListTable.count() == 0) {
				con.create(TABLE_PICKLIST).field("tablename","OIdentity").field("query","select @rid.asString(), name from ORole").field("description","This will restrict row level table privileges to only selecting Roles, alter or remove this to allow user and role selection for _allow, _allowRead, etc... columns").save();				
			}
			
			System.out.print(TABLE_TABLEGROUP+" ");
			OClass tableGroupTable = Database.checkCreateClass(oschema, TABLE_TABLEGROUP, installMessages);
			Database.checkClassSuperclass(oschema, tableGroupTable, "ORestricted", installMessages);
			Database.checkCreateProperty(tableGroupTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(tableGroupTable, "tables", OType.STRING, installMessages);

			if (tableGroupTable.count() == 0) {
				con.create(TABLE_TABLEGROUP).field("name","Security and Users").field("tables","OUser, user, ORole, key, menu, userRequest").field("_allowRead", adminRoles.toArray()).save();
				con.create(TABLE_TABLEGROUP).field("name","Application").field("tables","article, columns, constant, tableGroup, pickList, style, locale, message, -thumbnail").field("_allowRead", adminRoles.toArray()).save();
				con.create(TABLE_TABLEGROUP).field("name","OrientDB System").field("tables","ORole, OUser, OFunction, OSchedule, -ORIDs, -E, -V").field("_allowRead", adminRoles.toArray()).save();
				con.create(TABLE_TABLEGROUP).field("name","News").field("tables","article").field("_allowRead", allRoles.toArray()).save();
			}
			
			System.out.print(TABLE_COLUMNS+" ");
			OClass columnsTable = Database.checkCreateClass(oschema, TABLE_COLUMNS, installMessages);
			Database.checkCreateProperty(columnsTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(columnsTable, "columnList", OType.STRING, installMessages);
			
			if (columnsTable.count() == 0) {
				con.create(TABLE_COLUMNS).field("name",TABLE_NEWS).field("columnList","name, dateline, locale, description, archive").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_COLUMNS).field("columnList","name, columnList").save();	
				con.create(TABLE_COLUMNS).field("name",TABLE_KEY).field("columnList","name, classname, active, description, roles").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_LOCALE).field("columnList","name, description, active").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_MENU).field("columnList","name, active, description").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_MESSAGE).field("columnList","name, description, locale").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_STYLE).field("columnList","name, description").save();
				con.create(TABLE_COLUMNS).field("name",TABLE_THUMBNAIL).field("columnList","name, table, id, column, size, small, medium, width, height").save();
				con.create(TABLE_COLUMNS).field("name",TABLE_PICKLIST).field("columnList","tablename, query, description").save();
				con.create(TABLE_COLUMNS).field("name",TABLE_USERREQUEST).field("columnList","name, email").save();
			}

			System.out.print(TABLE_MENU+" ");
			OClass menuTable = Database.checkCreateClass(oschema, TABLE_MENU, installMessages);
			Database.checkCreateProperty(menuTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(menuTable, "active", OType.BOOLEAN, installMessages);
			Database.checkCreateProperty(menuTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(menuTable, "sortOrder", OType.INTEGER, installMessages);

			ODocument defaultMenu;
			if (menuTable.count() == 0) {
				defaultMenu = con.create(TABLE_MENU);
				defaultMenu.field("name","");  // Default blank menu
				defaultMenu.field("active",true);
				defaultMenu.field("description","Default menu");
				defaultMenu.field("sortOrder",10);
				defaultMenu.save();
			} else {
				defaultMenu = con.queryDocument("SELECT FROM menu WHERE name='Admin'");
			}
			
			System.out.print(TABLE_KEY+" ");
			OClass keyTable = Database.checkCreateClass(oschema, TABLE_KEY, installMessages);
			Database.checkCreateProperty(keyTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(keyTable, "classname", OType.STRING, installMessages);
			Database.checkCreateProperty(keyTable, "active", OType.BOOLEAN, installMessages);
			Database.checkCreateProperty(keyTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(keyTable, "sortOrder", OType.INTEGER, installMessages);
			Database.checkCreateProperty(keyTable, "menu", OType.LINK, menuTable, installMessages);
			OClass roleTable = oschema.getClass("ORole");
			Database.checkCreateProperty(keyTable, "roles", OType.LINKSET, roleTable, installMessages);
			
			if (keyTable.count() == 0) {
				ODocument k0 = con.create(TABLE_KEY);
				k0.field("name","Login");
				k0.field("description","Login page");
				k0.field("classname","permeagility.web.Login");
				k0.field("active",true);
				k0.field("roles", allRoles.toArray());
				k0.save();

				ODocument k1 = con.create(TABLE_KEY);
				k1.field("name","Home");
				k1.field("description","Home page including news");
				k1.field("classname","permeagility.web.Home");
				k1.field("active",true);
				k1.field("roles", allRoles.toArray());
				k1.save();

				ODocument k4 = con.create(TABLE_KEY);
				k4.field("name","Change password");
				k4.field("description","Change password");
				k4.field("classname","permeagility.web.ChangePassword");
				k4.field("active",true);
				k4.field("menu",defaultMenu);
				k4.field("roles", adminRoles.toArray());
				k4.save();

				ODocument k5 = con.create(TABLE_KEY);
				k5.field("name","UserRequest");
				k5.field("description","User Request");
				k5.field("classname","permeagility.web.UserRequest");
				k5.field("active",true);
				k5.field("roles", guestRoles.toArray());
				k5.save();

				ODocument k6 = con.create(TABLE_KEY);
				k6.field("name","Context");
				k6.field("description","Context");
				k6.field("classname","permeagility.web.Context");
				k6.field("active",true);
				k6.field("menu",defaultMenu);
				k6.field("roles", adminRoles.toArray());
				k6.save();

				ODocument k6a = con.create(TABLE_KEY);
				k6a.field("name","Settings");
				k6a.field("description","Basic settings");
				k6a.field("classname","permeagility.web.Settings");
				k6a.field("active",true);
				k6a.field("menu",defaultMenu);
				k6a.field("roles", adminRoles.toArray());
				k6a.save();

				ODocument k7 = con.create(TABLE_KEY);
				k7.field("name","Shutdown");
				k7.field("description","Shutdown the server");
				k7.field("classname","permeagility.web.Shutdown");
				k7.field("active",true);
				k7.field("menu",defaultMenu);
				k7.field("roles", adminRoles.toArray());
				k7.save();

				ODocument k8 = con.create(TABLE_KEY);
				k8.field("name","Query");
				k8.field("description","Query the database");
				k8.field("classname","permeagility.web.SQL");
				k8.field("active",true);
				k8.field("menu",defaultMenu);
				k8.field("roles", adminRoles.toArray());
				k8.save();

				// Following are for allRoles but not guest
				if (guestRoles.size() >0) {
					allRoles.remove(guestRoles.get(0));
				}
				ODocument k2 = con.create(TABLE_KEY);
				k2.field("name","Tables");
				k2.field("description","Table Catalog");
				k2.field("classname","permeagility.web.Schema");
				k2.field("active",true);
				k2.field("menu",defaultMenu);
				k2.field("roles", allRoles.toArray());
				k2.save();			

				ODocument k3 = con.create(TABLE_KEY);
				k3.field("name","Table Editor");
				k3.field("description","Table editor");
				k3.field("classname","permeagility.web.Table");
				k3.field("active",true);
				k3.field("roles", allRoles.toArray());
				k3.save();			

			
			}			

			System.out.print(TABLE_USERREQUEST+" ");
			OClass urTable = Database.checkCreateClass(oschema, TABLE_USERREQUEST, installMessages);
			Database.checkCreateProperty(urTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(urTable, "email", OType.STRING, installMessages);

			con.flush();
			
			System.out.println("- verified.");
			
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("- failed: "+e.getMessage());
			return false;
		}
		return true;
	}

	private static int checkCreateMessage(DatabaseConnection con, ODocument loc, String name, String description) {
		if (Message.get(con.getLocale(), name).equals(name)) {
			ODocument m = con.create(TABLE_MESSAGE);
			m.field("name",name).field("description",description).field("locale",loc);
			m.save();
			return 1;
		} else {
			return 0;
		}
	}
	
	public static String getMessages() {
		return installMessages.toString();
	}
	
	public static final String DEFAULT_STYLESHEET = 
"/* This is the default PermeAgility stylesheet */\n"
+"*.warning { background-color: #FF9900; }\n"
+"img.headerlogo { width: 120px; }\n"
+"BODY { font-family: verdana,sans-serif; \n"
+"       font-size: small; \n"
+"       margin: 0em;\n"
+"       padding: 0em; \n"
+"}\n"
+"#header { position: absolute; top: 0px; left: 0px; right: 0px;\n"
+"         height: 68px; \n"
+"         background-image: url(/images/header_background.jpg); \n"
+"         background-repeat: no-repeat; \n"
+"}\n"
+"#headertitle { font-size: 1.25em; \n"
+"               position: absolute; top: 5px; left: 160px;\n"
+"}\n"
+"#headerservice { font-size: 2em;  \n"
+"                 position: absolute; top: 30px; left: 160px; \n"
+"}\n"
+"#headeruser { position: absolute; top: 52px; left: 5px; }\n"
+"#headertime { position: absolute; top: 5px; right: 5px; }\n"
+"#menu { position: absolute; top: 68px; left: 0px; \n"
+"        width: 145px;  \n"
+"        background-image: url(../images/menu_background.jpg); \n"
+"        background-repeat: no-repeat; \n"
+"        padding-left: 5px;\n"
+"        z-index: 90;\n"
+"}\n"
+"#service { position: absolute; top: 68px; left: 155px; \n"
+"            z-index: 50; display: inline-block;}\n"
+"A.headerlogo:hover { text-decoration: none; background-color: transparent;}\n"
+"A:hover { background-color: #D3D3D3; text-decoration: none; }\n"
+"A.framemenu:link { color: blue; text-decoration: none; }\n"
+"A.framemenu:visited { text-decoration: none; color: blue; }\n"
+"A.framemenu:hover { /*background-color: dimgray; */\n"
+"            text-decoration: underline; \n"
+"            border: hidden;  \n"
+"}\n"
+"INPUT.number { text-align: right; width: 5em; height: 1.5em; }\n"
+"TD.total { text-align: right; \n"
+"           font-weight:bolder; \n"
+"           normal: solid thin black; \n"
+"}\n"
+"TABLE.layout { background-color: none;  width: 100%; }\n"
+"TH { background-color: #303b43; color: white; font-weight: bold; border-radius: 4px 4px 0px 0px; }\n"
+"TR.data, TD.data { background-color: #DCDCDC; }\n"
+"TR.clickable { background-color: #DCDCDC; }\n"
+"TR.clickable:hover { background-color: #AAAADC; text-decoration: bold; }\n"
+"TD.number { text-align: right; }\n"
+"TR.footer { font-weight: bold; }\n"
+"P.menuheader { font-size: 12pt; \n"
+"     font-weight: bold; \n"
+"     text-align:left; \n"
+"     color: black; \n"
+"     margin: 0.2em 0em 0em 0em; \n"
+"     padding 0em;\n"
+"}\n"
+".headline { color: #303b43; font-size: large; font-weight: bold; \n"
+"            margin-bottom: 2px; }\n"
+".dateline { font-size: 6pt; line-height: 50%; \n"
+"            margin-bottom: 1px; margin-top: 1px; padding: 0px;}\n"
+".article { font-size: 12pt; }\n"
+"P.banner { background-color: #303b43; \n"
+"     font-size: medium; \n"
+"     font-weight: bold; \n"
+"     text-align:center; \n"
+"     color: white; \n"
+"     margin: 0.2em 0em 0em 0em; \n"
+"     padding 0em; \n"
+"     page-break-after: avoid; \n"
+"     border-radius: 4px 4px 4px 4px; \n"
+"}\n"
+"P.error { background-color: rgb(117,0,0); \n"
+"           font-size: medium; font-weight: bold; text-align:center; \n"
+"           color: white; margin: 0.2em 0em 0em 0em; padding 0em;\n"
+"          border-radius: 6px 6px 6px 6px;\n"
+"}\n"
+"P.warning { background-color: #FFCC00; \n"
+"          font-size: medium; font-weight: bold; text-align:center; \n"
+"          color: white; margin: 0.2em 0em 0em 0em; padding 0em; \n"
+"          border-radius: 6px 6px 6px 6px;\n"
+"}\n"
+"P.success { background-color: #1d5e1f; \n"
+"          font-size: medium; font-weight: bold; text-align:center; \n"
+"          color: white; margin: 0.2em 0em 0em 0em; padding 0em; \n"
+"          border-radius: 6px 6px 6px 6px;\n"
+"}\n"
+"P.nochange { background-color: rgb(0,0,200); \n"
+"          font-size: medium; font-weight: bold; text-align:center; \n"
+"          color: white; margin: 0.2em 0em 0em 0em; padding 0em; \n"
+"}\n"
+"P.delete { text-align:right; }\n"
+"/*IMG { padding: 0em; margin: 0em; border: 0em; }*/\n"
+"*.alert { background-color: #FF6666; }\n"
+"INPUT {  font-size: x-small; margin: 0em; padding 0em; }\n"
+"*.new { background-color: #FFFF9C }\n"
+"*.changed { background-color: #DEBDDE }\n"
+"TD { font-size: x-small; }\n"
+"TD.total2 { text-align: right; font-weight:bolder; \n"
+"            normal: solid thin black; font-size: medium; \n"
+"}\n"
+"TD.total3 { text-align: right; font-weight:bolder; \n"
+"            normal: solid thin black; font-size: large; \n"
+"}\n"
+"P.bannerleft { background-color: #303b43; \n"
+"       font-size: medium; font-weight: bold; text-align:left; \n"
+"       color: white; margin: 0.2em 0em 0em 0em; padding 0em; \n"
+"}\n"
+"*.framelink { background-color: none; border:none; }\n"
+"SELECT.framemenu { font-size: xx-small; }\n"
+"FORM { margin: 4pt; white-space: nowrap; padding: 4pt; \n"
+"         border: 0em; display: inline; z-index: 50;\n"
+"}\n"
+"/* Sortable tables */\n"
+"table.sortable thead {\n"
+"    background-color: #303b43;\n"
+"    color: white;\n"
+"    font-weight: bold;\n"
+"    cursor: default;\n"
+"}\n"
+"/* Thumbnail images that popup on hover */\n"
+".thumbnail{\n"
+"position: relative;\n"
+"z-index: 0; \n"
+"}\n"
+".thumbnail:hover{\n"
+"background-color: transparent;\n"
+"z-index: 200; \n"
+"}\n"
+".thumbnail span{ /*CSS for enlarged image*/\n"
+"position: absolute;\n"
+"background-color: lightyellow;\n"
+"padding: 5px;\n"
+"left: -1000px;\n"
+"border: 1px dashed gray;\n"
+"visibility: hidden;\n"
+"color: black;\n"
+"text-decoration: none;\n"
+"z-index: 300; \n"
+"}\n"
+".thumbnail span img{ /*CSS for enlarged image*/\n"
+"border-width: 0;\n"
+"padding: 2px;\n"
+"z-index: 350; \n"
+"}\n"
+".thumbnail:hover span{ /*CSS for enlarged image on hover*/\n"
+"visibility: visible;\n"
+"top: 0;\n"
+"left: 50px; /*position where enlarged image should offset horizontally */\n"
+"z-index: 400; \n"
+"}\n"
+"/* For popup forms */\n"
+".popup {\n"
+"  position: relative; left: 5px; display: none;\n"
+"  z-index: 101;\n"
+"}\n"
+" .subtle {\n"
+"  margin: 0px;\n"
+"  padding: 5px;\n"
+"  border: 2px solid gray;\n"
+"  font-size: x-small;\n"
+"  background-color: #EEE;\n"
+"  color: #444;\n"
+"  display: block;\n"
+"  z-index: 100; \n"
+"  position: relative;\n"
+"}\n"
+"form.small input, form.small select, form.small.textarea {\n"
+"  font-size: x-small;\n"
+"}\n"
+"@media print { BODY { font-size: 6pt; margin: 1em; } }\n"
+"@media print { #menu {display: none; } }\n"
+"@media print { #service {position: absolute; top: 0.5in; left: auto;} }\n"
+"@media print { TABLE.data { border: solid thin;  page-break-inside: avoid;} }\n"
+"@media print { TD.header { border: solid thin; } }\n"
+"@media print { *.new { border: dotted thin; } }\n"
+"@media print { *.alert { border: solid medium; border-color: #FF0000;} }\n"
+"@media print { *.changed { border: double thin; } }\n"
+"@media print { *.button { display: none; } }";

}
