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
import permeagility.util.Setup;
import permeagility.web.Weblet;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class ImportJSON extends Weblet {

	public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
	
		StringBuilder sb = new StringBuilder();
		StringBuilder errors = new StringBuilder();

		String submit = parms.get("SUBMIT");
		String run = parms.get("RUN");
		String fromURL = parms.get("FROM_URL");
		String fromText = parms.get("FROM_TEXT");
		String toTable = parms.get("TO_TABLE");
		String replace = parms.get("REPLACE");
		String go = parms.get("GO");
		
		OSchema schema = con.getSchema();
		
		if (go != null) {
			if (fromText != null && !fromText.equals("")) {
				errors.append(paragraph("Using pasted text"));
				JSONObject jo = new JSONObject(fromText);
//				errors.append(paragraph("Got jo:"+jo.toString()));
				JSONArray names = jo.names();
				for (int i=0; i<names.length(); i++) {
					Object o = names.get(i);
					if (o instanceof JSONObject) {
						JSONObject jo2 = (JSONObject)o;
						errors.append(paragraph("Got jo2:"+o.toString()));								
					} else {
						errors.append(paragraph("Got jo2:"+o.getClass().getName()+":"+o.toString()));		
						OClass oclass = null;
						if (toTable != null && !toTable.equals("")) {
							String ccTable = makePrettyCamelCase(toTable);
							sb.append("Creating table "+toTable+" with name "+ccTable);
							oclass = Setup.checkCreateTable(schema, ccTable, errors);							
						}
						JSONArray array = jo.getJSONArray((String)o);
						for (int j=0; j<array.length(); j++) {
							Object ac = array.get(j);
							errors.append(paragraph("Array["+j+"]="+ac.getClass().getName()+":"+ac.toString()));	
							if (ac instanceof JSONObject) {
								ODocument doc = null;
								if (oclass != null) {
									doc = con.create(oclass.getName());
								}
								JSONObject acjo = (JSONObject)ac;
								JSONArray fields = acjo.names();
								for (int f=0; f<fields.length();f++) {
									String colName = fields.getString(f);
									String ccColName = makePrettyCamelCase(colName);
									Object val = acjo.get(colName);
									OProperty oproperty = null;
									errors.append(paragraph(colName+" is a "+val.getClass().getName()+" and its value is "+val));
									if (oclass != null) {
										oproperty = Setup.checkCreateColumn(con, oclass, ccColName, determineOTypeFromClassName(val.getClass().getName()), errors);
									}
									if (doc != null && oproperty != null) {
										doc.field(ccColName,val);
									}
								}	
								if (doc != null) {
									doc.save();
								}
							}
						}
					}
				}
			
			}
			
		} else {
			sb.append(paragraph("banner","Import JSON to a table"));
			sb.append(form(table("layout", hidden("RUN",run)
					+row(column("label","From URL")+column(input("FROM_URL",parms.get("FROM_URL"))))
					+row(column("label","or paste here:")+column(textArea("FROM_TEXT",parms.get("FROM_TEXT"),30,100)))
					+row(column("label","Table to create")+column(input("TO_TABLE",toTable)+" will be turned to camelCase"))
				//	+row(column("label","Replace if exists")+column(checkbox("REPLACE",false)+" will fail if table exists unless replace is checked"))
					+row(column("")+column(submitButton(con.getLocale(),"GO")+" load it"))
			)));
		}
		return head("Import JSON") + body(standardLayout(con, parms, errors.toString()+sb.toString()));
	}
	
	
	/** Determine the best lossless OrientDB representation 
	 * of the given java classname (fully qualified) of an object given in a result set 
	 * (not necessarily the most efficient storage or what you expect) 
	 * Note: This is a good candidate to be a general utility function but this is the only place it is used right now*/
	public OType determineOTypeFromClassName(String className) {
		OType otype = OType.STRING;  // Default
		if (className.equals("java.math.BigDecimal")) {
			otype = OType.DECIMAL;
		} else if (className.equals("java.util.Date")) {
			otype = OType.DATETIME;
		} else if (className.equals("java.lang.Double")) {
			otype = OType.DOUBLE;
		} else if (className.equals("java.lang.Integer")) {
			otype = OType.INTEGER;
		}
		//System.out.println(className+" becomes "+otype);
		return otype;
	}

}
