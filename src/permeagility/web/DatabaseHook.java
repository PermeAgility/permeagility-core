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

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.ODatabaseLifecycleListener;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class DatabaseHook implements ODatabaseLifecycleListener {
	
	public static boolean DEBUG = false;
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

	@Override
	public void onDrop(ODatabaseInternal arg0) {
		if (DEBUG) System.out.println("DatabaseHook:onDrop");
	}

    @Override
    public void onLocalNodeConfigurationRequest(ODocument od) {
	if (DEBUG) System.out.println("DatabaseHook:onLocalNodeConfigurationRequest: "+od);
    }
	 
}