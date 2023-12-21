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

import com.arcadedb.database.Document;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class News extends Weblet {
	
    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {

        StringBuilder sb = new StringBuilder();
		String query = "SELECT FROM "+Setup.TABLE_NEWS
				+" WHERE (archive IS NULL or archive=false) and (locale IS NULL or locale.name='"+con.getLocale().getLanguage()+"') "
				+" ORDER BY dateline desc ";

		QueryResult qr = con.query(query);
		for (Document d : qr.get()) {
			sb.append(div("headline","headline", d.getString("name")));
			sb.append(div("dateline", "dateline", formatDate(con.getLocale(), d.getDate("dateline"), "MMMM dd yyyy")));
			sb.append(div("article", "article", d.getString("description")));
		}
		String title = Message.get(con.getLocale(), "NEWS_PAGE_TITLE");
		return headMinimum(con, title) 
                + body (sb.toString()+serviceHeaderUpdateDiv(title));
    }

}
