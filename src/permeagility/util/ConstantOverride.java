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

import java.lang.reflect.Field;

import com.arcadedb.database.Document;


/** Use database table to load static constants into classes */
public class ConstantOverride {

    public static boolean DEBUG = true;

    public static boolean apply(DatabaseConnection con) {
        System.out.println("Refreshing system constants");
        String query = "SELECT FROM constant";
        String className = null;
        String field = null;
        String value = null;
        try {
            QueryResult result = con.query(query);
            for (Document row : result.get()) {
                className = row.getString("classname");
                field = row.getString("field");
                value = row.getString("value");
                try {
                    Class<?> c = Class.forName( className, true, PlusClassLoader.get() );
                    Object o = c.getDeclaredConstructor().newInstance();
                    try {
                            Field f = c.getField(field);
                            Class<?> t = f.getType();
                            String tName = t.getName();
                            if (tName.equals("int")) {
                                    f.setInt(o,Integer.valueOf(value).intValue());
                            } else if (tName.equals("long")) {
                                            f.setLong(o,Long.valueOf(value).longValue());
                            } else if (tName.equals("boolean")) {
                                    f.setBoolean(o,Boolean.valueOf(value).booleanValue());
                            } else if (tName.equals("double")) {
                                    f.setDouble(o,Double.valueOf(value).doubleValue());
                            } else if (t == Class.forName("java.lang.Long")) {
                                    f.setLong(o,Long.valueOf(value).longValue());
                            } else if (t == Class.forName("java.lang.Double")) {
                                    f.setDouble(o,Double.valueOf(value).doubleValue());
                            } else if (t == Class.forName("java.lang.String")) {
                                    f.set(o,value);
                            } else if (t == Class.forName("java.lang.Integer")) {
                                    f.setInt(o,Integer.valueOf(value).intValue());
                            } else if (t == Class.forName("java.lang.Short")) {
                                    f.setShort(o,Short.valueOf(value).shortValue());
                            } else if (t == Class.forName("java.lang.Float")) {
                                    f.setFloat(o,Float.valueOf(value).floatValue());
                            } else if (t == Class.forName("java.lang.Byte")) {
                                    f.setByte(o,Byte.valueOf(value).byteValue());
                            } else if (t == Class.forName("java.lang.Boolean")) {
                                    f.setBoolean(o,Boolean.valueOf(value).booleanValue());
                            } else {
                                    System.out.println("Type of "+t.getName()+" cannot be set by the ConstantOverride class, please edit it to add the type");
                            }
                            if (DEBUG) System.out.println(className+":"+field+"="+value);
                    } catch (Exception e) {
                            System.out.println("Unable to set constant "+field+" on "+className+" because of:"+e);
                    }
                } catch( Throwable t ) {
                    if (t instanceof InstantiationException) {
                        System.out.println( "An instance of "+ className + " could not be created. It may be because the class does not have an empty constructor. Error=" + t.toString() );
                    } else {
                        System.out.println( "Constant override classname "+className + " not found: " + t.toString() );
                    }
                }
            }
        } catch( Exception e ) {
            System.out.println("Error in ConstantOverride: "+e);
            e.printStackTrace();
            return false;
        }
        return true;
    }		

    
}


