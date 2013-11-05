/**
 * 
 */
package org.commcare.android.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;

/**
 * 
 * Simple utility/helper methods for common operations across
 * the applicaiton
 * 
 * @author ctsims
 *
 */
public class StorageUtils {
	public static FormRecord[] getUnsentRecords(SqlStorage<FormRecord> storage) {
		//TODO: This could all be one big sql query instead of doing it in code
		
		
    	//Get all forms which are either unsent or unprocessed
    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
    	
    	if(ids.size() == 0) {
    		return new FormRecord[0];
    	}
    	
		//We need to give these ids a valid order so the server can process them correctly.
		//NOTE: This is slower than it need be. We could batch query this with SQL.
		final Hashtable<Integer, Long> idToDateIndex = new Hashtable<Integer, Long>();
		
		
		for(int id : ids) {
			//Last modified for a unsent and complete forms is the formEnd date that was captured and locked when form
			//entry, so it's a safe cannonical ordering
			idToDateIndex.put(id, Date.parse(storage.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED)));
		}
		
		
		Collections.sort(ids, new Comparator<Integer>() {

			@Override
			public int compare(Integer lhs, Integer rhs) {
				Long lhd = idToDateIndex.get(lhs);
				Long rhd = idToDateIndex.get(rhs);
				if(lhd < rhd ) { return -1;}
				if(lhd > rhd) { return 1;}
				return 0;
			}
			
		});
		
		//The records should now be in order and we can pass to the next phase 
		FormRecord[] records = new FormRecord[ids.size()];
		for(int i = 0 ; i < ids.size() ; ++i) {
			records[i] = storage.read(ids.elementAt(i).intValue());
		}
		return records;
	}
}
