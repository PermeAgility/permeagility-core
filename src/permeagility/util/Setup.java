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
package permeagility.util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.database.RID;
import com.arcadedb.engine.ComponentFile.MODE;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import com.arcadedb.server.ArcadeDBServer;
import com.arcadedb.server.ServerDatabase;

import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Server;
import permeagility.web.Weblet;

public class Setup {

    static StringBuilder installMessages = new StringBuilder();

    public static boolean RESTRICTED_BY_ROLE = true;
    private static String SETUP_DEBUG_FLAG = "false";    // default setting for debug constants
    public static boolean SETUP_DEMO_USERS = true;        

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
    public static final String TABLE_NEWS = "news";
    public static final String TABLE_USERPROFILE = "userProfile";
    public static final String TABLE_AUDIT = "auditTrail";
    public static final String TABLE_PICKVALUES = "pickValues";

    public static String schemaScript = 
    """
      CREATE DOCUMENT TYPE restricted IF NOT EXISTS;
      CREATE DOCUMENT TYPE identity IF NOT EXISTS;

      CREATE DOCUMENT TYPE role IF NOT EXISTS;
      ALTER TYPE role SUPERTYPE +identity;
      CREATE DOCUMENT TYPE user IF NOT EXISTS;
      ALTER TYPE user SUPERTYPE +identity;
      CREATE DOCUMENT TYPE privilege IF NOT EXISTS;
      
      CREATE DOCUMENT TYPE constant IF NOT EXISTS;
      CREATE DOCUMENT TYPE locale IF NOT EXISTS;
      CREATE DOCUMENT TYPE message IF NOT EXISTS;
      CREATE DOCUMENT TYPE pickList IF NOT EXISTS;
      CREATE DOCUMENT TYPE tableGroup IF NOT EXISTS;
      CREATE DOCUMENT TYPE columns IF NOT EXISTS;
      CREATE DOCUMENT TYPE pickValues IF NOT EXISTS;
      CREATE DOCUMENT TYPE auditTrail IF NOT EXISTS;
      CREATE DOCUMENT TYPE thumbnail IF NOT EXISTS;
      CREATE DOCUMENT TYPE userProfile IF NOT EXISTS;

      CREATE DOCUMENT TYPE menu IF NOT EXISTS;
      CREATE DOCUMENT TYPE menuItem IF NOT EXISTS;
      ALTER TYPE menuItem SUPERTYPE +restricted;
      CREATE DOCUMENT TYPE news IF NOT EXISTS;
      ALTER TYPE news SUPERTYPE +restricted;


      CREATE PROPERTY identity.name IF NOT EXISTS STRING;
      CREATE PROPERTY restricted._allowUpdate IF NOT EXISTS LIST OF identity;
      CREATE PROPERTY restricted._allowDelete IF NOT EXISTS LIST OF identity;
      CREATE PROPERTY restricted._allowRead IF NOT EXISTS LIST OF identity;
      CREATE PROPERTY restricted._allow IF NOT EXISTS LIST OF identity;

      CREATE PROPERTY role.mode IF NOT EXISTS STRING;
      CREATE PROPERTY role.inheritedRole IF NOT EXISTS LINK OF role;
      CREATE PROPERTY role.rules IF NOT EXISTS EMBEDDED;

      CREATE PROPERTY user.password IF NOT EXISTS STRING;
      CREATE PROPERTY user.status IF NOT EXISTS STRING;
      CREATE PROPERTY user.roles IF NOT EXISTS LIST OF role;

      CREATE PROPERTY privilege.access IF NOT EXISTS STRING;
      CREATE PROPERTY privilege.resource IF NOT EXISTS STRING;
      CREATE PROPERTY privilege.identity IF NOT EXISTS LINK OF identity;

      CREATE PROPERTY constant.classname IF NOT EXISTS STRING;
      CREATE PROPERTY constant.description IF NOT EXISTS STRING;
      CREATE PROPERTY constant.field IF NOT EXISTS STRING;
      CREATE PROPERTY constant.value IF NOT EXISTS STRING;

      CREATE PROPERTY locale.name IF NOT EXISTS STRING;
      CREATE PROPERTY locale.description IF NOT EXISTS STRING;
      CREATE PROPERTY locale.active IF NOT EXISTS BOOLEAN;

      CREATE PROPERTY message.name IF NOT EXISTS STRING;
      CREATE PROPERTY message.description IF NOT EXISTS STRING;
      CREATE PROPERTY message.locale IF NOT EXISTS LINK OF locale;

      CREATE PROPERTY pickList.tablename IF NOT EXISTS STRING;
      CREATE PROPERTY pickList.query IF NOT EXISTS STRING;
      CREATE PROPERTY pickList.description IF NOT EXISTS STRING;

      CREATE PROPERTY tableGroup.name IF NOT EXISTS STRING;
      CREATE PROPERTY tableGroup.tables IF NOT EXISTS STRING;

      CREATE PROPERTY columns.name IF NOT EXISTS STRING;
      CREATE PROPERTY columns.columnList IF NOT EXISTS STRING;

      CREATE PROPERTY menu.name IF NOT EXISTS STRING;
      CREATE PROPERTY menu.active IF NOT EXISTS BOOLEAN;
      CREATE PROPERTY menu.description IF NOT EXISTS STRING;
      CREATE PROPERTY menu.sortOrder IF NOT EXISTS INTEGER;

      CREATE PROPERTY menuItem.name IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.description IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.classname IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.type IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.active IF NOT EXISTS BOOLEAN;
      CREATE PROPERTY menuItem.pageScript IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.pageStyle IF NOT EXISTS STRING;
      CREATE PROPERTY menuItem.useStyleFrom IF NOT EXISTS LINK OF menuItem;

      CREATE PROPERTY menu.items IF NOT EXISTS LIST OF menuItem;

      CREATE PROPERTY news.name IF NOT EXISTS STRING;
      CREATE PROPERTY news.description IF NOT EXISTS STRING;
      CREATE PROPERTY news.dateline IF NOT EXISTS DATETIME;
      CREATE PROPERTY news.locale IF NOT EXISTS LINK OF locale;
      CREATE PROPERTY news.archive IF NOT EXISTS BOOLEAN;

      CREATE PROPERTY pickValues.name IF NOT EXISTS STRING;
      CREATE PROPERTY pickValues.values IF NOT EXISTS STRING;

      CREATE PROPERTY auditTrail.timestamp IF NOT EXISTS DATETIME;
      CREATE PROPERTY auditTrail.user IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.action IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.table IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.rid IF NOT EXISTS STRING;
      CREATE PROPERTY auditTrail.detail IF NOT EXISTS EMBEDDED;
      CREATE INDEX IF NOT EXISTS ON auditTrail (timestamp) NOTUNIQUE;
      CREATE INDEX IF NOT EXISTS ON auditTrail (rid) NOTUNIQUE;

      CREATE PROPERTY thumbnail.name IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.table IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.column IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.id IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.type IF NOT EXISTS STRING;
      CREATE PROPERTY thumbnail.size IF NOT EXISTS LONG;
      CREATE PROPERTY thumbnail.width IF NOT EXISTS INTEGER;
      CREATE PROPERTY thumbnail.height IF NOT EXISTS INTEGER;
      CREATE PROPERTY thumbnail.small IF NOT EXISTS BINARY;
      CREATE PROPERTY thumbnail.medium IF NOT EXISTS BINARY;
      CREATE INDEX IF NOT EXISTS ON thumbnail (id) NOTUNIQUE; 

      CREATE PROPERTY userProfile.name IF NOT EXISTS STRING;
      CREATE PROPERTY userProfile.password IF NOT EXISTS STRING;

      """;

    /* This should not be run inside a transaction */
    public static boolean checkInstallation(DatabaseConnection con) {
 //       boolean myTrans = !con.getDb().isTransactionActive();
        try {
            System.out.println("DatabaseSetup.checkInstallation ");
            con.updateScript(schemaScript);
            System.out.println("DatabaseSetup.checkInstallation finished schemaScript");

  //          if (myTrans) {
  //              System.out.println("Setup.checkInstallation starting a transaction");
  //              con.begin();
  //          }

            Schema schema = con.getSchema();
 
            // Setup roles lists for use later on in this script
            List<Document> allRoles = new ArrayList<Document>();
            List<Document> allRolesButGuest = new ArrayList<Document>();
            List<Document> adminRoles = new ArrayList<Document>();
            List<Document> staffRoles = new ArrayList<Document>();
            List<Document> adminAndStaffRoles = new ArrayList<Document>();
            List<Document> customerRoles = new ArrayList<Document>();
            List<Document> guestRoles = new ArrayList<Document>();

            Document adminRole = checkCreateRole(con, "admin", "SUPER", "", installMessages);
            adminRoles.add(adminRole);
            allRoles.add(adminRole);
            adminAndStaffRoles.add(adminRole);
            allRolesButGuest.add(adminRole);

            Document staffRole = checkCreateRole(con, "staff", "SUPER", "", installMessages);
            staffRoles.add(staffRole);
            adminAndStaffRoles.add(staffRole);
            allRoles.add(staffRole);
            allRolesButGuest.add(staffRole);

            Document guestRole = checkCreateRole(con, "guest", "NORMAL", "", installMessages);
            allRoles.add(guestRole);
            guestRoles.add(guestRole);

            Document customerRole = checkCreateRole(con, "customer", "NORMAL", "guest", installMessages);
            allRoles.add(customerRole);
            allRolesButGuest.add(customerRole);
            customerRoles.add(customerRole);

            checkCreateUser(con, "admin", "admin", "admin", installMessages);
            checkCreateUser(con, "guest", "guest", "guest", installMessages);
            if (SETUP_DEMO_USERS) {  // todo: may need to call up the constant in db since we are before constants have loaded (or are we?)
                checkCreateUser(con, "cust1", "cust1", "customer", installMessages);
                checkCreateUser(con, "cust2", "cust2", "customer", installMessages);
                checkCreateUser(con, "cust3", "cust3", "customer", installMessages);
                checkCreateUser(con, "staff1", "staff1", "staff", installMessages);
                checkCreateUser(con, "staff2", "staff2", "staff", installMessages);
                checkCreateUser(con, "staff3", "staff3", "staff", installMessages);
            }
            
            // Verify the minimum privileges for the guest role
            checkCreatePrivilege(con, guestRole.getIdentity(), "READ", "menuItem", installMessages);  // home page (menuItem is restricted)
            checkCreatePrivilege(con, guestRole.getIdentity(), "READ", "thumbnail", installMessages);  // can read images
            checkCreatePrivilege(con, guestRole.getIdentity(), "READ", "news", installMessages);  // read the news
            checkCreatePrivilege(con, guestRole.getIdentity(), "READ", "message", installMessages);  // get messages
            checkCreatePrivilege(con, guestRole.getIdentity(), "CREATE", "userProfile", installMessages);  // create user request
            checkCreatePrivilege(con, guestRole.getIdentity(), "UPDATE", "userProfile", installMessages);  // Update their profile
            checkCreatePrivilege(con, guestRole.getIdentity(), "READ", "userProfile", installMessages);    // see their profile

            // Staff are mode SUPER, access everything except for: (protecting the system config)
            // note: in a mode SUPER, READONLY means no CREATE, UPDATE, or DELETE
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "schema", installMessages);   // no add table
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "restricted", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "identity", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "role", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "user", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "menu", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "menuItem", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "tableGroup", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "columns", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "pickList", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "pickValues", installMessages);
            checkCreatePrivilege(con, staffRole.getIdentity(), "READONLY", "constant", installMessages);

            // A customer is a guest until they log in 
            // then they are guest + whatever is granted to the customer role for the application
            // ie. orders, reservations, social media posts, etc...

            // columns must be first as it will receive the properties as they are created by checkCreateProperty
            System.out.println(TABLE_COLUMNS+" ");
            DocumentType columnsTable = Setup.checkCreateTable(schema, TABLE_COLUMNS, installMessages);
            // Need to create first two column manually then we can call the function that adds it to the new columns
            if (columnsTable != null && !columnsTable.existsProperty("name")) {
                columnsTable.createProperty("name", Type.STRING);
            }
            if (columnsTable != null && !columnsTable.existsProperty("columnList")) {
                columnsTable.createProperty("columnList", Type.STRING);
            }
            // This will ensure they are added to columns table in proper order
            Setup.checkCreateColumn(con, columnsTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, columnsTable, "columnList", Type.STRING, installMessages);
 
            // Create early so we can automatically add tables to them
            System.out.println(TABLE_TABLEGROUP+" ");
            DocumentType tableGroupTable = Setup.checkCreateTable(schema, TABLE_TABLEGROUP, installMessages);
            //Setup.checkTableSuperclass(schema, tableGroupTable, "restricted", installMessages);
            Setup.checkCreateColumn(con, tableGroupTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, tableGroupTable, "tables", Type.STRING, installMessages);

            if (con.getRowCount(TABLE_TABLEGROUP) == 0) {
                    con.create(TABLE_TABLEGROUP).set("name","Application").set("tables", "news,locale,message,-thumbnail").save();
                    con.create(TABLE_TABLEGROUP).set("name","Configure").set("tables", "menu,tableGroup,columns,pickList,pickValues,constant").save();
                    con.create(TABLE_TABLEGROUP).set("name","Security").set("tables", "menuItem,role,user,userProfile,auditTrail,privilege,-restricted,-identity").save();
                    con.create(TABLE_TABLEGROUP).set("name","Content").set("tables","").save();
                    con.create(TABLE_TABLEGROUP).set("name","Plus").set("tables","").save();
            }
  
            System.out.println(TABLE_THUMBNAIL+" ");
            DocumentType thumbnailTable = Setup.checkCreateTable(schema, TABLE_THUMBNAIL, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "table", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "column", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "id", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "type", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "size", Type.LONG, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "width", Type.INTEGER, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "height", Type.INTEGER, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "small", Type.BINARY, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "medium", Type.BINARY, installMessages);

            System.out.println(TABLE_CONSTANT+" ");
            DocumentType constantTable = Setup.checkCreateTable(schema, TABLE_CONSTANT, installMessages);
            Setup.checkCreateColumn(con, constantTable, "classname", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, constantTable, "description", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, constantTable, "field", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, constantTable, "value", Type.STRING, installMessages);

            if (con.getRowCount(TABLE_CONSTANT) == 0) {
                System.out.println("Inserting constants");
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Server").set("description","Server debug flag").set("field","DEBUG").set("value",SETUP_DEBUG_FLAG).save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.util.DatabaseConnection").set("description","Database debug (Shows all SQL)").set("field","DEBUG").set("value",SETUP_DEBUG_FLAG).save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Server").set("description","Audit Trail").set("field","AUDIT_WRITES").set("value","true").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Server").set("description","Use images/js in jar").set("field","WWW_IN_JAR").set("value","true").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Security").set("description","Security debug flag").set("field","DEBUG").set("value",SETUP_DEBUG_FLAG).save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Table").set("description","Table debug flag").set("field","DEBUG").set("value",SETUP_DEBUG_FLAG).save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Table").set("description","Table page count").set("field","ROW_COUNT_LIMIT").set("value","200").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Table").set("description","Show related tables even if no privilege").set("field","SHOW_ALL_RELATED_TABLES").set("value","true").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Context").set("description","Code editor theme").set("field","EDITOR_THEME").set("value","ambiance").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.Header").set("description","Logo for header").set("field","LOGO_FILE").set("value","Logo-yel.svg").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.util.Setup").set("description","When true, restricted tables will be by role (Change identity pickList if setting to user level restrictions)").set("field","RESTRICTED_BY_ROLE").set("value","true").save();
                con.create(TABLE_CONSTANT).set("classname","permeagility.web.UserRequest").set("description","Automatically assign new users to this role, leave blank to prevent automatic new user creation").set("field","ACCEPT_TO_ROLE").set("value","user").save();
            }
 
            System.out.println(TABLE_AUDIT+" ");
            DocumentType auditTable = Setup.checkCreateTable(schema, TABLE_AUDIT, installMessages);
            Setup.checkCreateColumn(con, auditTable, "timestamp", Type.DATETIME, installMessages);
            Setup.checkCreateColumn(con, auditTable, "user", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "action", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "table", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "rid", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "detail", Type.EMBEDDED, installMessages);

      
            System.out.println(TABLE_LOCALE+" ");
            DocumentType localeTable = Setup.checkCreateTable(schema, TABLE_LOCALE, installMessages);
            Setup.checkCreateColumn(con, localeTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, localeTable, "description", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, localeTable, "active", Type.BOOLEAN, installMessages);

            MutableDocument loc;  // Locale
            if (con.getRowCount(TABLE_LOCALE) == 0) {
                loc = con.create(TABLE_LOCALE);
                loc.set("name","en");
                loc.set("description","English");
                loc.set("active",true);
                loc.save();
                installMessages.append(Weblet.paragraph("CheckInstallation: Created English locale(en)"));
            } else {
                loc = con.queryDocument("SELECT FROM locale WHERE name='en'").modify();
            }

            System.out.println(TABLE_MESSAGE+" ");
            DocumentType messageTable = Setup.checkCreateTable(schema, TABLE_MESSAGE, installMessages);
            Setup.checkCreateColumn(con, messageTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, messageTable, "description", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, messageTable, "locale", Type.LINK, localeTable, installMessages);

            Message.initialize(con); // initialize so that we can check if messages exist

            int mCount = 0;
            // These are the data types that can be used
            mCount += checkCreateMessage(con, loc, "DATATYPE_FLOAT", "Floating point number (double)");
            mCount += checkCreateMessage(con, loc, "DATATYPE_INT", "Whole number (int)");
            mCount += checkCreateMessage(con, loc, "DATATYPE_TEXT", "Text (any length)");
            mCount += checkCreateMessage(con, loc, "DATATYPE_BOOLEAN", "Boolean (true/false)");
            mCount += checkCreateMessage(con, loc, "DATATYPE_DATETIME", "Date and time");
            mCount += checkCreateMessage(con, loc, "DATATYPE_DATE", "Date");
            mCount += checkCreateMessage(con, loc, "DATATYPE_DECIMAL", "Decimal (Currency)");
            mCount += checkCreateMessage(con, loc, "DATATYPE_BLOB", "Binary (image/file)");
            mCount += checkCreateMessage(con, loc, "DATATYPE_LINK", "Reference to");
            mCount += checkCreateMessage(con, loc, "DATATYPE_LIST", "List of");
            mCount += checkCreateMessage(con, loc, "DATATYPE_MAP", "Named list of");

            // These are core messages
            mCount += checkCreateMessage(con, loc, "HEADER_TITLE", "Dynamic adaptive data management");
            mCount += checkCreateMessage(con, loc, "HEADER_LOGO_DESC", "Go to the home page");
            mCount += checkCreateMessage(con, loc, "WELCOME_USER", "Welcome {0}");
            mCount += checkCreateMessage(con, loc, "DATE_LABEL", "Date:");
            mCount += checkCreateMessage(con, loc, "TABLE_EDITOR", "{0} editor");
            mCount += checkCreateMessage(con, loc, "LOGIN_TITLE", "Login");
            mCount += checkCreateMessage(con, loc, "PAGE_NAV", "Page");
            mCount += checkCreateMessage(con, loc, "HOME_PAGE_TITLE", "Welcome to PermeAgility");
            mCount += checkCreateMessage(con, loc, "LOGIN_BUTTON_TEXT", "Login");
            mCount += checkCreateMessage(con, loc, "USER_LABEL", "User");
            mCount += checkCreateMessage(con, loc, "PASSWORD_LABEL", "Password");
            mCount += checkCreateMessage(con, loc, "UPDATE_PASSWORD", "Change Password");
            mCount += checkCreateMessage(con, loc, "REQUEST_LOGIN", "Sign up");
            mCount += checkCreateMessage(con, loc, "PASSWORD_CHANGE_SUCCESS", "Password changed");
            mCount += checkCreateMessage(con, loc, "PASSWORD_CHANGE_FAILED", "Current password not verified - cannot change password");
            mCount += checkCreateMessage(con, loc, "UPDATE_PROFILE", "Update profile");
            mCount += checkCreateMessage(con, loc, "PROFILE_UPDATED", "Your profile has been updated");
            mCount += checkCreateMessage(con, loc, "CURRENT_PASSWORD", "Current password");
            mCount += checkCreateMessage(con, loc, "NEW_PASSWORD", "New password");
            mCount += checkCreateMessage(con, loc, "CONFIRM_PASSWORD", "Confirm password");
            mCount += checkCreateMessage(con, loc, "CONFIRM_RESTORE", "Confirm database restore to {0}");
            mCount += checkCreateMessage(con, loc, "SERVER_SETTINGS", "Server settings");
            mCount += checkCreateMessage(con, loc, "SET_ROWCOUNT", "Table page size");
            mCount += checkCreateMessage(con, loc, "INVALID_USER_OR_PASSWORD", "Invalid user/password");
            mCount += checkCreateMessage(con, loc, "YOU_ARE_NOT_LOGGED_IN", "You are not logged in");
            mCount += checkCreateMessage(con, loc, "LOGOUT", "Logout");
            mCount += checkCreateMessage(con, loc, "LANGUAGE", "Language");
            mCount += checkCreateMessage(con, loc, "SELECT_LANGUAGE", "Select");
            mCount += checkCreateMessage(con, loc, "NO_ACCESS_TO_TABLE", "Unsufficient privilege to access table");
            mCount += checkCreateMessage(con, loc, "NO_PERMISSION_TO_VIEW", "Unsufficient privilege to view table");
            mCount += checkCreateMessage(con, loc, "TABLE_NONGROUPED", "Ungrouped");
            mCount += checkCreateMessage(con, loc, "ALL_ROWS_IN_TABLE", "All rows");
            mCount += checkCreateMessage(con, loc, "NEW_ROW", "Add Row");
            mCount += checkCreateMessage(con, loc, "ADD_COLUMN", "Add Column");
            mCount += checkCreateMessage(con, loc, "TABLE_RIGHTS_OPTIONS", "Rights");
            mCount += checkCreateMessage(con, loc, "ADVANCED_TABLE_OPTIONS", "Advanced");
            mCount += checkCreateMessage(con, loc, "ALL_TABLES", "All tables");
            mCount += checkCreateMessage(con, loc, "SCHEMA_EDITOR", "All Tables in {0}");
            mCount += checkCreateMessage(con, loc, "CREATE_ROW", "Create");
            mCount += checkCreateMessage(con, loc, "NEW_COLUMN", "Add column");
            mCount += checkCreateMessage(con, loc, "ROLE_CAN_PRIV", "{0} can {1}");
            mCount += checkCreateMessage(con, loc, "ROLE_CANNOT_PRIV", "{0} can not {1}");
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
            mCount += checkCreateMessage(con, loc, "DROP_TABLE_SUCCESS", "Table removed");
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
            mCount += checkCreateMessage(con, loc, "ROW_COUNT_LIMIT_UPDATED", "Page size updated");
            mCount += checkCreateMessage(con, loc, "GOTO_ROW", "Goto&gt;");
            mCount += checkCreateMessage(con, loc, "COPY", "Copy");
            mCount += checkCreateMessage(con, loc, "ROW_COPIED", "{0} was copied");
            mCount += checkCreateMessage(con, loc, "COPY_PREFIX", "Copied on {0}: ");
            mCount += checkCreateMessage(con, loc, "COPY_SUFFIX", " Copy");
            mCount += checkCreateMessage(con, loc, "DELETE_MESSAGE", "Are you sure you want to delete this?");
            mCount += checkCreateMessage(con, loc, "DELETE", "Delete");
            mCount += checkCreateMessage(con, loc, "UPDATE", "Update");
            mCount += checkCreateMessage(con, loc, "CANCEL", "Cancel");
            mCount += checkCreateMessage(con, loc, "ONLY_ONE_ROW_CAN_BE_EDITED", "Sorry, Only one row can be edited at a time");
            mCount += checkCreateMessage(con, loc, "ROW_CANNOT_BE_DELETED", "Sorry, The row could not be deleted:");
            mCount += checkCreateMessage(con, loc, "ROW_DELETED", "{0} was deleted");
            mCount += checkCreateMessage(con, loc, "NOTHING_TO_UPDATE", "Nothing to update");
            mCount += checkCreateMessage(con, loc, "NOTHING_TO_INSERT", "Nothing to insert");
            mCount += checkCreateMessage(con, loc, "INVALID_DATE_VALUE", "Cannot read date value {0} column not updated");
            mCount += checkCreateMessage(con, loc, "INVALID_DATETIME_VALUE", "Cannot read datetime value {0} column not updated");
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
            mCount += checkCreateMessage(con, loc, "ADD_ITEM", "add");
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
            mCount += checkCreateMessage(con, loc, "SERVER_JAR", "using jar");
            mCount += checkCreateMessage(con, loc, "SERVER_USER", "with user");
            mCount += checkCreateMessage(con, loc, "SERVER_VERSION", "Database version is");
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
            mCount += checkCreateMessage(con, loc, "USERREQUEST_CREATED", "Your request was accepted - please proceed to home page and login");
            mCount += checkCreateMessage(con, loc, "USERREQUEST_NEED_NAMEPASS", "Please enter a username and password");
            mCount += checkCreateMessage(con, loc, "USERREQUEST_EXISTS", "Username exists, please select a different one");
            mCount += checkCreateMessage(con, loc, "USERREQUEST_ERROR", "User request could not be created");
            mCount += checkCreateMessage(con, loc, "IMAGE_VIEW_LINK", ".");
            mCount += checkCreateMessage(con, loc, "IMAGE_VIEW_HEADER", "View");
            mCount += checkCreateMessage(con, loc, "DOWNLOAD_FULL_SIZE", "Download full size");
            mCount += checkCreateMessage(con, loc, "DOWNLOAD_FILE", "Download file");
            mCount += checkCreateMessage(con, loc, "PLUS_MODULES", "Plus modules");
            mCount += checkCreateMessage(con, loc, "PLUS_NAME", "Name");
            mCount += checkCreateMessage(con, loc, "PLUS_DB_VERSION", "DB Version");
            mCount += checkCreateMessage(con, loc, "PLUS_VERSION", "Version");
            mCount += checkCreateMessage(con, loc, "PLUS_SIZE", "Size");
            mCount += checkCreateMessage(con, loc, "PLUS_EMBEDDED", "Embedded");
            mCount += checkCreateMessage(con, loc, "PLUS_SETUP", "Setup");
            mCount += checkCreateMessage(con, loc, "PLUS_DESCRIPTION", "Description");
            mCount += checkCreateMessage(con, loc, "PLUS_INSTALL", "Install");
            mCount += checkCreateMessage(con, loc, "PLUS_REMOVE", "Remove");
            mCount += checkCreateMessage(con, loc, "PLUS_UPGRADE", "Upgrade");
            mCount += checkCreateMessage(con, loc, "DOWNLOAD_PLUS_FILE", "Download");
            mCount += checkCreateMessage(con, loc, "DOWNLOAD_PLUS", "Download new plus");
            mCount += checkCreateMessage(con, loc, "RESTART_REQUIRED", "Restart required");
            mCount += checkCreateMessage(con, loc, "PLUS_PARMS_INVALID", "Please specify a table group, menu and the roles to allow access");
            mCount += checkCreateMessage(con, loc, "REFRESH", "Refresh");
            mCount += checkCreateMessage(con, loc, "VISUILITY", "Visuility");
            mCount += checkCreateMessage(con, loc, "LOGGING_TO_CONSOLE", "-- Logging to console --");
            mCount += checkCreateMessage(con, loc, "LOG_VIEW", "View");
            mCount += checkCreateMessage(con, loc, "LOG_FILENAME", "File Name");
            mCount += checkCreateMessage(con, loc, "LOG_SIZE", "Size");
            mCount += checkCreateMessage(con, loc, "LOG_DATE", "End Date");
            mCount += checkCreateMessage(con, loc, "CHECK_FOR_UPDATE", "Check for update");
            mCount += checkCreateMessage(con, loc, "DOWNLOAD_UPDATE", "Download update");
            mCount += checkCreateMessage(con, loc, "DOWNLOADING_UPDATE", "Downloading");
            mCount += checkCreateMessage(con, loc, "DOWNLOADING_COMPLETE", "Downloaded");
            mCount += checkCreateMessage(con, loc, "APPLY_UPDATE", "Apply update");
            mCount += checkCreateMessage(con, loc, "SAVE_AND_RUN", "Save/Run");
            mCount += checkCreateMessage(con, loc, "DETAILS", "Details");
            mCount += checkCreateMessage(con, loc, "MORE", "More");
            mCount += checkCreateMessage(con, loc, "VIEW_TABLE", "View table: {0}");
            mCount += checkCreateMessage(con, loc, "EDIT_ROW", "Update {0}: {1}");
            mCount += checkCreateMessage(con, loc, "NEWS_PAGE_TITLE", "Welcome to PermeAgility");
            mCount += checkCreateMessage(con, loc, "LIGHTDARK_LABEL", "Mode");
            mCount += checkCreateMessage(con, loc, "NEW_TABLE_NAME", "Table name");
            if (mCount > 0) {
                    installMessages.append(Weblet.paragraph("CheckInstallation: Created "+mCount+" messages"));
                    Server.tableUpdated(con, "message");
            }
            
            System.out.println(TABLE_NEWS+" ");
            DocumentType newsTable = Setup.checkCreateTable(schema, TABLE_NEWS, installMessages);
      //      Setup.checkTableSuperclass(schema, newsTable, "restricted", installMessages);
            Setup.checkCreateColumn(con, newsTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, newsTable, "description", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, newsTable, "dateline", Type.DATETIME, installMessages);
            Setup.checkCreateColumn(con, newsTable, "locale", Type.LINK, localeTable, installMessages);
            Setup.checkCreateColumn(con, newsTable, "archive", Type.BOOLEAN, installMessages);

            if (con.getRowCount(TABLE_NEWS) == 0) {
                MutableDocument n2 = con.create(TABLE_NEWS);
                n2.set("name","Welcome admin");
                n2.set("description", """
                    Tips for administrators
                    <ul>
                    <li>Use the backup tool</li>
                    <li>Copy config items when making changes</li>
                    <li>Change the admin password</li>
                    <li>System tables will be checked during startup</li>
                    <li>Go to the application: 
                    <a href='/Home?NAME=home-dark'>Dark</a>
                    <a href='/Home?NAME=home-light'>Light</a>
                    </li>
                    </ul><li>see <a target='_blank' href='http://www.permeagility.com'>www.permeagility.com</a>
                    for more information
                    </li>
                    </ul>
                    """);
                n2.set("dateline", LocalDateTime.now());
                n2.set("locale",loc);
                n2.set("archive",false);
                n2.set("_allowRead", adminRoles);
                n2.set("_allow", adminAndStaffRoles);
                n2.save();

                MutableDocument n3 = con.create(TABLE_NEWS);
                n3.set("name","Welcome customer");
                n3.set("description","""
                    This is where you will be able to see available products and/or services and then enter and track orders.
                    This system is currently under construction and there are limited functions available to you at this time.
                    Come back for more in the near future.
                   """);
                n3.set("dateline", LocalDateTime.now().plusSeconds(1));
                n3.set("locale",loc);
                n3.set("archive",false);
                n3.set("_allowRead", customerRoles);
                n3.set("_allow", adminAndStaffRoles);
                n3.save();

                MutableDocument n4 = con.create(TABLE_NEWS);
                n4.set("name","Welcome staff");
                n4.set("description","""
                    PermeAgility lets you create, update, and navigate data every way it is connected. 
                    Use the pop up menu at the top left to access the different functions of the system
                    <li>Go to the application: 
                    <a href='/Home?NAME=home-dark'>Dark</a> 
                    <a href='/Home?NAME=home-light'>Light</a>
                    </li>
                    """);
                n4.set("dateline", LocalDateTime.now().plusSeconds(2));
                n4.set("locale",loc);
                n4.set("archive",false);
                n4.set("_allowRead", staffRoles);
                n4.set("_allow", adminAndStaffRoles);
                n4.save();

                MutableDocument n5 = con.create(TABLE_NEWS);
                n5.set("name","Get started");
                n5.set("description","""
                    This is a demo installation which includes security with 
                    multiple roles and support for row level security.
                    You are currently using the system as a guest. 
                    Click below to login as:
                    <center>
                    <a href='/Login?USERNAME=cust1&PASSWORD=cust1'>Customer1</a><br>
                    <a href='/Login?USERNAME=cust2&PASSWORD=cust2'>Customer2</a><br>
                    <a href='/Login?USERNAME=cust3&PASSWORD=cust3'>Customer3</a><br>
                    <a href='/Login?USERNAME=staff1&PASSWORD=staff1'>Staff1</a><br>
                    <a href='/Login?USERNAME=staff2&PASSWORD=staff2'>Staff2</a><br>
                    <a href='/Login?USERNAME=staff3&PASSWORD=staff3'>Staff3</a><br>
                    <a href='/Login?USERNAME=admin&PASSWORD=admin'>Admin</a>
                    </center>
                    """);
                n5.set("dateline", LocalDateTime.now().plusSeconds(3));
                n5.set("locale",loc);
                n5.set("archive",false);
                n5.set("_allowRead", guestRoles);
                n5.set("_allow", adminAndStaffRoles);
                n5.save();

                MutableDocument n1 = con.create(TABLE_NEWS);
                n1.set("name","We are driven by data");
                n1.set("description","""
                    Everything you see here is generated from data found in the database and can be configured to fit your needs. Scroll for more...
                    """);
                n1.set("dateline", LocalDateTime.now().plusSeconds(4));
                n1.set("locale",loc);
                n1.set("archive",false);
                n1.set("_allowRead", guestRoles);
                n1.set("_allow", adminAndStaffRoles);
                n1.save();


                MutableDocument n6 = con.create(TABLE_NEWS);
                n6.set("name","Welcome to PermeAgility");
                n6.set("description","Enable data driven applications anywhere, in any language, for any purpose. Scroll for more...");
                n6.set("dateline", LocalDateTime.now().plusSeconds(5));
                n6.set("locale",loc);
                n6.set("archive",false);
                n6.set("_allowRead", guestRoles);
                n6.set("_allow", adminAndStaffRoles);
                n6.save();

            }
 
            System.out.println(TABLE_PICKLIST+" ");
            DocumentType pickListTable = Setup.checkCreateTable(schema, TABLE_PICKLIST, installMessages);
            Setup.checkCreateColumn(con, pickListTable, "tablename", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, pickListTable, "query", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, pickListTable, "description", Type.STRING, installMessages);

            if (con.getRowCount(TABLE_PICKLIST) == 0) {
                con.create(TABLE_PICKLIST)
                .set("tablename","identity")
                .set("query","SELECT FROM role")
                .set("description","This will restrict row level table privileges to only selecting Roles, if Setup.RESTRICTED_BY_ROLE is false replace identity pickList with SELECT FROM user")
                .save();

                con.create(TABLE_PICKLIST)
                .set("tablename","menuItem.useStyleFrom")
                .set("query","SELECT FROM menuItem WHERE pageStyle IS NOT NULL")
                .set("description","Restrict this list to menuItems with styles")
                .save();
            }
 
            System.out.println(TABLE_PICKVALUES+" ");
            DocumentType pickValuesTable = Setup.checkCreateTable(schema, TABLE_PICKVALUES, installMessages);
            Setup.checkCreateColumn(con, pickValuesTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, pickValuesTable, "values", Type.STRING, installMessages);

            if (con.getRowCount(TABLE_PICKVALUES) == 0) {
                con.create(TABLE_PICKVALUES).set("name","user.status").set("values","ACTIVE,SUSPENDED").save();
                con.create(TABLE_PICKVALUES).set("name","role.mode").set("values","SUPER,NORMAL").save();
                con.create(TABLE_PICKVALUES).set("name","menuItem.type").set("values","WELCOME,HOME,SERVICE,STYLE").save();
            }
 
            System.out.println(TABLE_MENU+" ");
            DocumentType menuTable = Setup.checkCreateTable(schema, TABLE_MENU, installMessages);
            Setup.checkCreateColumn(con, menuTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuTable, "active", Type.BOOLEAN, installMessages);
            Setup.checkCreateColumn(con, menuTable, "description", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuTable, "sortOrder", Type.INTEGER, installMessages);

            if (con.getRowCount(TABLE_MENU) == 0) {
                MutableDocument defaultMenu;
                defaultMenu = con.create(TABLE_MENU);
                defaultMenu.set("name","main");  // Default blank menu
                defaultMenu.set("active",true);
                defaultMenu.set("description","Default menu");
                defaultMenu.set("sortOrder",10);
                defaultMenu.save();
            }

            System.out.println(TABLE_MENUITEM+" ");
            DocumentType menuItemTable = Setup.checkCreateTable(schema, TABLE_MENUITEM, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "description", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "classname", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "type", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "active", Type.BOOLEAN, installMessages).setDefaultValue("true");
            Setup.checkCreateColumn(con, menuItemTable, "pageScript", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "pageStyle", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "useStyleFrom", Type.LINK, menuItemTable, installMessages);
            Setup.checkTableSuperclass(schema, menuItemTable, "restricted", installMessages);
            Setup.checkCreateColumn(con, menuTable, "items", Type.LIST, menuItemTable, installMessages);


            if (con.getRowCount(TABLE_MENUITEM) == 0) {
                MutableDocument mi_login = con.create(TABLE_MENUITEM);
                mi_login.set("name","Login");
                mi_login.set("description","Login page");
                mi_login.set("classname","permeagility.web.Login");
                mi_login.set("type","SERVICE");
                mi_login.set("active",true);
                mi_login.set("_allowRead", allRoles);
                mi_login.set("_allow", adminRoles);
                mi_login.save();

                MutableDocument mi_menu = con.create(TABLE_MENUITEM);
                mi_menu.set("name","Menu");
                mi_menu.set("description","Menu page");
                mi_menu.set("classname","permeagility.web.Menu");
                mi_menu.set("type","SERVICE");
                mi_menu.set("active",true);
                mi_menu.set("_allowRead", allRoles);
                mi_menu.set("_allow", adminRoles);
                mi_menu.save();

                MutableDocument mi_home = con.create(TABLE_MENUITEM);
                mi_home.set("name","Home Page Generator");
                mi_home.set("description","Home Page Generator runs Pages made with the PageBuilder - do not touch this");
                mi_home.set("classname","permeagility.web.Home");
                mi_home.set("type","SERVICE");
                mi_home.set("active",true);
                mi_home.set("_allowRead", allRoles);
                mi_home.set("_allow", adminRoles);
                mi_home.save();

                MutableDocument mi_header = con.create(TABLE_MENUITEM);
                mi_header.set("name","Header");
                mi_header.set("description","Header page");
                mi_header.set("classname","permeagility.web.Header");
                mi_header.set("type","SERVICE");
                mi_header.set("active",true);
                mi_header.set("_allowRead", allRoles);
                mi_header.set("_allow", adminRoles);
                mi_header.save();

                MutableDocument mi_password = con.create(TABLE_MENUITEM);
                mi_password.set("name","Profile");
                mi_password.set("description","Change profile or password");
                mi_password.set("classname","permeagility.web.Profile");
                mi_password.set("type","SERVICE");
                mi_password.set("active",true);
                mi_password.set("_allowRead", allRolesButGuest);
                mi_password.set("_allow", adminRoles);
                mi_password.save();

                MutableDocument mi_userRequest = con.create(TABLE_MENUITEM);
                mi_userRequest.set("name","Sign up");
                mi_userRequest.set("description","User Request");
                mi_userRequest.set("classname","permeagility.web.UserRequest");
                mi_userRequest.set("type","SERVICE");
                mi_userRequest.set("active",true);
                mi_userRequest.set("_allowRead", guestRoles);
                mi_userRequest.set("_allow", adminRoles);
                mi_userRequest.save();

                MutableDocument mi_context = con.create(TABLE_MENUITEM);
                mi_context.set("name","Context");
                mi_context.set("description","Context");
                mi_context.set("classname","permeagility.web.Context");
                mi_context.set("type","SERVICE");
                mi_context.set("active",true);
                mi_context.set("_allowRead", adminRoles);
                mi_context.set("_allow", adminRoles);
                mi_context.save();

                MutableDocument mi_query = con.create(TABLE_MENUITEM);
                mi_query.set("name","Query");
                mi_query.set("description","Query the database");
                mi_query.set("classname","permeagility.web.Query");
                mi_query.set("type","SERVICE");
                mi_query.set("active",true);
                mi_query.set("_allowRead", adminAndStaffRoles);
                mi_query.set("_allow", adminRoles);
                mi_query.save();

                MutableDocument mi_schema = con.create(TABLE_MENUITEM);
                mi_schema.set("name","Tables");
                mi_schema.set("description","Table Catalog");
                mi_schema.set("classname","permeagility.web.Schema");
                mi_schema.set("type","SERVICE");
                mi_schema.set("active",true);
                mi_schema.set("_allowRead", allRolesButGuest);
                mi_schema.set("_allow", adminRoles);
                mi_schema.save();

                MutableDocument mi_table = con.create(TABLE_MENUITEM);
                mi_table.set("name","Table Editor");
                mi_table.set("description","Table editor");
                mi_table.set("classname","permeagility.web.Table");
                mi_table.set("type","SERVICE");
                mi_table.set("active",true);
                mi_table.set("_allowRead", allRolesButGuest);
                mi_table.set("_allow", adminRoles);
                mi_table.save();

                MutableDocument mi_pagebuilder = con.create(TABLE_MENUITEM);
                mi_pagebuilder.set("name","Page Builder");
                mi_pagebuilder.set("description","Build pages/apps with JavaScript");
                mi_pagebuilder.set("classname","permeagility.web.PageBuilder");
                mi_pagebuilder.set("type","SERVICE");
                mi_pagebuilder.set("active",true);
                mi_pagebuilder.set("_allowRead", adminRoles);
                mi_pagebuilder.set("_allow", adminRoles);
                mi_pagebuilder.save();

                MutableDocument mi_basestyle = con.create(TABLE_MENUITEM);  // Not added to menu, to support page Builder
                mi_basestyle.set("name","default-scripts");
                mi_basestyle.set("description","Default scripts for basic PermeAgility functions");
                mi_basestyle.set("pageStyle", DEFAULT_BASE_STYLE);
                mi_basestyle.set("pageScript", DEFAULT_BASE_SCRIPT);
                mi_basestyle.set("type","STYLE");
                mi_basestyle.set("active",true);
                mi_basestyle.set("_allowRead", allRoles);
                mi_basestyle.set("_allow", adminRoles);
                mi_basestyle.save();


                MutableDocument mi_welcome = con.create(TABLE_MENUITEM);  // Not added to menu, to support page Builder
                mi_welcome.set("name","welcome");
                mi_welcome.set("description","Default welcome page for guests");
                mi_welcome.set("pageStyle", DEFAULT_WELCOME_STYLE);
                mi_welcome.set("pageScript", DEFAULT_WELCOME_SCRIPT);
                mi_welcome.set("type","WELCOME");
                mi_welcome.set("active",true);
                mi_welcome.set("_allowRead", allRoles);
                mi_welcome.set("_allow", adminRoles);
                mi_welcome.save();

                MutableDocument mi_homed = con.create(TABLE_MENUITEM);  // Not added to menu, to support page Builder
                mi_homed.set("name","home-dark");
                mi_homed.set("description","Home Application page in dark mode");
                mi_homed.set("pageStyle", DEFAULT_DARK_STYLESHEET);
                mi_homed.set("pageScript", DEFAULT_HOME_SCRIPT);
                mi_homed.set("type","HOME");
                mi_homed.set("active",true);
                mi_homed.set("_allowRead", allRoles);
                mi_homed.set("_allow", adminRoles);
                mi_homed.set("useStyleFrom", mi_basestyle.getIdentity());
                mi_homed.save();

                MutableDocument mi_homel = con.create(TABLE_MENUITEM);  // Not added to menu, to support page Builder
                mi_homel.set("name","home-light");
                mi_homel.set("description","Home Application page in light mode");
                mi_homel.set("pageStyle", DEFAULT_LIGHT_STYLESHEET);
                mi_homel.set("pageScript", DEFAULT_HOME_SCRIPT);
                mi_homel.set("type","HOME");
                mi_homel.set("active",true);
                mi_homel.set("_allowRead", allRoles);
                mi_homel.set("_allow", adminRoles);
                mi_homel.set("useStyleFrom", mi_basestyle.getIdentity());
                mi_homel.save();

                MutableDocument mi_visuility = con.create(TABLE_MENUITEM);
                mi_visuility.set("name","Visuility");
                mi_visuility.set("description","Visuility browser");
                mi_visuility.set("classname","permeagility.web.Visuility");
                mi_visuility.set("type","SERVICE");
                mi_visuility.set("active",true);
                mi_visuility.set("_allowRead", adminAndStaffRoles);
                mi_visuility.set("_allow", adminRoles);
                mi_visuility.save();

                MutableDocument mi_visuilityData = con.create(TABLE_MENUITEM);
                mi_visuilityData.set("name","Visuility");
                mi_visuilityData.set("description","Visuility browser (data component)");
                mi_visuilityData.set("classname","permeagility.web.VisuilityData");
                mi_visuilityData.set("type","SERVICE");
                mi_visuilityData.set("active",true);
                mi_visuilityData.set("_allowRead", adminAndStaffRoles);
                mi_visuilityData.set("_allow", adminRoles);
                mi_visuilityData.save();

                // Build default menu
                ArrayList<Document> items = new ArrayList<>();
                items.add(mi_userRequest);
                items.add(mi_visuility);
                items.add(mi_schema);
                items.add(mi_query);
  //              items.add(mi_blank);
                items.add(mi_context);
                items.add(mi_pagebuilder);
                items.add(mi_password);

                // Add the menu items property to the menu
                MutableDocument menuDoc = con.queryDocument("SELECT FROM menu").modify();
                if (menuDoc != null && items.size() > 0) {
                    menuDoc.set("items",items);
                    menuDoc.save();
                } else {
                    installMessages.append(Weblet.paragraph("error","menu is null or no items to add"));
                }
            }

            System.out.println(TABLE_USERPROFILE+" ");
            DocumentType urTable = Setup.checkCreateTable(schema, TABLE_USERPROFILE, installMessages);
            //Setup.checkTableSuperclass(schema, urTable, "restricted", installMessages);
            Setup.checkCreateColumn(con, urTable, "name", Type.STRING, installMessages);
            Setup.checkCreateColumn(con, urTable, "password", Type.STRING, installMessages);
 
            if (!installMessages.isEmpty()) System.out.println("\n\nSetup repaired:\n"+installMessages);

     //       if (myTrans && con.getDb().isTransactionActive()) {
     //           System.out.println("Setup.checkInstallation committing transaction");
     //           con.commit();
     //       }
            System.out.print("Checking database...");
            con.update("CHECK DATABASE FIX");
            System.out.println("complete.");
            
            installMessages.append(Weblet.paragraph("success","Setup.checkInstallation completed at "+LocalDateTime.now()));
    
        } catch (Exception e) {
            System.out.println("- failed: "+e.getMessage());
            e.printStackTrace();
     //       if (myTrans && con.getDb().isTransactionActive()) {
     //           System.out.println("Setup.checkInstallation rolling back transaction");
     //           con.rollback();
     //       }
            return false;
        }
     //   if (!myTrans && !con.getDb().isTransactionActive()) {
     //       System.out.println("Setup.checkInstallation restarting transaction that was open when process began");
     //       con.begin();  // if was run inside transaction, make sure it is still inside a transaction
     //   }
        return true;
    }


    public static void createMenuItem(DatabaseConnection con, String name, String description, String classname, String addTo, String roles) {
        // Create menuitem
        //roles = (roles != null ? "#"+roles.replace(" ", "").replace(",",",#") : "");
        //Object menuItem = con.update("INSERT INTO "+Setup.TABLE_MENUITEM+" SET name='"+name+"', active=true"
        //        + ", description='"+description+"', classname='"+classname+"', _allowRead=["+roles+"]");
        List<RID> roleList = new ArrayList<RID>();
        String[] rlt = roles.split(",");
        for (String tok : rlt) {
            if (tok != null && !tok.isEmpty()) roleList.add(new RID(con.getDb(),"#"+tok));
        }
        MutableDocument menuItem = con.create(TABLE_MENUITEM)
            .set("name",name)
            .set("active",true)
            .set("type", "SERVICE")
            .set("description",description)
            .set("classname", classname)
            .set("_allowRead",roleList)
            .set("_allow", Security.getUserRoles(con))
            .save();
        // Add to the specified menu
        if (addTo != null && !addTo.equals("") && menuItem instanceof Document) {
            con.update("UPDATE #"+addTo+" SET items += "+((Document)menuItem).getIdentity().toString());
        }
        Server.tableUpdated(con, "menu");
    }

    public static boolean removeMenuItem(DatabaseConnection con, String classname, StringBuilder errors) {
        try {
            // Get the menu items for this class
            QueryResult menuItems = con.query("SELECT FROM menuItem WHERE classname = '"+classname+"'");
            List<String> menuIds = menuItems.getIds();
            for (String mi : menuIds) {
                // Find the menus it is in and remove it from them
                QueryResult menus = con.query("SELECT FROM menu WHERE items CONTAINS "+mi);
                for (String m : menus.getIds()) {
                    Object ret = con.update("UPDATE "+m+" REMOVE items = "+mi);
                    errors.append(Weblet.paragraph("success","Removed from menu "+m+": "+ret));
                }
            }
            // Delete menu item(s)
            for (String mi : menuIds) {
                Document d = con.get(mi);
                d.delete();
            }
            errors.append(Weblet.paragraph("success","Deleted menu item for "+classname));
            Server.tableUpdated(con, "menu");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Create message if it doesn't already exist */
    public static int checkCreateMessage(DatabaseConnection con, Document loc, String name, String description) {
        if (Message.get(con.getLocale(), name).equals(name)) {
            con.create(TABLE_MESSAGE).set("name",name).set("description",description).set("locale",loc).save();
            return 1;
        } else {
            return 0;  // Message exists
        }
    }

    public static String getMessages() {
        return installMessages.toString();
    }

    /** Add the column to the columns table to preserve initial order (always append only if not already there)  */
    public static void addColumnToColumns(DatabaseConnection con, String theClass, String propertyName) {
        Document qd = con.queryDocument("SELECT FROM columns WHERE name='"+theClass+"'");
        MutableDocument d;
        if (qd == null) {
            d = con.create(TABLE_COLUMNS);
            d.set("name",theClass);
        } else {
            d = qd.modify();
        }
        String cl = d.getString("columnList");
        if (cl == null || cl.equals("")) {
            d.set("columnList",propertyName);
        } else {
            String clc[] = cl.split(",");
            for (String cln : clc) {
                if (cln.trim().equals(propertyName) || cln.trim().equals("-"+propertyName)) {
                    return;  // Its already here
                }
            }
            if (!cl.contains(propertyName))
            d.set("columnList",d.getString("columnList")+","+propertyName);
        }
        d.save();
        return;
    }

    /** Remove the column from the columns table to preserve initial order (always append) */
    public static void removeColumnFromColumns(DatabaseConnection con, String theClass, String propertyName) {
        // Todo: should make this tolerate multiple entries in case of restricted by role
        MutableDocument d = con.queryDocument("SELECT FROM "+TABLE_COLUMNS+" WHERE name='"+theClass+"'").modify();
        if (d == null) {
            return;
        }
        String cl = d.getString("columnList");
        if (cl == null || cl.equals("")) {
            return;
        } else {
            String cols[] = cl.split(",");
            StringBuilder newCols = new StringBuilder();
            for (String c : cols) {  // Build a new list
                if (!c.equals(propertyName)) {  // Without the property in the list
                    if (newCols.length()>0) newCols.append(",");
                    newCols.append(c);
                }
            }
            d.set("columnList",newCols);
        }
        d.save();
        return;
    }

    public static void addTableToTableGroup(DatabaseConnection con, String theClass, String tableGroup) {
        MutableDocument d = (MutableDocument)con.queryDocument("SELECT FROM "+TABLE_TABLEGROUP+" WHERE name='"+tableGroup+"'");
        if (d == null) {
            d = con.create(TABLE_TABLEGROUP);
            d.set("name",tableGroup);
            d.set("_allowRead",Security.getUserRoles(con));
        }
        String tableList = d.getString("tables");
        if (tableList == null || tableList.equals("")) {
            d.set("tables",theClass);
        } else {
            String clc[] = tableList.split(",");
            for (String cln : clc) {
                if (cln.trim().equals(theClass) || cln.trim().equals("-"+theClass)) {
                    return;  // Its already here
                }
            }
            if (!tableList.contains(theClass)) {
                d.set("tables",d.getString("tables")+","+theClass);
            }
        }
        d.save();
        Server.tableUpdated(con, "tableGroup");
        return;
    }

    /** Should be called when removing a table to ensure the table in out of table group and columns */
    public static void removeTableFromAllTableGroups(DatabaseConnection con, String theClass) {
        QueryResult q = con.query("SELECT FROM "+TABLE_TABLEGROUP+" WHERE tables CONTAINSTEXT '"+theClass+"'");
        if (q == null || q.size()==0) {
            return;
        }
        for (Document id : q.get()) {  // For each document that may contain the reference to the class
            MutableDocument d = id.modify();
            String tableList = d.getString("tables");
            if (tableList == null || tableList.equals("")) {
                return;
            } else {
                String tabs[] = tableList.split(",");
                StringBuilder newTabs = new StringBuilder();
                for (String t : tabs) {  // Build a new list
                    if (!t.equals(theClass)) {  // Without the property in the list
                        if (newTabs.length()>0) newTabs.append(",");
                        newTabs.append(t);
                    }
                }
                d.set("tables",newTabs);
            }
            d.save();
        }
        return;
    }

    /** Create or update a constant - note this does not call Server.tableUpdated("constant") to avoid repeated constant updates */
    public static void checkCreateConstant(DatabaseConnection con, String classname, String description, String field, String value) {
        QueryResult qr = con.query("SELECT FROM "+TABLE_CONSTANT+" WHERE classname='"+classname+"' AND field='"+field+"'");
        if (qr != null && qr.size()>0) {
            MutableDocument cd = (MutableDocument)qr.get(0);
            if (cd != null) {
                cd.set("value",value).save();
            }
        } else {
            con.create(Setup.TABLE_CONSTANT).set("classname",classname).set("description",description).set("field",field).set("value",value).save();
        }
    }


    /** Check for the existence of a class property or add it This assumes you want a link type, otherwise the linkClass may have adverse effects */
    public static Property checkCreateColumn(DatabaseConnection con, DocumentType theClass, String propertyName, Type propertyType, DocumentType linkClass, StringBuilder errors) {
        Property p;
        try { 
            p = theClass.getProperty(propertyName);
        } catch(Exception e) {
            p = theClass.createProperty(propertyName, propertyType, linkClass.getName());
            errors.append(Weblet.serviceNotificationDiv(Weblet.paragraph("Schema update: Created link type property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()+" linked to "+linkClass.getName())));
        }
        //addColumnToColumns(con, theClass.getName(),propertyName);
        return p;
    }

    /** Check for the existence of a class property or add it and add to columns */
    public static Property checkCreateColumn(DatabaseConnection con, DocumentType theClass, String propertyName, Type propertyType, StringBuilder errors) {
        Property p;
        try { 
            p = theClass.getProperty(propertyName);
        } catch (Exception e) {
            p = theClass.createProperty(propertyName, propertyType);
            errors.append(Weblet.serviceNotificationDiv(Weblet.paragraph("Schema update: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name())));
        }
        if (p != null) {
            if (p.isMandatory()) {
                p.setMandatory(false);
                errors.append(Weblet.paragraph("Schema update: setting non-mandatory on "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
            }
            if (p.isNotNull()) {
                p.setNotNull(false);
                errors.append(Weblet.paragraph("Schema update: setting nullable on "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
            }
        }
        addColumnToColumns(con, theClass.getName(),propertyName);
        return p;
    }

    /** Check for the existence of a class or add it */
    public static DocumentType checkCreateTable(Schema schema, String className, StringBuilder errors) {
        DocumentType c = schema.getOrCreateDocumentType(className);
//        if (c == null) {
//            c = schema.createClass(className);
//            errors.append(Weblet.paragraph("Schema update: Created "+className+" class/table"));
//        }
        if (c == null) {
            errors.append(Weblet.paragraph("error","Schema update: Error creating "+className+" class/table"));
        }
      //  if (c != null) {
      //      if (c.isStrictMode()) {
      //          c.setStrictMode(false);
      //          errors.append(Weblet.paragraph("Schema update: Set non-strict "+className+" class/table"));
      //      }
      //  }
        return c;
    }

    /** Check for the existence of a class or add it */
    public static DocumentType checkCreateTable(DatabaseConnection con, Schema schema, String className, StringBuilder errors, String tableGroup) {
        DocumentType c = schema.getOrCreateDocumentType(className);
   //     if (c == null) {
   //         c = schema.createClass(className);
   //         errors.append(Weblet.paragraph("Schema update: Created "+className+" class/table"));
   //     }
        if (c == null) {
            errors.append(Weblet.paragraph("error","Schema update: Error creating "+className+" class/table"));
        }
   //     if (c != null) {
   //         if (c.isStrictMode()) {
   //             c.setStrictMode(false);
   //             errors.append(Weblet.paragraph("Schema update: Set non-strict "+className+" class/table"));
   //         }
   //     }
        //if (tableGroup != null) addTableToTableGroup(con, className,tableGroup);
        return c;
    }

    /** Check for the existence of a class's superclass or set it */
    public static boolean checkTableSuperclass(Schema schema, DocumentType type, String superTypeName, StringBuilder errors) {
        DocumentType s = schema.getType(superTypeName);
        if (s == null) {
            errors.append(Weblet.paragraph("error","Schema update: Cannot find supertype "+superTypeName+" to assign to class "+type.getName()));
            return false;
        }
        List<DocumentType> sc = type.getSuperTypes();
        boolean hasSuper = false;
        for (DocumentType c : sc) {
            if (c.getName().equals(superTypeName)) { hasSuper = true; }
        }
        if (!hasSuper) {
            type.addSuperType(s);
            errors.append(Weblet.paragraph("Schema update: Assigned supertype "+superTypeName+" to type "+type.getName()));
            return true;
        }
        return false;
    }

    /** Check existence of and Create role if not exist */
    public static Document checkCreateRole(DatabaseConnection con, String name, String mode, String role, StringBuilder installMessages) {
        Document r = con.queryDocument("SELECT FROM role WHERE name='"+name+"'");
        if (r == null) {
            r = (Document)con.update("INSERT INTO role SET name = '"+name+"', mode = '"+mode
                   + "', inheritedRole = (SELECT FROM role WHERE name = '"+role+"') RETURN @this");
            installMessages.append(Weblet.paragraph("CheckInstallation: Created "+name+" role ")+r.getIdentity());
        }
        return r;
    }

    /** Check existence of and Create user if not exist */
    public static Document checkCreateUser(DatabaseConnection con, String name, String password, String role, StringBuilder installMessages) {
        Document u = con.queryDocument("SELECT FROM user WHERE name='"+name+"'");
        if (u == null) {
            u = (Document)con.update("INSERT INTO user SET name = '"+name+"', password = '"+Security.digest(password)
                   + "', status = 'ACTIVE', roles = (SELECT FROM role WHERE name = '"+role+"') RETURN @this");
            installMessages.append(Weblet.paragraph("CheckInstallation: Created "+name+" user "+u.getIdentity()));
        }
        return u;
    }

    /** Check for the existence of a privilege or add it */
    public static boolean checkCreatePrivilege(DatabaseConnection con, RID identity, String access, String resource, StringBuilder errors) {
        Document existing = con.queryDocument("SELECT FROM privilege WHERE identity="+identity+" AND access='"+access+"' and resource='"+resource+"'");
        if (existing != null) {
            //errors.append("CheckCreatePrivilege: Privilege "+identity+" already has "+access+" to "+resource+"\n");
            return true;
        }
        try {
            Object rv = con.update("INSERT INTO privilege SET identity="+identity+", access='"+access+"', resource='"+resource+"'");
            if (rv != null && rv instanceof Document) {
                errors.append("CheckCreatePrivilege: Added privilege "+identity+" has "+access+" to "+resource+"\n");
                return true;
            } else {
                System.out.println("CheckCreatePrivilege: Not sure about this result: "+rv);
                return false;
            }
        } catch (Exception e) {
            errors.append("CheckCreatePrivilege: Failed to insert privilege "+identity+" has "+access+" to "+resource+": "+e.getMessage()+"\n");
            //e.printStackTrace();
            return false;
        }
    }

    /** Drop a table */
    public static void dropTable(DatabaseConnection con, String typename) { dropTable(con, typename, null); }
    public static boolean dropTable(DatabaseConnection con, String typename, StringBuilder errors) {
        try {
            //con.update("ALTER TYPE "+classname+" SUPERTYPE NULL");  // Clear superclasses first otherwise will fail
            Schema schema = con.getSchema();
            schema.dropType(typename);
            Setup.removeTableFromAllTableGroups(con, typename);
            QueryResult qr = con.query("SELECT FROM columns WHERE name='"+typename+"'");
            List<String> colIds = qr.getIds();
            for (String colId : colIds) {
                MutableDocument d = con.get(colId).modify();
                if (d != null) d.delete();
            }
            DatabaseConnection.rowCountChanged(typename);
            if (errors != null) errors.append(Weblet.paragraph("success","Table dropped: "+typename));
            return true;
        } catch (Exception e) {
            if (errors != null) errors.append(Weblet.paragraph("error","Table "+typename+" could not be dropped: "+e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }

    public static final String DEFAULT_BASE_STYLE = """
<script type="text/javascript" src="/js/_hyperscript.min.js"></script>
<script type="text/javascript" src="/js/htmx.min.js"></script>
<script src="/js/sorttable.js"></script>
<script  type='text/javascript' src="/js/Sortable.min.js"></script>
<script  type='text/javascript' src="/js/d3.min.js"></script>
<link rel="stylesheet" type="text/css" href="/js/codemirror/lib/codemirror.css" />
<link rel="stylesheet" type="text/css" href="/js/codemirror/theme/ambiance.css" />
<link rel="stylesheet" type="text/css" href="/js/codemirror/addon/hint/show-hint.css" />
<link rel="stylesheet" type="text/css" href="/js/codemirror/addon/dialog/dialog.css" />
<link rel="stylesheet" type="text/css" href="/js/codemirror/addon/tern/tern.css" />
<script type="text/javascript" src="/js/codemirror/lib/codemirror.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/javascript/javascript.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/clike/clike.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/css/css.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/r/r.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/xml/xml.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/sql/sql.js"></script>
<script type="text/javascript" src="/js/codemirror/mode/htmlmixed/htmlmixed.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/dialog/dialog.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/tern/tern.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/hint/show-hint.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/hint/javascript-hint.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/hint/css-hint.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/lint/lint.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/lint/javascript-lint.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/lint/css-lint.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/selection/active-line.js"></script>
<script type="text/javascript" src="/js/codemirror/addon/edit/matchbrackets.js"></script>
<script  type='text/javascript' src="/js/split.min.js"></script>     
""";

    public static final String DEFAULT_BASE_SCRIPT = """
<p>This is the base style script where the various libraries are included</p>            
""";
    public static final String DEFAULT_LIGHT_STYLESHEET = """
<style type='text/css'>
/* This is the light PermeAgility stylesheet */

/* Reset from the default browser styles */
*,*::before,*::after{ 
    box-sizing: border-box; margin: 0px; padding: 0px; scroll-behavior: smooth; 
}
html { background-color: #fff; }
body { font-family: verdana,sans-serif; height: 100%; min-height:100%;
        color: black; 
}

/* positioning of header items */
img.headerlogo { width: 90px; left: 20px; top: 15px; position: absolute; border: none; user-select: none; }
a.headerlogo:hover { text-decoration: none; background-color: transparent;}

#header { position: absolute; top: 0px; left: 0px; right: 0px; height: 70px;
            background-image: linear-gradient(to left, white, #aaa) !important;   }

#headertitle { font-size: 0.75em; position: absolute; top: 20px; left: 120px; }
#headerservice { font-size: 1em; position: absolute; top: 40px; left: 120px; }
#headertime { font-size: 0.75em; position: absolute; top: 5px; right: 5px; }
#headeruser { font-size: 0.75em; position: absolute; top: 50px; right: 5px; }
#service { position: absolute; top: 70px; bottom: 0px; width: 100%;
    background-image: linear-gradient(to right, white, #ccc) !important; 
    overflow-y: auto; padding: 0.5em;
}

/* anchor tags appearance */
a { text-decoration: none; user-select: none; }
a:hover { text-decoration: underline; }
a, a.menuitem, a.popuplink {color: black;}
a.menuitem:link { text-decoration: none; }
a.menuitem:visited { text-decoration: none; }
a:hover, a.menuitem:hover, a.popuplink:hover { 
    text-decoration: none; color: black; 
    background: radial-gradient(ellipse, darkorange, white);
}
.selected { font-weight: 600; text-decoration: underline; }

/* Lists */
ol, ul { margin-left: 2.5em; }
a.list-delete { margin-left: 1em; }

/* labels and tables */
.label { color: black; }
td.label { text-align: right; vertical-align: top; font-size: small;
    color: black; font-weight: bold;
    border-radius: 6px 6px 6px 6px;
    background: linear-gradient(to right, white, #ccc);
        border: none;  padding: 0px 5px 0px 25px;}
input, textarea, select {
    background-color : #fff;
    color: black;
}
input.number { text-align: right; }
th { font-weight: bold; background-color: lightgray;
    border-radius: 8px 8px 0px 0px; 
}
tbody { overflow-y: scroll; }
tr { background-color: #fff; vertical-align: top; }
tr:nth-of-type(2n) { background-color: #eee; }
tr.clickable { vertical-align: top; }
tr.clickable:hover {
    background: radial-gradient(ellipse, darkorange, white);
}
tr.footer { font-weight: bold; }
td { text-align: left;  }
td.number { text-align: right; }
td.total { text-align: right; font-weight:bolder; normal: solid thin black; }
div.tabpanel { text-align: center; }
div.tableGroups { display: flex; flex-direction: row; flex-wrap: wrap; }
div.tableGroup { display: flex; flex-direction: column; align-self: first baseline; padding: 5px;}

/* Sortable tables */
table.sortable thead { color: black; font-weight: bold; cursor: default; }
.sortable thead th { position: sticky; top: 0; }
.sortable tfoot { position: sticky; bottom: 0; background-color: #eee; opacity: 0.85;}

/* paragraphs types */
p.menuheader {  color: white;  margin: 0.2em 0em 0em 0em; }
P.banner { 
    font-weight: bold;  text-align:center;  color: black;
    margin: 0.2em 0em 0em 0em;
    page-break-after: avoid;
}
P.error {
    font-weight: bold; text-align:center;
    color: black; margin: 0.2em 0em 0em 0em;
    border-radius: 6px 6px 6px 6px;
    background: radial-gradient(ellipse, rgb(117,0,0), white);
}
P.warning {
    font-weight: bold; text-align:center;
    color: black; margin: 0.2em 0em 0em 0em;
    border-radius: 6px 6px 6px 6px;
    background-color: #FFCC00;
    background: radial-gradient(ellipse, #FFCC00, white);
}
P.success { background-color: #white;
    font-weight: bold; text-align:center;
    color: black; margin: 0.2em 0em 0em 0em;
    border-radius: 6px 6px 6px 6px;
    background: radial-gradient(ellipse, #1d5e1f, white);
}
P.nochange { background-color: rgb(0,0,200);
    font-weight: bold; text-align:center;
    color: black; margin: 0.2em 0em 0em 0em;
}
*.alert { background-color: #FF6666; }
*.new { background-color: #FFFF9C }
*.changed { background-color: #DEBDDE }
*.warning { background-color: #FF9900; }
P.delete { text-align:right; }
P.bannerleft { background-color: #303b43;
        font-weight: bold; text-align:left;
        color: black; margin: 0.2em 0em 0em 0em;
}

/* For Code editor */
div.CodeMirror { height: auto; z-index: 0; overflow-x: hidden; overflow-y: hidden; }

/* For split.js - splitter bar */
.split {
box-sizing: border-box;
overflow-y: auto;
overflow-x: clip;
}
.gutter.gutter-horizontal { cursor: col-resize; }
.gutter.gutter-vertical { cursor: row-resize; }
.gutter.gutter-horizontal:hover { background-color: #444; }
.gutter.gutter-vertical:hover { background-color: #444; }
.split.split-horizontal, .gutter.gutter-horizontal {
height: 100%;
float: left;
}
.noscroll { overflow-y: clip; }
/* .split.split-vertical, .gutter.gutter-vertical {
height: 50%;
float: top;
} */
.gutter.gutter-vertical { background-image: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB4AAAAFAQMAAABo7865AAAABlBMVEVHcEzMzMzyAv2sAAAAAXRSTlMAQObYZgAAABBJREFUeF5jOAMEEAIEEFwAn3kMwcB6I2AAAAAASUVORK5CYII=');
background-repeat: no-repeat;
background-position: center;
}
.gutter.gutter-horizontal { background-image: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAeCAYAAADkftS9AAAAIklEQVQoU2M4c+bMfxAGAgYYmwGrIIiDjrELjpo5aiZeMwF+yNnOs5KSvgAAAABJRU5ErkJggg==');
background-repeat: no-repeat;
background-position: center;
}

/* For visuility */
.nodeTitle { fill: white; filter: url(#drop-shadow); font-size: small; }
g:not(.selected) { stroke: none; }
g.selected { stroke: yellow; }
.link { fill: lightgray; stroke: gray; }
rect.node { opacity: 0.5; }
rect.selection { opacity:0.8; fill: none; stroke: white; stroke-width: 4px; stroke-dasharray: 5,5; }

/* For popup modal forms */
.popbox {padding: 0.2em 0.2em; border-radius: 3px; }
.modal {
    background-color: #ccc;
    border-radius: 6px;
    position: fixed;
    transition: all 0.8s;
    visibility: hidden;
    opacity: 0; z-index: 1;
}
.pop-content { padding: 0.75em 0.75em; }
.modal:target {visibility: visible; opacity: 1; }
.box-close {float: right; font-size: 1.5em; }

/* For Menu/Navigator popup */
.nav-button {
    position: relative; display: flex; flex-direction: column;
    justify-content: center;  -webkit-appearance: none;
    border: 0; margin-left: 10px;
    background: transparent; border-radius: 0;
    height: 40px; width: 25px;
    cursor: pointer; pointer-events: auto;
    touch-action: manipulation; user-select: none;
    -webkit-tap-highlight-color: rgba(0,0,0,0);
}
.icon-bar { display: block; width: 100%; height: 3px; background: #666; transition: .5s; border-radius: 3px;}
.icon-bar + .icon-bar { margin-top: 5px; }
#nav-container { position: fixed; height: 100vh; width: 100%; pointer-events: none; }
#nav-container * { visibility: visible; }
#nav-container:focus-within .bg { visibility: visible; opacity: .6; }
#nav-container:focus-within .nav-button { pointer-events: none; }
#nav-container:focus-within .icon-bar:nth-of-type(1) { transform: translate3d(0,8px,0) rotate(45deg); }
#nav-container:focus-within .icon-bar:nth-of-type(2) { opacity: 0; }
#nav-container:focus-within .icon-bar:nth-of-type(3) { transform: translate3d(0,-8px,0) rotate(-45deg); }
#nav-container:focus-within #nav-content { transform: none; }
#nav-container .bg {
    position: absolute; top: 70px; left: 0;
    width: 100%; height: calc(100% - 70px);
    visibility: hidden; opacity: 0;
    transition: .5s; background: #333;
}
#nav-content ul { height: 100%; display: flex; flex-direction: column; margin-left: 0px; list-style: none; }
#nav-content li:not(.small) + .small { margin-top: auto; }
#nav-content {  
    margin-top: 40px;  padding: 10px; width: 90%; max-width: 170px;
    position: absolute; top: 0; left: 0; height: calc(100% - 70px);
    background: #666; opacity: 0.9; pointer-events: auto;
    -webkit-tap-highlight-color: rgba(0,0,0,0);
    transform: translateX(-100%);
    transition: .5s; 
    will-change: transform;  contain: paint;
}

.small { display: flex; align-self: center; }
.small a { font-size: 12px; font-weight: 400; color: #888; }
.small a + a { margin-left: 15px; }

/* For PageBuilder preview */
iframe.previewFrame { width: calc(100% - 10px); height: calc(100vh - 110px); }

/* For when printing */
@media print { BODY { font-size: 6pt; margin: 1em; } }
@media print { #menu {display: none; } }
@media print { #service {position: absolute; top: 0.5in; left: auto;} }
@media print { TABLE.data { border: solid thin;  page-break-inside: avoid;} }
@media print { TD.header { border: solid thin; } }
@media print { *.new { border: dotted thin; } }
@media print { *.alert { border: solid medium; border-color: #FF0000;} }
@media print { *.changed { border: double thin; } }
@media print { *.button { display: none; } }
</style>

""";

public static final String DEFAULT_HOME_SCRIPT = """
<div id="header" hx-trigger="load" hx-get="/Header" 
    hx-swap="innerHTML"></div>

<div id="service">
   <PermeAgility table="news" order="dateline desc"
               where="(archive IS NULL OR archive=false)
               AND (locale IS NULL OR locale.name='${locale}')
               AND ${_allowRead}">
   <div class="card-content">
       <h2>${news.name}</h2>
       <p style="font-size:8pt">${news.dateline} ${locale} #${news.rid}</p>
       <p>${news.description}</p>
   </div>
   </PermeAgility>
</div>

<div id="nav-container">
   <div id="underlay" class="bg"></div>
   <div id="nav-button" class="nav-button" tabindex="0">
   <span class="icon-bar"></span>
   <span class="icon-bar"></span>
   <span class="icon-bar"></span>
   </div>
   <div id="nav-content" hx-get="/Menu?TARGET=service" 
        hx-trigger="load" hx-swap="innerHTML" tabindex="0"></div>
</div>

""";

public static final String DEFAULT_DARK_STYLESHEET = """
<style type='text/css'>
/* This is the dark PermeAgility stylesheet */

/* Reset from the default browser styles */
*,*::before,*::after{ 
    box-sizing: border-box; margin: 0px; padding: 0px; scroll-behavior: smooth; 
}
html { background-color: #111; }
body { font-family: verdana,sans-serif; height: 100%; min-height:100%;
        color: white; 
}
::-webkit-scrollbar { background: #333; }  /* these are for Chrome's ugly scrollbars */
::-webkit-scrollbar-thumb { background: #444; }

/* positioning of header items */
img.headerlogo { width: 90px; left: 20px; top: 15px; position: absolute; border: none; user-select: none; }
a.headerlogo:hover { text-decoration: none; background-color: transparent;}

#header { position: absolute; top: 0px; left: 0px; right: 0px; height: 70px;
            background-image: linear-gradient(to left, black, #444444) !important;   }

#headertitle { font-size: 0.75em; position: absolute; top: 20px; left: 120px; }
#headerservice { font-size: 1em; position: absolute; top: 40px; left: 120px; }
#headertime { font-size: 0.75em; position: absolute; top: 5px; right: 5px; }
#headeruser { font-size: 0.75em; position: absolute; top: 50px; right: 5px; }
#service { position: absolute; top: 70px; bottom: 0px; width: 100%;
    background-image: linear-gradient(to right, black, #444444) !important; 
    overflow-y: auto; padding: 0.5em;
}

/* anchor tags appearance */
a { text-decoration: none; user-select: none; }
a:hover { text-decoration: underline; }
a, a.menuitem, a.popuplink {color: lightgray;}
a.menuitem:link { text-decoration: none; }
a.menuitem:visited { text-decoration: none; }
a:hover, a.menuitem:hover, a.popuplink:hover { 
    text-decoration: none; color: white;
    background: radial-gradient(ellipse, darkorange, black);
}
.selected { font-weight: 600; text-decoration: underline; }

/* Lists */
ol, ul { margin-left: 2.5em; }
a.list-delete { margin-left: 1em; }

/* labels and tables */
.label { color: black; }
td.label { text-align: right; vertical-align: top; font-size: small;
    color: white; font-weight: bold;
    border-radius: 6px 6px 6px 6px;
    background: linear-gradient(to right, black, #444);
        border: none;  padding: 0px 5px 0px 25px;}
input, textarea, select {
    background-color : #222;
    color: white;
}
input.number { text-align: right; }
th {  border-radius: 8px 8px 0px 0px;
    background-color: black; color: white;
        background: radial-gradient(ellipse, #339999, black);
}
tbody { overflow-y: scroll; }
tr { background-color: #222; vertical-align: top; }
tr:nth-of-type(2n) { background-color: #2a2a2a; }
tr.clickable { vertical-align: top; }
tr.clickable:hover {
    background: radial-gradient(ellipse, darkorange, black);
}
tr.footer { font-weight: bold; }
td { text-align: left;  }
td.number { text-align: right; }
td.total { text-align: right; font-weight:bolder; normal: solid thin black; }
div.tabpanel { text-align: center; }
div.tableGroups { display: flex; flex-direction: row; flex-wrap: wrap; }
div.tableGroup { display: flex; flex-direction: column; align-self: first baseline; padding: 5px;}

/* Sortable tables */
table.sortable thead { color: white; font-weight: bold; cursor: default; }
.sortable thead th { position: sticky; top: 0; }
.sortable tfoot { position: sticky; bottom: 0; background-color: #222; opacity: 0.85;}

/* paragraphs types */
p.menuheader {  color: white;  margin: 0.2em 0em 0em 0em; }
P.banner { background-color: #336666;
        font-weight: bold;  text-align:center;  color: white;
        margin: 0.2em 0em 0em 0em;
        page-break-after: avoid;
        border-radius: 8px 8px 8px 8px;
        background: radial-gradient(ellipse, #336666, black);
}
P.error {
    font-weight: bold; text-align:center;
    color: white; margin: 0.2em 0em 0em 0em;
    border-radius: 6px 6px 6px 6px;
    background: radial-gradient(ellipse, rgb(117,0,0), black);
}
P.warning {
    font-weight: bold; text-align:center;
    color: white; margin: 0.2em 0em 0em 0em;
    border-radius: 6px 6px 6px 6px;
    background-color: #FFCC00;
    background: radial-gradient(ellipse, #FFCC00, black);
}
P.success { background-color: #1d5e1f;
    font-weight: bold; text-align:center;
    color: white; margin: 0.2em 0em 0em 0em;
    border-radius: 6px 6px 6px 6px;
    background: radial-gradient(ellipse, #1d5e1f, black);
}
P.nochange { background-color: rgb(0,0,200);
    font-weight: bold; text-align:center;
    color: white; margin: 0.2em 0em 0em 0em;
}
*.alert { background-color: #FF6666; }
*.new { background-color: #FFFF9C }
*.changed { background-color: #DEBDDE }
*.warning { background-color: #FF9900; }
P.delete { text-align:right; }
P.bannerleft { background-color: #303b43;
        font-weight: bold; text-align:left;
        color: white; margin: 0.2em 0em 0em 0em;
}

/* For Code editor */
div.CodeMirror { height: auto; z-index: 0; overflow-x: hidden; overflow-y: hidden; }

/* For split.js - splitter bar */
.split {
box-sizing: border-box;
overflow-y: auto;
overflow-x: clip;
}
.gutter.gutter-horizontal { cursor: col-resize; }
.gutter.gutter-vertical { cursor: row-resize; }
.gutter.gutter-horizontal:hover { background-color: #444; }
.gutter.gutter-vertical:hover { background-color: #444; }
.split.split-horizontal, .gutter.gutter-horizontal {
height: 100%;
float: left;
}
.noscroll { overflow-y: clip; }
/* .split.split-vertical, .gutter.gutter-vertical {
height: 50%;
float: top;
} */
.gutter.gutter-vertical { background-image: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB4AAAAFAQMAAABo7865AAAABlBMVEVHcEzMzMzyAv2sAAAAAXRSTlMAQObYZgAAABBJREFUeF5jOAMEEAIEEFwAn3kMwcB6I2AAAAAASUVORK5CYII=');
background-repeat: no-repeat;
background-position: center;
}
.gutter.gutter-horizontal { background-image: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAeCAYAAADkftS9AAAAIklEQVQoU2M4c+bMfxAGAgYYmwGrIIiDjrELjpo5aiZeMwF+yNnOs5KSvgAAAABJRU5ErkJggg==');
background-repeat: no-repeat;
background-position: center;
}

/* For visuility */
.nodeTitle { fill: white; filter: url(#drop-shadow); font-size: small; }
g:not(.selected) { stroke: none; }
g.selected { stroke: yellow; }
.link { fill: lightgray; stroke: gray; }
rect.node { opacity: 0.5; }
rect.selection { opacity:0.8; fill: none; stroke: white; stroke-width: 4px; stroke-dasharray: 5,5; }

/* For popup modal forms */
.popbox {padding: 0.2em 0.2em; border-radius: 3px; }
.modal {
    background-color: #333;
    border-radius: 6px;
    position: fixed;
    transition: all 0.8s;
    visibility: hidden;
    opacity: 0; z-index: 1;
}
.pop-content { padding: 0.75em 0.75em; }
.modal:target {visibility: visible; opacity: 1; }
.box-close {float: right; font-size: 1.5em; }

/* For Menu/Navigator popup */
.nav-button {
    position: relative; display: flex; flex-direction: column;
    justify-content: center;  -webkit-appearance: none;
    border: 0; margin-left: 10px;
    background: transparent; border-radius: 0;
    height: 40px; width: 25px;
    cursor: pointer; pointer-events: auto;
    touch-action: manipulation; user-select: none;
    -webkit-tap-highlight-color: rgba(0,0,0,0);
}
.icon-bar { display: block; width: 100%; height: 3px; background: #aaa; transition: .5s; border-radius: 3px;}
.icon-bar + .icon-bar { margin-top: 5px; }
#nav-container { position: fixed; height: 100vh; width: 100%; pointer-events: none; }
#nav-container * { visibility: visible; }
#nav-container:focus-within .bg { visibility: visible; opacity: .6; }
#nav-container:focus-within .nav-button { pointer-events: none; }
#nav-container:focus-within .icon-bar:nth-of-type(1) { transform: translate3d(0,8px,0) rotate(45deg); }
#nav-container:focus-within .icon-bar:nth-of-type(2) { opacity: 0; }
#nav-container:focus-within .icon-bar:nth-of-type(3) { transform: translate3d(0,-8px,0) rotate(-45deg); }
#nav-container:focus-within #nav-content { transform: none; }
#nav-container .bg {
    position: absolute; top: 70px; left: 0;
    width: 100%; height: calc(100% - 70px);
    visibility: hidden; opacity: 0;
    transition: .5s; background: #333;
}

#nav-content ul { height: 100%; display: flex; flex-direction: column; margin-left: 0px; list-style: none; }
#nav-content li:not(.small) + .small { margin-top: auto; }
#nav-content {  
    margin-top: 40px;  padding: 10px; width: 90%; max-width: 170px;
    position: absolute; top: 0; left: 0; height: calc(100% - 70px);
    background: #333; opacity: 0.9; pointer-events: auto;
    -webkit-tap-highlight-color: rgba(0,0,0,0);
    transform: translateX(-100%);
    transition: .5s; 
    will-change: transform;  contain: paint;
}

.small { display: flex; align-self: center; }
.small a { font-size: 12px; font-weight: 400; color: #888; }
.small a + a { margin-left: 15px; }

/* For PageBuilder preview */
iframe.previewFrame { width: calc(100% - 10px); height: calc(100vh - 110px); }

/* For when printing */
@media print { BODY { font-size: 6pt; margin: 1em; } }
@media print { #menu {display: none; } }
@media print { #service {position: absolute; top: 0.5in; left: auto;} }
@media print { TABLE.data { border: solid thin;  page-break-inside: avoid;} }
@media print { TD.header { border: solid thin; } }
@media print { *.new { border: dotted thin; } }
@media print { *.alert { border: solid medium; border-color: #FF0000;} }
@media print { *.changed { border: double thin; } }
@media print { *.button { display: none; } }
</style>
""";

public static final String DEFAULT_WELCOME_STYLE = """
<script type="text/javascript" src="/js/htmx.min.js"></script>
<script type="text/javascript" src="/js/_hyperscript.min.js"></script>
<style type='text/css'>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: sans-serif; }
a { color: lightblue; }
h4 { margin-top: 1.5em; }

.header, .footer {
    background-color: #222;  color: white; height: 35px;
    justify-content: center; align-items: center;
}
img.headerlogo { width: 50px; left: 5px; top: 5px; position: absolute; border: none; user-select: none; }
a.headerlogo:hover { text-decoration: none; background-color: transparent;}

#header { top: 0px; left: 0px; right: 0px; height: 35px; z-index: -1;
        background-image: linear-gradient(to left, black, #444444) !important;   }
#headertitle { font-size: 0.75em; text-align: center; }
#headerservice { display: none; }
#headertime { display: none; }
#headeruser { font-size: 0.75em; text-align: right; }

.card {
    width: 100vw; height: 100vh;
    background: rgb(20, 50, 100, 0.8);  color: white;
    display: flex; align-items: center;
    justify-content: center;
    position: sticky; top: 0;
}

.card img {
    position: absolute; z-index: 1;
    left: 0; top: 0; width: 100%; height: 100%;
    object-fit: cover;
    filter: brightness(0.5);
}

.card-content {
    position: absolute; z-index: 2;
    width: 50%; height: 100%;
    margin: 20px;
    display: flex; flex-direction: column;
    justify-content: center;
    align-items: center;
}
</style>
""";

public static final String DEFAULT_WELCOME_SCRIPT = """
<div id="header" class="header" 
    hx-trigger="load" hx-get="/Header" hx-swap="innerHTML">
</div>
<div>
    <PermeAgility table="news" order="dateline desc"
        where="(archive IS NULL OR archive=false) 
            AND (locale IS NULL OR locale.name='${locale}')
            AND ${_allowRead}">
        <div class="card">
            <img src="https://source.unsplash.com/random/800x800" alt="" />
            <div class="card-content">
                <h1>${news.name}</h1>
                <h4>${news.description}</h4>
            </div>
        </div>
    </PermeAgility>
</div>
<div class="footer">
    <center><small><p>
    &copy;<span _="on load js return new Date().getFullYear() end then put it into me"></span>
    <a HREF=http://www.permeagility.com>PermeAgility Incorporated</a>
    </p></small></center>
</div>

""";

    // This is a test of the db setup independent of the server
    public static void mainx(String[] args) {
        Database database = null;
        
        try {
            database = new Database("tdb", "admin", "admin");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (database.isConnected()) {
            DatabaseConnection dbc = database.getConnection();
            try {
                DocumentType dt = dbc.getSchema().getType("menuItem");
                System.out.println("Update script has already run");
            } catch (Exception e) {
                System.out.println("Could not find menuItem");
                System.out.println("Running update script");
                dbc.updateScript(schemaScript);
                System.out.println("Update script complete");
            }
            database.close();
        }
    }

    private static ArcadeDBServer server = null;
    private static ServerDatabase sdb = null;
    private static ContextConfiguration config = new ContextConfiguration();

    public static void main(String[] args) {
        String url = "tdb";
        try {
            server = new ArcadeDBServer(config);
            server.start();
        } catch (Exception e) {
            System.out.println("Error at start server/create db "+e.getMessage());
            //e.printStackTrace();
        }
        try {
            if (!server.existsDatabase(url)) {
                sdb = server.createDatabase(url, MODE.READ_WRITE);
                if (sdb.isOpen()) {
                    System.out.println("Database is open");
                }
            } else {
                sdb = server.getDatabase(url);
            }
        } catch (Exception e) {            
            System.out.println("Error at getDatabase "+e.getMessage());
            //e.printStackTrace();
        }

        if (sdb.isOpen()) {
            try {
                DocumentType dt = sdb.getSchema().getType("menuItem");
                DocumentType dt2 = sdb.getSchema().getType("identity");
                System.out.println("\n\nUpdate script has already run\n\n");
            } catch (Exception e) {
                System.out.println("/nCould not find menuItem or identity: "+e.getMessage());
                System.out.println("Running update script");
                sdb.command("sqlscript",schemaScript);
                System.out.println("Update script complete");
            }
            //sdb.close();  // unsupported operation
        }
        server.stop();
    }

}
