package org.commcare.android.database.app;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.resource.AndroidResourceManager;
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
     */
    private static final int DB_VERSION_APP = 8;

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
        try {
            database.beginTransaction();
            AndroidTableBuilder builder = new AndroidTableBuilder("GLOBAL_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder("UPGRADE_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new AndroidTableBuilder(AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY);
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new AndroidTableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder("fixture");
            builder.addData(new FormInstance());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(UserKeyRecord.class);
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

    /**
     * Build SQL command to create an index on a table
     *
     * @param indexName        Name of index on the table
     * @param tableName        Table target of index being created
     * @param columnListString One or more columns used to create the index.
     *                         Multiple columns should be comma-seperated.
     * @return Indexed table creation SQL command.
     */
    public static String indexOnTableCommand(String indexName,
                                             String tableName,
                                             String columnListString) {
        return "CREATE INDEX " + indexName + " ON " +
                tableName + "( " + columnListString + " )";
    }

    public static String indexOnTableWithPGUIDCommand(String indexName,
                                             String tableName) {
        return indexOnTableCommand(indexName, tableName, Resource.META_INDEX_PARENT_GUID);
    }

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
