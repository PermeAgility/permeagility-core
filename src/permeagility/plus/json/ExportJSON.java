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
import permeagility.web.Weblet;
import permeagility.web.Schema;

/** Note: Uses the Download class to download the object */
public class ExportJSON extends Weblet {
    
    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {

        String onClick = """
            on click set @href to '/permeagility.plus.json.Download?FROMTABLE='
                    +FROMTABLE.value+'&SQL='+SQL.value+'&DEPTH='+DEPTH.value
            """;

        return paragraph("banner", "Export JSON from a table")
                  +table( 
                    row(column("label", "From Table") + column(Schema.getTableSelector(con, "FROMTABLE")))
                   + row(column("label", "or use SQL") + column(textArea("SQL","")))
                   + row(column("label", "Depth") + column(input("DEPTH","")))
                   + row(column("") + column("<a href=\"\" download=\"download.json\" _=\""+onClick+"\"><button>Download</button></a>"))
                ); 
    }

}
