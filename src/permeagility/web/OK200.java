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
