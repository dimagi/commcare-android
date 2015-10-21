package org.commcare.android.database.user.models;

import android.content.ContentValues;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.cases.model.Case;
import org.commcare.cases.model.CaseIndex;
import org.commcare.dalvik.application.CommCareApplication;

import java.util.Vector;

/**
 * @author ctsims
 */
public class CaseIndexTable {
    public static final String TABLE_NAME = "case_index_storage";

    public static final String COL_CASE_RECORD_ID = "case_rec_id";
    public static final String COL_INDEX_NAME = "name";
    public static final String COL_INDEX_TYPE = "type";
    public static final String COL_INDEX_TARGET = "target";


    public static String getTableDefinition() {
        String tableCreate = "CREATE TABLE " + TABLE_NAME + "(" +
                DbUtil.ID_COL + " INTEGER PRIMARY KEY, " +
                COL_CASE_RECORD_ID + ", " +
                COL_INDEX_NAME + ", " +
                COL_INDEX_TYPE + ", " +
                COL_INDEX_TARGET +
                ")";
        return tableCreate;
    }

    public static void createIndexes(SQLiteDatabase db) {
        String columns = COL_CASE_RECORD_ID + ", " + COL_INDEX_NAME + ", " + COL_INDEX_TARGET;
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("RECORD_NAME_ID_TARGET", TABLE_NAME, columns));
    }

    //TODO: We should do some synchronization to make it the case that nothing can hold
    //an object for the same cache at once and let us manage the lifecycle

    public CaseIndexTable() {
        // TODO PLM: remove this constructor and have callers pass in result
        // from getUserDbHandle()
        try {
            this.db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            // TODO PLM: find a way to fail elegantly here.
            throw new UserStorageClosedException(e.getMessage());
        }
    }

    SQLiteDatabase db;

    public CaseIndexTable(SQLiteDatabase dbHandle) {
        this.db = dbHandle;
    }

    /**
     * Creates all indexes for this case.
     * TODO: this doesn't ensure any sort of uniquenes, you should wipe constraints first
     */
    public void indexCase(Case c) {
        db.beginTransaction();
        try {
            for (CaseIndex ci : c.getIndices()) {
                ContentValues cv = new ContentValues();
                cv.put(COL_CASE_RECORD_ID, c.getID());
                cv.put(COL_INDEX_NAME, ci.getName());
                cv.put(COL_INDEX_TYPE, ci.getTargetType());
                cv.put(COL_INDEX_TARGET, ci.getTarget());
                db.insert(TABLE_NAME, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearCaseIndices(Case c) {
        db.beginTransaction();
        try {
            if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
                String sqlStatement = String.format("DELETE FROM %s WHERE %s = ?", TABLE_NAME, COL_CASE_RECORD_ID);
                DbUtil.explainSql(db, sqlStatement, new String[]{String.valueOf(c.getID())});
            }
            db.delete(TABLE_NAME, COL_CASE_RECORD_ID + " = ?", new String[]{String.valueOf(c.getID())});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

    }

    /**
     * Get a list of Case Record id's for cases which index a provided value.
     *
     * @param indexName   The name of the index
     * @param targetValue The case targeted by the index
     * @return An integer array of indexed case record ids
     */
    public Vector<Integer> getCasesMatchingIndex(String indexName, String targetValue) {
        String[] args = new String[]{indexName, targetValue};
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            String query = String.format("SELECT %s FROM %s WHERE %s = ? AND %s = ?", COL_CASE_RECORD_ID, TABLE_NAME, COL_INDEX_NAME, COL_INDEX_TARGET);
            DbUtil.explainSql(db, query, args);
        }
        Cursor c = db.query(TABLE_NAME, new String[]{COL_CASE_RECORD_ID}, COL_INDEX_NAME + " = ? AND " + COL_INDEX_TARGET + " =  ?", args, null, null, null);
        return SqlStorage.fillIdWindow(c, COL_CASE_RECORD_ID);
    }

}
