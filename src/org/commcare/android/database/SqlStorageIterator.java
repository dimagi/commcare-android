/**
 * 
 */
package org.commcare.android.database;

import java.util.Iterator;

import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;

import android.database.Cursor;

/**
 * @author ctsims
 *
 */
public class SqlStorageIterator<T extends Persistable> implements IStorageIterator, Iterator<T> {

	Cursor c;
	SqlIndexedStorageUtility<T> storage;

	public SqlStorageIterator(Cursor c, SqlIndexedStorageUtility<T> storage) {
		this.c = c;
		this.storage = storage;
		c.moveToFirst();
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#hasMore()
	 */
	public boolean hasMore() {
		if(!c.isClosed()) {
			return !c.isAfterLast();
		} 
		return false;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextID()
	 */
	public int nextID() {
		int id = c.getInt(c.getColumnIndexOrThrow(DbUtil.ID_COL));
		c.moveToNext();
		if(c.isAfterLast()) {
			c.close();
		}
		return id;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextRecord()
	 */
	public T nextRecord() {
		return storage.read(nextID());
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#numRecords()
	 */
	public int numRecords() {
		return c.getCount();
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

}
