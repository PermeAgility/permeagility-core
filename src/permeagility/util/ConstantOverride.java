/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.util;

import java.lang.reflect.Field;

import com.orientechnologies.orient.core.record.impl.ODocument;

/** Use database table to load static constants into classes */
public class ConstantOverride {

    public static boolean DEBUG = false;

    public static boolean apply(DatabaseConnection con) {
        System.out.println("Refreshing system constants");
        String query = "SELECT classname, field, value FROM constant";
        if (DEBUG) System.out.println(query);
        String className = null;
        String field = null;
        String value = null;
        try {
            QueryResult result = con.query(query);
            for (ODocument row : result.get()) {
                className = (String)row.field("classname");
                field = (String)row.field("field");
                value = (String)row.field("value");
                try {
                    Class<?> c = Class.forName( className, true, PlusClassLoader.get() );
                    Object o = c.newInstance();
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


