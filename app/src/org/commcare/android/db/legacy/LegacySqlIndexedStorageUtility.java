package org.commcare.android.db.legacy;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.InvalidIndexException;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.Externalizable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * @author ctsims
 */
public class LegacySqlIndexedStorageUtility<T extends Persistable> extends SqlStorage<T> {

    final String table;
    final Class<? extends T> ctype;
    EncryptedModel em;
    T t;
    final LegacyDbHelper helper;

    public LegacySqlIndexedStorageUtility(String table, Class<? extends T> ctype, LegacyDbHelper helper) {
        this.table = table;
        this.ctype = ctype;
        this.helper = helper;

        try {
            T e = ctype.newInstance();
            if (e instanceof EncryptedModel) {
                em = (EncryptedModel) e;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Vector<Integer> getIDsForValue(String fieldName, Object value) {
        return getIDsForValues(new String[]{fieldName}, new Object[]{value});
    }

    public Vector<Integer> getIDsForValues(String[] fieldNames, Object[] values) {
        Pair<String, String[]> whereClause = helper.createWhere(fieldNames, values, em, t);
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL}, whereClause.first, whereClause.second, null, null, null);
        if (c.getCount() == 0) {
            c.close();
            return new Vector<>();
        } else {
            c.moveToFirst();
            Vector<Integer> indices = new Vector<Integer>();
            int index = c.getColumnIndexOrThrow(DatabaseHelper.ID_COL);
            while (!c.isAfterLast()) {
                int id = c.getInt(index);
                indices.add(Integer.valueOf(id));
                c.moveToNext();
            }
            c.close();
            return indices;
        }
    }

    public Vector<T> getRecordsForValues(String[] fieldNames, Object[] values) {
        Pair<String, String[]> whereClause = helper.createWhere(fieldNames, values, em, t);
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        if (c.getCount() == 0) {
            c.close();
            return new Vector<>();
        } else {
            c.moveToFirst();
            Vector<T> indices = new Vector<T>();
            int index = c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL);
            while (!c.isAfterLast()) {
                byte[] data = c.getBlob(index);
                indices.add(newObject(data, c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL))));
                c.moveToNext();
            }
            c.close();
            return indices;
        }
    }

    public String getMetaDataFieldForRecord(int recordId, String rawFieldName) {
        String rid = String.valueOf(recordId);
        String scrubbedName = AndroidTableBuilder.scrubName(rawFieldName);
        Cursor c = helper.getHandle().query(table, new String[] {scrubbedName} , DatabaseHelper.ID_COL + "=?", new String[] {rid}, null, null, null);
        if(c.getCount() == 0) {
            c.close();
            throw new NoSuchElementException("No record in table " + table + " for ID " + recordId);
        }
        c.moveToFirst();
        String result = c.getString(c.getColumnIndexOrThrow(scrubbedName));
        c.close();
        return result;

    }

    @Override
    public T getRecordForValues(String[] rawFieldNames, Object[] values) throws NoSuchElementException, InvalidIndexException {
        Pair<String, String[]> whereClause = helper.createWhere(rawFieldNames, values, em, t);
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        if (c.getCount() == 0) {
            throw new NoSuchElementException("No element in table " + table + " with names " + rawFieldNames + " and values " + values.toString());
        }
        if (c.getCount() > 1) {
            throw new InvalidIndexException("Invalid unique column set" + rawFieldNames + ". Multiple records found with value " + values.toString(), rawFieldNames.toString());
        }
        c.moveToFirst();
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
        int dbId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
        c.close();
        return newObject(data, dbId);
    }

    @Override
    public T getRecordForValue(String rawFieldName, Object value) throws NoSuchElementException, InvalidIndexException {
        Pair<String, String[]> whereClause = helper.createWhere(new String[] {rawFieldName}, new Object[] {value}, em, t);
        String scrubbedName = AndroidTableBuilder.scrubName(rawFieldName);
        Cursor c = helper.getHandle().query(table, new String[] {DatabaseHelper.DATA_COL} ,whereClause.first, whereClause.second, null, null, null);
        if(c.getCount() == 0) {
            c.close();
            throw new NoSuchElementException("No element in table " + table + " with name " + scrubbedName + " and value " + value.toString());
        }
        if (c.getCount() > 1) {
            c.close();
            throw new InvalidIndexException("Invalid unique column " + scrubbedName + ". Multiple records found with value " + value.toString(), scrubbedName);
        }
        c.moveToFirst();
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
        int dbId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
        c.close();
        return newObject(data, dbId);
    }

    @Override
    public T newObject(byte[] serializedObjectAsBytes, int dbEntryId) {
        try {
            T e = ctype.newInstance();
            e.readExternal(new DataInputStream(new ByteArrayInputStream(serializedObjectAsBytes)), helper.getPrototypeFactory());
            e.setID(dbEntryId);

            return e;
        } catch (DeserializationException | IOException
                | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int add(Externalizable e) {
        SQLiteDatabase db = helper.getHandle();
        int i = -1;
        try {
            db.beginTransaction();
            long ret = db.insertOrThrow(table, DatabaseHelper.DATA_COL, helper.getContentValues(e));

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            i = (int) ret;

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return i;
    }

    @Override
    public void close() {
        helper.getHandle().close();
    }

    @Override
    public void destroy() {
        //nothing;
    }

    @Override
    public boolean exists(int id) {
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL}, DatabaseHelper.ID_COL + "= ? ", new String[]{String.valueOf(id)}, null, null, null);
        if (c.getCount() == 0) {
            c.close();
            return false;
        }
        if (c.getCount() > 1) {
            c.close();
            throw new InvalidIndexException("Invalid ID column. Multiple records found with value " + id, "ID");
        }
        c.close();
        return true;
    }

    @Override
    public net.sqlcipher.database.SQLiteDatabase getAccessLock() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getNumRecords() {
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL}, null, null, null, null, null);
        int records = c.getCount();
        c.close();
        return records;
    }

    @Override
    public int getRecordSize(int id) {
        //serialize and test blah blah.
        return 0;
    }

    @Override
    public int getTotalSize() {
        //serialize and test blah blah.
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return getNumRecords() == 0;
    }

    @Override
    public SqlStorageIterator<T> iterate() {
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL}, null, null, null, null, DatabaseHelper.ID_COL);
        return new SqlStorageIterator<>(c, this);
    }

    public Iterator<T> iterator() {
        return iterate();
    }

    @Override
    public T read(int id) {
        return newObject(readBytes(id), id);
    }

    @Override
    public byte[] readBytes(int id) {
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL}, DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)}, null, null, null);

        c.moveToFirst();
        byte[] blob = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
        c.close();
        return blob;
    }

    @Override
    public void remove(int id) {
        SQLiteDatabase db = helper.getHandle();
        db.beginTransaction();
        try {
            db.delete(table, DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void remove(List ids) {
        if (ids.size() == 0) {
            return;
        }
        SQLiteDatabase db = helper.getHandle();
        db.beginTransaction();
        try {
            List<Pair<String, String[]>> whereParamList = AndroidTableBuilder.sqlList(ids);
            for(Pair<String, String[]> whereParams : whereParamList) {
                int rowsRemoved = db.delete(table, DatabaseHelper.ID_COL +" IN " + whereParams.first, whereParams.second);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void remove(Persistable p) {
        this.remove(p.getID());
    }

    @Override
    public void removeAll() {
        SQLiteDatabase db = helper.getHandle();
        db.beginTransaction();
        try {
            db.delete(table, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public Vector<Integer> removeAll(EntityFilter ef) {
        Vector<Integer> removed = new Vector<>();
        for (IStorageIterator iterator = this.iterate(); iterator.hasMore(); ) {
            int id = iterator.nextID();
            switch (ef.preFilter(id, null)) {
                case EntityFilter.PREFILTER_INCLUDE:
                    removed.add(id);
                    continue;
                case EntityFilter.PREFILTER_EXCLUDE:
                    continue;
            }
            if (ef.matches(read(id))) {
                removed.add(id);
            }
        }
        
        if(removed.size() == 0) { return removed; }
        
        List<Pair<String, String[]>> whereParamList = AndroidTableBuilder.sqlList(removed);


        SQLiteDatabase db = helper.getHandle();
        db.beginTransaction();
        try {
            for (Pair<String, String[]> whereParams : whereParamList) {
                db.delete(table, DatabaseHelper.ID_COL + " IN " + whereParams.first, whereParams.second);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return removed;
    }

    @Override
    public void repack() {
        //Unecessary!
    }

    @Override
    public void repair() {
        //Unecessary!
    }

    @Override
    public void update(int id, Externalizable e) {
        SQLiteDatabase db = helper.getHandle();
        db.beginTransaction();
        try {
            db.update(table, helper.getContentValues(e), DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void write(Persistable p) {
        if (p.getID() != -1) {
            update(p.getID(), p);
            return;
        }
        SQLiteDatabase db = helper.getHandle();
        try {

            db.beginTransaction();
            long ret = db.insertOrThrow(table, DatabaseHelper.DATA_COL, helper.getContentValues(p));

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            int id = (int) ret;
            //Now we need to put the id into the record

            p.setID(id);
            db.update(table, helper.getContentValues(p), DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void setReadOnly() {
        //TODO: Implement (although I doubt there's much useful stuff to do)
    }

    @Override
    public void registerIndex(String filterIndex) {
        // TODO Auto-generated method stub
    }
}
