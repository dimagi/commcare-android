package org.commcare.models.database.connect;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.commcare.CommCareApplication;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.services.Logger;

import java.io.File;

import static org.commcare.models.database.connect.ConnectDatabaseSchemaManager.DB_NAME;
import static org.commcare.models.database.connect.ConnectDatabaseSchemaManager.DB_VERSION_CONNECT;

/**
 * The helper for opening/updating the Connect (encrypted) db space for CommCare.
 *
 * @author dviggiano
 */
public class DatabaseConnectOpenHelper extends SQLiteOpenHelper {

    private final Context mContext;
    private final String key;

    public DatabaseConnectOpenHelper(Context context, String key) {
        super(context, DB_NAME, key, null, DB_VERSION_CONNECT, 0, null, null, false);
        this.mContext = context;
        this.key = key;
    }

    private static File getDbFile() {
        return CommCareApplication.instance().getDatabasePath(DB_NAME);
    }

    public static boolean dbExists() {
        return getDbFile().exists();
    }

    public static void deleteDb() {
        getDbFile().delete();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ConnectDatabaseSchemaManager.initializeSchema(new EncryptedDatabaseAdapter(db));
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException sqle) {
            Logger.exception("Opening database failed", sqle);
            DbUtil.trySqlCipherDbUpdate(key, mContext, DB_NAME);
            try {
                return super.getWritableDatabase();
            } catch (SQLiteException e) {
                CrashUtil.log(e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("Connect", oldVersion, newVersion));
        new ConnectDatabaseUpgrader(mContext).upgrade(new EncryptedDatabaseAdapter(db), oldVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("Connect", oldVersion, newVersion));
    }
}
