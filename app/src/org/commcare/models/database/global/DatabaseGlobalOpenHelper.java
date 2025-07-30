package org.commcare.models.database.global;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.EncryptedDatabaseAdapter;

import static org.commcare.models.database.global.GlobalDatabaseSchemaManager.GLOBAL_DB_LOCATOR;
import static org.commcare.models.database.global.GlobalDatabaseSchemaManager.GLOBAL_DB_VERSION;

/**
 * The helper for opening/updating the global (unencrypted) db space for CommCare.
 *
 * @author ctsims
 */
public class DatabaseGlobalOpenHelper extends SQLiteOpenHelper {

    private final Context mContext;
    private final String key = "null";

    public DatabaseGlobalOpenHelper(Context context) {
        super(context, GLOBAL_DB_LOCATOR, "null", null, GLOBAL_DB_VERSION, 0, null, null, false);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        GlobalDatabaseSchemaManager.initializeSchema(new EncryptedDatabaseAdapter(db));
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
