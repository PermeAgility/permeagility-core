/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;

/** Extend and implement the abstract methods to allow download of something dynamic to the user (like a chart) */
public abstract class Download {

   public byte[] doPage(DatabaseConnection con, HashMap<String,String> parms) {
	   return getFile(con,parms);
   }

   public abstract String getContentType();
   public abstract String getContentDisposition();
   
   public abstract byte[] getFile(DatabaseConnection con, HashMap<String,String> parms);
	
}
