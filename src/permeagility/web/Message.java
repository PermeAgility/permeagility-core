/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.DatabaseSetup;
import permeagility.util.QueryResult;

import com.orientechnologies.orient.core.record.impl.ODocument;

public class Message {

	public static boolean DEBUG = false;
	public static boolean ALERT_MISSING_MESSAGES = false;  // Will blast message to log about unfound messages - can be annoying
	public static String DEFAULT_LOCALE = "en";
	
	private static HashMap<Locale,TreeMap<String,String>> bundles = null;
	private static TreeMap<String,String> defaultBundle = null;  // For fallback (currently disabled)
	private static Locale defaultLocale = new Locale(DEFAULT_LOCALE);
		
	public Message () {
	}

	public Message (DatabaseConnection con) {
		initialize(con);
	}
	
	public static void initialize(DatabaseConnection con) {
		if (con == null) {
			System.out.println("Cannot initialize messages with null connection");
			return;
		}
		QueryResult qr = con.query("SELECT name FROM "+DatabaseSetup.TABLE_LOCALE+" WHERE active=true");
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
			return new Locale(language,country,variant);
	}
	
	public static Locale getDefaultLocale() {
		return defaultLocale;
	}
		
	public static TreeMap<String,String> loadBundle(DatabaseConnection con, Locale locale) {
		TreeMap<String,String> table = new TreeMap<String,String>();
		try {
//			String query = "SELECT name, description FROM message WHERE locale.name='"+locale.toString()+"' ORDER BY name";
			String query = "SELECT name, description FROM "+DatabaseSetup.TABLE_MESSAGE+" WHERE locale.name='"+locale.toString()+"'";  // Not sure if order helps the TreeMap
			if (DEBUG) System.out.println("query: "+query );
			QueryResult rs = con.query(query);
			for (ODocument row : rs.get()) {
				table.put((String)row.field("name",String.class),(String)row.field("description",String.class));
			}
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
		StringBuffer sb = new StringBuffer(s.length());
		StringTokenizer st = new StringTokenizer(s,"\r\n",false);
		while (st.hasMoreTokens()) {
			String part = st.nextToken();
			sb.append(part);
			if(st.hasMoreTokens()) { sb.append("\n");}
		}
		return sb.toString();
	}

	public String createMessageStringForBrowser(String s) {
		StringBuffer sb = new StringBuffer(s.length());
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
		StringBuffer localeList = new StringBuffer(100);
		for (Locale l : getLocales()) {
		    if (!l.getLanguage().equals(locale.getLanguage())) {
			    localeList.append("<A HREF=\""+parms.get("REQUESTED_CLASS_NAME")+"?LOCALE="+l.getLanguage()+"\">"+l.getDisplayLanguage(l)+" "+l.getDisplayCountry(l)+" "+l.getDisplayVariant(l)+"</A><BR>\n");
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

   public static String getHeaderKey(String name) {
		name = name.trim();
		name = name.replace(' ','_');
		name = name.toUpperCase();
		return name;
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
