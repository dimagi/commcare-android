package org.commcare.android.db.legacy;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;

import org.commcare.android.crypt.CipherPool;
import org.commcare.modern.models.EncryptedModel;

import java.util.Hashtable;

/**
 * @author ctsims
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

    @Override
    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query) {
        if (models == null || !models.containsKey(editTable)) {
            return new SQLiteCursor(db, masterQuery, editTable, query);
        } else {
            EncryptedModel model = models.get(editTable);
            return new DecryptingCursor(db, masterQuery, editTable, query, model, getCipherPool());
        }
    }

    protected CipherPool getCipherPool() {
        return null;
    }
}
