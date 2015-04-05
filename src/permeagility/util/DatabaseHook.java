package permeagility.util;

import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.ODatabaseLifecycleListener;
import com.orientechnologies.orient.core.hook.ODocumentHookAbstract;
import com.orientechnologies.orient.core.hook.ORecordHook.DISTRIBUTED_EXECUTION_MODE;
import com.orientechnologies.orient.core.hook.ORecordHook.RESULT;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class DatabaseHook extends ODocumentHookAbstract implements ODatabaseLifecycleListener {
	
	public static boolean DEBUG = true;
	
	  public DatabaseHook() {
	    // REGISTER MYSELF AS LISTENER TO THE DATABASE LIFECYCLE
	    if (DEBUG) System.out.println("Hook:register(constructor)");
//	    Orient.instance().addDbLifecycleListener(this);
	  }

	  public void onOpen(final ODatabase iDatabase) {
		if (DEBUG) System.out.println("DatabaseHook:register(open)");
	    iDatabase.registerHook(this);
	  }

	  public void onCreate(final ODatabase iDatabase) {
		if (DEBUG) System.out.println("Hook:register(create)");
	    iDatabase.registerHook(this);
	  }

	  public void onClose(final ODatabase iDatabase) {
		if (DEBUG) System.out.println("Hook:unregister");
	    iDatabase.unregisterHook(this);
	  }
	 
	  public RESULT onRecordBeforeCreate(final ODocument iDocument) {
	    // DO SOMETHING BEFORE THE DOCUMENT IS CREATED
		  if (DEBUG) System.out.println("Hook:beforeCreate");
		return null;
	  }

	@Override
	public DISTRIBUTED_EXECUTION_MODE getDistributedExecutionMode() {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:gdem");
		return DISTRIBUTED_EXECUTION_MODE.TARGET_NODE;
	}

	@Override
	public PRIORITY getPriority() {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:gp");
		return PRIORITY.FIRST;
	}

	@Override
	public void onCreate(ODatabaseInternal iDatabase) {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:onCreate");
		
	}

	@Override
	public void onOpen(ODatabaseInternal iDatabase) {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:onOpen");		
	}

	@Override
	public void onClose(ODatabaseInternal iDatabase) {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:onClose");
		
	}

	@Override
	public void onCreateClass(ODatabaseInternal iDatabase, OClass iClass) {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:onCreateClass");		
	}

	@Override
	public void onDropClass(ODatabaseInternal iDatabase, OClass iClass) {
		// TODO Auto-generated method stub
		if (DEBUG) System.out.println("Hook:onDropClass");

	}
	  
	}