package org.commcare.android.database;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import org.commcare.android.database.global.DatabaseGlobalOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ApplicationRecordV1;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.externalizable.AndroidPrototypeFactory;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;

public class DbUtil {
    private static final String TAG = DbUtil.class.getSimpleName();
    
    public static final String ID_COL = "commcare_sql_id";
    public static final String DATA_COL = "commcare_sql_record";
    
    private static PrototypeFactory factory;

    public static void setDBUtilsPrototypeFactory(PrototypeFactory factory) {
        DbUtil.factory = factory;
    }

    /**
     * Basically this is our PrototypeManager for Android
     */
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
        
        factory = new AndroidPrototypeFactory(tree);
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
       ArrayList<String> classNames = new ArrayList<>();
       
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
    * Provides a hook for Sqllite databases to be able to try to migrate themselves in place 
    * from the writabledatabase method. Required due to SqlCipher making it incredibly difficult 
    * and obnoxious to determine when your databases need an upgrade, so we'll just try to run
    * one any time the method would have crashed anyway.
    * 
    * Will crash if this update doesn't work, so no return is needed
    */
   public static void trySqlCipherDbUpdate(String key, Context context, String dbName) {
       //There's no clear way how to tell whether this call is the invalid db version
       //because SqlLite didn't actually provide that info (thanks!), but we can 
       //test manually
       
       //Set up the hook to fire the right pragma ops
       SQLiteDatabaseHook updateHook = new SQLiteDatabaseHook() {

           public void preKey(SQLiteDatabase database) {}

           public void postKey(SQLiteDatabase database) {
               database.rawExecSQL("PRAGMA cipher_migrate;");
           }
       };
       
       //go find the db path because the helper hides this (thanks android)
       File dbPath = context.getDatabasePath(dbName);
       
       SQLiteDatabase oldDb = SQLiteDatabase.openOrCreateDatabase(dbPath, key, null, updateHook);
       
       //if we didn't get here, we didn't crash (what a great way to be testing our db version, right?)
       oldDb.close();
   }
   
   public static void createNumbersTable(SQLiteDatabase db) {
       //Virtual Table
       String dropStatement = "DROP TABLE IF EXISTS integers;";
       db.execSQL(dropStatement);
       String createStatement = "CREATE TABLE integers (i INTEGER);";
       db.execSQL(createStatement);
       
       for (long i = 0; i < 10; ++i) {
           db.execSQL("INSERT INTO integers VALUES (" + i + ");");
       }
   }

    public static void explainSql(SQLiteDatabase handle, String sql, String[] args) {
        Cursor explain = handle.rawQuery("EXPLAIN QUERY PLAN " + sql, args);
        Log.d(TAG, "SQL: " + sql);
        DatabaseUtils.dumpCursor(explain);
        explain.close();
    }

    public static boolean multipleInstalledAppRecords() {
        int globalDbVersionNumber = CommCareApplication._().getGlobalDbVersion();
        if (globalDbVersionNumber >= 3) {
            // If the 1st multiple apps migration has already occurred, then app records will be
            // stored in the db as an ApplicationRecord
            SqlStorage<ApplicationRecord> storage =
                    CommCareApplication._().getGlobalStorage(ApplicationRecord.class);
            int count = 0;
            for (ApplicationRecord r : storage) {
                if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                    count++;
                }
            }
            return (count > 1);
        } else {
            // If the 1st multiple apps migration has NOT already occurred, then app records will
            // be stored in the db as an ApplicationRecordV1
            SqlStorage<ApplicationRecordV1> storage =
                    CommCareApplication._().getGlobalStorage(ApplicationRecordV1.class);
            int count = 0;
            for (ApplicationRecordV1 r : storage) {
                if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                    count++;
                }
            }
            return (count > 1);
        }

    }

    public static ApplicationRecord getInstalledAppRecord(Context c, SQLiteDatabase db) {
        SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                ApplicationRecord.STORAGE_KEY,
                ApplicationRecord.class,
                new ConcreteAndroidDbHelper(c, db));
        for (Persistable p : storage) {
            ApplicationRecord r = (ApplicationRecord) p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                return r;
            }
        }
        return null;
    }
}
