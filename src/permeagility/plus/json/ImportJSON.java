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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Type;

import permeagility.util.QueryResult;

public class ImportJSON extends Weblet {

    public static boolean DEBUG = true;

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
                    URL tURL = new URI(fromURL).toURL();
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
                    fis.close();
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
                    if (DEBUG) System.out.println("length="+fromText.length());
                    jo = new JSONObject(fromText.replace("\\u0027", "\""));
                    Document doc;
                    doc = importObject(parms, run, con, parms.get("TABLE_FOR_"), jo, errors, classes);
                    if (doc != null) parseSuccess = true;
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
                    ArrayList<String> fields = new ArrayList<>(map.keySet());
                    sb.append("Use this field as a primary key "+createList(con.getLocale(), "KEY_FOR_"+cname, null, fields, null, true, null, true));

                    // then do the columns in the table
                    for (String column : map.keySet()) {
                        if (!column.equals("")) sb.append(map.get(column));
                    }
                }
                sb.append(hidden("FROM_TEXT",fromText.replace("\"","\\u0027")));
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
        return paragraph("banner", "Import JSON to a table")
                +form(sb.toString() + errors.toString());
    }

    /* Import a JSON Object as a Document */
    public Document importObject(HashMap<String,String> parms, boolean run, DatabaseConnection con, String typename
                , JSONObject acjo, StringBuilder errors, HashMap<String,HashMap<String,String>> classes) {
        if (DEBUG) System.out.println("importObject.JSONObject="+acjo.getClass().getName());
        String originalTypeName = typename;
        if (acjo.has("@class")) {
            typename = acjo.getString("@class");
        }
        String newTypeName = parms.get("TABLE_FOR_"+typename);
        if (newTypeName != null && !newTypeName.isEmpty()) {
            typename = newTypeName;
        }
        DocumentType docType = con.getSchema().getType(typename);
        MutableDocument doc = null;
        String keycol = parms.get("KEY_FOR_"+typename);
        if (keycol != null && (keycol.isEmpty() || keycol.equals("null"))) keycol = null;
        String keyval = null;  // Hold a key value for resolution against a primary key

        if (typename != null && !typename.isEmpty()) {
            if (run) {
                String ccTable = makePrettyCamelCase(typename);
                docType = Setup.checkCreateTable(con.getSchema(), typename, errors);
            } else {
                if (!classes.containsKey(typename)) {
                 classes.put(typename, new HashMap<>());
                if (docType != null) {
                    classes.get(typename).put("",paragraph("Existing table will be loaded called "+input("TABLE_FOR_"+typename,typename)));
                } else {
                    classes.get(typename).put("",paragraph("Table will be created called "+input("TABLE_FOR_"+typename,typename)));
                }
                }
            }
        } else {
            typename = originalTypeName;
        }
        if (run && docType != null && doc == null) {
           doc = con.create(docType.getName());
        }
        JSONArray fields = acjo.names();
        for (int f = 0; fields != null && f < fields.length(); f++) {
            String colName = fields.getString(f);
            Object val = acjo.get(colName);  // get the value before changing the name to an identifier
//            if (colName.startsWith("@")) colName = colName.substring(1);
            if (colName.startsWith("@")) continue;  // do not import these
            colName = makePrettyCamelCase(colName);
            String originalColName = colName;
            String newColName = parms.get("COLUMN_"+typename+"_"+colName);
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
                List<Document> docList = new ArrayList<>();
                for (int j = 0; j < array.length(); j++) {
                    Object ac = array.get(j);
                    if (DEBUG) System.out.println("Array[" + j + "]=" + ac.getClass().getName() /*  + ":" + ac.toString() */);
                    if (ac instanceof JSONObject) {
                        if (run) {
                            Document subdoc;
                            subdoc = importObject(parms, run, con, colName, (JSONObject)ac, errors, classes);
                            Property oproperty = null;
                  //          if (!colName.equals(classname) && oclass != null && subdoc != null) {
                  //              oproperty = Setup.checkCreateColumn(con, oclass, colName, Type.LINKLIST, subdoc.getSchemaClass(), errors);
                  //          }
                  //          if (oproperty != null && subdoc != null) {
                  //              docList.add(subdoc);
                  //          }
                        } else {
                            if (DEBUG) System.out.println("className="+typename+" colName="+colName);
                            if (classes.containsKey(typename)) {  // must be at the top level
                                classes.get(typename).put(colName,paragraph("Column " + input("COLUMN_"+typename+"_"+colName,colName) + " is an array and it will be a LINKLIST"));
                            }
                            importObject(parms, run, con, colName, (JSONObject)ac, errors, classes);
                        }
                    }
                }
                if (run && doc != null && docList.size() > 0) {
                    doc.set(colName, docList);
                }

            } else if (val instanceof JSONObject) {
                Property oproperty = docType != null ? docType.getProperty(colName) : null;  // See if property already exists
                if (run) {
                    if (oproperty == null) {
                        Document subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                        if (DEBUG) System.out.println("importObject.subdoc="+subdoc);
            //            if (subdoc != null && doc != null) {
            //                oproperty = Setup.checkCreateColumn(con, oclass, colName, Type.LINK, subdoc.getSchemaClass(), errors);
            //            }
            //            if (doc != null && oproperty != null && !subdoc.isEmpty()) {
            //                doc.set(colName, subdoc);
            //            }
                    } else if (oproperty.getType() == Type.MAP) {
                        if (DEBUG)  System.out.println("importObject(into map of class "+oproperty.getOfType()+")");
                        JSONObject j = (JSONObject)val;
                        HashMap<String,Object> newMap = new HashMap<>();
                        for (String n : j.keySet()) {
                            if (DEBUG) System.out.println("key="+n);
                            Object subval = j.get(n);
                            if (subval instanceof JSONObject) {
                                Document subdoc = importObject(parms, run, con, oproperty.getOfType(), (JSONObject)subval, errors, classes);
                                if (subdoc != null ) {
                                    if (DEBUG) System.out.println("adding (into map) "+n+": "+subdoc);
                                    newMap.put("'"+n+"'",subdoc);
                                }
                            }
                        }
                        if (doc != null && newMap.size() > 0) {
                            doc.set(colName, newMap);
                        }
                    } else if (oproperty.getType() == Type.STRING) {
                        if (DEBUG) System.out.println("Importing object into STRING");
                        JSONObject j = (JSONObject)val;
                        doc.set(colName,j.toString());
                    } else {
                        errors.append(paragraph("error", "I don't know how to import an object into the "+colName+" column of type "+oproperty.getType().name()));
                    }
                } else {
                    if (oproperty != null) {
                        if (oproperty.getType() == Type.MAP) {
                            classes.get(typename).put(colName,paragraph("Column " + input("COLUMN_"+typename+"_"+colName,colName) + " is an object and it will use existing LINKMAP"));
//                            ODocument subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);

                        }
                    } else {
                        classes.get(typename).put(colName,paragraph("Column " + input("COLUMN_"+typename+"_"+colName,colName) + " is an object and it will be a LINK"));
                        Document subdoc = importObject(parms, run, con, colName, (JSONObject)val, errors, classes);
                    }
                }
            } else {
                if (run) {
                    Property oproperty = null;
                    if (docType != null) {
                        oproperty = Setup.checkCreateColumn(con, docType, colName, determineTypeFromClassName(val.getClass().getName()), errors);
                    }
                    if (doc != null && oproperty != null && val != null && !val.toString().equals("null")) {
                        if (DEBUG) System.out.println("importObject.Setting field "+colName /*+" to "+val */);
                        doc.set(colName, val);
                    }
                } else {
                    classes.get(typename).put(colName,paragraph("Column "+input("COLUMN_"+typename+"_"+colName,colName)
                            +" of type " + val.getClass().getName()
                            + " will be a "+determineTypeFromClassName(val.getClass().getName())
                    ));
                }
            }
        }
        if (doc != null && docType.isSubTypeOf("restricted")) {
            doc.set("_allow",permeagility.web.Security.getUserRoles(con));  // Give the doc a default owner
        }
        if (doc != null) {
            if (keycol != null) {
                if (keyval != null && !keyval.equals("null")) {
                    String q = "SELECT FROM "+doc.getTypeName()+" WHERE "+keycol+" = "+wrapWithQuotes(keyval);
                    if (DEBUG) System.out.println("Resolving "+keycol+" with "+q);
                    QueryResult qr = con.query(q);
                    if (qr.size() == 0) {
                        errors.append(paragraph("Did not resolve reference SELECT FROM "+doc.getTypeName()+" WHERE "+keycol+"="+wrapWithQuotes(keyval)));
                        doc.save();
                    } else {
                        errors.append(paragraph("Resolved reference to "+doc.getTypeName()+" using "+keycol+"="+keyval));
                        //doc.delete();
                        return qr.get(0);
                    }
                } else {
                    errors.append(paragraph("error", "Could not resolve key value "+keyval+" from "+keycol+" in "+typename));
                    return null;
                }
            } else {
                doc.save();
            }
        }
        return doc;
    }
    /**
     * Determine the best lossless representation of the given java classname (fully qualified) of an object given in a result set (not necessarily the most efficient storage or what you expect) Note: This is a good candidate to be a general utility function but this is the only place it is used right now
     */
    public Type determineTypeFromClassName(String className) {
        Type otype = Type.STRING;  // Default
        if (className.endsWith("JSONObject$Null")) {
            otype = Type.LINK;
        } else if (className.endsWith("JSONObject")) {
            otype = Type.LINK;
        } else if (className.equals("java.math.BigDecimal")) {
            otype = Type.DECIMAL;
        } else if (className.equals("java.util.Date")) {
            otype = Type.DATETIME;
        } else if (className.equals("java.lang.Double")) {
            otype = Type.DOUBLE;
        } else if (className.equals("java.lang.Integer")) {
            otype = Type.INTEGER;
        }
        if (DEBUG) System.out.println(className+" becomes "+otype);
        return otype;
    }

}
