/**
 * 
 */
package org.commcare.android.database.cache;

import org.commcare.android.database.TableBuilder;

/**
 * @author ctsims
 *
 */
public class GeocoderDataCache {
	public static final String TABLE_NAME = "geocodercache";
	private static final String COL_KEY = "key";
	private static final String COL_VALUE = "value";
	private static final String COL_HIT = "";

	public static TableBuilder GetTableBuilder() {
		TableBuilder builder = new TableBuiler(TABLE_NAME);
		builder.addData(new String[] {"");
	}

}
