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
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import permeagility.web.Server;
import static permeagility.web.Table.PARM_PREFIX;
import permeagility.web.Thumbnail;
import static permeagility.web.Weblet.div;
import static permeagility.web.Weblet.getSplitScript;
import static permeagility.web.Weblet.hidden;
import static permeagility.web.Weblet.paragraph;
import static permeagility.web.Weblet.popupBox;
import static permeagility.web.Weblet.popupForm;
import static permeagility.web.Weblet.script;

public class RBuilder extends Table {

//    public static String R_COMMAND = "/bin/bash -c r";
    public static String R_COMMAND = "/usr/local/bin/r";
    public static boolean DEBUG = true;

    // Processes in progress by RScript ID containing RScript id and process
    static ConcurrentHashMap<String, Process> processes = new ConcurrentHashMap<>();

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
        parms.put("SERVICE", "R Builder");
        
        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        String serviceStyleOverride = "<style>#service { top: 0px !important; left: 0px !important; }</style>";

        String submit = parms.get("SUBMIT");
        String preview = parms.get("PREVIEW");
        String viewText = parms.get("VIEWTEXT");
        String tableName = PlusSetup.TABLE;
        
        // Handle data editing using the default table behaviour
        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) { return update; }

        // If there was an update, run it
        if (submit != null && submit.equals("UPDATE")) { 
            runRProcess(con, parms, errors); 
        }  

        // PDF Preview
        if (preview != null && !preview.equals("null")) {
            ODocument viewDoc = con.get(preview);
            if (viewDoc == null) {
                errors.append(paragraph("Could not retrieve preview using " + preview));
            } else {
                StringBuilder desc = new StringBuilder();
                String tid = Thumbnail.getThumbnailId(tableName, preview, "PDFResult", desc);
                if (tid != null) {
                    sb.append("<object data=\"/thumbnail?SIZE=FULL&ID=" + tid + "\" width=\"100%\" height=\"100%\">"
                           + "<a href=\"/thumbnail?SIZE=FULL&ID=" + tid + "\">Click to download " + desc.toString() + "</a> " + "</object>");
                } else {
                    sb.append(paragraph("warning","No Results"));
                }
                return head("R Builder", serviceStyleOverride) + body(errors.toString()+div("service",sb.toString()));
            }
        }

        // Text result
        if (viewText != null && !viewText.equals("null")) {
            if (processes.get(viewText) != null) {
                errors.append(paragraph("warning", "Process is still running"));
            } else {
                ODocument doc = con.get(viewText);
                if (doc == null) {
                    errors.append(paragraph("error", "View text - document is null"));
                } else {
                    String textResult = doc.field("textResult");
                    if (textResult == null) {
                        textResult = "No results found";
                    }
                    return head("R Builder - Text result view") + bodyOnLoad("<pre>"+textResult+"</pre>", "window.scrollTo(0, document.body.scrollHeight);");
                }
            }
        }

        // If nothing else happened, show the list of R Scripts owned by the user
        if (sb.length() == 0) {
            try {
                sb.append(getTable(con, parms, PlusSetup.TABLE, "SELECT FROM " + PlusSetup.TABLE+" WHERE _allow contains(name='"+con.getUser()+"')", null, 0, "name,description,RScript,-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving R Scripts: " + e.getMessage());
            }
        }
        
        // Return the default result
        return head("R Builder", getScripts(con))
        + body(standardLayout(con, parms,
            ((Security.getTablePriv(con, PlusSetup.TABLE) & PRIV_CREATE) > 0
                ? popupForm("CREATE_NEW_ROW", null, Message.get(locale, "CREATE_ROW"), null, "NAME",
                    paragraph("banner", Message.get(locale, "CREATE_ROW"))
                    + hidden("TABLENAME", PlusSetup.TABLE)
                    + super.getTableRowFields(con, PlusSetup.TABLE, parms, "name,description,-")
                    + submitButton(locale, "CREATE_ROW"))
                : "")
           + "&nbsp;&nbsp;"
            + errors.toString()
            + sb.toString()
        ));
    }

    public boolean updateBlobFromFile(ODocument doc, String table, String blobName, String blobFile) {
        if (doc != null) {
            try {
                if (blobFile != null && !blobFile.trim().equals("")) {
                    System.out.println("Writing blob " + blobFile + " to " + table + " row=" + doc.getIdentity().toString());
                    ORecordBytes record = new ORecordBytes();
                    try {
                        ByteArrayOutputStream fo = new ByteArrayOutputStream();
                        fo.write("application/pdf".getBytes());
                        fo.write(0x00);
                        fo.write((doc.field("name") + ".pdf").getBytes());
                        fo.write(0x00);
                        record.fromInputStream(new SequenceInputStream(
                                new ByteArrayInputStream(fo.toByteArray()), new FileInputStream(blobFile)
                        ));
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    record.save();
                    doc.field(blobName, record);
                    Thumbnail.createThumbnail(table, doc, blobName);
                }
            } catch (Exception e) {
                System.out.println("Cannot save PDF:" + e.getMessage());
                return false;
            }
        } else {
            System.out.println("RBuilder.updateBlobFromFile() - document is null");
            return false;
        }
        return true;
    }

    @Override
    public String getTableRowForm(DatabaseConnection con, String table, HashMap<String, String> parms) {
        return getTableRowFields(con, table, parms, null);
    }

     /**
     * Returns the fields for a table - can be for insert of a new row or update of an existing (as specified by the EDIT_ID in parms)
     */
    @Override
    public String getTableRowFields(DatabaseConnection con, String table, HashMap<String, String> parms, String columnOverride) {
        String edit_id = parms.get("EDIT_ID");
        ODocument initialValues = null;
        if (edit_id != null) {
            initialValues = con.get(edit_id);
            if (initialValues == null) {
                return paragraph("error", Message.get(con.getLocale(), "ERROR_IN_QUERY",edit_id));
            }
        }
        boolean readOnly = false;  // Assume a new doc
        if (initialValues != null) {
            readOnly = Security.isReadOnlyDocument(con, initialValues);
        }
        String formName = (edit_id == null ? "NEWROW" : "UPDATEROW");
        String init = initialValues != null ? initialValues.field("RScript") : null;
        if (init == null) init = "# R Script "+new Date()+"\n";
        String scriptEditor = getCodeEditorControl(formName, PARM_PREFIX + "RScript", init, "text/x-rsrc", readOnly ? ",readOnly:true" : null);                    
        String resultText = frame("resultFrame","/permeagility.plus.r.RBuilder?VIEWTEXT="+edit_id);  //"<iframe id='resultFrame' width='100%' height='100%'></iframe>\n";

        String resultView = 
                (readOnly ? "" : 
                    button("UpdateButton", "UPDATEBUTTON","UPDATE",Message.get(con.getLocale(),"SAVE_AND_RUN"))+"&nbsp;&nbsp;&nbsp;"
                    +"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                    + popupBox("UPDATE_NAME", null, Message.get(con.getLocale(), "DETAILS"), null, "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "DETAILS"))
                        + hidden("TABLENAME", PlusSetup.TABLE)
                        + super.getTableRowFields(con, PlusSetup.TABLE, parms, "name,description,-")
                    )
                    + "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                )
                + popupForm("UPDATE_MORE", null, Message.get(con.getLocale(), "MORE"), null, "NAME",
                        paragraph("banner", Message.get(con.getLocale(), "MORE"))
                        + hidden("TABLENAME", PlusSetup.TABLE)
                        + (readOnly ? "" : deleteButton(con.getLocale())+"<br>")
                        + submitButton(con.getLocale(), "COPY")
                )
                +"<br>"+frame("previewFrame","/permeagility.plus.r.RBuilder?PREVIEW="+edit_id);  // <iframe id='previewFrame' width='100%' height='100%'></iframe>\n";

        return getSplitScript()
               +div("leftHand","split split-horizontal",div("scriptEditor","split content",scriptEditor)+div("resultText",resultText))
               +div("rightHand","split split-horizontal",resultView)
               +script("Split(['#leftHand', '#rightHand'], { sizes:[50,50], gutterSize: 8, cursor: 'col-resize' });\n"
                        + "Split(['#scriptEditor', '#resultText'], { direction: 'vertical', sizes: [50, 50], gutterSize: 8, cursor: 'row-resize' });\n"
                    +(readOnly ? "" : 
                         "d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "d3.select('#UpdateButton').on('click', function() { \n"
                       + "   "+PARM_PREFIX+"RScriptEditor.save();\n"
                       + "   var formData = new FormData();\n"
                       + "   formData.append('SUBMIT','UPDATE');\n"
                            + addFormData("RScript")
                            + addFormData("name")
                            + addFormData("description")
                       + "   d3.xhr('').post(formData, function(error,data) {   \n"                        
                       + "      d3.select('#resultFrame').attr('src','/permeagility.plus.r.RBuilder?VIEWTEXT="+edit_id+"');\n"
                       + "      d3.select('#previewFrame').attr('src','/permeagility.plus.r.RBuilder?PREVIEW="+edit_id+"');\n"
                       + "      d3.select('#headerservice').text(document.getElementById('"+PARM_PREFIX+"name').value);\n"
                       + "   });\n"
                       + "});\n")
                );
       
    }

    public String addFormData(String name) {
        return "formData.append('"+PARM_PREFIX+name+"',document.getElementById('"+PARM_PREFIX+name+"').value);\n";
    }

    private void runRProcess(DatabaseConnection con, HashMap<String,String> parms, StringBuilder errors) {
        String run = parms.get("EDIT_ID");
        System.out.println("Build R Process " + run);
        ODocument runDoc = con.get(run);
        if (runDoc == null) {
            errors.append(paragraph("Could not retrieve run details using " + run));
        } else if (processes.get(run) != null) {
            errors.append(paragraph("warning", "Process is already running"));
        } else {
            try {
                File pdf = File.createTempFile("RProcess", ".pdf");
                System.out.println("Temp pdf file created " + pdf.getAbsolutePath());

                File rscript = File.createTempFile("RScript", ".r");
                System.out.println("Temp rscript file created " + rscript.getAbsolutePath());
                String rsrc = runDoc.field("RScript");
                runDoc.field("status", "Running");
                // Write the script to the file - put output to pdf instruction and PermeAgilityCSV function at the top
                Files.write(rscript.toPath(), ("#---- PermeAgility Header ----#\npdf(\"" + pdf.getAbsolutePath() + "\")\n").getBytes(), StandardOpenOption.WRITE);
                String rCode = "library(httr)\n"
                        + "PermeAgilityCSV <- function(p) {\n"
                        + "    content(GET(paste(\"http://localhost:" + Server.getHTTPPort()
                        + "/permeagility.plus.csv.Download?\", URLencode(p), sep=\"\"), set_cookies(\"name\" = \"PermeAgilitySession" + Server.getHTTPPort()
                        + "\", \"value\" = \"" + parms.get("COOKIE_VALUE") + "\"))) }\n#---- End of PermeAgility Header ----#\n\n";
                Files.write(rscript.toPath(), rCode.getBytes(), StandardOpenOption.APPEND);
                System.out.println("RCode preamble is "+rCode.length());
                Files.write(rscript.toPath(), rsrc.getBytes(), StandardOpenOption.APPEND);

                File output = File.createTempFile("RProcess", ".out");
                System.out.println("Temp output file created " + output.getAbsolutePath());

                String execCommand = R_COMMAND + " --quiet --vanilla -f " + rscript.getAbsolutePath() + " 1>" + output.getAbsolutePath() + " 2>&1";
                String execCommands[] = {"/bin/bash", "-c", R_COMMAND+" --quiet --vanilla -f " + rscript.getAbsolutePath() + " 1>" + output.getAbsolutePath() + " 2>&1"};

                System.out.println("Running command " + execCommands[2]);
                Process newProcess = Runtime.getRuntime().exec(execCommands);
                processes.put(run, newProcess);
                String runDocId = runDoc.getIdentity().toString().substring(1);
                StringBuilder result = new StringBuilder();
                try {
                    System.out.println("Waiting for process " + newProcess.toString() + " to complete");
                    int rc = newProcess.waitFor();
                    System.out.println("R Process waitFor ended with returnCode=" + rc);
                    FileInputStream fis = new FileInputStream(output);
                    if (fis.available() > 0) {
                        int binc = fis.read();
                        do {
                            result.append((char) binc);
                            binc = fis.read();
                        } while (fis.available() > 0);
                    }
                    int endOfHeader = result.toString().indexOf("#---- End of PermeAgility Header ----#");
                    //sb.append("<p>"+ result.toString().substring(endOfHeader > rCode.length() ? endOfHeader : 0)+"</p>");
                    String textResult = result.toString().substring(endOfHeader > rCode.length() ? endOfHeader+38 : 0)
                            .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
                    runDoc.field("textResult", textResult);
                    if (updateBlobFromFile(runDoc, "RScript", "PDFResult", pdf.getAbsolutePath())) {
                        runDoc.field("status", "Finished");
                    } else {
                        runDoc.field("status", "No PDF");
                    }
                    runDoc.save();
                    processes.remove(runDocId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            parms.put("SERVICE", "Running:" + runDoc.field("name"));
        }
    }
    
}
