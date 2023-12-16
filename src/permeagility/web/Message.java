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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import com.arcadedb.database.Document;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;


public class Message {

	public static boolean DEBUG = false;
	public static boolean ALERT_MISSING_MESSAGES = false;  // Will blast message to log about unfound messages - can be annoying
	public static String DEFAULT_LOCALE = "en";
	
	private static HashMap<Locale,TreeMap<String,String>> bundles = null;
	private static TreeMap<String,String> defaultBundle = null;  // For fallback (currently disabled)
	private static Locale defaultLocale = new Locale.Builder().setLanguage(DEFAULT_LOCALE).build();
		
	public Message () {
	}

	public static void initialize(DatabaseConnection con) {
		if (con == null) {
			System.out.println("Cannot initialize messages with null connection");
			return;
		}
		QueryResult qr = con.query("SELECT FROM "+Setup.TABLE_LOCALE+" WHERE active=true");
		if (qr == null || qr.size()==0) {
			System.out.println("Error - cannot load locale list from locale table");
			return;
		}
		
		HashMap<Locale,TreeMap<String,String>> newbundles = new HashMap<Locale,TreeMap<String,String>>(qr.size());
		for (int r=0; r<qr.size(); r++) {
			Locale locale = null;
			try {
				locale = parseLocaleString(qr.getStringValue(r, "name"));
			} catch (Exception e) {
				System.out.println("Unable to determine locale for:"+qr.getStringValue(r, "name"));
			}
			if (locale != null) {
				System.out.println("Message: Loading messages (locale="+locale+")");
				TreeMap<String,String> bundle = loadBundle(con, locale);
				if (bundle != null) {
					newbundles.put( locale, bundle );
					System.out.println("Added message bundle of "+bundle.size()+" messages for locale "+locale.toString());
					if (locale.getLanguage().equalsIgnoreCase(DEFAULT_LOCALE)) { defaultBundle = bundle; }
				}
				if (newbundles.size() == 1) { defaultLocale = locale; }  // First locale is default
			}
		}
		bundles = newbundles;
	}	

	public static Locale parseLocaleString(String s) {
			StringTokenizer lp = new StringTokenizer(s,"_");
			String language = lp.nextToken();
			String country = (lp.hasMoreTokens() ? lp.nextToken() : "");
			String variant = (lp.hasMoreTokens() ? lp.nextToken() : "");
			return new Locale.Builder().setLanguage(language).setRegion(country).setVariant(variant).build();
	}
	
	public static Locale getDefaultLocale() {
		return defaultLocale;
	}
		
	public static TreeMap<String,String> loadBundle(DatabaseConnection con, Locale locale) {
		TreeMap<String,String> table = new TreeMap<String,String>();
		try {
			String query = "SELECT FROM "+Setup.TABLE_MESSAGE+" WHERE locale.name='"+locale.toString()+"'";
			if (DEBUG) System.out.println("query: "+query );
			QueryResult rs = con.query(query);
			for (Document row : rs.get()) {
				table.put(row.getString("name"), row.getString("description"));
			}
			System.out.println("Loaded "+rs.size()+" rows into message bundle for "+locale.toString());
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		return table;
	}

	public void refresh(DatabaseConnection con, Locale locale) {
		TreeMap<String,String> bundle = loadBundle(con, locale);
		if (bundle != null) {
			bundles.put( locale, bundle );
			if (locale.getLanguage().equalsIgnoreCase(DEFAULT_LOCALE)) { defaultBundle = bundle; }
		}
	}	

	public static String getString(Locale locale,String key) {

		if (key==null) { return ""; }
		if (bundles == null) { return key; }
		TreeMap<String,String> bundle = bundles.get(locale);
		if (bundle == null) {
			bundle = defaultBundle;
			if (bundle == null) { return key; }
		}
		String string = (String)bundle.get(key);
		if(string==null && ALERT_MISSING_MESSAGES) {
			System.out.println("************************** Warning - no "+locale.getDisplayLanguage()+" message found for name:"+key);
		}
		return string != null ? string : key;
	}	

	public static Set<Locale> getLocales() {
		return bundles.keySet();
	}

	public String createMessageString(String s) {
		// Convert \r\n to \n string. Browser likes to add \r which we want to remove
		s = s.replaceAll("<", "&lt;"); // These characters cause problems with the HTML encoding
		s = s.replaceAll(">", "&gt;");
		StringBuilder sb = new StringBuilder(s.length());
		StringTokenizer st = new StringTokenizer(s,"\r\n",false);
		while (st.hasMoreTokens()) {
			String part = st.nextToken();
			sb.append(part);
			if(st.hasMoreTokens()) { sb.append("\n");}
		}
		return sb.toString();
	}

	public String createMessageStringForBrowser(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		StringTokenizer st = new StringTokenizer(s,"\n",false);
		while (st.hasMoreTokens()) {
			String part = st.nextToken();
			sb.append(part);
			if(st.hasMoreTokens()) { sb.append("\r\n"); }
		}
		return sb.toString();
	}

	public static String getLocaleSelector(Locale locale, HashMap<String,String> parms) {
		if (locale == null) {
		    locale = defaultLocale;
		}
		StringBuilder localeList = new StringBuilder();
		for (Locale l : getLocales()) {
		    if (!l.getLanguage().equals(locale.getLanguage())) {
			    localeList.append("<a class=\"menuitem\" href=\""+parms.get("REQUESTED_CLASS_NAME")+"?LOCALE="+l.getLanguage()+"\">"+l.getDisplayLanguage(l)+" "+l.getDisplayCountry(l)+" "+l.getDisplayVariant(l)+"</a><br>\n");
		    }				
		}
		return localeList.toString();
	 }
	
 	public static Locale getLocale(String l) {
 		for (Locale lo : getLocales()) {
		    if (lo.getLanguage().equals(l)) {
		    	return lo;
		    } 			
 		}
		return defaultLocale;
	}		

 	public static String get(Locale locale, String template) {
		return getString(locale,template);
	}

	public static String get(Locale locale, String template, String arg0) {
		Object[] messageArguments = {arg0};
		MessageFormat formatter = new MessageFormat("");
		formatter.setLocale(locale);
		
		try {
		    formatter.applyPattern(getString(locale,template));
		    return formatter.format(messageArguments);
		}
		catch (MissingResourceException e) {
		    return arg0;
		}
	}
	
   public static String get(Locale locale, String template, String arg0, String arg1) {
		Object[] messageArguments = {arg0, arg1};
		MessageFormat formatter = new MessageFormat("");
		formatter.setLocale(locale);
		try {
		    formatter.applyPattern(getString(locale,template));
		    return formatter.format(messageArguments);
		}
		catch (MissingResourceException e) {
		    return arg0 + ", " + arg1;
		}
    }
	
    public static String get(Locale locale, String template, String arg0, String arg1, String arg2) {
		Object[] messageArguments = {arg0, arg1, arg2};
		MessageFormat formatter = new MessageFormat("");
		formatter.setLocale(locale);
		try {
		    formatter.applyPattern(getString(locale,template));
		    return formatter.format(messageArguments);
		}
		catch (MissingResourceException e) {
		    return arg0 + ", " + arg1 + ", " + arg2;
		}
    }
    
}
