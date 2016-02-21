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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.ORule.ResourceGeneric;
import com.orientechnologies.orient.core.metadata.security.OSecurity;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.HashSet;
import java.util.Set;

import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Server;
import permeagility.web.Weblet;

public class Setup {

    static StringBuilder installMessages = new StringBuilder();

    public static boolean RESTRICTED_BY_ROLE = true;

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

    public static boolean checkInstallation(DatabaseConnection con) {
        try {
            OSchema oschema = con.getSchema();
            OSecurity osecurity = con.getSecurity();
            System.out.print("DatabaseSetup.checkInstallation ");

            // Setup roles lists for use later on in this script
            Set<ODocument> allRoles = new HashSet<>();			
            Set<ODocument> allRolesButGuest = new HashSet<>();			
            Set<ODocument> adminRoles = new HashSet<>();			
            Set<ODocument> adminAndWriterRoles = new HashSet<>();			
            Set<ODocument> readerRoles = new HashSet<>();			
            Set<ODocument> writerRoles = new HashSet<>();			
            Set<ODocument> guestRoles = new HashSet<>();			
            Set<ODocument> userRoles = new HashSet<>();			
            List<ODocument> qr = osecurity.getAllRoles();
            for (ODocument roleDoc : qr) {
                ORole role = osecurity.getRole(roleDoc);
                allRoles.add(role.getDocument());
                if (!role.getName().equals("guest")) allRolesButGuest.add(role.getDocument());
                if (role.getName().equals("admin")) adminRoles.add(role.getDocument());
                if (role.getName().equals("reader")) readerRoles.add(role.getDocument());
                if (role.getName().equals("writer")) writerRoles.add(role.getDocument());
                if (role.getName().equals("guest")) guestRoles.add(role.getDocument());
                if (role.getName().equals("user")) userRoles.add(role.getDocument());
                if (role.getName().equals("admin") || role.getName().equals("writer")) adminAndWriterRoles.add(role.getDocument());
            }

            //ODocument adminUser = con.queryDocument("SELECT FROM OUser WHERE name='admin'");  // Not actually used
            ODocument guestUser = con.queryDocument("SELECT FROM OUser WHERE name='guest'");

            if (guestRoles.isEmpty()) {
                ODocument guestRole = (ODocument)con.update("insert into ORole set name = 'guest', mode = 0");
                guestRoles.add(guestRole);
                allRoles.add(guestRole);
                installMessages.append(Weblet.paragraph("CheckInstallation: Created guest role"));
            }
            if (userRoles.isEmpty()) {
                ODocument userRole = (ODocument)con.update("insert into ORole set name = 'user', mode = 0");
                userRoles.add(userRole);
                allRoles.add(userRole);
                allRolesButGuest.add(userRole);
                installMessages.append(Weblet.paragraph("CheckInstallation: Created user role"));
            }
            if (guestUser == null) {
                guestUser = (ODocument)con.update("insert into OUser set name = 'guest', password = 'guest', status = 'ACTIVE', roles = (select from ORole where name = 'guest')");
                installMessages.append(Weblet.paragraph("CheckInstallation: Created guest user ")+guestUser.getIdentity());
            }

            // Verify the minimum privileges for the guest and user role
            checkCreatePrivilege(con,"guest",ResourceGeneric.DATABASE,null,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.COMMAND,null,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.SCHEMA,null,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLUSTER,null,2,installMessages);

            checkCreatePrivilege(con,"user",ResourceGeneric.DATABASE,null,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.COMMAND,null,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.SCHEMA,null,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLUSTER,null,3,installMessages);


            // Add ability for reader/writer to read from systemclusters and ORole
            
            // For some reason, the hasRule function thinks these already exist
//            checkCreatePrivilege(con,"reader",ResourceGeneric.SYSTEM_CLUSTERS,null,2,installMessages);
//            checkCreatePrivilege(con,"reader",ResourceGeneric.CLUSTER,"ORole",2,installMessages);
//            checkCreatePrivilege(con,"writer",ResourceGeneric.SYSTEM_CLUSTERS,null,2,installMessages);
//            checkCreatePrivilege(con,"writer",ResourceGeneric.CLUSTER,"ORole",2,installMessages);

            // So we have to do this manually
            con.update("GRANT READ on database.systemclusters TO reader");
            con.update("GRANT READ on database.systemclusters TO writer");
            con.update("GRANT READ on database.cluster.ORole TO reader");
            con.update("GRANT READ on database.cluster.ORole TO writer");

            // columns must be first as it will receive the properties as they are created by checkCreateProperty
            System.out.print(TABLE_COLUMNS+" ");  
            OClass columnsTable = Setup.checkCreateTable(oschema, TABLE_COLUMNS, installMessages);
            // Need to create first two column manually then we can call the function that adds it to the new columns
            if (columnsTable != null && !columnsTable.existsProperty("name")) {  
                columnsTable.createProperty("name", OType.STRING);
            }
            if (columnsTable != null && !columnsTable.existsProperty("columnList")) {
                columnsTable.createProperty("columnList", OType.STRING);
            }
            if (columnsTable.count() == 0) { // If first time, setup columns for 'O' tables
                con.create(TABLE_COLUMNS).field("name","OUser").field("columnList","name,status,roles").save();				
                con.create(TABLE_COLUMNS).field("name","ORole").field("columnList","name,mode,inheritedRole,rules").save();	
                con.create(TABLE_COLUMNS).field("name","OFunction").field("columnList","name,language,idempotent,parameters,code").save();				
                con.create(TABLE_COLUMNS).field("name","OSchedule").field("columnList","name,function,arguments,rule,status,starttime,start").save();				
            }

            // This will ensure they are added to columns table in proper order
            Setup.checkCreateColumn(con, columnsTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, columnsTable, "columnList", OType.STRING, installMessages);

            // Create early so we can automatically add tables to them
            System.out.print(TABLE_TABLEGROUP+" ");
            OClass tableGroupTable = Setup.checkCreateTable(oschema, TABLE_TABLEGROUP, installMessages);
            Setup.checkTableSuperclass(oschema, tableGroupTable, "ORestricted", installMessages);
            Setup.checkCreateColumn(con, tableGroupTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, tableGroupTable, "tables", OType.STRING, installMessages);

            if (tableGroupTable.count() == 0) {
                    con.create(TABLE_TABLEGROUP).field("name","Application").field("tables","news,columns,constant,locale,pickList,pickValues,menu,menuItem,message,style,tableGroup,userProfile,auditTrail,-thumbnail").field("_allowRead", adminRoles.toArray()).save();
                    con.create(TABLE_TABLEGROUP).field("name","System").field("tables","ORole,OUser,OFunction,OSchedule,-ORIDs,-E,-V,-_studio").field("_allowRead", adminRoles.toArray()).save();
                    con.create(TABLE_TABLEGROUP).field("name","Content").field("tables","").field("_allowRead", allRoles.toArray()).save();
                    con.create(TABLE_TABLEGROUP).field("name","Plus").field("tables","").field("_allowRead", adminRoles.toArray()).save();
            }

            System.out.print(TABLE_THUMBNAIL+" ");
            OClass thumbnailTable = Setup.checkCreateTable(oschema, TABLE_THUMBNAIL, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "table", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "column", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "id", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "type", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "size", OType.INTEGER, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "width", OType.INTEGER, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "height", OType.INTEGER, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "small", OType.CUSTOM, installMessages);
            Setup.checkCreateColumn(con, thumbnailTable, "medium", OType.CUSTOM, installMessages);

            System.out.print(TABLE_CONSTANT+" ");
            OClass constantTable = Setup.checkCreateTable(oschema, TABLE_CONSTANT, installMessages);
            Setup.checkCreateColumn(con, constantTable, "classname", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, constantTable, "description", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, constantTable, "field", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, constantTable, "value", OType.STRING, installMessages);

            System.out.print(TABLE_AUDIT+" ");
            OClass auditTable = Setup.checkCreateTable(oschema, TABLE_AUDIT, installMessages);
            Setup.checkCreateColumn(con, auditTable, "timestamp", OType.DATETIME, installMessages);
            Setup.checkCreateColumn(con, auditTable, "action", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "table", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "rid", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "user", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, auditTable, "recordVersion", OType.LONG, installMessages);
            Setup.checkCreateColumn(con, auditTable, "detail", OType.STRING, installMessages);

            if (constantTable.count() == 0) {
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Server").field("description","Server debug flag").field("field","DEBUG").field("value","false").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Server").field("description","Use images/js in jar").field("field","WWW_IN_JAR").field("value","true").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.RecordHook").field("description","Audit all changes to the database").field("field","AUDIT_WRITES").field("value","true").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Security").field("description","Security debug flag").field("field","DEBUG").field("value","false").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Table debug flag").field("field","DEBUG").field("value","false").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Table page count").field("field","ROW_COUNT_LIMIT").field("value","200").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Table").field("description","Show related tables even if no privilege").field("field","SHOW_ALL_RELATED_TABLES").field("value","true").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Context").field("description","Style sheet").field("field","DEFAULT_STYLE").field("value","dark (horizontal menu)").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Context").field("description","Code editor theme").field("field","EDITOR_THEME").field("value","blackboard").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Header").field("description","Logo for header").field("field","LOGO_FILE").field("value","Logo-yel.svg").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Menu").field("description","Menu direction (true=horizontal, false=vertical)").field("field","HORIZONTAL_LAYOUT").field("value","true").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.Schema").field("description","Number of columns in tables view").field("field","NUMBER_OF_COLUMNS").field("value","8").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.util.Setup").field("description","When true, ORestricted tables will be by user (Change OIdentity pickList if setting to true)").field("field","RESTRICTED_BY_ROLE").field("value","false").save();				
                con.create(TABLE_CONSTANT).field("classname","permeagility.web.UserRequest").field("description","Automatically assign new users to this role, leave blank to prevent automatic new user creation").field("field","ACCEPT_TO_ROLE").field("value","user").save();				
            }

            System.out.print(TABLE_LOCALE+" ");
            OClass localeTable = Setup.checkCreateTable(oschema, TABLE_LOCALE, installMessages);
            Setup.checkCreateColumn(con, localeTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, localeTable, "description", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, localeTable, "active", OType.BOOLEAN, installMessages);

            ODocument loc;  // Locale
            if (localeTable.count() == 0) {
                loc = con.create(TABLE_LOCALE);
                loc.field("name","en");
                loc.field("description","English");
                loc.field("active",true);
                loc.save();
                installMessages.append(Weblet.paragraph("CheckInstallation: Created English locale(en)"));
            } else {
                loc = con.queryDocument("SELECT FROM locale WHERE name='en'");
            }

            System.out.print(TABLE_MESSAGE+" ");
            OClass messageTable = Setup.checkCreateTable(oschema, TABLE_MESSAGE, installMessages);
            Setup.checkCreateColumn(con, messageTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, messageTable, "description", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, messageTable, "locale", OType.LINK, localeTable, installMessages);

            Message.initialize(con);

            int mCount = 0;

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
            mCount += checkCreateMessage(con, loc, "SET_STYLE", "Stylesheet");
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
            mCount += checkCreateMessage(con, loc, "SYSTEM_STYLE_UPDATED", "Style changed");
            mCount += checkCreateMessage(con, loc, "ROW_COUNT_LIMIT_UPDATED", "Page size updated");
            mCount += checkCreateMessage(con, loc, "GOTO_ROW", "Goto&gt;");
            mCount += checkCreateMessage(con, loc, "COPY", "Copy");
            mCount += checkCreateMessage(con, loc, "COPY_PREFIX", "Copied on {0}: ");
            mCount += checkCreateMessage(con, loc, "COPY_SUFFIX", " Copy");
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
            mCount += checkCreateMessage(con, loc, "LOG_DATE", "Start Date");
            mCount += checkCreateMessage(con, loc, "CHECK_FOR_UPDATE", "Check for update");
            mCount += checkCreateMessage(con, loc, "DOWNLOAD_UPDATE", "Download update");
            mCount += checkCreateMessage(con, loc, "DOWNLOADING_UPDATE", "Downloading");
            mCount += checkCreateMessage(con, loc, "DOWNLOADING_COMPLETE", "Downloaded");
            mCount += checkCreateMessage(con, loc, "APPLY_UPDATE", "Apply update");
            mCount += checkCreateMessage(con, loc, "SAVE_AND_RUN", "Save/Run");
            mCount += checkCreateMessage(con, loc, "DETAILS", "Details");
            mCount += checkCreateMessage(con, loc, "MORE", "More");

            if (mCount > 0) {
                    installMessages.append(Weblet.paragraph("CheckInstallation: Created "+mCount+" messages"));
                    Server.tableUpdated("message");
            }

            System.out.print(TABLE_NEWS+" ");
            OClass newsTable = Setup.checkCreateTable(oschema, TABLE_NEWS, installMessages);
            Setup.checkTableSuperclass(oschema, newsTable, "ORestricted", installMessages);
            Setup.checkCreateColumn(con, newsTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, newsTable, "description", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, newsTable, "dateline", OType.DATETIME, installMessages);
            Setup.checkCreateColumn(con, newsTable, "locale", OType.LINK, localeTable, installMessages);
            Setup.checkCreateColumn(con, newsTable, "archive", OType.BOOLEAN, installMessages);			

            if (newsTable.count() == 0) {
                ODocument n1 = con.create(TABLE_NEWS);
                n1.field("name","Welcome to PermeAgility");
                n1.field("description","The core template for big data applications in a micro service. Now with Visuility! Default logins are:\n"
                    + "<ul><li><a href='permeagility.web.Home?USERNAME=admin&PASSWORD=admin'>admin/admin</a></li>\n"
                    + "<li><a href='permeagility.web.Home?USERNAME=writer&PASSWORD=writer'>writer/writer</a></li>\n"
                    + "<li><a href='permeagility.web.Home?USERNAME=reader&PASSWORD=reader'>reader/reader</a></li></ul>\n"
                    + "<br><img height='60%' align='right' src='images/Logo.svg'>");
                n1.field("dateline",new Date());
                n1.field("locale",loc);
                n1.field("archive",false);
                n1.field("_allowRead", guestRoles.toArray());
                n1.save();

                ODocument n2 = con.create(TABLE_NEWS);
                n2.field("name","Welcome admin");
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

                ODocument n3 = con.create(TABLE_NEWS);
                n3.field("name","Welcome reader");
                n3.field("description","This is a place where you can navigate data and connections, click away!<br><br>\n"
                    + "Click <a href='permeagility.web.Schema'><b><i>Tables</i></b></a> to get started\n");
                n3.field("dateline",new Date());
                n3.field("locale",loc);
                n3.field("archive",false);
                n3.field("_allowRead", readerRoles.toArray());
                n3.save();

                ODocument n4 = con.create(TABLE_NEWS);
                n4.field("name","Welcome writer");
                n4.field("description","PermeAgility lets you create and navigate data every way it is connected.<br><br>\n"
                    + "Click <a href='permeagility.web.Schema'><b><i>Tables</i></b></a> to browse the data<br>\n"
                    + "Use <a href='permeagility.web.Query'><b><i>Query</i></b></a> to run custom SQL-like queries\n");
                n4.field("dateline",new Date());
                n4.field("locale",loc);
                n4.field("archive",false);
                n4.field("_allowRead", writerRoles.toArray());
                n4.save();
            }

            System.out.print(TABLE_STYLE+" ");
            OClass styleTable = Setup.checkCreateTable(oschema, TABLE_STYLE, installMessages);
            Setup.checkCreateColumn(con, styleTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, styleTable, "horizontal", OType.BOOLEAN, installMessages);
            Setup.checkCreateColumn(con, styleTable, "logo", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, styleTable, "editorTheme", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, styleTable, "CSSStyle", OType.STRING, installMessages);

            if (styleTable.count() == 0) {
                ODocument style = con.create(TABLE_STYLE); 
                style.field("name", "light (vertical menu)");
                style.field("horizontal", false);
                style.field("logo", "Logo-blk.svg");
                style.field("editorTheme","default");
                style.field("CSSStyle", DEFAULT_ALT_STYLESHEET);
                style.save();

                ODocument style2 = con.create(TABLE_STYLE); 
                style2.field("name", "dark (horizontal menu)");
                style2.field("horizontal", true);
                style2.field("logo", "Logo-yel.svg");
                style2.field("editorTheme","blackboard");
                style2.field("CSSStyle", DEFAULT_STYLESHEET);
                style2.save();				
            }

            // Upgrade styles - remove this eventually
            QueryResult styles = con.query("SELECT FROM "+TABLE_STYLE);
            for (ODocument s : styles.get()) {
                if (s.field("name").equals("default") && s.field("CSSStyle") == null) {
                    installMessages.append(Weblet.paragraph("success","Upgraded default stylesheet to latest in CSSStyle field - should remove the description column"));
                    s.field("CSSStyle",DEFAULT_STYLESHEET).save();
                }
                if (s.field("name").equals("horizontal") && s.field("CSSStyle") == null) {
                    installMessages.append(Weblet.paragraph("success","Upgraded horizontal stylesheet to latest in CSSStyle field - should remove the description column"));
                    s.field("CSSStyle",DEFAULT_ALT_STYLESHEET).save();
                }
            }

            System.out.print(TABLE_PICKLIST+" ");
            OClass pickListTable = Setup.checkCreateTable(oschema, TABLE_PICKLIST, installMessages);
            Setup.checkCreateColumn(con, pickListTable, "tablename", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, pickListTable, "query", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, pickListTable, "description", OType.STRING, installMessages);

            if (pickListTable.count() == 0) {
                con.create(TABLE_PICKLIST).field("tablename","OIdentity").field("query","select @rid.asString(), format('%s - %s',@class,name) as name from OIdentity").field("description","This will restrict row level table privileges to only selecting Roles, if Setup.RESTRICTED_BY_ROLE is true replace OIdentity pickList with SELECT @rid.asString(), name from ORole").save();				
            }

            System.out.print(TABLE_PICKVALUES+" ");
            OClass pickValuesTable = Setup.checkCreateTable(oschema, TABLE_PICKVALUES, installMessages);
            Setup.checkCreateColumn(con, pickValuesTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, pickValuesTable, "values", OType.STRING, installMessages);

            if (pickValuesTable.count() == 0) {
                con.create(TABLE_PICKVALUES).field("name","OUser.status").field("values","ACTIVE,SUSPENDED").save();				
                con.create(TABLE_PICKVALUES).field("name","OFunction.language").field("values","javascript").save();
                con.create(TABLE_PICKVALUES).field("name","style.editorTheme").field("values","default,3024-day,3024-night,ambiance-mobile,ambiance,base16-dark,base16-light,blackboard,cobalt,colorforth,eclipse,elegant,erlang-dark,lesser-dark,mbo,mdn-like,midnight,monokai,neat,neo,night,paraiso-dark,paraiso-light,pastel-on-dark,rubyblue,solarized,the-matrix,tomorrow-night-bright,tomorrow-night-eighties,twilight,vibrant-ink,xq-dark,xq-light,zenburn").save();
            }

            System.out.print(TABLE_MENU+" ");
            OClass menuTable = Setup.checkCreateTable(oschema, TABLE_MENU, installMessages);
            Setup.checkCreateColumn(con, menuTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, menuTable, "active", OType.BOOLEAN, installMessages);
            Setup.checkCreateColumn(con, menuTable, "description", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, menuTable, "sortOrder", OType.INTEGER, installMessages);

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
            OClass menuItemTable = Setup.checkCreateTable(oschema, TABLE_MENUITEM, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "classname", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "active", OType.BOOLEAN, installMessages);
            Setup.checkCreateColumn(con, menuItemTable, "description", OType.STRING, installMessages);
            Setup.checkTableSuperclass(oschema, menuItemTable, "ORestricted", installMessages);

            if (menuItemTable.count() == 0) {
                ODocument mi_login = con.create(TABLE_MENUITEM);
                mi_login.field("name","Login");
                mi_login.field("description","Login page");
                mi_login.field("classname","permeagility.web.Login");
                mi_login.field("active",true);
                mi_login.field("_allowRead", allRoles);
                mi_login.save();

                ODocument mi_home = con.create(TABLE_MENUITEM);
                mi_home.field("name","Home");
                mi_home.field("description","Home page including news");
                mi_home.field("classname","permeagility.web.Home");
                mi_home.field("active",true);
                mi_home.field("_allowRead", allRoles);
                mi_home.save();

                ODocument mi_password = con.create(TABLE_MENUITEM);
                mi_password.field("name","Profile");
                mi_password.field("description","Change profile or password");
                mi_password.field("classname","permeagility.web.Profile");
                mi_password.field("active",true);
                mi_password.field("_allowRead", allRolesButGuest);
                mi_password.save();

                ODocument mi_userRequest = con.create(TABLE_MENUITEM);
                mi_userRequest.field("name","Sign up");
                mi_userRequest.field("description","User Request");
                mi_userRequest.field("classname","permeagility.web.UserRequest");
                mi_userRequest.field("active",true);
                mi_userRequest.field("_allowRead", guestRoles);
                mi_userRequest.save();

                ODocument mi_context = con.create(TABLE_MENUITEM);
                mi_context.field("name","Context");
                mi_context.field("description","Context");
                mi_context.field("classname","permeagility.web.Context");
                mi_context.field("active",true);
                mi_context.field("_allowRead", adminRoles);
                mi_context.save();

                ODocument mi_settings = con.create(TABLE_MENUITEM);
                mi_settings.field("name","Settings");
                mi_settings.field("description","Basic settings");
                mi_settings.field("classname","permeagility.web.Settings");
                mi_settings.field("active",true);
                mi_settings.field("_allowRead", adminRoles);
                mi_settings.save();

                ODocument mi_shutdown = con.create(TABLE_MENUITEM);
                mi_shutdown.field("name","Shutdown");
                mi_shutdown.field("description","Shutdown the server");
                mi_shutdown.field("classname","permeagility.web.Shutdown");
                mi_shutdown.field("active",true);
                mi_shutdown.field("_allowRead", adminRoles);
                mi_shutdown.save();

                ODocument mi_query = con.create(TABLE_MENUITEM);
                mi_query.field("name","Query");
                mi_query.field("description","Query the database");
                mi_query.field("classname","permeagility.web.Query");
                mi_query.field("active",true);
                mi_query.field("_allowRead", adminAndWriterRoles);
                mi_query.save();

                ODocument mi_schema = con.create(TABLE_MENUITEM);
                mi_schema.field("name","Tables");
                mi_schema.field("description","Table Catalog");
                mi_schema.field("classname","permeagility.web.Schema");
                mi_schema.field("active",true);
                mi_schema.field("_allowRead", allRolesButGuest);
                mi_schema.save();

                ODocument mi_table = con.create(TABLE_MENUITEM);
                mi_table.field("name","Table Editor");
                mi_table.field("description","Table editor");
                mi_table.field("classname","permeagility.web.Table");
                mi_table.field("active",true);
                mi_table.field("_allowRead", allRolesButGuest);
                mi_table.save();				

                ODocument mi_visuility = con.create(TABLE_MENUITEM);
                mi_visuility.field("name","Visuility");
                mi_visuility.field("description","Visuility browser");
                mi_visuility.field("classname","permeagility.web.Visuility");
                mi_visuility.field("active",true);
                mi_visuility.field("_allowRead", adminAndWriterRoles);
                mi_visuility.save();				

                ODocument mi_visuilityData = con.create(TABLE_MENUITEM);
                mi_visuilityData.field("name","Visuility");
                mi_visuilityData.field("description","Visuility browser (data component)");
                mi_visuilityData.field("classname","permeagility.web.VisuilityData");
                mi_visuilityData.field("active",true);
                mi_visuilityData.field("_allowRead", allRolesButGuest);
                mi_visuilityData.save();				

                ODocument mi_backup = con.create(TABLE_MENUITEM);
                mi_backup.field("name","Backup");
                mi_backup.field("description","Backup and restore the database");
                mi_backup.field("classname","permeagility.web.BackupRestore");
                mi_backup.field("active",true);
                mi_backup.field("_allowRead", adminRoles);
                mi_backup.save();				

                ODocument mi_blank = con.create(TABLE_MENUITEM);
                mi_blank.field("name","");
                mi_blank.field("active",true);
                mi_blank.field("description","Blank menu item");
                mi_blank.field("_allow",adminRoles);
                mi_blank.field("_allowRead",allRoles);
                mi_blank.save();

                // Build default menu
                ArrayList<ODocument> items = new ArrayList<>();
                items.add(mi_userRequest);
                items.add(mi_visuility);
                items.add(mi_schema);
                items.add(mi_query);
                items.add(mi_blank);
                items.add(mi_context);
                items.add(mi_settings);
                items.add(mi_password);
                items.add(mi_backup);
                items.add(mi_shutdown);

                // Add the menu items property to the menu
                Setup.checkCreateColumn(con, menuTable, "items", OType.LINKLIST, menuItemTable, installMessages);
                ODocument menuDoc = con.queryDocument("SELECT FROM menu");
                if (menuDoc != null && items.size() > 0) {
                    menuDoc.field("items",items);
                    menuDoc.save();
                } else {
                    installMessages.append(Weblet.paragraph("error","menu is null or no items to add"));
                }
            }			

            System.out.print(TABLE_USERPROFILE+" ");
            OClass urTable = Setup.checkCreateTable(oschema, TABLE_USERPROFILE, installMessages);
            Setup.checkTableSuperclass(oschema, urTable, "ORestricted", installMessages);
            Setup.checkCreateColumn(con, urTable, "name", OType.STRING, installMessages);
            Setup.checkCreateColumn(con, urTable, "password", OType.STRING, installMessages);

            // Add table privileges for the guest and user roles
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_COLUMNS,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_NEWS,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_MENU,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_MENUITEM,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_STYLE,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_LOCALE,2,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_USERPROFILE,1,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLUSTER,TABLE_USERPROFILE,1,installMessages);

            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_COLUMNS,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_TABLEGROUP,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_NEWS,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_MENU,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_MENUITEM,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_STYLE,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_LOCALE,2,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_USERPROFILE,6,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLUSTER,TABLE_USERPROFILE,6,installMessages);

            checkCreatePrivilege(con,"guest",ResourceGeneric.CLASS,TABLE_AUDIT,1,installMessages);
            checkCreatePrivilege(con,"guest",ResourceGeneric.CLUSTER,TABLE_AUDIT,1,installMessages);

            checkCreatePrivilege(con,"user",ResourceGeneric.CLASS,TABLE_AUDIT,1,installMessages);
            checkCreatePrivilege(con,"user",ResourceGeneric.CLUSTER,TABLE_AUDIT,1,installMessages);

            con.flush();
            System.out.println("- verified.");

        } catch (Exception e) {
            System.out.println("- failed: "+e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }


    public static void createMenuItem(DatabaseConnection con, String name, String description, String classname, String addTo, String roles) {
        // Create menuitem
        roles = (roles != null ? "#"+roles.replace(" ", "").replace(",",",#") : "");
        Object menuItem = con.update("INSERT INTO "+Setup.TABLE_MENUITEM+" SET name='"+name+"', active=true"
                + ", description='"+description+"', classname='"+classname+"', _allowRead=["+roles+"]");

        // Add to the specified menu
        if (addTo != null && !addTo.equals("") && menuItem instanceof ODocument) {
            con.update("UPDATE #"+addTo+" ADD items = "+((ODocument)menuItem).getIdentity().toString());
        }
        Server.tableUpdated("menu");
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
                ODocument d = con.get(mi);
                d.delete();
            }
            errors.append(Weblet.paragraph("success","Deleted menu item for "+classname));
            Server.tableUpdated("menu");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Create message if it doesn't already exist */
    public static int checkCreateMessage(DatabaseConnection con, ODocument loc, String name, String description) {
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

    /** Add the column to the columns table to preserve initial order (always append only if not already there)  */
    public static void addColumnToColumns(DatabaseConnection con, String theClass, String propertyName) {
        ODocument d = con.queryDocument("SELECT FROM "+TABLE_COLUMNS+" WHERE name='"+theClass+"'");
        if (d == null) {
            d = con.create(TABLE_COLUMNS);
            d.field("name",theClass);
        }
        String cl = d.field("columnList");
        if (cl == null || cl.equals("")) {
            d.field("columnList",propertyName);
        } else {
            String clc[] = cl.split(",");
            for (String cln : clc) {
                if (cln.trim().equals(propertyName) || cln.trim().equals("-"+propertyName)) {
                    return;  // Its already here
                }
            }
            if (!cl.contains(propertyName))
            d.field("columnList",d.field("columnList")+","+propertyName);			
        }		
        d.save();
        return;
    }

    /** Remove the column from the columns table to preserve initial order (always append) */
    public static void removeColumnFromColumns(DatabaseConnection con, String theClass, String propertyName) {
        ODocument d = con.queryDocument("SELECT FROM "+TABLE_COLUMNS+" WHERE name='"+theClass+"'");
        if (d == null) {
            return;
        }
        String cl = d.field("columnList");
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
            d.field("columnList",newCols);			
        }		
        d.save();
        return;
    }

    public static void addTableToTableGroup(DatabaseConnection con, String theClass, String tableGroup) {
        ODocument d = con.queryDocument("SELECT FROM "+TABLE_TABLEGROUP+" WHERE name='"+tableGroup+"'");
        if (d == null) {
            d = con.create(TABLE_TABLEGROUP);
            d.field("name",tableGroup);
            d.field("_allowRead",Security.getUserRoles(con));
        }
        String tableList = d.field("tables");
        if (tableList == null || tableList.equals("")) {
            d.field("tables",theClass);
        } else {
            String clc[] = tableList.split(",");
            for (String cln : clc) {
                if (cln.trim().equals(theClass) || cln.trim().equals("-"+theClass)) {
                    return;  // Its already here
                }
            }
            if (!tableList.contains(theClass)) {
                d.field("tables",d.field("tables")+","+theClass);			
            }
        }		
        d.save();
        Server.tableUpdated("tableGroup");
        return;
    }

    /** Should be called when removing a table to ensure the table in out of table group and columns */
    public static void removeTableFromAllTableGroups(DatabaseConnection con, String theClass) {
        QueryResult q = con.query("SELECT FROM "+TABLE_TABLEGROUP+" WHERE tables CONTAINSTEXT '"+theClass+"'");
        if (q == null || q.size()==0) {
            return;
        }
        for (ODocument d : q.get()) {  // For each document that may contain the reference to the class
            String tableList = d.field("tables");
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
                d.field("tables",newTabs);			
            }		
            d.save();
        }
        return;
    }

    /** Create or update a constant - note this does not call Server.tableUpdated("constant") to avoid repeated constant updates */
    public static void checkCreateConstant(DatabaseConnection con, String classname, String description, String field, String value) {
        QueryResult qr = con.query("SELECT FROM "+TABLE_CONSTANT+" WHERE classname='"+classname+"' AND field='"+field+"'");
        if (qr != null && qr.size()>0) {
            ODocument cd = qr.get(0);
            if (cd != null) {
                cd.field("value",value).save();
            }
        } else {
            con.create(Setup.TABLE_CONSTANT).field("classname",classname).field("description",description).field("field",field).field("value",value).save();							
        }
    }

    /** Check for the existence of a class property or add it This assumes you want a link type, otherwise the linkClass may have adverse effects */
    public static OProperty checkCreateColumn(DatabaseConnection con, OClass theClass, String propertyName, OType propertyType, OClass linkClass, StringBuilder errors) {
        OProperty p = theClass.getProperty(propertyName);
        if (p == null) {
            p = theClass.createProperty(propertyName, propertyType, linkClass);
            errors.append(Weblet.paragraph("Schema update: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()+" linked to "+linkClass.getName()));
        }
        addColumnToColumns(con, theClass.getName(),propertyName);
        return p;
    }

    /** Check for the existence of a class property or add it and add to columns */
    public static OProperty checkCreateColumn(DatabaseConnection con, OClass theClass, String propertyName, OType propertyType, StringBuilder errors) {
        OProperty p = theClass.getProperty(propertyName);
        if (p == null) {
            p = theClass.createProperty(propertyName, propertyType);
            errors.append(Weblet.paragraph("Schema update: Created property "+theClass.getName()+"."+propertyName+" of type "+propertyType.name()));
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
    public static OClass checkCreateTable(OSchema oschema, String className, StringBuilder errors) {
        OClass c = oschema.getClass(className);
        if (c == null) {
            c = oschema.createClass(className);
            errors.append(Weblet.paragraph("Schema update: Created "+className+" class/table"));
        }
        if (c == null) {
            errors.append(Weblet.paragraph("error","Schema update: Error creating "+className+" class/table"));
        }
        if (c != null) {
            if (c.isStrictMode()) {
                c.setStrictMode(false);
                errors.append(Weblet.paragraph("Schema update: Set non-strict "+className+" class/table"));
            }
        }
        return c;
    }

    /** Check for the existence of a class or add it */
    public static OClass checkCreateTable(DatabaseConnection con, OSchema oschema, String className, StringBuilder errors, String tableGroup) {
        OClass c = oschema.getClass(className);
        if (c == null) {
            c = oschema.createClass(className);
            errors.append(Weblet.paragraph("Schema update: Created "+className+" class/table"));
        }
        if (c == null) {
            errors.append(Weblet.paragraph("error","Schema update: Error creating "+className+" class/table"));
        }
        if (c != null) {
            if (c.isStrictMode()) {
                c.setStrictMode(false);
                errors.append(Weblet.paragraph("Schema update: Set non-strict "+className+" class/table"));
            }
        }
        if (tableGroup != null) addTableToTableGroup(con, className,tableGroup);
        return c;
    }

    /** Check for the existence of a class's superclass or set it */
    public static boolean checkTableSuperclass(OSchema oschema, OClass oclass, String superClassName, StringBuilder errors) {
        OClass s = oschema.getClass(superClassName);
        if (s == null) {
            errors.append(Weblet.paragraph("error","Schema update: Cannot find superclass "+superClassName+" to assign to class "+oclass.getName()));
            return false;
        }
        List<OClass> sc = oclass.getSuperClasses();
        boolean hasSuper = false;
        for (OClass c : sc) {
            if (c.getName().equals(superClassName)) { hasSuper = true; }
        }
        if (!hasSuper) {
            oclass.addSuperClass(s);
            errors.append(Weblet.paragraph("Schema update: Assigned superclass "+superClassName+" to class "+oclass.getName()));
            if (superClassName.equals("ORestricted") && RESTRICTED_BY_ROLE) {
                oclass.setCustom("onCreate.identityType", "role");   //alter class x custom onCreate.identityType=role
            }
            return true;
        }
        return false;
    }

    /** Check for the existence of a privilege or add it */
    public static boolean checkCreatePrivilege(DatabaseConnection con, String roleName, ORule.ResourceGeneric resource, String className, int priv, StringBuilder errors) {
        OSecurity osecurity = con.getDb().getMetadata().getSecurity();
        ORole role = osecurity.getRole(roleName);
        if (role == null) {
            System.out.println("Setup.checkCreatePrivilege role "+roleName+" is null");
        }
        if (!role.hasRule(resource,className)) {
            System.out.println("Adding privilege: "+resource+" to "+roleName+" for "+className+" with priv="+priv);
            ORole newRole = role.addRule(resource,className, priv);
            if (newRole.allow(resource,className, priv)){
                newRole.save();
                errors.append(Weblet.paragraph("success",resource.getName()+(className != null ? "."+className : "")+":"+priv+" added to "+roleName));
            } else {
                errors.append(Weblet.paragraph("error",resource.getName()+(className != null ? "."+className : "")+" failed to add privilege "+priv+" to "+roleName));
            }
        }
        return true;
    }

    /** Drop a table */
    public static void dropTable(DatabaseConnection con, String classname) {
        dropTable(con, classname, null);
    }

    public static boolean dropTable(DatabaseConnection con, String classname, StringBuilder errors) {
        try {
            con.update("ALTER CLASS "+classname+" SUPERCLASSES NULL");  // Clear superclasses first otherwise will fail
            OSchema schema = con.getSchema();
            schema.dropClass(classname);
            Setup.removeTableFromAllTableGroups(con, classname);
            QueryResult qr = con.query("SELECT FROM columns WHERE name='"+classname+"'");
            List<String> colIds = qr.getIds();
            for (String colId : colIds) {
                ODocument d = con.get(colId);
                if (d != null) d.delete();
            }
            DatabaseConnection.rowCountChanged(classname);
            if (errors != null) errors.append(Weblet.paragraph("success","Table dropped: "+classname));
            return true;
        } catch (Exception e) {
            if (errors != null) errors.append(Weblet.paragraph("error","Table "+classname+" could not be dropped: "+e.getMessage()));            
            return false;
        }
    }

    public static final String DEFAULT_ALT_STYLESHEET = 
"/* This is the bright PermeAgility stylesheet (vertical menu)*/\n" +
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
"div.CodeMirror {\n"+
"    border: 1px solid #eee;\n"+
"    height: auto;\n"+
"}\n" +
"  .split {\n" +
"    box-sizing: border-box;\n" +
"    overflow-y: auto;\n" +
"    overflow-x: hidden;\n" +
"  }\n" +
"  .gutter.gutter-horizontal { cursor: col-resize; }\n" +
"  .gutter.gutter-vertical { cursor: row-resize; }\n" +
"  .gutter.gutter-horizontal:hover { background-color: lightgray; }\n" +
"  .gutter.gutter-vertical:hover { background-color: lightgray; }\n" +
"  .split.split-horizontal, .gutter.gutter-horizontal {\n" +
"    height: 100%;\n" +
"    float: left;\n" +
"  }\n"+
".nodeTitle { fill: black; font-size: medium; }\n"+
"g:not(.selected) { stroke: none; } \n"+
"g.selected { stroke: black; }\n"+
".link { fill: black; stroke: lightgray; }\n"+
"rect.node { opacity: 1; }\n"+
"rect.selection { stroke: black; fill: none; stroke-width: 4px; stroke-dasharray: 5,5; opacity: 0.8; }\n"+
"@media print { BODY { font-size: 6pt; margin: 1em; } }\n" +
"@media print { #menu {display: none; } }\n" +
"@media print { #service {position: absolute; top: 0.5in; left: auto;} }\n" +
"@media print { TABLE.data { border: solid thin;  page-break-inside: avoid;} }\n" +
"@media print { TD.header { border: solid thin; } }\n" +
"@media print { *.new { border: dotted thin; } }\n" +
"@media print { *.alert { border: solid medium; border-color: #FF0000;} }\n" +
"@media print { *.changed { border: double thin; } }\n" +
"@media print { *.button { display: none; } }\n";
					
	public static final String DEFAULT_STYLESHEET = 
"/* This is the dark PermeAgility stylesheet (horizontal menu) */\n" +
"img.headerlogo { width: 90px; left: 2px; top: 25px; \n" +
"    position: absolute; border: none; }\n" +
"a.headerlogo:hover { text-decoration: none; background-color: transparent;}\n" +
"body, html { font-family: verdana,sans-serif;\n" +
"       color: white; background: black;\n" +
"        background: linear-gradient(to right, black, #444444); }\n" +
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
"#service { position: absolute; top: 80px;  left: 5px; right: 5px; bottom: 0px; display: inline-block;}\n" +
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
"    font-weight: bold; text-align:center; \n" +
"    color: white; margin: 0.2em 0em 0em 0em; \n" +
"    border-radius: 6px 6px 6px 6px;\n" +
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
"    font-weight: bold; text-align:center; \n" +
"    color: white; margin: 0.2em 0em 0em 0em; \n" +
"    border-radius: 6px 6px 6px 6px;\n" +
"    background: radial-gradient(ellipse, #1d5e1f, black);\n" +
"}\n" +
"P.nochange { background-color: rgb(0,0,200); \n" +
"    font-weight: bold; text-align:center; \n" +
"    color: white; margin: 0.2em 0em 0em 0em; \n" +
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
"div.CodeMirror {\n"+
"    height: auto;\n"+
"}\n"+
"  .split {\n" +
"    box-sizing: border-box;\n" +
"    overflow-y: auto;\n" +
"    overflow-x: hidden;\n" +
"  }\n" +
"  .gutter.gutter-horizontal { cursor: col-resize; }\n" +
"  .gutter.gutter-vertical { cursor: row-resize; }\n" +
"  .gutter.gutter-horizontal:hover { background-color: #444444; }\n" +
"  .gutter.gutter-vertical:hover { background-color: #444444; }\n" +
"  .split.split-horizontal, .gutter.gutter-horizontal {\n" +
"    height: 100%;\n" +
"    float: left;\n" +
"  }\n"+
".nodeTitle { fill: white; filter: url(#drop-shadow); font-size: small; }\n"+
"g:not(.selected) { stroke: none; } \n"+
"g.selected { stroke: yellow; }\n"+
".link { fill: lightgray; stroke: gray; }\n"+
"rect.node { opacity: 0.5; }\n"+
"rect.selection { opacity:0.8; fill: none; stroke: white; stroke-width: 4px; stroke-dasharray: 5,5; }\n"+
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
