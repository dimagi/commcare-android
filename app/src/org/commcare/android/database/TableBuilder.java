/**
 * 
 */
package org.commcare.android.database;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Vector;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Table;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;

import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class TableBuilder {
	
	private String name;
	private Class c;
	
	private Vector<String> cols;
	private Vector<String> rawCols;
	
	public TableBuilder(Class c) {
		this.c = c;
		Table t = (Table)c.getAnnotation(Table.class);
		this.name = t.value();		
		
		cols = new Vector<String>();
		rawCols = new Vector<String>();
		
		addData(c);
	}
	public void addData(Class c) {
		cols.add(DbUtil.ID_COL + " INTEGER PRIMARY KEY");
		rawCols.add(DbUtil.ID_COL);
		
		for(Field f : c.getDeclaredFields()) {
			if(f.isAnnotationPresent(MetaField.class)) {
				MetaField mf = f.getAnnotation(MetaField.class);
				
				String key = mf.value();
				String columnName = scrubName(key);
				rawCols.add(columnName);
				String columnDef;
				columnDef = columnName;
				
				//Modifiers
				if(unique.contains(columnName) || mf.unique()) {
					columnDef += " UNIQUE";
				}
				cols.add(columnDef);
			}
		}
		
		cols.add(DbUtil.DATA_COL + " BLOB");
		rawCols.add(DbUtil.DATA_COL);
	}
	
	
	//Option Two - For models not made natively
	public TableBuilder(String name) {
		this.name = name;
		cols = new Vector<String>();
		rawCols = new Vector<String>();
	}
	
	public void addData(Persistable p) {
		cols.add(DbUtil.ID_COL + " INTEGER PRIMARY KEY");
		rawCols.add(DbUtil.ID_COL);
		
		if(p instanceof IMetaData) {
			String[] keys = ((IMetaData)p).getMetaDataFields();
			for(String key : keys) {
				String columnName = scrubName(key);
				rawCols.add(columnName);
				String columnDef = columnName;
				
				//Modifiers
				if(unique.contains(columnName)) {
					columnDef += " UNIQUE";
				}
				cols.add(columnDef);
			}
		}
		
		cols.add(DbUtil.DATA_COL + " BLOB");
		rawCols.add(DbUtil.DATA_COL);
	}

	
	HashSet<String> unique = new HashSet<String>();
	public void setUnique(String columnName) {
		unique.add(scrubName(columnName));
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

	public String getColumns() {
		String columns = "";
		for(int i = 0 ; i < rawCols.size() ; ++i) {
			columns += rawCols.elementAt(i);
			if(i < rawCols.size() - 1) {
				columns += ",";
			}
		}
		return columns;
	}
}
