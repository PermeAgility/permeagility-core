/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.web;

import java.util.HashMap;

import permeagility.util.DatabaseConnection;

public class WebletExample extends Weblet {
	public String getPage(DatabaseConnection con, HashMap<String,String> parms) {
		
		return
			head("Weblet Example")+
			body(
				//image("logo.gif") +
				paragraph(large("Weblet Example")+br()+xxSmall("Written by Glenn Irwin"))+
				form(
					input("TEXT1",parms.get("TEXT1"))+
					br()+checkbox("CHK1", parms.get("CHK1")!=null && parms.get("CHK1").equals("on"))+
					br()+textArea("PARM1",parms.get("PARM1"))+
					br()+submitButton("Execute")
				) +
				line()+
				paragraph(large((String)parms.get("PARM1"))) +
				"<FORM ACTION=\"org.permeagility.common.web.MessagesUpload\" METHOD=POST ENCTYPE=\"multipart/form-data\">"+
				paragraph("banner","UPLOAD_A_PROPERTIES_FILE")+
				table("layout",
					row(
						columnRight(50,"LOCALE")+
						column(50,"<INPUT TYPE=TEXT NAME=UPLOAD_LOCALE>")
					)+
					row(
						columnRight(50,"FILE_NAME")+
						column(50,"<INPUT TYPE=FILE NAME=FILE1>")
					)+
					row (
						columnSpan(2,submitButton())
					)
				)+
				"</FORM>"+

				table(
					row(
						column(20,large("Row1Col1"))+
						column(20,"Row1Col2")+
						column(20,"Row1Col3")+
						column(40,
							table(
								row(
									column(50,"Part1")+
									column(50,bold("Part2"))
								) +
								row(
									column(50,"Part1")+
									column(50,bold("Part2"))
								)
							)
						)
					) +
					row(
						column(20,"Row2Col1")+
						column(20,"Row2Col2")+
						column(20,"Row2Col3")+
						column(40,"Row2Col4")
					)
				) +
				table(getRows())
			);
	}

	public String getRows() {
		StringBuffer sb = new StringBuffer();
		for(int i=0;i<50;i++) {
			sb.append(row(getRow(i)));
		}
		return sb.toString();
	}
	
	public String getRow(int i) {
		StringBuffer sb = new StringBuffer();
		for(int j=0;j<5;j++) {
			sb.append(column(20,"Col"+i+","+j));
		}
		return sb.toString();
	}
	
}
