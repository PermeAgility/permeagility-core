package permeagility.web;

import java.util.Collection;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.web.Download;
import permeagility.web.Server;
import permeagility.web.Weblet;

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
	public byte[] getFile(DatabaseConnection con, HashMap<String, String> parms) {

		String view = parms.get("VIEW");
		if (view != null && !view.equals("")) {
			System.out.println("Build view "+view);
			ODocument viewDoc = con.get("#"+view);
			if (viewDoc == null) {
				return ("Could not retrieve data using "+parms.toString()).getBytes();
			} else {
				String sampleData = viewDoc.field("dataScript");
				return sampleData == null ? "".getBytes() : sampleData.replace("'","\"").getBytes();
			}
		}
		
		String id = parms.get("ROW");
		if (id != null && !id.equals("")) {
			System.out.println("Build ROW view "+id);
			ODocument viewDoc = con.get("#"+id);
			if (viewDoc == null) {
				return ("Could not retrieve data using "+parms.toString()).getBytes();
			} else {
				String detail = parms.get("DETAIL");
				String classname = viewDoc.getClassName();
				Collection<OProperty> cols = Server.getColumns(classname);
				StringBuilder nodes = new StringBuilder();
				StringBuilder links = new StringBuilder();
				nodes.append("{ \"id\": \"row."+id+"\", \"name\":\""+classname+id+"\" }");
			//	links.append("{ \"sourceId\":\"row."+id+"\", \"targetId\":\"table."+classname+"\" }");
				String linkComma = "";
				String nodeComma = "\n, ";
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
								+ ",\"name\":\""+Weblet.getDescriptionFromDocument(con, ld)+"\""
							//	+ ", \"count\":"+rowCount
								+ " }");
						nodeComma = "\n,";
						links.append(linkComma+"{ \"targetId\":\"row."+ld.getIdentity().toString().substring(1)+"\""
								+", \"sourceId\":\"row."+viewDoc.getIdentity().toString().substring(1)+"\""
						//		+ ", \"count\":"+rowCount
								+" }");
						linkComma = "\n,";
					} else {
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

		String table = parms.get("TABLE");
		if (table != null && !table.equals("")) {
			String detail = parms.get("DETAIL");
			System.out.println("Build TABLE view "+table);
			StringBuilder nodes = new StringBuilder();
			StringBuilder links = new StringBuilder();
			String linkComma = "";
			String nodeComma = "";

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

			// Rows
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
			
			// Columns
			Collection<OProperty> cols = Server.getColumns(table);
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
		
		return "{}".getBytes();
	}

	public byte[] assembleResult(StringBuilder nodes, StringBuilder links) {
		return links.length() == 0 && nodes.length() == 0
				? "{}".getBytes() 
				: ("{ "
					+ "\n\"nodes\": [ "+nodes.toString()+" ]"
					+ "\n, \"links\": [ "+links.toString()+" ]"
				+ " }").getBytes();
	}
	
}
