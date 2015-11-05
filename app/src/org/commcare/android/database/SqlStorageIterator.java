package org.commcare.android.database;

import android.database.Cursor;

import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageModifiedException;

import java.util.Iterator;

/**
 * @author ctsims
 */
public class SqlStorageIterator<T extends Persistable> implements IStorageIterator, Iterator<T> {

    Cursor c;
    private SqlStorage<T> storage;
    private boolean isClosedByProgress = false;
    private int count;
    private String primaryId;

    /**
     * only for use by subclasses which re-implement this behavior strategically (Note: Should be an interface pullout
     * not a subclass)
     */
    protected SqlStorageIterator() {

    }

    public SqlStorageIterator(Cursor c, SqlStorage<T> storage) {
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
    public SqlStorageIterator(Cursor c, SqlStorage<T> storage, String primaryId) {
        this.c = c;
        this.storage = storage;
        this.primaryId = primaryId;
        count = c.getCount();
        if (count == 0) {
            c.close();
            isClosedByProgress = true;
        } else {
            c.moveToFirst();
        }
    }

    @Override
    public boolean hasMore() {
        if (!c.isClosed()) {
            return !c.isAfterLast();
        } else {
            if (isClosedByProgress) {
                return false;
            } else {
                //If we didn't close the cursor as part of the iterator, it means that it
                //was forcibly invalidated externally, fail accordingly.
                throw new StorageModifiedException("Storage Iterator [" + storage.table + "]" + " was invalidated");
            }
        }
    }

    @Override
    public int nextID() {
        int id = c.getInt(c.getColumnIndexOrThrow(DbUtil.ID_COL));
        c.moveToNext();
        if (c.isAfterLast()) {
            c.close();
            isClosedByProgress = true;
        }
        return id;
    }

    @Override
    public T nextRecord() {
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));

        //we don't really use this
        nextID();
        return storage.newObject(data);
    }

    @Override
    public int numRecords() {
        return count;
    }

    public boolean hasNext() {
        return hasMore();
    }

    public T next() {
        return nextRecord();
    }

    public void remove() {
        //Unsupported for now
    }

    public int peekID() {
        int id = c.getInt(c.getColumnIndexOrThrow(DbUtil.ID_COL));
        return id;
    }

    //TESTING ONLY

    private byte[] getBlob() {
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));

        return data;
    }

    private Cursor getRawCursor() {
        return c;
    }

    public String getPrimaryId() {
        return c.getString(c.getColumnIndexOrThrow(primaryId));
    }
}
