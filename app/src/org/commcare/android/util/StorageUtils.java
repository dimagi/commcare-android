/**
 * 
 */
package org.commcare.android.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.javarosa.core.services.Logger;

/**
 * 
 * Simple utility/helper methods for common operations across
 * the applicaiton
 * 
 * @author ctsims
 *
 */
public class StorageUtils {
    @SuppressWarnings("deprecation")
    public static FormRecord[] getUnsentRecords(SqlStorage<FormRecord> storage) {
        //TODO: This could all be one big sql query instead of doing it in code
        
        //Get all forms which are either unsent or unprocessed
        Vector<Integer> ids;
        try {
            ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
            ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
        } catch (UserStorageClosedException e) {
            // the db was closed down
            return new FormRecord[0];
        }
        
        if(ids.size() == 0) {
            return new FormRecord[0];
        }
        
        //We need to give these ids a valid order so the server can process them correctly.
        //NOTE: This is slower than it need be. We could batch query this with SQL.
        final Hashtable<Integer, Long> idToDateIndex = new Hashtable<Integer, Long>();
        
        
        for(int id : ids) {
            //Last modified for a unsent and complete forms is the formEnd date that was captured and locked when form
            //entry, so it's a safe cannonical ordering
            String dateValue = storage.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);
            try {
                idToDateIndex.put(id, Date.parse(dateValue));
            } catch(IllegalArgumentException iae) {
                //As it turns out this string format is terrible! We need to use a diferent one in the future
                try {
                    //Try to use what the toString does on most devices
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
                    idToDateIndex.put(id, sdf.parse(dateValue).getTime());
                } catch (Exception e) {
                    //If it still doesn't work, fallback to using ids
                    Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Invalid date in last modified value: " + dateValue);
                    //For some reason this seems to be crashing on some devices... go with the next best ordering for now
                    idToDateIndex.put(id, Long.valueOf(id));
                }
            }
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
