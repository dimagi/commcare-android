package org.commcare.android.database;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.externalizable.ImprovedPrototypeFactory;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import android.content.Context;
import android.database.Cursor;
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
        List<String> classes = getClasses(new String[] { "org.javarosa", "org.commcare"}, c);
        for(String cl : classes) {
            tree.addString(cl);
        }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        
        
        factory = new ImprovedPrototypeFactory(tree);
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
    private static List<String> getClasses(String[] packageNames, Context c)
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
               for(String packageName : packageNames) {

                   if(cn.startsWith(packageName) && !cn.startsWith("org.commcare.dalvik") && !cn.contains(".test.") && !cn.contains("readystatesoftware") ) {
                       
                       //TODO: These optimize by preventing us from statically loading classes we don't need, but they take a _long_ time to run. 
                       //Maybe we should skip this and/or roll it into initializing the factory itself.
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
                       classNames.add(cn);
                   }
               }
           } catch(IllegalAccessError e) {
               //nothing
           } catch (SecurityException e) {
               
           } catch(Exception e) {
               
           } catch(Error e) {
               
           }
       }
       
       return classNames;
   }
   
   /**
    * Static experimentation code. Isnt for prod, but left in as an example and location to
    * test functionality
    */
   public static void getGroups() {
       SQLiteDatabase db = CommCareApplication._().getUserDbHandle();
       //String stmt = "SELECT commcare_sql_id AS IdStart,        (SELECT MAX(commcare_sql_id)         FROM AndroidCase AS t3         WHERE t3.commcare_sql_id - t1.commcare_sql_id + 1 = (SELECT COUNT(*)                                                FROM AndroidCase AS t4                                                WHERE t4.commcare_sql_id BETWEEN t1.commcare_sql_id AND t3.commcare_sql_id)        ) AS IdEnd FROM AndroidCase AS t1 WHERE NOT EXISTS (SELECT 1                   FROM AndroidCase AS t2                   WHERE t2.commcare_sql_id = t1.commcare_sql_id - 1) ";
       
       SQLiteStatement min = db.compileStatement("SELECT MIN(commcare_sql_id) from AndroidCase");
       
       SQLiteStatement max = db.compileStatement("SELECT MAX(commcare_sql_id) from AndroidCase");
       
       long minValue = min.simpleQueryForLong();
       long maxValue = max.simpleQueryForLong();
       max.close();
       min.close();
       
       System.out.println("Min ID: " + minValue);
       System.out.println("Max ID: " + maxValue);
       
       
       
//       DatabaseUtils.dumpCursor(db.rawQuery("select 10000 * tenthousands.i as commcare_sql_id from integers tenthousands", null));
//       
//       DatabaseUtils.dumpCursor(db.rawQuery("select i from integers", null));
       
//       DatabaseUtils.dumpCursor(db.rawQuery("select 10*tens.i + units.i as ints from integers tens, integers units",null));
//       
       //DatabaseUtils.dumpCursor(db.rawQuery("select 10*tens.i + units.i as ints from integers tens, integers units WHERE ints > CAST(? AS INTEGER)", new String[] {"3"}));
       
//       DatabaseUtils.dumpCursor(db.rawQuery("select 10*tens.i + units.i as ints from integers tens, integers units WHERE ints > 3", null));
//       
//       DatabaseUtils.dumpCursor(db.rawQuery("select 10*tens.i + units.i as dints from integers tens cross join integers units", null));
//       
       String vals = "select 10000 * tenthousands.i + 1000 * thousands.i + 100*hundreds.i + 10*tens.i + units.i as commcare_sql_id " + 
       "from integers tenthousands " +
            ", integers thousands " +
            ", integers hundreds  " +
            ", integers tens " +
            ", integers units " +
            " WHERE commcare_sql_id >= CAST(? AS INTEGER) AND commcare_sql_id <= CAST(? AS INTEGER)";


       String[] args = new String[] {String.valueOf(minValue), String.valueOf(maxValue)}; 
       
       String stmt = vals + " EXCEPT SELECT commcare_sql_id FROM AndroidCase";
       if(true) {
           Cursor explain = db.rawQuery("EXPLAIN QUERY PLAN " + stmt,args);
           System.out.println("SQL: " + stmt);
           DatabaseUtils.dumpCursor(explain);
           explain.close();
       }
       
       System.out.println("Beginning Index Intersection");
       long timeInMillis = System.currentTimeMillis();

       Cursor c = db.rawQuery(stmt, args);
       DatabaseUtils.dumpCursor(c);
       
       long value = System.currentTimeMillis() - timeInMillis;
       System.out.println("Index Intersection Took: " + value + "ms");
       
       c.close();
       
   }

   public static void createNumbersTable(SQLiteDatabase db) {
       //Virtual Table
       String dropStatement = "DROP TABLE IF EXISTS integers;";
       db.execSQL(dropStatement);
       String createStatement = "CREATE TABLE integers (i INTEGER);";
       db.execSQL(createStatement);
       
       for(long i =0 ; i < 10; ++i) {
           db.execSQL("INSERT INTO integers VALUES (" + i + ");");
       }
   }
}