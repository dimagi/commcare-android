/**
 * 
 */
package org.commcare.android.database.global;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.TableBuilder;
import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.javarosa.AndroidLogEntry;

import android.content.Context;

/**
 * The helper for opening/updating the global (unencrypted) db space for CommCare.
 * 
 * 
 * 
 * @author ctsims
 *
 */
public class DatabaseGlobalOpenHelper extends SQLiteOpenHelper {
    
    private static final int GLOBAL_DB_VERSION = 1;
    
    private static final String GLOBAL_DB_LOCATOR = "database_global";
    
    private Context context;

    public DatabaseGlobalOpenHelper(Context context) {
        super(context, GLOBAL_DB_LOCATOR, null, GLOBAL_DB_VERSION);
        this.context = context;
    }

    /* (non-Javadoc)
     * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
     */
    @Override
    public void onCreate(SQLiteDatabase database) {
        
        try {
            database.beginTransaction();
            
            TableBuilder builder = new TableBuilder(ApplicationRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new TableBuilder(AndroidSharedKeyRecord.class);
            database.execSQL(builder.getTableCreateString());

            
            builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());
            
            database.setVersion(GLOBAL_DB_VERSION);
                    
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
    
    public SQLiteDatabase getWritableDatabase(String key) {
        try{ 
            return super.getWritableDatabase(key);
        } catch(SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, context, GLOBAL_DB_LOCATOR);
            return super.getWritableDatabase(key);
        }
    }
    
    

    /* (non-Javadoc)
     * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

}
