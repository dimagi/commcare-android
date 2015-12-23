package org.commcare.android.database;

import android.database.Cursor;

import org.commcare.modern.database.DatabaseHelper;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author Phillip Mates (pmates@gmail.com)
 */
public class SqlFileBackedStorageIterator<T extends Persistable> extends SqlStorageIterator<T> {

    public SqlFileBackedStorageIterator(Cursor c, SqlFileBackedStorage<T> storage) {
        this(c, storage, null);
    }

    /**
     * Creates an iterator into either a full or partial sql storage.
     *
     * @param c         The uninitialized cursor for a query.
     * @param storage   The storage being queried
     * @param primaryId An optional key index for a primary id that is part
     *                  of the returned iterator
     */
    public SqlFileBackedStorageIterator(Cursor c, SqlFileBackedStorage<T> storage, String primaryId) {
        super(c, storage, primaryId);
    }

    @Override
    public T nextRecord() {
        String filename = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
        byte[] aesKeyBlob = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL));

        //we don't really use this
        nextID();

        return storage.newObject(((SqlFileBackedStorage<T>)storage).getFileInputStream(filename, aesKeyBlob));
    }
}
