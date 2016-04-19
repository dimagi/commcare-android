package org.commcare.models.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.interfaces.AppFilePathBuilder;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.utils.AndroidStreamUtil;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.InvalidIndexException;
import org.javarosa.core.util.externalizable.Externalizable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
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
     * Column selection used for reading file data:
     * - Id column needed to correctly set the id of objects read from db, which isn't set at write time for efficiency.
     * - Data column holds serialized objects under 1mb
     * - File column points to file holding serialized object over 1mb
     * - Aes column holds encryption key for objects saved to filesystem
     *
     * Constraint: we never expect both data and file/aes columns to contain data at the same time
     */
    protected final static String[] dataColumns =
            {DatabaseHelper.ID_COL, DatabaseHelper.DATA_COL,
                    DatabaseHelper.FILE_COL, DatabaseHelper.AES_COL};

    /**
     * Sql object storage layer that stores serialized objects on the filesystem.
     *
     * @param tableName     name of database table
     * @param classType     type of object being stored in this database
     * @param directoryName Name of storage root subdir where entry files are placed
     * @param fsPathBuilder Resolves a db dir path to the correct app dir in external storage
     */
    public HybridFileBackedSqlStorage(String tableName,
                                      Class<? extends T> classType,
                                      AndroidDbHelper dbHelper,
                                      String directoryName,
                                      AppFilePathBuilder fsPathBuilder) {
        super(tableName, classType, dbHelper);

        final String subPath = GlobalConstants.FILE_CC_DB + directoryName + "/_" + tableName;
        dbDir = new File(fsPathBuilder.fsPath(subPath));
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

        Cursor cur = db.query(table, dataColumns,
                whereClauseAndArgs.first, whereClauseAndArgs.second,
                null, null, null);
        try {
            Vector<T> recordObjects = new Vector<>();
            if (cur.getCount() > 0) {
                cur.moveToFirst();
                int dataColIndex = cur.getColumnIndexOrThrow(DatabaseHelper.DATA_COL);
                int fileColIndex = cur.getColumnIndexOrThrow(DatabaseHelper.FILE_COL);
                int aesColIndex = cur.getColumnIndexOrThrow(DatabaseHelper.AES_COL);
                int idColIndex = cur.getColumnIndexOrThrow(DatabaseHelper.ID_COL);
                while (!cur.isAfterLast()) {
                    byte[] serializedObj = cur.getBlob(dataColIndex);
                    int dbEntryId = cur.getInt(idColIndex);
                    if (serializedObj != null) {
                        // serialized object was small enough to fit in db entry
                        recordObjects.add(newObject(serializedObj, dbEntryId));
                    } else {
                        // serialized object was stored in filesystem due to large size
                        recordObjects.add(readObjectFromFile(cur, fileColIndex, aesColIndex, dbEntryId));
                    }
                    cur.moveToNext();
                }
            }
            return recordObjects;
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    }

    private T readObjectFromFile(Cursor cursor, int dbEntryId) {
        return readObjectFromFile(cursor,
                cursor.getColumnIndexOrThrow(DatabaseHelper.FILE_COL),
                cursor.getColumnIndexOrThrow(DatabaseHelper.AES_COL),
                dbEntryId);
    }

    private T readObjectFromFile(Cursor cursor, int fileColIndex,
                                 int aesColIndex, int dbEntryId) {
        String filename = cursor.getString(fileColIndex);
        byte[] aesKeyBlob = cursor.getBlob(aesColIndex);
        InputStream inputStream = null;
        try {
            inputStream = getInputStreamFromFile(filename, aesKeyBlob);
            return newObject(inputStream, dbEntryId);
        } catch (FileNotFoundException e) {
            // TODO PLM: throw runtime or return null?
            throw new RuntimeException(e);
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

    protected InputStream getInputStreamFromFile(String filename, byte[] aesKeyBytes) throws FileNotFoundException {
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        return EncryptionIO.getFileInputStream(filename, aesKey);
    }

    @Override
    public T getRecordForValues(String[] rawFieldNames, Object[] values)
            throws NoSuchElementException, InvalidIndexException {
        SQLiteDatabase db = getDbOrThrow();

        Pair<String, String[]> whereArgsAndVals =
                helper.createWhereAndroid(rawFieldNames, values, em, null);
        Cursor cur = db.query(table, dataColumns,
                whereArgsAndVals.first, whereArgsAndVals.second,
                null, null, null);
        try {
            int queryCount = cur.getCount();
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
            cur.moveToFirst();
            byte[] serializedObj = cur.getBlob(cur.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
            int dbEntryId = cur.getInt(cur.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
            if (serializedObj != null) {
                return newObject(serializedObj, dbEntryId);
            } else {
                return readObjectFromFile(cur, dbEntryId);
            }
        } finally {
            if (cur != null) {
                cur.close();
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
        Cursor cur = getDbOrThrow().query(table, dataColumns,
                DatabaseHelper.ID_COL + "=?",
                new String[]{String.valueOf(id)}, null, null, null);

        InputStream is = null;
        try {
            cur.moveToFirst();
            byte[] serializedObj = cur.getBlob(cur.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
            if (serializedObj != null) {
                return serializedObj;
            } else {
                String filename = cur.getString(cur.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
                byte[] aesKeyBlob = cur.getBlob(cur.getColumnIndexOrThrow(DatabaseHelper.AES_COL));
                is = getInputStreamFromFile(filename, aesKeyBlob);

                return AndroidStreamUtil.inputStreamToByteArray(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to read serialized object from file.", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (cur != null) {
                cur.close();
            }
        }
    }

    @Override
    public void write(Persistable persistable) {
        if (persistable.getID() != -1) {
            update(persistable.getID(), persistable);
            return;
        }
        SQLiteDatabase db = getDbOrThrow();

        try {

            long insertedId;
            ByteArrayOutputStream bos = writeExternalizableToStream(persistable);
            String dataFilePath = null;
            try {
                if (blobFitsInDb(bos)) {
                    db.beginTransaction();
                    // serialized object small enough to fit in db
                    ContentValues contentValues = helper.getNonDataContentValues(persistable);
                    contentValues.put(DatabaseHelper.DATA_COL, bos.toByteArray());
                    insertedId = db.insertOrThrow(table, DatabaseHelper.DATA_COL, contentValues);
                } else {
                    // store serialized object in file and file pointer in db
                    dataFilePath = HybridFileBackedSqlHelpers.newFileForEntry(dbDir).getAbsolutePath();
                    HybridFileBackedSqlHelpers.setFileAsOrphan(db, dataFilePath);

                    db.beginTransaction();
                    ContentValues contentValues = helper.getNonDataContentValues(persistable);
                    contentValues.put(DatabaseHelper.FILE_COL, dataFilePath);
                    byte[] key = generateKeyAndAdd(contentValues);
                    insertedId = db.insertOrThrow(table, DatabaseHelper.FILE_COL, contentValues);

                    writeStreamToFile(bos, dataFilePath, key);
                }
            } finally {
                bos.close();
            }
            // won't effect already stored obj id, which is set when reading out of db.
            // rather, needed in case persistable object is used after being written to storage.
            persistable.setID((int)insertedId);

            if (insertedId > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            if (dataFilePath != null) {
                HybridFileBackedSqlHelpers.unsetFileAsOrphan(db, dataFilePath);
            }
            db.setTransactionSuccessful();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            db.endTransaction();
        }
    }

    private ByteArrayOutputStream writeExternalizableToStream(Externalizable extObj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            extObj.writeExternal(new DataOutputStream(bos));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize externalizable", e);
        }
        return bos;
    }

    protected boolean blobFitsInDb(ByteArrayOutputStream blobStream) {
        return blobStream.size() < ONE_MB_DB_SIZE_LIMIT;
    }

    protected byte[] generateKeyAndAdd(ContentValues contentValues) {
        try {
            byte[] key = CommCareApplication._().createNewSymmetricKey().getEncoded();
            contentValues.put(DatabaseHelper.AES_COL, key);
            return key;
        } catch (SessionUnavailableException e) {
            throw new RuntimeException("Session unavailable; can't generate encryption key.", e);
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
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
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

        ByteArrayOutputStream bos = null;
        try {
            Pair<String, byte[]> filenameAndKey =
                    HybridFileBackedSqlHelpers.getEntryFilenameAndKey(helper, table, id);
            String filename = filenameAndKey.first;
            byte[] fileEncryptionKey = filenameAndKey.second;
            boolean objectInDb = (filename == null);

            bos = writeExternalizableToStream(extObj);
            if (blobFitsInDb(bos)) {
                db.beginTransaction();
                updateEntryToStoreInDb(extObj, objectInDb, filename, bos, db, id);
            } else {
                String newFilePath = HybridFileBackedSqlHelpers.newFileForEntry(dbDir).getAbsolutePath();
                HybridFileBackedSqlHelpers.setFileAsOrphan(db, newFilePath);

                db.beginTransaction();
                updateEntryToStoreInFs(extObj, objectInDb, filename,
                        newFilePath, fileEncryptionKey, bos, db, id);
            }

            db.setTransactionSuccessful();
        } catch (IOException e) {
            throw new RuntimeException("Unable update db entry to store data in filesystem", e);
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
        ContentValues updatedContentValues =
                helper.getContentValuesWithCustomData(extObj, bos.toByteArray());
        if (!objectInDb) {
            // was stored in file: remove file and store in db
            updatedContentValues.put(DatabaseHelper.FILE_COL, (String)null);
            updatedContentValues.put(DatabaseHelper.AES_COL, (byte[])null);

            HybridFileBackedSqlHelpers.setFileAsOrphan(db, filename);
        }
        db.update(table, updatedContentValues,
                DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
    }

    private void updateEntryToStoreInFs(Externalizable extObj, boolean objectInDb,
                                        String currentFilePath, String newFilePath,
                                        byte[] fileEncryptionKey,
                                        ByteArrayOutputStream bos,
                                        SQLiteDatabase db,
                                        int id) throws IOException {
        ContentValues updatedContentValues;
        if (objectInDb) {
            // was in db but is now to big, null db data entry and write to file
            updatedContentValues = helper.getContentValuesWithCustomData(extObj, null);
            updatedContentValues.put(DatabaseHelper.FILE_COL, newFilePath);
            fileEncryptionKey = generateKeyAndAdd(updatedContentValues);
        } else {
            // was stored in a file all along, atomically update file by
            // writing to new and removing old file
            updatedContentValues = helper.getNonDataContentValues(extObj);
            updatedContentValues.put(DatabaseHelper.FILE_COL, newFilePath);

            HybridFileBackedSqlHelpers.setFileAsOrphan(db, currentFilePath);
        }
        db.update(table, updatedContentValues,
                DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});

        writeStreamToFile(bos, newFilePath, fileEncryptionKey);
        HybridFileBackedSqlHelpers.unsetFileAsOrphan(db, newFilePath);
    }

    @Override
    public void remove(int id) {
        SQLiteDatabase db = getDbOrThrow();

        String filename = HybridFileBackedSqlHelpers.getEntryFilename(helper, table, id);
        db.beginTransaction();
        try {
            db.delete(table, DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (filename != null) {
            File dataFile = new File(filename);
            dataFile.delete();
        }
    }

    @Override
    public void remove(List<Integer> ids) {
        if (ids.size() > 0) {
            SQLiteDatabase db = getDbOrThrow();
            db.beginTransaction();
            List<String> filesToRemove;
            try {
                filesToRemove = HybridFileBackedSqlHelpers.getFilesToRemove(ids, helper, table);
                List<Pair<String, String[]>> whereParamList = AndroidTableBuilder.sqlList(ids);
                for (Pair<String, String[]> whereParams : whereParamList) {
                    String whereClause = DatabaseHelper.ID_COL + " IN " + whereParams.first;
                    db.delete(table, whereClause, whereParams.second);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            HybridFileBackedSqlHelpers.removeFiles(filesToRemove);
        }
    }

    @Override
    public void removeAll() {
        SQLiteDatabase db = getDbOrThrow();

        db.beginTransaction();
        try {
            db.delete(table, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        FileUtil.deleteFileOrDir(dbDir);
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

            List<String> filesToRemove;
            db.beginTransaction();
            try {
                filesToRemove = HybridFileBackedSqlHelpers.getFilesToRemove(removed, helper, table);
                for (Pair<String, String[]> whereParams : whereParamList) {
                    String whereClause = DatabaseHelper.ID_COL + " IN " + whereParams.first;
                    db.delete(table, whereClause, whereParams.second);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            HybridFileBackedSqlHelpers.removeFiles(filesToRemove);
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
            return new HybridFileBackedStorageIterator<>(getIterateCursor(db, includeData), this);
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
