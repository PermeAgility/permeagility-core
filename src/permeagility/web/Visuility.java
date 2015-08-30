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

import permeagility.util.DatabaseConnection;

public class Visuility extends Weblet {
	
    public String getPage(DatabaseConnection con, java.util.HashMap<String,String> parms) {
        parms.put("SERVICE", Message.get(con.getLocale(), "VISUILITY"));
        String type = parms.get("TYPE");
        String id = parms.get("ID");
        return 	
            head(Message.get(con.getLocale(), "VISUILITY"),getScript("d3.js"))+
            body(standardLayout(con, parms,  
                Schema.getTableSelector(con)
                +"<button style=\"position: fixed; bottom: 0px;\" id=\"save_as_svg\" download=\"view.svg\">to SVG</button>"
                +getScript("visuility.js")
                +(type != null && id != null ? makeScript("getMore('"+type+"','"+id+"')") : "")
            ));
    }

}


