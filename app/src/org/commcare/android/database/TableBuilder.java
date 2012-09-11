/**
 * 
 */
package org.commcare.android.database;

import java.util.Collection;
import java.util.Vector;

import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;

import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class TableBuilder {
	
	private String name;
	
	private Vector<String> cols;
	
	public TableBuilder(String name) {
		this.name = name;
		cols = new Vector<String>();
	}
	
	public void addData(Persistable p) {
		cols.add(DbUtil.ID_COL + " INTEGER PRIMARY KEY");
		
		if(p instanceof IMetaData) {
			String[] keys = ((IMetaData)p).getMetaDataFields();
			for(String key : keys) {
				if(p instanceof EncryptedModel && ((EncryptedModel)p).isEncrypted(key)) {
					cols.add(scrubName(key) + " BLOB");
				} else {
					cols.add(scrubName(key));
				}
			}
		}
		
		cols.add(DbUtil.DATA_COL + " BLOB");
	}
	
	public void addData(String[] columns) {
		cols.add(DbUtil.ID_COL + " INTEGER PRIMARY KEY");

		for(String c : columns) {
			cols.add(scrubName(c));
		}
	}
	
	public String getTableCreateString() {
		
		String built = "CREATE TABLE " + scrubName(name) + " (";
		for(int i = 0 ; i < cols.size() ; ++i) {
			built += cols.elementAt(i);
			if(i < cols.size() - 1) {
				built += ",";
			}
		}
		built += ");";
		return built;
	}
	
	public static String scrubName(String input) {
		//Scrub
		return input.replace("-", "_");
	}
	
	public static Pair<String, String[]> sqlList(Collection<Integer> input) {
		//I want list comprehensions so bad right now.
		String ret = "(";
		for(int i : input) {
			ret += "?" + ",";
		}
		
		String[] array = new String[input.size()];
		int count = 0 ;
		for(Integer i : input) {
			array[count++] = String.valueOf(i);
		}
		return new Pair<String, String[]>(ret.substring(0, ret.length()-1) + ")", array);
	}
}
