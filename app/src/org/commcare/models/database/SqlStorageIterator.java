package org.commcare.models.database;

import android.database.Cursor;

import org.commcare.cases.model.Case;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.database.TableBuilder;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageModifiedException;
import org.javarosa.core.util.ArrayUtilities;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author ctsims
 */
public class SqlStorageIterator<T extends Persistable> implements IStorageIterator, Iterator<T> {

    final Cursor c;
    protected final SqlStorage<T> storage;
    private boolean isClosedByProgress = false;
    private final int count;
    private final Set<String> metaDataIndexSet;

    /**
     * only for use by subclasses which re-implement this behavior strategically (Note: Should be an interface pullout
     * not a subclass)
     */
    SqlStorageIterator(Cursor cursor) {
        this.c = cursor;
        storage = null;

        //This will result in the right exception being thrown if metadata is missing
        metaDataIndexSet = new HashSet<>();
        count = -1;
    }

    public SqlStorageIterator(Cursor c, SqlStorage<T> storage) {
        this(c, storage, new String[]{});
    }

    /**
     * Creates an iterator into either a full or partial sql storage.
     *
     * @param c         The uninitialized cursor for a query.
     * @param storage   The storage being queried
     * @param metaDataIndexSet An optional set of metadata which are included "for free" in the
     *                         iterator.
     */
    public SqlStorageIterator(Cursor c, SqlStorage<T> storage, String[] metaDataIndexSet) {
        this.c = c;
        this.storage = storage;
        this.metaDataIndexSet = new HashSet<>(ArrayUtilities.toVector(metaDataIndexSet));
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
        int id = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
        c.moveToNext();
        if (c.isAfterLast()) {
            c.close();
            isClosedByProgress = true;
        }
        return id;
    }

    @Override
    public T nextRecord() {
        byte[] data = c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.DATA_COL));

        return storage.newObject(data, nextID());
    }

    @Override
    public int numRecords() {
        return count;
    }

    @Override
    public boolean hasNext() {
        return hasMore();
    }

    @Override
    public T next() {
        return nextRecord();
    }

    @Override
    public void remove() {
        //Unsupported for now
    }

    @Override
    public int peekID() {
        return c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.ID_COL));
    }

    public String getIncludedMetadata(String metadataKey) {
        if(!metaDataIndexSet.contains(metadataKey)) {
            throw new RuntimeException("Invalid iterator metadata request for key: " + metadataKey);
        }
        String columnName = TableBuilder.scrubName(metadataKey);
        return c.getString(c.getColumnIndexOrThrow(columnName));
    }
}
