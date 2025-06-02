package org.commcare.models.database;

import android.database.Cursor;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQueryBuilder;
import net.sqlcipher.database.SQLiteStatement;

import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.database.TableBuilder;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.util.Pair;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.condition.RequestAbandonedException;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final Class<? extends T> ctype;
    protected final EncryptedModel em;
    protected final AndroidDbHelper helper;

    protected SqlStorage() {
        em = null;
        helper = null;
        ctype = null;
    }

    public SqlStorage(String table, Class<? extends T> ctype, AndroidDbHelper helper) {
        this.table = table;
        this.ctype = ctype;
        this.helper = helper;

        T e = null;
        try {
            e = ctype.newInstance();
        } catch (IllegalAccessException ie) {
            ie.printStackTrace();
        } catch (InstantiationException ie) {
            ie.printStackTrace();
        }

        if (e != null && e instanceof EncryptedModel) {
            em = (EncryptedModel)e;
        } else {
            em = null;
        }
    }


    @Override
    public Vector<Integer> getIDsForValue(String fieldName, Object value) {
        return getIDsForValues(new String[]{fieldName}, new Object[]{value});
    }

    @Override
    public Vector<Integer> getIDsForValues(String[] fieldNames, Object[] values) {
        return getIDsForValues(fieldNames, values, null);
    }

    @Override
    public Vector<Integer> getIDsForValues(String[] fieldNames, Object[] values, LinkedHashSet returnSet) {
        SQLiteDatabase db = helper.getHandle();
        Pair<String, String[]> whereClause = helper.createWhereAndroid(fieldNames, values, em, null);
        return getIDsForValuesInner(db, whereClause, returnSet);
    }

    @Override
    public List<Integer> getIDsForValues(String[] fieldNames, Object[] values, String[] inverseFieldNames, Object[] inverseValues, LinkedHashSet returnSet) {
        SQLiteDatabase db = helper.getHandle();
        Pair<String, String[]> whereClause = DatabaseHelper.createWhere(fieldNames, values, inverseFieldNames, inverseValues, em, null);
        return getIDsForValuesInner(db, whereClause, returnSet);
    }

    private Vector<Integer> getIDsForValuesInner(SQLiteDatabase db, Pair<String, String[]> whereClause, LinkedHashSet returnSet) {
        if (STORAGE_OUTPUT_DEBUG) {
            String sql = SQLiteQueryBuilder.buildQueryString(false, table, new String[]{DatabaseHelper.ID_COL}, whereClause.first, null, null, null, null);
            DbUtil.explainSql(db, sql, whereClause.second);
        }

        Cursor c = db.query(table, new String[]{DatabaseHelper.ID_COL}, whereClause.first, whereClause.second, null, null, null);
        return fillIdWindow(c, DatabaseHelper.ID_COL, returnSet);
    }

    public static Vector<Integer> fillIdWindow(Cursor c, String columnName, LinkedHashSet<Integer> newReturn) {
        Vector<Integer> indices = new Vector<>();
        try {
            if (c.moveToFirst()) {
                int index = c.getColumnIndexOrThrow(columnName);
                while (!c.isAfterLast()) {
                    int id = c.getInt(index);
                    if (newReturn != null) {
                        newReturn.add(id);
                    }
                    indices.add(id);
                    c.moveToNext();
                }
            }
            return indices;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public Vector<T> getRecordsForValue(String fieldName, Object value) {
        return getRecordsForValues(new String[]{fieldName}, new Object[]{value});
    }

    /**
     * Return all records from this SqlStorage object for which, for each field in fieldNames,
     * the record has the correct corresponding value in values
     */
    public Vector<T> getRecordsForValues(String[] fieldNames, Object[] values) {
        Pair<String, String[]> whereClause = helper.createWhereAndroid(fieldNames, values, em, null);

        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        try {
            if (c.getCount() == 0) {
                return new Vector<>();
            } else {
                c.moveToFirst();
                Vector<T> indices = new Vector<>();
                int index = c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL);
                while (!c.isAfterLast()) {
                    byte[] data = c.getBlob(index);
                    indices.add(newObject(data, c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL))));
                    c.moveToNext();
                }
                return indices;
            }
        } finally {
            c.close();
        }
    }

    public String getMetaDataFieldForRecord(int recordId, String rawFieldName) {
        String rid = String.valueOf(recordId);
        String scrubbedName = TableBuilder.scrubName(rawFieldName);
        Cursor c = helper.getHandle().query(table, new String[]{scrubbedName}, DatabaseHelper.ID_COL + "=?", new String[]{rid}, null, null, null);
        try {
            if (c.getCount() == 0) {
                throw new NoSuchElementException("No record in table " + table + " for ID " + recordId);
            }
            c.moveToFirst();
            return c.getString(c.getColumnIndexOrThrow(scrubbedName));
        } finally {
            c.close();
        }
    }

    @Override
    public String[] getMetaDataForRecord(int recordId, String[] metaDataNames) {
        String rid = String.valueOf(recordId);
        String[] scrubbedNames = scrubMetadataNames(metaDataNames);
        String[] projection = getProjectedFieldsWithId(false, scrubbedNames);
        Cursor c = helper.getHandle().query(table, projection, DatabaseHelper.ID_COL + "=?", new String[]{rid}, null, null, null);
        try {
            if (c.getCount() == 0) {
                throw new NoSuchElementException("No record in table " + table + " for ID " + recordId);
            }
            c.moveToFirst();

            return readMetaDataFromCursor(c, scrubbedNames);
        } finally {
            c.close();
        }
    }


    public T getRecordForValues(String[] rawFieldNames, Object[] values) throws NoSuchElementException, InvalidIndexException {
        SQLiteDatabase appDb = helper.getHandle();

        Pair<String, String[]> whereClause = helper.createWhereAndroid(rawFieldNames, values, em, null);
        Cursor c = appDb.query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL}, whereClause.first, whereClause.second, null, null, null);
        try {
            int queryCount = c.getCount();
            if (queryCount == 0) {
                throw new NoSuchElementException("No element in table " + table + " with names " + Arrays.toString(rawFieldNames) + " and values " + Arrays.toString(values));
            } else if (queryCount > 1) {
                throw new InvalidIndexException("Invalid unique column set" + Arrays.toString(rawFieldNames) + ". Multiple records found with value " + Arrays.toString(values), Arrays.toString(rawFieldNames));
            }
            c.moveToFirst();
            byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
            return newObject(data, c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL)));
        } finally {
            c.close();
        }
    }

    @Override
    public T getRecordForValue(String rawFieldName, Object value) throws NoSuchElementException, InvalidIndexException {
        return getRecordForValues(new String[]{rawFieldName}, new Object[]{value});
    }

    /**
     * @param dbEntryId Set the deserialized persistable's id to the database entry id.
     *                  Doing so now is more effecient then during writes
     */
    public T newObject(InputStream serializedObjectInputStream, int dbEntryId) {
        try {
            T e = ctype.newInstance();
            e.readExternal(new DataInputStream(serializedObjectInputStream),
                    helper.getPrototypeFactory());
            e.setID(dbEntryId);
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

    /**
     * @param dbEntryId Set the deserialized persistable's id to the database entry id.
     *                  Doing so now is more effecient then during writes
     */
    public T newObject(byte[] serializedObjectAsBytes, int dbEntryId) {
        return newObject(new ByteArrayInputStream(serializedObjectAsBytes), dbEntryId);
    }

    private RuntimeException logAndWrap(Exception e, String message) {
        RuntimeException re = new RuntimeException(message + " while inflating type " + ctype.getName());
        re.initCause(e);
        Logger.log(LogTypes.TYPE_ERROR_STORAGE,
                "Error while inflating type " + ctype.getName() + ": " +
                        ForceCloseLogger.getStackTraceWithContext(re));
        return re;
    }

    @Override
    public int add(Externalizable e) {
        SQLiteDatabase db;
        db = helper.getHandle();
        int i = -1;
        db.beginTransaction();
        try {
            long ret = db.insertOrThrow(table, DatabaseHelper.DATA_COL, helper.getContentValues(e));

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            i = (int)ret;

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
    public boolean exists(int id) {
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL}, DatabaseHelper.ID_COL + "= ? ", new String[]{String.valueOf(id)}, null, null, null);

        try {
            int queryCount = c.getCount();
            if (queryCount == 0) {
                return false;
            } else if (queryCount > 1) {
                throw new InvalidIndexException("Invalid ID column. Multiple records found with value " + id, "ID");
            }
        } finally {
            c.close();
        }
        return true;
    }

    @Override
    public SQLiteDatabase getAccessLock() {
        return helper.getHandle();
    }

    @Override
    public int getNumRecords() {
        Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL}, null, null, null, null, null);
        try {
            int records = c.getCount();
            return records;
        } finally {
            c.close();
        }
    }

    @Override
    public boolean isEmpty() {
        return (getNumRecords() == 0);
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
    @Override
    public SqlStorageIterator<T> iterate(boolean includeData) {
        SQLiteDatabase db = helper.getHandle();

        SqlStorageIterator<T> spanningIterator = getIndexSpanningIteratorOrNull(db, includeData);
        if (spanningIterator != null) {
            return spanningIterator;
        } else {
            return new SqlStorageIterator<>(getIterateCursor(db, includeData), this);
        }
    }

    protected SqlStorageIterator<T> getIndexSpanningIteratorOrNull(SQLiteDatabase db, boolean includeData) {
        //If we're just iterating over ID's, we may want to use a different, much
        //faster method depending on our stats. This method retrieves the
        //index records that _don't_ exist so we can assume the spans that
        //do.
        if (!includeData && STORAGE_OPTIMIZATIONS_ACTIVE) {

            SQLiteStatement min = db.compileStatement("SELECT MIN(" + DatabaseHelper.ID_COL + ") from " + table);

            SQLiteStatement max = db.compileStatement("SELECT MAX(" + DatabaseHelper.ID_COL + ") from " + table);

            SQLiteStatement count = db.compileStatement("SELECT COUNT(" + DatabaseHelper.ID_COL + ") from " + table);

            int minValue = (int)min.simpleQueryForLong();
            int maxValue = (int)max.simpleQueryForLong() + 1;
            int countValue = (int)count.simpleQueryForLong();

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
        return null;
    }

    protected Cursor getIterateCursor(SQLiteDatabase db, boolean includeData) {
        String[] projection = includeData ? new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL} : new String[]{DatabaseHelper.ID_COL};
        return db.query(table, projection, null, null, null, null, null);
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
     * @param includeData       True to return an iterator with all records. False to return only the index.
     * @param metaDataToInclude A set of metadata keys that should be included in the request,
     *                          independently of any other data.
     */
    public SqlStorageIterator<T> iterate(boolean includeData, String[] metaDataToInclude) {
        String[] projection = getProjectedFieldsWithId(includeData, scrubMetadataNames(metaDataToInclude));

        Cursor c = helper.getHandle().query(table, projection, null, null, null, null, DatabaseHelper.ID_COL);
        return new SqlStorageIterator<>(c, this, metaDataToInclude);
    }

    private String[] scrubMetadataNames(String[] metaDataNames) {
        String[] scrubbedNames = new String[metaDataNames.length];

        for (int i = 0; i < metaDataNames.length; ++i) {
            scrubbedNames[i] = TableBuilder.scrubName(metaDataNames[i]);
        }
        return scrubbedNames;
    }

    private String[] getProjectedFieldsWithId(boolean includeData, String[] columnNamesToInclude) {
        String[] projection = new String[columnNamesToInclude.length + (includeData ? 2 : 1)];
        int firstIndex = 0;
        projection[firstIndex] = DatabaseHelper.ID_COL;
        firstIndex++;
        if (includeData) {
            projection[firstIndex] = DatabaseHelper.DATA_COL;
            firstIndex++;
        }
        for (int i = 0; i < columnNamesToInclude.length; ++i) {
            projection[i + firstIndex] = columnNamesToInclude[i];
        }
        return projection;
    }

    @Override
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

        try {
            if (!c.moveToFirst()) {
                throw new NoSuchElementException("No record in table " + table + " for ID " + id);
            }
            return c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
        } finally {
            c.close();
        }
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

    public void remove(List<Integer> ids) {
        if (ids.size() == 0) {
            return;
        }
        SQLiteDatabase db = helper.getHandle();
        db.beginTransaction();
        try {
            List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(ids);
            for (Pair<String, String[]> whereParams : whereParamList) {
                db.delete(table, DatabaseHelper.ID_COL + " IN " + whereParams.first, whereParams.second);
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
        wipeTable(helper.getHandle(), table);
    }

    public static void wipeTableWithoutCommit(SQLiteDatabase db, String table) {
        db.delete(table, null, null);
    }

    /**
     * Deletes all records for the given table
     * @param db SqliteDatabase the table is present in
     * @param table table name for which we want to delete the records
     */
    public static void wipeTable(SQLiteDatabase db, String table) {
        db.beginTransaction();
        try {
            if (isTableExist(db, table)) {
                db.delete(table, null, null);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Drops the given table
     * @param db SqliteDatabase the table is present in
     * @param table name of the table we want to drop
     */
    public static void dropTable(SQLiteDatabase db, String table) {
        db.beginTransaction();
        try {
            String sqlStatement = "DROP TABLE IF EXISTS " + table;
            db.execSQL(sqlStatement);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * whether the table exists in the given database
     * @param db database we should check for the table existence
     * @param table table name we want to check for
     * @return true if table exists, false otherwise
     */
    public static boolean isTableExist(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = '" + table + "'", null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }

    public Vector<Integer> removeAll(Vector<Integer> toRemove) {
        if (toRemove.size() == 0) {
            return toRemove;
        }

        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(toRemove);

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

        return toRemove;
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
                case EntityFilter.PREFILTER_FILTER:
                    if (ef.matches(read(id))) {
                        removed.add(id);
                    }
            }
        }
        return removeAll(removed);
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
        db.beginTransaction();
        try {
            long ret = db.insertOrThrow(table, DatabaseHelper.DATA_COL, helper.getContentValues(p));

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }
            // won't effect already stored obj id, which is set when reading out of db.
            // rather, needed in case persistable object is used after being written to storage.
            p.setID((int)ret);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
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
        String vals = "select 10000 * tenthousands.i + 1000 * thousands.i + 100*hundreds.i + 10*tens.i + units.i as " + DatabaseHelper.ID_COL +
                " from integers tenthousands " +
                ", integers thousands " +
                ", integers hundreds  " +
                ", integers tens " +
                ", integers units " +
                " WHERE " + DatabaseHelper.ID_COL + " >= CAST(? AS INTEGER) AND " + DatabaseHelper.ID_COL + "  <= CAST(? AS INTEGER)";


        String[] args = new String[]{String.valueOf(minValue), String.valueOf(maxValue)};

        String stmt = vals + " EXCEPT SELECT " + DatabaseHelper.ID_COL + " FROM " + table;

        Cursor c = db.rawQuery(stmt, args);

        //Return a covering iterator 
        return new IndexSpanningIterator<>(c, this, minValue, maxValue, countValue);
    }

    @Override
    public void bulkRead(LinkedHashSet cuedCases, HashMap recordMap) throws RequestAbandonedException {
        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(cuedCases);
        for (Pair<String, String[]> querySet : whereParamList) {
            Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL}, DatabaseHelper.ID_COL + " IN " + querySet.first, querySet.second, null, null, null);
            try {
                if (c.getCount() == 0) {
                    return;
                } else {
                    c.moveToFirst();
                    int index = c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL);
                    while (!c.isAfterLast()) {
                        if (Thread.interrupted()) {
                            throw new RequestAbandonedException();
                        }
                        byte[] data = c.getBlob(index);
                        recordMap.put(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL)),
                                newObject(data, c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL))));
                        c.moveToNext();
                    }
                }
            } finally {
                c.close();
            }
        }
    }

    @Override
    public void bulkReadMetadata(LinkedHashSet cuedCases, String[] metaDataIds, HashMap metadataMap) {
        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(cuedCases);
        String[] scrubbedNames = scrubMetadataNames(metaDataIds);
        String[] projection = getProjectedFieldsWithId(false, scrubbedNames);

        for (Pair<String, String[]> querySet : whereParamList) {
            Cursor c = helper.getHandle().query(table, projection, DatabaseHelper.ID_COL + " IN " + querySet.first, querySet.second, null, null, null);
            try {
                if (c.getCount() == 0) {
                    return;
                } else {
                    c.moveToFirst();
                    int idIndex = c.getColumnIndexOrThrow(DatabaseHelper.ID_COL);
                    while (!c.isAfterLast()) {
                        String[] metaRead = readMetaDataFromCursor(c, scrubbedNames);
                        metadataMap.put(c.getInt(idIndex), metaRead);
                        c.moveToNext();
                    }
                }
            } finally {
                c.close();
            }
        }
    }

    /**
     * Reads out the metadata columns from the provided cursor.
     *
     * NOTE: The column names _must be scrubbed here_ before the method is called
     */
    private String[] readMetaDataFromCursor(Cursor c, String[] columnNames) {
        String[] results = new String[columnNames.length];
        int i = 0;
        for (String columnName : columnNames) {
            results[i] = c.getString(c.getColumnIndexOrThrow(columnName));
            i++;
        }
        return results;
    }

    /**
     * Retrieves a set of the models in storage based on a list of values matching one of the
     * indexes of this storage
     */
    @Override
    public Vector<T> getBulkRecordsForIndex(String indexName, Collection matchingValues) {
        Vector<T> returnSet = new Vector<>();
        String fieldName = TableBuilder.scrubName(indexName);
        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(matchingValues, "?");
        for (Pair<String, String[]> querySet : whereParamList) {
            Cursor c = helper.getHandle().query(table, new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL, fieldName}, fieldName + " IN " + querySet.first, querySet.second, null, null, null);
            try {
                if (c.getCount() == 0) {
                    return returnSet;
                } else {
                    c.moveToFirst();
                    int index = c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL);
                    while (!c.isAfterLast()) {
                        byte[] data = c.getBlob(index);

                        returnSet.add(newObject(data, c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL))));
                        c.moveToNext();
                    }
                }
            } finally {
                c.close();
            }
        }
        return returnSet;
    }

    /**
     * Retrieves a set of the model ids in storage based on a list of values matching one of the
     * indexes of this storage
     */
    public Vector<Integer> getBulkIdsForIndex(String indexName, Collection matchingValues) {
        String fieldName = TableBuilder.scrubName(indexName);
        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(matchingValues, "?");
        for (Pair<String, String[]> querySet : whereParamList) {
            Cursor c = helper.getHandle().query(table,
                    new String[]{DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL, fieldName},
                    fieldName + " IN " + querySet.first, querySet.second, null, null, null);
            return fillIdWindow(c, DatabaseHelper.ID_COL, new LinkedHashSet<>());
        }
        return new Vector<>();
    }

    @Override
    public Class<?> getPrototype() {
        return ctype;
    }

    @Override
    public boolean isStorageExists() {
        return isTableExist(helper.getHandle(), table);
    }

    @Override
    public void initStorage() {
        // nothing to do here as storage is created outside of this class
    }

    @Override
    public void deleteStorage() {
        dropTable(helper.getHandle(), table);
    }
}
