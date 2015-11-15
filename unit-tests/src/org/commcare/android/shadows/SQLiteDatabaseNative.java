package org.commcare.android.shadows;

import android.content.ContentValues;
import android.database.Cursor;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase.CursorFactory;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteStatement;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.Locale;
import java.util.Map;

/**
 * @author ctsims
 */
@Implements(net.sqlcipher.database.SQLiteDatabase.class)
public class SQLiteDatabaseNative {
    private android.database.sqlite.SQLiteDatabase db;
    private static Locale dbLocale;
    
    public void __constructor__(String path, char[] password, CursorFactory factory, int flags) {
        db = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, flags);

        if (dbLocale != null) {
            db.setLocale(dbLocale);
        }
    }
    
    public void __constructor__(String path, char[] password, CursorFactory factory, int flags, SQLiteDatabaseHook hook) {
        db = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, flags);
        if (dbLocale != null) {
            db.setLocale(dbLocale);
        }
    }

    public SQLiteDatabaseNative() {
        
    }

    @Implementation
    public void beginTransaction() {
        db.beginTransaction();
    }

    @Implementation
    public void changePassword(char[] password) throws SQLiteException {
    }

    @Implementation
    public void changePassword(String password) throws SQLiteException {
    }

    @Implementation
    public void close() {

        db.close();
    }

    @Implementation
    public SQLiteStatement compileStatement(String sql) throws SQLException {
        throw new UnsupportedOperationException("Can't use compiled statements on mock db");
    }

    @Implementation
    public int delete(String arg0, String arg1, String[] arg2) {
        return db.delete(arg0, arg1, arg2);
    }

    @Implementation
    public void endTransaction() {
        db.endTransaction();
    }

    @Implementation
    public void execSQL(String arg0, Object[] arg1) throws SQLException {
        db.execSQL(arg0, arg1);
    }

    @Implementation
    public void execSQL(String arg0) throws SQLException {
        db.execSQL(arg0);
    }

    @Implementation
    public synchronized int getMaxSqlCacheSize() {
        return -1;
    }

    @Implementation
    public long getMaximumSize() {
        return db.getMaximumSize();
    }

    @Implementation
    public long getPageSize() {
        return db.getPageSize();
    }

    @Implementation
    public Map<String, String> getSyncedTables() {
        return db.getSyncedTables();
    }

    @Implementation
    public int getVersion() {
        return db.getVersion();
    }

    @Implementation
    public boolean inTransaction() {
        return db.inTransaction();
    }

    @Implementation
    public long insert(String arg0, String arg1, ContentValues arg2) {
        return db.insert(arg0, arg1, arg2);
    }

    @Implementation
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
        return db.insertOrThrow(table, nullColumnHack, values);
    }

    @Implementation
    public long insertWithOnConflict(String arg0, String arg1, ContentValues arg2, int arg3) {
        return db.insertWithOnConflict(arg0, arg1, arg2, arg3);
    }

    @Implementation
    public boolean isDbLockedByCurrentThread() {
        return db.isDbLockedByCurrentThread();
    }

    @Implementation
    public boolean isDbLockedByOtherThreads() {
        return db.isDbLockedByOtherThreads();
    }

    @Implementation
    public boolean isInCompiledSqlCache(String sql) {
        return false;
    }

    @Implementation
    public boolean isOpen() {
        return db.isOpen();
    }

    @Implementation
    public boolean isReadOnly() {
        return db.isReadOnly();
    }

    @Implementation
    public void markTableSyncable(String table, String foreignKey, String updateTable) {
        db.markTableSyncable(table, foreignKey, updateTable);
    }

    @Implementation
    public void markTableSyncable(String table, String deletedTable) {
        db.markTableSyncable(table, deletedTable);
    }

    @Implementation
    public boolean needUpgrade(int newVersion) {
        return db.needUpgrade(newVersion);
    }

    @Implementation
    protected void onAllReferencesReleased() {
    }

    @Implementation
    public void purgeFromCompiledSqlCache(String sql) {
    }

    @Implementation
    public Cursor query(boolean distinct, String table, String[] columns,
            String selection, String[] selectionArgs, String groupBy,
            String having, String orderBy, String limit) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    @Implementation
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having,
            String orderBy, String limit) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    @Implementation
    public Cursor query(String table, String[] columns, String selection,
            String[] selectionArgs, String groupBy, String having,
            String orderBy) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy));
    }

    @Implementation
    public Cursor queryWithFactory(CursorFactory cursorFactory,
            boolean distinct, String table, String[] columns, String selection,
            String[] selectionArgs, String groupBy, String having,
            String orderBy, String limit) {
        throw new UnsupportedOperationException("Can't perform queries with a factor in the mock db");
    }

    @Implementation
    public void rawExecSQL(String arg0) {
        //Mostly Done for Pragma commands and such, just skip and we'll see if it 
        //goes badly
    }

    @Implementation
    public Cursor rawQuery(String sql, String[] selectionArgs, int initialRead,
            int maxRead) {
        throw new UnsupportedOperationException("Mock DB cannot support raw Query with this signature");
    }

    @Implementation
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.rawQuery(sql, selectionArgs));
    }

    @Implementation
    public Cursor rawQueryWithFactory(CursorFactory arg0, String arg1,
            String[] arg2, String arg3) {
        throw new UnsupportedOperationException("Can't perform queries with a factor in the mock db");
    }

    @Implementation
    public long replace(String arg0, String arg1, ContentValues arg2) {
        return db.replace(arg0, arg1, arg2);
    }

    @Implementation
    public long replaceOrThrow(String table, String nullColumnHack, ContentValues initialValues) throws SQLException {

        return db.replaceOrThrow(table, nullColumnHack, initialValues);
    }

    @Implementation
    public void resetCompiledSqlCache() {
    }

    @Implementation
    public void setLocale(Locale locale) {
        if (db == null) {
            dbLocale = locale;
        } else {
            db.setLocale(locale);
        }
    }

    @Implementation
    public void setLockingEnabled(boolean lockingEnabled) {
        db.setLockingEnabled(lockingEnabled);
    }

    @Implementation
    public synchronized void setMaxSqlCacheSize(int cacheSize) {
        db.setMaxSqlCacheSize(cacheSize);
    }

    @Implementation
    public long setMaximumSize(long arg0) {
        return db.setMaximumSize(arg0);
    }

    @Implementation
    public void setPageSize(long numBytes) {
        db.setPageSize(numBytes);
    }

    @Implementation
    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    @Implementation
    public void setVersion(int version) {
        db.setVersion(version);
    }

    @Implementation
    public int status(int operation, boolean reset) {
        throw new UnsupportedOperationException("Mock DB cannot support status operation");
    }

    @Implementation
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return db.update(table, values, whereClause, whereArgs);
    }

    @Implementation
    public int updateWithOnConflict(String arg0, ContentValues arg1,
            String arg2, String[] arg3, int arg4) {
        return db.updateWithOnConflict(arg0, arg1, arg2, arg3, arg4);
    }

    @Implementation
    public boolean yieldIfContended() {
        return db.yieldIfContended();
    }

    @Implementation
    public boolean yieldIfContendedSafely() {
        return db.yieldIfContendedSafely();
    }

    @Implementation
    public boolean yieldIfContendedSafely(long sleepAfterYieldDelay) {
        return db.yieldIfContendedSafely(sleepAfterYieldDelay);
    }
}
