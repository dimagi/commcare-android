/**
 * 
 */
package org.commcare.android.database;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.InvalidIndexException;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.Externalizable;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * @author ctsims
 *
 */
public class SqlIndexedStorageUtility<T extends Persistable> implements IStorageUtilityIndexed, Iterable<T> {
	
	String table;
	Class<? extends T> ctype;
	Context c;
	T t;
	
	public SqlIndexedStorageUtility(String table, Class<? extends T> ctype, Context c) {
		this.table = table;
		this.ctype = ctype;
		this.c = c;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtilityIndexed#getIDsForValue(java.lang.String, java.lang.Object)
	 */
	public Vector getIDsForValue(String fieldName, Object value) {
		Cursor c = DbUtil.getHandle().query(table, new String[] {DbUtil.ID_COL, DbUtil.DATA_COL} , fieldName + "='" + value.toString() + "'", null,null, null, null);
		if(c.getCount() == 0) {
			return new Vector<Integer>();
		} else {
			c.moveToFirst();
			Vector<Integer> indices = new Vector<Integer>();
			int index = c.getColumnIndexOrThrow(DbUtil.ID_COL);
			while(!c.isAfterLast()) {		
				int id = c.getInt(index);
				indices.add(Integer.valueOf(id));
				c.moveToNext();
			}
			return indices;
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtilityIndexed#getRecordForValue(java.lang.String, java.lang.Object)
	 */
	public T getRecordForValue(String fieldName, Object value) throws NoSuchElementException, InvalidIndexException {
		Cursor c = DbUtil.getHandle().query(table, new String[] {DbUtil.DATA_COL} , fieldName + "='" + value.toString() +"'", null, null, null, null);
		if(c.getCount() == 0) {
			throw new NoSuchElementException("No element in table " + table + " with name " + fieldName +" and value " + value.toString());
		}
		if(c.getCount() > 1) {
			 throw new InvalidIndexException("Invalid unique column " + fieldName + ". Multiple records found with value " + value.toString(), fieldName);
		}
		c.moveToFirst();
		byte[] data = c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));
		return newObject(data);
	}
	
	public T newObject(byte[] data) {
		try {
			T e = (T)ctype.newInstance();
			e.readExternal(new DataInputStream(new ByteArrayInputStream(data)), DbUtil.getPrototypeFactory(c));
			
			return e;
			
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DeserializationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#add(org.javarosa.core.util.externalizable.Externalizable)
	 */
	public int add(Externalizable e) throws StorageFullException {
		SQLiteDatabase db = DbUtil.getHandle();
		int i = -1;
		try{
			db.beginTransaction();
			long ret = db.insertOrThrow(table, DbUtil.DATA_COL, DbUtil.getContentValues(e));
			
			if(ret > Integer.MAX_VALUE) {
				throw new RuntimeException("Waaaaaaaaaay too many values");
			}
		
			i =(int)ret;
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		
		return i;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#close()
	 */
	public void close() {
		DbUtil.getHandle().close();
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#destroy()
	 */
	public void destroy() {
		//nothing;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#exists(int)
	 */
	public boolean exists(int id) {
		Cursor c = DbUtil.getHandle().query(table, new String[] {DbUtil.ID_COL} , DbUtil.ID_COL +"="+String.valueOf(id), null, null, null, null);
		if(c.getCount() == 0) {
			return false;
		}
		if(c.getCount() > 1) {
			 throw new InvalidIndexException("Invalid ID column. Multiple records found with value " + id, "ID");
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#getAccessLock()
	 */
	public Object getAccessLock() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#getNumRecords()
	 */
	public int getNumRecords() {
		Cursor c = DbUtil.getHandle().query(table, new String[] {DbUtil.ID_COL} , null, null, null, null, null);
		return c.getCount();
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#getRecordSize(int)
	 */
	public int getRecordSize(int id) {
		//serialize and test blah blah.
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#getTotalSize()
	 */
	public int getTotalSize() {
		//serialize and test blah blah.
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#isEmpty()
	 */
	public boolean isEmpty() {
		if(getNumRecords() == 0) {
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#iterate()
	 */
	public SqlStorageIterator<T> iterate() {
		Cursor c = DbUtil.getHandle().query(table, new String[] {DbUtil.ID_COL} , null, null, null, null, null);
		return new SqlStorageIterator<T>(c, this);
	}
	
	public Iterator<T> iterator() {
		return iterate();
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#read(int)
	 */
	public T read(int id) {
		return newObject(readBytes(id));
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#readBytes(int)
	 */
	public byte[] readBytes(int id) {
		Cursor c = DbUtil.getHandle().query(table, new String[] {DbUtil.ID_COL, DbUtil.DATA_COL} , DbUtil.ID_COL +"="+String.valueOf(id), null, null, null, null);
		
		c.moveToFirst();
		return c.getBlob(c.getColumnIndexOrThrow(DbUtil.DATA_COL));
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#remove(int)
	 */
	public void remove(int id) {
		SQLiteDatabase db = DbUtil.getHandle();
		db.beginTransaction();
		try {
			db.delete(table, DbUtil.ID_COL +"="+String.valueOf(id),null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}	
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#remove(org.javarosa.core.services.storage.Persistable)
	 */
	public void remove(Persistable p) {
		this.remove(p.getID());
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#removeAll()
	 */
	public void removeAll() {
		SQLiteDatabase db = DbUtil.getHandle();
		db.beginTransaction();
		try {
			db.delete(table, null,null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#removeAll(org.javarosa.core.services.storage.EntityFilter)
	 */
	public Vector<Integer> removeAll(EntityFilter ef) {
		Vector<Integer> removed = new Vector<Integer>();
		for(IStorageIterator iterator = this.iterate() ; iterator.hasMore() ;) {
			int id = iterator.nextID();
			if(ef.matches(read(id))) {
				removed.add(id);
			}
		}
		String ids = "(";
		for(int i = 0; i < removed.size() ; ++i) { 
			ids += String.valueOf(i);
			if(i < removed.size() -1) {
				ids +=", ";
			}
		}
		ids +=")";
		
		SQLiteDatabase db = DbUtil.getHandle();
		db.beginTransaction();
		try {
			db.delete(table, DbUtil.ID_COL +" IN "+ ids,null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}	
		
		return removed;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#repack()
	 */
	public void repack() {
		//Unecessary!
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#repair()
	 */
	public void repair() { 
		//Unecessary!
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#update(int, org.javarosa.core.util.externalizable.Externalizable)
	 */
	public void update(int id, Externalizable e) throws StorageFullException {
		SQLiteDatabase db = DbUtil.getHandle();
		db.beginTransaction();
		try {
			db.update(table, DbUtil.getContentValues(e), DbUtil.ID_COL +"="+ String.valueOf(id), null);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageUtility#write(org.javarosa.core.services.storage.Persistable)
	 */
	public void write(Persistable p) throws StorageFullException {
		if(p.getID() != -1) {
			update(p.getID(), p);
			return;
		}
		SQLiteDatabase db = DbUtil.getHandle();
		try {
		
		db.beginTransaction();
		long ret = db.insertOrThrow(table, DbUtil.DATA_COL, DbUtil.getContentValues(p));
		
		if(ret > Integer.MAX_VALUE) {
			throw new RuntimeException("Waaaaaaaaaay too many values");
		}
		
		int id = (int)ret;
		//Now we need to put the id into the record
		
		p.setID(id);
		db.update(table, DbUtil.getContentValues(p), DbUtil.ID_COL +"="+ String.valueOf(id), null);
		
		db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

}
