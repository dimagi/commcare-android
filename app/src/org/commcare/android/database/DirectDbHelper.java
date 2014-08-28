/**
 * 
 */
package org.commcare.android.database;

import net.sqlcipher.database.SQLiteDatabase;
import android.content.Context;


/**
 * @author ctsims
 *
 */
public class DirectDbHelper extends DbHelper {
    
    private SQLiteDatabase handle;

    public DirectDbHelper(Context c, SQLiteDatabase database) {
        super(c);
        handle = database;
    }

    @Override
    public SQLiteDatabase getHandle() {
        return handle;
    }
    
}
