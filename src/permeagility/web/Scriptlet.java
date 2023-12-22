/*
 * Copyright 2016 PermeAgility Incorporated.
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
import com.arcadedb.database.Document;
import permeagility.util.DatabaseConnection;

/**
 * Generate a page, parms and con passed in and an HTML string is expected out
 * @author glenn
 */
public class Scriptlet extends Table {

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
        String serviceName = "Scriptlet page: none";
    	StringBuilder sb = new StringBuilder();

        Document menuItem = con.get(parms.get("ID"));
        if (menuItem != null) {
            serviceName = menuItem.getString("name");
            parms.put("SERVICE",serviceName);
            String styleScript = menuItem.getString("pageStyle");
            String htmlScript = menuItem.getString("pageScript");

            // Will likely allow some templating or var subs here but for now, just dump it
            sb.append(htmlScript == null ? "" : htmlScript);
            return head(con, serviceName,(styleScript == null ? "" : styleScript))
                 + body(sb.toString());
        } else {
            // If no script (probably new) return blank, otherwise, show an error
            return paragraph("error","No Scriptlet: "+menuItem+" maybe the ID was not specified, try adding ?ID=rid");
        }
    }
}
