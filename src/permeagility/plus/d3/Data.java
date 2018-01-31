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

		// For view builder sample views
		String view = parms.get("VIEW");
                String fromTable = parms.get("FROMTABLE");
                String fromSQL = parms.get("SQL");
                String callback = parms.get("CALLBACK");

                if (view != null && !view.equals("")) {
			//System.out.println("Return sample dataScript "+view);
			ODocument viewDoc = con.get("#"+view);
			if (viewDoc == null) {
				return ("Could not retrieve data using "+parms.toString()).getBytes();
			} else {
				String sampleData = viewDoc.field("dataScript");
				return sampleData == null ? "".getBytes() : sampleData.getBytes();
			}

		} else if ((fromTable != null && !fromTable.isEmpty()) || (fromSQL != null && !fromSQL.isEmpty())) {
                    if (callback != null && callback.isEmpty()) { callback = null; }
                    StringBuilder sb = new StringBuilder();

                    int depth = -1;  // unlimited is the default
                    try { depth = Integer.parseInt(parms.get("DEPTH")); } catch (Exception e) {}  // It will either parse or not

                    if ((fromTable == null || fromTable.isEmpty()) && (fromSQL == null || fromSQL.isEmpty())) {
                        return null;
                    } else {
                        try {
                            if (fromSQL == null || fromSQL.isEmpty()) {
                                fromSQL = "SELECT FROM "+fromTable;
                            }
                            QueryResult qr = con.query(fromSQL);
                            if (callback != null) { sb.append(callback+"("); }
                            sb.append("{ \""+(fromTable==null ? "data" : fromTable)+"\":[ ");
                            String comma = "";
                            for (ODocument d : qr.get()) {
                                sb.append(comma+permeagility.plus.json.Download.exportDocument(con, d, depth, 0));
                                comma = ", \n";
                            }
                            sb.append(" ] }");
                            if (callback != null) { sb.append(")"); }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return sb.toString().getBytes(Weblet.charset);
                } else {
                    return "Bad parameters (must specify one of VIEW, FROMTABLE, or SQL), So sorry, no results".getBytes(Weblet.charset);
                }
        }

}
