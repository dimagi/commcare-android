/**
 * 
 */
package org.commcare.android.util;

import java.util.Enumeration;
import java.util.Hashtable;

import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.externalizable.Externalizable;

/**
 * @author ctsims
 *
 */
public class DummyStorageIterator implements IStorageIterator {
	Hashtable<Integer, Externalizable> data;
	Enumeration<Integer> en;
	

	public DummyStorageIterator(Hashtable<Integer, Externalizable> data) {
		this.data = data;
		en = data.keys();
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#hasMore()
	 */
	public boolean hasMore() {
		return en.hasMoreElements();
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextID()
	 */
	public int nextID() {
		return en.nextElement().intValue();
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextRecord()
	 */
	public Externalizable nextRecord() {
		return data.get(en.nextElement());
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#numRecords()
	 */
	public int numRecords() {
		return data.size();
	}

}
