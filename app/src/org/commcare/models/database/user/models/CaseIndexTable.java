package org.commcare.models.database.user.models;

import android.content.ContentValues;
import android.util.Pair;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.cases.model.CaseIndex;
import org.commcare.cases.query.queryset.DualTableSingleMatchModelQuerySet;
import org.commcare.models.database.AndroidTableBuilder;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.database.DatabaseIndexingUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Vector;

/**
 * @author ctsims
 */
public class CaseIndexTable {
    private static final String TABLE_NAME = "case_index_storage";

    private static final String COL_CASE_RECORD_ID = "case_rec_id";
    private static final String COL_INDEX_NAME = "name";
    private static final String COL_INDEX_TYPE = "type";
    private static final String COL_INDEX_TARGET = "target";

    private final SQLiteDatabase db;

    //TODO: We should do some synchronization to make it the case that nothing can hold
    //an object for the same cache at once and let us manage the lifecycle

    public CaseIndexTable() {
        this.db = CommCareApplication.instance().getUserDbHandle();
    }

    public CaseIndexTable(SQLiteDatabase dbHandle) {
        this.db = dbHandle;
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

    public static void createIndexes(SQLiteDatabase db) {
        String recordFirstIndexId = "RECORD_NAME_ID_TARGET";
        String recordFirstIndex = COL_CASE_RECORD_ID + ", " + COL_INDEX_NAME + ", " + COL_INDEX_TARGET;
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand(recordFirstIndexId, TABLE_NAME, recordFirstIndex));

        String typeFirstIndexId = "NAME_TARGET_RECORD";
        String typeFirstIndex = COL_INDEX_NAME + ", " + COL_CASE_RECORD_ID + ", " + COL_INDEX_TARGET;
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand(typeFirstIndexId, TABLE_NAME, typeFirstIndex));
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
        clearCaseIndices(c.getID());
    }

    public void clearCaseIndices(int recordId) {
        String recordIdString = String.valueOf(recordId);
        db.beginTransaction();
        try {
            if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
                String sqlStatement = String.format("DELETE FROM %s WHERE %s = CAST(? as INT)", TABLE_NAME, COL_CASE_RECORD_ID);
                DbUtil.explainSql(db, sqlStatement, new String[]{recordIdString});
            }
            //NOTE: The cast is very necessary, SQLite's type coercion has problems here because
            //we can't provide arguments in any format other than a string
            db.delete(TABLE_NAME, COL_CASE_RECORD_ID + "= CAST(? as INT)", new String[]{recordIdString});
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
    public LinkedHashSet<Integer> getCasesMatchingIndex(String indexName, String targetValue) {
        String[] args = new String[]{indexName, targetValue};
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            String query = String.format("SELECT %s FROM %s WHERE %s = ? AND %s = ?", COL_CASE_RECORD_ID, TABLE_NAME, COL_INDEX_NAME, COL_INDEX_TARGET);
            DbUtil.explainSql(db, query, args);
        }
        Cursor c = db.query(TABLE_NAME, new String[]{COL_CASE_RECORD_ID}, COL_INDEX_NAME + " = ? AND " + COL_INDEX_TARGET + " =  ?", args, null, null, null);
        LinkedHashSet<Integer> ret = new LinkedHashSet<>();
        SqlStorage.fillIdWindow(c, COL_CASE_RECORD_ID, ret);
        return ret;
    }

    /**
     * Get a list of Case Record id's for cases which index any of a set of provided values
     *
     * @param indexName      The name of the index
     * @param targetValueSet The set of cases targeted by the index
     * @return An integer array of indexed case record ids
     */
    public LinkedHashSet<Integer> getCasesMatchingValueSet(String indexName, String[] targetValueSet) {
        String[] args = new String[1 + targetValueSet.length];
        args[0] = indexName;
        for (int i = 0; i < targetValueSet.length; ++i) {
            args[i + 1] = targetValueSet[i];
        }
        String inSet = getArgumentBasedVariableSet(targetValueSet.length);

        String whereExpr = String.format("%s = ? AND %s IN %s", COL_INDEX_NAME, COL_INDEX_TARGET, inSet);

        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            String query = String.format("SELECT %s FROM %s WHERE %s", COL_CASE_RECORD_ID, TABLE_NAME, whereExpr);
            DbUtil.explainSql(db, query, args);
        }

        Cursor c = db.query(TABLE_NAME, new String[]{COL_CASE_RECORD_ID}, whereExpr, args, null, null, null);
        LinkedHashSet<Integer> ret = new LinkedHashSet<>();

        SqlStorage.fillIdWindow(c, COL_CASE_RECORD_ID, ret);
        return ret;
    }

    public int loadIntoIndexTable(HashMap<String, Vector<Integer>> indexCache, String indexName) {
        int resultsReturned = 0;
        String[] args = new String[]{indexName};
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            String query = String.format("SELECT %s,%s %s FROM %s where %s = '%s'", COL_CASE_RECORD_ID, COL_INDEX_NAME, COL_INDEX_TARGET, TABLE_NAME, COL_INDEX_NAME, indexName);
            DbUtil.explainSql(db, query, null);
        }

        Cursor c = db.query(TABLE_NAME, new String[]{COL_CASE_RECORD_ID, COL_INDEX_NAME, COL_INDEX_TARGET},COL_INDEX_NAME + " = ?", args, null, null, null);

        try {
            if (c.moveToFirst()) {
                while (!c.isAfterLast()) {
                    resultsReturned++;
                    int id = c.getInt(c.getColumnIndexOrThrow(COL_CASE_RECORD_ID));
                    String target = c.getString(c.getColumnIndexOrThrow(COL_INDEX_TARGET));

                    String cacheID = indexName + "|" + target;
                    Vector<Integer> cache;
                    if(indexCache.containsKey(cacheID)){
                        cache = indexCache.get(cacheID);
                    } else {
                        cache = new Vector<>();
                    }
                    cache.add(id);
                    indexCache.put(cacheID, cache);
                    c.moveToNext();
                }
            }

            return resultsReturned;
        } finally {
            if (c != null) {
                c.close();
            }
        }

    }

    /**
     * Provided an index name and a list of case row ID's, provides a list of the row ID's of the
     * cases which point to that ID
     * @param cuedCases
     * @return
     */
    public DualTableSingleMatchModelQuerySet bulkReadIndexToCaseIdMatch(String indexName, Collection<Integer> cuedCases) {
        DualTableSingleMatchModelQuerySet set = new DualTableSingleMatchModelQuerySet();
        String caseIdIndex = AndroidTableBuilder.scrubName(Case.INDEX_CASE_ID);

        List<Pair<String, String[]>> whereParamList = AndroidTableBuilder.sqlList(cuedCases, "CAST(? as INT)");
        for(Pair<String, String[]> querySet : whereParamList) {

            String query =String.format(
                    "SELECT %s,%s " +
                            "FROM %s " +
                            "INNER JOIN %s " +
                            "ON %s = %s " +
                            "WHERE %s = '%s' " +
                            "AND " +
                            "%s IN %s",

                    COL_CASE_RECORD_ID, ACase.STORAGE_KEY + "." + DatabaseHelper.ID_COL,
                    TABLE_NAME,
                    ACase.STORAGE_KEY,
                    COL_INDEX_TARGET, caseIdIndex,
                    COL_INDEX_NAME, indexName,
                    COL_CASE_RECORD_ID, querySet.first);

            android.database.Cursor c = db.rawQuery(query, querySet.second);

            try {
                if (c.getCount() == 0) {
                    return set;
                } else {
                    c.moveToFirst();
                    while (!c.isAfterLast()) {
                        int caseId = c.getInt(c.getColumnIndexOrThrow(COL_CASE_RECORD_ID));
                        int targetCase = c.getInt(c.getColumnIndex(DatabaseHelper.ID_COL));
                        set.loadResult(caseId, targetCase);
                        c.moveToNext();
                    }
                }
            } finally {
                c.close();
            }
        }
        return set;
    }



    public static String getArgumentBasedVariableSet(int number) {
        StringBuffer sb = new StringBuffer();
        sb.append("(");
            for (int i = 0; i < number; i++) {
            sb.append('?');
            if (i < number - 1) {
                sb.append(",");
            }
        }
        sb.append(")");
        return sb.toString();
    }

}
