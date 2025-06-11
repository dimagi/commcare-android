package org.commcare.models.database;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * Interface to abstracts database operations to allow for different  and heterogeneous implementations, such as
 * SQLCipher and SQLite databases.
 */
public interface IDatabase {

    Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy,
                 String having, String orderBy);

    void beginTransaction();

    void setTransactionSuccessful();

    void endTransaction();

    boolean isOpen();

    int delete(String table, String whereClause, String[] whereArgs);

    void close();

    long insertOrThrow(String table, String nullColumnHack, ContentValues values);

    Cursor rawQuery(String sql, String[] selectionArgs);

    void execSQL(String sql);

    long runCompileStatement(String sql);

    void update(String table, ContentValues values, String whereClause, String[] whereArgs);

    void insert(String table, String nullColumnHack, ContentValues values);

    long insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues,
                              int conflictAlgorithm);

    void yieldIfContendedSafely();

    void setVersion(int dbVersion);

    String buildQueryString(boolean distinct, String tables, String[] columns, String where, String groupBy,
                            String having, String orderBy, String limit);

    int getConflictReplace();
}
