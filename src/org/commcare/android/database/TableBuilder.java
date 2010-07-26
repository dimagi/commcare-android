/**
 * 
 */
package org.commcare.android.database;

import java.util.Vector;

import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;

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
					cols.add(scrubInput(key) + " BLOB");
				} else {
					cols.add(scrubInput(key));
				}
			}
		}
		
		cols.add(DbUtil.DATA_COL + " BLOB");
	}
	
	public String getTableCreateString() {
		
		String built = "CREATE TABLE " + scrubInput(name) + " (";
		for(int i = 0 ; i < cols.size() ; ++i) {
			built += cols.elementAt(i);
			if(i < cols.size() - 1) {
				built += ",";
			}
		}
		built += ");";
		return built;
	}
	
	public static String scrubInput(String input) {
		//Scrub
		return input;
	}
}
