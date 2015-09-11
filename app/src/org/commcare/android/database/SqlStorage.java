package org.commcare.android.database;

import android.database.Cursor;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQueryBuilder;
import net.sqlcipher.database.SQLiteStatement;

import org.commcare.android.db.legacy.LegacyInstallUtils.CopyMapper;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.util.SessionUnavailableException;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.InvalidIndexException;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.Externalizable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * @author ctsims
 */
public class SqlStorage<T extends Persistable> implements IStorageUtilityIndexed, Iterable<T> {

    /**
     * Static flag identifying whether storage optimizations are active.
     */
    public static boolean STORAGE_OPTIMIZATIONS_ACTIVE = true;

    public static final boolean STORAGE_OUTPUT_DEBUG = false;

    String table;
    Class<? extends T> ctype;
    EncryptedModel em;
    T t;
    DbHelper helper;

    protected SqlStorage() {
    }

    public SqlStorage(String table, Class<? extends T> ctype, DbHelper helper) {
        this.table = table;
        this.ctype = ctype;
        this.helper = helper;

        try {
            T e = (T) ctype.newInstance();
            if (e instanceof EncryptedModel) {
                em = (EncryptedModel) e;
            }
        } catch (InstantiationException ie) {
            ie.printStackTrace();
        } catch (IllegalAccessException iae) {
            iae.printStackTrace();
        }
    }

    @Override
    public Vector<Integer> getIDsForValue(String fieldName, Object value) {
        return getIDsForValues(new String[]{fieldName}, new Object[]{value});
    }

    public Vector<Integer> getIDsForValues(String[] fieldNames, Object[] values) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        Pair<String, String[]> whereClause = helper.createWhere(fieldNames, values, em, t);

        if (STORAGE_OUTPUT_DEBUG) {
            String sql = SQLiteQueryBuilder.buildQueryString(false, table, new String[]{DbUtil.ID_COL}, whereClause.first, null, null, null, null);
            DbUtil.explainSql(db, sql, whereClause.second);
        }

        Cursor c = db.query(table, new String[]{DbUtil.ID_COL}, whereClause.first, whereClause.second, null, null, null);
        return fillIdWindow(c, DbUtil.ID_COL);
    }

    public static Vector<Integer> fillIdWindow(Cursor c, String columnName) {
        if (c.getCount() == 0) {
            c.close();
            return new Vector<Integer>();
        } else {
            c.moveToFirst();
            Vector<Integer> indices = new Vector<Integer>();
            int index = c.getColumnIndexOrThrow(columnName);
            while (!c.isAfterLast()) {
                int id = c.getInt(index);
                indices.add(id);
                c.moveToNext();
            }
            c.close();
            return indices;
        }
    }

    public Vector<T> getRecordsForValue(String fieldName, Object value) {
        return getRecordsForValues(new String[]{fieldName}, new Object[]{value});
    }

    public Vector<T> getRecordsForValues(String[] fieldNames, Object[] values) {
        Pair<String, String[]> whereClause = helper.createWhere(fieldNames, values, em, t);

        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{DbUtil.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        if (c.getCount() == 0) {
            c.close();
            return new Vector<T>();
        } else {
            c.moveToFirst();
            Vector<T> indices = new Vector<T>();
            int index = c.getColumnIndexOrThrow(DbUtil.DATA_COL);
            while (!c.isAfterLast()) {
                byte[] data = c.getBlob(index);
                indices.add(newObject(data));
                c.moveToNext();
            }
            c.close();
            return indices;
        }
    }

    public String getMetaDataFieldForRecord(int recordId, String rawFieldName) {
        String rid = String.valueOf(recordId);
        String scrubbedName = TableBuilder.scrubName(rawFieldName);
        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{scrubbedName}, DbUtil.ID_COL + "=?", new String[]{rid}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        if (c.getCount() == 0) {
            c.close();
            throw new NoSuchElementException("No record in table " + table + " for ID " + recordId);
        }
        c.moveToFirst();
        String result = c.getString(c.getColumnIndexOrThrow(scrubbedName));
        c.close();
        return result;

    }

    public T getRecordForValues(String[] rawFieldNames, Object[] values) throws NoSuchElementException, InvalidIndexException {
        Pair<String, String[]> whereClause = helper.createWhere(rawFieldNames, values, em, t);
        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{DbUtil.ID_COL, DbUtil.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        if (c.getCount() == 0) {
            throw new NoSuchElementException("No element in table " + table + " with names " + Arrays.toString(rawFieldNames) + " and values " + Arrays.toString(values));
        }
        if (c.getCount() > 1) {
            throw new InvalidIndexException("Invalid unique column set" + Arrays.toString(rawFieldNames) + ". Multiple records found with value " + Arrays.toString(values), Arrays.toString(rawFieldNames));
        }
        c.moveToFirst();
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));
        c.close();
        return newObject(data);
    }

    @Override
    public T getRecordForValue(String rawFieldName, Object value) throws NoSuchElementException, InvalidIndexException {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        Pair<String, String[]> whereClause = helper.createWhere(new String[]{rawFieldName}, new Object[]{value}, em, t);

        if (STORAGE_OUTPUT_DEBUG) {
            String sql = SQLiteQueryBuilder.buildQueryString(false, table, new String[]{DbUtil.ID_COL}, whereClause.first, null, null, null, null);
            DbUtil.explainSql(db, sql, whereClause.second);
        }

        String scrubbedName = TableBuilder.scrubName(rawFieldName);
        Cursor c = db.query(table, new String[]{DbUtil.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        if (c.getCount() == 0) {
            c.close();
            throw new NoSuchElementException("No element in table " + table + " with name " + scrubbedName + " and value " + value.toString());
        }
        if (c.getCount() > 1) {
            c.close();
            throw new InvalidIndexException("Invalid unique column " + scrubbedName + ". Multiple records found with value " + value.toString(), scrubbedName);
        }
        c.moveToFirst();
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));
        c.close();
        return newObject(data);
    }

    public T newObject(byte[] data) {
        try {
            T e = (T) ctype.newInstance();
            e.readExternal(new DataInputStream(new ByteArrayInputStream(data)), helper.getPrototypeFactory());

            return e;
        } catch (IllegalAccessException e) {
            throw logAndWrap(e, "Illegal Access Exception");
        } catch (InstantiationException e) {
            throw logAndWrap(e, "Instantiation Exception");
        } catch (IOException e) {
            throw logAndWrap(e, "Totally non-sensical IO Exception");
        } catch (DeserializationException e) {
            throw logAndWrap(e, "CommCare ran into an issue deserializing data");
        }
    }

    private RuntimeException logAndWrap(Exception e, String message) {
        RuntimeException re = new RuntimeException(message + " while inflating type " + ctype.getName());
        re.initCause(e);
        Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, ExceptionReportTask.getStackTrace(re, true));
        return re;
    }

    @Override
    public int add(Externalizable e) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException sue) {
            throw new UserStorageClosedException(sue.getMessage());
        }
        int i = -1;
        try {
            db.beginTransaction();
            long ret = db.insertOrThrow(table, DbUtil.DATA_COL, helper.getContentValues(e));

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
        try {
            helper.getHandle().close();
        } catch (SessionUnavailableException e) {
            // The db isn't available so don't worry about closing it.
        }
    }

    @Override
    public void destroy() {
        //nothing;
    }

    @Override
    public boolean exists(int id) {
        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{DbUtil.ID_COL}, DbUtil.ID_COL + "= ? ", new String[]{String.valueOf(id)}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

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
    public SQLiteDatabase getAccessLock() {
        try {
            return helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
    }

    @Override
    public int getNumRecords() {
        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{DbUtil.ID_COL}, null, null, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
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
        if (getNumRecords() == 0) {
            return true;
        }
        return false;
    }

    @Override
    public SqlStorageIterator<T> iterate() {
        return iterate(true);
    }


    /**
     * Creates a custom iterator for this storage which can either include or exclude the actual data.
     * Useful for getting an overview of data for querying into without wasting the bits to transfer over
     * the huge full records.
     *
     * @param includeData True to return an iterator with all records. False to return only the index.
     */
    public SqlStorageIterator<T> iterate(boolean includeData) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        //If we're just iterating over ID's, we may want to use a different, much 
        //faster method depending on our stats. This method retrieves the 
        //index records that _don't_ exist so we can assume the spans that
        //do.
        if (!includeData && STORAGE_OPTIMIZATIONS_ACTIVE) {

            SQLiteStatement min = db.compileStatement("SELECT MIN(" + DbUtil.ID_COL + ") from " + table);

            SQLiteStatement max = db.compileStatement("SELECT MAX(" + DbUtil.ID_COL + ") from " + table);

            SQLiteStatement count = db.compileStatement("SELECT COUNT(" + DbUtil.ID_COL + ") from " + table);

            int minValue = (int) min.simpleQueryForLong();
            int maxValue = (int) max.simpleQueryForLong() + 1;
            int countValue = (int) count.simpleQueryForLong();

            min.close();
            max.close();
            count.close();

            double density = countValue / (maxValue - minValue * 1.0);

            //Ok, so basic metrics:
            //1) Only use a covering iterator if the number of records is > 1k
            //2) Only use a covering iterator if the number of records is less than 100k (vital, hard limit)
            //3) Only use a covering iterator if the record density is 50% or more
            if (countValue > 1000 &&
                    countValue < 100000 &&
                    density >= 0.5) {
                return getCoveringIndexIterator(db, minValue, maxValue, countValue);
            }
        }

        String[] projection = includeData ? new String[]{DbUtil.ID_COL, DbUtil.DATA_COL} : new String[]{DbUtil.ID_COL};
        Cursor c = db.query(table, projection, null, null, null, null, null);
        return new SqlStorageIterator<T>(c, this);
    }


    /**
     * Creates a custom iterator for this storage which can either include or exclude the actual data, and
     * additionally collects a primary ID that will be returned and available during iteration.
     *
     * Useful for situations where the iterator is loading data that will be indexed by the primary id
     * since it will prevent the need to turn that primary id into the storage key for retrieving each
     * record.
     *
     * TODO: This is a bit too close to comfort to the other custom iterator. It's possible we should just
     * have a method to query for all metadata?
     *
     * @param includeData True to return an iterator with all records. False to return only the index.
     * @param primaryId   a metadata index that
     */
    public SqlStorageIterator<T> iterate(boolean includeData, String primaryId) {
        String[] projection = includeData ? new String[]{DbUtil.ID_COL, DbUtil.DATA_COL, TableBuilder.scrubName(primaryId)} : new String[]{DbUtil.ID_COL, TableBuilder.scrubName(primaryId)};
        Cursor c;
        try {
            c = helper.getHandle().query(table, projection, null, null, null, null, DbUtil.ID_COL);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        return new SqlStorageIterator<T>(c, this, TableBuilder.scrubName(primaryId));
    }

    public Iterator<T> iterator() {
        return iterate();
    }

    @Override
    public T read(int id) {
        return newObject(readBytes(id));
    }

    @Override
    public byte[] readBytes(int id) {
        Cursor c;
        try {
            c = helper.getHandle().query(table, new String[]{DbUtil.ID_COL, DbUtil.DATA_COL}, DbUtil.ID_COL + "=?", new String[]{String.valueOf(id)}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        c.moveToFirst();
        byte[] blob = c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));
        c.close();
        return blob;
    }

    @Override
    public void remove(int id) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        db.beginTransaction();
        try {
            db.delete(table, DbUtil.ID_COL + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void remove(List<Integer> ids) {
        if (ids.size() == 0) {
            return;
        }
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        db.beginTransaction();
        try {
            List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(ids);
            for (Pair<String, String[]> whereParams : whereParamList) {
                int rowsRemoved = db.delete(table, DbUtil.ID_COL + " IN " + whereParams.first, whereParams.second);
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
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
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
        Vector<Integer> removed = new Vector<Integer>();
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

        if (removed.size() == 0) {
            return removed;
        }

        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(removed);


        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        db.beginTransaction();
        try {
            for (Pair<String, String[]> whereParams : whereParamList) {
                db.delete(table, DbUtil.ID_COL + " IN " + whereParams.first, whereParams.second);
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
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException sue) {
            throw new UserStorageClosedException(sue.getMessage());
        }
        db.beginTransaction();
        try {
            db.update(table, helper.getContentValues(e), DbUtil.ID_COL + "=?", new String[]{String.valueOf(id)});
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
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        try {
            db.beginTransaction();
            long ret = db.insertOrThrow(table, DbUtil.DATA_COL, helper.getContentValues(p));

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            int id = (int) ret;
            //Now we need to put the id into the record

            p.setID(id);
            db.update(table, helper.getContentValues(p), DbUtil.ID_COL + "=?", new String[]{String.valueOf(id)});

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

    public static <T extends Persistable> Map<Integer, Integer> cleanCopy(SqlStorage<T> from, SqlStorage<T> to) throws SessionUnavailableException {
        return cleanCopy(from, to, null);
    }

    public static <T extends Persistable> Map<Integer, Integer> cleanCopy(SqlStorage<T> from, SqlStorage<T> to, CopyMapper<T> mapper) throws SessionUnavailableException {
        to.removeAll();
        SQLiteDatabase toDb = to.helper.getHandle();
        try {
            Hashtable<Integer, Integer> idMapping = new Hashtable<Integer, Integer>();
            toDb.beginTransaction();

            for (T t : from) {
                int key = t.getID();
                //Clear the ID, we don't wanna guarantee it
                t.setID(-1);
                if (mapper != null) {
                    t = mapper.transform(t);
                }
                to.write(t);
                idMapping.put(key, t.getID());
            }
            toDb.setTransactionSuccessful();
            return idMapping;
        } finally {
            toDb.endTransaction();
        }
    }

    /**
     * @return An iterator which can provide a list of all of the indices in this table.
     */
    private SqlStorageIterator<T> getCoveringIndexIterator(SQLiteDatabase db, int minValue, int maxValue, int countValue) {

        //So here's what we're doing: 
        //Build a select statement that has all of the numbers from 1 to 100k
        //Filter it to contain our real boundaries
        //Select all id's from our table's index
        //Except those ids from the virtual table
        //
        //This returns what is essentially a set of spans from min -> max where ID's do _not_
        //exist in this table.
        String vals = "select 10000 * tenthousands.i + 1000 * thousands.i + 100*hundreds.i + 10*tens.i + units.i as " + DbUtil.ID_COL +
                " from integers tenthousands " +
                ", integers thousands " +
                ", integers hundreds  " +
                ", integers tens " +
                ", integers units " +
                " WHERE " + DbUtil.ID_COL + " >= CAST(? AS INTEGER) AND " + DbUtil.ID_COL + "  <= CAST(? AS INTEGER)";


        String[] args = new String[]{String.valueOf(minValue), String.valueOf(maxValue)};

        String stmt = vals + " EXCEPT SELECT " + DbUtil.ID_COL + " FROM " + table;

        Cursor c = db.rawQuery(stmt, args);

        //Return a covering iterator 
        return new IndexSpanningIterator<T>(c, this, (int) minValue, (int) maxValue, (int) countValue);
    }
}
