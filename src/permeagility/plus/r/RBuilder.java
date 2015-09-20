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
package permeagility.plus.r;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;
import permeagility.web.Message;
import permeagility.web.Security;
import permeagility.web.Table;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import permeagility.web.Server;
import permeagility.web.Thumbnail;

public class RBuilder extends Table {

    // Processes in progress by RScript ID containing RScript id and process
    static ConcurrentHashMap<String,Process> processes = new ConcurrentHashMap<>();

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        String run = parms.get("RUN");
        String viewText = parms.get("VIEWTEXT");
        String viewPDF = parms.get("VIEWPDF");
        String tableName = PlusSetup.TABLE;

        // Handle data editing using the default table behaviour
        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) { return update; }
        
        if (viewText != null) {
            if (processes.get(viewText) != null) {
                errors.append(paragraph("warning","Process is still running"));
            } else {
                ODocument doc = con.get(viewText);
                if (doc == null) {
                    errors.append(paragraph("error","View text - document is null"));
                } else {
                    String textResult = doc.field("textResult");
                    if (textResult == null) {
                        errors.append(paragraph("error","No results found"));
                    } else {
                        textResult = textResult.replace("<","&lt;").replace(">","&gt;").replace("\n", "<br>");
                        return head("R Builder - Text result view", getScripts(con) )
                            + body(standardLayout(con, parms, textResult));
                    }
                }
            }
        } else if (viewPDF != null) {
            if (processes.get(viewPDF) != null) {
                errors.append(paragraph("warning","Process is still running"));
            } else {
                StringBuilder desc = new StringBuilder();
                String tid = Thumbnail.getThumbnailId(tableName, viewPDF, "PDFResult", desc);
                return head("R Builder - PDF result view", getScripts(con) )
                    + body(standardLayout(con, parms,
                        "<object data=\"/thumbnail?SIZE=FULL&ID="+tid+"\" width=\"100%\" height=\"100%\">"
                            +"<a href=\"/thumbnail?SIZE=FULL&ID="+tid+"\">Click to download "+desc.toString()+"</a> "
                       +"</object>"     
                ));
            }
        } else if (run != null) {
            System.out.println("Build R Process " + run);
            ODocument runDoc = con.get(run);
            if (runDoc == null) {
                errors.append(paragraph("Could not retrieve run details using " + run));
            } else if (processes.get(run) != null) {
                errors.append(paragraph("warning","Process is already running"));
            } else {
                try {
                    File pdf = File.createTempFile("RProcess", ".pdf");
                    System.out.println("Temp pdf file created "+pdf.getAbsolutePath());

                    File rscript = File.createTempFile("RScript", ".r");
                    System.out.println("Temp rscript file created "+rscript.getAbsolutePath());
                    String rsrc = runDoc.field("RScript");
                    runDoc.field("status","Running");
                    // Replace $$pdf$$ with path to PDF output
                    // example cookie options: "name" = "PermeAgilitySession1999", "value" = "admin2.029913179111651E7"
                    //rsrc = rsrc.replace("$$cookie$$", "\"name\"=\"PermeAgilitySession"+Server.getHTTPPort()+"\", \"value\"=\""+parms.get("COOKIE_VALUE")+"\"");
                    // Write the script to the file - put output to pdf instruction at the top
                    Files.write(rscript.toPath(), ("pdf(\""+pdf.getAbsolutePath()+"\")\n").getBytes(), StandardOpenOption.WRITE);
                    String rCode = "library(httr)\n"
                        +"PermeAgilityCSV <- function(p) {\n"
                        + "    content(GET(paste(\"http://localhost:"+Server.getHTTPPort()
                                +"/permeagility.plus.csv.Download?\", URLencode(p), sep=\"\"), set_cookies(\"name\" = \"PermeAgilitySession"+Server.getHTTPPort()
                                +"\", \"value\" = \""+parms.get("COOKIE_VALUE")+"\")))\n"
                        + "}\n";
                    Files.write(rscript.toPath(), rCode.getBytes(), StandardOpenOption.APPEND);
                    Files.write(rscript.toPath(), rsrc.getBytes(), StandardOpenOption.APPEND);
                    
                    File output = File.createTempFile("RProcess", ".out");
                    System.out.println("Temp output file created "+output.getAbsolutePath());
                     
                    String execCommands[] = {"/bin/sh", "-c", "r --vanilla -f "+rscript.getAbsolutePath()+" 1>"+output.getAbsolutePath()+" 2>&1" };
                    
                    System.out.println("Running command "+execCommands[2]);
                    Process newProcess = Runtime.getRuntime().exec(execCommands);
                    processes.put(run, newProcess);
                    Thread waitForIt = new Thread() {
                        public void run() {
                            String runDocId = runDoc.getIdentity().toString().substring(1);
                            DatabaseConnection threadCon = con.getNewConnection();
                            StringBuilder result = new StringBuilder();
                            try {
                                System.out.println("Waiting for process "+newProcess.toString()+" to complete");
                                int rc = newProcess.waitFor();
                                System.out.println("R Process waitFor ended with returnCode="+rc);
                                FileInputStream fis = new FileInputStream(output);
                                if (fis.available() > 0) {
                                    int binc = fis.read();
                                    do {
                                        result.append((char)binc);
                                        binc = fis.read();
                                    } while (fis.available() > 0);
                                }
                                ODocument resultDoc = threadCon.get(runDocId);
                                resultDoc.field("textResult", result);
                                updateBlobFromFile(resultDoc, "RScript", "PDFResult", pdf.getAbsolutePath());
                                resultDoc.field("status","Finished");
                                resultDoc.save();
                                processes.remove(runDocId);
                            } catch (Exception e) {
                                System.out.println("Exception in R Process waitFor: "+e.getMessage());
                                e.printStackTrace();
                            } finally {
                                if (threadCon != null) {
                                    con.freeNewConnection(threadCon);
                                }
                            }                          
                            System.out.println("Result is "+result.toString());
                        }
                    };
                    waitForIt.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                parms.put("SERVICE", "Running:" + runDoc.field("name"));
             }
        }
        if (sb.length() == 0) {
            try {
                parms.put("SERVICE", "R Builder");
                sb.append(getTable(con, parms, PlusSetup.TABLE, "SELECT FROM " + PlusSetup.TABLE, null, 0, "name,description,button_RUN_Run,status,button_VIEWTEXT_ViewText,button_VIEWPDF_ViewPDF,-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving R Scripts patterns: " + e.getMessage());
            }
        }
        return head("R Builder", getScripts(con) )
                + body(standardLayout(con, parms,
                    ((Security.getTablePriv(con, PlusSetup.TABLE) & PRIV_CREATE) > 0
                    ? popupForm("CREATE_NEW_ROW", null, Message.get(locale, "CREATE_ROW"), null, "NAME",
                            paragraph("banner", Message.get(locale, "CREATE_ROW"))
                            + hidden("TABLENAME", PlusSetup.TABLE)
                            + getTableRowFields(con, PlusSetup.TABLE, parms)
                            + submitButton(locale, "CREATE_ROW"))
                    : "")
                    + errors.toString()
                    + sb.toString()
         ));
    }

    	public boolean updateBlobFromFile(ODocument doc, String table, String blobName, String blobFile) {
            if (doc != null) {
                if (blobFile != null && !blobFile.trim().equals("")) {
                    System.out.println("Writing blob "+blobFile+" to "+table+" row="+doc.getIdentity().toString());
                    ORecordBytes record = new ORecordBytes();
                    try {
                        ByteArrayOutputStream fo = new ByteArrayOutputStream();
                        fo.write("application/pdf".getBytes());
                        fo.write(0x00);
                        fo.write((doc.field("name")+".pdf").getBytes());
                        fo.write(0x00);				
                        record.fromInputStream(new SequenceInputStream(
                                new ByteArrayInputStream(fo.toByteArray())
                                ,new FileInputStream(blobFile)
                        ));
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    record.save();
                    doc.field(blobName,record);
                    Thumbnail.createThumbnail(table, doc, blobName);
                }
            } else {
                System.out.println("Table.updateBlobs() - document is null");
                return false;
            }
            return true;
	}

    @Override
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms) {
        System.out.println("RBuilder: Get table row fields");
        return getTableRowFields(con, table, parms, "name,description,RScript,-");
    }

}
