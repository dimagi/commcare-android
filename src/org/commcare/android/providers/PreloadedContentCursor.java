/**
 * 
 */
package org.commcare.android.providers;

import android.database.AbstractCursor;

/**
 * @author ctsims
 *
 */
public class PreloadedContentCursor extends AbstractCursor {
	
	String data;
	
	public PreloadedContentCursor(String data) {
		this.data = data;
	}

	@Override
	public String[] getColumnNames() {
		return new String[] { "data" };
	}

	@Override
	public int getCount() {
		return 1;
	}

	@Override
	public double getDouble(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public short getShort(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getString(int column) {
		return data;
	}

	@Override
	public boolean isNull(int column) {
		// TODO Auto-generated method stub
		return false;
	}
	
	
}
