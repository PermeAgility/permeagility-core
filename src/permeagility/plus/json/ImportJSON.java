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
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import permeagility.util.QueryResult;

public class ImportJSON extends Weblet {
    
    public static boolean DEBUG = false;

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        String submit = parms.get("SUBMIT");
        boolean run = (submit != null && submit.equals("GO"));
        String fromURL = parms.get("FROM_URL");
        String fromFile = parms.get("FROM_FILE");
        String fromText = parms.get("FROM_TEXT");

        if (submit != null) {
            JSONObject jo = null;
            if (fromURL != null && !fromURL.isEmpty()) {
                errors.append(paragraph("Using URL: "+fromURL));
                try {            
                    URL tURL = new URL(fromURL);
                    Object o = tURL.getContent();
                    if (DEBUG) System.out.println("Received from: "+tURL.getQuery()+" content="+o);
                    if (o != null && o instanceof InputStream) {
                        fromText = new Scanner((InputStream)o,"UTF-8").useDelimiter("\\A").next();
                    }
                } catch (Exception e) {
                    errors.append(paragraph("error","Nothing to parse. error="+e.getMessage()));
                    e.printStackTrace();
                }
            } else if (fromFile != null && !fromFile.isEmpty()) {
                if (DEBUG) System.out.println("Using File: "+fromFile);
                errors.append(paragraph("Using File: "+fromFile));
                try { 
                    FileInputStream fis = new FileInputStream(fromFile);
      //              ByteArrayInputStream bis = new ByteArrayInputStream(bytes.toStream());
                    StringBuilder content_type = new StringBuilder();
                    if (fis.available() > 0) {
                            int binc = fis.read();
                            do {
                                    content_type.append((char)binc);
                                    binc = fis.read();
                            } while (binc != 0x00 && fis.available() > 0);
                    }
                    errors.append(paragraph("ContentType: "+content_type));
                    StringBuilder content_filename = new StringBuilder();
                    if (fis.available() > 0) {
                            int binc = fis.read();
                            do {
                                    content_filename.append((char)binc);
                                    binc = fis.read();
                            } while (binc != 0x00 && fis.available() > 0);
                    }
                    errors.append(paragraph("ContentFilename: "+content_filename));
                   // type.append(content_type);
                   // file.append(content_filename);
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    if (DEBUG) System.out.println("Reading content: available="+fis.available());
                    int avail;
                    int binc;
                    while ((binc = fis.read()) != -1 && (avail = fis.available()) > 0) {
                            content.write(binc);
                            byte[] buf = new byte[avail];
                            int br = fis.read(buf);
                            if (br > 0) {
                                    content.write(buf,0,br);
                            }
                    }
                    if (content.size() > 0) {
                        fromText = content.toString();
                        if (DEBUG) System.out.println("content="+fromText);
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
                    doc = importObject(parms, run, con, parms.get("TABLE_FOR_"), jo, errors, classes);
                    parseSuccess = true;
                    if (run) errors.append(paragraph("success","Successfully parsed and imported "+parms.get("TABLE_FOR_")));                    
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
                sb.append(submitButton(con.getLocale(), "GO"));
            }
        } else {
            sb.append(table("layout", 
                     row(column("label", "From URL") + column(input("FROM_URL", parms.get("FROM_URL"))))
                    + row(column("label", "From File") + column(fileInput("FROM_FILE")))
                    + row(column("label", "or paste here:") + column(textArea("FROM_TEXT", parms.get("FROM_TEXT"), 30, 100)))
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
        if (DEBUG) System.out.println("importObject.JSONObject="+acjo.getClass().getName());
        String originalClassName = classname;
        if (acjo.has("@class")) {
            classname = acjo.getString("@class");
        }
        String newClassName = parms.get("TABLE_FOR_"+classname);
        if (newClassName != null && !newClassName.isEmpty()) {
            classname = newClassName;
        }
        OClass oclass = con.getSchema().getClass(classname);
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
                if (oclass != null) {
                    classes.get(classname).put("",paragraph("Existing table will be loaded called "+input("TABLE_FOR_"+classname,classname)));                    
                } else {
                    classes.get(classname).put("",paragraph("Table will be created called "+input("TABLE_FOR_"+classname,classname)));
                }
                }
            }
        } else {
            classname = originalClassName;
        }
        if (run && oclass != null && doc == null) {
           doc = con.create(oclass.getName());
        }
        JSONArray fields = acjo.names();
        for (int f = 0; fields != null && f < fields.length(); f++) {
            String colName = fields.getString(f);
            Object val = acjo.get(colName);  // get the value before changing the name to an identifier
//            if (colName.startsWith("@")) colName = colName.substring(1);
            if (colName.startsWith("@")) continue;  // do not import these
            colName = makePrettyCamelCase(colName);
            String originalColName = colName;
            String newColName = parms.get("COLUMN_"+classname+"_"+colName);
            if (newColName != null && !newColName.isEmpty()) {
                if (keycol != null && keycol.equals(colName)) keycol = newColName;
                colName = newColName;
            }
            if (keycol != null && colName.equals(keycol)) {  // capture the key value
                keyval = val.toString();
                if (DEBUG) System.out.println("keyval="+keyval);
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
                            if (!colName.equals(classname) && oclass != null && subdoc != null) {
                                oproperty = Setup.checkCreateColumn(con, oclass, colName, OType.LINKLIST, subdoc.getSchemaClass(), errors);
                            }
                            if (oproperty != null && subdoc != null) {
                                docList.add(subdoc);
                            }
                        } else {
                            if (DEBUG) System.out.println("className="+classname+" colName="+colName);
                            if (classes.containsKey(classname)) {  // must be at the top level
                                classes.get(classname).put(colName,paragraph("Column " + input("COLUMN_"+classname+"_"+colName,colName) + " is an array and it will be a LINKLIST"));
                            }
                            importObject(parms, run, con, colName, (JSONObject)ac, errors, classes);                            
                        }
                    }
                }
                if (run && doc != null && docList.size() > 0) {
                    doc.field(colName, docList);
                }

            } else if (val instanceof JSONObject) {
                OProperty oproperty = oclass != null ? oclass.getProperty(colName) : null;  // See if property already exists
                if (run) {
                    if (oproperty == null) {
                        ODocument subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                        if (DEBUG) System.out.println("importObject.subdoc="+subdoc);
                        if (subdoc != null && doc != null) {
                            oproperty = Setup.checkCreateColumn(con, oclass, colName, OType.LINK, subdoc.getSchemaClass(), errors);
                        }
                        if (doc != null && oproperty != null && !subdoc.isEmpty()) {
                            doc.field(colName, subdoc);
                        }
                    } else if (oproperty.getType() == OType.LINKMAP) {
 //                       if (DEBUG) 
                            System.out.println("importObject(into map of class "+oproperty.getLinkedClass().getName()+")");
                        JSONObject j = (JSONObject)val;
                        HashMap<String,ODocument> newMap = new HashMap<>();
                        for (String n : j.keySet()) {
                            System.out.println("key="+n);
                            Object subval = j.get(n);
                            if (subval instanceof JSONObject) {
                                ODocument subdoc = importObject(parms, run, con, oproperty.getLinkedClass().getName(), (JSONObject)subval, errors, classes);
                                if (subdoc != null && !subdoc.isEmpty()) {
 //                               if (DEBUG) 
                                    System.out.println("adding (into map) "+n+": "+subdoc);
                                    newMap.put("'"+n+"'",subdoc);
                                }
                            }
                        }
                        if (doc != null && newMap.size() > 0) {
                            doc.field(colName, newMap);
                        }
                    } else if (oproperty.getType() == OType.STRING) {
                        System.out.println("Importing object into STRING");
                        JSONObject j = (JSONObject)val;
                        doc.field(colName,j.toString());
                    } else {
                        errors.append(paragraph("error", "I don't know how to import an object into the "+colName+" column of type "+oproperty.getType().name()));
                    }
                } else {
                    if (oproperty != null) {
                        if (oproperty.getType() == OType.LINKMAP) {
                            classes.get(classname).put(colName,paragraph("Column " + input("COLUMN_"+classname+"_"+colName,colName) + " is an object and it will use existing LINKMAP"));
//                            ODocument subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                            
                        }
                    } else {
                        classes.get(classname).put(colName,paragraph("Column " + input("COLUMN_"+classname+"_"+colName,colName) + " is an object and it will be a LINK"));
                        ODocument subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                    }
                }
            } else {
                if (run) {
                    OProperty oproperty = null;
                    if (oclass != null) {
                        oproperty = Setup.checkCreateColumn(con, oclass, colName, determineOTypeFromClassName(val.getClass().getName()), errors);
                    }
                    if (doc != null && oproperty != null && val != null && !val.toString().equals("null")) {
                        if (DEBUG) System.out.println("importObject.Setting field "+colName+" to "+val);
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
                if (keyval != null && !keyval.equals("null")) {
                    String q = "SELECT FROM "+doc.getClassName()+" WHERE "+keycol+" = "+wrapWithQuotes(keyval);
                    if (DEBUG) System.out.println("Resolving "+keycol+" with "+q);
                    QueryResult qr = con.query(q);
                    if (qr.size() == 0) {
                        errors.append(paragraph("Did not resolve reference SELECT FROM "+doc.getClassName()+" WHERE "+keycol+"="+wrapWithQuotes(keyval)));
                        doc.save();
                    } else {
                        errors.append(paragraph("Resolved reference to "+doc.getClassName()+" using "+keycol+"="+keyval));
                        doc.delete();
                        return qr.get(0);
                    }        
                } else {
                    errors.append(paragraph("error", "Could not resolve key value "+keyval+" from "+keycol+" in "+classname));
                    return null;
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
            otype = OType.LINK;
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
