/**
 * 
 */
package org.commcare.android.models;

import java.util.ArrayList;
import java.util.Collection;

import org.commcare.android.util.StringUtils;



/**
 * @author ctsims
 *
 */
public class Entity<T> {
    
    protected T t;
    Object[] data;
    String[] sortData;
    boolean[] relevancyData;
	String[] backgroundData;
    
    protected Entity(T t) {
        this.t = t;
    }
    
    public Entity(Object[] data, String[] sortData, String[] backgroundData, boolean[] relevancyData,  T t) {
        this.t = t;
        this.sortData = sortData;
        this.backgroundData = backgroundData;
        this.data = data;
        this.relevancyData = relevancyData;
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
    
    /**
     * @param i index of field
     * @return True iff the given field is relevant and has a non-blank value.
     */
    public boolean isValidField(int i) {
        return !getField(i).equals("") && relevancyData[i];
    }
    
    /**
     * Gets the indexed field used for searching and sorting these entities 
     * 
     * @return either the sort or the string field at the provided index, normalized
     * (IE: lowercase, etc) for searching.
     */
    public String getNormalizedField(int i) {
        String normalized = this.getFieldString(i);
        return StringUtils.normalize(normalized);
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
	
	public Object[] getData(){
		return data;
	}
	
	public String [] getBackgroundData(){
		return backgroundData;
	}
	
	public String[] getSortFieldPieces(int i) {
        String sortField = getSortField(i);
	    if(sortField == null ) {return new String[0];}
	    else {
            //We always fuzzy match on the sort field and only if it is available
            //(as a way to restrict possible matching)
            sortField = StringUtils.normalize(sortField);
            return sortField.split("\\s+");
        }
	}
}
