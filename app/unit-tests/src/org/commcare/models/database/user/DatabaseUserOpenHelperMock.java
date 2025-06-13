package org.commcare.models.database.user;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.UnencryptedDatabaseAdapter;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.CommCareEntityStorageCache;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.modern.database.TableBuilder;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;
import org.commcare.modern.database.IndexedFixturePathsConstants;
import org.commcare.modern.database.TableBuilder;


import static org.commcare.modern.database.IndexedFixturePathsConstants.INDEXED_FIXTURE_INDEXING_STMT;
import static org.commcare.modern.database.IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_TABLE_STMT;

public class DatabaseUserOpenHelperMock extends SQLiteOpenHelper {

    private static final String USER_DB_LOCATOR = "database_sandbox_";

    public DatabaseUserOpenHelperMock(Context context, String userKeyRecordId) {
        super(context, getDbName(userKeyRecordId), null,1, null);
    }

    public static String getDbName(String sandboxId) {
        return USER_DB_LOCATOR + sandboxId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        IDatabase database = new UnencryptedDatabaseAdapter(db);
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

            database.execSQL(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_TABLE_STMT);
            database.execSQL(IndexedFixturePathsConstants.INDEXED_FIXTURE_INDEXING_STMT);

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

            database.setVersion(1);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
