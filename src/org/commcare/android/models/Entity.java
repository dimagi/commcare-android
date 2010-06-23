/**
 * 
 */
package org.commcare.android.models;

import org.javarosa.core.services.storage.Persistable;


/**
 * @author ctsims
 *
 */
public class Entity<T extends Persistable> {
	
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
