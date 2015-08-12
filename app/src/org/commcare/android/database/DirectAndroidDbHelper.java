package org.commcare.android.database;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;


/**
 * @author ctsims
 *
 */
public class DirectAndroidDbHelper extends AndroidDbHelper {
    
    private SQLiteDatabase handle;

    public DirectAndroidDbHelper(Context c, SQLiteDatabase database) {
        super(c);
        handle = database;
    }

    @Override
    public SQLiteDatabase getHandle() {
        return handle;
    }
    
}
