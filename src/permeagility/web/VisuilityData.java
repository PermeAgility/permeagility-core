package permeagility.web;

import java.util.Collection;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class VisuilityData extends Download {

    @Override
    public String getContentType() {
            return "application/json";
    }

    @Override
    public String getContentDisposition() {
            return "inline; filename=\"data.json\"";
    }

    @Override
    public byte[] getBytes(DatabaseConnection con, HashMap<String, String> parms) {

            String type = parms.get("TYPE");
            String id = parms.get("ID");
            String detail = parms.get("DETAIL");

            try {
                    if ("ROW".equals(type) && id != null && !id.equals("")) {
                            System.out.println("Build ROW view "+id);
                            return getRow(con,id,detail);
                    } else if ("TABLE".equals(type) && id != null && !id.equals("")) {
                            return getTable(con,id,detail);
                    } else if ("COLUMN".equals(type) && id != null && !id.equals("")) {
                            return getColumn(con,id,detail);
                    } else if ("DATA".equals(type) && id != null && !id.equals("")) {
                            return getData(con,id,detail);
                    } else if ("SET".equals(type) && id != null && !id.equals("")) {
                            return getSet(con,id,detail);
                    }	
            } catch(Exception e) {
                    e.printStackTrace();
                    return ("{ \"error\": \""+e.getMessage()+"\"}").getBytes();			
            }
            return ("{ \"error\": \"Unknown type: "+type+"\"}").getBytes();			
    }

    private byte[] getTable(DatabaseConnection con, String table, String detail) {
            StringBuilder nodes = new StringBuilder();
            StringBuilder links = new StringBuilder();
            String linkComma = "";
            String nodeComma = "";
            System.out.println("Build TABLE node & links for  "+table);

            nodes.append("{ \"id\": \"table."+table+"\", \"name\":\""+table+"\" }");
            nodeComma = "\n,";

            // Related tables
            int relCount = 0;
            for (OClass c : con.getSchema().getClasses()) {
                    for (OProperty p : c.properties()) {
                            if (p.getLinkedClass() != null && p.getLinkedClass().getName().equals(table)) {
                                    relCount++;
                                    nodes.append(nodeComma+"{ \"id\":\"table."+c.getName()+"\""
                                                    + ",\"name\":\""+c.getName()+"\""
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
                for (ODocument row : rows.get()) {
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
                        + ", \"count\":"+rowCount
                        +" }");
                    linkComma = "\n,";
                    rowTarget = "row."+row.getIdentity().toString().substring(1);
                }
            }

            // Columns - C
            Collection<OProperty> cols = con.getColumns(table);
            String columnTarget = "table."+table;
            int colCount = 0;
            relCount = 0;
            columnTarget = "table."+table+"";
            for (OProperty col : cols) {
                colCount++;
                if (detail != null && detail.equalsIgnoreCase("C") && !col.getName().startsWith("_")) {
                    nodes.append(nodeComma+"{ \"id\":\"column."+table+"."+col.getName()+"\""
                        + ",\"name\":\""+col.getName()+"\""
                        + ", \"count\":"+colCount
                        + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"targetId\": \""+columnTarget+"\", \"sourceId\":\"column."+table+"."+col.getName()+"\" }");
                    linkComma = "\n,";
                    columnTarget = "column."+table+"."+col.getName();
                }
                // Connect to a table if the property is a relationship
                String lc = col.getLinkedClass() != null ? col.getLinkedClass().getName() : null; 
                if (lc != null) {
                    relCount++;
                    nodes.append(nodeComma+"{ \"id\":\"table."+lc+"\""
                        + ",\"name\":\""+lc+"\""
                        + ", \"count\":"+relCount
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
        System.out.println("Build COLUMN node & links for "+table);

        nodes.append("{ \"id\": \"column."+table+"\", \"name\":\""+table+"\" }");
        nodeComma = "\n,";

        // Related tables
        int relCount = 0;
        for (OClass c : con.getSchema().getClasses()) {
            for (OProperty p : c.properties()) {
                if (p.getLinkedClass() != null && p.getLinkedClass().getName().equals(table)) {
                    relCount++;
                    nodes.append(nodeComma+"{ \"id\":\"table."+c.getName()+"\""
                        + ",\"name\":\""+c.getName()+"\""
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
            for (ODocument row : rows.get()) {
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
        Collection<OProperty> cols = con.getColumns(table);
        String columnTarget = "table."+table;
        int colCount = 0;
        relCount = 0;
        columnTarget = "table."+table+"";
        for (OProperty col : cols) {
            colCount++;
            if (detail != null && detail.equalsIgnoreCase("C")) {
                nodes.append(nodeComma+"{ \"id\":\"column."+table+"."+col.getName()+"\""
                    + ",\"name\":\""+col.getName()+"\""
                    + ", \"count\":"+colCount
                    + " }");
                nodeComma = "\n,";
                links.append(linkComma+"{ \"targetId\": \""+columnTarget+"\", \"sourceId\":\"column."+table+"."+col.getName()+"\" }");
                linkComma = "\n,";
                columnTarget = "column."+table+"."+col.getName();
            }
            // Connect to a table if the property is a relationship
            String lc = col.getLinkedClass() != null ? col.getLinkedClass().getName() : null; 
            if (lc != null) {
                relCount++;
                nodes.append(nodeComma+"{ \"id\":\"table."+lc+"\""
                    + ",\"name\":\""+lc+"\""
                    + ", \"count\":"+relCount
                    + " }");
                nodeComma = "\n,";
                links.append(linkComma+"{ \"sourceId\": \"table."+table+"\", \"targetId\":\"table."+lc+"\" }");
                linkComma = "\n,";
            }
        }
        return assembleResult(nodes,links);
    }

    private byte[] getRow(DatabaseConnection con, String id, String detail) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder links = new StringBuilder();
        String linkComma = "";
        String nodeComma = "";
        ODocument viewDoc = con.get("#"+id);
        if (viewDoc == null) {
            return ("Could not retrieve row using id:"+id).getBytes();
        } else {
            String classname = viewDoc.getClassName();
            Collection<OProperty> cols = con.getColumns(classname);
            nodes.append("{ \"id\": \"row."+id+"\", \"name\":\""+classname+id+"\" }");
            String columnTarget = "table."+classname;  // For stringing columns together in order
            String dataTarget = "row."+id;  // For stringing data columns together in order
            int colCount = 0;
            for (OProperty col : cols) {
                String colName = col.getName();
                Object colData = viewDoc.field(colName);
                if (colData instanceof ODocument) {
                    ODocument ld = (ODocument)colData;
                    colData = ld.getIdentity().toString().substring(1);
                    // Should be a link to the other row
                    nodes.append(nodeComma+"{ \"id\":\"row."+ld.getIdentity().toString().substring(1)+"\""
                            + ",\"name\":\""+ld.getIdentity().toString()+"\""
                            + ",\"description\":\""+Weblet.getDescriptionFromDocument(con, ld)+"\""
                    //	+ ", \"count\":"+rowCount
                            + " }");
                    nodeComma = "\n,";
                    links.append(linkComma+"{ \"targetId\":\"row."+ld.getIdentity().toString().substring(1)+"\""
                            +", \"sourceId\":\"row."+viewDoc.getIdentity().toString().substring(1)+"\""
            //		+ ", \"count\":"+rowCount
                            +" }");
                    linkComma = "\n,";
                } else {   // Data - D
                    if (detail != null && detail.equalsIgnoreCase("D")) {
                        if (colData != null) {
                            colData = colData.toString().replace("\r","").replace("\n","<BR>").replace("\\","\\\\").replace("'","\\u0027").replace("<","&lt;").replace(">","&gt;");
                        }
                        colCount++;
                        nodes.append(nodeComma+"{ \"id\":\"data."+id+"."+colName+"\""
                            + ",\"name\":\""+colData+"\""
                            + ", \"count\":"+colCount
                            + " }");
                        nodeComma = "\n,";
                        links.append(linkComma+"{ \"sourceId\":\"data."+id+"."+colName+"\", \"targetId\":\""+dataTarget+"\", \"count\":"+colCount+"}");
                        linkComma = "\n, ";
                        columnTarget = "column."+classname+"."+colName;
                        dataTarget = "data."+id+"."+colName;
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
        System.out.println("Build DATA node & links for "+id);

        return assembleResult(nodes,links);
    }

    private byte[] getSet(DatabaseConnection con, String id, String detail) {
        StringBuilder nodes = new StringBuilder();
        StringBuilder links = new StringBuilder();
        String linkComma = "";
        String nodeComma = "";
        System.out.println("Build SET node & links for "+id);

        return assembleResult(nodes,links);		
    }

    public byte[] assembleResult(StringBuilder nodes, StringBuilder links) {
        return links.length() == 0 && nodes.length() == 0
            ? "{ \"nodes\": [ ],  \"links\": [ ] }".getBytes() 
            : ("{ "
                    + "\n\"nodes\": [ "+nodes.toString()+" ]"
                    + "\n, \"links\": [ "+links.toString()+" ]"
            + " }").getBytes();
    }
	
}
