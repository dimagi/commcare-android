package org.commcare.android.database.user;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.GeocodeCacheModel;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.cases.ledger.Ledger;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;

/**
 * The helper for opening/updating the user (encrypted) db space for
 * CommCare. This stores users, cases, fixtures, form records, etc.
 *
 * @author ctsims
 */
public class DatabaseUserOpenHelper extends SQLiteOpenHelper {

    /**
     * Version History
     * V.4 - Added Stock table for tracking quantities. Fixed Case ID index
     * V.5 - Fixed Ledger Stock ID's
     * V.6 - Indexed the case open + case type pairing (~every select screen)
     * Added Case Index table and join
     * Added Entity Cache Table
     * V.7 - Case index models now maintain relationship types. Migration object
     * used to update DB
     * V.8 - Merge commcare-odk and commcare User, make AUser legacy type.
     * V.9 - Update serialized fixtures in db to use new schema
     * V.10 - Migration of FormRecord to add appId field
     */

    private static final int USER_DB_VERSION = 10;

    private static final String USER_DB_LOCATOR = "database_sandbox_";

    private final Context context;

    private final String mUserId;

    public DatabaseUserOpenHelper(Context context, String userId) {
        super(context, getDbName(userId), null, USER_DB_VERSION);
        this.context = context;
        this.mUserId = userId;
    }

    public static String getDbName(String sandboxId) {
        return USER_DB_LOCATOR + sandboxId;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        try {
            database.beginTransaction();
            
            AndroidTableBuilder builder = new AndroidTableBuilder(ACase.STORAGE_KEY);
            builder.addData(new ACase());
            builder.setUnique(ACase.INDEX_CASE_ID);
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder("USER");
            builder.addData(new User());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(FormRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(SessionStateDescriptor.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(GeocodeCacheModel.STORAGE_KEY);
            builder.addData(new GeocodeCacheModel());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(DeviceReportRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder("fixture");
            builder.addData(new FormInstance());
            database.execSQL(builder.getTableCreateString());
            
            builder = new AndroidTableBuilder(Ledger.STORAGE_KEY);
            builder.addData(new Ledger());
            builder.setUnique(Ledger.INDEX_ENTITY_ID);
            database.execSQL(builder.getTableCreateString());

            //The uniqueness index should be doing this for us
            database.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_id_index", "AndroidCase", "case_id"));
            database.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_type_index", "AndroidCase", "case_type"));
            database.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_status_index", "AndroidCase", "case_status"));

            database.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_status_open_index", "AndroidCase", "case_type,case_status"));

            database.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("ledger_entity_id", "ledger", "entity_id"));

            DbUtil.createNumbersTable(database);

            database.execSQL(EntityStorageCache.getTableDefinition());
            EntityStorageCache.createIndexes(database);

            database.execSQL(CaseIndexTable.getTableDefinition());
            CaseIndexTable.createIndexes(database);

            database.setVersion(USER_DB_VERSION);

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
            DbUtil.trySqlCipherDbUpdate(key, context, getDbName(mUserId));
            return super.getWritableDatabase(key);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        boolean inSenseMode = false;
        //TODO: Not a great way to get the current app! Pass this in to the constructor.
        //I am preeeeeety sure that we can't get here without _having_ an app/platform, but not 100%
        try {
            if (CommCareApplication._().getCommCarePlatform() != null && CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null) {
                if (CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null &&
                        CommCareApplication._().getCommCarePlatform().getCurrentProfile().isFeatureActive("sense")) {
                    inSenseMode = true;
                }
            }
        } catch (Exception e) {

        }
        new UserDatabaseUpgrader(context, inSenseMode).upgrade(db, oldVersion, newVersion);
    }
}
