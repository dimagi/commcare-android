package org.commcare.models.database.global;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.global.models.AppAvailableToInstall;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.DbUtil;
import org.commcare.modern.database.TableBuilder;

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
     * V.4 - Add table for storing force close log entries that occur outside of an active session
     * V.5 - Add table for storing apps available for install
     * V.6 - Add table for storing (encrypted) passphrase for ConnectId DB
     * V.7 - Add is_local column to ConnectKeyRecord table (to store both local and server passphrase)
     * V.8 - Add GlobalErrorRecord table
     */
    private static final int GLOBAL_DB_VERSION = 8;

    private static final String GLOBAL_DB_LOCATOR = "database_global";

    private final Context mContext;

    public DatabaseGlobalOpenHelper(Context context) {
        super(context, GLOBAL_DB_LOCATOR, null, GLOBAL_DB_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(ApplicationRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new TableBuilder(AndroidSharedKeyRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ForceCloseLogEntry.STORAGE_KEY);
            builder.addData(new ForceCloseLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(AppAvailableToInstall.STORAGE_KEY);
            builder.addData(new AppAvailableToInstall());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectKeyRecord.STORAGE_KEY);
            builder.addData(new ConnectKeyRecord());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(GlobalErrorRecord.STORAGE_KEY);
            builder.addData(new GlobalErrorRecord());
            database.execSQL(builder.getTableCreateString());

            DbUtil.createNumbersTable(database);

            database.setVersion(GLOBAL_DB_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
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
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("Global", oldVersion, newVersion));
        new GlobalDatabaseUpgrader(mContext).upgrade(db, oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("Global", oldVersion, newVersion));
    }

}
