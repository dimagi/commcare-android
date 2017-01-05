package org.commcare.android.shadows;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

/**
 * Wrapper which translates a plain android SQLite cursor 
 * into a SQL-Cipher compatible cursor 
 * 
 * @author ctsims
 *
 */
public class SQLiteCursorNative implements net.sqlcipher.Cursor{
    
    private final android.database.sqlite.SQLiteCursor cursor;

    public SQLiteCursorNative(android.database.sqlite.SQLiteCursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public int getCount() {
        return cursor.getCount();
    }

    @Override
    public int getColumnIndex(String columnName) {
        return cursor.getColumnIndex(columnName);
    }

    @Override
    public String[] getColumnNames() {
        return cursor.getColumnNames();
    }

    @Override
    public void deactivate() {
        cursor.deactivate();
    }

    @Override
    public void close() {
        cursor.close();
    }

    @Override
    public boolean requery() {
        return cursor.requery();
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        return cursor.getBlob(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        return cursor.getString(columnIndex);
    }

    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        cursor.copyStringToBuffer(columnIndex, buffer);
    }

    @Override
    public short getShort(int columnIndex) {
        return cursor.getShort(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        return cursor.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        return cursor.getLong(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) {
        return cursor.getFloat(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) {
        return cursor.getDouble(columnIndex);
    }

    @Override
    public boolean isNull(int columnIndex) {
        return cursor.isNull(columnIndex);
    }
    
    @Override
    public int getType(int columnIndex) {
        return cursor.getType(columnIndex);
    }

    @Override
    public int getColumnCount() {
        return cursor.getColumnCount();
    }

    @Override
    public boolean isClosed() {
        return cursor.isClosed();
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) {
        return cursor.getColumnIndexOrThrow(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return cursor.getColumnName(columnIndex);
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        cursor.registerContentObserver(observer);
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        cursor.unregisterContentObserver(observer);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        cursor.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        cursor.unregisterDataSetObserver(observer);
    }

    @Override
    public void setNotificationUri(ContentResolver cr, Uri notifyUri) {
        cursor.setNotificationUri(cr, notifyUri);
    }

    @Override
    public Uri getNotificationUri() {
        return cursor.getNotificationUri();
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
        return cursor.getWantsAllOnMoveCalls();
    }

    @Override
    public Bundle getExtras() {
        return cursor.getExtras();
    }

    @Override
    public Bundle respond(Bundle extras) {
        return cursor.respond(extras);
    }

    @Override
    public int getPosition() {
        return cursor.getPosition();
    }

    @Override
    public boolean move(int offset) {
        return cursor.move(offset);
    }

    @Override
    public boolean moveToPosition(int position) {
        return cursor.moveToPosition(position);
    }

    @Override
    public boolean moveToFirst() {
        return cursor.moveToFirst();
    }

    @Override
    public boolean moveToLast() {
        return cursor.moveToLast();
    }

    @Override
    public boolean moveToNext() {
        return cursor.moveToNext();
    }

    @Override
    public boolean moveToPrevious() {
        return cursor.moveToPrevious();
    }

    @Override
    public boolean isFirst() {
        return cursor.isFirst();
    }

    @Override
    public boolean isLast() {
        return cursor.isLast();
    }

    @Override
    public boolean isBeforeFirst() {
        return cursor.isBeforeFirst();
    }

    @Override
    public boolean isAfterLast() {
        return cursor.isAfterLast();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void setExtras(Bundle extras) {
        cursor.setExtras(extras);
    }
}
