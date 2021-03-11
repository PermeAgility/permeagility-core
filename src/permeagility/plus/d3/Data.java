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
package permeagility.plus.d3;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.web.Download;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.record.impl.ODocument;
import permeagility.util.QueryResult;

public class Data extends Download {

	@Override
	public String getContentType() { return "application/json"; }

	@Override
	public String getContentDisposition() {	return "inline; filename=\"data.json\""; }

	@Override
	public byte[] getBytes(DatabaseConnection con, HashMap<String, String> parms) {
	
		// For view builder sample views.  The other stuff has been implemented elsewhere
		String view = parms.get("VIEW");
        String fromTable = parms.get("FROMTABLE");
        String fromQueries = parms.get("QUERIES");  // List of queries (will use as JSON attribute names)
        String fromSQL = parms.get("SQL");
        String callback = parms.get("CALLBACK");

            if (view != null && !view.equals("")) {
			System.out.println("Build view "+view);
			ODocument viewDoc = con.get("#"+view);
			if (viewDoc == null) {
				return ("Could not retrieve data using "+parms.toString()).getBytes();
			} else {
				String sampleData = viewDoc.field("dataScript");
				return sampleData == null ? "".getBytes() : sampleData.replace("'","\"").getBytes();
			}
                         
		} else if ((fromTable != null && !fromTable.isEmpty()) 
            || (fromQueries != null && !fromQueries.isEmpty())
            || (fromSQL != null && !fromSQL.isEmpty())) {
                if (callback != null && callback.isEmpty()) { callback = null; }
                StringBuilder sb = new StringBuilder();
                int depth = -1;  // unlimited is the default
                try { depth = Integer.parseInt(parms.get("DEPTH")); } catch (Exception e) {}  // It will either parse or not

                if (callback != null) { sb.append(callback+"("); }
                if (fromQueries != null && !fromQueries.isEmpty()) {
                    sb.append("{ ");
                    String[] queries = fromQueries.split(",",0);
                    String comma = "";
                    for (String q : queries) {
                        String qx = parms.get(q);
                        System.out.println("query "+q+" = "+qx);
                        if (qx != null && !qx.isEmpty()) {
                            sb.append(comma);
                            appendSQL(con, sb, qx, q, depth);
                            comma = ", \n";
                        }
                    }
                    sb.append("}");                        
                } else {
                    sb.append("{ ");
                    appendSQL(con, sb, fromSQL, fromTable, depth);
                    sb.append("}");                        
                }                        
                if (callback != null) { sb.append(")"); }
                return sb.toString().getBytes(Weblet.charset);
            } else {
                return "Bad parameters (must specify one of VIEW, FROMTABLE, SQL, or QUERIES), So sorry, no results".getBytes(Weblet.charset);
            }
        }	

        void appendSQL(DatabaseConnection con, StringBuilder sb, String sql, String fromTable, int depth) {
            sb.append("\""+(fromTable==null ? "data" : fromTable)+"\": [ ");
            try {
                if (sql == null || sql.isEmpty()) {
                    sql = "SELECT FROM "+fromTable;
                }
                QueryResult qr = con.query(sql);
                String comma = "";
                for (ODocument d : qr.get()) {
                    sb.append(comma+permeagility.plus.json.Download.exportDocument(con, d, depth, 0));
                    comma = ", \n";
                }
            } catch (Exception e) {
                e.printStackTrace();  // Query errors show up here
            }
            sb.append(" ] "); 
        }
}
