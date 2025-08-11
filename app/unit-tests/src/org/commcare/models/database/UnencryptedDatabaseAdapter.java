package org.commcare.models.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;


public class UnencryptedDatabaseAdapter implements IDatabase {
    private SQLiteDatabase db;

    public UnencryptedDatabaseAdapter(SQLiteOpenHelper dbOpenHelper) {
        this.db = dbOpenHelper.getWritableDatabase();
    }

    public UnencryptedDatabaseAdapter(SQLiteDatabase db) {
        this.db = db;
    }

    public UnencryptedDatabaseAdapter(String path) {
        this.db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE, null);
    }

    @Override
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }

    @Override
    public void beginTransaction() {
        db.beginTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    @Override
    public void endTransaction() {
        db.endTransaction();
    }

    @Override
    public boolean isOpen() {
        return db.isOpen();
    }

    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        return db.delete(table, whereClause, whereArgs);
    }

    @Override
    public void close() {
        db.close();
    }

    @Override
    public long insertOrThrow(String table, String dataCol, ContentValues contentValues) {
        return db.insertOrThrow(table, dataCol, contentValues);
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return db.rawQuery(sql, selectionArgs);
    }

    @Override
    public void execSQL(String sql) {
        db.execSQL(sql);
    }

    @Override
    public void rawExecSQL(String sql) {
        execSQL(sql);
    }

    @Override
    public long runCompileStatement(String sql) {
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement(sql);
            return stmt.simpleQueryForLong();
        } finally{
            stmt.close();
        }
    }

    @Override
    public void update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        db.update(table, values, whereClause, whereArgs);
    }

    @Override
    public void insert(String table, String nullColumnHack, ContentValues values) {
        db.insert(table, nullColumnHack, values);
    }

    @Override
    public long insertWithOnConflict(String table, String nullColumnHack,
                                     ContentValues initialValues, int conflictAlgorithm) {
        return db.insertWithOnConflict(table, nullColumnHack, initialValues, conflictAlgorithm);
    }

    @Override
    public void yieldIfContendedSafely() {
        db.yieldIfContendedSafely();
    }

    @Override
    public void setVersion(int dbVersion) {
        db.setVersion(dbVersion);
    }

    @Override
    public String buildQueryString(boolean distinct, String tables, String[] columns, String where,
                                   String groupBy, String having, String orderBy, String limit) {
        return SQLiteQueryBuilder.buildQueryString(distinct, tables, columns, where, groupBy, having, orderBy, limit);
    }

    @Override
    public int getConflictReplace() {
        return SQLiteDatabase.CONFLICT_REPLACE;
    }
}
