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

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;

import permeagility.util.DatabaseConnection;
import permeagility.util.QueryResult;
import permeagility.util.Setup;

public class Thumbnail {

	public static boolean DEBUG = false;
	
	public static int THUMBNAIL_SMALL_WIDTH = 50;
	public static int THUMBNAIL_SMALL_HEIGHT = 50;
	public static int THUMBNAIL_MEDIUM_WIDTH = 640;
	public static int THUMBNAIL_MEDIUM_HEIGHT = 480;
	
	public static String getThumbnailLink(Locale locale, String rid, String description) {
		if (description.startsWith("image")) {
			return "<IMG SRC=\"../thumbnail?SIZE=SMALL&ID=" + rid + "\" >\n"
					+Weblet.popupHTMX("image_"+rid.replace(':','_'), Message.get(locale,"IMAGE_VIEW_LINK") , "IMAGE_POPUP",
						Weblet.paragraph("banner",Message.get(locale,"IMAGE_VIEW_HEADER"))+description
						+"<a href=\"/thumbnail?SIZE=FULL&ID="+rid+"\" target=\"_blank\">"+Message.get(locale,"DOWNLOAD_FULL_SIZE")+"</a><br>"
						+ "<IMG SRC=\"../thumbnail?SIZE=MEDIUM&ID="+rid+"\"/>\n"
					);
		} else {
			return "<A href=\"/thumbnail?SIZE=FULL&ID="+rid+"\" title=\""+description+"\">"+Message.get(locale,"DOWNLOAD_FILE")+"</A>";			
		}
	}

	public static String getThumbnailAsCell(Locale locale, String rid, String description) {
		if (description.startsWith("image")) {
			return "<IMG SRC=\"../thumbnail?SIZE=SMALL&ID=" + rid + "\" >\n";
		} else {
			return "<A href=\"/thumbnail?SIZE=FULL&ID="+rid+"\" title=\""+description+"\">"+Message.get(locale,"DOWNLOAD_FILE")+"</A>";			
		}
	}


	public static String getThumbnailId(DatabaseConnection con, String table, String rid, String column, StringBuilder desc) {
		if (table.equals("thumbnail")) return rid;
		if (rid.startsWith("#")) rid = rid.substring(1);
		try {
			String query = "SELECT FROM thumbnail\n"
					+"WHERE id = '"+rid+"'\n"
					+"AND column = '"+column+"'";
			QueryResult qr = con.query(query);
			if (qr.size()>0) {
				Document doc = qr.get(0);
				String type = doc.getString("type");
				if (type.startsWith("image")) {
					desc.append(type +" "+ doc.getString("name")+" "+doc.get("size")+" bytes "+doc.get("width")+"x"+doc.get("height"));
				} else {
					desc.append(type +" "+ doc.getString("name")+" "+doc.getString("size")+" bytes");
				}
				return doc.getIdentity().toString().substring(1);
			} else {
				if (DEBUG) System.out.println("No thumbnail found for table="+table+" rid="+rid+" column="+column+" query="+query);
				//return createThumbnail(con, table, con.get("#"+rid), column);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] getThumbnail(DatabaseConnection con, String rid, String size, StringBuilder type, StringBuilder file) {
		if (rid == null || rid.equals("null")) {
			return null;
		}
		try {
			Document thumbnail = con.get(rid);
			if (thumbnail == null) {
				return null;
			}
			String column = null;
			if (size != null && size.equalsIgnoreCase("full")) {
				Document full = con.get("#"+thumbnail.getString("id"));
				if (full != null) {
					if (DEBUG) System.out.println("Going full size! - "+rid);
					column = thumbnail.getString("column");
					thumbnail = full;
				}
			}
			if (column == null) {
				if (size != null && size.equalsIgnoreCase("medium")) {
					column = "medium";
				} else {
					column = "small";
				}
			}
			if (DEBUG) System.out.println("Thumbnail: reading from column "+column);
			byte[] bytes = thumbnail.getBinary(column);
			if (bytes != null && bytes.length > 0) {
				ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
				if (size != null && size.equalsIgnoreCase("full")) {
					StringBuilder content_type = new StringBuilder();
					if (bis.available() > 0) {
						int binc = bis.read();
						do {
							content_type.append((char)binc);
							binc = bis.read();
						} while (binc != 0x00 && bis.available() > 0);
					}
					StringBuilder content_filename = new StringBuilder();
					if (bis.available() > 0) {
						int binc = bis.read();
						do {
							content_filename.append((char)binc);
							binc = bis.read();
						} while (binc != 0x00 && bis.available() > 0);
					}
					type.append(content_type);
					file.append(content_filename);
				} else {
					String t = thumbnail.getString("type");
					if (!t.contains("svg")) {
						t = "image/jpeg";
					}
					type.append(t);
					file.append(thumbnail.getString("name"));
				}
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				if (DEBUG) System.out.print("Reading blob content: available="+bis.available());
				int avail;
				int binc;
				while ((binc = bis.read()) != -1 && (avail = bis.available()) > 0) {
					content.write(binc);
					byte[] buf = new byte[avail];
					int br = bis.read(buf);
					if (br > 0) {
						content.write(buf,0,br);
					}
				}
				if (content.size() > 0) {
					return content.toByteArray();
				}
			} else {
				System.out.println("Thumbnail: Image is empty");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] getContent(DatabaseConnection con, String rid, String column, StringBuilder type, StringBuilder file) {
		try {
			Document record = con.get(rid);
			if (record == null)	return null;

			if (DEBUG) System.out.println("Thumbnail: reading from column "+column);
			byte[] bytes = record.getBinary(column);
			if (bytes != null) {
				ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
				StringBuilder content_type = new StringBuilder();
				if (bis.available() > 0) {
					int binc = bis.read();
					do {
						content_type.append((char)binc);
						binc = bis.read();
					} while (binc != 0x00 && bis.available() > 0);
				}
				StringBuilder content_filename = new StringBuilder();
				if (bis.available() > 0) {
					int binc = bis.read();
					do {
						content_filename.append((char)binc);
						binc = bis.read();
					} while (binc != 0x00 && bis.available() > 0);
				}
				type.append(content_type);
				file.append(content_filename);
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				if (DEBUG) System.out.print("Reading blob content: available="+bis.available());
				int avail;
				int binc;
				while ((binc = bis.read()) != -1 && (avail = bis.available()) > 0) {
					content.write(binc);
					byte[] buf = new byte[avail];
					int br = bis.read(buf);
					if (br > 0) {
						content.write(buf,0,br);
					}
				}
				if (content.size() > 0) {
					return content.toByteArray();
				}
			} else {
				System.out.println("Thumbnail: Image is empty");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	
	public static String createThumbnail(DatabaseConnection con, String table, Document doc, String column) {

		if (DEBUG) System.out.println("Thumbnail: creating thumbnail for "+table+" column="+column+" id="+doc.getIdentity().toString());
		try {
			// Delete any and all that may already exist
			String query = "SELECT FROM "+Setup.TABLE_THUMBNAIL+"\n"
					+"WHERE id = '"+doc.getIdentity().toString().substring(1)+"'\n"
					+"AND column = '"+column+"'";
			QueryResult qr = con.query(query);
			if (qr.size()>0) {
				for (Document d : qr.get()) {
					d.delete();
				}
			}
			byte[] bytes = doc.getBinary(column);
			
			StringBuilder content_type = new StringBuilder();
			StringBuilder content_filename = new StringBuilder();
			
			if (bytes == null || bytes.length == 0) { 
				if (DEBUG) System.out.println("No content for thumbnail for table="+table+" column="+column+" rid="+doc.getIdentity().toString());
				return null;
			}
			if (DEBUG) System.out.println("bytes length="+bytes.length);
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			if (bis.available() > 0) {
				int binc = bis.read();
				do {
					content_type.append((char)binc);
					binc = bis.read();
				} while (binc != 0x00 && bis.available() > 0);
			}
			if (bis.available() > 0) {
				int binc = bis.read();
				do {
					content_filename.append((char)binc);
					binc = bis.read();
				} while (binc != 0x00 && bis.available() > 0);
			}
			
			ByteArrayOutputStream content = null;
			content = new ByteArrayOutputStream();
			if (DEBUG) System.out.println("Reading blob content: available="+bis.available()+" type="+content_type);
			int avail;
			int binc;
			while ((binc = bis.read()) != -1 && (avail = bis.available()) > 0) {
				content.write(binc);
				byte[] buf = new byte[avail];
				int br = bis.read(buf);
				if (br > 0) {
					content.write(buf,0,br);
				}
			} 

			MutableDocument thumbnail = con.create(Setup.TABLE_THUMBNAIL);

			if (content != null) {
				if (content_type.toString().equals("image/svg+xml")) {  // SVG scales to any size and doesn't take up much space
					if (DEBUG) System.out.println("encoding same svg straight into thumbnails");
					thumbnail.set("small",content.toByteArray());
					thumbnail.set("medium",content.toByteArray());					
				} else if (content_type.toString().startsWith("image")) {
					if (DEBUG) System.out.println(" -> Encoding "+content.size()+" bytes of image envHeadless="+GraphicsEnvironment.isHeadless());
					// Now scale it down into two sizes
					ImageIcon imagefull = new ImageIcon(content.toByteArray());
					if (DEBUG) System.out.println(" -> Image full read as "+imagefull.toString());
					int width = imagefull.getIconWidth();
					int height = imagefull.getIconHeight();
					int gheight = (int)((double)THUMBNAIL_SMALL_WIDTH/(double)width*(double)height);
					int g2height = (int)((double)THUMBNAIL_MEDIUM_WIDTH/(double)width*(double)height);
					if (DEBUG) System.out.println(" -> width="+width+" height="+height+" gheight="+gheight+" g2height="+g2height);
					BufferedImage imagesmall = new BufferedImage(THUMBNAIL_SMALL_WIDTH,gheight,BufferedImage.TYPE_INT_RGB);
					BufferedImage imagemedium = new BufferedImage(THUMBNAIL_MEDIUM_WIDTH,g2height,BufferedImage.TYPE_INT_RGB);
					if (DEBUG) System.out.println(" -> ismall="+imagesmall.toString()+" imedium="+imagemedium);
					Graphics2D g = (Graphics2D)imagesmall.getGraphics();
					Graphics2D g2 = (Graphics2D)imagemedium.getGraphics();
					if (DEBUG) System.out.println(" -> gsmall="+g.toString()+" gmedium="+g2.toString());
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					g.drawImage(imagefull.getImage(), 0, 0, THUMBNAIL_SMALL_WIDTH, gheight, null);
					g2.drawImage(imagefull.getImage(), 0, 0, THUMBNAIL_MEDIUM_WIDTH, g2height, null);
					if (DEBUG) System.out.println(" -> gsmall="+g.toString()+" gmedium="+g2.toString());
					ByteArrayOutputStream tos = new ByteArrayOutputStream();
					ByteArrayOutputStream tos2 = new ByteArrayOutputStream();
					ImageIO.write(imagesmall, "jpg", tos);
					ImageIO.write(imagemedium, "jpg", tos2);
					tos.close();
					tos2.close();
					g.dispose();
					g2.dispose();
					thumbnail.set("width",width);
					thumbnail.set("height",height);
					thumbnail.set("small", tos.toByteArray());
					thumbnail.set("medium", tos2.toByteArray());
					if (DEBUG) System.out.println(" -> Encoded "+content.size()+" bytes into "+tos.size()+" and "+tos2.size());
				} else {
					if (DEBUG) System.out.println("Thumbnail: not an image - no encoding");
				}
				thumbnail.set("size",content.size());
			}			
			thumbnail.set("table",table);
			thumbnail.set("id",doc.getIdentity().toString().substring(1));
			thumbnail.set("column",column);
			thumbnail.set("type",content_type);
			thumbnail.set("name",content_filename);
			thumbnail.save();
			return thumbnail.getIdentity().toString().substring(1);
		
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
					
		}

		return null;
	}

	public static String deleteThumbnail(DatabaseConnection con, String table, String id) {

		if (DEBUG) System.out.println("Thumbnail: deleting thumbnail for "+table+" id="+id);
		try {
			// Delete any and all that may already exist for this row
			String query = "SELECT FROM "+Setup.TABLE_THUMBNAIL+"\n"
					+"WHERE table = '"+table+"'\n"
					+"AND id = '"+id+"'";
			QueryResult qr = con.query(query);
			if (qr.size()>0) {
				for (Document d : qr.get()) {
					d.delete();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	
}
