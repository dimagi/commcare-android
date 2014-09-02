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
	String[] backgroundData;
	
	protected Entity(T t) {
		this.t = t;
	}
	
	public Entity(String[] data, String[] sortData, T t) {
		this.t = t;
		this.sortData = sortData;
		this.data = data;
	}
	
	public Entity(String[] data, String[] sortData, String[] backgroundData, T t) {
		this.t = t;
		this.sortData = sortData;
		this.data = data;
		this.backgroundData = backgroundData;
	}
	
	public String getField(int i) {
		return data[i];
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
	
	public String[] getData(){
		return data;
	}
	
	public String [] getBackgroundData(){
		return backgroundData;
	}
}
