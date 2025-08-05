package org.commcare.models.database.app;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.models.database.DbUtil;

import static org.commcare.models.database.app.AppDatabaseSchemaManager.DB_VERSION_APP;
import static org.commcare.models.database.app.AppDatabaseSchemaManager.getDbName;

/**
 * The helper for opening/updating the global (unencrypted) db space for
 * CommCare.
 *
 * @author ctsims
 */
public class DatabaseAppOpenHelper extends SQLiteOpenHelper {

    private final Context context;

    private final String mAppId;
    private final String key = "null";

    public DatabaseAppOpenHelper(Context context, String appId) {
        super(context, getDbName(appId), "null", null, DB_VERSION_APP, 0, null, null, false);

        this.mAppId = appId;
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        AppDatabaseSchemaManager.initializeSchema(new EncryptedDatabaseAdapter(db));
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, context, getDbName(mAppId));
            return super.getWritableDatabase();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("App", oldVersion, newVersion));
        new AppDatabaseUpgrader(context).upgrade(new EncryptedDatabaseAdapter(db), oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("App", oldVersion, newVersion));
    }
}
