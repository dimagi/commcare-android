/**
 * 
 */
package org.commcare.android.database;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

/**
 * @author ctsims
 *
 */
public class DecryptingCursor extends SQLiteCursor {

	public DecryptingCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
		super(db, driver, editTable, query);
	}

	@Override
	public byte[] getBlob(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getBlob(columnIndex);
	}

	@Override
	public double getDouble(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getDouble(columnIndex);
	}

	@Override
	public float getFloat(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getFloat(columnIndex);
	}

	@Override
	public int getInt(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getInt(columnIndex);
	}

	@Override
	public long getLong(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getLong(columnIndex);
	}

	@Override
	public short getShort(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getShort(columnIndex);
	}

	@Override
	public String getString(int columnIndex) {
		// TODO Auto-generated method stub
		return super.getString(columnIndex);
	}

	@Override
	public boolean isBlob(int columnIndex) {
		// TODO Auto-generated method stub
		return super.isBlob(columnIndex);
	}

	@Override
	public boolean isFloat(int columnIndex) {
		// TODO Auto-generated method stub
		return super.isFloat(columnIndex);
	}

	@Override
	public boolean isLong(int columnIndex) {
		// TODO Auto-generated method stub
		return super.isLong(columnIndex);
	}

	@Override
	public boolean isNull(int columnIndex) {
		// TODO Auto-generated method stub
		return super.isNull(columnIndex);
	}

	@Override
	public boolean isString(int columnIndex) {
		// TODO Auto-generated method stub
		return super.isString(columnIndex);
	}

}
