package org.commcare.android.db.legacy;

import java.io.IOException;

import javax.crypto.Cipher;

import org.commcare.android.crypt.CipherPool;
import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.EncryptedModel;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
/**
 * @author ctsims
 *
 */
public class DecryptingCursor extends SQLiteCursor {
    Cipher cipher;
    EncryptedModel model;
    CipherPool pool;

    public DecryptingCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query, EncryptedModel model, CipherPool pool) {
        super(db, driver, editTable, query);
        this.model = model;
        this.pool = pool;
        this.cipher = pool.borrow();
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getBlob(int)
     */
    @Override
    public byte[] getBlob(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getBlob(columnIndex);
        } else {
            return decrypt(columnIndex);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getDouble(int)
     */
    @Override
    public double getDouble(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getDouble(columnIndex);
        } else {
            return Double.valueOf(new String(decrypt(columnIndex)));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getFloat(int)
     */
    @Override
    public float getFloat(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getFloat(columnIndex);
        } else {
            return Float.valueOf(new String(decrypt(columnIndex)));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getInt(int)
     */
    @Override
    public int getInt(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getInt(columnIndex);
        } else {
            return Integer.valueOf(new String(decrypt(columnIndex)));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getLong(int)
     */
    @Override
    public long getLong(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getLong(columnIndex);
        } else {
            return Long.valueOf(new String(decrypt(columnIndex)));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getShort(int)
     */
    @Override
    public short getShort(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getShort(columnIndex);
        } else {
            return Short.valueOf(new String(decrypt(columnIndex)));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.database.AbstractWindowedCursor#getString(int)
     */
    @Override
    public String getString(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.getString(columnIndex);
        } else {
            return new String(decrypt(columnIndex));
        }
    }
/**
    @Override
    public boolean isBlob(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.isBlob(columnIndex);
        }
    }

    @Override
    public boolean isFloat(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.isFloat(columnIndex);
        }
        
    }

    @Override
    public boolean isLong(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.isLong(columnIndex);
        }
    }

    @Override
    public boolean isNull(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.isNull(columnIndex);
        }
    }

    @Override
    public boolean isString(int columnIndex) {
        if(!isEncrypted(columnIndex)) {
            return super.isString(columnIndex);
        }
    }
    **/
    
    private boolean isEncrypted(int columnIndex) {
        String column = this.getColumnName(columnIndex);
        if(model.isEncrypted(column)) {
            return true;
        } if(column.equals(DbUtil.DATA_COL)) {
            return model.isBlobEncrypted();
        }
        return false;
    }
    
    private byte[] decrypt(int columnIndex) {
        byte[] data = super.getBlob(columnIndex);
        return CryptUtil.decrypt(data, cipher);
    }
    
    
    /* (non-Javadoc)
     * @see android.database.sqlite.SQLiteCursor#close()
     */
    @Override
    public void close() {
        super.close();
        pool.remit(cipher);
    }
}
