package permeagility.web;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.ODatabaseLifecycleListener;
import com.orientechnologies.orient.core.metadata.schema.OClass;

public class DatabaseHook implements ODatabaseLifecycleListener {
	
	public static boolean DEBUG = true;
	private static RecordHook hook = new RecordHook();
	
	public DatabaseHook() {
	    if (DEBUG) System.out.println("DatabaseHook:constructor - adding myself as a DbLifeCycleListener");
		Orient.instance().addDbLifecycleListener(this);
	}

	@Override
	public PRIORITY getPriority() {
		if (DEBUG) System.out.println("DatabaseHook:gp");
		return PRIORITY.REGULAR;
	}

	@Override
	public void onCreate(ODatabaseInternal iDatabase) {
		if (DEBUG) System.out.println("DatabaseHook:onCreate");
	    iDatabase.registerHook(hook);		
	}

	@Override
	public void onOpen(ODatabaseInternal iDatabase) {
		if (DEBUG) System.out.println("DatabaseHook:onOpen");		
	    iDatabase.registerHook(hook);
	}

	@Override
	public void onClose(ODatabaseInternal iDatabase) {
		if (DEBUG) System.out.println("DatabaseHook:onClose");
	    //iDatabase.unregisterHook(hook);		
	}

	@Override
	public void onCreateClass(ODatabaseInternal iDatabase, OClass iClass) {
		if (DEBUG) System.out.println("DatabaseHook:onCreateClass");		
	}

	@Override
	public void onDropClass(ODatabaseInternal iDatabase, OClass iClass) {
		if (DEBUG) System.out.println("DatabaseHook:onDropClass");
	}
	 
}