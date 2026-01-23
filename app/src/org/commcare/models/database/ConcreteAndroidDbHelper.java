package org.commcare.models.database;

import android.content.Context;

/**
 * A Db Handler for direct DB Handle access, when
 * lazy handoff isn't necessary.
 *
 * @author ctsims
 */
public class ConcreteAndroidDbHelper extends AndroidDbHelper {
    private final IDatabase handle;

    public ConcreteAndroidDbHelper(Context c, IDatabase handle) {
        super(c);
        this.handle = handle;
    }

    @Override
    public IDatabase getHandle() {
        return handle;
    }

}
