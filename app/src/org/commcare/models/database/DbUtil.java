package org.commcare.models.database;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.database.TableBuilder;

import java.io.File;

import androidx.annotation.NonNull;

public class DbUtil {
    private static final String TAG = DbUtil.class.getSimpleName();
    public final static String orphanFileTableName = "OrphanedFiles";

    /**
     * Provides a hook for Sqllite databases to be able to try to migrate themselves in place
     * from the writabledatabase method. Required due to SqlCipher making it incredibly difficult
     * and obnoxious to determine when your databases need an upgrade, so we'll just try to run
     * one any time the method would have crashed anyway.
     *
     * Will crash if this update doesn't work, so no return is needed
     */
    public static void trySqlCipherDbUpdate(String key, Context context, String dbName) {
        //There's no clear way how to tell whether this call is the invalid db version
        //because SqlLite didn't actually provide that info (thanks!), but we can
        //test manually

        //Set up the hook to fire the right pragma ops
        SQLiteDatabaseHook updateHook = new SQLiteDatabaseHook() {

            @Override
            public void preKey(SQLiteDatabase database) {
            }

            @Override
            public void postKey(SQLiteDatabase database) {
                database.rawExecSQL("PRAGMA cipher_migrate;");
            }
        };

        //go find the db path because the helper hides this (thanks android)
        File dbPath = context.getDatabasePath(dbName);

        SQLiteDatabase oldDb = SQLiteDatabase.openOrCreateDatabase(dbPath, key, null, updateHook);

        //if we didn't get here, we didn't crash (what a great way to be testing our db version, right?)
        oldDb.close();
    }

    public static void createNumbersTable(SQLiteDatabase db) {
        //Virtual Table
        String dropStatement = "DROP TABLE IF EXISTS integers;";
        db.execSQL(dropStatement);
        String createStatement = "CREATE TABLE integers (i INTEGER);";
        db.execSQL(createStatement);

        for (long i = 0; i < 10; ++i) {
            db.execSQL("INSERT INTO integers VALUES (" + i + ");");
        }
    }

    public static void explainSql(SQLiteDatabase handle, String sql, String[] args) {
        Cursor explain = handle.rawQuery("EXPLAIN QUERY PLAN " + sql, args);
        Log.d(TAG, "SQL: " + sql);
        DatabaseUtils.dumpCursor(explain);
        explain.close();
    }

    /**
     * Table of files scheduled for deletion. Entries added when file-based
     * database transactions fail or when file-backed entries are removed.
     */
    public static void createOrphanedFileTable(SQLiteDatabase db) {
        String createStatement =
                "CREATE TABLE IF NOT EXISTS "
                        + orphanFileTableName
                        + " (" + DatabaseHelper.FILE_COL + ");";
        db.execSQL(createStatement);
    }

    /**
     * Build and return SQL command to add a column to a table
     */
    public static String addColumnToTable(String tableName, String columnName, String dataType) {
        return "ALTER TABLE " + tableName + " ADD " +
                TableBuilder.scrubName(columnName) + " " + dataType;
    }

    /**
     * Build and return SQL command to add a column with a default value to a table
     */
    public static String addColumnToTable(String tableName, String columnName, String dataType, String defaultValue) {
        return addColumnToTable(tableName, columnName, dataType) + " DEFAULT " + defaultValue;
    }
}
