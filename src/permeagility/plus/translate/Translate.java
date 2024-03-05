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
package permeagility.plus.translate;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;
import permeagility.web.Server;
import permeagility.web.Table;


public class Translate extends Table {

	private static String credit = " translation provided by <a href='http://mymemory.translated.net'>http://mymemory.translated.net</a>";
	
	@Override
	public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
	
		StringBuilder sb = new StringBuilder();
		StringBuilder errors = new StringBuilder();

		String submit = parms.get("SUBMIT");
		String connect = parms.get("CONNECT");
		String editId = parms.get("EDIT_ID");
		String updateId = parms.get("UPDATE_ID");
		String run = parms.get("RUN");
		String tableName = parms.get("TABLENAME");

		// Process update of work tables
		if (updateId != null && submit != null) {
			System.out.println("update_id="+updateId);
			if (submit.equals("DELETE")) {
				// TODO: Need to delete all messages, etc for a locale before deleting locale
				if (deleteRow(con, tableName, parms, errors)) {
					submit = null;
				} else {
					return head(con, "Could not delete")
							+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
				}
			} else if (submit.equals("UPDATE")) {
				System.out.println("In updating row");
				if (updateRow(con, tableName, parms, errors)) {
					submit = null;
				} else {
					return head(con, "Could not update")
							+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
				}
			} 
			// Cancel is assumed
			editId = null;
			updateId = null;
			connect = parms.get(PARM_PREFIX+"path");
		}

		// Create a SQL import directly - set the created date
		if (submit != null && submit.equals("CREATE_ROW")) {
			parms.put(PARM_PREFIX+"created", formatDate(con.getLocale(), new java.util.Date()));
			boolean inserted = insertRow(con,tableName,parms,errors);
			if (!inserted) {
				errors.append(paragraph("error","Could not insert"));
			}
		}
		
		// Show edit form if row selected for edit
		if (editId != null && submit == null && connect == null) {
			return head(con, "Edit")
					+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms)));
		}
		
		if (run != null) {
			Document fromLocale = con.get(run);
			String toLocale = parms.get("TO_LOCALE");
			if (toLocale == null || fromLocale == null || submit == null || !submit.equals("GO")) {
				sb.append(paragraph("banner","New locale messages to generate"));
				sb.append(form(table("layout", hidden("RUN",run)
						+row(column("")+column(credit))
						+row(column("label","To Locale/Language")+column(input("TO_LOCALE","")+" use standard two character ISO 639-1 language code"))
						+row(column("label","Description")+column(input("DESCRIPTION","")+" only used if new locale created"))
						+row(column("label","Replace if exists")+column(checkbox("REPLACE",false)+" Unchecked preserves existing items (much faster)"))
						+row(column("label","Translate messages")+column(checkbox("DO_MESSAGES",false)+" will include menus, tableGroups, tables, and columns if they exist"))
						+row(column("label","Translate menus")+column(checkbox("DO_MENUS",false)+" MENU_name, MENUITEM_name, MENUITEMDESC_name will be used as message names"))
						+row(column("label","Translate table group names")+column(checkbox("DO_TABLEGROUPS",false)+" will use TABLEGROUP_name as message name"))
						+row(column("label","Translate table names")+column(checkbox("DO_TABLES",false)+" will use TABLE_name as message name"))
						+row(column("label","Translate column names")+column(checkbox("DO_COLUMNS",false)+" will use COLUMN_name as message name"))
						+row(column("label","Translate news articles")+column(checkbox("DO_NEWS",false)+" will create new articles for the new locale"))
						+row(column("label","Email address")+column(input("EMAIL","")+" up to 10000 words/day if email given"))
						+row(column("")+column(submitButton(con.getLocale(),"GO")+" warning: can take a while"))
				)));
			} else {
				Document oldLocale = con.queryDocument("SELECT FROM "+Setup.TABLE_LOCALE+" WHERE name='"+toLocale+"'");
				MutableDocument newLocale;
				if (oldLocale == null) {
					newLocale = con.create(Setup.TABLE_LOCALE);
					newLocale.set("name",toLocale).set("description",parms.get("DESCRIPTION")).set("active",false).save();
				} else {
					newLocale = (MutableDocument)oldLocale;
				}
				
				if (fromLocale != null && newLocale != null) {
					String doReplace = parms.get("REPLACE");
					boolean replace = false;
					if (doReplace != null && (doReplace.equals("on")|| doReplace.equals(",on"))) {
						replace = true;
					}
					String doMessages = parms.get("DO_MESSAGES");
					String doMenus = parms.get("DO_MENUS");
					String doTableGroups = parms.get("DO_TABLEGROUPS");
					String doTables = parms.get("DO_TABLES");
					String doColumns = parms.get("DO_COLUMNS");
					String doNews = parms.get("DO_NEWS");
					
					int translateCount = 0;

					String email = parms.get("EMAIL");
					if (email != null) {
						email = "&de="+email;
					} else {
						email = "";
					}

					if (doMessages != null && doMessages.equals("on")) {
						QueryResult qr = con.query("SELECT FROM message WHERE locale=#"+run);
						for (Document m : qr.get()) {
							String messageName = m.getString("name");
							Document newMessage = null;
							newMessage = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='"+messageName+"' AND locale="+newLocale.getIdentity().toString());
							if (newMessage != null && replace == false) {
								continue;
							}
							String fromText = m.getString("description");
							String originalText = fromText;
							fromText = fromText.replace("{0}", "0").replace("{1}", "1").replace("{2}", "2").replace("{3}", "3");
							String newText = translate(fromLocale, toLocale, sb, fromText, email);
							if (newText != null) {
									updateMessage(con, messageName, originalText, newText, newLocale);
							}
							translateCount++;
						}
					}
					
					if (doMenus != null && doMenus.equals("on")) {
						QueryResult qr = con.query("SELECT FROM menu");
						for (Document m : qr.get()) {
							String menuName = m.getString("name");
							if (menuName != null && !menuName.trim().equals("")) {
								Document newMessage = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='MENU_"+menuName+"' AND locale="+newLocale.getIdentity().toString());
								if (newMessage != null && replace == false) {
									continue;
								}
								String originalText = menuName;
								String fromText = menuName;
								String newText = translate(fromLocale, toLocale, sb, fromText, email);
								if (newText != null) {
									updateMessage(con, "MENU_"+menuName, originalText, newText, newLocale);
								}
								translateCount++;
							}
						}
						qr = con.query("SELECT FROM menuItem");
						for (Document m : qr.get()) {
							String menuName = m.getString("name");
							String menuDesc = m.getString("description");
							if (menuName != null && !menuName.trim().equals("")) {
								Document newMessage = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='MENUITEM_"+menuName+"' AND locale="+newLocale.getIdentity().toString());
								Document newMessageDesc = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='MENUITEMDESC_"+menuName+"' AND locale="+newLocale.getIdentity().toString());
								if (newMessage == null || replace == true) {
									String originalText = menuName;
									String fromText = menuName;
									String newText = translate(fromLocale, toLocale, sb, fromText, email);
									if (newText != null) {
										updateMessage(con, "MENUITEM_"+menuName, originalText, newText, newLocale);
										translateCount++;
									}
								}
								if (newMessageDesc == null || replace == true) {
									String newDesc = translate(fromLocale, toLocale, sb, menuDesc, email);
									if (newDesc != null) {
										updateMessage(con, "MENUITEMDESC_"+menuName, menuDesc, newDesc, newLocale);
										translateCount++;
									}
									translateCount++;
								}
							}
						}
					}

					if (doTableGroups != null && doTableGroups.equals("on")) {
						QueryResult qr = con.query("SELECT name FROM "+Setup.TABLE_TABLEGROUP);
						for (Document m : qr.get()) {
							String tName = m.getString("name");
							if (tName != null && !tName.trim().equals("")) {
								Document newMessage = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='TABLEGROUP_"+tName+"' AND locale="+newLocale.getIdentity().toString());
								if (newMessage != null && replace == false) {
									continue;
								}
								String originalText = tName;
								String newText = translate(fromLocale, toLocale, sb, makeCamelCasePretty(tName), email);
								if (newText != null) {
									updateMessage(con, "TABLEGROUP_"+tName, originalText, newText, newLocale);
								}
								translateCount++;
							}
						}
					}

					if (doTables != null && doTables.equals("on")) {
						QueryResult qr = con.query("SELECT name FROM (select expand(classes) from metadata:schema)");
						for (Document m : qr.get()) {
							String tName = m.getString("name");
							if (tName != null && !tName.trim().equals("")) {
								Document newMessage = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='TABLE_"+tName+"' AND locale="+newLocale.getIdentity().toString());
								if (newMessage != null && replace == false) {
									continue;
								}
								String originalText = tName;
								String newText = translate(fromLocale, toLocale, sb, makeCamelCasePretty(tName), email);
								if (newText != null) {
									updateMessage(con, "TABLE_"+tName, originalText, newText, newLocale);
								}
								translateCount++;
							}
						}
					}

					if (doColumns != null && doColumns.equals("on")) {
						QueryResult qrTables = con.query("SELECT name FROM (select expand(classes) from metadata:schema)");
                                                for (Document tabDoc : qrTables.get()) {
                                                    String tabName = tabDoc.getString("name");
                                                    QueryResult qr = con.query("select distinct(name) as name from (select expand(properties) from (select expand(classes) from metadata:schema) where name='"+tabName+"')");
                                                    for (Document m : qr.get()) {
                                                            String cName = m.getString("name");
                                                            if (cName != null && !cName.trim().equals("")) {
                                                                    Document newMessage = con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='COLUMN_"+tabName+"."+cName+"' AND locale="+newLocale.getIdentity().toString());
                                                                    if (newMessage != null && replace == false) {
                                                                            continue;
                                                                    }
                                                                    String originalText = cName;
                                                                    String newText = translate(fromLocale, toLocale, sb, makeCamelCasePretty(cName), email);
                                                                    if (newText != null) {
                                                                            updateMessage(con, "COLUMN_"+tabName+"."+cName, originalText, newText, newLocale);
                                                                    }
                                                                    translateCount++;
                                                            }
                                                    }
                                                }
					}

					if (doNews != null && doNews.equals("on")) {
						QueryResult qr = con.query("SELECT FROM "+Setup.TABLE_NEWS+" WHERE (archive is null OR archive=false) AND locale=#"+run);
						for (Document newsDoc : qr.get()) {
							String cName = newsDoc.getString("name");
							String cDesc = newsDoc.getString("description");
							QueryResult existingArticles = con.query("SELECT FROM "+Setup.TABLE_NEWS+" WHERE name='"+cName+"' AND locale="+newLocale.getIdentity().toString());
							if (existingArticles != null && existingArticles.size() > 0 && replace == false) {
								continue;
							}
							if (cDesc.length() > 500) {
								cDesc = cDesc.substring(0,499);  // Limit of 500 characters
							}
							if (cName != null && !cName.trim().equals("")) {
								String newText = translate(fromLocale, toLocale, sb, cName, email);
								String newDesc = translate(fromLocale, toLocale, sb, cDesc, email);
								if (newText != null && newDesc != null) {
									MutableDocument newsArticle = con.create(Setup.TABLE_NEWS);
									newsArticle.set("name",newText);
									newsArticle.set("description",newDesc);
									newsArticle.set("dateline",newsDoc.getDate("dateline"));
									newsArticle.set("locale",newLocale);
									newsArticle.set("archive",false);
						//			newsArticle.field("_allowRead",(Set)newsDoc.get("_allowRead"));
									newsArticle.save();
								}
								translateCount++;
							}
						}
					}

					if (translateCount > 0) {
						Server.tableUpdated(con, Setup.TABLE_LOCALE);
					}
					sb.append(paragraph("Translated "+translateCount+" messages"));
				}
			}
		}
		
		if (sb.length() == 0) {
	    	try {
	    		parms.put("SERVICE", "Translate messages");
				sb.append(paragraph("banner","Select locale"));
				sb.append(getTable(con,parms,WORK_TABLE,null, null,0, "name, description, button(RUN:Translate), -"));
	    	} catch (Exception e) {  
	    		e.printStackTrace();
	    		sb.append("Error retrieving locales: "+e.getMessage());
	    	}
		}
		return 	head(con, "Translate")+body(standardLayout(con, parms, errors.toString() + sb.toString() ));
	}

	public String translate(Document fromLocale, String toLocale, StringBuilder sb, String fromText, String email) {
		fromText = fromText.trim().replace("\n"," ").replace("\r"," ").replace(" ", "%20");
		String newText = null;
		try {
			URL tURL = new URI("http://api.mymemory.translated.net/get?q="+fromText+email+"&langpair="+fromLocale.getString("name")+"|"+toLocale).toURL();
			if (tURL != null) {
		//		MutableDocument rDoc = new MutableDocument().fromJSON(tURL.openStream());
		//		if (rDoc != null) {
		//			Map<String,Object> rdm = rDoc.getMap("responseData");
		//			newText = (String)rdm.get("translatedText");
		//			System.out.println(fromText+"=>"+newText);
		//			sb.append(paragraph("&nbsp;&nbsp;&nbsp;from: "+fromText+"<br>&nbsp;&nbsp;&nbsp;to: "+newText));
		//			List<Map<String,Object>> matches = rDoc.field("matches");
		//			if (matches != null) {
		//				for (Map<String,Object> match : matches) {
		//					sb.append(paragraph("match: qual="+match.get("quality")+" t="+match.get("translation")+" created="+match.get("created-by")));
		//				}
		//			}
		//		}									
			}
		} catch (Exception e) {
			sb.append(paragraph(e.getMessage()));
		}
		return newText;
	}

	/** Updates or inserts a message - will replace {0}, {1}... in new text if the original had them */
	public boolean updateMessage(DatabaseConnection con, String messageName, String originalText, String newText, Document newLocale) {
		newText = replaceIfOriginal(newText, originalText, "{0}");
		newText = replaceIfOriginal(newText, originalText, "{1}");
		newText = replaceIfOriginal(newText, originalText, "{2}");
		newText = replaceIfOriginal(newText, originalText, "{3}");
		MutableDocument newMessage = null;
		newMessage = (MutableDocument)con.queryDocument("SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE name='"+messageName+"' and locale="+newLocale.getIdentity().toString());
		if (newMessage == null) {
			newMessage = con.create(Setup.TABLE_MESSAGE);
		}
		newMessage.set("name",messageName);
		newMessage.set("description",newText);
		newMessage.set("locale",newLocale);
		newMessage.save();
		return true;
	}

	/** Replaces a given text in a message if it is in the original (also tries to position it in a similar place to the original (front-back)) */
	private String replaceIfOriginal(String newText, String originalText, String thing) {
		if (originalText.contains(thing) && !newText.contains(thing)) {
			if (originalText.startsWith(thing)) {
				newText = thing+" "+newText;
			} else {
				newText = newText+" "+thing;
			}
		}
		return newText;
	}

	public static final String WORK_TABLE = "locale";
	
}
