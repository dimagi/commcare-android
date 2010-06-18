package org.commcare.android.database;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.commcare.android.logic.GlobalConstants;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import dalvik.system.DexFile;

public class DbUtil {
	
	public static String ID_COL = "commcare_sql_id";
	public static String DATA_COL = "commcare_sql_record";

	private static SQLiteDatabase db;
	
	private static PrototypeFactory factory;
	
	public static SQLiteDatabase getHandle() {
		if(db == null) {
			db = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.OPEN_READWRITE);
		} else if(!db.isOpen()) {
			db = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.OPEN_READWRITE);
		}
		return db;
	}
	
	public static ContentValues getContentValues(Externalizable e) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		try {
			e.writeExternal(new DataOutputStream(out));
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new RuntimeException("Failed to serialize externalizable for content values");
		}
		
		ContentValues values = new ContentValues();
		
		if(e instanceof IMetaData) {
			IMetaData m = (IMetaData)e;
			Hashtable<String,Object> data = m.getMetaData();
			for(Enumeration<String> keys = data.keys() ; keys.hasMoreElements() ;) {
				String key = keys.nextElement();
				String value = data.get(key).toString();
				values.put(key, value);
			}
		}
		
		values.put(DATA_COL, out.toByteArray());
		
		return values;
	}

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
	   
	   URL uri = c.getClassLoader().getResource(c.getPackageName());
	   
	   String zpath = null;
	   if(uri == null) {
		   zpath = "/data/app/org.commcare.android.apk";
	   } else {
		  zpath = uri.toString();
	   }
	   DexFile df = new DexFile(new File(zpath));
	   for(Enumeration<String> en = df.entries() ; en.hasMoreElements() ;) {
		   String cn = en.nextElement();
		   if(cn.startsWith(packageName) && !cn.contains(".test.")) {
			   try{
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
			   } catch(IllegalAccessError e) {
				   //nothing
			   } catch (SecurityException e) {
				   
			   } catch(Exception e) {
			   }
		   }
	   }
	   
	   return classNames;
   }
}