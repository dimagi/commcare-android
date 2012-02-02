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
	
	public Entity(String[] data, T t) {
		this.t = t;
		this.data = data;
	}
	
	public String[] getFields() {
		return data;
	}
	
	public T getElement() {
		return t;
	}
}
