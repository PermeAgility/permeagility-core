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

public class Home extends Weblet {
	
  String pageBody = """
    <div id="header" hx-trigger="load" hx-get="/Header" hx-swap="innerHTML"></div>
    <div id="service" hx-trigger="load delay:100ms" hx-get="/News" hx-swap="innerHTML"></div>
    <div id="nav-container">
        <div id="underlay" class="bg"></div>
        <div id="nav-button" class="nav-button" tabindex="0">
            <span class="icon-bar"></span><span class="icon-bar"></span><span class="icon-bar"></span>
        </div>
        <div id="nav-content" hx-get="/Menu?TARGET=service" hx-trigger="load delay:50ms" hx-swap="innerHTML" tabindex="0"></div>
    </div>
""";

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {

        String title = Message.get(con.getLocale(), "HOME_PAGE_TITLE");
        return head(con, title)
             + body(pageBody+serviceHeaderUpdateDiv(parms, title));
    }
}
