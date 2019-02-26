package org.commcare.models.database;

import android.content.ContentValues;

import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.modern.util.Pair;
import org.javarosa.core.model.IndexedFixtureIndex;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class IndexedFixturePathUtils {

    public final static String INDEXED_FIXTURE_PATHS_TABLE = "IndexedFixtureIndex";
    public final static String INDEXED_FIXTURE_PATHS_COL_NAME = "name";
    public final static String INDEXED_FIXTURE_PATHS_COL_BASE = "base";
    public final static String INDEXED_FIXTURE_PATHS_COL_CHILD = "child";
    public final static String INDEXED_FIXTURE_PATHS_COL_LAST_SYNC = "last_sync";

    public final static String INDEXED_FIXTURE_PATHS_TABLE_STMT =
            "CREATE TABLE IF NOT EXISTS " +
                    INDEXED_FIXTURE_PATHS_TABLE +
                    " (" + INDEXED_FIXTURE_PATHS_COL_NAME + " UNIQUE" +
                    ", " + INDEXED_FIXTURE_PATHS_COL_BASE +
                    ", " + INDEXED_FIXTURE_PATHS_COL_CHILD +
                    ", " + INDEXED_FIXTURE_PATHS_COL_LAST_SYNC + ");";

    public final static String INDEXED_FIXTURE_INDEXING_STMT =
            DatabaseIndexingUtils.indexOnTableCommand("fixture_name_index",
                    INDEXED_FIXTURE_PATHS_TABLE, INDEXED_FIXTURE_PATHS_COL_NAME);

    public final static String INDEXED_FIXTURE_PATHS_TABLE_STMT_V15 =
            "CREATE TABLE IF NOT EXISTS " +
                    INDEXED_FIXTURE_PATHS_TABLE +
                    " (" + INDEXED_FIXTURE_PATHS_COL_NAME + " UNIQUE" +
                    ", " + INDEXED_FIXTURE_PATHS_COL_BASE +
                    ", " + INDEXED_FIXTURE_PATHS_COL_CHILD + ");";

    public static IndexedFixtureIndex lookupIndexedFixturePaths(SQLiteDatabase db,
                                                                String fixtureName) {
        Cursor c = db.query(INDEXED_FIXTURE_PATHS_TABLE,
                new String[]{INDEXED_FIXTURE_PATHS_COL_BASE, INDEXED_FIXTURE_PATHS_COL_CHILD, INDEXED_FIXTURE_PATHS_COL_LAST_SYNC},
                INDEXED_FIXTURE_PATHS_COL_NAME + "=?", new String[]{fixtureName}, null, null, null);
        try {
            if (c.getCount() == 0) {
                return null;
            } else {
                c.moveToFirst();
                return new IndexedFixtureIndex(fixtureName,
                        c.getString(c.getColumnIndexOrThrow(INDEXED_FIXTURE_PATHS_COL_BASE)),
                        c.getString(c.getColumnIndexOrThrow(INDEXED_FIXTURE_PATHS_COL_CHILD)),
                        c.getString(c.getColumnIndexOrThrow(INDEXED_FIXTURE_PATHS_COL_LAST_SYNC)));
            }
        } finally {
            c.close();
        }
    }

    public static List<String> getAllIndexedFixtureNames(SQLiteDatabase db) {
        Cursor c = db.query(INDEXED_FIXTURE_PATHS_TABLE,
                new String[]{INDEXED_FIXTURE_PATHS_COL_NAME},
                null, null, null, null, null);
        List<String> fixtureNames = new ArrayList<>();
        try {
            if (c.moveToFirst()) {
                int desiredColumnIndex = c.getColumnIndexOrThrow(
                        INDEXED_FIXTURE_PATHS_COL_NAME);
                while (!c.isAfterLast()) {
                    String name = c.getString(desiredColumnIndex);
                    fixtureNames.add(name);
                    c.moveToNext();
                }
            }
            return fixtureNames;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public static Set<String> getAllIndexedFixtureNamesAsSet(SQLiteDatabase db) {
        Set<String> fixtureNamesAsSet = new HashSet<>();
        fixtureNamesAsSet.addAll(getAllIndexedFixtureNames(db));
        return fixtureNamesAsSet;
    }

    public static void insertIndexedFixturePathBases(SQLiteDatabase db, String fixtureName,
                                                     String baseName, String childName, String lastSync) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(INDEXED_FIXTURE_PATHS_COL_BASE, baseName);
        contentValues.put(INDEXED_FIXTURE_PATHS_COL_CHILD, childName);
        contentValues.put(INDEXED_FIXTURE_PATHS_COL_NAME, fixtureName);
        contentValues.put(INDEXED_FIXTURE_PATHS_COL_LAST_SYNC, lastSync);

        db.beginTransaction();
        try {
            long ret = db.insertWithOnConflict(
                    INDEXED_FIXTURE_PATHS_TABLE,
                    INDEXED_FIXTURE_PATHS_COL_BASE,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE);

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void createStorageBackedFixtureIndexTable(SQLiteDatabase db) {
        db.execSQL(INDEXED_FIXTURE_PATHS_TABLE_STMT);
        db.execSQL(INDEXED_FIXTURE_INDEXING_STMT);
    }

    public static void createStorageBackedFixtureIndexTableV15(SQLiteDatabase db) {
        db.execSQL(INDEXED_FIXTURE_PATHS_TABLE_STMT_V15);
        db.execSQL(INDEXED_FIXTURE_INDEXING_STMT);
    }

    public static void buildFixtureIndices(SQLiteDatabase database,
                                           String tableName,
                                           Set<String> indices) {
        database.beginTransaction();
        try {
            for (String indexStmt : DatabaseIndexingUtils.getIndexStatements(tableName, indices)) {
                database.execSQL(indexStmt);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
