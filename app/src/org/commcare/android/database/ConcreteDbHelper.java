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
