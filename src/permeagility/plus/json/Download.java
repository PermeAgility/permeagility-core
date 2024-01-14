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
package permeagility.plus.json;

import com.arcadedb.database.Document;
import com.arcadedb.database.RID;
import com.arcadedb.schema.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.web.Weblet;

public class Download extends permeagility.web.Download {

    public static boolean NO_ALLOWS = true;
    public String filename = "data.json";
    @Override public String getContentType() { return "application/json"; }
    @Override public String getContentDisposition() { return "inline; filename=\""+filename+"\""; }

    @Override
    public byte[] getBytes(DatabaseConnection con, HashMap<String, String> parms) {
        StringBuilder sb = new StringBuilder();
        String fromTable = parms.get("FROMTABLE");
        String fromSQL = parms.get("SQL");
        String callback = parms.get("CALLBACK");
        if (callback != null && callback.isEmpty()) { callback = null; }
        
        int depth = -1;  // unlimited is the default
        try { depth = Integer.parseInt(parms.get("DEPTH")); } catch (Exception e) {}  // It will either parse or not

        if ((fromTable == null || fromTable.isEmpty()) && (fromSQL == null || fromSQL.isEmpty())) {
            return null;
        } else {
            try {
                if (fromSQL == null || fromSQL.isEmpty()) {
                    filename = fromTable+".json";
                    fromSQL = "SELECT FROM "+fromTable;
                }
                QueryResult qr = con.query(fromSQL);
                if (callback != null) { sb.append(callback+"("); }
                sb.append("{ \""+(fromTable==null ? "data" : fromTable)+"\":[ ");
                String comma = "";
                for (Document d : qr.get()) {
                    if (d != null) {
                        sb.append(comma+exportDocument(con, d, depth, 0));
                        comma = ", \n";
                    }
                }
                sb.append(" ] }\n");
                if (callback != null) { sb.append(")"); }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sb.toString().getBytes(Weblet.charset);
    }
    
    public static String exportDocument(DatabaseConnection con, Document d, int maxLevel, int level) {
        StringBuilder sb = new StringBuilder();
        String comma = "";
        sb.append("{");
        String className = d.getTypeName();
        Set<String> columns = d.getPropertyNames();
        if (className != null) {
            sb.append("\"@rid\":\""+d.getIdentity().toString().substring(1)+"\", \"@type\":\""+className+"\"");
            comma = "\n, ";
        }
        for (String p : columns) {
            if (className != null && (d.getTypeName().equals("role") || d.getTypeName().equals("user")) && !p.equals("name")) {
                continue;  // Only the name is shown for an role or an user
            }
            if (NO_ALLOWS && p.startsWith("_allow")) {
                continue;  // No allow columns, rename them with AS to retrieve them
            }
            Type t = d.getType().getProperty(p).getType();
            if (t == null) {  // Generated fields from SQL do not give a type must deduce it from the object
                Object o = d.get(p);
                if (o != null) {
                    if (o instanceof String) {
                        t = Type.STRING;
                    } else if (o instanceof Boolean) {
                        t = Type.BOOLEAN;
                    } else if (o instanceof Date) {
                        t = Type.DATETIME;
                    } else if (o instanceof Integer) {
                        t = Type.INTEGER;
                    } else if (o instanceof Long) {
                        t = Type.LONG;
                    } else if (o instanceof Float) {
                        t = Type.FLOAT;
                    } else if (o instanceof Double) {
                        t = Type.DOUBLE;
                    } else if (o instanceof Document) {
                        t = Type.LINK;
                    } else if (o instanceof List) {
                        t = Type.LIST;
                    } else if (o instanceof Map) {
                        t = Type.MAP;
           //         } else if (o instanceof ORecordBytes) {
           //             t = OType.CUSTOM;
                    } else {
                        System.out.println("Encountered a null type returning a "+o.getClass().getName());
                        t = Type.STRING;
                    }
                }
            }
            if (t != null ) {
                sb.append(comma);
                if (t == Type.LINK) {
                    RID ldr = (RID)d.get(p);
                    Document ld = con.get(ldr);
                    if (ld == null) {
                        sb.append("\""+p+"\":null");                    
                    } else if (level < maxLevel) {
                        sb.append("\""+p+"\":"+exportDocument(con, ld , maxLevel, level+1));
                    } else {
                        sb.append("\""+p+"\":\""+ld.getIdentity().toString().substring(1)+"\"");                    
                    }
                } else if (t == Type.LIST) {
                    List<RID> set = d.getList(p);
                    String lcomma = "";
                    sb.append("\""+p+"\": [");
                    if (set != null) {
                        for (RID sdr : set) {
                            Document sd = con.get(sdr);
                            if (sd != null) {
                                if (level < maxLevel) {
                                    sb.append(lcomma+exportDocument(con, sd, maxLevel, level+1));
                                } else {
                                    sb.append(lcomma+"\""+sd.getIdentity().toString().substring(1)+"\"");                    
                                }
                                lcomma = "\n ,";
                            }
                        }
                    }
                    sb.append("] ");
                } else if (t == Type.MAP) {
                    Map<String,Object> map = d.getMap(p);
                    String lcomma = "";
                    sb.append("\""+p+"\": {");
                    if (map != null) {
                        for (String key : map.keySet()) {
                            RID keyDocrid = (RID)map.get(key);
                            if (key != null && keyDocrid != null ) {
                                Document keyDoc = con.get(keyDocrid);
                                if (key.startsWith("'") && key.endsWith("'")) key = key.substring(1, key.length() - 1);
                                sb.append(lcomma+"\""+key.trim()+"\": ");
                                if (level < maxLevel) {
                                    sb.append(exportDocument(con, keyDoc, maxLevel, level+1));
                                } else {
                                    sb.append("\""+keyDoc.getIdentity().toString().substring(1)+"\"");                    
                                }
                                lcomma = "\n ,";
                            }
                        }
                    }
                    sb.append("} ");
                } else if (t == Type.BOOLEAN) {
                    sb.append("\""+p+"\":"+d.getBoolean(p));
                } else if (t == Type.INTEGER || t == Type.LONG || t == Type.BYTE) {
                    sb.append("\""+p+"\":"+d.getLong(p));
                } else if (t == Type.DOUBLE || t == Type.FLOAT || t == Type.DECIMAL) {
                    sb.append("\""+p+"\":"+d.getDouble(p));
                } else if (t == Type.DATE || t == Type.DATETIME) {
                    sb.append("\""+p+"\":\""+d.getString(p)+"\"");
                 } else if (t == Type.STRING) {
                    String content = d.getString(p);
                    if (content != null && !content.isEmpty()) {
                        content = content
                                .replace("\\","\\u005c")
                                .replace("\"","\\\"")
                                .replace("\t","\\t")
                                .replace("\r","\\r")
                                .replace("\n","\\n");
                    }
                    sb.append("\""+p+"\":\""+(content == null ? "" : content)+"\"");                
                } else {
                    System.out.println("json.Download: unrecognized column type: "+t);
                    sb.append("\""+p+"\":\""+d.getString(p)+"\"");
                }
                comma = "\n ,";
            }
        }
        sb.append("}");
        return sb.toString();
    }

}
