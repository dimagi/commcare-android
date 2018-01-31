package org.commcare.models.database.app;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.InstanceRecord;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.modern.database.TableBuilder;
import org.commcare.models.database.DbUtil;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.resources.model.Resource;
import org.javarosa.core.model.instance.FormInstance;

/**
 * The helper for opening/updating the global (unencrypted) db space for
 * CommCare.
 *
 * @author ctsims
 */
public class DatabaseAppOpenHelper extends SQLiteOpenHelper {
    /**
     * Version History
     * V.2 - Added recovery table
     * V.3 - Upgraded Resource models to have an optional descriptor field
     * V.4 - Table parent resource indices
     * V.5 - Added numbers table
     * V.6 - Added temporary upgrade table for ease of checking for new updates
     * V.7 - Update serialized fixtures in db to use new schema
     * V.8 - Add fields to UserKeyRecord to support PIN auth
     * V.9 - Adds FormRecord and Instance Record tables, XFormAndroidInstaller: contentUri -> formDefId
     */
    private static final int DB_VERSION_APP = 9;

    private static final String DB_LOCATOR_PREF_APP = "database_app_";

    private final Context context;

    private final String mAppId;

    public DatabaseAppOpenHelper(Context context, String appId) {
        super(context, getDbName(appId), null, DB_VERSION_APP);
        this.mAppId = appId;
        this.context = context;
    }

    public static String getDbName(String appId) {
        return DB_LOCATOR_PREF_APP + appId;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder("GLOBAL_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder("UPGRADE_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY);
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder("fixture");
            builder.addFileBackedData(new FormInstance());
            database.execSQL(builder.getTableCreateString());

            DbUtil.createOrphanedFileTable(database);

            builder = new TableBuilder(UserKeyRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(FormDefRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(InstanceRecord.class);
            database.execSQL(builder.getTableCreateString());

            database.execSQL(indexOnTableWithPGUIDCommand("global_index_id", "GLOBAL_RESOURCE_TABLE"));
            database.execSQL(indexOnTableWithPGUIDCommand("upgrade_index_id", "UPGRADE_RESOURCE_TABLE"));
            database.execSQL(indexOnTableWithPGUIDCommand("recovery_index_id", "RECOVERY_RESOURCE_TABLE"));
            database.execSQL(indexOnTableWithPGUIDCommand("temp_upgrade_index_id", AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY));

            DbUtil.createNumbersTable(database);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static String indexOnTableWithPGUIDCommand(String indexName,
                                                      String tableName) {
        return DatabaseIndexingUtils.indexOnTableCommand(indexName, tableName, Resource.META_INDEX_PARENT_GUID);
    }

    @Override
    public SQLiteDatabase getWritableDatabase(String key) {
        try {
            return super.getWritableDatabase(key);
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, context, getDbName(mAppId));
            return super.getWritableDatabase(key);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        new AppDatabaseUpgrader(context).upgrade(db, oldVersion, newVersion);
    }
}
