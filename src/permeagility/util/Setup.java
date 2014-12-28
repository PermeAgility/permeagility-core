package permeagility.util;

import java.util.ArrayList;
import java.util.Date;

import permeagility.web.Message;
import permeagility.web.Server;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class Setup {

	static StringBuffer installMessages = new StringBuffer();
	
	public static final String TABLE_THUMBNAIL = "thumbnail";
	public static final String TABLE_CONSTANT = "constant";
	public static final String TABLE_LOCALE = "locale";
	public static final String TABLE_MESSAGE = "message";
	public static final String TABLE_STYLE = "style";
	public static final String TABLE_PICKLIST = "pickList";
	public static final String TABLE_TABLEGROUP = "tableGroup";
	public static final String TABLE_COLUMNS = "columns";
	public static final String TABLE_MENU = "menu";
	public static final String TABLE_MENUITEM = "menuItem";
	public static final String TABLE_NEWS = "article";
	public static final String TABLE_USERREQUEST = "userRequest";
	
	/**
	 *  Verify the installation of the permeagility-core table structures
	 *  returns true if all tables and data are sufficient for framework startup
	 */
	public static boolean checkInstallation(DatabaseConnection con) {
		try {
			OSchema oschema = con.getSchema();
			
			System.out.print("DatabaseSetup.checkInstallation ");

			// Setup roles lists
			ArrayList<ODocument> allRoles = new ArrayList<ODocument>();			
			ArrayList<ODocument> allRolesButGuest = new ArrayList<ODocument>();			
			ArrayList<ODocument> adminRoles = new ArrayList<ODocument>();			
			ArrayList<ODocument> guestRoles = new ArrayList<ODocument>();			
			QueryResult qr = con.query("SELECT FROM ORole");
			for (ODocument role : qr.get()) {
				allRoles.add(role);
				if (!role.field("name").equals("guest")) allRolesButGuest.add(role);
				if (role.field("name").equals("admin")) adminRoles.add(role);
				if (role.field("name").equals("guest")) guestRoles.add(role);
			}
						
			ODocument adminUser = con.queryDocument("SELECT FROM OUser WHERE name='admin'");
			ODocument guestUser = con.queryDocument("SELECT FROM OUser WHERE name='guest'");

			if (guestRoles.size() == 0) {
				ODocument guestRole = (ODocument)con.update("insert into ORole set name = 'guest', mode = 0");
//						+", rules = {\"database.schema\":2, \"database\":2, \"database.command\":3"
//						+", \"database.class.userrequest\":1, \"database.cluster.userrequest\":1"
//						+", \"database.class.article\":2, \"database.cluster.article\":2"
//						+", \"database.cluster.*\":2"
//						+ ", \"database.class.style\":2,\"database.cluster.style\":2 }");
				try {
					con.update("GRANT READ ON database.cluster.* TO guest");
					con.update("GRANT READ ON database.class.article TO guest");
					con.update("GRANT READ ON database.class.style TO guest");
					con.update("GRANT READ ON database.class.locale TO guest");
					con.update("GRANT CREATE ON database.class.userrequest TO guest");
					con.update("GRANT CREATE ON database.cluster.userrequest TO guest");
				} catch (Exception e) {
					e.printStackTrace();
				}
				guestRoles.add(guestRole);
				allRoles.add(guestRole);
				installMessages.append(Weblet.paragraph("CheckInstallation: Created guest role"));
			}
			if (guestUser == null) {
				guestUser = (ODocument)con.update("insert into OUser set name = 'guest', password = 'guest', status = 'ACTIVE', roles = (select from ORole where name = 'guest')");
				installMessages.append(Weblet.paragraph("CheckInstallation: Created guest user"));
			}

			// columns must be first as it will receive the properties as they are created by checkCreateProperty
			System.out.print(TABLE_COLUMNS+" ");  
			OClass columnsTable = Database.checkCreateClass(oschema, TABLE_COLUMNS, installMessages);
			// Need to create first two column manually then we can call the function that adds it to the new columns
			if (columnsTable != null && !columnsTable.existsProperty("name")) {  
				columnsTable.createProperty("name", OType.STRING);
			}
			if (columnsTable != null && !columnsTable.existsProperty("columnList")) {
				columnsTable.createProperty("columnList", OType.STRING);
			}
			Database.checkCreateProperty(con, columnsTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, columnsTable, "columnList", OType.STRING, installMessages);
			
			System.out.print(TABLE_THUMBNAIL+" ");
			OClass thumbnailTable = Database.checkCreateClass(oschema, TABLE_THUMBNAIL, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "table", OType.STRING, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "column", OType.STRING, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "id", OType.STRING, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "type", OType.STRING, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "size", OType.INTEGER, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "width", OType.INTEGER, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "height", OType.INTEGER, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "small", OType.CUSTOM, installMessages);
			Database.checkCreateProperty(con, thumbnailTable, "medium", OType.CUSTOM, installMessages);

			System.out.print(TABLE_CONSTANT+" ");
			OClass constantTable = Database.checkCreateClass(oschema, TABLE_CONSTANT, installMessages);
			Database.checkCreateProperty(con, constantTable, "classname", OType.STRING, installMessages);
			Database.checkCreateProperty(con, constantTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(con, constantTable, "field", OType.STRING, installMessages);
			Database.checkCreateProperty(con, constantTable, "value", OType.STRING, installMessages);
			
			if (constantTable.count() == 0) {
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Server").field("description","Server debug flag").field("field","DEBUG").field("value","false").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Server").field("description","Use images/js in jar").field("field","WWW_IN_JAR").field("value","true").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Table debug flag").field("field","DEBUG").field("value","false").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Table page count").field("field","ROW_COUNT_LIMIT").field("value","200").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Show related tables even if no privilege").field("field","SHOW_ALL_RELATED_TABLES").field("value","true").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Context").field("description","Style sheet").field("field","DEFAULT_STYLE").field("value","default").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Header").field("description","Logo for header").field("field","LOGO_FILE").field("value","Logo-blk.svg").save();				
				con.create(TABLE_CONSTANT).field("classname","permeagility.web.Schema").field("description","Number of columns in tables view").field("field","NUMBER_OF_COLUMNS").field("value","4").save();				
			}

			System.out.print(TABLE_LOCALE+" ");
			OClass localeTable = Database.checkCreateClass(oschema, TABLE_LOCALE, installMessages);
			Database.checkCreateProperty(con, localeTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, localeTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(con, localeTable, "active", OType.BOOLEAN, installMessages);
			
			ODocument loc;  // Locale
			if (localeTable.count() == 0) {
				loc = con.create(TABLE_LOCALE);
				loc.field("name","en");
				loc.field("description","English");
				loc.field("active",true);
				loc.save();
				installMessages.append(Weblet.paragraph("CheckInstallation: Created en locale"));
			} else {
				loc = con.queryDocument("SELECT FROM locale WHERE name='en'");
			}
			
			System.out.print(TABLE_MESSAGE+" ");
			OClass messageTable = Database.checkCreateClass(oschema, TABLE_MESSAGE, installMessages);
			Database.checkCreateProperty(con, messageTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, messageTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(con, messageTable, "locale", OType.LINK, localeTable, installMessages);

			Message.initialize(con);
			
			int mCount = 0;
			// These first ones are pretty special as Data format and type format are universally needed but locale specific
			mCount += checkCreateMessage(con, loc, "DATE_FORMAT", "yyyy-MM-dd");
			mCount += checkCreateMessage(con, loc, "TIME_FORMAT", "hh:mm:ss");

			// These are the data types that can be used
			mCount += checkCreateMessage(con, loc, "DATATYPE_FLOAT", "Floating point number (double)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_INT", "Whole number (integer)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_TEXT", "Text (any length)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_BOOLEAN", "Boolean (true/false)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_DATETIME", "Date and time");
			mCount += checkCreateMessage(con, loc, "DATATYPE_DATE", "Date");
			mCount += checkCreateMessage(con, loc, "DATATYPE_DECIMAL", "Decimal (Currency)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_BLOB", "Binary (image/file)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_LINK", "Link (single reference)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_LINKLIST", "Link list (ordered)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_LINKSET", "Link set (unordered)");
			mCount += checkCreateMessage(con, loc, "DATATYPE_LINKMAP", "Link map (with names, ordered)");

			// These are core messages
			mCount += checkCreateMessage(con, loc, "HEADER_TITLE", "The dynamic adaptive data management platform");
			mCount += checkCreateMessage(con, loc, "HEADER_LOGO_DESC", "Go to the home page");
			mCount += checkCreateMessage(con, loc, "WELCOME_USER", "Welcome {0}");
			mCount += checkCreateMessage(con, loc, "DATE_LABEL", "Date:");
			mCount += checkCreateMessage(con, loc, "TABLE_EDITOR", "{0} editor");
			mCount += checkCreateMessage(con, loc, "LOGIN_TITLE", "Login");
			mCount += checkCreateMessage(con, loc, "PAGE_NAV", "Page");
			mCount += checkCreateMessage(con, loc, "HOME_PAGE_TITLE", "Home page");
			mCount += checkCreateMessage(con, loc, "LOGIN_BUTTON_TEXT", "Login");
			mCount += checkCreateMessage(con, loc, "USER_LABEL", "User");
			mCount += checkCreateMessage(con, loc, "PASSWORD_LABEL", "Password");
			mCount += checkCreateMessage(con, loc, "REQUEST_LOGIN", "Sign up");
			mCount += checkCreateMessage(con, loc, "PASSWORD_CHANGE_SUCCESS", "Password changed");
			mCount += checkCreateMessage(con, loc, "CHANGE_PASSWORD_FOR", "Change {0} password");
			mCount += checkCreateMessage(con, loc, "CURRENT_PASSWORD", "Current password");
			mCount += checkCreateMessage(con, loc, "NEW_PASSWORD", "New password");
			mCount += checkCreateMessage(con, loc, "CONFIRM_PASSWORD", "Confirm password");
			mCount += checkCreateMessage(con, loc, "SERVER_SETTINGS", "Server settings");
			mCount += checkCreateMessage(con, loc, "SET_STYLE", "Stylesheet");
			mCount += checkCreateMessage(con, loc, "SET_ROWCOUNT", "Table page size");
			mCount += checkCreateMessage(con, loc, "INVALID_USER_OR_PASSWORD", "Invalid user/password");
			mCount += checkCreateMessage(con, loc, "YOU_ARE_NOT_LOGGED_IN", "You are not logged in");
			mCount += checkCreateMessage(con, loc, "LOGOUT", "Logout");
			mCount += checkCreateMessage(con, loc, "LANGUAGE", "Language");
			mCount += checkCreateMessage(con, loc, "SELECT_LANGUAGE", "Select");
			mCount += checkCreateMessage(con, loc, "NO_ACCESS_TO_TABLE", "Unsufficient privilege to access table");
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
			mCount += checkCreateMessage(con, loc, "DROP_COLUMN", "Drop column");
			mCount += checkCreateMessage(con, loc, "DROP_TABLE_BUTTON", "Drop table");
			mCount += checkCreateMessage(con, loc, "DROP_TABLE", "Drop table");
			mCount += checkCreateMessage(con, loc, "DROP_TABLE_CONFIRM", "Are you sure you want to drop this table? (cannot be undone)");
			mCount += checkCreateMessage(con, loc, "NEW_TABLE", "New table");
			mCount += checkCreateMessage(con, loc, "NEW_TABLE_CREATED", "New table created called {0} will be shown as {1} - translate with TABLE_{0}");			
			mCount += checkCreateMessage(con, loc, "PRIV_CREATE", "Create");
			mCount += checkCreateMessage(con, loc, "PRIV_READ", "Read");
			mCount += checkCreateMessage(con, loc, "PRIV_UPDATE", "Update");
			mCount += checkCreateMessage(con, loc, "PRIV_DELETE", "Delete");
			mCount += checkCreateMessage(con, loc, "PRIV_ALL", "do anything");
			mCount += checkCreateMessage(con, loc, "SHUTDOWN_CONFIRM_MESSAGE", "This will shutdown the server, the server will need to be restarted at the host to continue");
			mCount += checkCreateMessage(con, loc, "CONFIRM_SHUTDOWN", "Shut down");
			mCount += checkCreateMessage(con, loc, "SHUTDOWN_RESTART", "with restart? (if running in script)");
			mCount += checkCreateMessage(con, loc, "SHUTDOWN_SERVER", "Shut down the server");
			mCount += checkCreateMessage(con, loc, "SQL_WEBLET", "Query");
			mCount += checkCreateMessage(con, loc, "CANNOT_CREATE_ROW", "Error creating row: ");
			mCount += checkCreateMessage(con, loc, "CANNOT_CREATE_COLUMN", "Error creating column: ");
			mCount += checkCreateMessage(con, loc, "CANNOT_UPDATE", "Error updating row: ");
			mCount += checkCreateMessage(con, loc, "NEW_ROW_CREATED", "New row created: ");
			mCount += checkCreateMessage(con, loc, "NEW_COLUMN_CREATED", "New column created");
			mCount += checkCreateMessage(con, loc, "ROW_UPDATED", "Row updated: {0}");
			mCount += checkCreateMessage(con, loc, "SYSTEM_STYLE_UPDATED", "Style changed");
			mCount += checkCreateMessage(con, loc, "ROW_COUNT_LIMIT_UPDATED", "Page size updated");
			mCount += checkCreateMessage(con, loc, "GOTO_ROW", "Goto&gt;");
			mCount += checkCreateMessage(con, loc, "COPY", "Copy");
			mCount += checkCreateMessage(con, loc, "DELETE_MESSAGE", "Are you sure you want to delete this?");
			mCount += checkCreateMessage(con, loc, "DELETE", "Delete");
			mCount += checkCreateMessage(con, loc, "UPDATE", "Update");
			mCount += checkCreateMessage(con, loc, "CANCEL", "Cancel");
			mCount += checkCreateMessage(con, loc, "ONLY_ONE_ROW_CAN_BE_EDITED", "Sorry, Only one row can be edited at a time");
			mCount += checkCreateMessage(con, loc, "ROW_CANNOT_BE_DELETED", "Sorry, The row could not be deleted:");
			mCount += checkCreateMessage(con, loc, "ROW_DELETED", "Row deleted");
			mCount += checkCreateMessage(con, loc, "NOTHING_TO_UPDATE", "Nothing to update");
			mCount += checkCreateMessage(con, loc, "NOTHING_TO_INSERT", "Nothing to insert");
			mCount += checkCreateMessage(con, loc, "INVALID_DATE_VALUE", "Cannot read date value {0} column not updated");
			mCount += checkCreateMessage(con, loc, "INVALID_NUMBER_VALUE", "Cannot read number value {0} column not updated");
			mCount += checkCreateMessage(con, loc, "COLUMN_NAME_AND_TYPE_REQUIRED", "Column name and type required");
			mCount += checkCreateMessage(con, loc, "SPECIFY_COLUMN_NAME", "Column name required");
			mCount += checkCreateMessage(con, loc, "LINK_TYPES_NEED_LINK_TABLE", "Link columns must specify a link table");
			mCount += checkCreateMessage(con, loc, "REDIRECT_TO_SCHEMA", "No table specified, redirecting to tables page");
			mCount += checkCreateMessage(con, loc, "REDIRECT", "Redirecting...");
			mCount += checkCreateMessage(con, loc, "UNKNOWN_FIELD_TYPE", "Cannot recognize field type {0} in column {1}");
			mCount += checkCreateMessage(con, loc, "THUMBNAIL_NOT_FOUND", "Cannot find thumbnail for column {0} in record {1}");
			mCount += checkCreateMessage(con, loc, "BACK_TO_TABLE", "Return to table");
			mCount += checkCreateMessage(con, loc, "RENAME_TABLE_TO", "Rename table to");
			mCount += checkCreateMessage(con, loc, "RENAME_COLUMN", "Rename column");
			mCount += checkCreateMessage(con, loc, "CHANGE_NAME_TO", "change name to");
			mCount += checkCreateMessage(con, loc, "ADD_OR_REMOVE", "add/remove");
			mCount += checkCreateMessage(con, loc, "CLICK_TO_DELETE", "click to delete");
			mCount += checkCreateMessage(con, loc, "CLICK_TO_MOVE_UP", "click to move up");
			mCount += checkCreateMessage(con, loc, "CLICK_TO_MOVE_DOWN", "click to move down");
			mCount += checkCreateMessage(con, loc, "ADD_ITEM", "add item");
			mCount += checkCreateMessage(con, loc, "USE_CONTROLS_TO_CHANGE", "use controls to change items");
			mCount += checkCreateMessage(con, loc, "OPTION_NONE", "None");
			mCount += checkCreateMessage(con, loc, "SUBMIT_BUTTON", "Submit");
			mCount += checkCreateMessage(con, loc, "EXECUTE_QUERY", "Execute query");
			mCount += checkCreateMessage(con, loc, "QUERY_RESULTS", "Query results");
			mCount += checkCreateMessage(con, loc, "RESULTS_TOP", "top");
			mCount += checkCreateMessage(con, loc, "RESULTS_BOTTOM", "bottom");
			mCount += checkCreateMessage(con, loc, "NO_QUERY_GIVEN", "no query was given");
			mCount += checkCreateMessage(con, loc, "QUERY_IS", "Query is:");
			mCount += checkCreateMessage(con, loc, "ERROR_IN_QUERY", "Error in query:");
			mCount += checkCreateMessage(con, loc, "ROWS_UPDATED", "{0} rows updated");
			mCount += checkCreateMessage(con, loc, "ROWS_OF", "{0} rows of {1}");
			mCount += checkCreateMessage(con, loc, "SECURITY_REFRESHED", "security cache was refreshed");
			mCount += checkCreateMessage(con, loc, "SECURITY_UPDATED", "security last updated");
			mCount += checkCreateMessage(con, loc, "REFRESH_SECURITY", "Refresh security");
			mCount += checkCreateMessage(con, loc, "CHECK_INSTALLATION", "Check installation");
			mCount += checkCreateMessage(con, loc, "SERVER_CONTEXT", "Server context information");
			mCount += checkCreateMessage(con, loc, "SERVER_SETUP", "Server setup information");
			mCount += checkCreateMessage(con, loc, "SERVER_SECURITY", "Server security information");
			mCount += checkCreateMessage(con, loc, "SERVER_SESSIONS", "Sessions");
			mCount += checkCreateMessage(con, loc, "SERVER_ON_PORT", "HTTP/Web server on port");
			mCount += checkCreateMessage(con, loc, "SERVER_RUNNING", "running since");
			mCount += checkCreateMessage(con, loc, "SERVER_CONNECT", "connected to");
			mCount += checkCreateMessage(con, loc, "SERVER_USER", "with user");
			mCount += checkCreateMessage(con, loc, "SERVER_VERSION", "OrientDB version is");
			mCount += checkCreateMessage(con, loc, "SERVER_CACHE", "Cached lists");
			mCount += checkCreateMessage(con, loc, "CACHE_COUNT", "{0} Cached");
			mCount += checkCreateMessage(con, loc, "CACHE_CLEAR_COLUMNS", "Clear table columns cache");
			mCount += checkCreateMessage(con, loc, "CACHE_CLEAR_MENUS", "Clear menu cache");
			mCount += checkCreateMessage(con, loc, "CACHE_CLEAR_LISTS", "Clear list cache");
			mCount += checkCreateMessage(con, loc, "CACHED_QUERY", "Cached query");
			mCount += checkCreateMessage(con, loc, "CACHE_SIZE", "Cache size");
			mCount += checkCreateMessage(con, loc, "CACHE_LASTREFRESH", "Last refreshed");
			mCount += checkCreateMessage(con, loc, "BACKUP_AND_RESTORE", "Backup and restore");
			mCount += checkCreateMessage(con, loc, "BACKUP_DIRECTORY_CREATED", "Backup directory was created");
			mCount += checkCreateMessage(con, loc, "BACKUP_THE_DATABASE", "Backup the database");
			mCount += checkCreateMessage(con, loc, "BACKUP_FILENAME", "Backup filename");
			mCount += checkCreateMessage(con, loc, "BACKUP_DATE", "Backup date");
			mCount += checkCreateMessage(con, loc, "BACKUP_SIZE", "Backup size");
			mCount += checkCreateMessage(con, loc, "BACKUP_FILENAME_NEEDED", "Backup filename needed");
			mCount += checkCreateMessage(con, loc, "BACKUP_NOW", "Backup now");
			mCount += checkCreateMessage(con, loc, "BACKUP_SUCCESS", "The database was successfully backed up to:");
			mCount += checkCreateMessage(con, loc, "BACKUP_FAIL", "Error backing up the database:");
			mCount += checkCreateMessage(con, loc, "RESTORE_THE_DATABASE", "Restore the database");
			mCount += checkCreateMessage(con, loc, "RESTORE_NOW", "Restore now");
			mCount += checkCreateMessage(con, loc, "RESTORE_CONFIRM", "Restoring a backup will logout and lockout all users,<br> restore the database and restart the system.<br> Data that is currently in the database may be lost.<br> Please confirm this action");
			mCount += checkCreateMessage(con, loc, "RESTORE_ACCESS", "You must be admin or a dba to backup and restore a database");
			mCount += checkCreateMessage(con, loc, "RESTORE_PLOCAL", "You can only restore a plocal database using this tool");
			mCount += checkCreateMessage(con, loc, "USERREQUEST_INSERTED", "Your request was inserted - you will receive an email to confirm your account");
			mCount += checkCreateMessage(con, loc, "IMAGE_VIEW_LINK", ".");
			mCount += checkCreateMessage(con, loc, "IMAGE_VIEW_HEADER", "View");
			mCount += checkCreateMessage(con, loc, "DOWNLOAD_FULL_SIZE", "Download full size");
			
			if (mCount > 0) {
				installMessages.append(Weblet.paragraph("CheckInstallation: Created "+mCount+" messages"));
				Server.tableUpdated("message");
			}
			
			System.out.print(TABLE_NEWS+" ");
			OClass newsTable = Database.checkCreateClass(oschema, TABLE_NEWS, installMessages);
			Database.checkClassSuperclass(oschema, newsTable, "ORestricted", installMessages);
			Database.checkCreateProperty(con, newsTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, newsTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(con, newsTable, "dateline", OType.DATETIME, installMessages);
			Database.checkCreateProperty(con, newsTable, "locale", OType.LINK, localeTable, installMessages);
			Database.checkCreateProperty(con, newsTable, "archive", OType.BOOLEAN, installMessages);			

			if (newsTable.count() == 0) {
				ODocument n1 = con.create(TABLE_NEWS);
				n1.field("name","Welcome to PermeAgility");
				n1.field("description","The core template for big applications in a micro service<br><img src='images/Logo.svg'>");
				n1.field("dateline",new Date());
				n1.field("locale",loc);
				n1.field("archive",false);
				n1.field("_allowRead", guestRoles.toArray());
				n1.save();

				ODocument n2 = con.create(TABLE_NEWS);
				n2.field("name","Welcome admin or dba");
				n2.field("description","Tips for administrators\n"
						+ "<ul>\n"
						+ "<li>Use the backup tool - you can deploy backup files as starter databases</li>\n"
						+ "<li>Copy the main menu as a backup when making menu changes</li>\n"
						+ "<li>Change the admin, reader, writer, and server passwords</li>\n"
						+ "<li>If a system table is deleted or truncated, it will be restored to factory settings during startup</li>\n"
						+ "<li>see <a target='_blank' href='http://www.permeagility.com'>www.permeagility.com</a> for more information</li>\n"
						+ "</ul>\n");
				n2.field("dateline",new Date());
				n2.field("locale",loc);
				n2.field("archive",false);
				n2.field("_allowRead", adminRoles.toArray());
				n2.save();				
			}
			
			System.out.print(TABLE_STYLE+" ");
			OClass styleTable = Database.checkCreateClass(oschema, TABLE_STYLE, installMessages);
			Database.checkCreateProperty(con, styleTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, styleTable, "horizontal", OType.BOOLEAN, installMessages);
			Database.checkCreateProperty(con, styleTable, "logo", OType.STRING, installMessages);
			Database.checkCreateProperty(con, styleTable, "description", OType.STRING, installMessages);
			
			if (styleTable.count() == 0) {
				ODocument style = con.create(TABLE_STYLE); 
				style.field("name", "default");
				style.field("description", DEFAULT_STYLESHEET);
				style.field("logo", "Logo-blk.svg");
				style.save();

				ODocument style2 = con.create(TABLE_STYLE); 
				style2.field("name", "horizontal");
				style2.field("horizontal", true);
				style2.field("logo", "Logo-yel.svg");
				style2.field("description", DEFAULT_ALT_STYLESHEET);
				style2.save();
				
			}
			
			System.out.print(TABLE_PICKLIST+" ");
			OClass pickListTable = Database.checkCreateClass(oschema, TABLE_PICKLIST, installMessages);
			Database.checkCreateProperty(con, pickListTable, "tablename", OType.STRING, installMessages);
			Database.checkCreateProperty(con, pickListTable, "query", OType.STRING, installMessages);
			Database.checkCreateProperty(con, pickListTable, "description", OType.STRING, installMessages);

			if (pickListTable.count() == 0) {
				con.create(TABLE_PICKLIST).field("tablename","OIdentity").field("query","select @rid.asString(), name from ORole").field("description","This will restrict row level table privileges to only selecting Roles, alter or remove this to allow user and role selection for _allow, _allowRead, etc... columns").save();				
			}
			
			System.out.print(TABLE_TABLEGROUP+" ");
			OClass tableGroupTable = Database.checkCreateClass(oschema, TABLE_TABLEGROUP, installMessages);
			Database.checkClassSuperclass(oschema, tableGroupTable, "ORestricted", installMessages);
			Database.checkCreateProperty(con, tableGroupTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, tableGroupTable, "tables", OType.STRING, installMessages);

			if (tableGroupTable.count() == 0) {
				con.create(TABLE_TABLEGROUP).field("name","Application").field("tables","article, columns, constant, tableGroup, pickList, style, locale, message, menu, menuItem, userRequest, -thumbnail").field("_allowRead", adminRoles.toArray()).save();
				con.create(TABLE_TABLEGROUP).field("name","System").field("tables","ORole, OUser, OFunction, OSchedule, -ORIDs, -E, -V, -_studio").field("_allowRead", adminRoles.toArray()).save();
				con.create(TABLE_TABLEGROUP).field("name","News").field("tables","article").field("_allowRead", allRoles.toArray()).save();
			}
			
			if (columnsTable.count() == 0) {
				con.create(TABLE_COLUMNS).field("name",TABLE_NEWS).field("columnList","name, dateline, locale, description, archive").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_COLUMNS).field("columnList","name, columnList").save();	
				con.create(TABLE_COLUMNS).field("name",TABLE_LOCALE).field("columnList","name, description, active").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_MENU).field("columnList","name, active, description").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_MENUITEM).field("columnList","name, classname, active, description, _allowRead").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_MESSAGE).field("columnList","name, description, locale").save();				
				con.create(TABLE_COLUMNS).field("name",TABLE_STYLE).field("columnList","name, horizontal, logo, description").save();
				con.create(TABLE_COLUMNS).field("name",TABLE_THUMBNAIL).field("columnList","name, table, id, column, size, small, medium, width, height").save();
				con.create(TABLE_COLUMNS).field("name",TABLE_PICKLIST).field("columnList","tablename, query, description").save();
				con.create(TABLE_COLUMNS).field("name",TABLE_USERREQUEST).field("columnList","name, email").save();
			}

			System.out.print(TABLE_MENU+" ");
			OClass menuTable = Database.checkCreateClass(oschema, TABLE_MENU, installMessages);
			Database.checkCreateProperty(con, menuTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, menuTable, "active", OType.BOOLEAN, installMessages);
			Database.checkCreateProperty(con, menuTable, "description", OType.STRING, installMessages);
			Database.checkCreateProperty(con, menuTable, "sortOrder", OType.INTEGER, installMessages);

			if (menuTable.count() == 0) {
				ODocument defaultMenu;
				defaultMenu = con.create(TABLE_MENU);
				defaultMenu.field("name","");  // Default blank menu
				defaultMenu.field("active",true);
				defaultMenu.field("description","Default menu");
				defaultMenu.field("sortOrder",10);
				defaultMenu.save();
			}
			
			System.out.print(TABLE_MENUITEM+" ");
			OClass menuItemTable = Database.checkCreateClass(oschema, TABLE_MENUITEM, installMessages);
			Database.checkCreateProperty(con, menuItemTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, menuItemTable, "classname", OType.STRING, installMessages);
			Database.checkCreateProperty(con, menuItemTable, "active", OType.BOOLEAN, installMessages);
			Database.checkCreateProperty(con, menuItemTable, "description", OType.STRING, installMessages);
			Database.checkClassSuperclass(oschema, menuItemTable, "ORestricted", installMessages);
			
			if (menuItemTable.count() == 0) {
				ODocument mi_login = con.create(TABLE_MENUITEM);
				mi_login.field("name","Login");
				mi_login.field("description","Login page");
				mi_login.field("classname","permeagility.web.Login");
				mi_login.field("active",true);
				mi_login.field("_allowRead", allRoles.toArray());
				mi_login.save();

				ODocument mi_home = con.create(TABLE_MENUITEM);
				mi_home.field("name","Home");
				mi_home.field("description","Home page including news");
				mi_home.field("classname","permeagility.web.Home");
				mi_home.field("active",true);
				mi_home.field("_allowRead", allRoles.toArray());
				mi_home.save();

				ODocument mi_password = con.create(TABLE_MENUITEM);
				mi_password.field("name","Password");
				mi_password.field("description","Change password");
				mi_password.field("classname","permeagility.web.Password");
				mi_password.field("active",true);
				mi_password.field("_allowRead", allRolesButGuest.toArray());
				mi_password.save();

				ODocument mi_userRequest = con.create(TABLE_MENUITEM);
				mi_userRequest.field("name","Sign up");
				mi_userRequest.field("description","User Request");
				mi_userRequest.field("classname","permeagility.web.UserRequest");
				mi_userRequest.field("active",true);
				mi_userRequest.field("_allowRead", guestRoles.toArray());
				mi_userRequest.save();

				ODocument mi_context = con.create(TABLE_MENUITEM);
				mi_context.field("name","Context");
				mi_context.field("description","Context");
				mi_context.field("classname","permeagility.web.Context");
				mi_context.field("active",true);
				mi_context.field("_allowRead", adminRoles.toArray());
				mi_context.save();

				ODocument mi_settings = con.create(TABLE_MENUITEM);
				mi_settings.field("name","Settings");
				mi_settings.field("description","Basic settings");
				mi_settings.field("classname","permeagility.web.Settings");
				mi_settings.field("active",true);
				mi_settings.field("_allowRead", adminRoles.toArray());
				mi_settings.save();

				ODocument mi_shutdown = con.create(TABLE_MENUITEM);
				mi_shutdown.field("name","Shutdown");
				mi_shutdown.field("description","Shutdown the server");
				mi_shutdown.field("classname","permeagility.web.Shutdown");
				mi_shutdown.field("active",true);
				mi_shutdown.field("_allowRead", adminRoles.toArray());
				mi_shutdown.save();

				ODocument mi_query = con.create(TABLE_MENUITEM);
				mi_query.field("name","Query");
				mi_query.field("description","Query the database");
				mi_query.field("classname","permeagility.web.Query");
				mi_query.field("active",true);
				mi_query.field("_allowRead", adminRoles.toArray());
				mi_query.save();

				ODocument mi_schema = con.create(TABLE_MENUITEM);
				mi_schema.field("name","Tables");
				mi_schema.field("description","Table Catalog");
				mi_schema.field("classname","permeagility.web.Schema");
				mi_schema.field("active",true);
				mi_schema.field("_allowRead", allRolesButGuest.toArray());
				mi_schema.save();

				ODocument mi_table = con.create(TABLE_MENUITEM);
				mi_table.field("name","Table Editor");
				mi_table.field("description","Table editor");
				mi_table.field("classname","permeagility.web.Table");
				mi_table.field("active",true);
				mi_table.field("_allowRead", allRolesButGuest.toArray());
				mi_table.save();				

				ODocument mi_backup = con.create(TABLE_MENUITEM);
				mi_backup.field("name","Backup");
				mi_backup.field("description","Backup and restore the database");
				mi_backup.field("classname","permeagility.web.BackupRestore");
				mi_backup.field("active",true);
				mi_backup.field("_allowRead", adminRoles.toArray());
				mi_backup.save();				

				ODocument mi_blank = con.create(TABLE_MENUITEM);
				mi_blank.field("name","");
				mi_blank.field("active",true);
				mi_blank.field("description","Blank menu item");
				mi_blank.field("_allow",adminRoles);
				mi_blank.field("_allowRead",allRoles);
				mi_blank.save();

				ODocument mi_translate = con.create(TABLE_MENUITEM);
				mi_translate.field("name","Translate");
				mi_translate.field("active",false);
				mi_translate.field("description","Translate messages (plus)");
				mi_translate.field("classname","permeagility.plus.Translate");
				mi_translate.field("_allow",adminRoles);
				mi_translate.field("_allowRead",adminRoles);
				mi_translate.save();

				ODocument mi_merge = con.create(TABLE_MENUITEM);
				mi_merge.field("name","Merge");
				mi_merge.field("active",false);
				mi_merge.field("description","Merge data (plus)");
				mi_merge.field("classname","permeagility.plus.Merge");
				mi_merge.field("_allow",adminRoles);
				mi_merge.field("_allowRead",adminRoles);
				mi_merge.save();

				// Build default menu
				ArrayList<ODocument> items = new ArrayList<ODocument>();
				items.add(mi_userRequest);
				items.add(mi_schema);
				items.add(mi_query);
				items.add(mi_blank);
				items.add(mi_context);
				items.add(mi_settings);
				items.add(mi_password);
				items.add(mi_backup);
				items.add(mi_shutdown);
				items.add(mi_translate); // Will be inactive
				items.add(mi_merge);   // Will be inactive

				// Add the menu items property to the menu
				Database.checkCreateProperty(con, menuTable, "items", OType.LINKLIST, menuItemTable, installMessages);
				ODocument menuDoc = con.queryDocument("SELECT FROM menu");
				if (menuDoc != null && items.size() > 0) {
					menuDoc.field("items",items.toArray());
					menuDoc.save();
				} else {
					installMessages.append(Weblet.paragraph("error","menu is null or no items to add"));
				}
			}			
			
			System.out.print(TABLE_USERREQUEST+" ");
			OClass urTable = Database.checkCreateClass(oschema, TABLE_USERREQUEST, installMessages);
			Database.checkCreateProperty(con, urTable, "name", OType.STRING, installMessages);
			Database.checkCreateProperty(con, urTable, "email", OType.STRING, installMessages);
			Database.checkCreateProperty(con, urTable, "password", OType.STRING, installMessages);

			con.flush();
			
			System.out.println("- verified.");
			
		} catch (Exception e) {
			System.out.println("- failed: "+e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/** Create message if it doesn't already exist */
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
"/* This is the default PermeAgility stylesheet */\n" +
"img.headerlogo { width: 110px; height: 55px; position: fixed; top: 3px; left: 3px; border: none;}\n" +
"a.headerlogo:hover { text-decoration: none; background-color: transparent;}\n" +
"BODY { font-family: verdana,sans-serif; /* font-size: small; */ }\n" +
"#menu { position: fixed; top: 0px; left: 0px; bottom: 0px;\n" +
"  width: 145px; background-repeat: repeat-y; border: 0;\n" +
"  background-image: url(../images/menu_background.jpg);\n" +
"  padding-left: 5px; padding-top: 75px; /* menu starts below header */\n" +
"}\n" +
"a, a.menuitem, a.popuplink {color: black;}\n" +
"a.menuitem:link { text-decoration: none; }\n" +
"a.menuitem:visited { text-decoration: none; }\n" +
"a:hover, a.menuitem:hover, a.popuplink:hover { text-decoration: none; color: blue;\n" +
"         border-radius: 6px 6px 6px 6px;   border: 1px solid blue;  }\n" +
"#header { position: absolute; top: 0px; left: 0px; right: 0px; height: 75px;\n" +
"         background-image: url(/images/header_background.jpg);   }\n" +
"#headertitle { font-size: 1em; position: absolute; top: 5px; left: 160px;  }\n" +
"#headerservice { font-size: 1.5em;    position: absolute; top: 30px; left: 160px; }\n" +
"#headertime { position: absolute; top: 5px; right: 5px; }\n" +
"#headeruser { position: absolute; top: 25px; right: 5px; }\n" +
"#service { position: absolute; top: 75px; left: 125px; right: 0px; bottom: 0px; display: inline-block;}\n" +
".label { color: black; }\n" +
"td.label { text-align: right; vertical-align: middle; font-size: x-small;\n" +
"  border-radius: 6px 6px 6px 6px;\n" +
" background: linear-gradient(to right, white, #E0E0E0);\n" +
"        border: none;  padding: 0px 5px 0px 25px;}\n" +
"a { text-decoration: none; }\n" +
"a:hover { text-decoration: underline; }\n" +
"input.number { text-align: right; }\n" +
"table.layout {  width: 100%; }\n" +
"td.layout { border-radius: 6px 6px 6px 6px; text-align: left;\n" +
"    padding: 2px 2px 2px 2px;  background-color: #DCDCDC;\n" +
"}\n" +
"th { background-color: #303b43; color: white; font-weight: bold;\n" +
"     border-radius: 4px 4px 0px 0px;\n" +
"}\n" +
"tr.data, TD.data { background-color: #DCDCDC; vertical-align: top; }\n" +
"tr.clickable { background-color: #DCDCDC; vertical-align: top; }\n" +
"tr.clickable:hover { background-color: #AAAADC; text-decoration: bold; }\n" +
"tr.footer { font-weight: bold; }\n" +
"td { text-align: left;  }\n" +
"td.number { text-align: right; }\n" +
"td.total { text-align: right; font-weight:bolder; normal: solid thin black; }\n" +
"p.headline { color: #303b43; font-size: large; font-weight: bold;\n" +
"            margin-bottom: 2px; }\n" +
"p.dateline { font-size: 6pt; line-height: 50%;\n" +
"            margin-bottom: 1px; margin-top: 1px; }\n" +
"p.article { font-size: 12pt; }\n" +
"p.menuheader {  color: black;  margin: 0.2em 0em 0em 0em; }\n" +
"P.banner { background-color: #777777;\n" +
"     font-size: medium;  font-weight: bold; text-align:center; color: white;\n" +
"     margin: 0.2em 0em 0em 0em;\n" +
"     page-break-after: avoid;\n" +
"     border-radius: 4px 4px 4px 4px;\n" +
"}\n" +
"P.error { background-color: rgb(117,0,0);\n" +
"           font-size: medium; font-weight: bold; text-align:center;\n" +
"           color: white; margin: 0.2em 0em 0em 0em;\n" +
"          border-radius: 6px 6px 6px 6px;\n" +
"}\n" +
"P.warning { background-color: #FFCC00;\n" +
"          font-size: medium; font-weight: bold; text-align:center;\n" +
"          color: white; margin: 0.2em 0em 0em 0em;\n" +
"          border-radius: 6px 6px 6px 6px;\n" +
"}\n" +
"P.success { background-color: #1d5e1f;\n" +
"          font-size: medium; font-weight: bold; text-align:center;\n" +
"          color: white; margin: 0.2em 0em 0em 0em;\n" +
"          border-radius: 6px 6px 6px 6px;\n" +
"}\n" +
"P.nochange { background-color: rgb(0,0,200);\n" +
"          font-size: medium; font-weight: bold; text-align:center;\n" +
"          color: white; margin: 0.2em 0em 0em 0em;\n" +
"}\n" +
"*.alert { background-color: #FF6666; }\n" +
"*.new { background-color: #FFFF9C }\n" +
"*.changed { background-color: #DEBDDE }\n" +
"*.warning { background-color: #FF9900; }\n" +
"P.delete { text-align:right; }\n" +
"P.bannerleft { background-color: #303b43;\n" +
"       font-size: medium; font-weight: bold; text-align:left;\n" +
"       color: white; margin: 0.2em 0em 0em 0em;\n" +
"}\n" +
"/* Sortable tables */\n" +
"table.sortable thead {\n" +
"    background-color: #303b43;\n" +
"    color: white;\n" +
"    font-weight: bold;\n" +
"    cursor: default;\n" +
"}\n" +
".canpopup {\n" +
"    z-index: 100; position: absolute;  display: none;\n" +
" /*   opacity: 0.9; */  background-color: white;\n" +
"    border: 1px solid gray;\n" +
"    padding: 0.4em 0.4em 0.4em 0.4em;\n" +
"     border-radius: 6px 6px 6px 6px;\n" +
"}\n" +
".screenfade {\n" +
"    z-index: 99; position: fixed;  display: none;\n" +
"    opacity: 0.6;   background-color: gray;\n" +
"    top: 0; left: 0; bottom: 0; right: 0;\n" +
"}\n" +
"@media print { BODY { font-size: 6pt; margin: 1em; } }\n" +
"@media print { #menu {display: none; } }\n" +
"@media print { #service {position: absolute; top: 0.5in; left: auto;} }\n" +
"@media print { TABLE.data { border: solid thin;  page-break-inside: avoid;} }\n" +
"@media print { TD.header { border: solid thin; } }\n" +
"@media print { *.new { border: dotted thin; } }\n" +
"@media print { *.alert { border: solid medium; border-color: #FF0000;} }\n" +
"@media print { *.changed { border: double thin; } }\n" +
"@media print { *.button { display: none; } }\n";
					
	public static final String DEFAULT_ALT_STYLESHEET = 
"/* horizontal menu in Gravity style */\n" +
"img.headerlogo { width: 90px; left: 2px; top: 25px; \n" +
"    position: absolute; border: none; }\n" +
"a.headerlogo:hover { text-decoration: none; background-color: transparent;}\n" +
"body, html { font-family: verdana,sans-serif;\n" +
"       color: white;\n" +
"       background-color: black; }\n" +
"#menu { position: fixed; left: 0px; top: 2px; right: 0px; height: 20px; z-index: 100;\n" +
"/*  background-repeat: repeat-y; border: 0;\n" +
"  background-image: url(../images/PSBlackMenu.jpg); */\n" +
"  padding-left: 5px; \n" +
"}\n" +
"a, a.menuitem, a.popuplink {color: lightgray;}\n" +
"a.menuitem:link { text-decoration: none; }\n" +
"a.menuitem:visited { text-decoration: none; }\n" +
"a:hover, a.menuitem:hover, a.popuplink:hover { text-decoration: none;   \n" +
"    color: white;  \n" +
"    background: radial-gradient(ellipse, darkorange, black);\n" +
"/*         border-radius: 6px 6px 6px 6px;   border: 1px solid cyan;  */\n" +
"}\n" +
"#header { position: absolute; top: 0px; left: 0px; right: 0px; height: 80px;\n" +
"         background-image: url(/images/PSBlackHeader.jpg);   }\n" +
"#headertitle { font-size: 0.75em; position: absolute; top: 30px; left: 100px; }\n" +
"#headerservice { font-size: 1em; position: absolute; top: 50px; left: 100px; }\n" +
"#headertime { font-size: 0.75em; position: absolute; top: 20px; right: 5px; }\n" +
"#headeruser { font-size: 0.75em; position: absolute; top: 40px; right: 5px; }\n" +
"#service { position: absolute; top: 80px;  left: 5px; right: 5px; display: inline-block;}\n" +
".label { color: black; }\n" +
"td.label { text-align: right; vertical-align: middle; font-size: small; \n" +
"  color: white; font-weight: bold;\n" +
"  border-radius: 6px 6px 6px 6px;  \n" +
" background: linear-gradient(to right, black, #444444);\n" +
"        border: none;  padding: 0px 5px 0px 25px;}\n" +
"a { text-decoration: none; }\n" +
"a:hover { text-decoration: underline; }\n" +
"input, textarea, select {\n" +
"  background-color : #222222;\n" +
"  color: white; \n" +
"}\n" +
"input.number { text-align: right; }\n" +
"table.layout {  width: 100%; }\n" +
"td.layout { border-radius: 6px 6px 6px 6px;\n" +
"    padding: 2px 2px 2px 2px;  background-color: #222222;\n" +
"}\n" +
"th { font-weight: bold; \n" +
"     border-radius: 8px 8px 0px 0px; \n" +
"    background-color: #339999;  \n" +
"     background: radial-gradient(ellipse, #339999, black);\n" +
"    font-weight: bold; color: white;\n" +
"}\n" +
"tr.data, td.data { background-color: #222222; vertical-align: top; }\n" +
"tr.clickable { background-color: #222222; vertical-align: top; }\n" +
"tr.clickable:hover {         \n" +
"   background: radial-gradient(ellipse, darkorange, black);\n" +
"}\n" +
"tr.footer { font-weight: bold; }\n" +
"td { text-align: left;  }\n" +
"td.number { text-align: right; }\n" +
"td.total { text-align: right; font-weight:bolder; normal: solid thin black; }\n" +
"p.headline { color: #339999; font-size: large; font-weight: bold; \n" +
"            margin-bottom: 2px; }\n" +
"p.dateline { font-size: 6pt; line-height: 50%; \n" +
"            margin-bottom: 1px; margin-top: 1px; }\n" +
"p.article { font-size: 12pt; }\n" +
"p.menuheader {  color: white;  margin: 0.2em 0em 0em 0em; }\n" +
"P.banner { background-color: #336666;\n" +
"     font-weight: bold;  text-align:center;  color: white; \n" +
"     margin: 0.2em 0em 0em 0em; \n" +
"     page-break-after: avoid; \n" +
"     border-radius: 8px 8px 8px 8px; \n" +
"     background: radial-gradient(ellipse, #336666, black);\n" +
"}\n" +
"P.error { \n" +
"           font-weight: bold; text-align:center; \n" +
"           color: white; margin: 0.2em 0em 0em 0em; \n" +
"          border-radius: 6px 6px 6px 6px;\n" +
" background: radial-gradient(ellipse, rgb(117,0,0), black);\n" +
"}\n" +
"P.warning {\n" +
"    font-weight: bold; text-align:center; \n" +
"    color: white; margin: 0.2em 0em 0em 0em; \n" +
"    border-radius: 6px 6px 6px 6px;\n" +
"    background-color: #FFCC00;\n" +
"    background: radial-gradient(ellipse, #FFCC00, black);\n" +
"}\n" +
"P.success { background-color: #1d5e1f; \n" +
"          font-weight: bold; text-align:center; \n" +
"          color: white; margin: 0.2em 0em 0em 0em; \n" +
"          border-radius: 6px 6px 6px 6px;\n" +
"          background: radial-gradient(ellipse, #1d5e1f, black);\n" +
"}\n" +
"P.nochange { background-color: rgb(0,0,200); \n" +
"          font-weight: bold; text-align:center; \n" +
"          color: white; margin: 0.2em 0em 0em 0em; \n" +
"}\n" +
"*.alert { background-color: #FF6666; }\n" +
"*.new { background-color: #FFFF9C }\n" +
"*.changed { background-color: #DEBDDE }\n" +
"*.warning { background-color: #FF9900; }\n" +
"P.delete { text-align:right; }\n" +
"P.bannerleft { background-color: #303b43; \n" +
"       font-weight: bold; text-align:left; \n" +
"       color: white; margin: 0.2em 0em 0em 0em; \n" +
"}\n" +
"/* Sortable tables */\n" +
"table.sortable thead {\n" +
"    background-color: #303b43;\n" +
"    color: white;\n" +
"    font-weight: bold;\n" +
"    cursor: default;\n" +
"}\n" +
".canpopup {\n" +
"    z-index: 100; position: absolute;  display: none;\n" +
" /*   opacity: 0.9; */  background-color: black;    \n" +
"    border: 1px solid gray;\n" +
"    padding: 0.4em 0.4em 0.4em 0.4em;\n" +
"     border-radius: 6px 6px 6px 6px; \n" +
"}\n" +
".screenfade {\n" +
"    z-index: 99; position: fixed;  display: none; \n" +
"    opacity: 0.7;   background-color: #000000;\n" +
"    top: 0; left: 0; bottom: 0; right: 0;\n" +
"}\n" +
"@media print { BODY { font-size: 6pt; margin: 1em; } }\n" +
"@media print { #menu {display: none; } }\n" +
"@media print { #service {position: absolute; top: 0.5in; left: auto;} }\n" +
"@media print { TABLE.data { border: solid thin;  page-break-inside: avoid;} }\n" +
"@media print { TD.header { border: solid thin; } }\n" +
"@media print { *.new { border: dotted thin; } }\n" +
"@media print { *.alert { border: solid medium; border-color: #FF0000;} }\n" +
"@media print { *.changed { border: double thin; } }\n" +
"@media print { *.button { display: none; } }\n";
}