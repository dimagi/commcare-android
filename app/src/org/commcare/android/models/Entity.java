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
	
	public Entity(String[] data, String[] sortData, T t) {
		this.t = t;
		this.sortData = sortData;
		this.data = data;
	}
	
	public String[] getFields() {
		return data;
	}
	
	public String[] getSortFields() {
		return sortData;
	}
	
	public T getElement() {
		return t;
	}
}
