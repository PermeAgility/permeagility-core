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

import java.util.HashMap;
import permeagility.util.DatabaseConnection;
import permeagility.web.Weblet;
import permeagility.web.Schema;

public class ExportCSV extends Weblet {
    
    @Override
    public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
         return head(con, "Export CSV") + body(standardLayout(con, parms, 
                paragraph("banner", "Export CSV from a table")
                +table("layout", 
                    row(column("label", "From Table") + column(Schema.getTableSelector(con,"selectedTable")))
                   + row(column("label", "or use SQL") + column(ngTextArea("sql")))
                   + row(column("") + column("<a href=\"permeagility.plus.csv.Download?FROMTABLE={{selectedTable.table}}&SQL={{sql}}\" download=\"data.csv\"><button>Download</button></a>"))
                )
                + br()
                + xSmall("permeagility.plus.csv.Download?FROMTABLE={{selectedTable.table}}&SQL={{sql}}")
        ));
    }
}
