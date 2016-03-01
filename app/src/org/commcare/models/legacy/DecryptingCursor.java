package org.commcare.models.legacy;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import org.commcare.android.crypt.CipherPool;
import org.commcare.android.crypt.CryptUtil;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.models.EncryptedModel;

import javax.crypto.Cipher;

/**
 * @author ctsims
 */
public class DecryptingCursor extends SQLiteCursor {
    final Cipher cipher;
    final EncryptedModel model;
    final CipherPool pool;

    public DecryptingCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query, EncryptedModel model, CipherPool pool) {
        super(db, driver, editTable, query);
        this.model = model;
        this.pool = pool;
        this.cipher = pool.borrow();
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getBlob(columnIndex);
        } else {
            return decrypt(columnIndex);
        }
    }

    @Override
    public double getDouble(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getDouble(columnIndex);
        } else {
            return Double.valueOf(new String(decrypt(columnIndex)));
        }
    }

    @Override
    public float getFloat(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getFloat(columnIndex);
        } else {
            return Float.valueOf(new String(decrypt(columnIndex)));
        }
    }

    @Override
    public int getInt(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getInt(columnIndex);
        } else {
            return Integer.valueOf(new String(decrypt(columnIndex)));
        }
    }

    @Override
    public long getLong(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getLong(columnIndex);
        } else {
            return Long.valueOf(new String(decrypt(columnIndex)));
        }
    }

    @Override
    public short getShort(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getShort(columnIndex);
        } else {
            return Short.valueOf(new String(decrypt(columnIndex)));
        }
    }

    @Override
    public String getString(int columnIndex) {
        if (!isEncrypted(columnIndex)) {
            return super.getString(columnIndex);
        } else {
            return new String(decrypt(columnIndex));
        }
    }

    private boolean isEncrypted(int columnIndex) {
        String column = this.getColumnName(columnIndex);
        if (model.isEncrypted(column)) {
            return true;
        }
        return (column.equals(DatabaseHelper.DATA_COL) &&
                model.isBlobEncrypted());
    }

    private byte[] decrypt(int columnIndex) {
        byte[] data = super.getBlob(columnIndex);
        return CryptUtil.decrypt(data, cipher);
    }


    @Override
    public void close() {
        super.close();
        pool.remit(cipher);
    }
}
