package org.commcare.models.database.user.models;

import android.content.ContentValues;

import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.cases.model.CaseIndex;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.DatabaseHelper;

// Case Index table extension for Pre user db model 21 to use in DB migration
public class AndroidCaseIndexTablePreV21  {

    public static final String TABLE_NAME = "case_index_storage";

    private static final String COL_CASE_RECORD_ID = "case_rec_id";
    private static final String COL_INDEX_NAME = "name";
    private static final String COL_INDEX_TYPE = "type";
    private static final String COL_INDEX_TARGET = "target";

    private final IDatabase db;

    public AndroidCaseIndexTablePreV21(IDatabase db) {
        this.db = db;
    }


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

    public static String getTableDefinition() {
        return "CREATE TABLE " + TABLE_NAME + "(" +
                DatabaseHelper.ID_COL + " INTEGER PRIMARY KEY, " +
                COL_CASE_RECORD_ID + ", " +
                COL_INDEX_NAME + ", " +
                COL_INDEX_TYPE + ", " +
                COL_INDEX_TARGET +
                ")";
    }

    public void reIndexAllCases(SqlStorage<ACase> caseStorage) {
        db.beginTransaction();
        try {
            wipeTable();
            for (ACase c : caseStorage) {
                indexCase(c);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void wipeTable() {
        SqlStorage.wipeTable(db, TABLE_NAME);
    }

}
