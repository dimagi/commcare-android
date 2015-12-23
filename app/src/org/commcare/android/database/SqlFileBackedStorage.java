package org.commcare.android.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CryptUtil;
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
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Sql logic for storing persistable objects. Uses the filesystem to store
 * persitables in _encrypted_ manner; useful when objects are larger than the
 * 1mb sql row limit.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SqlFileBackedStorage<T extends Persistable> extends SqlStorage<T> {
    private final File dbDir;

    /**
     * column selection used for reading file data
     */
    private final static String[] dataColumns =
            {DatabaseHelper.ID_COL, DatabaseHelper.FILE_COL, DatabaseHelper.AES_COL};

    /**
     * Sql object storage layer that stores serialized objects on the filesystem.
     *
     * @param table   name of database table
     * @param ctype   type of object being stored in this database
     * @param baseDir all files for entries will be placed within this dir
     */
    public SqlFileBackedStorage(String table,
                                Class<? extends T> ctype,
                                AndroidDbHelper helper,
                                String baseDir) {
        super(table, ctype, helper);

        dbDir = new File(baseDir + GlobalConstants.FILE_CC_DB + table);
        setupDir();
    }

    private void setupDir() {
        if (!dbDir.exists()) {
            if (!dbDir.mkdirs()) {
                String errorMsg = "unable to create db storage directory: " + dbDir;
                throw new RuntimeException(errorMsg);
            }
        }
    }

    @Override
    public Vector<T> getRecordsForValues(String[] fieldNames,
                                         Object[] values) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        Pair<String, String[]> whereClauseAndArgs =
                helper.createWhereAndroid(fieldNames, values, em, null);

        String[] columns = new String[]{DatabaseHelper.FILE_COL, DatabaseHelper.AES_COL};
        Cursor c = db.query(table, columns,
                whereClauseAndArgs.first, whereClauseAndArgs.second,
                null, null, null);
        try {
            if (c.getCount() == 0) {
                return new Vector<>();
            } else {
                c.moveToFirst();
                Vector<T> recordObjects = new Vector<>();
                int fileColIndex = c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL);
                int aesColIndex = c.getColumnIndexOrThrow(DatabaseHelper.AES_COL);
                while (!c.isAfterLast()) {
                    String filename = c.getString(fileColIndex);
                    byte[] aesKeyBlob = c.getBlob(aesColIndex);
                    InputStream inputStream = getInputStreamFromFile(filename, aesKeyBlob);
                    recordObjects.add(newObject(inputStream));
                    c.moveToNext();
                }
                return recordObjects;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected InputStream getInputStreamFromFile(String filename, byte[] aesKeyBytes) {
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        return EncryptionIO.getFileInputStream(filename, aesKey);
    }

    @Override
    public T getRecordForValues(String[] rawFieldNames, Object[] values)
            throws NoSuchElementException, InvalidIndexException {
        SQLiteDatabase appDb;
        try {
            appDb = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        Pair<String, String[]> whereArgsAndVals =
                helper.createWhereAndroid(rawFieldNames, values, em, null);
        Cursor c = appDb.query(table, dataColumns,
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
            String filename = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
            byte[] aesKeyBlob = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL));
            return newObject(getInputStreamFromFile(filename, aesKeyBlob));
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
        Pair<String, byte[]> filenameAndKeyBytes = getEntryFilenameAndKey(id);
        String filename = filenameAndKeyBytes.first;
        byte[] aesKeyBlob = filenameAndKeyBytes.second;

        InputStream is = getInputStreamFromFile(filename, aesKeyBlob);
        if (is == null) {
            throw new RuntimeException("Unable to open and decrypt file: " + filename);
        }

        return StreamsUtil.getStreamAsBytes(is);
    }

    private Pair<String, byte[]> getEntryFilenameAndKey(int id) {
        Cursor c;
        try {
            c = helper.getHandle().query(table, dataColumns, DatabaseHelper.ID_COL + "=?",
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
            File dataFile = newFileForEntry();
            ContentValues contentValues = helper.getNonDataContentValues(p);
            contentValues.put(DatabaseHelper.FILE_COL, dataFile.getAbsolutePath());
            byte[] key = generateKey(contentValues);
            long ret = db.insertOrThrow(table, DatabaseHelper.FILE_COL, contentValues);

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            p.setID((int)ret);

            writePersitableToEncryptedFile(p, dataFile.getAbsolutePath(), key);

            db.setTransactionSuccessful();
        } catch (IOException e) {
            // Failed to create new file
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    protected byte[] generateKey(ContentValues contentValues) {
        try {
            byte[] key = CommCareApplication._().createNewSymetricKey().getEncoded();
            contentValues.put(DatabaseHelper.AES_COL, key);
            return key;
        } catch (SessionUnavailableException e) {
            throw new RuntimeException("Session unavailable; can't generate encryption key.");
        }
    }

    private File newFileForEntry() throws IOException {
        File newFile = getUniqueFilename();

        if (!newFile.createNewFile()) {
            throw new RuntimeException("Trying to create a new file using existing filename; " +
                    "Shouldn't be possible since we already checked for uniqueness");
        }

        return newFile;
    }

    private File getUniqueFilename() {
        String uniqueSuffix = "_" + (Math.random() * 5);
        String timeAsString =
                DateTime.now().toString(DateTimeFormat.forPattern("MM_dd_yyyy_HH_mm_ss"));
        String filename = timeAsString + uniqueSuffix;
        File newFile = new File(dbDir, filename);
        // keep trying until we find a filename that doesn't already exist
        while (newFile.exists()) {
            newFile = getUniqueFilename();
        }
        return newFile;
    }

    @Override
    public int add(Externalizable externalizable) {
        throw new UnsupportedOperationException("Use 'SqlFileBackedStorage.write'");
    }

    @Override
    public void update(int id, Externalizable extObj) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException sue) {
            throw new UserStorageClosedException(sue.getMessage());
        }
        db.beginTransaction();
        try {
            db.update(table, helper.getNonDataContentValues(extObj),
                    DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});

            String filename = getEntryFilename(id);
            writePersitableToEncryptedFile(extObj, filename, getEntryAESKey(id));

            db.setTransactionSuccessful();
        } catch (IOException e) {
            // Failed to update file
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    protected void writePersitableToEncryptedFile(Externalizable externalizable,
                                                  String filename,
                                                  byte[] aesKeyBytes) throws IOException {
        DataOutputStream objectOutStream = null;
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        try {
            objectOutStream =
                    new DataOutputStream(EncryptionIO.createFileOutputStream(filename, aesKey));
            externalizable.writeExternal(objectOutStream);
        } finally {
            if (objectOutStream != null) {
                objectOutStream.close();
            }
        }
    }

    protected byte[] getEntryAESKey(int id) {
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
            String filename = getEntryFilename(id);
            File dataFile = new File(filename);
            dataFile.delete();

            db.delete(table, DatabaseHelper.ID_COL + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String getEntryFilename(int id) {
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

    @Override
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
            removeFiles(ids);
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

    private void removeFiles(List<Integer> idsBeingRemoved) {
        // delete files storing data for entries being removed
        for (Integer id : idsBeingRemoved) {
            File datafile = new File(getEntryFilename(id));
            datafile.delete();
        }
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

        if (removed.size() == 0) {
            return removed;
        }

        List<Pair<String, String[]>> whereParamList =
                AndroidTableBuilder.sqlList(removed);

        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        db.beginTransaction();
        try {
            removeFiles(removed);
            for (Pair<String, String[]> whereParams : whereParamList) {
                String whereClause = DatabaseHelper.ID_COL + " IN " + whereParams.first;
                db.delete(table, whereClause, whereParams.second);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return removed;
    }

    @Override
    public SqlStorageIterator<T> iterate(boolean includeData) {
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

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
        String[] projection;
        if (includeData) {
            projection = new String[]{
                    DatabaseHelper.ID_COL,
                    DatabaseHelper.FILE_COL,
                    DatabaseHelper.AES_COL};
        } else {
            projection = new String[]{DatabaseHelper.ID_COL};
        }
        return db.query(table, projection, null, null, null, null, null);
    }

    @Override
    public SqlStorageIterator<T> iterate(boolean includeData, String primaryId) {
        final String msg = "Custom iterate method is unsupported by SqlFileBackedStorage";
        throw new UnsupportedOperationException(msg);
    }

    /**
     * For testing only
     */
    public File getDbDir() {
        return dbDir;
    }

    /**
     * For testing only
     */
    public String getEntryFilenameForTesting(int id) {
        return getEntryFilename(id);
    }
}
