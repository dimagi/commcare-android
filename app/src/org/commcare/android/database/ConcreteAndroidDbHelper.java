package org.commcare.android.database;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * A Db Handler for direct DB Handle access, when
 * lazy handoff isn't necessary.
 *
 * @author ctsims
 */
public class ConcreteAndroidDbHelper extends AndroidDbHelper {
    private final SQLiteDatabase handle;

    public ConcreteAndroidDbHelper(Context c, SQLiteDatabase handle) {
        super(c);
        this.handle = handle;
    }

    @Override
    public SQLiteDatabase getHandle() {
        return handle;
    }

}
