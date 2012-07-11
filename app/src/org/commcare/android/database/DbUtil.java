package org.commcare.android.database;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import android.content.Context;
import dalvik.system.DexFile;

public class DbUtil {
	
	public static String ID_COL = "commcare_sql_id";
	public static String DATA_COL = "commcare_sql_record";
	
	private static PrototypeFactory factory;


	public static PrototypeFactory getPrototypeFactory(Context c) {
		if(factory != null) {
			return factory;
		}
		
		PrefixTree tree = new PrefixTree();
		
		try {
		List<String> classes = getClasses("org.javarosa", c);
		for(String cl : classes) {
			//Log.i("CLASS", cl);
			tree.addString(cl);
		}
		classes = getClasses("org.commcare", c);
		for(String cl : classes) {
			//Log.i("CLASS", cl);
			tree.addString(cl);
		}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		
		
		factory = new PrototypeFactory(tree);
		return factory;
		
	}
	
    /* Scans all classes accessible from the context class loader which belong to the given package and subpackages.
    *
    * @param packageName The base package
    * @return The classes
    * @throws ClassNotFoundException
    * @throws IOException
    */
   @SuppressWarnings("unchecked")
	private static List<String> getClasses(String packageName, Context c)
           throws IOException 
   {
       ArrayList<String> classNames = new ArrayList<String>();
       
       String zpath = c.getApplicationInfo().sourceDir;
	   
	   
	   if(zpath == null) {
		   zpath = "/data/app/org.commcare.android.apk";
	   }
	   
	   DexFile df = new DexFile(new File(zpath));
	   for(Enumeration<String> en = df.entries() ; en.hasMoreElements() ;) {
		   String cn = en.nextElement();
		   try{

		   if(cn.startsWith(packageName) && !cn.contains(".test.") && !cn.contains("readystatesoftware")) {
				   Class prototype = Class.forName(cn);
				   if(prototype.isInterface()) {
					   continue;
				   }
				   boolean emptyc = false;
				   for(Constructor<?> cons : prototype.getConstructors()) {
					   if(cons.getParameterTypes().length == 0){
						   emptyc = true;
					   }
				   }
				   if(!emptyc) {
					   continue;
				   }
				   if(Externalizable.class.isAssignableFrom(prototype)) {
					   classNames.add(cn);
				   }
		   		}
		   } catch(IllegalAccessError e) {
			   //nothing
		   } catch (SecurityException e) {
			   
		   } catch(Exception e) {
			   
		   } catch(ExceptionInInitializerError e) {
			   
		   }
	   }
	   
	   return classNames;
   }
}