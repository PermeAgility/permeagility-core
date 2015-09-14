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

import com.orientechnologies.orient.core.metadata.schema.OClass;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.Scanner;
import permeagility.util.Setup;
import permeagility.web.Table;

public class ImportCSV extends Weblet {
    
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
        String toTable = parms.get("TO_TABLE");

        if (!run && toTable != null && !toTable.isEmpty()) {
            sb.append(paragraph("Table name will be "+input("TO_TABLE",toTable)));
        }

        if (submit != null) {
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
                try { 
                    FileInputStream fis = new FileInputStream(fromFile);
                    StringBuilder content_type = new StringBuilder();
                    if (fis.available() > 0) {
                            int binc = fis.read();
                            do {
                                    content_type.append((char)binc);
                                    binc = fis.read();
                            } while (binc != 0x00 && fis.available() > 0);
                    }
                    sb.append(paragraph("ContentType: "+content_type));
                    StringBuilder content_filename = new StringBuilder();
                    if (fis.available() > 0) {
                            int binc = fis.read();
                            do {
                                    content_filename.append((char)binc);
                                    binc = fis.read();
                            } while (binc != 0x00 && fis.available() > 0);
                    }
                    sb.append(paragraph("ContentFilename: "+content_filename));
                    if (toTable == null || toTable.isEmpty()) {
                        toTable = content_filename.substring(0,content_filename.indexOf("."));
                        sb.append(paragraph("Table name will be "+input("TO_TABLE",toTable)));
                    }
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

            if (fromText == null || fromText.isEmpty()) {
                errors.append(paragraph("error","Nothing to parse"));
            } else {
                try {
                    BufferedReader from = new BufferedReader(new StringReader(fromText));
                    String line;
                    line = from.readLine();  // First line is column names
                    String[] columns = splitCSV(line);
                    if (columns.length == 0) {
                        errors.append(paragraph("error","No column names found in first line"));                
                    }
                    ODocument doc = null;
                    OClass docClass = null;
                    if (run) {
                        docClass = Setup.checkCreateTable(con.getSchema(), toTable, errors);
                    }
                    OType[] types = new OType[columns.length];
                    while ((line = from.readLine()) != null) {
                        String data[] = splitCSV(line);
                        if (data.length > 1) {
                            if (run && docClass != null) {
                                doc = con.create(toTable);
                            }                            
                            for (int i=0; i<columns.length; i++) {
                                String colName = parms.get("COLUMN_"+columns[i]);
                                if (colName == null || colName.isEmpty()) {
                                    colName = columns[i];
                                }
                                if (i < data.length) {
                                    if (types[i] == null) {
                                        String selType = parms.get("COLUMN_"+columns[i]+"_TYPE");
                                        if (selType != null && !selType.isEmpty()) {
                                            types[i] = Table.getOTypeFromName(selType);
                                        }
                                        if (types[i] == null) {
                                            types[i] = determineOTypeFromColumnNameAndData(columns[i], data[i]);
                                        }
                                    }
                                    if (run && doc != null && docClass != null) {
                                        Setup.checkCreateColumn(con, docClass, colName, types[i], errors);
                                        doc.field(colName,data[i]);
                                    }
                                    if (DEBUG) System.out.print(" "+columns[i]+" = "+data[i]);
                                } else {
                                    if (DEBUG) System.out.print(" "+columns[i]+" = null");                                
                                }
                            }
                        }
                        if (DEBUG) System.out.println();
                        if (run && doc != null && !doc.isEmpty()) {
                            doc.save();
                        }
                    }
                    if (!run) {
                        for (int i=0; i<columns.length; i++) {
                            sb.append(paragraph("Column " + input("COLUMN_"+columns[i],columns[i]) + " will be type "+Table.getDatatypeList(con.getLocale(), "COLUMN_"+columns[i]+"_TYPE", Table.getTypeName(types[i].getId()), null)));
                        }
                        sb.append(hidden("FROM_TEXT",fromText.replace("\"","\\u0022")));
                        sb.append(submitButton(con.getLocale(), "GO"));
                    }
                    if (run) errors.append(paragraph("success","Successfully parsed and imported "+toTable));                    
                } catch (Exception e) {
                    errors.append(paragraph("error","Error parsing CSV:"+e.getMessage()));
                    e.printStackTrace();
                }
            }
        } else {
            sb.append(table("layout", 
                     row(column("label", "From URL") + column(input("FROM_URL", parms.get("FROM_URL"))))
                    + row(column("label", "From File") + column(fileInput("FROM_FILE")))
                    + row(column("label", "or paste here:") + column(textArea("FROM_TEXT", parms.get("FROM_TEXT"), 30, 100)))
                    + row(column("label", "Table to create") + column(input("TO_TABLE", toTable) + " will be turned to camelCase"))
                    + row(column("") + column(submitButton(con.getLocale(), "PREVIEW")))
            ));
        }
        return head("Import CSV") + body(standardLayout(con, parms, 
                paragraph("banner", "Import CSV to a table")
                +form(sb.toString() + errors.toString())
            ));
    }

    /**
     * Determine the best lossless OrientDB representation of the given column
     */
    public OType determineOTypeFromColumnNameAndData(String columnName, String data) {
        OType otype = OType.STRING;  // Default
        
        // Try whole number
        try { long x = Long.parseLong(data);
                return OType.LONG;
        } catch (Exception e) {}
        
        // Try floating point number
        try { double x = Double.parseDouble(data);
            return OType.DOUBLE;
        } catch (Exception e) {}
        
        columnName = columnName.toUpperCase();
        if (columnName.endsWith("DATE")) {
            otype = OType.DATE;
        } else if (columnName.endsWith("TIME")) {
            otype = OType.DATETIME;
        }
        return otype;
    }

}
