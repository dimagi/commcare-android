/**
 * 
 */
package org.commcare.android.database;

import java.util.Hashtable;

import javax.crypto.Cipher;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * @author ctsims
 *
 */
public class CommCareDBCursorFactory implements CursorFactory {
	
	private Hashtable<String, EncryptedModel> models;
	private Cipher cipher;
	
	/**
	 * Creates a cursor factory which is incapable of dealing with 
	 * Encrypted data
	 */
	public CommCareDBCursorFactory() {
		
	}
	
	public CommCareDBCursorFactory(Hashtable<String, EncryptedModel> models, Cipher cipher) {
		this.models = models;
		this.cipher = cipher;
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteDatabase.CursorFactory#newCursor(android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteCursorDriver, java.lang.String, android.database.sqlite.SQLiteQuery)
	 */
	public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query) {
		if(models == null || !models.containsKey(editTable)) {
			return new SQLiteCursor(db, masterQuery, editTable, query);
		} else {
			EncryptedModel model = models.get(editTable);
			return new DecryptingCursor(db, masterQuery, editTable, query, model, cipher);
		}
	} 
}
