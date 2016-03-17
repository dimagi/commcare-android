/**
 *
 */
package org.commcare.models.database.global;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.logging.AndroidLogEntry;
import org.commcare.models.database.AndroidTableBuilder;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.global.models.AndroidSharedKeyRecord;
import org.commcare.models.database.global.models.ApplicationRecord;

/**
 * The helper for opening/updating the global (unencrypted) db space for CommCare.
 *
 * @author ctsims
 */
public class DatabaseGlobalOpenHelper extends SQLiteOpenHelper {

    /**
     * V.2 - all sqlstorage objects now need numbers tables
     * V.3 - ApplicationRecord has new fields to support multiple app seating, FormsProvider
     * and InstanceProvider use per-app databases
     */
    private static final int GLOBAL_DB_VERSION = 3;

    private static final String GLOBAL_DB_LOCATOR = "database_global";

    private final Context mContext;

    public DatabaseGlobalOpenHelper(Context context) {
        super(context, GLOBAL_DB_LOCATOR, null, GLOBAL_DB_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {

        try {
            database.beginTransaction();
            
            AndroidTableBuilder builder = new AndroidTableBuilder(ApplicationRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(AndroidSharedKeyRecord.class);
            database.execSQL(builder.getTableCreateString());


            builder = new AndroidTableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());

            DbUtil.createNumbersTable(database);

            database.setVersion(GLOBAL_DB_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public SQLiteDatabase getWritableDatabase(String key) {
        try {
            return super.getWritableDatabase(key);
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, mContext, GLOBAL_DB_LOCATOR);
            return super.getWritableDatabase(key);
        }
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        new GlobalDatabaseUpgrader(mContext).upgrade(db, oldVersion, newVersion);
    }

}
