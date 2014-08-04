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
	String[] data;
	String[] sortData;
	boolean[] relevancyData;
	
	protected Entity(T t) {
		this.t = t;
	}
	
	public Entity(String[] data, String[] sortData, boolean[] relevancyData, T t) {
		this.t = t;
		this.sortData = sortData;
		this.data = data;
		this.relevancyData = relevancyData;
	}
	
	public String getField(int i) {
		return data[i];
	}
	
	public boolean isValidField(int i) {
		return !getField(i).equals("") && relevancyData[i];
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
