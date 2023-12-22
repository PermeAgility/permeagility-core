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
package permeagility.plus.csv;

import java.util.HashMap;
import java.util.Set;

import com.arcadedb.database.Document;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.web.Weblet;

/**
 *
 * @author glenn
 */
public class Download extends permeagility.web.Download {

    @Override
    public String getContentType() { return "text/csv"; }

    @Override
    public String getContentDisposition() { return "inline; filename=\"data.csv\""; }

    @Override
    public byte[] getBytes(DatabaseConnection con, HashMap<String, String> parms) {
        StringBuilder sb = new StringBuilder();
        String fromTable = parms.get("FROMTABLE");
        String fromSQL = parms.get("SQL");

        if ((fromTable == null || fromTable.isEmpty()) && (fromSQL == null || fromSQL.isEmpty())) {
            return null;
        } else {
            try {
                if (fromSQL == null || fromSQL.isEmpty()) {
                    fromSQL = "SELECT FROM "+fromTable;
                }
                QueryResult qr = con.query(fromSQL);
                Set<String> columns = null;
                // Get first document for columns
                if (qr.size() > 0) {
                    String comma = "";
                    Document firstDoc = qr.get(0);
                    columns = firstDoc.getPropertyNames();               
                    for (String p : columns) {
                        sb.append(comma);
                        sb.append(p);
                        comma = ",";
                    }
                    sb.append("\n");
                }
                for (Document d : qr.get()) {
                    sb.append(exportDocument(con, d, columns)+"\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sb.toString().getBytes(Weblet.charset);
    }
    
    String exportDocument(DatabaseConnection con, Document d, Set<String> columns) {
        StringBuilder sb = new StringBuilder();
 //       String comma = "";
 //       for (String p : columns) {
 //           Type t = d.getType(p);
 //           sb.append(comma);
 //           if (t == OType.LINK) {
 //               ODocument ld = (ODocument)d.field(p);
 //               if (ld == null) {
 //               } else {
 //                   sb.append(ld.getIdentity().toString().substring(1));                    
 //               }
 //           } else if (t == OType.LINKSET) {
 //               Set<ODocument> set = d.field(p);
 //               String lcomma = "";
 //               if (set != null) {
 //                   for (ODocument sd : set) {
 //                       if (sd != null) {
 //                           sb.append(lcomma+sd.getIdentity().toString().substring(1));                    
 //                           lcomma = "/";
 //                       }
 //                   }
 //               }
 //           } else if (t == OType.LINKLIST) {
 //               List<ODocument> set = d.field(p);
 //               String lcomma = "";
 //               if (set != null) {
 //                   for (ODocument sd : set) {
 //                       if (sd != null) {
 //                           sb.append(lcomma+sd.getIdentity().toString().substring(1));                    
 //                       }
 //                       lcomma = "/";
 //                   }
 //               }
 //           } else if (t == OType.DATE || t == OType.DATETIME) {
 //               sb.append(""+d.field(p));
 //           } else if (t == OType.EMBEDDED || t == OType.EMBEDDEDLIST || t == OType.EMBEDDEDMAP || t == OType.EMBEDDEDSET) {
 //               sb.append("\""+d.field(p)+"\"");                
 //           } else if (t == OType.STRING) {
 //               String content = d.field(p);
 //               if (content != null && !content.isEmpty()) {
 //                   content = content.replace("\"","\\\"").replace("\\s","\\u005cs").replace("\t","\\t").replace("\r","\\r").replace("\n","\\n");
 //               }
 //               sb.append("\""+(content == null ? "" : content)+"\"");                
 //           } else {
 //               sb.append(""+d.field(p));
 //           }
 //           comma = ",";
 //       }
        return sb.toString();
    }

}
