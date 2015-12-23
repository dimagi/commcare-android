package org.commcare.android.database;

import android.database.Cursor;

import org.commcare.modern.database.DatabaseHelper;
import org.javarosa.core.services.storage.Persistable;

import java.io.InputStream;

/**
 * Iterator for storage layer that uses filesystem to store payload.
 *
 * @author Phillip Mates (pmates@gmail.com)
 */
public class SqlFileBackedStorageIterator<T extends Persistable>
        extends SqlStorageIterator<T> {

    /**
     * Creates an iterator into either a full or partial sql storage.
     *
     * @param c       The uninitialized cursor for a query.
     * @param storage The storage being queried
     */
    public SqlFileBackedStorageIterator(Cursor c,
                                        FileBackedSqlStorage<T> storage) {
        super(c, storage, null);
    }

    @Override
    public T nextRecord() {
        String filename =
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
        byte[] aesKeyBlob =
                c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL));

        //we don't really use this
        nextID();

        InputStream fileInputStream =
                ((FileBackedSqlStorage<T>)storage).getInputStreamFromFile(filename, aesKeyBlob);
        return storage.newObject(fileInputStream);
    }
}
