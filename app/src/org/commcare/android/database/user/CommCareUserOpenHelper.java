/**
 *
 */
package org.commcare.android.database.user;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.GeocodeCacheModel;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.javarosa.core.model.User;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.cases.ledger.Ledger;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.FormInstance;

/**
 * The central db point for
 *
 * @author ctsims
 */
public class CommCareUserOpenHelper extends SQLiteOpenHelper {

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
     */

    private static final int USER_DB_VERSION = 8;

    private static final String USER_DB_LOCATOR = "database_sandbox_";

    private Context context;

    private String mUserId;

    public CommCareUserOpenHelper(Context context, String userId) {
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
            database.execSQL("CREATE INDEX case_id_index ON AndroidCase (case_id)");
            database.execSQL("CREATE INDEX case_type_index ON AndroidCase (case_type)");
            database.execSQL("CREATE INDEX case_status_index ON AndroidCase (case_status)");

            database.execSQL("CREATE INDEX case_status_open_index ON AndroidCase (case_type,case_status)");

            database.execSQL("CREATE INDEX ledger_entity_id ON ledger (entity_id)");

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
            } else {
                //Hold off on update?
            }
        } catch (Exception e) {

        }
        new UserDatabaseUpgrader(context, inSenseMode).upgrade(db, oldVersion, newVersion);
    }

}
