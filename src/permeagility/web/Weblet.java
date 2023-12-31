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

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryCache;
import permeagility.util.QueryResult;
import permeagility.util.QueryResultCache;
import permeagility.util.Setup;

import com.arcadedb.database.Document;
import com.arcadedb.database.RID;
import com.arcadedb.schema.Property;
import com.arcadedb.serializer.json.JSONObject;

/** The Weblet help you build web pages with simple functional coding - can't be explained here - see examples */
public abstract class Weblet {

    public static final String CHARACTER_ENCODING = "utf-8";
    public static final Charset charset = Charset.forName("UTF-8");

    // These values can be overridden using the constant override - use permeagility.web.Context as this class is abstract
//    public static boolean DEBUG = false;
    public static int START_HOUR = 0;
    public static boolean TOP_OF_DAY = true;
    public static boolean INCLUSIVE_END_DAY = true;
    public static String INT_FORMAT = "#,##0";
    public static String FLOAT_FORMAT = "#,##0.00";
    public static String DATE_FORMAT = "yyyy-MM-dd";
    public static String TIME_FORMAT = "HH:mm:ss";
    public static String DECIMAL_FORMAT = "#,##0;(#,##0)";
    public static String DEFAULT_STYLE = "dark (horizontal menu)";
    public static String POPUP_SUFFIX = "..";
    public static String DEFAULT_TARGET = "service";

    
    public static String NONE_STRING = "null";  // this is what a null reference will convert to on description lookups (when something is deleted)

    // Override this to add body attributes (but don't remove the one that's there unless you are doing your own controls)
    public static String BODY_OPTIONS = "";
    public static String SCREEN_FADE = "";  // Injected into body to provide background 'blur' for a popup

    static QueryCache queryCache = new QueryCache();  // protected so that server can refresh it
    public static QueryCache getCache() { return queryCache; }

    protected byte[] doPage(DatabaseConnection con, HashMap<String, String> parms) {
             StringBuilder response = new StringBuilder();
            response.append("<!DOCTYPE html>\n<html>\n");
            response.append(getPage(con, parms));
            response.append("\n</html>\n");
            return response.toString().getBytes(charset);
    }

    public abstract String getPage(DatabaseConnection con, java.util.HashMap<String, String> parms);

    public String head(DatabaseConnection con, String title) { return head(con, title, ""); }
    public String head(DatabaseConnection con, String title, String script) {
        return "<head>\n" + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset="+CHARACTER_ENCODING+"\">\n"
            + "<meta http-equiv=\"Pragma\" content=\"no-cache\">\n" + "<meta http-equiv=\"Expires\" content=\"-1\">\n"
            + "<meta http-equiv=\"Cache-Control\" content=\"no-cache\">\n"
            + "<meta name=\"GENERATOR\" content=\"by PermeAgility\">\n" 
            + "<title>" + title + "</title>\n"
            + script + "\n"
            + "</head>\n";
    }

    public static String bodyMinimum(String s) { return "<body>"+"\n" + s + "</body>"; }
    public static String bodyMinimum(String att, String s) { return "<body "+att+">"+"\n" + s + "</body>"; }
    public static String body(String s) { return "<body "+BODY_OPTIONS+">"+"\n" + s +SCREEN_FADE+ "</body>"; }
    public static String body(String c, String s) { return "<body "+BODY_OPTIONS+" class=\"" + c + "\">"+"\n" + s +SCREEN_FADE+ "</body>"; }
    public static String bodyOnLoad(String s, String l) { return "<body "+BODY_OPTIONS+" onLoad=\"" + l + "\">"+"\n" + s + SCREEN_FADE + "</body>"; }
    public static String bodyWithAttribute(String s, String l) { return "<body "+BODY_OPTIONS+" " + l + "\">"+"\n" + s + SCREEN_FADE + "</body>"; }
    
    public static String span(String style, String s) { return "<span class=\"" + style + "\">\n" + s + "</span>\n"; }
    public static String div(String contents) { return "<div>\n" + (contents == null ? "" : contents) + "</div>\n"; }
    public static String div(String id, String contents) { return "<div "+(id == null ? "" : "id=\"" + id + "\"")+">\n" + (contents == null ? "" : contents) + "</div>\n"; }
    public static String div(String id, String classes, String contents) { return "<div "+(id == null ? "" : "id=\"" + id + "\"")+(classes == null ? "" : " class=\""+classes+"\"")+">\n" + (contents == null ? "" : contents) + "</div>\n"; }

    public static String chartDiv(String id) { return "<div id=\"" + id + "\" style=\"position: static; width: 100%; height: 100%; overflow: visible; \"></div>\n"; }

    public static String serviceHeaderUpdateDiv(HashMap<String,String> parms, String serviceText) { 
        String target = parms.get("HX-TARGET");
        if (target != null && !target.equals(DEFAULT_TARGET)) {
            return "";  // if we have a target but it is not the main default service, then don't update the header
        } else {
            return "<div _=\"on load wait 100ms then put '" + serviceText + "' into #headerservice\"></div>\n"; 
        }
    }

    public static String serviceNotificationDiv(String content) { 
        return "<div _=\"on load wait 1s then transition my opacity to 0 over 1.5s then transition my height to 0 over 0.5s then remove me\">"+content+"</div>\n"; 
    }

    public static String standardLayout(DatabaseConnection con, java.util.HashMap<String, String> parms, String html) {
        if (Menu.HORIZONTAL_LAYOUT) {  // Needs menu over header
            return div("header", (new Header()).getHTML(con, parms)) 
                + div("menu", (new Menu()).getHTML(con, parms))
                + div("service", html);
        } else {  // Need header over menu
            return div("menu", (new Menu()).getHTML(con, parms))  
                + div("header", (new Header()).getHTML(con, parms)) 
                + div("service", html);			
        }
    }

    public String redirect(HashMap<String,String> parms, Object object) { return redirect(parms, object, null); }
    public String redirect(HashMap<String,String> parms, Object object, String parameters) {    return redirect(parms, object.getClass().getName()+(parameters != null ? "?"+parameters : "")); }
    public String redirectHTMX(HashMap<String,String> parms, String key) {return redirect(parms, key); }
    public String redirect(HashMap<String,String> parms, String target) {
        parms.put("RESPONSE_REDIRECT", target);
        return null;
    }
        
    public static String image(String s) { return "<img src=\"../images/" + s + "\">\n"; }
    public static String image(String c, String s) { return "<img class=\"" + c + "\" src=\"../images/" + s + "\">\n"; }

    public static String center(String s) { return "<center>\n" + s + "</center>\n"; }

    public static String h1(String s) { return "<h1>" + s + "</h1>\n"; }
    public static String h2(String s) { return "<h2>" + s + "</h2>\n"; }
    public static String h3(String s) { return "<h3>" + s + "</h3>\n"; }
    public static String h4(String s) { return "<h4>" + s + "</h4>\n"; }
    public static String h5(String s) { return "<h5>" + s + "</h5>\n"; }
    public static String h6(String s) { return "<h6>" + s + "</h6>\n"; }

    public static String paragraph(String s) { return "<p>" + s + "</p>\n"; }
    public static String paragraph(String c, String s) { return "<p class=\"" + c + "\">" + s + "</p>\n"; }
    public static String paragraphLeft(String c, String s) { return "<p align=\"left\" class=\"" + c + "\">" + s + "</p>\n"; }
    public static String paragraphLeft(String s) { return "<p align=\"left\">" + (s == null ? "&nbsp;" : s) + "</p>\n"; }
    public static String paragraphRight(String s) { return "<p align=\"right\">" + (s == null ? "&nbsp;" : s) + "</p>\n"; }
    public static String paragraphCenter(String s) { return "<p align=\"center\">" + (s == null ? "&nbsp;" : s) + "</p>\n"; }
    public static String alignRight(String s) { return "<p align=\"right\">" + (s == null ? "&nbsp;" : s)+ "</p>\n"; }

    public static String br() { return "<br>"; }
    public static String bold(String s) { return "<b>" + (s == null ? "&nbsp;" : s) + "</b>"; }
    public static String color(String c, String s) { return "<font color=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</font>"; }

    public static String fontSize(int size, String s) { return "<FONT SIZE=\"" + size + "\">" + (s == null ? "&nbsp;" : s) + "</FONT>"; }
    public static String xxSmall(String s) { return fontSize(1, s); }
    public static String xSmall(String s) { return fontSize(2, s); }
    public static String small(String s) { return fontSize(3, s); }
    public static String medium(String s) { return fontSize(4, s); }
    public static String large(String s) { return fontSize(5, s); }
    public static String xLarge(String s) { return fontSize(6, s); }
    public static String xxLarge(String s) { return fontSize(7, s); }

    /*   Table */
    public static String table(String s) { return table(0, s); }  // Default table has no border
    public static String table(int border, String s) { return "<table"+(border>0 ? " border=\"" + border + "\"" : "")+">\n" + s + "</table>\n"; }
    public static String table(String c, String s) { return "<table class=\"" + c + "\">\n" + s + "</table>\n"; }

    public static String tableHTMX(String name, String c, String s) {
        String onLoad="";
        if (c.equals("sortable")) {
            onLoad = "_=\"on load if sorttable exists call sorttable.makeSortable(document.getElementById('"+name+"'))\"";
        }
        return "<table id=\""+name+"\" class=\"" + c + "\" "+onLoad+">\n" + s + "</table>\n";
    }

    public static String tableStart(int border) { return "<table"+(border>0 ? " border=\"" + border + "\"" : "")+">\n"; }
    public static String tableStart(String c) { return "<table class=\"" + c + "\">\n"; }
    public static String tableStart(String width, String c) { return "<table width=\""+width+"\" class=\"" + c + "\">\n"; }
    public static String tableEnd() { return "</table>\n"; }

    /* Table Header, Body, Footer */
    public static String tableHeader(String c, String s) {
        return "<thead class=\"" + c + "\">" + s + "</thead>\n";
    }

    public static String tableHeader(String s) { return "<thead>\n" + s + "</thead>\n"; }
    public static String tableBody(String c, String s) { return "<tbody class=\"" + c + "\">" + s + "</tbody>\n"; }
    public static String tableBody(String s) { return "<tbody>\n" + s + "</tbody>\n"; }
    public static String tableFooter(String c, String s) { return "<tfoot class=\"" + c + "\">\n" + s + "</tfoot>\n"; }
    public static String tableFooter(String s) { return "<tfoot>" + s + "</tfoot>\n"; }
    public static String row(String c, String s) { return "<tr class=\"" + c + "\">\n" + s + "</tr>\n";  }
    public static String row(String s) { return "<tr>\n" + s + "</tr>\n"; }

    public static String rowOnClick(String c, String s, String onClick) {
        return rowOnClick(c, s, onClick, null);
    }

    public static String rowOnClick(String c, String s, String onClick, String title) {
        return "<tr class=\"" + c + "\" href=\"" + onClick + "\" "+(title!=null ? " title=\""+title+"\"" : "")+" >\n" + (s == null ? "&nbsp;" : s) + "</tr>\n";
    }

    public static String rowOnClickHTMX(String c, String s, String onClick, String title) {
        return rowOnClickHTMX(c, s, onClick, title, DEFAULT_TARGET);
    }
    public static String rowOnClickHTMX(String c, String s, String onClick, String title, String target) {
        return "<tr class=\"" + c + "\" hx-target=\"#"+target+"\" hx-trigger=\"click\" hx-get=\"" + onClick + "\" "+(title!=null ? " title=\""+title+"\"" : "")+" >\n" + (s == null ? "&nbsp;" : s) + "</tr>\n";
    }

    public static String columnHeader(String c, String s) { return "<th class=\"" + c + "\">" + s + "</th>\n"; }
    public static String columnHeader(String s) { return "<th>" + s + "</th>\n"; }
    public static String columnHeaderNoSort(String s) { return "<th class=\"sorttable_nosort\">" + s + "</th>\n"; }
    public static String column(String s) { return "<td>" + (s == null ? "&nbsp;" : s) + "</td>\n"; }

    public static String column(String c, String s) {
        return "<td class=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String column(String c, int width, String s) {
        return "<td class=\"" + c + "\" width=\"" + width + "%\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String column(String c, int width, String s, String title) {
        return "<td class=\"" + c + "\" width=\"" + width + "%\" title=\""+ title +"\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String column(int width, String s) {
        return "<td width=\"" + width + "%\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnOnClick(int width, String s, String onClick) {
        return "<td width=\"" + width + "%\" onClick=\"" + onClick + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnRight(int width, String s) {
        return "<td align=\"right\" width=\"" + width + "%\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnColor(int width, String color) {
        return "<td bgcolor=\""+color+"\" width=\"" + width + "%\">" + (color == null ? "&nbsp;" : color) + "</td>\n";
    }

    public static String columnRight(String c, int width, String s) {
        return "<td align=\"right\" width=\"" + width + "%\" class=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnRight(String s) {
        return "<td align=\"right\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnRight(String c, String s) {
        return "<td align=\"right\" class=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnTop(int width, String s) {
        return "<td valign=\"top\" width=\"" + width + "%\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnTopRight(int width, String s) {
        return "<td align=\"right\" valign=\"top\" width=\"" + width + "%\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnCenter(int width, String s) {
        return "<td align=\"center\" width=\"" + width + "%\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnSpan(int width, String s) {
        return "<td colspan=\"" + width + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnSpanRight(int width, String s) {
        return "<td colspan=\"" + width + "\" align=\"right\" >" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnSpan(String c, int width, String s) {
        return "<td colspan=\"" + width + "\" class=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnNoWrap(String c, int width, String s) {
        return "<td nowrap width=\"" + width + "%\" class=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnNoWrap(int width, String s) {
        return "<td nowrap width=\"" + width + "%\" >" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnNoWrap(String s) {
        return "<td nowrap >" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnTopNoWrap(int width, String s) {
        return "<td valign=\"top\" nowrap width=\"" + width + "%\" >" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String columnSpanNoWrap(int span, String c, int width, String s) {
        return "<td colspan=\"" + span + "\" nowrap width=\"" + width + "%\" class=\"" + c + "\">" + (s == null ? "&nbsp;" : s) + "</td>\n";
    }

    public static String formHTMX(String n, String action, String method, String target, String s) { return formHTMX(n, action, method, target, s, ""); }
    public static String formHTMX(String n, String action, String method, String target, String s, String extraFormAttributes) {
        return "<form " + (n==null ? "" : "name=\""+n+"\"" ) + " hx-target=\"#" + target 
               + "\" hx-" + method.toLowerCase() + "=\"" + (action==null ? "" : action) + "\" "
               + "hx-swap=\"innerHTML\" enctype=\"multipart/form-data\" "+extraFormAttributes+">\n" + s + "</form>\n";
    }
    public static String form(String n, String action, String s) {
        return "<form " + (n==null ? "" : "name=\""+n+"\"" ) + " action=\"" + (action==null ? "" : action) + "\" "+
                "method=\"POST\" enctype=\"multipart/form-data\">\n" + s + "</form>\n";
    }
    public static String form(String n, String s) { return form(n, null, s); }
    public static String form(String s) { return form(null, null, s); }

    public static String formStart(String n, String action) {
    	return "<form name=\""+n+"\" action=\""+(action==null ? "" : action)+"\" method=\"POST\" enctype=\"multipart/form-data\">\n";
    }
        
    public static String formEnd() { return "</form>\n"; }
    
    /** Creates a link which will popup a form containing the content given as a parameter */
    public static String popupForm(String formName, String action, String linkText, String linkClass, String focusField, String content) {
        return "<a class=\"popuplink\">"+linkText+POPUP_SUFFIX+"</a>\n"
                +"<div class=\"canpopup\">\n"
                +"<form id=\""+formName+"\" name=\""+formName+"\" method=\"post\" ENCTYPE=\"multipart/form-data\" action=\""+(action==null ? "" : action)+"\" >\n"
                +content
                +"\n</form></div>\n";
    }

    /** Creates a link which will popup a form containing the content given as a parameter */
    public static String popupFormHTMX(String formName, String action, String method, String target, String linkText, String focusField, String content) {
        return "<a href=\"#popup-"+formName+"\" class=\"popbox\">"+linkText+POPUP_SUFFIX+"</a>\n"
                +"<div id=\"popup-"+formName+"\" class=\"modal\">\n"
                +"  <div class=\"pop-content\">\n"
                +"    <form  id=\""+formName+"\" name=\""+formName+"\" enctype=\"multipart/form-data\""
                +"           hx-"+method.toLowerCase()+"=\""+action+"\" hx-target=\"#"+target+"\" hx-swap=\"innerHTML\" class=\"form-container\">\n"
                +        content 
                +"    </form>\n"
                +"  </div>\n"
                +"</div>\n";
    }
    public static String POPUP_FORM_CLOSER = "&nbsp;<a href=\"#\" class=\"box-close\">x</a>\n";

    public static String popupHTMX(String formName, String linkText, String focusField, String content) {
        return "<a href=\"#popup-"+formName+"\" class=\"popbox\">"+linkText+POPUP_SUFFIX+"</a>\n"
                +"<div id=\"popup-"+formName+"\" class=\"modal\">\n"
                +"  <div class=\"pop-content\">\n"
                +        content
                +"  </div>\n"
                +"</div>\n";
    }

    /** Creates a link which will popup a form containing the content given as a parameter */
    public static String popupBox(String formName, String action, String linkText, String linkClass, String focusField, String content) {
        return "<a class=\"popuplink\">"+linkText+POPUP_SUFFIX+"</a>\n<div class=\"canpopup\">\n" +content +"\n</div>\n";
    }

    public static String popupBox(String linkText, String content) {
        return "<a class=\"popuplink\">"+linkText+POPUP_SUFFIX+"</a>\n<div class=\"canpopup\">\n" +content +"\n</div>\n";
    }

    public static String frame(String id) { return frame(id,null); }
    public static String frame(String id, String src) { return "<iframe id='"+id+"' "+(src == null ? "" : " src='"+src+"'")+" width='100%' height='100%'></iframe>\n"; }
    public static String frame(String id, String style, String src) { return "<iframe id='"+id+"' "+(src == null ? "" : " src='"+src+"'")+" class='"+style+"'></iframe>\n"; }
    
    public static String fieldSet(String s) { return "<fieldset>" + s + "</fieldset>"; }

    public static String hidden(String n, Object value) { return hidden(n, value, ""); }
    public static String hidden(String n, Object value, String options) {
            return "<input type=\"HIDDEN\" name=\"" + n + "\""  + (value == null ? "" : " value=\""+value+"\"") + (options == null ? "" : options) +">";
    }

    public static String TEXT_INPUT_OPTIONS = "spellcheck=\"false\"";  // Clear this to enable spell checking, add to this to add options to textual input fields

    public String input(int tabIndex, String n, Object value) {
            return "<input "+TEXT_INPUT_OPTIONS+" id=\"" + n + "\" name=\"" + n + "\" " + (isReadOnly() ? "DISABLED" : "") + "  value=\"" + (value == null ? "" : value)
                            + "\" tabindex=" + tabIndex + ">";
    }

    public String input(int tabIndex, String c, String n, Object value) {
            return "<input "+TEXT_INPUT_OPTIONS+" class=\"" + c + "\" id=\"" + n + "\" name=\"" + n + "\" type=\"TEXT\" " + (isReadOnly() ? "DISABLED" : "") + "  value=\""
                            + (value == null ? "" : value) + "\" TABINDEX=" + tabIndex + ">";
    }

    public String input(String n, Object value) {
            return "<input "+TEXT_INPUT_OPTIONS+" id=\"" + n + "\" name=\"" + n + "\" " + (isReadOnly() ? "DISABLED" : "") + "  value=\"" + (value == null ? "" : value) + "\">";
    }
    public String inputWithPlaceholder(String n, String placeholder) {
            return "<input "+TEXT_INPUT_OPTIONS+" placeholder=\""+placeholder+"\" id=\"" + n + "\" name=\"" + n + "\" " + (isReadOnly() ? "DISABLED" : "") + ">";
    }

    public String input(String c, String n, Object value) {
            return "<input "+TEXT_INPUT_OPTIONS+" class=\"" + c + "\" id=\"" + n + "\" name=\"" + n + "\" type=\"TEXT\" " + (isReadOnly() ? "DISABLED" : "") + "  value=\"" + (value == null ? "" : value) + "\">";
    }

    public String input(String c, String n, Object value, int size) {
            return "<input "+TEXT_INPUT_OPTIONS+" class=\"" + c + "\" id=\"" + n + "\" name=\"" + n + "\" type=\"TEXT\" size=" + size + (isReadOnly() ? "DISABLED" : "") + "  value=\"" + (value == null ? "" : value) + "\">";
    }

    public String input(String n, Object value, int size) {
            return "<input "+TEXT_INPUT_OPTIONS+" id=\"" + n + "\" name=\"" + n + "\" size=" + size + " " + (isReadOnly() ? "DISABLED" : "") + "  value=\"" + (value == null ? "" : value) + "\">";
    }

    public String inputChanged(String n, Object value, String onChange, int size) {
            return "<input "+TEXT_INPUT_OPTIONS+" " + (isReadOnly() ? "DISABLED" : "") + " id=\"" + n + "\" name=\"" + n + "\" size=" + size + " onChange=\"" + onChange
                            + "\" value=\"" + (value == null ? "" : value) + "\">";
    }

    public String inputChanged(String n, Object value, String onChange) {
            return "<input "+TEXT_INPUT_OPTIONS+" " + (isReadOnly() ? "DISABLED" : "") + " id=\"" + n + "\" name=\"" + n + "\" onChange=\"" + onChange + "\" value=\""
                            + (value == null ? "" : value) + "\">";
    }

    public static String inputDisabled(String n, Object value, int size) {
            return "<input id=\"" + n + "\" name=\"" + n + "\" size=" + size + " DISABLED value=\"" + (value == null ? "" : value) + "\">";
    }

    public String ngInput(String model) {
            return "<input "+TEXT_INPUT_OPTIONS+" ng-model=\"" + model + "\" type=\"TEXT\" " + (isReadOnly() ? "DISABLED" : "") + ">";
    }

    public String ngInput(String model, String init) {
            return "<input "+TEXT_INPUT_OPTIONS+" ng-model=\"" + model + "\" ng-init=\"" + init + "\" type=\"TEXT\" " + (isReadOnly() ? "DISABLED" : "") + ">";
    }

    public static String password() {
            return "<input type=\"PASSWORD\" name=\"PASSWORD\">";
    }

    public static String password(String value) {
            return "<input type=\"PASSWORD\" name=\"PASSWORD\" value=\"" + (value == null ? "" : value) + "\">";
    }

    public String checkbox(String name) {
            return checkbox(name, false);
    }

    public String checkbox(String name, boolean checked) {
            return "<input type=\"hidden\" name=\""+name+"\"/><input value=\"on\" type=\"CHECKBOX\" " + (isReadOnly() ? "DISABLED" : "") + " name=\"" + name + "\"" + ((checked) ? " CHECKED " : "") + ">";
    }

    public static String checkboxDisabled(String name) {
            return "<input type=\"CHECKBOX\" DISABLED name=\"" + name + "\">";
    }

    public static String checkboxDisabled(String name, boolean checked) {
            return "<input type=\"CHECKBOX\" DISABLED name=\"" + name + "\"" + ((checked) ? " CHECKED " : "") + ">";
    }

    public String radio(String name, String value) {
            return "<input type=\"RADIO\" " + (isReadOnly() ? "DISABLED" : "") + " name=\"" + name + "\" value=\"" + value + "\">";
    }

    public String radioChecked(String name, String value) {
            return "<input " + (isReadOnly() ? "DISABLED" : "") + " CHECKED type=\"RADIO\" name=\"" + name + "\" value=\"" + value + "\">";
    }

    public String textArea(String n, Object s) {
            return textArea(n, s, 10, 40);
    }

    public String textArea(String n, Object s, int rows, int cols) {
            return "<textarea "+TEXT_INPUT_OPTIONS+" id=\"" + n + "\" name=\"" + n + "\" rows=\"" + rows + "\" cols=\"" + cols + "\" " + (isReadOnly() ? " READONLY " : "")
                            + " >" + (s == null ? "" : s) + "</textarea>";
    }

    public String textArea(String n, Object s, int rows, int cols, String option) {
            return "<textarea "+TEXT_INPUT_OPTIONS+" id=\"" + n + "\" name=\"" + n + "\" rows=\"" + rows + "\" cols=\"" + cols + "\" " + (isReadOnly() ? " READONLY " : " ")
                            + option + ">" + (s == null ? "" : s) + "</textarea>";
    }

    public static String textAreaDisabled(String n, Object s, int rows, int cols) {
            return "<textarea "+TEXT_INPUT_OPTIONS+" id=\"" + n + "\" name=\"" + n + "\" rows=\"" + rows + "\" cols=\"" + cols + "\" DISABLED >" + (s == null ? "" : s) + "</textarea>";
    }

    public static String textAreaReadOnly(String n, Object s, int rows, int cols) {
            return "<textarea id=\"" + n + "\" name=\"" + n + "\" rows=\"" + rows + "\" cols=\"" + cols + "\" READONLY >" + (s == null ? "" : s) + "</textarea>";
    }

    public String ngTextArea(String model) {
        return ngTextArea(model, null, 10, 40);
    }
    
    public String ngTextArea(String model, String init) {
        return ngTextArea(model, init, 10, 40);
    }
    
    public String ngTextArea(String model, String init, int rows, int cols) {
            return "<textarea "+TEXT_INPUT_OPTIONS+" ng-model=\"" + model + "\" "+(init == null ? "" : "ng-init=\"" + init + "\"")+" rows=\"" + rows + "\" cols=\"" + cols + "\" " + (isReadOnly() ? " READONLY " : "") + "></textarea>";    
    }

    public String submitButton(Locale l, String s) { return submitButton(l, s, ""); }
    public String submitButton(Locale l, String s, String extraAtt) { return button(s.toLowerCase(), "SUBMIT", s, Message.get(l,s), extraAtt); }
    public String button(String name, String value, String text) {
            return "<button " + (isReadOnly() ? "DISABLED" : "") + " class=\"button\" name=\"" + name + "\" value=\"" + value + "\">" + text + "</button>";
    }
    public String button(String id, String name, String value, String text) {
            return "<button " + (isReadOnly() ? "DISABLED" : "") + " class=\"button\" id=\"" + id + "\" name=\"" + name + "\" value=\"" + value + "\">" + text + "</button>";
    }
    public String button(String id, String name, String value, String text, String extraAtt) {
            return "<button " + (isReadOnly() ? "DISABLED" : "") + " class=\"button\" id=\"" + id + "\" name=\"" + name + "\" value=\"" + value + "\" "+extraAtt+">" + text + "</button>";
    }

    public String deleteButton(Locale locale) {
            return confirmButton(locale, "DELETE", "DELETE_MESSAGE");
    }

    public String cancelButton(Locale locale, String table, String target) {  // HTMX Version
        if (target == null) target = DEFAULT_TARGET;
            return "<button hx-target=\"#"+target+"\" hx-swap=\"innerHTML\" hx-get=\""
                +"/"+this.getClass().getName()+"/"+table+"\"" 
                + "  class=\"button\" name=\"SUBMIT\" value=\"" + "CANCEL" + "\">"
            + Message.get(locale,"CANCEL") + "</button>";
    }

    public String deleteButton(Locale locale, String table, String edit_id, String target) {  // HTMX Version
            if (target == null) target = DEFAULT_TARGET;
            return "<button hx-target=\"#"+target+"\" hx-swap=\"innerHTML\" hx-delete=\""
                +"/"+this.getClass().getName()+"/"+table+"/"+edit_id+"\" " 
                +" hx-confirm=\""+Message.get(locale, "DELETE_MESSAGE")+"\" "
                + (isReadOnly() ? " DISABLED " : "") 
                + " class=\"button\" name=\"SUBMIT\" value=\"" + "DELETE" + "\">"
            + Message.get(locale,"DELETE") + "</button>";
    }

    public String deleteButton(Locale locale, String confirm) {
        return confirmButton(locale, "DELETE", confirm);
    }

    public String confirmButton(Locale l, String text, String confirm) {
            return "<button " + (isReadOnly() ? "DISABLED" : "") + "  class=\"button\" name=\"SUBMIT\" value=\"" + text + "\""
            + " onclick=\"javascript:if (confirm('" + Message.get(l, confirm) + "')) return true; else return false; \">"
            + Message.get(l,text) + "</button>";
    }

    public String resetButton(String n, String v) {
            return "<input " + (isReadOnly() ? "DISABLED" : "") + "  type=\"RESET\" name=\"" + n + "\" value=\"" + v + "\">";
    }

    public static String line() {
            return line(0);
    }

    public static String line(int size) {
            return "<hr size=\"" + size + "\">";
    }

    public static String bulletList(String s) {
            return "<ul>" + s + "</ul>";
    }

    public static String numberList(String s) {
            return "<ol>" + s + "</ol>";
    }

    public static String listItem(String s) {
            return "<li>" + s + "</li>";
    }

    /* Links */
    public static String anchor(String name) {
            return "<a id=\""+name+"\" name=\"" + name + "\"></a>\n";
    }
    public static String anchor(String name, String desc) {
            return "<a name=\"" + name + "\">" + desc + "</a>\n";
    }

    public static String link(String ref, String desc) {
            return "<a href=\"" + ref + "\">" + desc + "</a>\n";
    }

    public static String linkHTMX(String ref, String desc, String target) {
        return linkHTMX(ref, desc, target, "");
    }

    public static String linkHTMX(String ref, String desc, String target, String extra) {
        return "<a hx-target=\"#"+target+"\" hx-trigger=\"click\" hx-swap=\"innerHTML\" hx-get=\"" 
                + ref + "\" "+extra+" >" + desc + "</a>\n";
    }

    public static String linkNewWindow(String ref, String desc) {
            return "<a target=\"_blank\" href=\"" + ref + "\">" + desc + "</a>\n";
    }

    public static String linkWithTip(String ref, String desc, String tooltip) {
            return "<a href=\"" + ref + "\" title=\""+tooltip+"\">" + desc + "</a>\n";
    }

    public static String linkWithTipHTMX(String ref, String desc, String tooltip, String target) {
        return "<a hx-target=\"#"+target+"\" hx-trigger=\"click\" hx-swap=\"innerHTML\" hx-get=\"" 
                + ref + "\" title=\""+tooltip+"\">" + desc + "</a>\n";
    }

    public static String linkNewWindowWithTip(String ref, String desc, String tooltip) {
            return "<a target=\"_blank\" href=\"" + ref + "\" title=\""+tooltip+"\">" + desc + "</a>\n";
    }

    public static String link(String ref, String desc, String target) {
            return "<a href=\"" + ref + "\" target=\"" + target + "\">" + desc + "</a>\n";
    }

    public static String actionLink(String ref, String desc, String onClick) {
            return "<a href=\"" + ref + "\" OnClick=\"" + onClick + "\">" + desc + "</a>\n";
    }

    public static String actionLink(String ref, String desc, String onClick, String target) {
            return "<a href=\"" + ref + "\" target=\"" + target + "\" OnClick=\"" + onClick + "\">" + desc + "</a>\n";
    }

    public static String mailLink(String recip, String subject, String body, String desc) throws UnsupportedEncodingException {
            return "<a href=\"mailto:" + recip + "?subject=" + URLEncoder.encode(subject, CHARACTER_ENCODING).replace('+', ' ')
                            + "&body=" + URLEncoder.encode(body, CHARACTER_ENCODING).replace('+', ' ') + "\">" + desc + "</a>\n";
    }

    public static String mailLink(String recip, String subject, String body, String onClick, String desc)
                    throws UnsupportedEncodingException {
            return "<a href=\"mailto:" + recip + "?subject=" + URLEncoder.encode(subject, CHARACTER_ENCODING).replace('+', ' ')
                            + "&body=" + URLEncoder.encode(body, CHARACTER_ENCODING).replace('+', ' ') + "\" onClick=\"" + onClick + "\">"
                            + desc + "</a>\n";
    }

    public String fileInput(String name) {
        return "<input type=\"FILE\" " + (isReadOnly() ? "DISABLED" : "") + "  name=\"" + name + "\">";
    }

    public static String getScript(String name) { return "<script src=\"/js/"+name+"\"></script>\n"; }

    public static String script(String script) { return "<script>"+script+"</script>\n"; }

    public static String style(String script) { return "<style type=\"text/css\">\n" + script + "</style>\n"; }

    public String getCodeEditorControl(String formName, String controlName, String initialValue, String mode, ArrayList<String> submitCodeLines) {
        return getCodeEditorControl(formName, controlName, initialValue, mode, null, submitCodeLines);
    }

    public static String EDITOR_THEME = "ambiance";

    public String getCodeEditorControl(String formName, String controlName, String initialValue, String mode, String options, ArrayList<String> submitCodeLines) {
        if (submitCodeLines != null) submitCodeLines.add("call "+controlName+"Editor.save()");
        return "<textarea id=\""+formName+controlName+"\" name=\""+controlName+"\">"+(initialValue==null ? "" : initialValue)+"</textarea>\n"
            +" <script>\n"
            + "var "+controlName+"Editor = CodeMirror.fromTextArea(document.getElementById(\""+formName+controlName+"\")\n"
            + ", { lineNumbers: false, mode: \""+mode+"\""
            + ", theme: \""+EDITOR_THEME+"\", matchBrackets: true, extraKeys: {\"Ctrl-Space\": \"autocomplete\"}"
            + ", viewportMargin: Infinity "+(options == null ? "" : options)+" });\n"
            + "</script>\n";				
    }

    public int countLines(String string) { return countLines(string, 80);  }
    public int countLines(String string, int lineLength) {
        if (string == null || string.isEmpty()) return 1;
        int lineBreakThreshold = lineLength/10;
        int lines = 0;
        int linel = 0;
        char[] lineArray = string.toCharArray();
        for (char c : lineArray) {
            linel++;
            if (c== '\n') {
                lines += Math.max(linel/(lineLength+lineBreakThreshold),1);
                linel = 0;
            }
        }
        return lines > 0 ? lines : 1;
    }

    public static String multiSelectList(String name, List<String> names, List<String> values, List<String> tooltips, Locale l) {
    	StringBuilder sb = new StringBuilder(1024);
    	sb.append("<SELECT id=\""+name+"\" name=\""+name+"\" size=\""+names.size()+"\" MULTIPLE>\n");
    	for(int i=0; i < names.size();i++) {
    	    sb.append("<OPTION title=\"" + tooltips.get(i) + "\" value=\""+values.get(i)+"\">"+names.get(i)+"</OPTION>\n");
    	}
    	sb.append("</SELECT>\n");
    	return sb.toString();
    }

    /** Build a list of checkboxes based on a query  */
    public static String multiCheckboxList(String name, QueryResult qr, Locale l, Set<Document> picked) {
        List<String> names = new ArrayList<>(qr.size());
        List<String> values = new ArrayList<>(qr.size());
        List<String> tooltips = new ArrayList<>(qr.size());
        List<String> checks = new ArrayList<>(qr.size());
        for(Document row : qr.get()) {
            String rid = row.getString("rid");
            if (rid == null) {
                rid = row.getIdentity().toString().substring(1);
            }
            values.add(rid);
            names.add(row.getString("name"));
            checks.add((picked != null && picked.contains(row) ? "Y" : null));
        }
        return multiCheckboxList(name, names, values, tooltips, checks, l);
    }

    public static String multiCheckboxListWithGoto(String name, String table, QueryResult qr, Locale l, Set<Document> picked) {
	    List<String> names = new ArrayList<>(qr.size());
	    List<String> values = new ArrayList<>(qr.size());
	    List<String> tooltips = new ArrayList<>(qr.size());
	    List<String> checks = new ArrayList<>(qr.size());
	    for(Document row : qr.get()) {
	    	String rid = row.getString("rid");
	    	if (rid == null) {
	    		rid = row.getIdentity().toString().substring(1);
	    	}
	    	values.add(rid);
	    	names.add(row.getString("name") + "&nbsp;&nbsp;&nbsp;"+linkNewWindow("/permeagility.web.Table"
					+"?TABLENAME="+table
					+"&EDIT_ID="+rid, Message.get(l, "GOTO_ROW")));
	    	tooltips.add(row.getString("tooltip"));
	    	checks.add((picked != null && picked.contains(row) ? "Y" : null));
	    }
	    return multiCheckboxList(name, names, values, tooltips, checks, l);
    }

    public static String multiCheckboxList(String name, List<String> names, List<String> values, List<String> tooltips, List<String> checks, Locale l) { 
    	StringBuilder sb = new StringBuilder(1024);
    	for(int i=0; i < names.size();i++) {
    	    sb.append("<input type=\"CHECKBOX\""
                +(checks.get(i)==null ? "" : " checked=\"yes"+"\"")
                +" id=\""+name+"\"" + " name=\""+name+"\""
                +" title=\""+tooltips.get(i)+"\"" + " value=\""+values.get(i)+"\">"
                +names.get(i)
                +"</input><BR>\n"
    	    );
    	}
    	return sb.toString();
    }

    public static String multiCheckboxList(String name, List<String> names, List<String> values, List<String> tooltips, Locale l) {
    	StringBuilder sb = new StringBuilder(1024);
    	for(int i=0; i < names.size();i++) {
    	    sb.append("<input type=\"CHECKBOX\" id=\""+name+"\" name=\""+name
                   + "\" title=\""+tooltips.get(i)+"\" value=\""+values.get(i)+"\">"
                   + names.get(i)
                   + "</input><BR>\n");
    	}
    	return sb.toString();
    }

    public String linkListControl(DatabaseConnection con, String name, String table, QueryResultCache qr, Locale l, List<RID> picked) {
        if (qr == null) {
            return paragraph("error","Cannot produce list for table "+table+" query is empty");
        }
        HashMap<String,Integer> listMap = new HashMap<>();  // for keeping counts of objects LinkList can have duplicates
        List<String> names = new ArrayList<>(qr.size());
        List<String> values = new ArrayList<>(qr.size());
        List<String> tooltips = new ArrayList<>(qr.size());
        List<String> checks = new ArrayList<>(qr.size());
        List<String> listnames = new ArrayList<>(qr.size());
        List<String> listvalues = new ArrayList<>(qr.size());
        List<String> listtooltips = new ArrayList<>(qr.size());
        List<String> listchecks = new ArrayList<>(qr.size());
        if (picked != null) {
            for(RID pickedRID : picked) {
                Document pick = con.get(pickedRID);
                if (pick != null) {
                    String rid = pickedRID.toString();
                    if (rid.startsWith("#")) rid = rid.substring(1);
                    listvalues.add(rid);
                    listnames.add(toJSONString(getDescriptionFromDocument(con, pick)));
                    //listtooltips.add(toJSONString(pick.getString("tooltip")));
                    listtooltips.add(toJSONString("a tooltip"));
                    Integer active = listMap.get(rid);
                    if (active == null) {
                            active = Integer.valueOf(1);
                    } else {
                            active = Integer.valueOf(active.intValue()+1);
                    }
                    //System.out.println("Adding to listValues: "+rid+" active="+active);
                    listMap.put(rid, active);	    	
                    listchecks.add(active.toString());
                }
            }
        }
        for(JSONObject row : qr.get()) {
            String rid = row.getString("rid");
            if (rid.startsWith("#")) rid = rid.substring(1);
            values.add(rid);
            names.add(toJSONString(row.getString("name")));
//            tooltips.add(toJSONString(row.getString("tooltip")));
            tooltips.add(toJSONString("a tooltip"));
            Integer active = listMap.get(rid);
            if (active == null) {
                    active = Integer.valueOf(0);
            } else {
                    active = Integer.valueOf(active.intValue());
            }
            checks.add(active.toString());
        }
        return getLinkList(name, table, names, values, tooltips, checks, listnames, listvalues, listtooltips, listchecks, l);
    }

    public String getLinkList(String name, String table, List<String> names, List<String> values, List<String> tooltips, List<String> checks, List<String> listnames, List<String> listvalues, List<String> listtooltips, List<String> listchecks, Locale l) {   
        StringBuilder result = new StringBuilder();
        StringBuilder valuesList = new StringBuilder();
        // Selected values
        result.append("<ol id=\""+name+"_list"+"\" class=\""+name+"_class"+"\" _=\"on change put '' into the innerHTML of #"+name+"_result \n"
                   + " then for i in the children of #"+name+"_list"+" put ',' + @rid of i at end of #"+name+"_result end \n"
                   + " then put the innerHTML of #"+name+"_result"+" into the value of #"+name+"\">\n");
        for (int i=0; i<listnames.size(); i++) {
                result.append("<li rid=\"" + listvalues.get(i) + "\">" + listnames.get(i)
                    + "<a title=\"delete me\" _=\"on click remove the closest <li/> then send change to #"+name+"_list"+"\">x</a></li>\n");
                valuesList.append("," + listvalues.get(i));
        }
        result.append("</ol>");
        result.append("<select id=\"" + name + "_items"+"\"\n" + 
                "  _=\"on change if #" + name + "_items"+".value is not 'null' \n" + 
                "     put '<li rid=\\'' + #" + name + "_items" + ".value + '\\'>' + #" + name + "_items"+".options[#" + name + "_items" + ".selectedIndex].innerText\n" + 
                "     + '&nbsp;<a title=\\'delete me\\' _=\\'on click remove the closest <li/> then send change to #"+name+"_list"+"\\'>x</a>'\n" + 
                "     + '</li>' at end of #" + name + "_list" + " \n" + 
                "     then put '' into the innerHTML of #"+name+"_result"+"\n" + 
                "     then for i in the children of #"+name+"_list"+" put ',' + @rid of i at end of #"+name+"_result end \n" +
                "     then put the innerHTML of #"+name+"_result"+" into the value of #"+name+"\">\n");
        // The default null option
        result.append("<option value=\"null\" selected=\"yes\">- Select an item -</option>\n");
        // The full list of possible values
        for (int i=0; i<names.size(); i++) {
                if (i > 0) { result.append(","); }
                result.append("<option value=\""+values.get(i)+"\">"+names.get(i)+"</option>\n");
        }
        result.append("</select>");
        result.append("<script>\nvar "+name+"_dragArea"+" = document.querySelector(\"."+name+"_class"+"\");\nnew Sortable("+name+"_dragArea"+", {  animation: 350 });\n</script>");
        result.append("<div style=\"display: none;\" id=\""+name+"_result"+"\"></div>"); // temp holder for result, probably could do this with vars
        result.append("<input type=\"hidden\" name=\""+name+"\"  id=\""+name+"\" value=\""+valuesList+"\"/>");  // this last input is what will be sent in the form submission

        return result.toString();
    }

    public String linkMapControl(DatabaseConnection con, String name, String table, QueryResultCache qr, Locale l, Map<String,Object> picked) {
        if (qr == null) {
                return paragraph("error","Cannot produce list for table "+table+" query is empty");
        }
        HashMap<String,Integer> listMap = new HashMap<>();  // for keeping counts of objects LinkList can have duplicates
        List<String> names = new ArrayList<>(qr.size());
        List<String> values = new ArrayList<>(qr.size());
        List<String> tooltips = new ArrayList<>(qr.size());
        List<String> checks = new ArrayList<>(qr.size());
        List<String> listmaps = new ArrayList<>(qr.size());
        List<String> listnames = new ArrayList<>(qr.size());
        List<String> listvalues = new ArrayList<>(qr.size());
        List<String> listtooltips = new ArrayList<>(qr.size());
        List<String> listchecks = new ArrayList<>(qr.size());
        if (picked != null) {
            for (String key : picked.keySet()) {
                Object po = picked.get(key);
                RID pickedRID = po instanceof RID ? (RID)po : null;
                Document pick = po instanceof Document ? (Document)po : pickedRID != null ? con.get(pickedRID) : null;
                if (pick != null) {
                    String rid = pickedRID != null ? pickedRID.toString() : pick.getIdentity().toString();
                    if (rid.startsWith("#")) rid = rid.substring(1);
                    // For some reason, map keys have single quotes around them now
                    if (key.startsWith("'") && key.endsWith("'")) {
                        System.out.println("Removing single quotes from map key");
                        key = key.substring(1,key.length() - 1);
                    }
                    listmaps.add(key);
                    listvalues.add(rid);
                    listnames.add(toJSONString(getDescriptionFromDocument(con, pick)));
                    listtooltips.add("a tooltip");
                    //listtooltips.add(toJSONString(pick.getString("tooltip")));
                    Integer active = listMap.get(rid);
                    if (active == null) {
                        active = Integer.valueOf(1);
                    } else {
                        active = Integer.valueOf(active.intValue()+1);
                    }
                    //System.out.println("Adding to listValues: "+rid+" active="+active);
                    listMap.put(rid, active);	    	
                    listchecks.add(active.toString());
                }
            }
        }
        for(JSONObject row : qr.get()) {
            String rid = row.getString("rid");
            if (rid.startsWith("#")) rid = rid.substring(1);
            values.add(rid);
            names.add(toJSONString(row.getString("name")));
            //tooltips.add(toJSONString(row.getString("tooltip")));
            tooltips.add("a tooltip");
            Integer active = listMap.get(rid);
            if (active == null) {
                    active = Integer.valueOf(0);
            } else {
                    active = Integer.valueOf(active.intValue());
            }
            checks.add(active.toString());
        }
        return getLinkMap(name, table, names, values, tooltips, checks, listmaps, listnames, listvalues, listtooltips, listchecks, l);
    }

    public String getLinkMap(String name, String table, List<String> names, List<String> values
            , List<String> tooltips, List<String> checks, List<String> listmaps, List<String> listnames
            , List<String> listvalues, List<String> listtooltips, List<String> listchecks, Locale l) {   
        StringBuilder result = new StringBuilder();
        StringBuilder valuesList = new StringBuilder();
        // Selected values
        result.append("<ol id=\""+name+"_list"+"\" class=\""+name+"_class"+"\" _=\"on change put '' into the innerHTML of #"+name+"_result \n"
                   + " then for i in the children of #"+name+"_list"+" put ',' + @rid of i at end of #"+name+"_result \n"
                   + " then put the innerHTML of #"+name+"_result"+" into the value of #"+name+"\">\n");
        for (int i=0; i<listnames.size(); i++) {
                result.append("<li rid=\"" + listvalues.get(i) + "\"><input type=\"text\" id=\""+name + "_map"+"\" name=\""+name + "_map"+"\" value=\""+listmaps.get(i)+"\"/ required>" + listnames.get(i)
                    + "<a title=\"delete me\" _=\"on click remove the closest <li/> then send change to #"+name+"_list"+"\">x</a></li>\n");
                valuesList.append("," + listvalues.get(i));
        }
        result.append("</ol>");
        result.append("<select id=\"" + name + "_items"+"\"\n" + 
                "  _=\"on change if #" + name + "_items"+".value is not 'null' \n" + 
                "     put '<li rid=\\'' + #" + name + "_items" + ".value + '\\'><input type=\\'text\\' id=\\'"+name + "_map"+"\\'  name=\\'"+name + "_map"+"\\'/ required>' + #" + 
                      name + "_items"+".options[#" + name + "_items" + ".selectedIndex].innerText\n" + 
                "     + '&nbsp;<a title=\\'delete me\\' _=\\'on click remove the closest <li/> then send change to #"+name+"_list"+"\\'>x</a>'\n" + 
                "     + '</li>' at end of #" + name + "_list" + " \n" + 
                "     then put '' into the innerHTML of #"+name+"_result"+"\n" + 
                "     then for i in the children of #"+name+"_list"+" put ',' + @rid of i at end of #"+name+"_result" + "\n" +
                "     then put the innerHTML of #"+name+"_result"+" into the value of #"+name+"\">\n");
        // The default null option
        result.append("<option value=\"null\" selected=\"yes\">- Select an item -</option>\n");
        // The full list of possible values
        for (int i=0; i<names.size(); i++) {
                if (i > 0) { result.append(","); }
                result.append("<option value=\""+values.get(i)+"\">"+names.get(i)+"</option>\n");
        }
        result.append("</select>");
        result.append("<script>\nvar "+name+"_dragArea"+" = document.querySelector(\"."+name+"_class"+"\");\nnew Sortable("+name+"_dragArea"+", {  animation: 350 });\n</script>");
        result.append("<div style=\"display: none;\" id=\""+name+"_result"+"\"></div>"); // temp holder for result, probably could do this with vars
        result.append("<input type=\"hidden\" name=\""+name+"\"  id=\""+name+"\" value=\""+valuesList+"\"/>");  // this last input is what will be sent in the form submission

        return result.toString();
    }
    
    public static String getQueryForTable(DatabaseConnection con, String table, String column) {
            QueryResultCache lists = getCache().getResult(con, "SELECT FROM "+Setup.TABLE_PICKLIST+" WHERE tablename='"+table+"."+column+"'");
            if (lists != null && lists.size()>0) {
                    return lists.getStringValue(0, "query");
            }
            lists = getCache().getResult(con, "SELECT FROM "+Setup.TABLE_PICKLIST+" WHERE tablename='"+table+"'");
            if (lists != null && lists.size()>0) {
                    return lists.getStringValue(0, "query");
            }
            return "SELECT FROM "+table+" LIMIT "+Table.ROW_COUNT_LIMIT;
    }

    public static String getQueryForTable(DatabaseConnection con, String table) {
            QueryResultCache lists = getCache().getResult(con, "SELECT FROM "+Setup.TABLE_PICKLIST+" WHERE tablename='"+table+"'");
            if (lists != null && lists.size()>0) {
                    return lists.getStringValue(0, "query");
            }
            return "SELECT FROM "+table+" LIMIT "+Table.ROW_COUNT_LIMIT;
    }

    /** Prepare a string value for insertion into a JSON document (convert \ to \\ and ' to \') */
    public static String toJSONString(String text) {
            return text==null ? "" : text.replace("\\","\\\\").replace("'","\\'");
    }

    public static String getDescriptionFromDocument(DatabaseConnection con, Document document) {
        if (document == null) {
            return NONE_STRING;
        } else if (document.has("name")) {
            String name = document.getString("name");
            if (name != null) {
                    return name;
            }
        }
        Set<String> props = document.getPropertyNames();
        if (props.size() > 0) {
            return document.getString(props.toArray()[0].toString());  // if no name, get the first property
        } else {
            return NONE_STRING;
        }
    }

    // used only by Table.getLinkTrail
    public static String getDescriptionFromTable(DatabaseConnection con, String table, String id) {
        if (id == null || id.equals("")) return NONE_STRING;
        //System.out.println(getQueryForTable(con, table));
        if (!id.startsWith("#")) id = "#"+id;
        QueryResultCache qr = getCache().getResult(con, getQueryForTable(con, table));
        if (qr != null) {
            int r = qr.findFirstRow("rid", id);
            if (r > -1) {
                    return qr.getStringValue(r, "name");
            } else {
                    return id+" not in "+table;
            }
        } else {
            return NONE_STRING;
        }
    }

    public static String createListFromTable(String name, String initial, DatabaseConnection con, String table) {
        return createListFromTable(name, initial, con, table, null, true, null, true);
    }

    public static String createListFromTable(String name, String initial, DatabaseConnection con, String table, String onChange,
                boolean allowNull, String classname, boolean enabled) {
        return createListFromCache(name, initial, con, getQueryForTable(con,table),onChange, allowNull, classname, enabled);
    }

    public static String createListFromCache(String name, String initial, DatabaseConnection con, String query) {
        return createListFromCache(name, initial, con, query, null, true, null, true);
    }

    public static String createListFromCache(String name, String initial, DatabaseConnection con, String query
                    , String attributes, boolean allowNull, String classname, boolean enabled) {
        QueryResultCache qr = queryCache.getResult(con, query);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<SELECT " + (enabled ? "" : "DISABLED") + (classname != null ? " class=\"" + classname + "\"" : "") + " id=\"" + name + "\" name=\"" + name + "\" " + (attributes != null ? attributes : "") + ">\n");
        if (initial == null && allowNull) {
            sb.append("<OPTION SELECTED=\"yes\" value=null>" + "Select" + "\n");
        } else if (allowNull) {
            sb.append("<OPTION VALUE=null>"+Message.get(con.getLocale(), "OPTION_NONE")+"\n");
        }
        if (qr != null) {
            for (JSONObject item : qr.get()) {
                String id = item.getString("rid");
                if (id.startsWith("#")) id = id.substring(1);
                String itemname = null;
                itemname = item.getString("name");
                if (itemname == null) {
                    itemname = item.getString("Name");					
                }
                if (itemname == null && qr.getColumns().length>0) {
                    itemname = item.getString(qr.getColumns()[0]);
                }
                //System.out.println("createListFromCache options initial="+initial+" id="+id+" itemname="+itemname);  // to debug pre-selected item
                sb.append("<OPTION ");
                if (initial != null && (initial.equals(id) || initial.equals(itemname))) {
                    sb.append("SELECTED=\"yes\" ");
                }
                sb.append(" VALUE=\"");
                sb.append(id);
                sb.append("\">");
                sb.append(itemname == null ? id : itemname);
                sb.append("\n");
            }
        }
        sb.append("</SELECT>\n");
        return sb.toString();
    }

    public static String createColumnList(Locale locale, String name, String initial, String attributes, boolean allowNull, String classname, boolean enabled, Collection<Property> properties) {
        ArrayList<String> names = new ArrayList<String>();
        for (Property p : properties) {
            names.add(p.getName());
        }
        return createList(locale, name, initial, names, attributes, allowNull, classname, enabled);
    }
    public static String createList(Locale locale, String name, String initial, List<String> names, String attributes, boolean allowNull, String classname, boolean enabled) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<SELECT " 
            + (enabled ? "" : "DISABLED") 
            + (classname != null ? " class=\"" + classname + "\"" : "") 
            + " id=\"" + name + "\" " 
            + " name=\"" + name + "\" " 
            + (attributes != null ? attributes : "") + ">\n");
        if (initial == null && allowNull) {
            sb.append("<OPTION SELECTED=\"yes\" value=null>" + "Select" + "\n");
        } else if (allowNull) {
            sb.append("<OPTION VALUE=null>" + Message.get(locale,"OPTION_NONE"));
        }
        if (names != null) {
            for (String item : names) {
                sb.append("<OPTION ");
                if (initial != null && item.equals(initial)) {
                    sb.append("SELECTED=\"yes\" ");
                }
                sb.append(">");
                sb.append(item);
                sb.append("</OPTION>\n");
            }
        }
        sb.append("</SELECT>\n");
        return sb.toString();
    }

    public static String selectList(Locale l, String name, String selected, List<String> names, List<String> values, String attributes, boolean allowNull, String classname, boolean enabled) {
            StringBuilder sb = new StringBuilder(1024);
            sb.append("<select " 
                            + (enabled ? "" : "DISABLED") 
                            + (classname != null ? " class=\"" + classname + "\"" : "") 
                            + " id=\"" + name + "\" " 
                            + " name=\"" + name + "\" " 
                            + (attributes != null ? attributes : "") + ">\n");
            if (allowNull) {
                    sb.append("<option value=null>" + Message.get(l,"OPTION_NONE")+"</option>");
            }
            for (int i = 0; i < names.size(); i++) {
                    if (selected != null && selected.equals(values.get(i))) {
                            sb.append("<option value=\"" + values.get(i) + "\" SELECTED=\"yes\">" + names.get(i) + "</option>\n");
                    } else {
                            sb.append("<option value=\"" + values.get(i) + "\">" + names.get(i) + "</option>\n");
                    }
            }
            sb.append("</select>\n");
            return sb.toString();
    }

    public static String getTabPanel(String name, ArrayList<String> tabNames, ArrayList<String> tabTargets) {
        StringBuilder result = new StringBuilder();
        result.append("<div id=\"tabs\" class=\"tabpanel\" hx-target=\"#"+name+"\" role=\"tablist\" "
          +" _=\"on htmx:afterOnLoad set @aria-selected of <[aria-selected=true]/> to false "
          +" tell the target take .selected set @aria-selected to true\">\n");
        for (int i = 0; i < tabNames.size(); i++) {
            result.append("<a role=\"tab\" aria-controls=\""+name+"\" aria-selected=\"false\" hx-get=\""+tabTargets.get(i)+"\">&nbsp;"+tabNames.get(i)+"&nbsp;</a>\n");
        }
        result.append("</div>\n");
        result.append("<div id=\""+name+"\" role=\"tabpanel\"></div>\n");
        return result.toString();
    }

    public static boolean isNullOrBlank(String string) {
            if (string == null) {
                    return true;
            } else if (string.trim().equals("")) {
                    return true;
            } else {
                    return false;
            }
    }

    public static boolean valueChanged(Object oldObject, Object newObject) {
            if (oldObject == null && newObject != null) {
                    // System.out.println("New Value");
                    return true;
            } else if (oldObject != null && newObject != null) {
                    // System.out.println("old="+oldObject+" new="+newObject);
                    if (oldObject instanceof Number && newObject instanceof Number) {
                            if (((Number) oldObject).doubleValue() != ((Number) newObject).doubleValue()) {
                                    // System.out.println("NumberChanged!="+newObject);
                                    return true;
                            } else {
                                    return false;
                            }
                    } else if (!oldObject.equals(newObject)) {
                            // System.out.println(oldObject.getClass().getName()+"!="+newObject.getClass().getName());
                            return true;
                    }
            }
            return false;
    }

    public static BigDecimal roundDouble(double value, int precision) {
            try {
                    BigDecimal n = new BigDecimal(value);
                    return n.setScale(precision, RoundingMode.HALF_UP);
            } catch (Exception e) {
                    return new BigDecimal(0.0);
            }
    }

    public static BigDecimal roundDouble(Number value, int precision) {
            return roundDouble(value.doubleValue(), precision);
    }

    // Localized number and date handling methods
    // ------------------------------------------

    /** Format a number based on locale and return the formatted number as a string */
    public static String formatNumber(Locale locale, Number n, String format) {
            if (locale == null || n == null) {
                    return "!"+n;
            }
            DecimalFormat numberFormat = new DecimalFormat(format);
            numberFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(locale));
            numberFormat.applyPattern(format);
            return numberFormat.format(n);
    }

    /** Format a number based on locale and return the formatted number as a string */
    public static String formatNumber(Locale locale, Number n, String format, int precision) {
            if (locale == null || n == null) {
                    return "!"+n;
            }
            DecimalFormat numberFormat = new DecimalFormat(format);
            numberFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(locale));
            numberFormat.applyPattern(format);
            return ((n.doubleValue() == 0.0 ? numberFormat.format(Double.valueOf(0.0)) : numberFormat.format(roundDouble(n.doubleValue(),precision))));
    }

    /** Format a number based on locale and return the formatted number as a string */
    public static String formatNumber(Locale locale, double n, String format) {
            if (locale == null) {
                    return "!"+n;
            }
            DecimalFormat numberFormat = new DecimalFormat(format);
            numberFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(locale));
            numberFormat.applyPattern(format);
            return numberFormat.format(n);
    }

    public static String formatNumber(Locale locale, double n, String format, int precision) {
            if (locale == null) {
                    return "!"+n;
            }
            DecimalFormat numberFormat = new DecimalFormat(format);
            numberFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(locale));
            numberFormat.applyPattern(format);
            return numberFormat.format(roundDouble(n, precision));

    }

    /** Parse a string number based on locale and return the number */
    public static Number parseNumber(Locale locale, String s) {
            DecimalFormat numberFormat = new DecimalFormat();
            if (locale != null) {
                    numberFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(locale));
            }
            Number n = null;
            if (s != null) {
                    s = s.trim();
                    try {
                            n = numberFormat.parse(s);
                    } catch (Exception e) {}
            }
            return n;
    }

    /** Format a date based on locale and return the formatted date as a string */
    public static String formatDate(Locale locale, Date dateToFormat) {
        return formatDate(locale, dateToFormat, DATE_FORMAT);
    }
    
    /** Format a date based on locale and return the formatted date as a string */
    public static String formatDatetime(Locale locale, LocalDateTime dateToFormat) {
            if (dateToFormat == null) {
                    return "";
            }
            DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern(DATE_FORMAT + " " + TIME_FORMAT);
            return dateTimeFormat.format(dateToFormat);
    }

    /** Format a date based on locale and return the formatted date as a string */
    public static String formatDate(Locale locale, Date dateToFormat, String format) {
            if (dateToFormat == null) {
                    return "";
            }
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat(format, locale);
            return dateTimeFormat.format(dateToFormat);
    }

    /** Parse a string date based on locale and return the date  */
    public static Date parseDate(Locale locale, String dayString, String format) {
        if (dayString == null) {
            return null;
        } else {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, locale);
                sdf.setLenient(false);
                return sdf.parse(dayString);
            } catch (ParseException pe) {
                System.out.println("Weblet: cannot parse date "+dayString);
                // pe.printStackTrace();
                return null;
            }
        }
    }

    /** Parse a string date based on locale and return the date  */
    public static Date parseDate(Locale locale, String dayString) {
        return parseDate(locale, dayString, DATE_FORMAT);
    }

    /** Parse a string datetime based on locale and return the date   */
    public static LocalDateTime parseLocalDatetime(Locale locale, String dtString) {
            return LocalDateTime.parse(dtString, new DateTimeFormatterBuilder().appendPattern(DATE_FORMAT+" "+TIME_FORMAT).toFormatter());
    }

    /** If an overridden version of this returns true, then the generated fields will be disabled */
    public boolean isReadOnly() {
        return false;
    }

    /** this is faster than Regex parsing */
    public String[] splitCSV(String input) {
        input = input.replace("\\u0022", "\"");
            List<String> tokensList = new ArrayList<>();
            StringBuilder b = new StringBuilder();
            int itemCount = 0;
            Stack<Character> quotes = new Stack<>(); 
            for (char c : input.toCharArray()) {
                switch (c) {
                        case ',':
                            if (!quotes.isEmpty()) {
                                b.append(c);
                            } else {
                                String tok = b.toString();
                                if (tok.startsWith("\"") && tok.endsWith("\"")) {
                                    tok = tok.substring(1,tok.length()-1);
                                }
                                tokensList.add(tok);
                                itemCount++;
                                b = new StringBuilder();
                            }
                            break;
                        case '\"': case '\'':
                            if (c == '\'' && !quotes.empty() && quotes.peek().equals('\"')) { // Ignore single quotes inside double (apostrophe in data)
                            } else if (!quotes.empty() && quotes.peek().equals(c)) {
                                    quotes.pop(); // Coming out
                            } else {
                                    quotes.push(c); // Going in
                            }
                            break;  // added to prevent fall-through (not sure though)
                        default:
                            b.append(c);
                        break;
                }
            }
            if (b.length()>0) {
                String tok = b.toString();
                if (tok.startsWith("\"") && tok.endsWith("\"")) {
                    tok = tok.substring(1,tok.length()-1);
                }
                tokensList.add(tok);
                itemCount++;
            }
            //System.out.println("splitCSV turned "+input+" into "+tokensList);
            String[] temp = new String[itemCount];
            return tokensList.toArray(temp);
    }

    public static String wrapWithQuotes(String what) {
            if (what == null || what.equals("")) {
                    return "null";
            } else {
//			return '"'+what.replace("\"", "'")+'"';
                    return "'"+what.replace("\\","\\\\").replace("'", "\\'")+"'";
            }
    }

    /** Turn a "camelCase" into "Camel Case" */
    public static String makeCamelCasePretty(String input) {
            StringBuilder cn = new StringBuilder();
            if (input.startsWith("_")) input = input.substring(1);  // Remove possible leading underscore (_allowREAD)
            char[] chars = input.toCharArray();
            for (int i=0; i<chars.length; i++) {
                    if (i == 0) {
                            cn.append(Character.toUpperCase(chars[i]));
                    } else {
                            if (Character.isUpperCase(chars[i])) {
                                    if (i > 0 && Character.isLowerCase(chars[i-1])) {
                                            cn.append(' ');
                                    }
                                    if (i + 1 < chars.length && Character.isLowerCase(chars[i+1])) {
                                            cn.append(' ');
                                    }
                            }
                            cn.append(chars[i]);
                    }
            }
            return cn.toString();
    }

    /** Turn pretty and not so pretty text into a camelCase valid identifier 
     * ie. "Camel Case" becomes "camelCase" 
     * "first name" becomes "firstName" 
     * "User ID" becomes "userId" 
     * "USER_ID" becomes "userId" (Note: if all caps, will be turned to all lowercase)
     * "543-device-reading" becomes "n543DeviceReading"  */
    public static String makePrettyCamelCase(String input) {
        StringBuilder cn = new StringBuilder();
        input = input.trim();
        if (input.startsWith("_")) input = input.substring(1);  // Remove possible leading underscore (_allowREAD)
        char[] chars = input.toCharArray();

        // If the name is all uppercase (looking at you Oracle), then lowerCase it all before processing 
        // Underlines will cause camel casing to happen
        boolean isUpperCase = true;
        for (int i=0; i<chars.length; i++) {
            if (Character.isLowerCase(chars[i])) {
                isUpperCase = false;
            }
        }
        if (isUpperCase) {
            chars = input.toLowerCase().toCharArray();
        }

        for (int i=0; i<chars.length; i++) {
            if (i == 0 && Character.isUpperCase(chars[i])) {
                if (i<chars.length && !Character.isUpperCase(chars[i+1])) { // Will allow uppercase for first if Second is also upper (acronym)
                    chars[i] = Character.toLowerCase(chars[i]);
                }
            }				
            if (i == 0 && (chars[i] >= '0' && chars[i] <= '9')) {
                cn.append('n');					
            }
            if (chars[i] == '#') {
                cn.append("Num");
            } else if (chars[i] == '&') {
                cn.append("And");
            } else if (chars[i] == '_' || chars[i] == ' ' || chars[i] == '&' || chars[i] == '\''
                || chars[i] == '\\' || chars[i] == '/' || chars[i] == '.' || chars[i] == ','
                || chars[i] == '(' || chars[i] == ')' || chars[i] == '{' || chars[i] == '}'
                || chars[i] == '@' || chars[i] == '!' || chars[i] == '?' || chars[i] == '<' 
                || chars[i] == '>' || chars[i] == '+' || chars[i] == '-' || chars[i] == ';'  || chars[i] == ':'
                || chars[i] == '[' || chars[i] == ']' || chars[i] == '$' || chars[i] == '*') {  // Don't bring these
                if (i < chars.length - 1) {  // and ensure next is uppercase as long as we are not at the end
                        chars[i+1] = Character.toUpperCase(chars[i+1]);
                }
            } else {
                cn.append(chars[i]);
            }
        }
        return cn.toString();
    }

}
