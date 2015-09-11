package org.commcare.android.database;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;


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
