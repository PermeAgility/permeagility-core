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

import java.util.Collection;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import java.util.List;
import java.util.Set;
import java.util.Stack;

import com.arcadedb.database.Document;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Type;

import static permeagility.web.Table.PRIV_READ;
import static permeagility.web.Table.SHOW_ALL_RELATED_TABLES;

public class VisuilityData extends Download {

    public static boolean DEBUG = false;
    
    @Override
    public String getContentType() { return "application/json";  }

    @Override
    public String getContentDisposition() { return "inline; filename=\"data.json\""; }

    @Override
    public byte[] getBytes(DatabaseConnection con, HashMap<String, String> parms) {

            String type = parms.get("TYPE");
            String id = parms.get("ID");
            String detail = parms.get("DETAIL");
            if (DEBUG) System.out.println("VisuilityData: TYPE="+type+" ID="+id+" DETAIL="+detail);
            
            try {
                if ( id != null && !id.equals("")) {
                    if ("ROW".equalsIgnoreCase(type)) {
                        return getRow(con,id,detail);
                    } else if ("TABLE".equalsIgnoreCase(type)) {
                        return getTable(con,id,detail);
                    } else if ("COLUMN".equalsIgnoreCase(type)) {
                        return getColumn(con,id,detail);
                    } else if ("DATA".equalsIgnoreCase(type)) {
                        return getData(con,id,detail);
                    } else if ("SET".equalsIgnoreCase(type)) {
                        return getSet(con,id,detail);
                    } else {
                        return ("{ \"error\": \"Unknown type: "+type+"\"}").getBytes();			            
                    }
                } else {
                    return ("{ \"error\": \""+"ID not specified"+"\"}").getBytes();			                    
                }
            } catch(Exception e) {
                    e.printStackTrace();
                    return ("{ \"error\": \""+e.getMessage()+"\"}").getBytes();			
            }
    }

    private byte[] getTable(DatabaseConnection con, String table, String detail) {
            StringBuilder nodes = new StringBuilder();
            StringBuilder links = new StringBuilder();
            String linkComma = "";
            String nodeComma = "";

            nodes.append("{ \"id\": \"table."+table+"\", \"name\":\""+table+"\", \"description\":\"table "+table+"\" }");
            nodeComma = "\n,";

            // Related tables
            for (DocumentType c : con.getSchema().getTypes()) {
                    for (Property p : c.getProperties()) {
                            if (p.getOfType() != null && p.getOfType().equals(table)) {
                                    nodes.append(nodeComma+"{ \"id\":\"table."+c.getName()+"\""
                                                    + ",\"name\":\""+c.getName()+"\", \"description\":\"table "+c.getName()+"\""
                                                    + " }");
                                    nodeComma = "\n,";
                                    links.append(linkComma+"{ \"targetId\": \"table."+table+"\", \"sourceId\":\"table."+c.getName()+"\" }");
                                    linkComma = "\n,";
                            }
                    }
            }

            // Rows - R
            if (detail != null && detail.equalsIgnoreCase("R")) {
                QueryResult rows = con.query("SELECT FROM "+table);
                String rowTarget = "table."+table+"";
                int rowCount = 0;
                for (Document row : rows.get()) {
                    rowCount++;
                    String desc = Weblet.getDescriptionFromDocument(con, row);
                    nodes.append(nodeComma+"{ \"id\":\"row."+row.getIdentity().toString().substring(1)+"\""
//                        + ",\"name\":\""+row.getIdentity().toString().substring(1)+"\""
                        + ",\"name\":\""+(desc != null ? desc : row.getIdentity().toString().substring(1))+"\""
                        + ",\"description\":\""+table+" "+row.getIdentity().toString().substring(1)+" "+desc+"\""
                        + ", \"count\":"+rowCount
                        + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"targetId\":\""+rowTarget+"\""
                        +", \"sourceId\":\"row."+row.getIdentity().toString().substring(1)+"\""
                        + ", \"chain\": true, \"count\":"+rowCount
                        +" }");
                    linkComma = "\n,";
                    rowTarget = "row."+row.getIdentity().toString().substring(1);
                }
            }

            // Columns - C
            Collection<Property> cols = con.getColumns(table);
            String columnTarget = "table."+table;
            int colCount = 0;
            columnTarget = "table."+table+"";
            for (Property col : cols) {
                colCount++;
                if (detail != null && detail.equalsIgnoreCase("C") && !col.getName().startsWith("_")) {
                    nodes.append(nodeComma+"{ \"id\":\"column."+table+"."+col.getName()+"\""
                        + ",\"name\":\""+col.getName()+"\", \"description\":\"column "+table+"."+col.getName()+" "+col.getType().name()+"\""
                        + ", \"count\":"+colCount
                        + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"targetId\": \""+columnTarget+"\", \"sourceId\":\"column."+table+"."+col.getName()+"\", \"chain\": true }");
                    linkComma = "\n,";
                    columnTarget = "column."+table+"."+col.getName();
                }
                // Connect to a table if the property is a relationship
                String lc = col.getOfType() != null ? col.getOfType() : null; 
                if (lc != null) {
                    nodes.append(nodeComma+"{ \"id\":\"table."+lc+"\""
                        + ",\"name\":\""+lc+"\", \"description\":\"table "+lc+"\""
                        + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"sourceId\": \"table."+table+"\", \"targetId\":\"table."+lc+"\" }");
                    linkComma = "\n,";
                }
            }
            return assembleResult(nodes,links);

    }

    private byte[] getColumn(DatabaseConnection con, String table, String detail) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder links = new StringBuilder();
        String linkComma = "";
        String nodeComma = "";

        //nodes.append("{ \"id\": \"column."+table+"\", \"name\":\""+table+"\" }");
        //nodeComma = "\n,";

        // Related tables
        int relCount = 0;
        for (DocumentType c : con.getSchema().getTypes()) {
            for (Property p : c.getProperties()) {
                if (p.getOfType() != null && p.getOfType().equals(table)) {
                    relCount++;
                    nodes.append(nodeComma+"{ \"id\":\"table."+c.getName()+"\""
                        + ",\"name\":\""+c.getName()+"\", \"description\":\"table "+c.getName()+"\""
                        + ", \"count\":"+relCount
                        + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"targetId\": \"table."+table+"\", \"sourceId\":\"table."+c.getName()+"\" }");
                    linkComma = "\n,";
                }
            }
        }

        // Rows - R
        if (detail != null && detail.equalsIgnoreCase("R")) {
            QueryResult rows = con.query("SELECT FROM "+table);
            String rowTarget = "table."+table+"";
            int rowCount = 0;
            for (Document row : rows.get()) {
                rowCount++;
                nodes.append(nodeComma+"{ \"id\":\"row."+row.getIdentity().toString().substring(1)+"\""
                    + ",\"name\":\""+Weblet.getDescriptionFromDocument(con, row)+"\""
                    + ", \"count\":"+rowCount
                    + " }");
                nodeComma = "\n,";
                links.append(linkComma+"{ \"targetId\":\""+rowTarget+"\""
                    +", \"sourceId\":\"row."+row.getIdentity().toString().substring(1)+"\""
                    + ", \"count\":"+rowCount
                    +" }");
                linkComma = "\n,";
                rowTarget = "row."+row.getIdentity().toString().substring(1);
            }
        }

        // Columns - C
        Collection<Property> cols = con.getColumns(table);
        String columnTarget = "table."+table;
        int colCount = 0;
        columnTarget = "table."+table+"";
        for (Property col : cols) {
            colCount++;
            if (detail != null && detail.equalsIgnoreCase("C")) {
                nodes.append(nodeComma+"{ \"id\":\"column."+table+"."+col.getName()+"\""
                    + ",\"name\":\""+col.getName()+"\", \"description\":\"column "+table+"."+col.getName()+" "+col.getType().name()+"\""
                    + ", \"count\":"+colCount
                    + " }");
                nodeComma = "\n,";
                links.append(linkComma+"{ \"targetId\": \""+columnTarget+"\", \"sourceId\":\"column."+table+"."+col.getName()+"\", \"chain\": true }");
                linkComma = "\n,";
                columnTarget = "column."+table+"."+col.getName();
            }
            // Connect to a table if the property is a relationship
            String lc = col.getOfType() != null ? col.getOfType() : null; 
            if (lc != null) {
                nodes.append(nodeComma+"{ \"id\":\"table."+lc+"\""
                    + ",\"name\":\""+lc+"\", \"description\":\"table "+lc+"\""
                    + " }");
                nodeComma = "\n,";
                links.append(linkComma+"{ \"sourceId\": \"table."+table+"\", \"targetId\":\"table."+lc+"\" }");
                linkComma = "\n,";
            }
        }
        return assembleResult(nodes,links);
    }

    @SuppressWarnings("unchecked")
    private byte[] getRow(DatabaseConnection con, String id, String detail) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder links = new StringBuilder();
        String linkComma = "";
        String nodeComma = "";
        Document viewDoc = con.get(id);
        if (viewDoc == null) {
            return ("Could not retrieve row using id:"+id).getBytes();
        } else {
            String classname = viewDoc.getTypeName();
            Collection<Property> cols = con.getColumns(classname);
            String classDesc = Weblet.getDescriptionFromDocument(con, viewDoc);
            nodes.append("{ \"id\": \"row."+id+"\", \"name\":\""+classDesc+"\""
                    +", \"description\":\"row "+id+" "+classname+" "+classDesc+"\" }");
            nodeComma = "\n,";
            if (detail != null && detail.equalsIgnoreCase("T")) {
                // Make a table node for the row
                String table = viewDoc.getTypeName();
                nodes.append(nodeComma+"{ \"id\":\"table."+table+"\""
                    + ",\"name\":\""+table+"\", \"description\":\"table "+table+"\""
                    + " }");
                nodeComma = "\n,";
                // and link it to row
                links.append(linkComma+"{ \"sourceId\": \"row."+id+"\", \"targetId\":\"table."+table+"\" }");
                linkComma = "\n,";
            }
            String dataTarget = "row."+id;  // For stringing data columns together in order
            int colCount = 0;
            for (Property col : cols) {
                String colName = col.getName();
                Object colData = viewDoc.get(colName);
                if (colData instanceof Document) {
                    Document ld = (Document)colData;
                    String refId = ld.getIdentity().toString().substring(1);
                    String docName = Weblet.getDescriptionFromDocument(con, ld);
                    // Should be a link to the other row
                    nodes.append(nodeComma+"{ \"id\":\"row."+refId+"\""
                            + ",\"name\":\""+docName+"\""
                            + ",\"description\":\"row "+refId+" "+ld.getTypeName()+" "+docName+"\""
                            + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"targetId\":\"row."+refId+"\""
                            +", \"sourceId\":\"row."+id+"\""
                            +" }");
                    linkComma = "\n,";
                 } else if (colData instanceof List) {
                    List<Document> set = (List<Document>)colData;
                    for (Document ld : set) {
                        String refId = ld.getIdentity().toString().substring(1);
                        String docName = Weblet.getDescriptionFromDocument(con, ld);
                        // Should be a link to the other row
                        nodes.append(nodeComma+"{ \"id\":\"row."+refId+"\""
                                + ",\"name\":\""+docName+"\""
                                + ",\"description\":\"row "+refId+" "+ld.getTypeName()+" "+docName+"\""
                                + " }");
                        nodeComma = "\n,";
                        links.append(linkComma+"{ \"targetId\":\"row."+refId+"\""
                                +", \"sourceId\":\"row."+id+"\""
                                +" }");
                        linkComma = "\n,";
                    }
                } else {   // Data - D
                    if (detail != null && detail.equalsIgnoreCase("D")) {
                        if (colData != null) {
                            colData = colData.toString().replace("\r","").replace("\n","<BR>").replace("\\","\\\\").replace("'","\\u0027").replace("<","&lt;").replace(">","&gt;");
                        }
                        nodes.append(nodeComma+"{ \"id\":\"data."+id+"."+colName+"\""
                            + ",\"name\":\""+colData+"\""
                            + ",\"description\":\"data "+id+"."+colName+"\""
                            + " }");
                        nodeComma = "\n,";
                        links.append(linkComma+"{ \"sourceId\":\"data."+id+"."+colName+"\", \"targetId\":\""+dataTarget+"\", \"chain\": true, \"count\":"+colCount+"}");
                        linkComma = "\n, ";
                        dataTarget = "data."+id+"."+colName;
                    }
                }
            }
            
            Stack<String> tables = new Stack<>();
            Stack<String> columns = new Stack<>();
            Stack<Type> types = new Stack<>();

            for (DocumentType c : con.getSchema().getTypes()) {
                for (Property p : c.getProperties()) {
                    if (p.getOfType() != null && p.getOfType().equals(classname)) {
                        if (SHOW_ALL_RELATED_TABLES || (Security.getTablePriv(con, c.getName()) & PRIV_READ) > 0) {
                            tables.push(c.getName());
                            columns.push(p.getName());
                            types.push(p.getType());
                        }
                    }
                }
            }
            while (!tables.empty()) {
                String relTable = tables.pop();
                String col = columns.pop();
                Type fkType = types.pop();
                String operator;
                if ((Security.getTablePriv(con, relTable) & PRIV_READ) > 0) {
                    if (fkType == Type.LIST) {
                        operator = "contains";
                    } else if (fkType == Type.MAP) {
                        operator = "constainsvalue";
                    } else {
                        operator = "=";
                    }
                    String query = "SELECT FROM " + relTable + " WHERE "+col+" "+operator+" #"+id;
                    if (DEBUG) System.out.println("related query="+query);
                    QueryResult rel = con.query(query);
                    for (Document rd : rel.get()) {
                        String relId = rd.getIdentity().toString().substring(1);
                        String relName = Weblet.getDescriptionFromDocument(con, rd);
                        nodes.append(nodeComma+"{ \"id\":\"row."+relId+"\""
                            + ",\"name\":\""+relName+"\""
                            + ",\"description\":\"row "+relId+" "+relTable+" "+relName+"\""
                            + " }");
                        nodeComma = "\n,";
                        links.append(linkComma+"{ \"sourceId\":\"row."+relId+"\", \"targetId\":\"row."+id+"\" }");
                        linkComma = "\n,";
                    }
                }
            }
            
            return assembleResult(nodes,links);
        }
    }

    private byte[] getData(DatabaseConnection con, String id, String detail) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder links = new StringBuilder();
        String linkComma = "";
        String nodeComma = "";
        System.out.println("Not implemented: Build DATA node & links for "+id);

        return assembleResult(nodes,links);
    }

    private byte[] getSet(DatabaseConnection con, String id, String detail) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder links = new StringBuilder();
        String linkComma = "";
        String nodeComma = "";
        System.out.println("Not implemented: Build SET node & links for "+id);

        return assembleResult(nodes,links);		
    }

    public byte[] assembleResult(StringBuilder nodes, StringBuilder links) {
        return links.length() == 0 && nodes.length() == 0
            ? "{ \"nodes\": [ ],  \"links\": [ ] }".getBytes() 
            : ("{ " + "\n\"nodes\": [ "+nodes.toString()+" ]"
                    + "\n, \"links\": [ "+links.toString()+" ]"
            + " }").getBytes();
    }
	
}
