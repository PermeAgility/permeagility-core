/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public class Query extends Weblet {
	
	public static boolean DEBUG = false;
	
	public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
		parms.put("SERVICE", Message.get(con.getLocale(), "SQL_WEBLET"));
		String query = (String)parms.get("SQL");
		return 	
			head(Message.get(con.getLocale(), "SQL_WEBLET")+"&nbsp;"+query,getSortTableScript())+
			body( standardLayout(con, parms,  
				getSQLBuilder(con)
				+form("QUERY","#",
					"<textarea spellcheck=\"false\" name=\"SQL\" rows=6 cols=80 text-build>"+(query==null ? "" : query)+"</textarea>"
					+br()
					+submitButton(Message.get(con.getLocale(), "EXECUTE_QUERY"))
				) 
				+br()
				+paragraph("banner",Message.get(con.getLocale(), "QUERY_RESULTS"))
				+anchor("TOP",Message.get(con.getLocale(), "RESULTS_TOP"))+"&nbsp;&nbsp;&nbsp;"
				+link("#BOTTOM",Message.get(con.getLocale(), "RESULTS_BOTTOM"))
				+paragraph(Message.get(con.getLocale(), "QUERY_IS")+"&nbsp;"+query)
				+table("sortable", getResult(con, query))
				+anchor("BOTTOM",Message.get(con.getLocale(), "RESULTS_BOTTOM"))+"&nbsp;&nbsp;&nbsp;"
				+link("#TOP",Message.get(con.getLocale(), "RESULTS_TOP"))		  
			));
	}

	public String getResult(DatabaseConnection con, String query) {
		if (query == null || query.equals("")) {
			return paragraph(Message.get(con.getLocale(), "NO_QUERY_GIVEN"));
		}
		if (!query.trim().toUpperCase().startsWith("SELECT")) {  // If not a select, then update
			return getUpdate(con,query);			
		}
		
		try {
			StringBuilder sb = new StringBuilder();
			int rowCount = 0;
			QueryResult rs = con.query(query);
			ArrayList<String> columns = new ArrayList<String>();
			sb.append(row(getRowHeader(rs,columns)));
			for (int i=0;i<rs.size();i++) {
				sb.append(row("data",getRow(con, rs,i, columns)));
				rowCount++;
			}
			sb.append(paragraph("RowCount="+rowCount));
			return sb.toString();
		} catch (Exception e) {
			Throwable cause = e.getCause();
			System.out.println("Error in SQL Weblet select: "+e.getMessage());
			e.printStackTrace();
			return paragraph("error",Message.get(con.getLocale(), "ERROR_IN_QUERY")+e.getMessage()+(cause == null ? "" : "<BR>"+cause.getMessage()));
		}
	}

	public String getUpdate(DatabaseConnection con, String query) {
		if (query == null || query.equals("")) {
			return Message.get(con.getLocale(), "NO_QUERY_GIVEN");
		}
		Object rc = null;
		boolean secRef = false;
		try {
			rc = con.update(query);
			if (rc != null && (query.trim().toUpperCase().startsWith("GRANT") || query.trim().toUpperCase().startsWith("REVOKE"))) {
				Server.refreshSecurity();
				secRef = true;
			}
		} catch (Exception e) {
			System.out.println("Error in SQL Weblet update: "+e.getMessage());
			e.printStackTrace();
			return paragraph("error",Message.get(con.getLocale(), "ERROR_IN_QUERY")+e.getMessage());
		}
		return Message.get(con.getLocale(), "ROWS_UPDATED",""+rc)+(secRef ? Message.get(con.getLocale(), "SECURITY_REFRESHED") : "");
	}


	public String getRowHeader(QueryResult rs, ArrayList<String> cols) {
		StringBuilder sb = new StringBuilder();
		String[] columns = rs.getColumns();
		sb.append(columnHeader("rid"));		
		if (columns != null) {
			for (String colName : columns) {
				sb.append(columnHeader(colName));
				cols.add(colName);
			}
			return sb.toString();
		} else { 
			return "";
		}
	}
	
	public String getRow(DatabaseConnection con, QueryResult rs, int r, ArrayList<String> columns) {
		StringBuilder sb = new StringBuilder();
		ODocument row = rs.get(r);
		if (row.getIdentity().toString().contains("-")) {
			sb.append(column(paragraphRight(row.getIdentity().toString())));
		} else {
			sb.append(column(paragraphRight(link("permeagility.web.Table?TABLENAME="+row.getClassName()+"&EDIT_ID="+row.getIdentity().toString().substring(1), row.getIdentity().toString(), "_blank"))));			
		}
		for (String colName : columns) {
			Object o = row.field(colName);
			if (DEBUG) System.out.println(colName+"="+(o == null ? "null" : o.getClass().getName()));
			if (o instanceof ODocument) {  // OrientDB Link  
				ODocument d = row.field(colName);
				sb.append(column(paragraphRight(d == null ? "null" : getDocumentLink(con, d))));
			} else if (o instanceof Boolean) { // OrientDB boolean
				sb.append(column(paragraphRight(""+row.field(colName))));
			} else if (o instanceof Byte) { // OrientDB boolean
				sb.append(column(paragraphRight(""+row.field(colName))));
			} else if (o instanceof ORecordBytes) { // PermeAgility Image/Blob or other?
				String out;
				//ORecordBytes or = (ORecordBytes)o;
				StringBuilder desc = new StringBuilder();
				String blobid = Thumbnail.getThumbnailId(row.getClassName(), row.getIdentity().toString().substring(1), colName, desc);
				if (blobid != null) {
					out = column(Thumbnail.getThumbnailLink(con.getLocale(),blobid, desc.toString()));
				} else {
					out = column(Message.get(con.getLocale(), "THUMBNAIL_NOT_FOUND",colName,row.getIdentity().toString()));					
				} 
				sb.append(out);
			} else if (o instanceof List) {  // LinkList
				@SuppressWarnings("unchecked")
				List<ODocument> l = (List<ODocument>)o;
				StringBuilder ll = new StringBuilder();
				if (l != null) {
					for (Object od : l) {
						if (od instanceof ODocument) {
							ODocument d = (ODocument)od;
							ll.append(getDocumentLink(con, d)+br());
						} else {
							ll.append(od+br());							
						}
					}
				}
				sb.append(column(ll.toString()));
			} else if (o instanceof Set) {  // LinkSet
				@SuppressWarnings("unchecked")
				Set<ODocument> l = (Set<ODocument>)o;
				StringBuilder ll = new StringBuilder();
				if (l != null) {
					for (Object od : l) {
						if (od instanceof ODocument) {
							ODocument d = (ODocument)od;
							ll.append(getDocumentLink(con, d)+br());
						} else {
							ll.append(od+br());							
						}
					}
				}
				sb.append(column(ll.toString()));
			} else if (o instanceof Map) {    // LinkMap
				@SuppressWarnings("unchecked")
				Map<String,ODocument> l = (Map<String,ODocument>)o;
				StringBuilder ll = new StringBuilder();
				if (l != null) {
					for (String k : l.keySet()) {
						Object d = l.get(k);
						if (d != null && d instanceof ODocument) {
							ll.append(k+":"+getDocumentLink(con, (ODocument)d)+br());
						} else {
							ll.append(k+":"+d+br());							
						}
					}
				}
				sb.append(column(ll.toString()));
			} else if (o instanceof Number) {
				sb.append(column(paragraphRight(""+row.field(colName))));
			} else {
				if (colName.toUpperCase().endsWith("PASSWORD")) {
					sb.append(column("---"));					
				} else {
					sb.append(column(""+row.field(colName)));
				}
			}
		}
		return sb.toString();
	}
	
	public String getDocumentLink(DatabaseConnection con, ODocument d) {
		if (d == null || d.getClassName() == null) {
			return ""+d;
		} else {
			return link("permeagility.web.Table?TABLENAME="+d.getClassName()+"&EDIT_ID="+d.getIdentity().toString().substring(1)
				, getDescriptionFromDocument(con, d)
				, "_blank");
		}
	}
	
	String getSQLBuilder(DatabaseConnection con) {
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
			//boolean groupHasTable = false;
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
				//		groupHasTable = true;
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
		
	    return "<div ng-controller=\"TextBuildControl\""
	           +" ng-init=\"tables=["+tableInit+"]; columns=["+columnInit+"];\">\n"
	    +table(0,
	    	row(columnSpan(5,"<select ng-model=\"statement\" ng-change=\"add(statement+' ')\">\n"
  				      +"  <option value=\"SELECT FROM\">SELECT [field, *] FROM class|rid [LET $a=(query)] [WHERE condition] [GROUP BY field, *] [ORDER BY field, *] [SKIP n] [LIMIT n]</option>\n"
				      +"  <option value=\"SELECT EXPAND( $c ) LET $a = ( SELECT FROM t1 ), $b = ( SELECT FROM t2 ), $c = UNIONALL( $a, $b )\">SELECT (2 Table union template - replace t1 and t2)</option>\n"
				      +"  <option value=\"SELECT expand(classes) FROM metadata:schema\">SELECT (list of tables/classes)</option>\n"
				      +"  <option value=\"SELECT FROM (SELECT expand(properties) FROM (SELECT expand(classes) FROM metadata:schema) WHERE name = 't')\">SELECT (column list for a table - replace t)</option>\n"
				      +"  <option value=\"TRAVERSE\">TRAVERSE class.field|*|any()|all() FROM class|rid|query [LET var*] WHILE $depth&lt;n [LIMIT n] [STRATEGY s] </option>\n"
				      +"  <option value=\"EXPLAIN SELECT FROM\">EXPLAIN query</option>\n"
				      +"  <option value=\"INSERT INTO\">INSERT INTO class [SET field=value, *] [FROM query] [RETURN ret]</option>\n"
				      +"  <option value=\"UPDATE\">UPDATE [SET field=val *] [UPSERT] [WHERE condition *] [LIMIT n] [RETURN ret]</option>\n"
				      +"  <option value=\"DELETE FROM\">DELETE class [WHERE condition *] [LIMIT n] [RETURN ret]</option>\n"
				      +(Server.isDBA(con)
				      	? "  <option value=\"CREATE CLASS\">CREATE CLASS class [EXTENDS super] [CLUSTER name, *]</option>\n"
					      +"  <option value=\"CREATE CLUSTER\">CREATE CLUSTER name [POSITION position|APPEND] [CLUSTER name, *]</option>\n"
					      +"  <option value=\"CREATE PROPERTY\">CREATE PROPERTY class.property type [linkedClass]</option>\n"
					      +"  <option value=\"CREATE INDEX\">CREATE INDEX name|class.property [ON class (property, *)] UNIQUE|NOTUNIQUE|FULLTEXT</option>\n"
					      +"  <option value=\"CREATE LINK\">CREATE LINK name TYPE LINK|LINKSET|LINKLIST FROM class.property TO class.property [INVERSE]</option>\n"
					      +"  <option value=\"ALTER CLASS\">ALTER CLASS class NAME|SHORTNAME|SUPERCLASS|OVERSIZE|ADDCLUSTER|REMOVECLUSTER|STRICTMODE value</option>\n"
					      +"  <option value=\"ALTER CLUSTER\">ALTER CLUSTER name|id NAME|DATASEGMENT|COMPRESSION|USE_WAL|RECORD_GROW_FACTOR|CONFLICTSTRATEGY value</option>\n"
					      +"  <option value=\"ALTER PROPERTY\">ALTER PROPERTY class.property NAME|LINKEDCLASS|MIN|MAX|MANDATORY|NOTNULL|REGEXP|TYPE|COLLATE value</option>\n"
					      +"  <option value=\"DROP\">DROP CLASS|CLUSTER|INDEX|PROPERTY value</option>\n"
					      +"  <option value=\"TRUNCATE\">TRUNCATE CLASS|CLUSTER|RECORD name|rid</option>\n"
					      +"  <option value=\"GRANT\">GRANT NONE|CREATE|READ|UPDATE|DELETE|ALL ON class|resource TO role</option>\n"
					      +"  <option value=\"REVOKE\">REVOKE NONE|CREATE|READ|UPDATE|DELETE|ALL ON class|resource FROM role</option>\n"
					      +"  <option value=\"TRAVERSE\">TRAVERSE class.field|*|any()|all() FROM class|rid|query [LET var*] WHILE $depth&lt;n [LIMIT n] [STRATEGY s] </option>\n"
					    : "")
				      +"  <option value=\"\">None</option>\n"
				      +"</select>\n"))
	    	+row(column("")
	    		+column(
			    	"<select ng-model=\"selGroup\"\n"
			    	+"  ng-options=\"v.group for v in tables | unique:'group'\" >\n"
			    	+"  <option value=\"\">None</option>\n"
			    	+"</select>\n")
			    +column("")
			    +column(
			      "<select ng-model=\"selTable\"\n"
			      +"      ng-options=\"v.table for v in tables | orderBy:'table'\" >\n"
			      +"  <option value=\"\">None</option>\n"
			      +"</select>\n")
			     +column("")
		     )
		     +row(column("<button ng-click=\"add('SELECT FROM ')\">SELECT FROM</button>\n")
		    	+column("<select ng-model=\"selTable\"\n" 
		    			+"  ng-change=\"add(selTable.table+' ')\"\n"
		    			+"  ng-options=\"v.table for v in tables | filter:{group:selGroup.group} | orderBy:'table'\">\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<button ng-click=\"add('WHERE ')\">WHERE</button>\n")
		    	+column("<select ng-model=\"selColumn\"\n" 
		    			+"  ng-options=\"v.column+' -'+v.type for v in columns | filter:{table:selTable.table}\"\n" 
		    			+"  ng-change=\"add(selColumn.column+' ')\">\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	+column("<select ng-model=\"selOperator\" ng-change=\"add(selOperator+' ')\">\n"
		    			+"  <option value=\"=\">=</option>\n"
		    			+"  <option value=\"!=\">!=</option>\n"
		    			+"  <option value=\"<\">&lt;</option>\n"
		    			+"  <option value=\">\">&gt;</option>\n"
		    			+"  <option value=\"LIKE\">LIKE</option>\n"
		    			+"  <option value=\"CONTAINS\">CONTAINS</option>\n"
		    			+"  <option value=\"CONTAINSKEY\">CONTAINSKEY</option>\n"
		    			+"  <option value=\"CONTAINSVALUE\">CONTAINSVALUE</option>\n"
		    			+"  <option value=\"\">None</option>\n"
		    			+"</select>\n")
		    	)
		    )
	    +"</div>\n";
	}
}
