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

import java.util.Date;

import permeagility.util.DatabaseConnection;

import com.orientechnologies.orient.core.db.ODatabase.STATUS;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.core.metadata.schema.OType;
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

    public static boolean DEBUG = false;

    // Set these to enable an audit trail of all reads and/or changes to the database
    public static boolean AUDIT_READS = false;  // This gets crazy
    public static boolean AUDIT_WRITES = false;
    public static boolean FINALIZE_ONLY = true;  // for writes, otherwise saves three records

    private static DatabaseConnection con = null;  // Don't connect until we have to (holds the connection)

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
        boolean typeDelete = iType == TYPE.BEFORE_DELETE;
        boolean typeFail = (iType == TYPE.CREATE_FAILED || iType == TYPE.DELETE_FAILED || iType == TYPE.UPDATE_FAILED || iType == TYPE.READ_FAILED);
        // Filter out the before and after create and update
        if (!typeRead && !typeDelete && !typeFail && FINALIZE_ONLY && !iType.name().startsWith("FINALIZE")) {
            return null;
        }
        if (typeFail || (typeRead && AUDIT_READS) || (!typeRead && AUDIT_WRITES)) {
            if (iRecord instanceof ODocument) {
                final ODocument document = (ODocument) iRecord;
                String className = document.getClassName();
                String user = ODatabaseRecordThreadLocal.INSTANCE.get().getUser().getName();
                if (className != null && !className.equalsIgnoreCase("auditTrail")) {
                    if (DEBUG) System.out.println("RecordHook:onTrigger type="+iType.name()+" table="+className+" record="+iRecord.toJSON());
                    try {
                        if (con == null) {
                            con = Server.getServerConnection();
                        }
                        if (con != null) {
                            ODocument log = con.create("auditTrail");
                            log.field("timestamp", new Date())
                                .field("action", iType.toString())
                                .field("table", document.getClassName())
                                .field("rid", document.getIdentity().toString().substring(1))
                                .field("user", user)
                                .field("recordVersion", document.getRecordVersion().getCounter());
                            try {
                                StringBuilder details = new StringBuilder();
                                for (String n : document.fieldNames()) {
                                    if (document.fieldType(n) == OType.CUSTOM) {
                                    System.out.println("REMOVING Name="+n+" Type="+document.fieldType(n));
                                        document.removeField(n);
                                    }
                                }
                                log.field("detail", document.toJSON());
                            } catch(Exception e) {
                                System.out.println("AuditLog error: cannot save detail for document "+document.getIdentity().toString());
                            }
                            log.save();
                            //con.getDb().getLocalCache().invalidate();  // Don't cache these but hold on to the connection
                            DatabaseConnection.rowCountChanged("auditTrail");  // This should clear the rowcount for the auditTrail from the cache
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
        return DISTRIBUTED_EXECUTION_MODE.BOTH;
    }

}
