package org.commcare.android.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.EncryptionIO;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.modern.database.DatabaseHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.InvalidIndexException;
import org.javarosa.core.util.externalizable.Externalizable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.crypto.spec.SecretKeySpec;

/**
 * Sql logic for storing persistable objects. Uses the filesystem to store
 * persitables in _encrypted_ manner; useful when objects are larger than the
 * 1mb sql row limit.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HybridFileBackedSqlStorage<T extends Persistable> extends SqlStorage<T> {
    private final File dbDir;
    private final static int ONE_MB_DB_SIZE_LIMIT = 1000000;

    /**
     * column selection used for reading file data
     */
    protected final static String[] dataColumns =
            {DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL,
                    DatabaseHelper.FILE_COL, DatabaseHelper.AES_COL};

    /**
     * Sql object storage layer that stores serialized objects on the filesystem.
     *
     * @param table   name of database table
     * @param ctype   type of object being stored in this database
     * @param baseDir all files for entries will be placed within this dir
     */
    public HybridFileBackedSqlStorage(String table,
                                      Class<? extends T> ctype,
                                      AndroidDbHelper helper,
                                      String baseDir) {
        super(table, ctype, helper);

        dbDir = new File(baseDir + GlobalConstants.FILE_CC_DB + table);
        setupDir();
    }

    private void setupDir() {
        if (!dbDir.exists() && !dbDir.mkdirs()) {
            throw new RuntimeException("Unable to create db storage directory: " + dbDir);
        }
    }

    @Override
    public Vector<T> getRecordsForValues(String[] fieldNames,
                                         Object[] values) {
        SQLiteDatabase db = getDbOrThrow();

        Pair<String, String[]> whereClauseAndArgs =
                helper.createWhereAndroid(fieldNames, values, em, null);

        Cursor c = db.query(table, dataColumns,
                whereClauseAndArgs.first, whereClauseAndArgs.second,
                null, null, null);
        try {
            Vector<T> recordObjects = new Vector<>();
            if (c.getCount() > 0) {
                c.moveToFirst();
                int dataColIndex = c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL);
                int fileColIndex = c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL);
                int aesColIndex = c.getColumnIndexOrThrow(DatabaseHelper.AES_COL);
                while (!c.isAfterLast()) {
                    byte[] data = c.getBlob(dataColIndex);
                    int dbEntryId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
                    if (data != null) {
                        // serialized object was small enough to fit in db entry
                        recordObjects.add(newObject(data, dbEntryId));
                    } else {
                        // serialized object was stored in filesystem due to large size
                        recordObjects.add(readObjectFromFile(c, fileColIndex, aesColIndex, dbEntryId));
                    }
                    c.moveToNext();
                }
            }
            return recordObjects;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private T readObjectFromFile(Cursor c, int dbEntryId) {
        return readObjectFromFile(c,
                c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL),
                c.getColumnIndexOrThrow(DatabaseHelper.AES_COL),
                dbEntryId);
    }

    private T readObjectFromFile(Cursor c, int fileColIndex, int aesColIndex, int dbEntryId) {
        String filename = c.getString(fileColIndex);
        byte[] aesKeyBlob = c.getBlob(aesColIndex);
        InputStream inputStream = null;
        try {
            inputStream = getInputStreamFromFile(filename, aesKeyBlob);
            return newObject(inputStream, dbEntryId);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private SQLiteDatabase getDbOrThrow() {
        try {
            return helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
    }

    protected InputStream getInputStreamFromFile(String filename, byte[] aesKeyBytes) {
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        return EncryptionIO.getFileInputStream(filename, aesKey);
    }

    @Override
    public T getRecordForValues(String[] rawFieldNames, Object[] values)
            throws NoSuchElementException, InvalidIndexException {
        SQLiteDatabase db = getDbOrThrow();

        Pair<String, String[]> whereArgsAndVals =
                helper.createWhereAndroid(rawFieldNames, values, em, null);
        Cursor c = db.query(table, dataColumns,
                whereArgsAndVals.first, whereArgsAndVals.second,
                null, null, null);
        try {
            int queryCount = c.getCount();
            if (queryCount == 0) {
                throw new NoSuchElementException("No element in table " + table +
                        " with names " + Arrays.toString(rawFieldNames) +
                        " and values " + Arrays.toString(values));
            } else if (queryCount > 1) {
                throw new InvalidIndexException("Invalid unique column set" +
                        Arrays.toString(rawFieldNames) +
                        ". Multiple records found with value " +
                        Arrays.toString(values), Arrays.toString(rawFieldNames));
            }
            c.moveToFirst();
            byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
            int dbEntryId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
            if (data != null) {
                return newObject(data, dbEntryId);
            } else {
                return readObjectFromFile(c, dbEntryId);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public T getRecordForValue(String rawFieldName, Object value)
            throws NoSuchElementException, InvalidIndexException {
        return getRecordForValues(new String[]{rawFieldName}, new Object[]{value});
    }

    @Override
    public byte[] readBytes(int id) {
        Cursor c;
        try {
            c = helper.getHandle().query(table, dataColumns,
                    DatabaseHelper.ID_COL + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        InputStream is = null;
        try {
            c.moveToFirst();
            byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
            if (data != null) {
                return data;
            } else {
                String filename = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
                byte[] aesKeyBlob = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL));
                is = getInputStreamFromFile(filename, aesKeyBlob);
                if (is == null) {
                    throw new RuntimeException("Unable to open and decrypt file: " + filename);
                }

                return StreamsUtil.getStreamAsBytes(is);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public void write(Persistable p) {
        if (p.getID() != -1) {
            update(p.getID(), p);
            return;
        }
        SQLiteDatabase db = getDbOrThrow();

        try {
            db.beginTransaction();

            long insertedId;
            ByteArrayOutputStream bos = writeExternalizableToStream(p);
            try {
                if (blobFitsInDb(bos)) {
                    // serialized object small enough to fit in db
                    ContentValues contentValues = helper.getNonDataContentValues(p);
                    contentValues.put(DatabaseHelper.DATA_COL, bos.toByteArray());
                    insertedId = db.insertOrThrow(table, DatabaseHelper.DATA_COL, contentValues);
                } else {
                    // store serialized object in file and file pointer in db
                    File dataFile = HybridFileBackedSqlHelpers.newFileForEntry(dbDir);
                    ContentValues contentValues = helper.getNonDataContentValues(p);
                    contentValues.put(DatabaseHelper.FILE_COL, dataFile.getAbsolutePath());
                    byte[] key = generateKeyAndAdd(contentValues);
                    insertedId = db.insertOrThrow(table, DatabaseHelper.FILE_COL, contentValues);

                    writeStreamToFile(bos, dataFile.getAbsolutePath(), key);
                }
            } finally {
                bos.close();
            }
            // won't effect already stored obj id, which is set when reading out of db.
            // rather, needed in case persistable object is used after being written to storage.
            p.setID((int)insertedId);

            if (insertedId > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            db.setTransactionSuccessful();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    private ByteArrayOutputStream writeExternalizableToStream(Externalizable extObj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            extObj.writeExternal(new DataOutputStream(bos));
        } catch (IOException e1) {
            e1.printStackTrace();
            throw new RuntimeException("Failed to serialize externalizable");
        }
        return bos;
    }

    protected boolean blobFitsInDb(ByteArrayOutputStream blobStream) {
        return blobStream.size() < ONE_MB_DB_SIZE_LIMIT;
    }

    protected byte[] generateKeyAndAdd(ContentValues contentValues) {
        try {
            byte[] key = CommCareApplication._().createNewSymetricKey().getEncoded();
            contentValues.put(DatabaseHelper.AES_COL, key);
            return key;
        } catch (SessionUnavailableException e) {
            throw new RuntimeException("Session unavailable; can't generate encryption key.");
        }
    }

    private void writeStreamToFile(ByteArrayOutputStream bos, String filename,
                                   byte[] key) throws IOException {
        DataOutputStream fileOutputStream = null;
        try {
            fileOutputStream = getOutputFileStream(filename, key);
            bos.writeTo(fileOutputStream);
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }

    protected DataOutputStream getOutputFileStream(String filename,
                                                   byte[] aesKeyBytes) throws IOException {
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        return new DataOutputStream(EncryptionIO.createFileOutputStream(filename, aesKey));
    }

    @Override
    public int add(Externalizable externalizable) {
        throw new UnsupportedOperationException("Use 'SqlFileBackedStorage.write'");
    }

    @Override
    public void update(int id, Externalizable extObj) {
        SQLiteDatabase db = getDbOrThrow();

        db.beginTransaction();
        ByteArrayOutputStream bos = null;
        try {
            Pair<String, byte[]> filenameAndKey =
                    HybridFileBackedSqlHelpers.getEntryFilenameAndKey(helper, table, id);
            String filename = filenameAndKey.first;
            byte[] fileEncryptionKey = filenameAndKey.second;
            boolean objectInDb = filename == null;

            bos = writeExternalizableToStream(extObj);
            if (blobFitsInDb(bos)) {
                updateEntryToStoreInDb(extObj, objectInDb, filename, bos, db, id);
            } else {
                updateEntryToStoreInFs(extObj, objectInDb, filename,
                        fileEncryptionKey, bos, db, id);
            }

            db.setTransactionSuccessful();
        } catch (IOException e) {
            // Failed to update file
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            db.endTransaction();
        }
    }

    private void updateEntryToStoreInDb(Externalizable extObj, boolean objectInDb,
                                        String filename, ByteArrayOutputStream bos,
                                        SQLiteDatabase db, int id) {
        ContentValues updatedContentValues;
        if (objectInDb) {
            // was already stored in db, do normal update
            updatedContentValues = helper.getContentValuesWithCustomData(extObj, bos.toByteArray());
        } else {
            // was stored in file: remove file and store in db
            updatedContentValues = helper.getContentValuesWithCustomData(extObj, bos.toByteArray());
            updatedContentValues.put(DatabaseHelper.FILE_COL, (String)null);
            updatedContentValues.put(DatabaseHelper.AES_COL, (byte[])null);

            File dataFile = new File(filename);
            dataFile.delete();
        }
        db.update(table, updatedContentValues,
                DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
    }

    private void updateEntryToStoreInFs(Externalizable extObj, boolean objectInDb,
                                        String filename, byte[] fileEncryptionKey,
                                        ByteArrayOutputStream bos,
                                        SQLiteDatabase db,
                                        int id) throws IOException {
        ContentValues updatedContentValues;
        if (objectInDb) {
            // was in db but is now to big, null db data entry and write to file
            updatedContentValues = helper.getContentValuesWithCustomData(extObj, null);
            filename = HybridFileBackedSqlHelpers.newFileForEntry(dbDir).getAbsolutePath();
            updatedContentValues.put(DatabaseHelper.FILE_COL, filename);
            fileEncryptionKey = generateKeyAndAdd(updatedContentValues);
        } else {
            // was stored in a file all along, update file
            updatedContentValues = helper.getNonDataContentValues(extObj);
        }
        db.update(table, updatedContentValues,
                DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});

        writeStreamToFile(bos, filename, fileEncryptionKey);
    }

    @Override
    public void remove(int id) {
        SQLiteDatabase db = getDbOrThrow();

        db.beginTransaction();
        try {
            String filename = HybridFileBackedSqlHelpers.getEntryFilename(helper, table, id);
            if (filename != null) {
                File dataFile = new File(filename);
                dataFile.delete();
            }

            db.delete(table, DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void remove(List<Integer> ids) {
        if (ids.size() > 0) {
            SQLiteDatabase db = getDbOrThrow();
            db.beginTransaction();
            try {
                HybridFileBackedSqlHelpers.removeFiles(ids, helper, table);
                List<Pair<String, String[]>> whereParamList = AndroidTableBuilder.sqlList(ids);
                for (Pair<String, String[]> whereParams : whereParamList) {
                    String whereClause = DatabaseHelper.ID_COL + " IN " + whereParams.first;
                    db.delete(table, whereClause, whereParams.second);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    @Override
    public void removeAll() {
        SQLiteDatabase db = getDbOrThrow();

        db.beginTransaction();
        try {
            FileUtil.deleteFileOrDir(dbDir);
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
                case EntityFilter.PREFILTER_FILTER:
                    if (ef.matches(read(id))) {
                        removed.add(id);
                    }
            }
        }

        if (removed.size() > 0) {
            List<Pair<String, String[]>> whereParamList =
                    AndroidTableBuilder.sqlList(removed);

            SQLiteDatabase db = getDbOrThrow();

            db.beginTransaction();
            try {
                HybridFileBackedSqlHelpers.removeFiles(removed, helper, table);
                for (Pair<String, String[]> whereParams : whereParamList) {
                    String whereClause = DatabaseHelper.ID_COL + " IN " + whereParams.first;
                    db.delete(table, whereClause, whereParams.second);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        return removed;
    }

    @Override
    public SqlStorageIterator<T> iterate(boolean includeData) {
        SQLiteDatabase db = getDbOrThrow();

        SqlStorageIterator<T> spanningIterator =
                getIndexSpanningIteratorOrNull(db, includeData);
        if (spanningIterator != null) {
            return spanningIterator;
        } else {
            return new SqlFileBackedStorageIterator<>(getIterateCursor(db, includeData), this);
        }
    }

    @Override
    protected Cursor getIterateCursor(SQLiteDatabase db, boolean includeData) {
        if (includeData) {
            return db.query(table, dataColumns, null, null, null, null, null);
        } else {
            return db.query(table, new String[]{DatabaseHelper.ID_COL},
                    null, null, null, null, null);
        }
    }

    @Override
    public SqlStorageIterator<T> iterate(boolean includeData, String primaryId) {
        throw new UnsupportedOperationException("iterate method unsupported");
    }

    /**
     * For testing only
     */
    public File getDbDirForTesting() {
        return dbDir;
    }

    /**
     * For testing only
     */
    public String getEntryFilenameForTesting(int id) {
        return HybridFileBackedSqlHelpers.getEntryFilename(helper, table, id);
    }
}
