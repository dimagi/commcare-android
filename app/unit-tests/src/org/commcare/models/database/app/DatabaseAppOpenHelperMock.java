package org.commcare.models.database.app;

import static org.commcare.models.database.app.DatabaseAppOpenHelper.indexOnTableWithPGUIDCommand;
import static org.commcare.utils.AndroidCommCarePlatform.GLOBAL_RESOURCE_TABLE_NAME;
import static org.commcare.utils.AndroidCommCarePlatform.RECOVERY_RESOURCE_TABLE_NAME;
import static org.commcare.utils.AndroidCommCarePlatform.UPGRADE_RESOURCE_TABLE_NAME;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.UnencryptedDatabaseAdapter;
import org.commcare.modern.database.TableBuilder;
import org.commcare.recovery.measures.RecoveryMeasure;
import org.commcare.resources.model.Resource;
import org.javarosa.core.model.instance.FormInstance;

public class DatabaseAppOpenHelperMock extends SQLiteOpenHelper {

    private static final String DB_LOCATOR_PREF_APP = "database_app_";

    public DatabaseAppOpenHelperMock(Context context, String appId) {
        super(context, getDbName(appId), null, 1);
    }

    public static String getDbName(String appId) {
        return DB_LOCATOR_PREF_APP + appId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        IDatabase database = new UnencryptedDatabaseAdapter(db);
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(GLOBAL_RESOURCE_TABLE_NAME);
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(UPGRADE_RESOURCE_TABLE_NAME);
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY);
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(RECOVERY_RESOURCE_TABLE_NAME);
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

            database.execSQL(indexOnTableWithPGUIDCommand("global_index_id", GLOBAL_RESOURCE_TABLE_NAME));
            database.execSQL(indexOnTableWithPGUIDCommand("upgrade_index_id", UPGRADE_RESOURCE_TABLE_NAME));
            database.execSQL(indexOnTableWithPGUIDCommand("recovery_index_id", RECOVERY_RESOURCE_TABLE_NAME));
            database.execSQL(indexOnTableWithPGUIDCommand("temp_upgrade_index_id", AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY));

            DbUtil.createNumbersTable(database);

            database.execSQL(new TableBuilder(RecoveryMeasure.class).getTableCreateString());

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
