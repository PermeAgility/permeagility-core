permeagility-core
=================

PermeAgility is a lightweight integrated data manager and web application framework

Try out the live demo site hosted on Amazon's Cloud: http://demo.permeagility.com

This is the source code. Binary releases are available at: http://sourceforge.net/projects/permeagility/files  
To build from source you must have Java 1.8+ and maven installed.  Then type: 

    mvn install

Libraries will be downloaded, java files compiled, selftest will run, and a deployment jar 
file will be created in the target directory.  Deploy the jar file to a directory 
where you want the server to run in and double click it or type: 

    java -jar permeagility-_version_.jar

An eclipse project is also configured, just import the project as a maven project using eclipse.  
Main class is: <b>permeagility.web.Server</b>

Server Arguments: <b>[port] [db] [-selftest]</b>

Argument | Description
-------- | -----------
port | default is 1999
db | default is <b>plocal:db</b> use <b>remote:_host_/_db_</b> for non-embedded db
-selftest | this will create a testdb, open it, set it up, initialize the server, and exit

Once the server is running, open browser to http://localhost:1999 
(browser will open automatically on Windows or OSX)

A log directory will be created unless a log file exists, otherwise output to console.  

Login to the server using admin/admin, writer/writer, or reader/reader
<br>
<br>
<br>
&copy; 2015 PermeAgility Incorporated

Licensed under Eclipse Public License http://www.eclipse.org/legal/epl-v10.html


Includes other components and copyrights: (Database and JavaScript Components)

OrientDB - http://www.orientechnologies.com/orientdb/ (version 2.1)  License: Apache2
JQuery - http://jquery.org/license (version 2.1.3)  License: MIT
AngularJs - Google, Inc. http://angularjs.org (version 1.3.8)  License: MIT
D3.js - Mike Bostock, http://d3js.org (version 3.5)  License: BSD
SortTable - Stuart Langridge, http://www.kryogenix.org/code/browser/sorttable/
JSCalendar - Mihai Bazon, http://www.dynarch.com/projects/calendar
JSColor - Jan Odvarko, http://odvarko.cz, http://jscolor.com
CodeMirror - http://codemirror.net (version 5.2.1) License: MIT