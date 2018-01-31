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
package permeagility.plus.webgl;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.record.impl.ODocument;
import permeagility.web.Message;
import permeagility.web.Server;
import permeagility.web.Table;
import static permeagility.web.Weblet.paragraph;

public class PlusSetup extends permeagility.plus.PlusSetup {

	public static boolean INSTALLED = false;  // Set via constant to complete installation
	public static String INSTALLED_VERSION = "0";  // Set via constant to complete installation

	// Override these to change the names of the tables that will be created and used by this importer
	public static String TABLE = "shader";   // Local OrientDB table name to hold connection specs

	public static String MENU_CLASS = "permeagility.plus.webgl.ShaderBuilder";

	@Override public String getName() { return "Shader Builder"; }
	@Override public String getInfo() { return "Build WebGL Shaders for games, visualization and computing"; }

	@Override public boolean isInstalled() { return INSTALLED; }

	@Override public boolean install(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
		OSchema oschema = con.getSchema();
		String newTableGroup = pickTableGroup(con, parms);
                String roles = parms.get("ROLES");
                String samples = parms.get("LOAD_EXAMPLES");

		if (isNullOrBlank(newTableGroup) || isNullOrBlank(parms.get("MENU")) || isNullOrBlank(roles)) {
			errors.append(paragraph("error",Message.get(con.getLocale(), "PLUS_PARMS_INVALID")));
			return false;
		}
                ODocument loc = con.queryDocument("SELECT FROM locale WHERE name='en'");

		OClass table = Setup.checkCreateTable(con, oschema, TABLE, errors, newTableGroup);
                Setup.checkTableSuperclass(oschema, table, "ORestricted", errors);
		Setup.checkCreateColumn(con,table, "name", OType.STRING, errors);
		Setup.checkCreateColumn(con,table, "description", OType.STRING, errors);
		Setup.checkCreateColumn(con,table, "vertexScript", OType.STRING, errors);
		Setup.checkCreateColumn(con,table, "fragmentScript", OType.STRING, errors);
		Setup.checkCreateColumn(con,table, "testScript", OType.STRING, errors);
		Setup.checkCreateColumn(con,table, "usesShader", OType.LINKMAP, table, errors);

		Setup.createMenuItem(con,getName(),getInfo(),MENU_CLASS,parms.get("MENU"),roles);

                // Add table privs for each role
                String privRoles[] = roles.split(",");
                for (String role : privRoles) {
                    String roleName = con.get(role).field("name");
                    if (roleName != null) {
                        Setup.checkCreatePrivilege(con, roleName, ORule.ResourceGeneric.CLASS, TABLE, Table.PRIV_ALL, errors);
                        Setup.checkCreatePrivilege(con, roleName, ORule.ResourceGeneric.CLUSTER, TABLE, Table.PRIV_ALL, errors);
                    }
                }
                Server.tableUpdated("message");

                if (samples != null && samples.equalsIgnoreCase("on")) {
                    System.out.println("Loading sample data");
                    importData(con, "permeagility/plus/webgl/shaders.json", TABLE, "name", errors);
                }

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
			Setup.dropTable(con, TABLE, errors);  // Need to drop all privs too?
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

        /** add checkbox Default install form */
        @Override  public String getAddForm(DatabaseConnection con) {
            return super.getAddForm(con) + "<br>Load examples"+checkbox("LOAD_EXAMPLES", true);
    }

}
