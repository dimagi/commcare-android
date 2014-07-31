/**
 * 
 */
package org.commcare.android.models;



/**
 * @author ctsims
 *
 */
public class Entity<T> {
	
	T t;
	Object[] data;
	String[] sortData;
	
	protected Entity(T t) {
		this.t = t;
	}
	
	public Entity(Object[] data, String[] sortData, T t) {
		this.t = t;
		this.sortData = sortData;
		this.data = data;
	}
	
	public Object getField(int i) {
		return data[i];
	}
	
	/*
	 * Same as getField, but guaranteed to return a string.
	 * If field is not already a string, will return blank string.
	 */
	public String getFieldString(int i) {
		Object field = getField(i);
		if (field instanceof String) {
			return (String) field;
		}
		return "";
	}
	
	public String getSortField(int i) {
		return sortData[i];
	}
	
	public T getElement() {
		return t;
	}

	public int getNumFields() {
		return data.length;
	}
}
