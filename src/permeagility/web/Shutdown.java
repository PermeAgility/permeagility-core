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
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;

public class Shutdown extends Weblet {

    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
        String service = Message.get(con.getLocale(), "SHUTDOWN_SERVER");
        parms.put("SERVICE", service);
        StringBuilder errors = new StringBuilder();

        if (parms.get("SUBMIT") != null && parms.get("SUBMIT").equals("CANCEL")) {
            return redirect(parms, "/");
        }

        if (parms.get("SUBMIT") == null || !parms.get("SUBMIT").equals("CONFIRM_SHUTDOWN")) {
            return head(con, service)
                    + standardLayout(con, parms,
                            errors
                            + paragraph("banner", Message.get(con.getLocale(), "CONFIRM_SHUTDOWN"))
                            + form("SHUTDOWN_FORM",
                                    hidden("SHUTDOWN", parms.get("SHUTDOWN"))
                                    + paragraph(Message.get(con.getLocale(), "SHUTDOWN_CONFIRM_MESSAGE"))
                                    + paragraph(checkbox("WITH_RESTART", true) + "&nbsp;" + Message.get(con.getLocale(), "SHUTDOWN_RESTART"))
                                    + submitButton(con.getLocale(), "CONFIRM_SHUTDOWN")
                                    + "&nbsp;&nbsp;&nbsp;&nbsp;"
                                    + submitButton(con.getLocale(), "CANCEL")
                            )
                    );
        } else {
            System.out.println("Database and Server shutdown initiated by user " + con.getUser());
            Server.restore_lockout = true;
            int exitCode = parms.get("WITH_RESTART") != null ? 1 : 0;
            try {
                return redirect(parms, "/");
            } catch (Exception e) {
            } finally {
                Server.exit(exitCode);
            }
            return redirect(parms, "/");
        }
    }

}
