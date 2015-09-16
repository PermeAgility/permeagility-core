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
package permeagility.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class extends the URLClassLoader to load jars named 'plus-*' from the plus directory
 * each 'plus' jar may have a ClassPath set to load additional libraries (not named plus-)
 * the additional libraries must be in the root of the plus directory to ensure no conflicts in VM space
 */
public class PlusClassLoader extends URLClassLoader {
	
	public static final String PLUS_DIRECTORY = "plus";
	public static final String PLUS_PREFIX = "plus-";
	
	private static ClassLoader instance = null;  // There can only be one and it can only be loaded once
	
	private static ArrayList<String> modules = new ArrayList<>();
	private static String builtins[] = { "plus-translate", "plus-merge", "plus-json", "plus-csv", "plus-d3", "plus-r" }; 
	
	public PlusClassLoader(URL[] urls) {
		super(urls);
	}

	public static ClassLoader get() {
		if (instance != null) {
			return instance;
		}
		for (String m : builtins) {  // add builtins to the module list
			modules.add(m);			
		}
		ArrayList<URL> urls = new ArrayList<>();
		File d = new File(PLUS_DIRECTORY);
		if (!d.exists()) {
			d.mkdir();
		}
		if (d.isDirectory()) {
			for (File f : d.listFiles()) {
				if (f.getName().startsWith(PLUS_PREFIX) && f.getName().endsWith(".jar")) {
					System.out.println("Loading plus module "+f.getName());
					try {
						urls.add(f.toURI().toURL());
						modules.add(f.getName().substring(0, f.getName().length()-4));
					} catch (MalformedURLException e) {
						System.out.println("Cannot make URL plus module "+f.getName()+": "+e.getMessage());
					}
				}
			}
			URL[] urlArray = new URL[urls.size()];
			int i = 0;
			for (URL u : urls) {
				urlArray[i++] = u;
			}
			instance = new PlusClassLoader(urlArray);
		} else {
			System.out.println("Exit condition: "+PLUS_DIRECTORY+" is not a directory - no plus modules loaded");
			instance = ClassLoader.getSystemClassLoader();
		}
		return instance;
	}

	public static List<String> getModules() {
		return new ArrayList<>(modules);   // Return a copy to protect the list
	}
	
}
