package org.commcare.models.database;

import android.content.ContentValues;

import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.modern.util.Pair;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.Set;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class IndexedFixturePathUtils {
    private final static String INDEXED_FIXTURE_INDEX_TABLE = "IndexedFixtureIndex";
    private final static String INDEXED_FIXTURE_INDEX_COL_NAME = "name";
    private final static String INDEXED_FIXTURE_INDEX_COL_BASE = "base";
    private final static String INDEXED_FIXTURE_INDEX_COL_CHILD = "child";

    public static Pair<String, String> lookupIndexedFixturePaths(SQLiteDatabase db, String fixtureName) {
        Cursor c = db.query(INDEXED_FIXTURE_INDEX_TABLE,
                new String[]{INDEXED_FIXTURE_INDEX_COL_BASE, INDEXED_FIXTURE_INDEX_COL_CHILD},
                INDEXED_FIXTURE_INDEX_COL_NAME + "=?", new String[]{fixtureName}, null, null, null);
        try {
            if (c.getCount() == 0) {
                return null;
            } else {
                c.moveToFirst();
                return Pair.create(
                        c.getString(c.getColumnIndexOrThrow(INDEXED_FIXTURE_INDEX_COL_BASE)),
                        c.getString(c.getColumnIndexOrThrow(INDEXED_FIXTURE_INDEX_COL_CHILD)));
            }
        } finally {
            c.close();
        }
    }

    public static void insertIndexedFixturePathBases(SQLiteDatabase db, String fixtureName,
                                                     String baseName, String childName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(INDEXED_FIXTURE_INDEX_COL_BASE, baseName);
        contentValues.put(INDEXED_FIXTURE_INDEX_COL_CHILD, childName);
        contentValues.put(INDEXED_FIXTURE_INDEX_COL_NAME, fixtureName);

        try {
            db.beginTransaction();

            long ret = db.insertOrThrow(INDEXED_FIXTURE_INDEX_TABLE,
                    INDEXED_FIXTURE_INDEX_COL_BASE,
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
        String createStatement = "CREATE TABLE IF NOT EXISTS " +
                INDEXED_FIXTURE_INDEX_TABLE +
                " (" + INDEXED_FIXTURE_INDEX_COL_NAME +
                ", " + INDEXED_FIXTURE_INDEX_COL_BASE +
                ", " + INDEXED_FIXTURE_INDEX_COL_CHILD + ");";
        db.execSQL(createStatement);

        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("fixture_name_index", INDEXED_FIXTURE_INDEX_TABLE, INDEXED_FIXTURE_INDEX_COL_NAME));
    }

    public static void buildFixtureIndices(SQLiteDatabase database,
                                           String tableName,
                                           Set<String> indices) {
        try {
            database.beginTransaction();

            for (String index : indices) {
                String indexName = index + "_index";
                if (index.contains(",")) {
                    indexName = index.replaceAll(",", "_") + "_index";
                }
                database.execSQL(DatabaseAppOpenHelper.indexOnTableCommand(indexName, tableName, index));
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
