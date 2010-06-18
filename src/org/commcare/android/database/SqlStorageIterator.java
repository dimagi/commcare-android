/**
 * 
 */
package org.commcare.android.database;

import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.externalizable.Externalizable;

import android.database.Cursor;

/**
 * @author ctsims
 *
 */
public class SqlStorageIterator implements IStorageIterator {

	Cursor c;
	SqlIndexedStorageUtility storage;

	public SqlStorageIterator(Cursor c, SqlIndexedStorageUtility storage) {
		this.c = c;
		this.storage = storage;
		c.moveToFirst();
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#hasMore()
	 */
	public boolean hasMore() {
		return !c.isAfterLast();
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextID()
	 */
	public int nextID() {
		int id = c.getInt(c.getColumnIndexOrThrow(DbUtil.ID_COL));
		c.moveToNext();
		return id;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextRecord()
	 */
	public Externalizable nextRecord() {
		return storage.read(nextID());
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#numRecords()
	 */
	public int numRecords() {
		return c.getCount();
	}

}
