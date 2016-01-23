package org.commcare.android.database;

import android.database.Cursor;

import org.commcare.modern.database.DatabaseHelper;
import org.javarosa.core.services.storage.Persistable;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Iterator for storage layer that uses filesystem to store payload.
 *
 * @author Phillip Mates (pmates@gmail.com)
 */
public class HybridFileBackedStorageIterator<T extends Persistable>
        extends SqlStorageIterator<T> {

    /**
     * Creates an iterator into either a full or partial sql storage.
     *
     * @param c       The uninitialized cursor for a query.
     * @param storage The storage being queried
     */
    public HybridFileBackedStorageIterator(Cursor c,
                                           HybridFileBackedSqlStorage<T> storage) {
        super(c, storage, null);
    }

    @Override
    public T nextRecord() {
        byte[] blob =
                c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));
        if (blob != null) {
            return storage.newObject(blob, nextID());
        } else {
            String filename =
                    c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
            byte[] aesKeyBlob =
                    c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL));

            try {
                InputStream fileInputStream =
                        ((HybridFileBackedSqlStorage<T>)storage).getInputStreamFromFile(filename, aesKeyBlob);
                return storage.newObject(fileInputStream, nextID());
            } catch (FileNotFoundException e) {
                // TODO PLM: throw runtime or return null?
                throw new RuntimeException(e);
            }
        }
    }
}
