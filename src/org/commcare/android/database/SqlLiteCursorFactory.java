/**
 * 
 */
package org.commcare.android.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * @author ctsims
 *
 */
public class SqlLiteCursorFactory implements CursorFactory {

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteDatabase.CursorFactory#newCursor(android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteCursorDriver, java.lang.String, android.database.sqlite.SQLiteQuery)
	 */
	public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query) {
		return null;
	}

}
