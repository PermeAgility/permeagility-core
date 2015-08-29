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
package permeagility.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.metadata.schema.OProperty;
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
                        "<textarea spellcheck=\"false\" name=\"SQL\" rows=6 cols=100 text-build>"+(query==null ? "" : query)+"</textarea>"
                        +br()
                        +submitButton(con.getLocale(), "EXECUTE_QUERY")
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
        if (!(query.trim().toUpperCase().startsWith("SELECT")
            || query.trim().toUpperCase().startsWith("TRAVERSE"))) {  // If not a select, then update
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
                Security.refreshSecurity();
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

        // Add tables in groups (similar code to Schema - should be combined in one place - need one more use)
        QueryResult schemas = con.query("SELECT from tableGroup");
        QueryResult tables = con.query("SELECT name, superClass FROM (SELECT expand(classes) FROM metadata:schema) WHERE abstract=false ORDER BY name");
        ArrayList<String> tablesInGroups = new ArrayList<String>(); 
        for (ODocument schema : schemas.get()) {
            StringBuilder tablelist = new StringBuilder();
            String tablesf = schema.field("tables");
            String table[] = {};
            if (tablesf != null) {
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
                    int privs = Security.getTablePriv(con, tableName);
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
                if (Security.getTablePriv(con, tablename) > 0) {
                    if (tableInit.length()>0) tableInit.append(", ");
                    tableInit.append("{ group:'New', table:'"+tablename+"'}");
                }
            }
        }

        // Columns - this will be slow as tables grow (could do an angular get when user selects table - later)
        for (ODocument t : tables.get()) {
            String tName = t.field("name");
            for (OProperty col : con.getColumns(tName)) {
                String cName = col.getName();
                Integer cType = col.getType().getId();
                String cClass = col.getLinkedClass() != null ? col.getLinkedClass().getName() : null;
                if (columnInit.length()>0) columnInit.append(", ");
                columnInit.append("{ table:'"+tName+"', column:'"+cName+"', type:'"+(cType == null ? "" : Table.getTypeName(con.getLocale(),cType))+(cClass == null ? "" : " to "+cClass)+"'}");
            }
        }

    return "<div ng-controller=\"TextBuildControl\""
           +" ng-init=\"tables=["+tableInit+"]; columns=["+columnInit+"];\">\n"
    +table(
        row(column("")
            +column(
                    "<select ng-model=\"selGroup\"\n"
                    +"  ng-options=\"v.group for v in tables | unique:'group'\" >\n"
                    +"  <option value=\"\">Table Group</option>\n"
                    +"</select>\n")
                +column("")
                +column(
                  "<select ng-model=\"selTable\"\n"
                  +"      ng-options=\"v.table for v in tables | orderBy:'table'\" >\n"
                  +"  <option value=\"\">Table</option>\n"
                  +"</select>\n")
         )
         +row(column("label","<button ng-click=\"add('SELECT FROM ')\">SELECT FROM</button>\n")
            +column("<select ng-model=\"selTable\"\n" 
                            +"  ng-change=\"add(selTable.table+' ')\"\n"
                            +"  ng-options=\"v.table for v in tables | filter:{group:selGroup.group} | orderBy:'table'\">\n"
                            +"  <option value=\"\">Table</option>\n"
                            +"</select>\n")
            +column("label","<button ng-click=\"add('WHERE ')\">WHERE</button>\n")
            +column("<select ng-model=\"selColumn\"\n" 
                            +"  ng-options=\"v.column+' -'+v.type for v in columns | filter:{table:selTable.table}\"\n" 
                            +"  ng-change=\"add(selColumn.column+' ')\">\n"
                            +"  <option value=\"\">Column</option>\n"
                            +"</select>\n")
            )
        +row(columnSpan(4,"<select ng-model=\"statement\" ng-change=\"add(statement+' ')\">\n"
            +"  <option value=\"SELECT FROM\">SELECT [field, *] FROM class|rid [LET $a=(query)] [WHERE condition] [GROUP BY field, *] [ORDER BY field, *] [SKIP n] [LIMIT n]</option>\n"
            +"  <option value=\"SELECT EXPAND( $c ) LET $a = ( SELECT FROM t1 ), $b = ( SELECT FROM t2 ), $c = UNIONALL( $a, $b )\">SELECT (2 Table union template - replace t1 and t2)</option>\n"
            +"  <option value=\"SELECT expand(classes) FROM metadata:schema\">SELECT (list of tables/classes)</option>\n"
            +"  <option value=\"SELECT FROM (SELECT expand(properties) FROM (SELECT expand(classes) FROM metadata:schema) WHERE name = 't')\">SELECT (column list for a table - replace t)</option>\n"
            +"  <option value=\"TRAVERSE\">TRAVERSE class.field|*|any()|all() FROM class|rid|query [LET var*] WHILE $depth&lt;n [LIMIT n] [STRATEGY s] </option>\n"
            +"  <option value=\"EXPLAIN SELECT FROM\">EXPLAIN query</option>\n"
            +"  <option value=\"INSERT INTO\">INSERT INTO class [SET field=value, *] [FROM query] [RETURN ret]</option>\n"
            +"  <option value=\"UPDATE\">UPDATE [SET field=val *] [UPSERT] [WHERE condition *] [LIMIT n] [RETURN ret]</option>\n"
            +"  <option value=\"DELETE FROM\">DELETE class [WHERE condition *] [LIMIT n] [RETURN ret]</option>\n"
            +(Security.isDBA(con)
              ? "  <option value=\"CREATE CLASS\">CREATE CLASS class [EXTENDS super] [CLUSTER name, *]</option>\n"
                    +"  <option value=\"CREATE CLUSTER\">CREATE CLUSTER name [POSITION position|APPEND] [CLUSTER name, *]</option>\n"
                    +"  <option value=\"CREATE PROPERTY\">CREATE PROPERTY class.property type [linkedClass]</option>\n"
                    +"  <option value=\"CREATE INDEX\">CREATE INDEX name|class.property [ON class (property, *)] UNIQUE|NOTUNIQUE|FULLTEXT</option>\n"
                    +"  <option value=\"CREATE LINK\">CREATE LINK name [TYPE LINK|LINKSET|LINKLIST] FROM class.property TO class.property [INVERSE]</option>\n"
                    +"  <option value=\"ALTER CLASS\">ALTER CLASS class NAME|SHORTNAME|SUPERCLASS|OVERSIZE|ADDCLUSTER|REMOVECLUSTER|STRICTMODE value</option>\n"
                    +"  <option value=\"ALTER CLUSTER\">ALTER CLUSTER name|id NAME|DATASEGMENT|COMPRESSION|USE_WAL|RECORD_GROW_FACTOR|CONFLICTSTRATEGY value</option>\n"
                    +"  <option value=\"ALTER PROPERTY\">ALTER PROPERTY class.property NAME|LINKEDCLASS|MIN|MAX|MANDATORY|NOTNULL|REGEXP|TYPE|COLLATE value</option>\n"
                    +"  <option value=\"DROP\">DROP CLASS|CLUSTER|INDEX|PROPERTY value</option>\n"
                    +"  <option value=\"TRUNCATE CLASS\">TRUNCATE CLASS|CLUSTER|RECORD name|rid</option>\n"
                    +"  <option value=\"GRANT\">GRANT NONE|CREATE|READ|UPDATE|DELETE|ALL ON class|resource TO role</option>\n"
                    +"  <option value=\"REVOKE\">REVOKE NONE|CREATE|READ|UPDATE|DELETE|ALL ON class|resource FROM role</option>\n"
                  : "")
            +"  <option value=\"\">Query/Command Templates</option>\n"
            +"</select>\n"))
        +row(columnSpan(2,"<select ng-model=\"selFunction\" ng-change=\"add(selFunction)\">\n"
            +"  <option value=\"\">Function</option>\n"
                +"  <option value=\"eval( )\">eval(formula) - can use property names and math operators in formula</option>\n"
                +"  <option value=\"format( , )\">format('%s %d',str,int) see: java.util.Formatter</option>\n"
                +"  <option value=\"coalesce( )\">coalesce(p1, p2, p3...) return the first not null</option>\n"
                +"  <option value=\"distinct( )\">distinct(property) return only unique items</option>\n"
                +"  <option value=\"if( , , )\">if(expression, ifTrue, ifFalse)</option>\n"
                +"  <option value=\"ifnull( , )\">ifnull(property, valueIfNull)</option>\n"
                +"  <option value=\"expand( )\">expand(property) extract a collection as a result</option>\n"
                +"  <option value=\"union( , )\">union(p1, p2) aggregate collections as a result</option>\n"
                +"  <option value=\"intersect( )\">intersect(p1, p2, p3...) returns intersection of lists</option>\n"
                +"  <option value=\"difference( )\">difference(p1, p2, p3...) returns difference between lists</option>\n"
                +"  <option value=\"first( )\">first(property) return the first in a list property</option>\n"
                +"  <option value=\"last( )\">last(property) return the last in a list property</option>\n"
                +"  <option value=\"count( )\">count(property) return the count of items in a list property</option>\n"
                +"  <option value=\"min( )\">min(p1, p2, p3...) return the minimum value</option>\n"
                +"  <option value=\"max( )\">max(p1, p2, p3...) return the maximum value</option>\n"
                +"  <option value=\"avg( )\">avg(property) return the average</option>\n"
                +"  <option value=\"stddev( )\">stddev(property) return the standard deviation</option>\n"
                +"  <option value=\"median( )\">median(property) return the middle value</option>\n"
                +"  <option value=\"percentile( )\">percentile(property, quantile...) return the nth percentiles</option>\n"
                +"  <option value=\"mode( )\">mode(property) return the most frequent value</option>\n"
                +"  <option value=\"variance( )\">variance(property) return the middle variance</option>\n"
                +"  <option value=\"date( , )\">date(string,format[,timezone]) return the string as date</option>\n"
                +"  <option value=\"sysdate()\">sysdate([format] [, timezone]) return the system date</option>\n"
                +"  <option value=\"distance( , , , )\">distance(x1, y1, x2, y2) coordinated must be degrees</option>\n"
                +"  <option value=\"set( )\">set(property) returns a set created from a property</option>\n"
                +"  <option value=\"list( )\">list(property) returns a list created from a property</option>\n"
                +"  <option value=\"map( , )\">map(key,value) returns a map created from a key/value</option>\n"
                +"  <option value=\"uuid()\">uuid() returns a generated 128-bit value</option>\n"
                +"</select>\n")
        +columnSpan(2,"<select ng-model=\"selOperator\" ng-change=\"add(selOperator+' ')\">\n"
                // Operators
            +"  <option value=\"\">Operator</option>\n"
                +"  <option value=\"=\">= equals</option>\n"
                +"  <option value=\"<>\">&lt;&gt; not equal</option>\n"
                +"  <option value=\"<\">&lt; less than</option>\n"
                +"  <option value=\"<=\">&lt;= less than or equal</option>\n"
                +"  <option value=\">\">&gt; greater than</option>\n"
                +"  <option value=\">=\">&gt;= greater than or equal</option>\n"
                +"  <option value=\"[ ]\">property[element] [name='x']|[0-3]</option>\n"
                +"  <option value=\"AND\">condition AND condition</option>\n"
                +"  <option value=\"OR\">condition OR condition</option>\n"
                +"  <option value=\"BETWEEN\">field BETWEEN value1 AND value2</option>\n"
                +"  <option value=\"CONTAINS\">list CONTAINS item|items</option>\n"
                +"  <option value=\"CONTAINSALL\">list CONTAINSALL (field=value)</option>\n"
                +"  <option value=\"CONTAINSKEY\">map CONTAINSKEY key</option>\n"
                +"  <option value=\"CONTAINSVALUE\">map CONTAINSVALUE value</option>\n"
                +"  <option value=\"CONTAINSTEXT\">string CONTAINSTEXT value</option>\n"
                +"  <option value=\"IN\">field|list IN list</option>\n"
                +"  <option value=\"INSTANCEOF\">@class INSTANCEOF classname</option>\n"
                +"  <option value=\"IS NULL\">field IS NULL|NOT NULL</option>\n"
                +"  <option value=\"LIKE\">string LIKE value (%=wildcard)</option>\n"
                +"  <option value=\"MATCHES\">string MATCHES regexp</option>\n"
                +"  <option value=\"any()\">any() matches any field</option>\n"
                +"  <option value=\"all()\">all() matches all fields</option>\n"
                +"</select>\n"))
        +row(columnSpan(2,"<select ng-model=\"selAttribute\" ng-change=\"add(selAttribute+' ')\">\n"
                // Attributes
            +"  <option value=\"\">Attribute</option>\n"
                +"  <option value=\"@class\">@class returns the class name</option>\n"
                +"  <option value=\"@rid\">@class returns the rid</option>\n"
                +"  <option value=\"@version\">@version returns the record version</option>\n"
                +"  <option value=\"@size\">@class returns the record size in bytes</option>\n"
                +"  <option value=\"@type\">@type returns the class type</option>\n"
                +"  <option value=\"$parent\">$parent context from subquery</option>\n"
                +"  <option value=\"$current\">$current record ($parent.$current)</option>\n"
                +"  <option value=\"$depth\">$depth current depth of nesting in traversal</option>\n"
                +"  <option value=\"$path\">$path representation of the current path</option>\n"
                +"  <option value=\"$stack\">$stack history of the traversal</option>\n"
                +"  <option value=\"$history\">$history all records traversed as a set<ORID></option>\n"
                +"</select>\n")
        +columnSpan(2,"<select ng-model=\"selMethod\" ng-change=\"add(selMethod+' ')\">\n"
            // Methods
            +"  <option value=\"\">Method</option>\n"
            +"  <option value=\".append( )\">.append(string) appends a string to another</option>\n"
            +"  <option value=\".asBoolean()\">.asBoolean() convert to boolean (true/false, 1/0)</option>\n"
            +"  <option value=\".asDate()\">.asDate() convert to date</option>\n"
            +"  <option value=\".asDateTime()\">.asDateTime() convert to datetime</option>\n"
            +"  <option value=\".asDecimal()\">.asDecimal() convert to decimal</option>\n"
            +"  <option value=\".asFloat()\">.asFloat() convert to float</option>\n"
            +"  <option value=\".asInteger()\">.asInteger() convert to integer</option>\n"
            +"  <option value=\".asList()\">.asList() convert to list</option>\n"
            +"  <option value=\".asLong()\">.asLong() convert to long</option>\n"
            +"  <option value=\".asMap()\">.asMap() convert to map</option>\n"
            +"  <option value=\".asSet()\">.asSet() convert to set</option>\n"
            +"  <option value=\".asString()\">.asString() convert to string</option>\n"
            +"  <option value=\".charAt( )\">.charAt(position) get char at position</option>\n"
            +"  <option value=\".convert( )\">.convert(type) convert to type</option>\n"
            +"  <option value=\".exclude( )\">.exclude(prop) exclude property from doc</option>\n"
            +"  <option value=\".format( )\">.format() formats the value see: java.util.Formatter</option>\n"
            +"  <option value=\".hash( )\">.hash(alg) returns the hash of the field</option>\n"
            +"  <option value=\".include( , )\">.include(prop,...) includes only the properties</option>\n"
            +"  <option value=\".indexOf( , )\">.indexOf(string[,pos]) index of string in field</option>\n"
            +"  <option value=\".javaType()\">.javaType() returns the java type of the field</option>\n"
            +"  <option value=\".keys()\">.keys() returns a map field's keys</option>\n"
            +"  <option value=\".left()\">.left(len) returns the left len chars of the field</option>\n"
            +"  <option value=\".length()\">.length() returns the length of the field</option>\n"
            +"  <option value=\".normalize( )\">.normalize(form[,pattern]) returns the normalized field</option>\n"
            +"  <option value=\".prefix( )\">.prefix(string) returns the field with the string in front</option>\n"
            +"  <option value=\".remove( )\">.remove(item,...) remove the first occurrence of the item(s)</option>\n"
            +"  <option value=\".removeAll( )\">.removeAll(item,...) remove all occurrences of the item(s)</option>\n"
            +"  <option value=\".replace( ,)\">.replace(this,with) returns the first of the item(s)</option>\n"
            +"  <option value=\".right()\">.right(len) returns the right len chars of the field</option>\n"
            +"  <option value=\".size()\">.size() returns the size of the collection</option>\n"
            +"  <option value=\".substring()\">.substring(start[,len]) returns the portion of the string</option>\n"
            +"  <option value=\".trim()\">.trim() returns the field without leading/trailing spaces</option>\n"
            +"  <option value=\".toJSON()\">.toJSON([format]) returns record in JSON format</option>\n"
            +"  <option value=\".toLowerCase()\">.toLowerCase() returns string in lower case</option>\n"
            +"  <option value=\".toUpperCase()\">.toUpperCase() returns string in upper case</option>\n"
            +"  <option value=\".type()\">.type() returns the field type</option>\n"
            +"  <option value=\".values()\">.values() returns the map's values</option>\n"
            +"</select>\n"))
            )
	    +"</div>\n";
	}
}


