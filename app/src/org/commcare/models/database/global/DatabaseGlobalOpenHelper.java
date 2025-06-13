package org.commcare.models.database.global;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.global.models.AppAvailableToInstall;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.models.database.IDatabase;
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
     */
    private static final int GLOBAL_DB_VERSION = 7;

    private static final String GLOBAL_DB_LOCATOR = "database_global";

    private final Context mContext;
    private final String key;

    public DatabaseGlobalOpenHelper(Context context, String key) {
        super(context, GLOBAL_DB_LOCATOR, "null", null, GLOBAL_DB_VERSION, 0, null, null, false);
        this.mContext = context;
        this.key = key;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        IDatabase database = new EncryptedDatabaseAdapter(db);
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

            DbUtil.createNumbersTable(database);

            database.setVersion(GLOBAL_DB_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, mContext, GLOBAL_DB_LOCATOR);
            return super.getWritableDatabase();
        }
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("Global", oldVersion, newVersion));
        new GlobalDatabaseUpgrader(mContext).upgrade(new EncryptedDatabaseAdapter(db), oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("Global", oldVersion, newVersion));
    }

}
