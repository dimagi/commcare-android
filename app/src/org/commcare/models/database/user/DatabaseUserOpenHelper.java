package org.commcare.models.database.user;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.commcare.CommCareApplication;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.models.database.DbUtil;

import static org.commcare.models.database.user.UserDatabaseSchemaManager.USER_DB_VERSION;
import static org.commcare.models.database.user.UserDatabaseSchemaManager.getDbName;

/**
 * The helper for opening/updating the user (encrypted) db space for
 * CommCare. This stores users, cases, fixtures, form records, etc.
 *
 * @author ctsims
 */
public class DatabaseUserOpenHelper extends SQLiteOpenHelper {

    private final Context context;

    private final String mUserId;
    private final String key;
    private byte[] fileMigrationKeySeed = null;

    public DatabaseUserOpenHelper(Context context, String userKeyRecordId, String key) {
        super(context, getDbName(userKeyRecordId), key, null, USER_DB_VERSION, 0, null, null, false);
        this.key = key;
        this.context = context;
        this.mUserId = userKeyRecordId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        UserDatabaseSchemaManager.initializeSchema(new EncryptedDatabaseAdapter(db));
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        fileMigrationKeySeed = key.getBytes();

        try {
            return super.getWritableDatabase();
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, context, getDbName(mUserId));
            return super.getWritableDatabase();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("User", oldVersion, newVersion));
        boolean inSenseMode = false;
        //TODO: Not a great way to get the current app! Pass this in to the constructor.
        //I am preeeeeety sure that we can't get here without _having_ an app/platform, but not 100%
        try {
            if (CommCareApplication.instance().getCommCarePlatform() != null && CommCareApplication.instance().getCommCarePlatform().getCurrentProfile() != null) {
                if (CommCareApplication.instance().getCommCarePlatform().getCurrentProfile() != null &&
                        CommCareApplication.instance().getCommCarePlatform().getCurrentProfile().isFeatureActive("sense")) {
                    inSenseMode = true;
                }
            }
        } catch (Exception e) {

        }
        new UserDatabaseUpgrader(context, mUserId, inSenseMode, fileMigrationKeySeed).upgrade(new EncryptedDatabaseAdapter(db), oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("User", oldVersion, newVersion));
    }
}
