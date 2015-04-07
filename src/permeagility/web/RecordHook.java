package permeagility.web;

import java.util.Date;

import permeagility.util.DatabaseConnection;

import com.orientechnologies.orient.core.db.ODatabase.STATUS;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.impl.ODocument;

/**
 * Implements a hook that is called for all database updates
 * 
 * Currently only the Audit Trail functionality is implemented
 * Future versions will support configuring the running of a function when a change happens
 * 
 * The changes will all be logged in the table auditTrail
 * @author glenn
 *
 */
public class RecordHook implements ORecordHook {

	public static boolean DEBUG = true;

	// Set these to enable an audit trail of all reads and/or changes to the database
	public static boolean AUDIT_READS = false;  // This gets crazy
	public static boolean AUDIT_WRITES = false;
	
	private static DatabaseConnection con = null;  // Don't connect until we have to
	
	public RecordHook() {
	}

	@Override
	public void onUnregister() {
		System.out.println("Unregistering the RecordHook");
	}

	@Override
	public RESULT onTrigger(TYPE iType, ORecord iRecord) {
	    if (ODatabaseRecordThreadLocal.INSTANCE.isDefined() && ODatabaseRecordThreadLocal.INSTANCE.get().getStatus() != STATUS.OPEN) return null;  // Not sure I need this - found in an example
		boolean typeRead = (iType == TYPE.BEFORE_READ || iType == TYPE.AFTER_READ);
		if ((typeRead && AUDIT_READS) || (!typeRead && AUDIT_WRITES)) {
		    if (iRecord instanceof ODocument) {
		    	final ODocument document = (ODocument) iRecord;
		    	String className = document.getClassName();
		    	if (className != null && !className.equalsIgnoreCase("auditTrail")) {
					if (DEBUG) System.out.println("RecordHook:onTrigger type="+iType.name()+" table="+className+" record="+iRecord.toJSON());
					try {
						if (con == null) {
							con = Server.getDatabase().getConnection();
						}
						if (con != null) {
							ODocument log = con.create("auditTrail");
							log.field("timestamp", new Date())
								.field("action", iType.toString())
								.field("table", document.getClassName())
								.field("rid", iRecord.getIdentity().toString().substring(1))
								.field("recordVersion", iRecord.getRecordVersion().getCounter())
								.field("detail", iRecord.toJSON())
								.save();
							con.getDb().getLocalCache().invalidate();  // Don't cache these but hold on to the connection
						}
					} catch (Exception e) {
						e.printStackTrace();
					}		    		
		    	}
		    }
		}
		return RESULT.RECORD_NOT_CHANGED;
	}

	@Override
	public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
		if (DEBUG) System.out.println("Hook:gdem");
		return DISTRIBUTED_EXECUTION_MODE.SOURCE_NODE;
	}

}
