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

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import permeagility.util.QueryResult;

public class ImportJSON extends Weblet {
    
    public static boolean DEBUG = false;

    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        String submit = parms.get("SUBMIT");
        boolean run = (submit != null && submit.equals("GO"));
        String fromURL = parms.get("FROM_URL");
        String fromText = parms.get("FROM_TEXT");
        String toTable = parms.get("TO_TABLE");

        if (submit != null) {
            JSONObject jo = null;
            if (fromURL != null && !fromURL.isEmpty()) {
                errors.append(paragraph("Using URL: "+fromURL));
                try {            
                    URL tURL = new URL(fromURL);
                    Object o = tURL.getContent();
                    if (o != null) {
                        fromText = o.toString();
                    }
                } catch (Exception e) {
                    errors.append(paragraph("error","Nothing to parse. error="+e.getMessage()));
                    e.printStackTrace();
                }
            }
            boolean parseSuccess = false;
            HashMap<String, HashMap<String,String>> classes = new HashMap<>();
            if (fromText == null || fromText.isEmpty()) {
                errors.append(paragraph("error","Nothing to parse"));
            } else {
                try {
                    jo = new JSONObject(fromText.replace("\\u0022", "\""));
                    ODocument doc;
                    doc = importObject(parms, run, con, toTable, jo, errors, classes);
                    parseSuccess = true;
                    if (run) errors.append(paragraph("success","Successfully parsed and imported "+toTable));                    
                } catch (Exception e) {
                    errors.append(paragraph("error","Error parsing JSON:"+e.getMessage()));
                    e.printStackTrace();
                }
            }
            if (!run && parseSuccess) {
                // Dump the class information and import options
                for (String cname : classes.keySet()) {
                    HashMap<String,String> map = classes.get(cname);
                    // Do the null "" column first as this is about the table
                    for (String column : map.keySet()) {
                        if (column.equals("")) sb.append(map.get(column));
                    }
                    ArrayList fields = new ArrayList(map.keySet());
                    sb.append("Use this field as a primary key "+createList(con.getLocale(), "KEY_FOR_"+cname, null, fields, null, true, null, true));
                    
                    // then do the columns in the table
                    for (String column : map.keySet()) {
                        if (!column.equals("")) sb.append(map.get(column));
                    }
                }
                sb.append(hidden("FROM_TEXT",fromText.replace("\"","\\u0022")));
                sb.append(hidden("TO_TABLE",toTable));
                sb.append(submitButton(con.getLocale(), "GO"));
            }
        } else {
            sb.append(table("layout", 
                     row(column("label", "From URL") + column(input("FROM_URL", parms.get("FROM_URL"))))
                    + row(column("label", "or paste here:") + column(textArea("FROM_TEXT", parms.get("FROM_TEXT"), 30, 100)))
                    + row(column("label", "Table to create") + column(input("TO_TABLE", toTable) + " will be turned to camelCase"))
                    //	+row(column("label","Replace if exists")+column(checkbox("REPLACE",false)+" will fail if table exists unless replace is checked"))
                    + row(column("") + column(submitButton(con.getLocale(), "PREVIEW")))
            ));
        }
        return head("Import JSON") + body(standardLayout(con, parms, 
                paragraph("banner", "Import JSON to a table")
                +form(sb.toString() + errors.toString())
            ));
    }

    /* Import a JSON Object as a Document */
    public ODocument importObject(HashMap<String,String> parms, boolean run, DatabaseConnection con, String classname, JSONObject acjo, StringBuilder errors, HashMap<String,HashMap<String,String>> classes) {
        OClass oclass = null;
        ODocument doc = null;
        String keycol = parms.get("KEY_FOR_"+classname);
        if (keycol != null && (keycol.isEmpty() || keycol.equals("null"))) keycol = null;
        String keyval = null;  // Hold a key value for resolution against a primary key
        
        if (classname != null && !classname.isEmpty()) {
            if (run) {
                String ccTable = makePrettyCamelCase(classname);
                oclass = Setup.checkCreateTable(con.getSchema(), classname, errors);
            } else {
                if (!classes.containsKey(classname)) {
                 classes.put(classname, new HashMap<>());                    
                 classes.get(classname).put("",paragraph("Table will be created called "+input("TABLE_FOR_"+classname,classname)));
                }
            }
        }
        if (oclass != null && doc == null) {
           doc = con.create(oclass.getName());
        }
        JSONArray fields = acjo.names();
        for (int f = 0; fields != null && f < fields.length(); f++) {
            String colName = fields.getString(f);
            Object val = acjo.get(colName);  // get the value before changing the name to an identifier
            if (colName.startsWith("@")) colName = colName.substring(1);
            colName = makePrettyCamelCase(colName);
            if (keycol != null && colName.equals(keycol)) {  // capture the key value
                keyval = val.toString();
            }
            if (val instanceof JSONArray) {                
                JSONArray array = (JSONArray)val;
                List<ODocument> docList = new ArrayList<>();
                for (int j = 0; j < array.length(); j++) {
                    Object ac = array.get(j);
                    if (DEBUG) System.out.println("Array[" + j + "]=" + ac.getClass().getName() + ":" + ac.toString());
                    if (ac instanceof JSONObject) {
                        if (run) {
                            ODocument subdoc;
                            subdoc = importObject(parms, run, con, colName, (JSONObject)ac, errors, classes);
                            OProperty oproperty = null;
                            if (oclass != null && subdoc != null) {
                                oproperty = Setup.checkCreateColumn(con, oclass, colName, OType.LINKLIST, subdoc.getSchemaClass(), errors);
                            }
                            if (oproperty != null && subdoc != null) {
                                docList.add(subdoc);
                            }
                        } else {
                            classes.get(classname).put(colName,paragraph("Column " + input("COLUMN_"+classname+"_"+colName,colName) + " is an array and it will be a LINKLIST"));
                            importObject(parms, run, con, colName, (JSONObject)ac, errors, classes);                            
                        }
                    }
                }
                if (run && doc != null && docList.size() > 0) {
                    doc.field(colName, docList);
                }

            } else if (val instanceof JSONObject) {
                if (run) {
                    ODocument subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                    if (subdoc != null && doc != null) {
                        OProperty oproperty = null;
                        if (oclass != null) {
                            oproperty = Setup.checkCreateColumn(con, oclass, colName, OType.LINK, subdoc.getSchemaClass(), errors);
                        }
                        if (oproperty != null) {
                            doc.field(colName, subdoc);
                        }
                    }
                } else {
                    classes.get(classname).put(colName,paragraph("Column " + input("COLUMN_"+classname+"_"+colName,colName) + " is an object and it will be a LINK"));
                    ODocument subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                }
            } else {
                if (run) {
                    OProperty oproperty = null;
                    if (oclass != null) {
                        oproperty = Setup.checkCreateColumn(con, oclass, colName, determineOTypeFromClassName(val.getClass().getName()), errors);
                    }
                    if (doc != null && oproperty != null) {
                        doc.field(colName, val);
                    }
                } else {
                    classes.get(classname).put(colName,paragraph("Column "+input("COLUMN_"+classname+"_"+colName,colName)
                            +" of type " + val.getClass().getName() 
                            + " will be a "+determineOTypeFromClassName(val.getClass().getName())
                    ));                    
                }
            }
        }
        if (doc != null) {
            if (keycol != null) {
                if (keyval != null) {
                    QueryResult qr = con.query("SELECT FROM "+doc.getClassName()+" WHERE "+keycol+" = "+wrapWithQuotes(keyval));
                    if (qr.size() == 0) {
                        errors.append(paragraph("Did not resolve reference SELECT FROM "+doc.getClassName()+" WHERE "+keycol+"="+wrapWithQuotes(keyval)));
                        doc.save();
                    } else {
                        errors.append(paragraph("Resolved reference to "+doc.getClassName()+" using "+keycol+"="+keyval));
                        doc.delete();
                        return qr.get(0);
                    }        
                } else {
                    errors.append(paragraph("error", "Could not resolve key value from "+keycol+" in "+classname));                    
                }                
            } else {
                doc.save();
            }
        }
        return doc;
    }
    /**
     * Determine the best lossless OrientDB representation of the given java classname (fully qualified) of an object given in a result set (not necessarily the most efficient storage or what you expect) Note: This is a good candidate to be a general utility function but this is the only place it is used right now
     */
    public OType determineOTypeFromClassName(String className) {
        OType otype = OType.STRING;  // Default
        if (className.endsWith("JSONObject$Null")) {
            otype = OType.STRING;
        } else if (className.endsWith("JSONObject")) {
            otype = OType.LINK;
        } else if (className.equals("java.math.BigDecimal")) {
            otype = OType.DECIMAL;
        } else if (className.equals("java.util.Date")) {
            otype = OType.DATETIME;
        } else if (className.equals("java.lang.Double")) {
            otype = OType.DOUBLE;
        } else if (className.equals("java.lang.Integer")) {
            otype = OType.INTEGER;
        }
        if (DEBUG) System.out.println(className+" becomes "+otype);
        return otype;
    }

}
