/**
 * 
 */
package org.commcare.android.db.legacy;

import java.util.Hashtable;

import net.sqlcipher.database.SQLiteCursor;
import net.sqlcipher.database.SQLiteCursorDriver;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabase.CursorFactory;
import net.sqlcipher.database.SQLiteQuery;

import org.commcare.android.crypt.CipherPool;
import org.commcare.android.database.EncryptedModel;
import org.commcare.android.util.SessionUnavailableException;

import android.database.Cursor;

/**
 * @author ctsims
 *
 */
public class LegacyCommCareDBCursorFactory implements CursorFactory {
	
	private Hashtable<String, EncryptedModel> models;
	
	/**
	 * Creates a cursor factory which is incapable of dealing with 
	 * Encrypted data
	 */
	public LegacyCommCareDBCursorFactory() {
		
	}
	
	public LegacyCommCareDBCursorFactory(Hashtable<String, EncryptedModel> models) {
		this.models = models;
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteDatabase.CursorFactory#newCursor(android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteCursorDriver, java.lang.String, android.database.sqlite.SQLiteQuery)
	 */
	public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query) throws SessionUnavailableException{
		if(models == null || !models.containsKey(editTable)) {
			return new SQLiteCursor(db, masterQuery, editTable, query);
		} else {
			EncryptedModel model = models.get(editTable);
			return new DecryptingCursor(db, masterQuery, editTable, query, model, getCipherPool());
		}
	} 
	
	protected CipherPool getCipherPool() throws SessionUnavailableException {
		return null;
	}
}
