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
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class RBuilder extends Table {

    // Processes in progress by User containing RScript id and process
    static ConcurrentHashMap<String,ConcurrentHashMap<String,Process>> processes = new ConcurrentHashMap<>();

    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        StringBuilder sb = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Locale locale = con.getLocale();

        String run = parms.get("RUN");
        String tableName = PlusSetup.TABLE;

        String update = processSubmit(con, parms, tableName, errors);
        if (update != null) {
            return update;
        }
        
        if (run != null) {
            System.out.println("Build R Process " + run);
            ODocument runDoc = con.get(run);
            if (runDoc == null) {
                errors.append(paragraph("Could not retrieve run details using " + run));
            } else {
                try {
                    File t1 = File.createTempFile("Rprocess", "out");
                    System.out.println("Temp file created "+t1.getAbsolutePath());
                    String execCommands[] = {"/bin/sh", "-c", "sleep 10; ls > "+t1.getAbsolutePath() };
                    System.out.println("Running command "+execCommands[2]);
                    Process newProcess = Runtime.getRuntime().exec(execCommands);
                    Thread waitForIt = new Thread() {
                        public void run() {
                            StringBuilder result = new StringBuilder();
                            try {
                                System.out.println("Waiting for process "+newProcess.toString()+" to complete");
                                int rc = newProcess.waitFor();
                                System.out.println("R Process waitFor ended with returnCode="+rc);
                                FileInputStream fis = new FileInputStream(t1);
                                if (fis.available() > 0) {
                                    int binc = fis.read();
                                    do {
                                        result.append((char)binc);
                                        binc = fis.read();
                                    } while (fis.available() > 0);
                                }
                            } catch (Exception e) {
                                System.out.println("Exception in R Process waitfor: "+e.getMessage());
                                e.printStackTrace();
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
                parms.put("SERVICE", "R Builder: Setup process");
                sb.append(paragraph("banner", "Select R Process"));
                sb.append(getTable(con, parms, PlusSetup.TABLE, "SELECT FROM " + PlusSetup.TABLE, null, 0, "name, description, button_RUN_Run),-"));
            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Error retrieving R Scripts patterns: " + e.getMessage());
            }
        }
        return head("R Builder", getScripts(con) )
                + body(standardLayout(con, parms,
                    ((Security.getTablePriv(con, PlusSetup.TABLE) & PRIV_CREATE) > 0 && run == null
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

}
