package org.commcare.android.shadows;

import java.util.Locale;
import java.util.Map;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase.CursorFactory;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteStatement;

import org.robolectric.annotation.Implements;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * @author ctsims
 *
 */
@Implements(net.sqlcipher.database.SQLiteDatabase.class)
public class SQLiteDatabaseNative {
    private android.database.sqlite.SQLiteDatabase db;
    
    public void __constructor__(String path, char[] password, CursorFactory factory, int flags) {
        db = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, flags);
    }
    
    public void __constructor__(String path, char[] password, CursorFactory factory, int flags, SQLiteDatabaseHook hook) {
        db = android.database.sqlite.SQLiteDatabase.openDatabase(path, null, flags);
    }

    public SQLiteDatabaseNative() {
        
    }

    //@Override
    public void beginTransaction() {
        db.beginTransaction();
    }

    //@Override
    public void changePassword(char[] password) throws SQLiteException {
    }

    //@Override
    public void changePassword(String password) throws SQLiteException {
    }

    //@Override
    public void close() {

        db.close();
    }

    //@Override
    public SQLiteStatement compileStatement(String sql) throws SQLException {
        throw new UnsupportedOperationException("Can't use compiled statements on mock db");
    }

    //@Override
    public int delete(String arg0, String arg1, String[] arg2) {
        return db.delete(arg0, arg1, arg2);
    }

    //@Override
    public void endTransaction() {
        db.endTransaction();
    }

    //@Override
    public void execSQL(String arg0, Object[] arg1) throws SQLException {
        db.execSQL(arg0, arg1);
    }

    //@Override
    public void execSQL(String arg0) throws SQLException {
        db.execSQL(arg0);
    }

    //@Override
    public synchronized int getMaxSqlCacheSize() {
        return -1;
    }

    //@Override
    public long getMaximumSize() {
        return db.getMaximumSize();
    }

    //@Override
    public long getPageSize() {
        return db.getPageSize();
    }

    //@Override
    public Map<String, String> getSyncedTables() {
        return db.getSyncedTables();
    }

    //@Override
    public int getVersion() {
        return db.getVersion();
    }

    //@Override
    public boolean inTransaction() {
        return db.inTransaction();
    }

    //@Override
    public long insert(String arg0, String arg1, ContentValues arg2) {
        return db.insert(arg0, arg1, arg2);
    }

    //@Override
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
        return db.insertOrThrow(table, nullColumnHack, values);
    }

    //@Override
    public long insertWithOnConflict(String arg0, String arg1, ContentValues arg2, int arg3) {
        return db.insertWithOnConflict(arg0, arg1, arg2, arg3);
    }

    //@Override
    public boolean isDbLockedByCurrentThread() {
        return db.isDbLockedByCurrentThread();
    }

    //@Override
    public boolean isDbLockedByOtherThreads() {
        return db.isDbLockedByOtherThreads();
    }

    //@Override
    public boolean isInCompiledSqlCache(String sql) {
        return false;
    }

    //@Override
    public boolean isOpen() {
        return db.isOpen();
    }

    //@Override
    public boolean isReadOnly() {
        return db.isReadOnly();
    }

    //@Override
    public void markTableSyncable(String table, String foreignKey, String updateTable) {
        db.markTableSyncable(table, foreignKey, updateTable);
    }

    //@Override
    public void markTableSyncable(String table, String deletedTable) {
        db.markTableSyncable(table, deletedTable);
    }

    //@Override
    public boolean needUpgrade(int newVersion) {
        return db.needUpgrade(newVersion);
    }

    //@Override
    protected void onAllReferencesReleased() {
    }

    //@Override
    public void purgeFromCompiledSqlCache(String sql) {
    }

    //@Override
    public Cursor query(boolean distinct, String table, String[] columns,
            String selection, String[] selectionArgs, String groupBy,
            String having, String orderBy, String limit) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    //@Override
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having,
            String orderBy, String limit) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    //@Override
    public Cursor query(String table, String[] columns, String selection,
            String[] selectionArgs, String groupBy, String having,
            String orderBy) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy));
    }

    //@Override
    public Cursor queryWithFactory(CursorFactory cursorFactory,
            boolean distinct, String table, String[] columns, String selection,
            String[] selectionArgs, String groupBy, String having,
            String orderBy, String limit) {
        throw new UnsupportedOperationException("Can't perform queries with a factor in the mock db");
    }

    //@Override
    public void rawExecSQL(String arg0) {
        //Mostly Done for Pragma commands and such, just skip and we'll see if it 
        //goes badly
    }

    //@Override
    public Cursor rawQuery(String sql, String[] selectionArgs, int initialRead,
            int maxRead) {
        throw new UnsupportedOperationException("Mock DB cannot support raw Query with this signature");
    }

    //@Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return new SQLiteCursorNative((android.database.sqlite.SQLiteCursor)db.rawQuery(sql, selectionArgs));
    }

    //@Override
    public Cursor rawQueryWithFactory(CursorFactory arg0, String arg1,
            String[] arg2, String arg3) {
        throw new UnsupportedOperationException("Can't perform queries with a factor in the mock db");
    }

    //@Override
    public long replace(String arg0, String arg1, ContentValues arg2) {
        return db.replace(arg0, arg1, arg2);
    }

    //@Override
    public long replaceOrThrow(String table, String nullColumnHack, ContentValues initialValues) throws SQLException {

        return db.replaceOrThrow(table, nullColumnHack, initialValues);
    }

    //@Override
    public void resetCompiledSqlCache() {
    }

    //@Override
    public void setLocale(Locale locale) {
        db.setLocale(locale);
    }

    //@Override
    public void setLockingEnabled(boolean lockingEnabled) {
        db.setLockingEnabled(lockingEnabled);
    }

    //@Override
    public synchronized void setMaxSqlCacheSize(int cacheSize) {
        db.setMaxSqlCacheSize(cacheSize);
    }

    //@Override
    public long setMaximumSize(long arg0) {
        return db.setMaximumSize(arg0);
    }

    //@Override
    public void setPageSize(long numBytes) {
        db.setPageSize(numBytes);
    }

    //@Override
    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    //@Override
    public void setVersion(int version) {
        db.setVersion(version);
    }

    //@Override
    public int status(int operation, boolean reset) {
        throw new UnsupportedOperationException("Mock DB cannot support status operation");
    }

    //@Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return db.update(table, values, whereClause, whereArgs);
    }

    //@Override
    public int updateWithOnConflict(String arg0, ContentValues arg1,
            String arg2, String[] arg3, int arg4) {
        return db.updateWithOnConflict(arg0, arg1, arg2, arg3, arg4);
    }

    //@Override
    public boolean yieldIfContended() {
        return db.yieldIfContended();
    }

    //@Override
    public boolean yieldIfContendedSafely() {
        return db.yieldIfContendedSafely();
    }

    //@Override
    public boolean yieldIfContendedSafely(long sleepAfterYieldDelay) {
        return db.yieldIfContendedSafely(sleepAfterYieldDelay);
    }
}
