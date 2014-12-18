/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.util;

import java.io.IOException;

/**
 * A simple, static class to display a URL in the system browser.
 * Under Unix or Linux etc.. nothing will happen (assumes in Cloud)
 * Under Windows or Mac OS X, this will bring up the default browser.
 */
public class BrowserControl {
	
    /**
     * Display a file in the system browser. If you want to display a
     * file, you must include the absolute path name.
     *
     * @param url the file's url (the url must start with either "http://" or
     * "file://").
     */
    public static void displayURL(String url) {
        String os = System.getProperty("os.name");
        String cmd = null;

        System.out.println("Call to open browser on "+os+" for url "+url);
        try {
            if (os.startsWith(WIN_OS_ID)) {
                // cmd = 'rundll32 url.dll,FileProtocolHandler http://...'
                cmd = WIN_PATH + " " + WIN_FLAG + " " + url;
                Runtime.getRuntime().exec(cmd);
            } else if (os.startsWith(MAC_OS_ID)) {
            	cmd = "open "+url;
                Runtime.getRuntime().exec(cmd);            	
            } else {
            	System.out.println("Not configured to open a browser in this operating system");
            }
        }
        catch(IOException x) {
            System.err.println("Could not invoke browser, command=" + cmd);
            System.err.println("Caught: " + x);
        }
    }

    /** Simple example. */
    public static void main(String[] args) {
        displayURL("http://www.google.com");
    }

    // Used to identify the platform.
    private static String WIN_OS_ID = "Windows";
    private static String MAC_OS_ID = "Mac OS X";

    // The default system browser under windows.
    private static String WIN_PATH = "rundll32";

    // The flag to display a url.
    private static String WIN_FLAG = "url.dll,FileProtocolHandler";
}
