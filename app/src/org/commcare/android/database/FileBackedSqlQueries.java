package org.commcare.android.database;

import android.util.Pair;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.modern.database.DatabaseHelper;

/**
 * Methods for performing column queries on database table that uses files to
 * store serialized object payloads.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FileBackedSqlQueries {

    protected static Pair<String, byte[]> getEntryFilenameAndKey(AndroidDbHelper helper,
                                                                 String table,
                                                                 int id) {
        Cursor c;
        try {
            c = helper.getHandle().query(table, FileBackedSqlStorage.dataColumns,
                    DatabaseHelper.ID_COL + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        try {
            c.moveToFirst();
            return new Pair<>(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL)),
                    c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL)));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected static String getEntryFilename(AndroidDbHelper helper,
                                             String table, int id) {
        Cursor c;
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        String[] columns = new String[]{DatabaseHelper.FILE_COL};
        c = db.query(table, columns, DatabaseHelper.ID_COL + "=?",
                new String[]{String.valueOf(id)}, null, null, null);

        try {
            c.moveToFirst();
            return c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected static byte[] getEntryKey(AndroidDbHelper helper, String table, int id) {
        Cursor c;
        try {
            String[] columns = new String[]{DatabaseHelper.ID_COL, DatabaseHelper.AES_COL};
            c = helper.getHandle().query(table, columns, DatabaseHelper.ID_COL + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        try {
            c.moveToFirst();
            return c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}
