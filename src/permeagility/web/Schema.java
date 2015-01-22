/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.ArrayList;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class Schema extends Weblet {
	
	public static int NUMBER_OF_COLUMNS = 4;
	public static boolean ADD_NAME_TO_NEW_TABLE = true;   // Will always add a name field to a new table (if you don't like it, delete it or change this constant)
	
	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		parms.put("SERVICE", Message.get(con.getLocale(),"SCHEMA_EDITOR"));
		StringBuilder errors = new StringBuilder();
		
		String submit = (String)parms.get("SUBMIT");
		if (submit != null) {
			if (submit.equals(Message.get(con.getLocale(),"NEW_TABLE"))) {
				String tn = (String)parms.get("NEWTABLENAME");
				if (tn == null || tn.equals("")) {
					errors.append(paragraph("error","Table name must be specified"));
				} else {
					try {
						String camel = makePrettyCamelCase(tn);
						OClass newclass = con.getSchema().createClass(camel);
						if (newclass != null) {
							errors.append(paragraph("success",Message.get(con.getLocale(),"NEW_TABLE_CREATED",camel,makeCamelCasePretty(camel))));
							if (ADD_NAME_TO_NEW_TABLE) {
								Setup.checkCreateColumn(con, newclass, "name", OType.STRING, errors);
								//newclass.createProperty("name", OType.STRING).setNotNull(false).setMandatory(false);
							}
							Server.tableUpdated("metadata:schema");
						} else {
							errors.append(paragraph("warning","New table returned null class"));							
						}
					} catch (Exception e) {
						errors.append(paragraph("error","Cannot create table: "+e.getMessage()));
					}
					
				}
			}
		}
		
		// Prepare response
		int cellCount = 0;
		StringBuilder rows = new StringBuilder();
		StringBuilder columns = new StringBuilder();
		QueryResult schemas = con.query("SELECT from "+Setup.TABLE_TABLEGROUP+" WHERE _allowRead in ["+Server.getUserRolesList(con)+"] ORDER BY name");
		QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
		ArrayList<String> tablesInGroups = new ArrayList<String>(); 
		for (ODocument schema : schemas.get()) {
			String groupName = schema.field("name");
			StringBuilder tablelist = new StringBuilder();
			String tablesf = schema.field("tables");
			String table[] = {};
			if (tablelist != null) {
				table = tablesf.split(",");
			}
			String prettyGroup = Message.get(con.getLocale(), "TABLEGROUP_"+groupName);
			if (prettyGroup != null && ("TABLEGROUP_"+groupName).equals(prettyGroup)) {
				prettyGroup = makeCamelCasePretty(groupName);
			}

			tablelist.append(paragraph("banner",prettyGroup));
			boolean groupHasTable = false;
			for (String tableName : table) {
				tableName = tableName.trim();
				boolean show = true;
				if (tableName.startsWith("-")) {
					show = false;
					tableName = tableName.substring(1);
				}
				String pretty = Message.get(con.getLocale(), "TABLE_"+tableName);
				if (pretty != null && ("TABLE_"+tableName).equals(pretty)) {
					pretty = makeCamelCasePretty(tableName);
				}
				tablesInGroups.add(tableName);
				if (show) {
					int privs = Server.getTablePriv(con, tableName);
					//System.out.println("Table privs for table "+tableName+" for user "+con.getUser()+" privs="+privs);
					if (privs > 0) {
						tableName = tableName.trim();
						if (con.getSchema().getClass(tableName) != null) {
							tablelist.append(link("permeagility.web.Table?TABLENAME="+tableName,pretty)+br());
							groupHasTable = true;
						} else {
							System.out.println("permeagility.web.Schema: Table "+tableName+" not found - will not be shown");
						}
					}
				}
			}
			if (groupHasTable && !groupName.startsWith("-")) {
				columns.append(column("layout",tablelist.toString()));
				cellCount++;
				if (cellCount == NUMBER_OF_COLUMNS) {
					rows.append(row(columns.toString()));
					columns = new StringBuilder();
					cellCount = 0;
				}
			}
		}
		
		// Add new/ungrouped - for DBA's only
		if (Server.isDBA(con)) {
			StringBuilder tablelist = new StringBuilder();
			tablelist.append(paragraph("banner",Message.get(con.getLocale(), "TABLE_NONGROUPED")));
			for (ODocument row : tables.get()) {
				String tablename = row.field("name");
				if (!tablesInGroups.contains(tablename)) {
					if (Server.getTablePriv(con, tablename) > 0) {
						String pretty = Message.get(con.getLocale(), "TABLE_"+tablename);
						if (pretty != null && ("TABLE_"+tablename).equals(pretty)) {
							pretty = makeCamelCasePretty(tablename);
						}
						tablelist.append(link("permeagility.web.Table?TABLENAME="+(String)tablename,pretty)+br());
					}
				}
			}
			columns.append(column("layout",tablelist.toString()));
		}
		
		// Make sure the last row is added
		if (columns.length() > 0) {
			rows.append(row(columns.toString()));
		}

		// Return result
		return 	
			head(Message.get(con.getLocale(),"SCHEMA_EDITOR"))+
			body( standardLayout(con, parms,  
					errors.toString()
					+table("layout",rows.toString())+br()
					+(Server.isDBA(con) 
						? popupForm("NEWTABLE_Ungrouped",null,Message.get(con.getLocale(),"NEW_TABLE"),"","NEWTABLENAME",
								input("NEWTABLENAME","")+"&nbsp;&nbsp;"
								+submitButton(Message.get(con.getLocale(),"NEW_TABLE"))
								)
						: "")
			));
	}
	
}
