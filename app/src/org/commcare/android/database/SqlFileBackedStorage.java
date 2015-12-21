package org.commcare.android.database;

import android.database.Cursor;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.modern.database.DatabaseHelper;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.InvalidIndexException;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SqlFileBackedStorage<T extends Persistable> extends SqlStorage<T> {

    public SqlFileBackedStorage(String table, Class<? extends T> ctype, AndroidDbHelper helper) {
        super(table, ctype, helper);
    }

    @Override
    public Vector<T> getRecordsForValues(String[] fieldNames, Object[] values) {
        Pair<String, String[]> whereClause = helper.createWhereAndroid(fieldNames, values, em, null);

        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{DatabaseHelper.FILE_COL}, whereClause.first, whereClause.second, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        try {
            if (c.getCount() == 0) {
                return new Vector<>();
            } else {
                c.moveToFirst();
                Vector<T> indices = new Vector<>();
                int index = c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL);
                while (!c.isAfterLast()) {
                    byte[] data = c.getBlob(index);
                    indices.add(newObject(data));
                    c.moveToNext();
                }
                return indices;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public T getRecordForValues(String[] rawFieldNames, Object[] values) throws NoSuchElementException, InvalidIndexException {
        SQLiteDatabase appDb;
        try {
            appDb = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        Cursor c;
        Pair<String, String[]> whereClause = helper.createWhereAndroid(rawFieldNames, values, em, null);
        c = appDb.query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.FILE_COL}, whereClause.first, whereClause.second, null, null, null);
        try {
            int queryCount = c.getCount();
            if (queryCount  == 0) {
                throw new NoSuchElementException("No element in table " + table + " with names " + Arrays.toString(rawFieldNames) + " and values " + Arrays.toString(values));
            } else if (queryCount > 1) {
                throw new InvalidIndexException("Invalid unique column set" + Arrays.toString(rawFieldNames) + ". Multiple records found with value " + Arrays.toString(values), Arrays.toString(rawFieldNames));
            }
            c.moveToFirst();
            byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
            return newObject(data);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}
