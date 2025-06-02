package org.commcare.models.database.user;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.cases.ledger.Ledger;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.cases.model.Case;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.modern.database.TableBuilder;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IndexedFixturePathUtils;
import org.commcare.android.database.user.models.ACase;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.CommCareEntityStorageCache;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.Persistable;

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
     * V.11 - Add table for storing xpath errors for specific cc app versions
     * V.12 - Drop old GeocodeCacheModel table
     * V.13 - Add tables for storing normal device logs and force close logs in user storage
     * V.14 - Change format of last modified date in form record to canonical SQLite form
     * V.15 - Add table to store path info about storage-backed fixture tables
     * V.16 - Add type -> id index for case index storage
     * V.17 - Add global counter metadata field to form records, for use in submission ordering
     * V.18 - Add index on @owner_id for cases
     * V.19 - Rebuild case index table due to the possibility of previous 412 indexing issues
     * V.20 - Migrate index names on indexed fixtures so that multiple fixtures are able to have an index on the same column name
     * V.21 - Reindex all cases to add relationship, and add reasonForQuarantine field to FormRecords
     * V.22 - Add column for appId in entity_cache table
     * V.23 - Merges InstanceProvider to FormRecord (delete instanceUri, add displayName, filePath and canEditWhenComplete)
     * v.24 - Adds and indexes column for Case external_id
     * v.25 - No DB changes, validates SessionStateDescriptor records corrupted due to an earlier bug in v23 migration (In 2.44 and 2.44.1)
     * v.26 - Adds a column for 'last_sync' in IndexedFixtureIndex
     * v.27 - Adds a column `descriptor` in FormRecord.
     * v.28 - Adds and indexes columns for Case state and category
     * v.29 - Add columns for is_dirty and is_shallow in entity_cache table
     */

    private static final int USER_DB_VERSION = 29;

    private static final String USER_DB_LOCATOR = "database_sandbox_";

    private final Context context;

    private final String mUserId;
    private byte[] fileMigrationKeySeed = null;

    public DatabaseUserOpenHelper(Context context, String userKeyRecordId) {
        super(context, getDbName(userKeyRecordId), null, USER_DB_VERSION);
        this.context = context;
        this.mUserId = userKeyRecordId;
    }

    public static String getDbName(String sandboxId) {
        return USER_DB_LOCATOR + sandboxId;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(ACase.STORAGE_KEY);
            builder.addData(new ACase());
            builder.setUnique(ACase.INDEX_CASE_ID);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder("USER");
            builder.addData(new User());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(FormRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(SessionStateDescriptor.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(DeviceReportRecord.class);
            database.execSQL(builder.getTableCreateString());

            // add table for dedicated xpath error logging for reporting xpath
            // errors on specific cc app builds.
            builder = new TableBuilder(XPathErrorEntry.STORAGE_KEY);
            builder.addData(new XPathErrorEntry());
            database.execSQL(builder.getTableCreateString());

            // Add tables for storing normal device logs and force close logs in user storage
            // (as opposed to global storage) whenever possible
            builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ForceCloseLogEntry.STORAGE_KEY);
            builder.addData(new ForceCloseLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder("fixture");
            builder.addFileBackedData(new FormInstance());
            database.execSQL(builder.getTableCreateString());

            DbUtil.createOrphanedFileTable(database);

            IndexedFixturePathUtils.createStorageBackedFixtureIndexTable(database);

            builder = new TableBuilder(Ledger.STORAGE_KEY);
            builder.addData(new Ledger());
            builder.setUnique(Ledger.INDEX_ENTITY_ID);
            database.execSQL(builder.getTableCreateString());

            //The uniqueness index should be doing this for us
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_id_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_CASE_ID)));
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_type_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_CASE_TYPE)));
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_status_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_CASE_STATUS)));
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_owner_id_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_OWNER_ID)));
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_external_id_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_EXTERNAL_ID)));
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_category_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_CATEGORY)));
            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_state_index", "AndroidCase", TableBuilder.scrubName(Case.INDEX_STATE)));

            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_status_open_index", "AndroidCase", "case_type,case_status"));

            database.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "ledger_entity_id", "ledger", "entity_id"));

            DbUtil.createNumbersTable(database);

            database.execSQL(CommCareEntityStorageCache.getTableDefinition());
            CommCareEntityStorageCache.createIndexes(database);

            database.execSQL(AndroidCaseIndexTable.getTableDefinition());
            AndroidCaseIndexTable.createIndexes(database);

            database.setVersion(USER_DB_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase(String key) {
        fileMigrationKeySeed = key.getBytes();

        try {
            return super.getWritableDatabase(key);
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, context, getDbName(mUserId));
            return super.getWritableDatabase(key);
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
        new UserDatabaseUpgrader(context, mUserId, inSenseMode, fileMigrationKeySeed).upgrade(db, oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("User", oldVersion, newVersion));
    }

    public static void buildTable(SQLiteDatabase database,
                                  String tableName,
                                  Persistable dataObject) {
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(tableName);
            builder.addData(dataObject);
            database.execSQL(builder.getTableCreateString());
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static void dropTable(SQLiteDatabase database,
                                 String tableName) {
        database.beginTransaction();
        try {
            database.execSQL("DROP TABLE IF EXISTS '" + tableName + "'");
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

}
