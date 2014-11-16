/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;

import permeagility.util.DatabaseConnection;

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.tool.ODatabaseExport;

public class BackupRestore extends Weblet {

    public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		String service = Message.get(con.getLocale(),"BACKUP_AND_RESTORE");
		parms.put("SERVICE",service);
		StringBuffer errors = new StringBuffer();
		
		File backupDir = new File("backup");
		if (backupDir == null || !backupDir.exists()) {
			errors.append(paragraph("error","Could not open backup directory - creating one"));
			backupDir.mkdir();
		} else {
			if (!backupDir.isDirectory()) {
				boolean success = backupDir.mkdir();
				if (success) {
					errors.append(paragraph("warning","Backup directory was created"));
				} else {
					errors.append(paragraph("error","Backup directory could not be created - cannot perform backups"));					
				}
			}
		}
		
		if (parms.get("RESTORE") != null && parms.get("RESTORE_CANCEL") == null) {
			if (parms.get("RESTORE_CONFIRM") == null || !parms.get("RESTORE_CONFIRM").equals("YES")) {
		    	return head(service)+
			    standardLayout(con, parms,
			    	errors
		    		+paragraph("banner",Message.get(con.getLocale(), "CONFIRM_RESTORE"))
		    		+form(
		    			hidden("RESTORE",parms.get("RESTORE"))
		    			+paragraph("Restoring a backup will logout and lockout all users,<br> restore the database and restart the system.<br> Data that is currently in the database may be lost.<br> Please confirm this action")
		    			+button("RESTORE_CONFIRM","YES",Message.get(con.getLocale(),"CONFIRM_RESTORE"))
		    			+button("RESTORE_CANCEL","YES",Message.get(con.getLocale(),"CANCEL"))
			        )
			    );
			} else {
				System.out.println("Restoring the database from file "+parms.get("RESTORE"));
				Server.restore_lockout = true;
				Server.restore_file = new File("backup/"+parms.get("RESTORE"));
				System.out.println("Shutting down the database");
				try {
					//Server.shutdownDatabase();
				} catch (Exception e) {
					e.printStackTrace();
				}
				Thread restore_thread = new Thread() {
					public void run() {
						System.out.println("Restore thread started... waiting 5 seconds for files to be released...");
						// Delete the database files in the data directory
						try {
							// Wait 5 seconds for requests to clear
							sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						File dbdata = new File("db");
						if (dbdata.isDirectory()) {
							boolean deletesuccess = true;
							for (File c : dbdata.listFiles()) {
								try {
									if (!deleteFile(c)) {
										deletesuccess = false;
									}
								} catch (Exception e) {
									e.printStackTrace();
									deletesuccess = false;
								}
							}
							if (!deletesuccess) {
								System.out.println("Error deleting old database before restore - exiting");
								System.exit(-1);
							}
						} else {
							System.out.println("The data directory is not a directory - aborting restore - sorry");
							System.exit(-1);
						}
						//Server.startDatabase();
						Server.initializeServer();
						Server.restore_lockout = false;
						System.out.println("Database has been restored");
					}
				};
				restore_thread.start();
				return head("Redirect")
				+ bodyOnLoad("Redirecting...", "window.location.href='/';");

			}
		}
		
		if (parms.get("SUBMIT") != null && parms.get("SUBMIT").equals(Message.get(con.getLocale(),"BACKUP_NOW"))) {
			String backupName = parms.get("BACKUP_FILENAME");
			System.out.println("Creating backup of database to file "+backupName);
			if (backupName.equals("")) {
				errors.append(paragraph("error","Backup filename not specified"));
			} else {
				try {
					ODatabaseExport exp = new ODatabaseExport(con.getDb(), "backup/"+backupName, new OCommandOutputListener() {
						public void onMessage(String iText) {
							System.out.println("Export Message: "+iText);
						}});
					exp.exportDatabase();
					exp.close();
					errors.append(paragraph("success","The database was successfully backed up to "+backupName));
				} catch (IOException e1) {
					e1.printStackTrace();
					errors.append(paragraph("error","Error performing backup: "+e1.getLocalizedMessage()));
				}
			}
		}
		StringBuffer restorePoints = new StringBuffer();
		File[] backupFiles = backupDir.listFiles();
		if (backupFiles != null) {
			for (int i=0; i<backupFiles.length; i++) {
				if (backupFiles[i].getName().endsWith(".gz") || backupFiles[i].getName().endsWith(".json")) {
					String fileSizeString;
					DecimalFormat sizeFormat = new DecimalFormat("#0.0");
					long fileSize = backupFiles[i].length();
					if (fileSize/1024 < 1024) {
						fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0)+"KB";
					} else if (fileSize/1024/1024 < 1024) {
						fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0/1024.0)+"MB";		
					} else {
						fileSizeString = ""+sizeFormat.format((double)fileSize/1024.0/1024.0/1024.0)+"GB";							
					}
					restorePoints.append(row(
								column(10,backupFiles[i].getName())
								+column(3,fileSizeString)
								+column(10,""+(new Date(backupFiles[i].lastModified())))
								+column(10,"Unavailable - not implemented" /* button("RESTORE",backupFiles[i].getName(),Message.get(con.getLocale(), "RESTORE_FROM_THIS_BACKUP") */)
					));
				}
			}
		}
		String backupFilename = "Backup_"+formatDate(con.getLocale(),new Date(),"yyyy-MM-dd_HH-mm");
    	return head(service,getSortTableScript())+
	    standardLayout(con, parms,
	    	errors
    		+paragraph("banner",Message.get(con.getLocale(), "BACKUP_THE_DATABASE"))
    		+form(
	    		table("layout",
	    			row(
	       				columnRight(20,Message.get(con.getLocale(), "BACKUP_FILENAME"))+column(40,input("BACKUP_FILENAME",backupFilename,40))
		   				)
	    			+row(
	        			columnRight(20,"")+column(40,submitButton(Message.get(con.getLocale(),"BACKUP_NOW")))
	        		)
	        	)
	        )
	    	+paragraph("banner",Message.get(con.getLocale(), "RESTORE_THE_DATABASE"))
	    	+form(
	    		table("sortable", 
	    			tableHead(column(10,"Filename")+column(3,"Size")+column(10,"Date")+column(10,"Restore"))
	    			+restorePoints.toString())
	    	)
    	);
    }

    private boolean deleteFile(File f) throws IOException {
    	if (f.isDirectory()) {
    		for (File c : f.listFiles()) {
    			deleteFile(c);
    		}
    		System.out.println("Deleting directory "+f);
    		return f.delete();
    	} else {
        	System.out.println("Deleting file "+f);
    		return f.delete();
    	}
    }
}


