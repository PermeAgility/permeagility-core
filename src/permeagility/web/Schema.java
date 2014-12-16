/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.ArrayList;

import permeagility.util.DatabaseConnection;
import permeagility.util.DatabaseSetup;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class Schema extends Weblet {
	
	public static int NUMBER_OF_COLUMNS = 4;
	
	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		parms.put("SERVICE", Message.get(con.getLocale(),"SCHEMA_EDITOR"));
		StringBuffer errors = new StringBuffer();
		String submit = (String)parms.get("SUBMIT");
		if (submit != null) {
			if (submit.equals(Message.get(con.getLocale(),"NEW_TABLE"))) {
				String tn = (String)parms.get("NEWTABLENAME");
				if (tn == null || tn.equals("")) {
					errors.append(paragraph("error","Table name must be specified"));
				} else {
					try {
						OClass newclass = con.getSchema().createClass(tn);
						if (newclass != null) {
							errors.append(paragraph("success","New table created"));
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
		StringBuffer rows = new StringBuffer();
		StringBuffer columns = new StringBuffer();
		QueryResult schemas = con.query("SELECT from "+DatabaseSetup.TABLE_TABLEGROUP+" WHERE _allowRead in ["+Server.getUserRolesList(con)+"] ORDER BY name");
		QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
		ArrayList<String> tablesInGroups = new ArrayList<String>(); 
		for (ODocument schema : schemas.get()) {
			String groupName = schema.field("name");
			StringBuffer tablelist = new StringBuffer();
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
						tablelist.append(link("permeagility.web.Table?TABLENAME="+tableName,pretty)+br());
						groupHasTable = true;
					}
				}
			}
			if (groupHasTable && !groupName.startsWith("-")) {
				columns.append(columnTop(100/(schemas.size()+1),tablelist.toString()));
				cellCount++;
				if (cellCount == NUMBER_OF_COLUMNS) {
					rows.append(row(columns.toString()));
					columns = new StringBuffer();
					cellCount = 0;
				}
			}
		}
		
		// Add new/ungrouped - for DBA's only
		if (Server.isDBA(con)) {
			StringBuffer tablelist = new StringBuffer();
			tablelist.append(paragraph("banner",Message.get(con.getLocale(), "TABLE_NONGROUPED")));
			for (ODocument row : tables.get()) {
				String tablename = row.field("name");
				if (!tablesInGroups.contains(tablename)) {
					if (Server.getTablePriv(con, tablename) > 0) {
						tablelist.append(link(
							"permeagility.web.Table?TABLENAME="
							+(String)tablename
							,makeCamelCasePretty((String)tablename))
							+br()
						);
					}
				}
			}
			columns.append(columnTop(100/(schemas.size()+1),tablelist.toString()));
		}
		
		// Make sure the last row is added
		if (columns.length() > 0) {
			rows.append(columns);
		}

		// Return result
		return 	
			head("Schema Weblet")+
			body( standardLayout(con, parms,  
					errors.toString()
					+table(0,rows.toString())
					+br()
					+(Server.isDBA(con) 
						? popupForm("NEWTABLE_Ungrouped",null,Message.get(con.getLocale(),"NEW_TABLE"),"","NEWTABLENAME",
								input("NEWTABLENAME","")
								+submitButton(Message.get(con.getLocale(),"NEW_TABLE"))
								)
						: "")
			));
	}
	
}
