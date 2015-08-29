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

/** This is a minimal Weblet for testing raw performance
 * the only difference is that this will be verified against security rights by the server before running
 * the database connection is given but not used by this test
 */
public class OK200 extends Weblet {

	@Override
	public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
		return "Hello World";
	}

}
