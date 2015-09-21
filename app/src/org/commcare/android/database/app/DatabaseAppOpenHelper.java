package org.commcare.android.database.app;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.app.models.UserKeyRecord;
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
     */
    private static final int DB_VERSION_APP = 5;
    
    private static final String DB_LOCATOR_PREF_APP = "database_app_";
    
    private Context context;
    
    private String mAppId;

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
            
            builder = new AndroidTableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder("fixture");
            builder.addData(new FormInstance());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(UserKeyRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            database.execSQL("CREATE INDEX global_index_id ON GLOBAL_RESOURCE_TABLE ( " + Resource.META_INDEX_PARENT_GUID + " )");
            database.execSQL("CREATE INDEX upgrade_index_id ON UPGRADE_RESOURCE_TABLE ( " + Resource.META_INDEX_PARENT_GUID + " )");
            database.execSQL("CREATE INDEX recovery_index_id ON RECOVERY_RESOURCE_TABLE ( " + Resource.META_INDEX_PARENT_GUID + " )");

            DbUtil.createNumbersTable(database);
            
            database.setTransactionSuccessful();
            
        } finally {
            database.endTransaction();
        }
    }
    
    public SQLiteDatabase getWritableDatabase(String key) {
        try{ 
            return super.getWritableDatabase(key);
        } catch(SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, context, getDbName(mAppId));
            return super.getWritableDatabase(key);
        }
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        new AppDatabaseUpgrader(context).upgrade(db, oldVersion, newVersion);
    }

}
