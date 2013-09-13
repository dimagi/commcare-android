/**
 * 
 */
package org.commcare.android.database;

import net.sqlcipher.database.SQLiteDatabase;
import android.content.Context;

/**
 * A Db Handler for direct DB Handle access, when
 * lazy handoff isn't necessary.
 * 
 * @author ctsims
 *
 */
public class ConcreteDbHelper extends DbHelper {
	private SQLiteDatabase handle;

	public ConcreteDbHelper(Context c, SQLiteDatabase handle) {
		super(c);
		this.handle = handle;
	}

	@Override
	public SQLiteDatabase getHandle() {
		return handle;
	}

}
