package permeagility.plus;

import java.util.ArrayList;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;
import permeagility.web.Message;
import permeagility.web.Server;
import permeagility.web.Table;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class Merge extends Table {

	// Override this with a constant to true after installation to avoid installation check
	public static boolean INSTALLED = false;  // Will check for existence of config tables and create - can turn off in constant

	@Override
	public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
	
		StringBuilder sb = new StringBuilder();
		StringBuilder errors = new StringBuilder();

		if (!INSTALLED) {
			checkInstallation(con, errors);
		}
		String submit = parms.get("SUBMIT");
		String connect = parms.get("CONNECT");
		String fromTable = parms.get("fromTable");
		String toTable = parms.get("toTable");
		String fromKey = parms.get("fromKey");
		String toKey = parms.get("toKey");
		String editId = parms.get("EDIT_ID");
		String updateId = parms.get("UPDATE_ID");
		String run = parms.get("RUN");
		String tableName = parms.get("TABLENAME");
		String go = parms.get("GO");

		// Process update of work tables
		if (updateId != null && submit != null) {
			System.out.println("update_id="+updateId);
			if (submit.equals(Message.get(con.getLocale(), "DELETE"))) {
				if (deleteRow(con, tableName, parms, errors)) {
					submit = null;
				} else {
					return head("Could not delete")
							+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
				}
			} else if (submit.equals(Message.get(con.getLocale(), "UPDATE"))) {
				System.out.println("In updating row");
				if (updateRow(con, tableName, parms, errors)) {
					submit = null;
				} else {
					return head("Could not update", getDateControlScript(con.getLocale())+getColorControlScript())
							+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
				}
			} 
			// Cancel is assumed
			editId = null;
			updateId = null;
			connect = parms.get(PARM_PREFIX+"path");
		}

		// Create a SQL import directly - set the created date
		if (submit != null && submit.equals(Message.get(con.getLocale(), "CREATE_ROW"))) {
			parms.put(PARM_PREFIX+"created", formatDate(con.getLocale(), new java.util.Date(), "yyyy-MM-dd HH:mm:ss"));
			boolean inserted = insertRow(con,tableName,parms,errors);
			if (!inserted) {
				errors.append(paragraph("error","Could not insert"));
			}
		}
		
		// Show edit form if row selected for edit
		if (editId != null && submit == null && connect == null) {
			toTable = tableName;
			return head("Edit", getDateControlScript(con.getLocale())+getColorControlScript())
					+ body(standardLayout(con, parms, getTableRowForm(con, toTable, parms)));
		}
		
		if (run != null) {
			// Run a merge path 
			int insertCount = 0;
			int updateCount = 0;
			System.out.println("Running merge path "+run);
			editId = null;
			ODocument mDoc = con.get(run);
			if (mDoc != null) {
				fromTable = mDoc.field("fromTable");
				toTable = mDoc.field("toTable");
				fromKey = mDoc.field("fromKey");
				toKey = mDoc.field("toKey");
			}
			OClass fromClass = con.getSchema().getClass(fromTable);
			System.out.println("Merge from "+fromTable+" to "+toTable);
			QueryResult fromResult = null;
			QueryResult toResult = null;
			try {
				fromResult = con.query("SELECT FROM "+fromTable+" ORDER BY "+fromKey);
			} catch (Exception e) {
				Throwable cause = e.getCause();
				sb.append(paragraph("error","FromTable: "+cause==null ? e.getMessage() : cause.getMessage()));
			}
			try {
				toResult = con.query("SELECT FROM "+toTable+" ORDER BY "+toKey);
			} catch (Exception e) {
				Throwable cause = e.getCause();
				sb.append(paragraph("error","ToTable: "+cause==null ? e.getMessage() : cause.getMessage()));				
			}
			if (fromResult != null && toResult != null) {
				sb.append(paragraph("success","Merging from count "+fromResult.size()+" to count "+toResult.size()));
				QueryResult columnMap = null;
				try {
					columnMap = con.query("SELECT FROM "+ATTR_TABLE+" WHERE path=#"+run);
				} catch (Exception e) {
					Throwable cause = e.getCause();
					sb.append(paragraph("error","AttributeTable: "+cause==null ? e.getMessage() : cause.getMessage()));				
				}
				if (columnMap != null && columnMap.size() > 0) {
					int fromIndex = 0;
					int toIndex = 0;
					while (fromIndex < fromResult.size()) {
						ODocument fromDoc = fromResult.get(fromIndex);
						ODocument toDoc = null;
						if (toIndex < toResult.size()) {
							toDoc = toResult.get(toIndex);
						}
						Object fromId = fromDoc.field(fromKey);
						Object toId = (toDoc != null ? toDoc.field(toKey) : null);
						if (fromId == null) {
							sb.append(paragraph("error","Cannot merge a null key"));	
							fromIndex++;
						} else {
							String fromK = fromId.toString();
							if (toId != null) {
								String toK = toId.toString();
								int comp = fromK.compareTo(toK);
								if (comp < 0) {
									//sb.append(paragraph("Would insert "+fromKey+"="+fromK));
									insertCount += insertDocument(con, fromDoc, columnMap, toTable);
									fromIndex++;
								} else if (comp == 0) {
									//sb.append(paragraph("Would merge "+fromKey+"="+fromK));
									updateCount += mergeDocument(con, fromDoc, toDoc, columnMap);
									fromIndex++;
									toIndex++;
								} else {
									toIndex++;
								}
							} else {
								//sb.append(paragraph("To is empty: Would insert "+fromKey+"="+fromK));
								insertCount += insertDocument(con, fromDoc, columnMap, toTable);
								fromIndex++;
								toIndex++;
							}
						}
					}
					sb.append(paragraph("success","Merged "+columnMap.size()+" columns in "+fromResult.size()+" rows "));
					if (insertCount > 0 || updateCount > 0) {
						sb.append(paragraph("success","Merge: "+insertCount+" rows inserted "+updateCount+" data elements merged"));
					} else {
						sb.append(paragraph("warning","Zero inserts or updates"));						
					}
				} else {
					sb.append(paragraph("error","No columns mapped from table "+fromTable+" to table "+toTable+" in merge path "+mDoc.field("name")));					
				}
			}
		}
		if (sb.length() == 0) {
	    	try {
	    		parms.put("SERVICE", "Merge: Setup/Select merge path");
				sb.append(paragraph("banner","Select merge path"));
				sb.append(getTable(con,parms,MERGE_TABLE,"SELECT FROM "+MERGE_TABLE, null,0, "button(RUN:Run), name, fromTable, toTable, created, executed"));
	    	} catch (Exception e) {  
	    		e.printStackTrace();
	    		sb.append("Error retrieving import patterns: "+e.getMessage());
	    	}
		}
		return 	head("Merge",getDateControlScript(con.getLocale())+getColorControlScript())
				+body(standardLayout(con, parms, 
				errors.toString()
				+((Server.getTablePriv(con, MERGE_TABLE) & PRIV_CREATE) > 0 && connect == null 
					? popupForm("CREATE_NEW_ROW",null,Message.get(con.getLocale(),"NEW_PATH"),null,"name",
						paragraph("banner",Message.get(con.getLocale(), "CREATE_ROW"))
						+hidden("TABLENAME", MERGE_TABLE)
						+getTableRowFields(con, MERGE_TABLE, parms, "name,fromTable,toTable,fromKey,toKey")
						+submitButton(Message.get(con.getLocale(), "CREATE_ROW"))) 
					: "")
				+sb.toString()
			));
	}

	public int insertDocument(DatabaseConnection con, ODocument doc, QueryResult columnMap, String toTable) {
		
		OClass tableClass = con.getSchema().getClass(toTable);
		ODocument newdoc = con.create(toTable);
		if (newdoc == null || tableClass == null) {
			System.out.println("Could not create new document of type "+toTable);
			return 0;
		}
		
		for (ODocument cm : columnMap.get()) {
			String fromCol = cm.field("fromColumn");
			String toCol = cm.field("toColumn");
			String linkProp = cm.field("linkProperty");
			if (fromCol == null || fromCol.equals("")) {
				System.out.println("fromColumn is null");
				return 0;
			}
			if (toCol == null || toCol.equals("")) {
				System.out.println("toColumn is null");
				return 0;
			}
			OProperty toProp = tableClass.getProperty(toCol);
			if (toProp == null){
				System.out.println("toColumn property can not be found in the target class");
				return 0;
			}
			OClass linkedClass = toProp.getLinkedClass();
			Object data = doc.field(fromCol);
			newdoc.field(toCol, data);
			
		}
		newdoc.save();
		return 1;
	}
	
	public int mergeDocument(DatabaseConnection con, ODocument fromDoc, ODocument toDoc, QueryResult columnMap) {
		if (fromDoc == null || toDoc == null || columnMap == null) {
			System.out.println("Merge: What the?");
			return 0;
		}
		
		int mergeCount = 0;
		OClass tableClass = toDoc.getSchemaClass();
		for (ODocument cm : columnMap.get()) {
			String fromCol = cm.field("fromColumn");
			String toCol = cm.field("toColumn");
			String linkProp = cm.field("linkProperty");
			OProperty toProp = tableClass.getProperty(toCol);
			if (fromCol == null || fromCol.equals("")) {
				System.out.println("fromColumn is null");
			} else if (toCol == null || toCol.equals("")) {
				System.out.println("toColumn is null");
			} else if (toProp == null){
				System.out.println("toColumn property can not be found in the target class");
			} else {
				OClass linkedClass = toProp.getLinkedClass();
				Object data = fromDoc.field(fromCol);
				Object toData = toDoc.field(toCol);
				if (data != null) {
					if (linkedClass != null) {
						if ( linkProp != null && toProp.getType() == OType.LINK) {
							String q = "SELECT FROM "+linkedClass.getName()+" WHERE "+linkProp+" = "+data.toString();
							//System.out.println("query="+q);
							QueryResult refs = con.query(q);
							if (refs != null && refs.size()>0) {
								ODocument linkDoc = refs.get(0);
								if (toData == null || !((ODocument)toData).getIdentity().equals(linkDoc.getIdentity())) {
									toDoc.field(toCol, linkDoc);
									mergeCount++;
								}
							} else {
								System.out.println("Could not find document for link to "+linkedClass.getName()+" where "+linkProp+"="+data);
							}
						} else {
							System.out.println("Linked class found but linkProperty not defined or link is multiple type");
						}
					} else if (toData == null || !data.toString().equals(toData.toString())) {
						toDoc.field(toCol, data);
						mergeCount++;
					}
				}
			}
			
		}
		if (mergeCount > 0) {
			toDoc.save();
		}
		return mergeCount;
	}
	
	public String getTableRowFields(DatabaseConnection con, String table, HashMap<String,String> parms) {
/*		if (table.equals(MERGE_TABLE)) {
			String edit_id = (parms != null ? parms.get("EDIT_ID") : null);
			ODocument d = null;
			if (edit_id != null) {
				d = con.get(edit_id);
			}
			return input(PARM_PREFIX+"name",(d == null ? "" : d.field("name")))
					+getPathBuilder(con, d)
					+(edit_id == null ? "" : hidden("UPDATE_ID", edit_id));
		}
*/		return super.getTableRowFields(con, table, parms);
	}
	
	String getPathBuilder(DatabaseConnection con, ODocument d) {
		StringBuilder tableInit = new StringBuilder(); // JSON list of tables and groups
		StringBuilder columnInit = new StringBuilder();  // JSON list of tables and columns
		
		// Add tables in groups (similar code to Schema - should be combined in one place - need one more use
		QueryResult schemas = con.query("SELECT from tableGroup");
		QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
		ArrayList<String> tablesInGroups = new ArrayList<String>(); 
		for (ODocument schema : schemas.get()) {
			StringBuilder tablelist = new StringBuilder();
			String tablesf = schema.field("tables");
			String table[] = {};
			if (tablelist != null) {
				table = tablesf.split(",");
			}
			String groupName = (String)schema.field("name");
			tablelist.append(paragraph("banner", groupName));
			boolean groupHasTable = false;
			for (String tableName : table) {
				tableName = tableName.trim();
				boolean show = true;
				if (tableName.startsWith("-")) {
					show = false;
					tableName = tableName.substring(1);
				}
				tablesInGroups.add(tableName);
				if (show) {
					int privs = Server.getTablePriv(con, tableName);
					//System.out.println("Table privs for table "+tableName+" for user "+con.getUser()+" privs="+privs);
					if (privs > 0) {
						tableName = tableName.trim();
						if (tableInit.length()>0) tableInit.append(", ");
						tableInit.append("{ group:'"+groupName+"', table:'"+tableName+"'}");
						groupHasTable = true;
					}
				}
			}
		}
		
		// Add the non grouped (new) tables
		StringBuilder tablelist = new StringBuilder();
		tablelist.append(paragraph("banner",Message.get(con.getLocale(), "TABLE_NONGROUPED")));
		for (ODocument row : tables.get()) {
			String tablename = row.field("name");
			if (!tablesInGroups.contains(tablename)) {
				if (Server.getTablePriv(con, tablename) > 0) {
					if (tableInit.length()>0) tableInit.append(", ");
					tableInit.append("{ group:'New', table:'"+tablename+"'}");
				}
			}
		}

		// Columns
		for (ODocument t : tables.get()) {
			String tName = t.field("name");
			QueryResult cols = Server.getColumns(tName);
			for (ODocument col : cols.get()) {
				String cName = col.field("name");
				Integer cType = col.field("type");
				String cClass = col.field("linkedClass");
				if (columnInit.length()>0) columnInit.append(", ");
				columnInit.append("{ table:'"+tName+"', column:'"+cName+"', type:'"+Table.getTypeName(con.getLocale(),cType)+(cClass == null ? "" : " to "+cClass)+"'}");
			}
		}
		
	    return "<div "
	           +" ng-init=\"tables=["+tableInit+"]; columns=["+columnInit+"]; \">\n"
	    +table(0,
	    	row(column("From")
			    +column("")
			    +column("To")
			    +column("")
		     )
		     +row(column(
			    	"<select ng-model=\"selGroupF\"\n"
			    	+"  ng-options=\"v.group for v in tables | unique:'group'\" >\n"
			    	+"  <option value=\"\">None</option>\n"
			    	+"</select>\n")
			    +column("using key")
			    +column(
			    	"<select ng-model=\"selGroupT\"\n"
			    	+"  ng-options=\"v.group for v in tables | unique:'group'\" >\n"
			    	+"  <option value=\"\">None</option>\n"
			    	+"</select>\n")
			    +column("using key")
		     )
		     +row(
		    	column("<select ng-model=\"selTableF\"\n" 
		    		//	+" ng-change=\"fromTable=selTableF.table;\""
		    			+"  ng-options=\"v.table for v in tables | filter:{group:selGroupF.group} | orderBy:'table'\">\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<select ng-model=\"selColumnF\"\n" 
		    			+"  ng-options=\"v.column+' -'+v.type for v in columns | filter:{table:selTableF.table}\"\n" 
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<select ng-model=\"selTableT\"\n" 
		    			+"  ng-options=\"v.table for v in tables | filter:{group:selGroupT.group} | orderBy:'table'\">\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<select ng-model=\"selColumnT\"\n" 
		    			+"  ng-options=\"v.column+' -'+v.type for v in columns | filter:{table:selTableT.table}\"\n" 
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	)
		     +row(
		    		 //column("<input name="+PARM_PREFIX+"fromTable"+" value=\""+(d==null ? "" : d.field("fromTable"))+"\" ngModel=\"selTableF.table\">")
			    column(input(PARM_PREFIX+"fromTable",(d==null ? "" : d.field("fromTable"))+"{{selTableF.column}}"))
			    +column(input(PARM_PREFIX+"fromKey",(d==null ? "" : d.field("fromKey"))+"{{selColumnF.column}}"))
			    +column(input(PARM_PREFIX+"toTable","{{selTableT.table}}"))
			    +column(input(PARM_PREFIX+"toKey","{{selColumnT.column}}"))
		     )
		   )
	    +"</div>\n";
	}

	// Override these to change the names of the tables that will be created and used by this importer
	public static String MERGE_TABLE = "mergePath";   // Local OrientDB table name to hold connection specs
	public static String ATTR_TABLE = "mergePathAttribute";   // Saved path from a Oracle schema.table to a PermeAgility class/table
    	
	private void checkInstallation(DatabaseConnection con, StringBuilder errors) {
		// Verify the installation of the Merge table structures
		if (!Server.isDBA(con)) {
			return;
		}
		OSchema oschema = con.getSchema();
		
		OClass table = Setup.checkCreateClass(oschema, MERGE_TABLE, errors);
		Setup.checkCreateProperty(con, table, "name", OType.STRING, errors);
		Setup.checkCreateProperty(con, table, "fromTable", OType.STRING, errors);
		Setup.checkCreateProperty(con, table, "fromKey", OType.STRING, errors);
		Setup.checkCreateProperty(con, table, "toTable", OType.STRING, errors);
		Setup.checkCreateProperty(con, table, "toKey", OType.STRING, errors);
		Setup.checkCreateProperty(con, table, "created", OType.DATETIME, errors);
		Setup.checkCreateProperty(con, table, "executed", OType.DATETIME, errors);
		
		OClass logTable = Setup.checkCreateClass(oschema, ATTR_TABLE, errors);
		Setup.checkCreateProperty(con, logTable, "path", OType.LINK, table, errors);
		Setup.checkCreateProperty(con, logTable, "fromColumn", OType.STRING, errors);
		Setup.checkCreateProperty(con, logTable, "toColumn", OType.STRING, errors);
		Setup.checkCreateProperty(con, logTable, "linkProperty", OType.STRING, errors);

		Server.clearColumnsCache(MERGE_TABLE);
		Server.clearColumnsCache(ATTR_TABLE);
		
		INSTALLED = true;  //This will be checked every startup unless this flag is set true using a constant
	}

}
