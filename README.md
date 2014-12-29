permeagility-core
=================

PermeAgility is a lightweight integrated data manager and web application framework

Try out the live demo site hosted on Amazon's Cloud: http://demo.permeagility.com

This is the source code. To build from source you must have Java 1.7+ and maven installed.
Then type: mvn install

Libraries will be downloaded, java files compiled, selftest will run, and a deployment jar 
file will be created in the target directory.  Deploy the jar file to a directory 
where you want the server to run in and double click it or type: java -jar permeagility-<version>.jar

An eclipse project is also configured, just import the project using eclipse.  
Main class is: permeagility.web.Server

Server Arguments: [port] [db]
port default is 1999
db default is plocal:db   use remote:<host>/<db> for remote databases

Once the server is running, open browser to http://localhost:1999 
(browser will open automatically on Windows or OSX)

A log directory will be created unless a log file exists, otherwise output to console.  

Login to the server using admin/admin, writer/writer, or reader/reader


Copyright 2014 PermeAgility

Licensed under Eclipse Public License http://www.eclipse.org/legal/epl-v10.html


Includes other components and copyrights: (Database and JavaScript Components)

- OrientDB - http://www.orientechnologies.com/orientdb/
- JQuery - http://jquery.org/license
- AngularJs - Google, Inc. http://angularjs.org  License: MIT
- SortTable - Stuart Langridge, http://www.kryogenix.org/code/browser/sorttable/
- JSCalendar - Mihai Bazon, http://www.dynarch.com/projects/calendar
- JSColor - Jan Odvarko, http://odvarko.cz, http://jscolor.com
