package org.commcare.models.database;

import android.content.ContentValues;

import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.modern.database.IndexedFixturePathsConstants;
import org.commcare.modern.util.Pair;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.Set;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class IndexedFixturePathUtils {

    public static Pair<String, String> lookupIndexedFixturePaths(SQLiteDatabase db,
                                                                 String fixtureName) {
        Cursor c = db.query(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_TABLE,
                new String[]{IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_BASE, IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_CHILD},
                IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_NAME + "=?", new String[]{fixtureName}, null, null, null);
        try {
            if (c.getCount() == 0) {
                return null;
            } else {
                c.moveToFirst();
                return Pair.create(
                        c.getString(c.getColumnIndexOrThrow(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_BASE)),
                        c.getString(c.getColumnIndexOrThrow(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_CHILD)));
            }
        } finally {
            c.close();
        }
    }

    public static void insertIndexedFixturePathBases(SQLiteDatabase db, String fixtureName,
                                                     String baseName, String childName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_BASE, baseName);
        contentValues.put(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_CHILD, childName);
        contentValues.put(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_NAME, fixtureName);

        try {
            db.beginTransaction();

            long ret = db.insertOrThrow(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_TABLE,
                    IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_COL_BASE,
                    contentValues);

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void createStorageBackedFixtureIndexTable(SQLiteDatabase db) {
        db.execSQL(IndexedFixturePathsConstants.INDEXED_FIXTURE_PATHS_TABLE_STMT);
        db.execSQL(IndexedFixturePathsConstants.INDEXED_FIXTURE_INDEXING_STMT);
    }

    public static void buildFixtureIndices(SQLiteDatabase database,
                                           String tableName,
                                           Set<String> indices) {
        try {
            database.beginTransaction();
            for (String indexStmt : DatabaseIndexingUtils.getIndexStatements(tableName, indices)) {
                database.execSQL(indexStmt);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
